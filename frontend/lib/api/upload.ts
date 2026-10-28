import { ApiError, notifyUnauthorized, toApiError } from "./client";
import type { ApiErrorBody, Material } from "./types";

export interface UploadOptions {
  /** 0～100；仅展示用途，不参与校验。 */
  onProgress?: (percent: number) => void;
  signal?: AbortSignal;
}

/**
 * 素材上传必须走 multipart，且需要上传进度，因此用 XHR 而不是 fetch。
 * 401 与其他接口一样交给全局处理器（清 store + 跳登录）。
 */
export function uploadMaterial(file: File, options: UploadOptions = {}): Promise<Material> {
  return new Promise<Material>((resolve, reject) => {
    if (options.signal?.aborted) {
      reject(new DOMException("上传已取消", "AbortError"));
      return;
    }

    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/materials");
    xhr.withCredentials = true;
    xhr.responseType = "text";

    const cleanup = () => {
      options.signal?.removeEventListener("abort", handleAbort);
    };

    const handleAbort = () => xhr.abort();

    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable || event.total === 0) return;
      options.onProgress?.(Math.min(100, Math.round((event.loaded / event.total) * 100)));
    };

    xhr.onload = () => {
      cleanup();
      const body = parseJson(xhr.responseText);
      if (xhr.status === 401) {
        notifyUnauthorized();
        reject(toApiError(401, body as ApiErrorBody | null));
        return;
      }
      if (xhr.status < 200 || xhr.status >= 300) {
        reject(toApiError(xhr.status, body as ApiErrorBody | null));
        return;
      }
      if (!body) {
        reject(new ApiError(xhr.status, "INVALID_RESPONSE", "服务器返回内容无法解析"));
        return;
      }
      resolve(body as Material);
    };

    xhr.onerror = () => {
      cleanup();
      reject(new ApiError(0, "NETWORK_ERROR", "上传失败，请检查网络后重试"));
    };

    xhr.onabort = () => {
      cleanup();
      reject(new DOMException("上传已取消", "AbortError"));
    };

    options.signal?.addEventListener("abort", handleAbort, { once: true });

    const form = new FormData();
    form.append("file", file);
    xhr.send(form);
  });
}

function parseJson(text: string): unknown {
  if (!text) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}