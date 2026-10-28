"use client";

import { useId, type ChangeEvent } from "react";
import type { UploadSlotState } from "./types";
import { acceptedTypesLabel } from "./uploadValidation";

interface UploadSlotProps {
  slot: UploadSlotState;
  acceptedMimeTypes: string[];
  maxFileSizeHint: string;
  disabled?: boolean;
  onSelect: (file: File) => void;
  onRemove: () => void;
  onRetry: () => void;
}

function statusText(slot: UploadSlotState): string {
  switch (slot.phase) {
    case "UPLOADING":
      return `上传中 ${slot.progress}%`;
    case "UPLOADED":
      return "已上传";
    case "ERROR":
      return "上传失败";
    case "SELECTED":
      return "已选择";
    default:
      return slot.error ? "需要重新选择" : "待选择";
  }
}

export function UploadSlot({
  slot,
  acceptedMimeTypes,
  maxFileSizeHint,
  disabled = false,
  onSelect,
  onRemove,
  onRetry,
}: UploadSlotProps) {
  const inputId = useId();
  const errorId = `${inputId}-error`;
  const hintId = `${inputId}-hint`;
  const number = slot.index + 1;
  const isUploading = slot.phase === "UPLOADING";
  const hasFile = Boolean(slot.file);

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // 清空 value，允许再次选择同一个文件（例如替换或重试）。
    event.target.value = "";
    if (file) onSelect(file);
  }

  return (
    <li className="upload-slot" data-phase={slot.phase}>
      <div className="slot-head">
        <span className="slot-index">槽位 {number}</span>
        <span className="slot-status" data-tone={slot.phase.toLowerCase()}>
          {statusText(slot)}
        </span>
      </div>

      <div className="slot-frame">
        {slot.previewUrl ? (
          // blob: 预览与受 Cookie 保护的结果图不能走 next/image 优化器。
          // eslint-disable-next-line @next/next/no-img-element
          <img className="slot-preview" src={slot.previewUrl} alt={`槽位 ${number} 预览`} />
        ) : (
          <div className="slot-empty">
            <span aria-hidden="true">＋</span>
            <p>尚未选择图片</p>
          </div>
        )}
      </div>

      {isUploading && (
        <div
          className="progress"
          role="progressbar"
          aria-label={`槽位 ${number} 上传进度`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={slot.progress}
        >
          <span style={{ width: `${slot.progress}%` }} />
        </div>
      )}

      {slot.error && (
        <p className="slot-error" id={errorId} role="alert">
          {slot.error}
        </p>
      )}

      <p className="slot-hint" id={hintId}>
        {acceptedTypesLabel(acceptedMimeTypes)}，单张不超过 {maxFileSizeHint}
      </p>

      <div className="slot-actions">
        <label className={`btn btn-ghost btn-sm upload-pick${disabled || isUploading ? " is-disabled" : ""}`}>
          {hasFile ? "替换图片" : "选择图片"}
          <input
            id={inputId}
            type="file"
            accept={acceptedMimeTypes.join(",")}
            onChange={handleChange}
            disabled={disabled || isUploading}
            aria-describedby={slot.error ? `${hintId} ${errorId}` : hintId}
          />
        </label>

        {slot.phase === "ERROR" && (
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={onRetry}
            disabled={disabled}
            aria-label={`重试槽位 ${number} 的上传`}
          >
            重试
          </button>
        )}

        {hasFile && (
          <button
            type="button"
            className="btn btn-ghost btn-sm btn-danger-text"
            onClick={onRemove}
            disabled={disabled}
            aria-label={`移除槽位 ${number} 的图片`}
          >
            移除
          </button>
        )}
      </div>
    </li>
  );
}