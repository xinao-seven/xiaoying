import type { ReactNode } from "react";
import { Provider } from "react-redux";
import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { listActiveTasks, listTasks } from "@/lib/api/endpoints";
import type { Task } from "@/lib/api/types";
import { makeStore } from "@/store";
import { resetAll, upsertMany } from "@/store/tasksSlice";
import { makeTask } from "@/test/fixtures";
import { useTaskHistory, useTaskPolling } from "./useTaskPolling";

vi.mock("@/lib/api/endpoints", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/api/endpoints")>();
  return {
    ...actual,
    getTask: vi.fn(),
    listTasks: vi.fn(),
    listActiveTasks: vi.fn(),
  };
});

const mockedListTasks = vi.mocked(listTasks);
const mockedListActiveTasks = vi.mocked(listActiveTasks);

type TimerCallback = () => void;

interface HarnessOptions {
  tasks: Task[];
  fetcher: (id: string, signal: AbortSignal) => Promise<Task>;
}

function makeHarness({ tasks, fetcher }: HarnessOptions) {
  const store = makeStore();
  if (tasks.length > 0) store.dispatch(upsertMany(tasks));

  const timers: TimerCallback[] = [];
  const cancel = vi.fn();
  const schedule = vi.fn((callback: TimerCallback) => {
    timers.push(callback);
    return timers.length as unknown as ReturnType<typeof setTimeout>;
  });
  const wrapper = ({ children }: { children: ReactNode }) => <Provider store={store}>{children}</Provider>;

  const view = renderHook(
    () =>
      useTaskPolling({
        intervalMs: 1000,
        deps: { fetchTask: fetcher, schedule, cancel },
      }),
    { wrapper },
  );

  return { store, timers, schedule, cancel, ...view };
}

async function flushMicrotasks(): Promise<void> {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
}

