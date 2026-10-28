"use client";

import { useMemo } from "react";
import { useAppSelector } from "@/store";
import { TaskCard } from "./TaskCard";
import { useTaskSync } from "./TaskSync";

export function TaskPanel({ templateId }: { templateId: string }) {
  const tasksState = useAppSelector((state) => state.tasks);
  const tasks = useMemo(
    () =>
      tasksState.order
        .map((id) => tasksState.byId[id])
        .filter((task): task is NonNullable<typeof task> => Boolean(task))
        .filter((task) => task.templateId === templateId),
    [tasksState, templateId],
  );

  const { historyState, historyError, reloadHistory, refresh } = useTaskSync();

  if (tasks.length === 0) {
    if (historyState === "loading") {
      return (
        <p className="state-note" role="status">
          正在加载历史任务…
        </p>
      );
    }
    if (historyState === "error") {
      return (
        <div className="inline-error" role="alert">
          <p>历史任务加载失败{historyError ? `：${historyError}` : "。"}</p>
          <button type="button" className="btn btn-ghost btn-sm" onClick={reloadHistory}>
            重新加载
          </button>
        </div>
      );
    }
    return <p className="state-note">还没有任务。上传素材并提交后，任务会立即出现在这里。</p>;
  }

  return (
    <div className="task-list">
      {historyState === "error" && (
        <div className="inline-error" role="status">
          <span>历史任务未能完整加载{historyError ? `：${historyError}` : "。"}</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={reloadHistory}>
            重试
          </button>
        </div>
      )}
      {tasks.map((task) => (
        <TaskCard key={task.id} task={task} onRetry={(id) => refresh(id)} />
      ))}
    </div>
  );
}