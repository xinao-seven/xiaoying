import { fireEvent, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { makeStore } from "@/store";
import { setSync, upsertTask } from "@/store/tasksSlice";
import { makeTask } from "@/test/fixtures";
import { renderWithStore } from "@/test/render";
import { TaskCard } from "./TaskCard";

function storeWith(task: ReturnType<typeof makeTask>, sync?: { state: "idle" | "error"; message?: string }) {
  const store = makeStore();
  store.dispatch(upsertTask(task));
  if (sync) store.dispatch(setSync({ id: task.id, ...sync }));
  return store;
}

describe("TaskCard（TC-FE-02/03/14）", () => {
  it("处理中展示当前阶段与阶段轨迹", () => {
    const task = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 4 });
    renderWithStore(<TaskCard task={task} />, storeWith(task));

    expect(screen.getByText("处理中")).toBeInTheDocument();
    expect(screen.getAllByText("模拟生成").length).toBeGreaterThan(0);
    expect(screen.getByRole("list", { name: "制作阶段" })).toBeInTheDocument();
    expect(screen.getByText("素材检查")).toBeInTheDocument();
    expect(screen.queryByText(/状态刷新失败/)).not.toBeInTheDocument();
  });

  it("失败任务展示失败阶段与原因（与查询错误区分）", () => {
    const task = makeTask({
      status: "FAILED",
      stage: "GENERATING",
      failedStage: "GENERATING",
      errorCode: "SIMULATED_FAILURE",
      errorMessage: "模拟生成阶段失败",
      version: 7,
    });
    renderWithStore(<TaskCard task={task} />, storeWith(task));

    expect(screen.getByRole("alert")).toHaveTextContent("制作失败（模拟生成）：模拟生成阶段失败");
    expect(screen.queryByText(/状态刷新失败/)).not.toBeInTheDocument();
  });

  it("查询异常只追加提示，任务仍显示最后一次成功状态（TC-FE-02/03）", () => {
    const task = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 4 });
    renderWithStore(
      <TaskCard task={task} />,
      storeWith(task, { state: "error", message: "网络连接失败" }),
    );

    expect(screen.getByText("处理中")).toBeInTheDocument();
    expect(screen.getAllByText("模拟生成").length).toBeGreaterThan(0);
    expect(screen.getByRole("status")).toHaveTextContent("状态刷新失败");
    expect(screen.getByRole("status")).toHaveTextContent("当前显示最后一次成功状态");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("查询异常不改变 store 中的任务状态", () => {
    const task = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 4 });
    const store = storeWith(task, { state: "error", message: "网络连接失败" });
    renderWithStore(<TaskCard task={task} />, store);

    expect(store.getState().tasks.byId[task.id]).toEqual(task);
  });

  it("重试按钮回调当前任务 id", () => {
    const task = makeTask({ status: "PROCESSING", version: 4 });
    const onRetry = vi.fn();
    renderWithStore(<TaskCard task={task} onRetry={onRetry} />, storeWith(task, { state: "error" }));

    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(onRetry).toHaveBeenCalledWith(task.id);
  });

  it("结果图 404 时显示占位，任务仍为已完成（TC-FE-14）", () => {
    const task = makeTask({
      status: "SUCCEEDED",
      stage: "DONE",
      version: 8,
      resultUrl: `/api/tasks/tsk_404/result`,
    });
    renderWithStore(<TaskCard task={task} />, storeWith(task));

    fireEvent.error(screen.getByRole("img", { name: /制作结果/ }));

    expect(screen.getByText(/结果图暂时无法加载/)).toBeInTheDocument();
    expect(screen.getByText("已完成")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "预览大图" })).toBeDisabled();
  });

  it("预览对话框支持 Esc 关闭并把焦点还给触发按钮", () => {
    const task = makeTask({
      status: "SUCCEEDED",
      stage: "DONE",
      version: 8,
      resultUrl: `/api/tasks/tsk_preview/result`,
    });
    renderWithStore(<TaskCard task={task} />, storeWith(task));

    const trigger = screen.getByRole("button", { name: "预览大图" });
    trigger.focus();
    expect(trigger).toHaveFocus();

    fireEvent.click(trigger);
    const dialog = screen.getByRole("dialog", { name: /旅行明信片制作结果预览/ });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "关闭" })).toHaveFocus();

    fireEvent.keyDown(document, { key: "Escape" });

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("成功任务没有结果文件时给出说明", () => {
    const task = makeTask({ status: "SUCCEEDED", stage: "DONE", version: 8, resultUrl: null });
    renderWithStore(<TaskCard task={task} />, storeWith(task));

    expect(screen.getByText("任务已完成，但没有可预览的结果文件。")).toBeInTheDocument();
  });
});