async function fireNextTimer(timers: TimerCallback[]): Promise<void> {
  await act(async () => {
    const next = timers.shift();
    next?.();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
}

beforeEach(() => {
  mockedListTasks.mockReset();
  mockedListActiveTasks.mockReset();
});

describe("useTaskPolling（TC-FE-01/02/04/05/07/13）", () => {
  it("无活跃任务时不发请求也不排定时器（TC-FE-05）", async () => {
    const fetcher = vi.fn<(id: string, signal: AbortSignal) => Promise<Task>>();
    const { schedule } = makeHarness({
      tasks: [makeTask({ status: "SUCCEEDED", version: 6 })],
      fetcher,
    });

    await flushMicrotasks();

    expect(fetcher).not.toHaveBeenCalled();
    expect(schedule).not.toHaveBeenCalled();
  });

  it("上一轮结束后才排下一轮，并按版本合并（TC-FE-04 前置）", async () => {
    const task = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 3 });
    const next = { ...task, stage: "ASSEMBLING", version: 4 };
    const fetcher = vi.fn<(id: string, signal: AbortSignal) => Promise<Task>>().mockResolvedValue(next);
    const { store, timers, schedule } = makeHarness({ tasks: [task], fetcher });

    await flushMicrotasks();
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher).toHaveBeenCalledWith(task.id, expect.any(AbortSignal));
    expect(schedule).toHaveBeenCalledTimes(1);
    expect(store.getState().tasks.byId[task.id]?.stage).toBe("ASSEMBLING");

    await fireNextTimer(timers);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("任务到达终态后停止轮询（TC-FE-04）", async () => {
    const task = makeTask({ status: "PROCESSING", stage: "ASSEMBLING", version: 5 });
    const done: Task = {
      ...task,
      status: "SUCCEEDED",
      stage: "DONE",
      version: 6,
      resultUrl: `/api/tasks/${task.id}/result`,
    };
    const fetcher = vi.fn<(id: string, signal: AbortSignal) => Promise<Task>>().mockResolvedValue(done);
    const { store, timers, schedule } = makeHarness({ tasks: [task], fetcher });

    await flushMicrotasks();

    expect(store.getState().tasks.byId[task.id]?.status).toBe("SUCCEEDED");
    expect(schedule).not.toHaveBeenCalled();

    // 即使测试里还留着旧回调，也会因 effect 已清理而不再请求。
    timers.shift()?.();
    await flushMicrotasks();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("查询失败只写 sync，保留任务快照，恢复后自动继续（TC-FE-02）", async () => {
    const task = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 4 });
    const done: Task = { ...task, status: "SUCCEEDED", stage: "DONE", version: 5 };
    const fetcher = vi
      .fn<(id: string, signal: AbortSignal) => Promise<Task>>()
      .mockRejectedValueOnce(new ApiError(503, "SERVICE_UNAVAILABLE", "服务暂不可用"))
      .mockResolvedValue(done);
    const { store, timers, schedule } = makeHarness({ tasks: [task], fetcher });

    await flushMicrotasks();

    expect(store.getState().tasks.byId[task.id]?.status).toBe("PROCESSING");
    expect(store.getState().tasks.byId[task.id]?.stage).toBe("GENERATING");
    expect(store.getState().tasks.byId[task.id]?.version).toBe(4);
    expect(store.getState().tasks.sync[task.id]?.state).toBe("error");
    expect(store.getState().tasks.sync[task.id]?.message).toContain("服务暂不可用");
    expect(schedule).toHaveBeenCalledTimes(1);

    await fireNextTimer(timers);

    expect(store.getState().tasks.byId[task.id]?.status).toBe("SUCCEEDED");
    expect(store.getState().tasks.sync[task.id]?.state).toBe("idle");
  });

  it("多任务各自合并，一个失败不影响另一个（TC-FE-01）", async () => {
    const failing = makeTask({ status: "PROCESSING", stage: "MATERIAL_CHECK", version: 2 });
    const succeeding = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 5 });
    const fetcher = vi.fn<(id: string, signal: AbortSignal) => Promise<Task>>((id) => {
      if (id === failing.id) {
        return Promise.reject(new ApiError(500, "INTERNAL_ERROR", "查询失败"));
      }
      return Promise.resolve({ ...succeeding, status: "SUCCEEDED", stage: "DONE", version: 6 });
    });
    const { store } = makeHarness({ tasks: [failing, succeeding], fetcher });

    await flushMicrotasks();

    expect(store.getState().tasks.byId[failing.id]?.status).toBe("PROCESSING");
    expect(store.getState().tasks.byId[failing.id]?.version).toBe(2);
    expect(store.getState().tasks.sync[failing.id]?.state).toBe("error");

    expect(store.getState().tasks.byId[succeeding.id]?.status).toBe("SUCCEEDED");
    expect(store.getState().tasks.byId[succeeding.id]?.version).toBe(6);
    expect(store.getState().tasks.sync[succeeding.id]?.state).toBe("idle");
  });

  it("resetAll（切换用户）会 abort 在途请求并停止后续轮询（TC-FE-07）", async () => {
    const task = makeTask({ status: "PROCESSING", version: 2 });
    let captured: AbortSignal | null = null;
    const fetcher = vi.fn((_id: string, signal: AbortSignal) => {
      captured = signal;
      return new Promise<Task>(() => {});
    });
    const { store } = makeHarness({ tasks: [task], fetcher });

    await flushMicrotasks();
    expect(fetcher).toHaveBeenCalledTimes(1);

    act(() => {
      store.dispatch(resetAll());
    });

    expect(captured).not.toBeNull();
    expect((captured as AbortSignal | null)?.aborted).toBe(true);
    expect(store.getState().tasks.order).toHaveLength(0);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("卸载时 abort 在途请求且不再发起新请求（TC-FE-13）", async () => {
    const task = makeTask({ status: "PROCESSING", version: 2 });
    let captured: AbortSignal | null = null;
    const fetcher = vi.fn((_id: string, signal: AbortSignal) => {
      captured = signal;
      return new Promise<Task>(() => {});
    });
    const { unmount } = makeHarness({ tasks: [task], fetcher });

    await flushMicrotasks();
    unmount();

    expect(captured).not.toBeNull();
    expect((captured as AbortSignal | null)?.aborted).toBe(true);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("手动 refresh 立即发起一轮请求", async () => {
    const task = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 3 });
    const fetcher = vi
      .fn<(id: string, signal: AbortSignal) => Promise<Task>>()
      .mockResolvedValue({ ...task, stage: "ASSEMBLING", version: 4 });
    const { result } = makeHarness({ tasks: [task], fetcher });

    await flushMicrotasks();
    expect(fetcher).toHaveBeenCalledTimes(1);

    await act(async () => {
      result.current.refresh(task.id);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(fetcher).toHaveBeenCalledTimes(2);
  });
});

describe("useTaskHistory（TC-FE-08 刷新恢复）", () => {
  it("首屏同时拉历史和活跃任务并写入 store", async () => {
    const finished = makeTask({ status: "SUCCEEDED", version: 6 });
    const active = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 3 });
    mockedListTasks.mockResolvedValue({ items: [finished], page: 0, size: 50, total: 1 });
    mockedListActiveTasks.mockResolvedValue({ items: [active] });

    const store = makeStore();
    const wrapper = ({ children }: { children: ReactNode }) => <Provider store={store}>{children}</Provider>;
    const { result } = renderHook(() => useTaskHistory(true), { wrapper });

    await flushMicrotasks();

    expect(result.current.state).toBe("ready");
    expect(mockedListTasks).toHaveBeenCalledWith(0, 50, expect.any(AbortSignal));
    expect(mockedListActiveTasks).toHaveBeenCalledWith(expect.any(AbortSignal));
    expect(store.getState().tasks.byId[finished.id]?.status).toBe("SUCCEEDED");
    expect(store.getState().tasks.byId[active.id]?.stage).toBe("GENERATING");
  });

  it("历史失败时保留已加载的活跃任务并标记错误（TC-FE-02 边界）", async () => {
    const active = makeTask({ status: "PROCESSING", version: 3 });
    mockedListTasks.mockRejectedValue(new ApiError(500, "INTERNAL_ERROR", "历史查询失败"));
    mockedListActiveTasks.mockResolvedValue({ items: [active] });

    const store = makeStore();
    const wrapper = ({ children }: { children: ReactNode }) => <Provider store={store}>{children}</Provider>;
    const { result } = renderHook(() => useTaskHistory(true), { wrapper });

    await flushMicrotasks();

    expect(result.current.state).toBe("error");
    expect(result.current.error).toContain("历史查询失败");
    expect(store.getState().tasks.byId[active.id]?.status).toBe("PROCESSING");
  });

  it("登录页（enabled=false）不请求任务接口", async () => {
    const store = makeStore();
    const wrapper = ({ children }: { children: ReactNode }) => <Provider store={store}>{children}</Provider>;
    const { result } = renderHook(() => useTaskHistory(false), { wrapper });

    await flushMicrotasks();

    expect(mockedListTasks).not.toHaveBeenCalled();
    expect(mockedListActiveTasks).not.toHaveBeenCalled();
    expect(result.current.state).toBe("idle");
  });
});