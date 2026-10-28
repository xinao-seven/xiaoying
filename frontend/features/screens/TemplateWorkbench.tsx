"use client";

import type { Template } from "@/lib/api/types";
import { formatBytes } from "@/lib/format";
import { SubmitPanel } from "@/features/upload/SubmitPanel";
import { UploadSlot } from "@/features/upload/UploadSlot";
import { useUploadSlots } from "@/features/upload/useUploadSlots";
import { acceptedTypesLabel } from "@/features/upload/uploadValidation";
import { TaskPanel } from "@/features/tasks/TaskPanel";

export function TemplateWorkbench({ template }: { template: Template }) {
  const { slots, selectFile, removeSlot, retrySlot, allUploaded, uploading, uploadedCount } =
    useUploadSlots(template);

  return (
    <div className="detail-grid">
      <div className="detail-main">
        <section className="detail-hero" aria-labelledby="template-title">
          <div className="detail-cover-wrap">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="detail-cover" src={template.coverUrl} alt={`${template.title} 封面`} />
          </div>
          <div className="detail-intro">
            <span className="badge badge-soft">{template.category}</span>
            <h1 id="template-title">{template.title}</h1>
            <p className="detail-desc">{template.description ?? "暂无模板描述。"}</p>
            <ul className="requirements" aria-label="素材要求">
              <li>
                <span>所需素材</span>
                <strong>{template.requiredImageCount} 张</strong>
              </li>
              <li>
                <span>支持格式</span>
                <strong>{acceptedTypesLabel(template.acceptedMimeTypes)}</strong>
              </li>
              <li>
                <span>单张上限</span>
                <strong>{formatBytes(template.maxFileSizeBytes)}</strong>
              </li>
            </ul>
          </div>
        </section>

        <section aria-labelledby="upload-title">
          <div className="section-head">
            <h2 id="upload-title">上传素材</h2>
            <span className="section-note">
              已上传 {uploadedCount}/{slots.length}
            </span>
          </div>
          <ul className="upload-grid">
            {slots.map((slot) => (
              <UploadSlot
                key={slot.index}
                slot={slot}
                acceptedMimeTypes={template.acceptedMimeTypes}
                maxFileSizeHint={formatBytes(template.maxFileSizeBytes)}
                onSelect={(file) => selectFile(slot.index, file)}
                onRemove={() => removeSlot(slot.index)}
                onRetry={() => retrySlot(slot.index)}
              />
            ))}
          </ul>
        </section>

        <SubmitPanel
          template={template}
          slots={slots}
          allUploaded={allUploaded}
          uploading={uploading}
        />
      </div>

      <aside className="detail-side" aria-labelledby="tasks-title">
        <div className="section-head">
          <h2 id="tasks-title">我的任务</h2>
        </div>
        <p className="section-note">提交后任务在后台异步处理，可在这里查看状态。</p>
        <TaskPanel templateId={template.id} />
      </aside>
    </div>
  );
}