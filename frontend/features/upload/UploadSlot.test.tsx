import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { uploadMaterial } from "@/lib/api/upload";
import type { Material, Template } from "@/lib/api/types";
import { UploadSlot } from "@/features/upload/UploadSlot";
import { useUploadSlots } from "@/features/upload/useUploadSlots";
import { makeFile, postcardTemplate } from "@/test/fixtures";

vi.mock("@/lib/api/upload", () => ({ uploadMaterial: vi.fn() }));

const mockedUpload = vi.mocked(uploadMaterial);

const createObjectURL = vi.fn();
const revokeObjectURL = vi.fn();
let previewSeq = 0;

Object.defineProperty(URL, "createObjectURL", {
  configurable: true,
  writable: true,
  value: createObjectURL,
});
Object.defineProperty(URL, "revokeObjectURL", {
  configurable: true,
  writable: true,
  value: revokeObjectURL,
});

function material(id: string): Material {
  return {
    id,
    url: `/api/materials/${id}/content`,
    mimeType: "image/png",
    sizeBytes: 1024,
    width: 640,
    height: 480,
    createdAt: "2026-10-28T10:00:00.000Z",
  };
}

function Harness({ template }: { template: Template }) {
  const { slots, selectFile, removeSlot, retrySlot } = useUploadSlots(template);

  return (
    <ul>
      {slots.map((slot) => (
        <UploadSlot
          key={slot.index}
          slot={slot}
          acceptedMimeTypes={template.acceptedMimeTypes}
          maxFileSizeHint="5 MB"
          onSelect={(file) => selectFile(slot.index, file)}
          onRemove={() => removeSlot(slot.index)}
          onRetry={() => retrySlot(slot.index)}
        />
      ))}
    </ul>
  );
}

beforeEach(() => {
  mockedUpload.mockReset();
  createObjectURL.mockReset();
  revokeObjectURL.mockReset();
  previewSeq = 0;
  createObjectURL.mockImplementation(() => {
    previewSeq += 1;
    return `blob:preview-${previewSeq}`;
  });
});

describe("UploadSlot（TC-FE-11 客户端校验）", () => {
  it("按模板渲染对应数量的槽位并编号", () => {
    render(<Harness template={postcardTemplate} />);

    expect(screen.getByText("槽位 1")).toBeInTheDocument();
    expect(screen.getByText("槽位 2")).toBeInTheDocument();
    expect(screen.getAllByLabelText(/选择图片/)).toHaveLength(2);
  });

  it("类型不符时不发上传请求，并在对应槽位提示", async () => {
    render(<Harness template={postcardTemplate} />);
    const input = screen.getAllByLabelText(/选择图片/)[0] as HTMLInputElement;
    const wrongType = makeFile("notes.txt", "text/plain", 100);

    fireEvent.change(input, { target: { files: [wrongType] } });

    expect(await screen.findByText(/仅支持 JPEG、PNG 格式/)).toBeInTheDocument();
    expect(mockedUpload).not.toHaveBeenCalled();
    expect(screen.getByText("需要重新选择")).toBeInTheDocument();
  });

  it("超过模板大小上限时不发上传请求", async () => {
    render(<Harness template={postcardTemplate} />);
    const input = screen.getAllByLabelText(/选择图片/)[0] as HTMLInputElement;
    const oversized = makeFile("big.png", "image/png", 6 * 1024 * 1024);

    fireEvent.change(input, { target: { files: [oversized] } });

    expect(await screen.findByText(/图片不能超过 5 MB/)).toBeInTheDocument();
    expect(mockedUpload).not.toHaveBeenCalled();
  });
});

describe("UploadSlot（TC-FE-12 失败重试）", () => {
  it("上传失败的槽位可单独重试，其他槽位不受影响", async () => {
    const fileA = makeFile("a.png", "image/png", 1000);
    const fileB = makeFile("b.png", "image/png", 1000);
    let attemptsA = 0;

    mockedUpload.mockImplementation((file) => {
      if (file === fileA) {
        attemptsA += 1;
        return attemptsA === 1
          ? Promise.reject(new ApiError(500, "INTERNAL_ERROR", "服务器开小差了"))
          : Promise.resolve(material("mat_a"));
      }
      return Promise.resolve(material("mat_b"));
    });

    render(<Harness template={postcardTemplate} />);
    const inputs = screen.getAllByLabelText(/选择图片/);

    fireEvent.change(inputs[0]!, { target: { files: [fileA] } });
    fireEvent.change(inputs[1]!, { target: { files: [fileB] } });

    expect(await screen.findByText("服务器开小差了")).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText("已上传")).toHaveLength(1));
    expect(mockedUpload).toHaveBeenCalledTimes(2);

    mockedUpload.mockClear();
    fireEvent.click(screen.getByRole("button", { name: "重试槽位 1 的上传" }));

    await waitFor(() => expect(mockedUpload).toHaveBeenCalledTimes(1));
    expect(mockedUpload).toHaveBeenCalledWith(fileA, expect.anything());
    await waitFor(() => expect(screen.getAllByText("已上传")).toHaveLength(2));
  });

  it("替换与移除时回收旧的预览 URL", async () => {
    const fileA = makeFile("a.png", "image/png", 1000);
    const fileB = makeFile("b.png", "image/png", 1000);
    mockedUpload.mockResolvedValue(material("mat_x"));

    render(<Harness template={postcardTemplate} />);
    const firstInput = screen.getAllByLabelText(/选择图片/)[0] as HTMLInputElement;
    fireEvent.change(firstInput, { target: { files: [fileA] } });

    await waitFor(() => expect(screen.getAllByText("已上传")).toHaveLength(1));
    expect(createObjectURL).toHaveBeenCalledTimes(1);

    const replaceInput = screen.getByLabelText("替换图片") as HTMLInputElement;
    fireEvent.change(replaceInput, { target: { files: [fileB] } });

    await waitFor(() => expect(revokeObjectURL).toHaveBeenCalledWith("blob:preview-1"));
    await waitFor(() =>
      expect(screen.getAllByRole("img", { name: /槽位 1 预览/ })[0]).toHaveAttribute(
        "src",
        "blob:preview-2",
      ),
    );

    fireEvent.click(screen.getByRole("button", { name: "移除槽位 1 的图片" }));

    await waitFor(() => expect(revokeObjectURL).toHaveBeenCalledWith("blob:preview-2"));
    expect(screen.queryByRole("img", { name: /槽位 1 预览/ })).not.toBeInTheDocument();
    expect(screen.getAllByText("尚未选择图片")).toHaveLength(2);
  });

  it("组件卸载时回收预览 URL", async () => {
    const fileA = makeFile("a.png", "image/png", 1000);
    mockedUpload.mockResolvedValue(material("mat_u"));

    const { unmount } = render(<Harness template={postcardTemplate} />);
    const input = screen.getAllByLabelText(/选择图片/)[0] as HTMLInputElement;
    fireEvent.change(input, { target: { files: [fileA] } });
    await waitFor(() => expect(screen.getAllByText("已上传")).toHaveLength(1));

    unmount();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:preview-1");
  });
});