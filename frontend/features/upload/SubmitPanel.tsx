"use client";

import { useRef, useState, type FormEvent } from "react";
import { createTask } from "@/lib/api/endpoints";
import { describeError, isAbortError } from "@/lib/api/client";
import type { Task, Template } from "@/lib/api/types";
import { useAppDispatch } from "@/store";
import { upsertTask } from "@/store/tasksSlice";
import type { UploadSlotState } from "./types";

interface SubmitPanelProps {
  template: Template;
  slots: UploadSlotState[];
  allUploaded: boolean;
  uploading: boolean;
  onSubmitted?: (task: Task) => void;
}

export function SubmitPanel({ template, slots, allUploaded, uploading, onSubmitted }: SubmitPanelProps) {
  const dispatch = useAppDispatch();
  const [caption, setCaption] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const inFlight = useRef(false);

  const missingCount = slots.filter((slot) => slot.phase !== "UPLOADED").length;
  const disabled = submitting || uploading || !allUploaded;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // ref 守卫与 disabled 双保险：同一 tick 内的连续点击只发一次请求（TC-FE-10）。
    if (inFlight.current || !allUploaded || uploading) return;

    const materialIds = slots
      .map((slot) => slot.materialId)
      .filter((id): id is string => Boolean(id));
    if (materialIds.length !== template.requiredImageCount) {
      setError("还有素材尚未上传完成，请稍后再提交");
      return;
    }

    inFlight.current = true;
    setSubmitting(true);
    setError(null);
    setNotice(null);

    try {
      const task = await createTask({
        templateId: template.id,
        materialIds,
        params: caption.trim() ? { caption: caption.trim() } : undefined,
      });
      dispatch(upsertTask(task));
      setCaption("");
      setNotice("提交成功，任务已加入右侧列表。");
      onSubmitted?.(task);
    } catch (caught) {
      if (isAbortError(caught)) return;
      setError(`提交失败：${describeError(caught)}`);
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  return (
    <form className="submit-panel" onSubmit={handleSubmit} noValidate>
      <div className="field">
        <label htmlFor="caption">作品标题（可选）</label>
        <input
          id="caption"
          name="caption"
          type="text"
          maxLength={200}
          value={caption}
          onChange={(event) => setCaption(event.target.value)}
          placeholder="例如：夏日旅行"
          disabled={submitting}
        />
      </div>

      <p className="submit-hint" role="status">
        {uploading
          ? "正在上传素材，全部完成后即可提交"
          : !allUploaded
            ? `还有 ${missingCount} 个槽位未上传完成`
            : submitting
              ? "正在提交，请稍候…"
              : "所有素材已就绪，可以提交制作"}
      </p>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {notice && !error && (
        <p className="form-notice" role="status">
          {notice}
        </p>
      )}

      <button type="submit" className="btn btn-primary btn-block" disabled={disabled}>
        {submitting ? "提交中…" : "提交制作"}
      </button>
    </form>
  );
}