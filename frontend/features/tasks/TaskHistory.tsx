"use client";

import Link from "next/link";
import { selectOrderedTasks, useAppSelector } from "@/store";
import { TaskCard } from "./TaskCard";
import { useTaskSync } from "./TaskSync";

const SUMMARY_ITEMS = [
  { key: "PROCESSING", label: "处理中" },
  { key: "PENDING", label: "等待处理" },
  { key: "SUCCEEDED", label: "已完成" },
  { key: "FAILED", label: "失败" },
] as const;

export function TaskHistory() {
  const tasks = useAppSelector(selectOrderedTasks);
  const { historyState, historyError, reloadHistory, refresh } = useTaskSync();

  const counts = SUMMARY_ITEMS.map((item) => ({
    ...item,
    count: tasks.filter((task) => task.status === item.key).length,
  }));

  return (
    <main className="page">
      <header className="page-head">
        <p className="eyebrow">任务中心</p>
        <h1>历史任务</h1>
        <p className="page-lede">
          汇总当前账号最近 50 个制作任务。处理中的任务会自动刷新，刷新页面后也能从服务端恢复。
        </p>
      </header>

      <div className="history-bar">
        <ul className="history-summary" aria-label="任务状态统计">
          {counts.map((item) => (
            <li key={item.key} data-status={item.key}>
              <strong>{item.count}</strong>
              <span>{item.label}</span>
            </li>
          ))}
        </ul>
        <div className="history-actions">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={reloadHistory}
            disabled={historyState === "loading"}
          >
            {historyState === "loading" ? "刷新中…" : "刷新列表"}
          </button>
        </div>
      </div>

      {historyState === "error" && (
        <div className="inline-error" role="alert">
          <span>历史任务加载失败{historyError ? `：${historyError}` : "。"} 已保留此前已加载的任务。</span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={reloadHistory}>
            重新加载
          </button>
        </div>
      )}

      {historyState === "loading" && tasks.length === 0 ? (
        <div className="task-list task-list-wide" aria-busy="true">
          {[0, 1, 2].map((index) => (
            <div key={index} className="task-card skeleton-card" aria-hidden="true">
              <span className="skeleton skeleton-line skeleton-line-md" />
              <span className="skeleton skeleton-line" />
              <span className="skeleton skeleton-line skeleton-line-sm" />
            </div>
          ))}
          <p className="sr-only" role="status">
            正在加载历史任务
          </p>
        </div>
      ) : tasks.length === 0 ? (
        <div className="state-panel">
          <h2>还没有任务</h2>
          <p>选择一个模板、上传素材并提交后，任务会出现在这里。</p>
          <div className="state-actions">
            <Link href="/" className="btn btn-primary">
              去选模板
            </Link>
          </div>
        </div>
      ) : (
        <div className="task-list task-list-wide">
          {tasks.map((task) => (
            <TaskCard key={task.id} task={task} onRetry={(id) => refresh(id)} />
          ))}
        </div>
      )}
    </main>
  );
}