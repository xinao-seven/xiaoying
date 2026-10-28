"use client";

import { useMemo } from "react";
import { useAppSelector } from "@/store";
import { TaskCard } from "./TaskCard";

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

  if (tasks.length === 0) {
    return <p className="state-note">还没有任务。上传素材并提交后，任务会立即出现在这里。</p>;
  }

  return (
    <div className="task-list">
      {tasks.map((task) => (
        <TaskCard key={task.id} task={task} />
      ))}
    </div>
  );
}