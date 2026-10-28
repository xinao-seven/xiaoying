import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { getTemplate, listCategories, listTemplates } from "@/lib/api/endpoints";
import { TemplateDetail } from "@/features/templates/TemplateDetail";
import { TemplateList } from "@/features/templates/TemplateList";
import { categoriesFixture, cityTemplate, petTemplate, postcardTemplate } from "@/test/fixtures";
import { renderWithStore } from "@/test/render";

vi.mock("@/lib/api/endpoints", () => ({
  listCategories: vi.fn(),
  listTemplates: vi.fn(),
  getTemplate: vi.fn(),
}));

const mockedListCategories = vi.mocked(listCategories);
const mockedListTemplates = vi.mocked(listTemplates);
const mockedGetTemplate = vi.mocked(getTemplate);

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolver) => {
    resolve = resolver;
  });
  return { promise, resolve };
}

beforeEach(() => {
  mockedListCategories.mockReset();
  mockedListTemplates.mockReset();
  mockedGetTemplate.mockReset();
  mockedListCategories.mockResolvedValue({ items: categoriesFixture });
});

describe("TemplateList（TC-FE-15 列表异常/空态）", () => {
  it("加载失败显示错误态，重试后恢复列表", async () => {
    mockedListTemplates
      .mockRejectedValueOnce(new ApiError(500, "INTERNAL_ERROR", "服务器开小差了"))
      .mockResolvedValue({ items: [postcardTemplate] });

    render(<TemplateList />);

    expect(await screen.findByRole("heading", { name: "模板加载失败" })).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("服务器开小差了");

    await userEvent.click(screen.getByRole("button", { name: "重新加载" }));

    expect(await screen.findByRole("link", { name: /旅行明信片/ })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "模板加载失败" })).not.toBeInTheDocument();
  });

  it("空分类显示空态与查看全部入口", async () => {
    mockedListTemplates.mockResolvedValue({ items: [] });

    render(<TemplateList />);

    expect(await screen.findByRole("heading", { name: "该分类下暂时没有模板" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "查看全部模板" })).toBeInTheDocument();
  });

  it("快速切换分类时丢弃旧响应，最终展示当前分类（TC-FE-15）", async () => {
    const petDeferred = deferred<{ items: (typeof petTemplate)[] }>();
    const cityDeferred = deferred<{ items: (typeof cityTemplate)[] }>();

    mockedListTemplates.mockImplementation((category) => {
      if (category === "宠物写真") return petDeferred.promise;
      if (category === "创意图片") return cityDeferred.promise;
      return Promise.resolve({ items: [postcardTemplate] });
    });

    render(<TemplateList />);
    expect(await screen.findByRole("link", { name: /旅行明信片/ })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /宠物写真/ }));
    fireEvent.click(screen.getByRole("button", { name: /创意图片/ }));

    await act(async () => {
      cityDeferred.resolve({ items: [cityTemplate] });
    });
    expect(await screen.findByRole("link", { name: /城市海报/ })).toBeInTheDocument();

    await act(async () => {
      petDeferred.resolve({ items: [petTemplate] });
    });
    await waitFor(() => {
      expect(screen.queryByRole("link", { name: /萌宠写真/ })).not.toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /城市海报/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /创意图片/ })).toHaveAttribute("aria-pressed", "true");
  });
});

describe("TemplateDetail（TC-FE-16 模板 404）", () => {
  it("模板不存在时显示提示与返回列表入口", async () => {
    mockedGetTemplate.mockRejectedValue(new ApiError(404, "TEMPLATE_NOT_FOUND", "模板不存在或已下架"));

    renderWithStore(<TemplateDetail templateId="missing" />);

    expect(await screen.findByRole("heading", { name: "模板不存在" })).toBeInTheDocument();
    const backLinks = screen.getAllByRole("link", { name: /返回模板列表/ });
    expect(backLinks.length).toBeGreaterThan(0);
    expect(backLinks[0]).toHaveAttribute("href", "/");
  });

  it("详情加载失败可重试", async () => {
    mockedGetTemplate
      .mockRejectedValueOnce(new ApiError(500, "INTERNAL_ERROR", "服务器开小差了"))
      .mockResolvedValue(postcardTemplate);

    renderWithStore(<TemplateDetail templateId="demo-postcard" />);

    expect(await screen.findByRole("heading", { name: "模板加载失败" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "重新加载" }));

    expect(await screen.findByRole("heading", { name: "旅行明信片" })).toBeInTheDocument();
    expect(screen.getAllByText("槽位 1").length).toBeGreaterThan(0);
    expect(screen.getByText("槽位 2")).toBeInTheDocument();
  });
});