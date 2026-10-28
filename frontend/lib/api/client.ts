import type { ApiErrorBody } from "./types";

/** 统一的接口错误；前端按 status/code 区分上传、提交、查询、登录失效。 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors?: Record<string, string>;
  readonly traceId?: string;

  constructor(
    status: number,
    code: string,
    message: string,
    details: { fieldErrors?: Record<string, string>; traceId?: string } = {},
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = details.fieldErrors;
    this.traceId = details.traceId;
  }
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/** 由 Provider 注册：401 时清空任务 store 并跳转登录。 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

export function notifyUnauthorized(): void {
  unauthorizedHandler?.();
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/** 把任意异常转成可直接展示的中文文案。 */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0) return error.message;
    if (error.status === 404) return "请求的内容不存在或已被移除";
    return error.message;
  }
  if (error instanceof Error && error.message) return error.message;
  return "请求失败，请稍后重试";
}

export function toApiError(status: number, body: ApiErrorBody | null): ApiError {
  return new ApiError(status, body?.code ?? `HTTP_${status}`, body?.message ?? `请求失败（${status}）`, {
    fieldErrors: body?.fieldErrors,
    traceId: body?.traceId,
  });
}

async function parseErrorBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody;
  } catch {
    return null;
  }
}

/**
 * 统一请求入口：同源 `/api` 代理、自动携带 Cookie、非 2xx 解析错误体。
 * 401 先交给全局处理器（清 store + 跳登录），再抛出 ApiError。
 */
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { credentials: "include", cache: "no-store", ...init });
  } catch (error) {
    if (isAbortError(error)) throw error;
    throw new ApiError(0, "NETWORK_ERROR", "网络连接失败，请检查网络后重试");
  }

  if (response.status === 401) {
    // 登录失败的 401 是表单错误，不是会话失效，不触发全局跳转。
    if (path !== "/api/auth/login") {
      notifyUnauthorized();
    }
    throw toApiError(401, await parseErrorBody(response));
  }
  if (!response.ok) {
    throw toApiError(response.status, await parseErrorBody(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}