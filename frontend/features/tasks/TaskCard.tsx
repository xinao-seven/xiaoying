import { formatDateTime } from "@/lib/format";
import type { Task } from "@/lib/api/types";

const STAGE_LABELS: Record<string, string> = {
  MATERIAL_CHECK: "素材检查",
  GENERATING: "模拟生成",
  ASSEMBLING: "结果整理",
  DONE: "完成",
};

const STATUS_LABELS: Record<Task["status"], string> = {
  PENDING: "等待处理",
  PROCESSING: "处理中",
  SUCCEEDED: "已完成",
  FAILED: "制作失败",
};

export function stageLabel(stage: string | null): string {
  if (!stage) return "—";
  return STAGE_LABELS[stage] ?? stage;
}

export function TaskCard({ task }: { task: Task }) {
  return (
    <article className="task-card" data-status={task.status} aria-label={`${task.templateTitle} · ${STATUS_LABELS[task.status]}`}>
      <header className="task-head">
        <h3 className="task-title">{task.templateTitle}</h3>
        <span className="task-status" data-status={task.status}>
          {STATUS_LABELS[task.status]}
        </span>
      </header>

      <dl className="task-meta">
        <div>
          <dt>创建时间</dt>
          <dd>{formatDateTime(task.createdAt)}</dd>
        </div>
        {task.status === "PROCESSING" && (
          <div>
            <dt>当前阶段</dt>
            <dd>{stageLabel(task.stage)}</dd>
          </div>
        )}
        {task.status === "FAILED" && (
          <div>
            <dt>失败阶段</dt>
            <dd>{stageLabel(task.failedStage)}</dd>
          </div>
        )}
      </dl>

      {task.status === "PENDING" && <p className="task-hint">已受理，等待后台处理。</p>}

      {task.status === "FAILED" && (
        <p className="task-failure" role="alert">
          {task.errorMessage ?? "制作失败，原因未知"}
        </p>
      )}

      {task.status === "SUCCEEDED" && task.resultUrl && (
        // 结果图受会话 Cookie 保护，必须直接请求后端，不能经过 next/image 优化。
        // eslint-disable-next-line @next/next/no-img-element
        <img
          className="result-image"
          src={task.resultUrl}
          alt={`${task.templateTitle} 的制作结果`}
          loading="lazy"
        />
      )}
    </article>
  );
}