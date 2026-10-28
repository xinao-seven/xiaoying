import type { Template } from "@/lib/api/types";
import { formatBytes } from "@/lib/format";

const MIME_LABELS: Record<string, string> = {
  "image/jpeg": "JPEG",
  "image/png": "PNG",
  "image/webp": "WebP",
};

export function mimeLabel(mime: string): string {
  return MIME_LABELS[mime] ?? mime;
}

export function acceptedTypesLabel(accepted: string[]): string {
  return accepted.map(mimeLabel).join("、");
}

export interface ValidationResult {
  ok: boolean;
  message?: string;
}

/** 客户端预校验：类型与大小；数量由槽位数量本身保证。 */
export function validateSelection(
  file: File,
  rules: Pick<Template, "acceptedMimeTypes" | "maxFileSizeBytes">,
): ValidationResult {
  if (file.size === 0) {
    return { ok: false, message: "文件为空，请重新选择" };
  }
  if (!rules.acceptedMimeTypes.includes(file.type)) {
    return {
      ok: false,
      message: `仅支持 ${acceptedTypesLabel(rules.acceptedMimeTypes)} 格式，当前为 ${mimeLabel(
        file.type || "未知格式",
      )}`,
    };
  }
  if (file.size > rules.maxFileSizeBytes) {
    return {
      ok: false,
      message: `图片不能超过 ${formatBytes(rules.maxFileSizeBytes)}，当前 ${formatBytes(file.size)}`,
    };
  }
  return { ok: true };
}