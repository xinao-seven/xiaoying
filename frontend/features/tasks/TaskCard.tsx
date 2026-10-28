"use client";

import { useCallback, useRef, useState } from "react";
import { formatDateTime } from "@/lib/format";
import type { Task } from "@/lib/api/types";
import { useAppSelector } from "@/store";
import { ResultDialog } from "./ResultDialog";

const STAGE_LABELS: Record<string, string> = {
  MATERIAL_CHECK: "素材检查",
  GENERATING: "模拟生成",
  ASSEMBLING: "结果整理",
  DONE: "完成",
};

const STAGE_ORDER = ["MATERIAL_CHECK", "GENERATING", "ASSEMBLING"] as const;

const STATUS_LABELS: Record<Task["status"], string> = {
  PENDING: "等待处理",
  PROCESSING: "处理中",
  SUCCEEDED: "已完成",
  FAILED: "制作失败",
};

type StageState = "done" | "current" | "failed" | "todo";

export function stageLabel(stage: string | null): string {
  if (!stage) return "—";
  return STAGE_LABELS[stage] ?? stage;
}

function stageState(task: Task, stage: (typeof STAGE_ORDER)[number], index: number): StageState {
  if (task.status === "SUCCEEDED") return "done";
  if (task.status === "FAILED" && task.failedStage === stage) return "failed";
  const activeIndex = task.stage ? STAGE_ORDER.indexOf(task.stage as (typeof STAGE_ORDER)[number]) : -1;
  if (activeIndex < 0) return "todo";
  if (index < activeIndex) return "done";
  return index === activeIndex ? "current" : "todo";
}

function StageTrack({ task }: { task: Task }) {
  return (
    <ol className="stage-track" aria-label="制作阶段">
      {STAGE_ORDER.map((stage, index) => {
        const state = stageState(task, stage, index);
        return (
          <li key={stage} data-state={state} aria-current={state === "current" ? "step" : undefined}>
            <span className="stage-dot" aria-hidden="true" />
            <span>{stageLabel(stage)}</span>
          </li>
        );
      })}
    </ol>
  );
}

interface TaskCardProps {
  task: Task;
  /** 查询失败后的手动重试；不传时按钮只用于展示入口。 */
  onRetry?: (id: string) => void;
}

export function TaskCard({ task, onRetry }: TaskCardProps) {
  // 查询状态单独订阅：只影响提示，不影响任务本身的 status/stage/result。
  const sync = useAppSelector((state) => state.tasks.sync[task.id]);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [imageBroken, setImageBroken] = useState(false);
  const previewButtonRef = useRef<HTMLButtonElement | null>(null);

  const closePreview = useCallback(() => setPreviewOpen(false), []);

  const showStageTrack = task.status === "PROCESSING" || task.status === "FAILED";

  return (
    <article
      className="task-card"
      data-status={task.status}
      aria-label={`${task.templateTitle} · ${STATUS_LABELS[task.status]}`}
    >
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

      {showStageTrack && <StageTrack task={task} />}

      {task.status === "PENDING" && <p className="task-hint">已受理，等待后台处理。</p>}

      {task.status === "FAILED" && (
        <p className="task-failure" role="alert">
          <strong>制作失败</strong>
          {task.failedStage ? `（${stageLabel(task.failedStage)}）` : ""}：
          {task.errorMessage ?? "原因未知"}
        </p>
      )}

      {task.status === "SUCCEEDED" && task.resultUrl && (
        <div className="result-block">
          {imageBroken ? (
            <p className="result-placeholder" role="status">
              结果图暂时无法加载，任务仍为已完成。稍后可刷新页面重试。
            </p>
          ) : (
            // 结果图受会话 Cookie 保护，必须直接请求后端，不能经过 next/image 优化。
            // eslint-disable-next-line @next/next/no-img-element
            <img
              className="result-image"
              src={task.resultUrl}
              alt={`${task.templateTitle} 的制作结果`}
              loading="lazy"
              onError={() => setImageBroken(true)}
            />
          )}
          <button
            ref={previewButtonRef}
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => setPreviewOpen(true)}
            disabled={imageBroken}
          >
            预览大图
          </button>
        </div>
      )}

      {task.status === "SUCCEEDED" && !task.resultUrl && (
        <p className="task-hint">任务已完成，但没有可预览的结果文件。</p>
      )}

      {sync?.state === "error" && (
        <div className="task-sync-error" role="status">
          <span>
            状态刷新失败，当前显示最后一次成功状态。
            {sync.message ? `（${sync.message}）` : ""}
          </span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => onRetry?.(task.id)}>
            重试
          </button>
        </div>
      )}

      {task.resultUrl && (
        <ResultDialog
          open={previewOpen}
          title={task.templateTitle}
          imageUrl={task.resultUrl}
          onClose={closePreview}
        />
      )}
    </article>
  );
}