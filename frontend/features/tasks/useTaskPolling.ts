"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { describeError, isAbortError } from "@/lib/api/client";
import { getTask, listActiveTasks, listTasks } from "@/lib/api/endpoints";
import type { Task } from "@/lib/api/types";
import { selectActiveTaskIds, useAppDispatch, useAppSelector, useAppStore } from "@/store";
import { setSync, upsertMany, upsertTask } from "@/store/tasksSlice";

/** 历史恢复一次取最近 50 条；后端支持分页，demo 先以单页覆盖刷新场景。 */
export const HISTORY_PAGE_SIZE = 50;
export const DEFAULT_POLL_INTERVAL_MS = 2000;

type SchedulerHandle = ReturnType<typeof setTimeout>;

export interface TaskPollingDeps {
  /** 查询单个任务；默认走 GET /api/tasks/{id}。 */
  fetchTask?: (id: string, signal: AbortSignal) => Promise<Task>;
  /** 安排下一轮；默认 setTimeout。 */
  schedule?: (callback: () => void, delayMs: number) => SchedulerHandle;
  /** 取消已安排的下一轮；默认 clearTimeout。 */
  cancel?: (handle: SchedulerHandle) => void;
}

interface TaskPollingOptions {
  enabled?: boolean;
  intervalMs?: number;
  deps?: TaskPollingDeps;
}

const defaultFetchTask: NonNullable<TaskPollingDeps["fetchTask"]> = (id, signal) => getTask(id, signal);
const defaultSchedule: NonNullable<TaskPollingDeps["schedule"]> = (callback, delayMs) =>
  setTimeout(callback, delayMs);
const defaultCancel: NonNullable<TaskPollingDeps["cancel"]> = (handle) => clearTimeout(handle);

export interface TaskPollingResult {
  /** 立即刷新全部活跃任务；传 id 时只刷新该任务（用于卡片上的“重试”）。 */
  refresh: (id?: string) => void;
}

/**
 * 页面级轮询：只跟踪 PENDING/PROCESSING 的任务。
 * - 无活跃任务时不排定时器（TC-FE-05）。
 * - 上一轮全部结束后再排下一轮，避免请求堆叠。
 * - 单任务查询失败只写 sync，不触碰 status/stage/result（TC-FE-02）。
 * - 终态任务在下一次 selector 计算时移出集合，effect 清理会 abort 并取消定时器（TC-FE-04）。
 * - 卸载、登出、resetAll 均通过 cleanup 的 abort + clearTimeout 收口（TC-FE-07/13）。
 */
export function useTaskPolling(options: TaskPollingOptions = {}): TaskPollingResult {
  const { enabled = true, intervalMs = DEFAULT_POLL_INTERVAL_MS, deps } = options;
  const fetchTask = deps?.fetchTask ?? defaultFetchTask;
  const schedule = deps?.schedule ?? defaultSchedule;
  const cancel = deps?.cancel ?? defaultCancel;

  const dispatch = useAppDispatch();
  const store = useAppStore();
  // 只依赖 id 集合的字符串形式：版本更新不重启轮询，终态或新增任务才重启。
  const activeKey = useAppSelectorActiveKey();

  const pendingRefresh = useRef<string | null | undefined>(undefined);
  const [refreshToken, setRefreshToken] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    if (selectActiveTaskIds(store.getState()).length === 0) return;

    const controller = new AbortController();
    let timer: SchedulerHandle | null = null;
    let stopped = false;
    const refreshTarget = pendingRefresh.current;
    pendingRefresh.current = undefined;

    const runRound = async (targetIds?: string[]): Promise<void> => {
      if (stopped) return;
      const ids = targetIds ?? selectActiveTaskIds(store.getState());
      if (ids.length === 0) return;

      const results = await Promise.allSettled(ids.map((id) => fetchTask(id, controller.signal)));
      if (stopped) return;

      results.forEach((result, index) => {
        const id = ids[index]!;
        if (result.status === "fulfilled") {
          dispatch(upsertTask(result.value));
          dispatch(setSync({ id, state: "idle" }));
          return;
        }
        if (isAbortError(result.reason)) return;
        // 查询异常 ≠ 任务失败：只记录同步状态，保留最后一次成功快照。
        dispatch(setSync({ id, state: "error", message: describeError(result.reason) }));
      });

      if (stopped) return;
      if (selectActiveTaskIds(store.getState()).length === 0) return;
      timer = schedule(() => {
        timer = null;
        void runRound();
      }, intervalMs);
    };

    void runRound(refreshTarget ? [refreshTarget] : undefined);

    return () => {
      stopped = true;
      controller.abort();
      if (timer !== null) cancel(timer);
    };
  }, [enabled, activeKey, refreshToken, intervalMs, fetchTask, schedule, cancel, dispatch, store]);

  const refresh = useCallback((id?: string) => {
    pendingRefresh.current = id ?? null;
    setRefreshToken((token) => token + 1);
  }, []);

  return { refresh };
}

/** 独立成行以便在 hook 内保持依赖稳定：只有活跃 id 集合变化才返回新字符串。 */
function useAppSelectorActiveKey(): string {
  return useAppSelector((state) => selectActiveTaskIds(state).join("\u001f"));
}

export type HistoryState = "idle" | "loading" | "ready" | "error";

export interface TaskHistoryResult {
  state: HistoryState;
  error: string | null;
  reload: () => void;
}

/**
 * 刷新恢复：首屏同时拉历史与活跃任务，写入 store 后由 useTaskPolling 继续跟踪。
 * 历史失败不清空已有任务，只标记错误并给出重试入口。
 */
export function useTaskHistory(enabled: boolean): TaskHistoryResult {
  const dispatch = useAppDispatch();
  const [state, setState] = useState<HistoryState>(enabled ? "loading" : "idle");
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!enabled) {
      setState("idle");
      setError(null);
      return;
    }

    const controller = new AbortController();
    setState("loading");
    setError(null);

    void (async () => {
      const [historyResult, activeResult] = await Promise.allSettled([
        listTasks(0, HISTORY_PAGE_SIZE, controller.signal),
        listActiveTasks(controller.signal),
      ]);
      if (controller.signal.aborted) return;

      if (historyResult.status === "fulfilled") dispatch(upsertMany(historyResult.value.items));
      if (activeResult.status === "fulfilled") dispatch(upsertMany(activeResult.value.items));

      const rejected = [historyResult, activeResult].find(
        (result): result is PromiseRejectedResult => result.status === "rejected",
      );
      if (!rejected) {
        setState("ready");
        return;
      }
      if (isAbortError(rejected.reason)) return;
      setError(describeError(rejected.reason));
      setState("error");
    })();

    return () => controller.abort();
  }, [enabled, reloadKey, dispatch]);

  const reload = useCallback(() => {
    setReloadKey((key) => key + 1);
  }, []);

  return { state, error, reload };
}

