"use client";

import { createContext, useContext, useMemo } from "react";
import { usePathname } from "next/navigation";
import { useTaskHistory, useTaskPolling, type HistoryState } from "./useTaskPolling";

interface TaskSyncValue {
  refresh: (id?: string) => void;
  reloadHistory: () => void;
  historyState: HistoryState;
  historyError: string | null;
}

const noop = () => {};

const TaskSyncContext = createContext<TaskSyncValue>({
  refresh: noop,
  reloadHistory: noop,
  historyState: "idle",
  historyError: null,
});

export function useTaskSync(): TaskSyncValue {
  return useContext(TaskSyncContext);
}

/**
 * 全局任务同步层：登录态下首屏恢复历史，随后只轮询活跃任务。
 * 放在布局里，保证从详情页切到历史页再刷新都不会丢跟踪。
 */
export function TaskSync({ children }: { children: React.ReactNode }) {
  // 登录页不触碰任务接口，避免未登录请求被全局 401 处理器反复跳转。
  const pathname = usePathname();
  const enabled = pathname !== "/login";
  const history = useTaskHistory(enabled);
  const { refresh } = useTaskPolling({ enabled });

  const value = useMemo<TaskSyncValue>(
    () => ({
      refresh,
      reloadHistory: history.reload,
      historyState: history.state,
      historyError: history.error,
    }),
    [refresh, history.reload, history.state, history.error],
  );

  return <TaskSyncContext.Provider value={value}>{children}</TaskSyncContext.Provider>;
}