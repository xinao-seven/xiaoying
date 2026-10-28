import { fireEvent, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { createTask } from "@/lib/api/endpoints";
import { SubmitPanel } from "@/features/upload/SubmitPanel";
import type { UploadSlotState } from "@/features/upload/types";
import { makeTask, postcardTemplate } from "@/test/fixtures";
import { renderWithStore } from "@/test/render";

vi.mock("@/lib/api/endpoints", () => ({ createTask: vi.fn() }));

const mockedCreateTask = vi.mocked(createTask);

function uploadedSlots(): UploadSlotState[] {
  return [
    {
      index: 0,
      phase: "UPLOADED",
      file: null,
      previewUrl: "blob:0",
      materialId: "mat_1",
      progress: 100,
      error: null,
    },
    {
      index: 1,
      phase: "UPLOADED",
      file: null,
      previewUrl: "blob:1",
      materialId: "mat_2",
      progress: 100,
      error: null,
    },
  ];
}

beforeEach(() => {
  mockedCreateTask.mockReset();
});

describe("SubmitPanel（TC-FE-09 提交成功切换 / TC-FE-10 防重复）", () => {
  it("素材未全部上传时按钮禁用并说明原因", () => {
    const slots = uploadedSlots();
    slots[1] = { ...slots[1]!, phase: "UPLOADING", materialId: null, progress: 40 };

    renderWithStore(
      <SubmitPanel template={postcardTemplate} slots={slots} allUploaded={false} uploading />,
    );

    expect(screen.getByRole("button", { name: "提交制作" })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("正在上传素材");
  });

  it("连续点击只发起一次请求，成功后立即插入任务并恢复按钮", async () => {
    const task = makeTask();
    mockedCreateTask.mockResolvedValue(task);
    const { store } = renderWithStore(
      <SubmitPanel
        template={postcardTemplate}
        slots={uploadedSlots()}
        allUploaded
        uploading={false}
      />,
    );

    const button = screen.getByRole("button", { name: "提交制作" });
    fireEvent.click(button);
    fireEvent.click(button);

    expect(mockedCreateTask).toHaveBeenCalledTimes(1);
    expect(mockedCreateTask).toHaveBeenCalledWith({
      templateId: postcardTemplate.id,
      materialIds: ["mat_1", "mat_2"],
      params: undefined,
    });

    expect(await screen.findByText(/提交成功/)).toBeInTheDocument();
    expect(store.getState().tasks.byId[task.id]).toEqual(task);

    const restored = screen.getByRole("button", { name: "提交制作" });
    expect(restored).toBeEnabled();
  });

  it("提交失败保留槽位并显示表单级错误（TC-FE-09 反向）", async () => {
    mockedCreateTask.mockRejectedValue(new ApiError(500, "INTERNAL_ERROR", "服务器忙不过来"));

    renderWithStore(
      <SubmitPanel
        template={postcardTemplate}
        slots={uploadedSlots()}
        allUploaded
        uploading={false}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "提交制作" }));

    expect(await screen.findByText(/提交失败：服务器忙不过来/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "提交制作" })).toBeEnabled();
    expect(mockedCreateTask).toHaveBeenCalledTimes(1);
  });

  it("填写标题时作为 params.caption 提交", async () => {
    mockedCreateTask.mockResolvedValue(makeTask());
    renderWithStore(
      <SubmitPanel
        template={postcardTemplate}
        slots={uploadedSlots()}
        allUploaded
        uploading={false}
      />,
    );

    fireEvent.change(screen.getByLabelText("作品标题（可选）"), { target: { value: "夏日旅行" } });
    fireEvent.click(screen.getByRole("button", { name: "提交制作" }));

    await waitFor(() =>
      expect(mockedCreateTask).toHaveBeenCalledWith({
        templateId: postcardTemplate.id,
        materialIds: ["mat_1", "mat_2"],
        params: { caption: "夏日旅行" },
      }),
    );
  });
});