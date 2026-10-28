/** 与后端接口一一对应的 DTO；接口变更先改这里。 */

export interface User {
  id: string;
  username: string;
  displayName: string;
}

export interface Template {
  id: string;
  title: string;
  category: string;
  coverUrl: string;
  description: string | null;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
}

export interface Category {
  name: string;
  count: number;
}

export interface Material {
  id: string;
  url: string;
  mimeType: string;
  sizeBytes: number;
  width: number;
  height: number;
  createdAt: string;
}

export type TaskStatus = "PENDING" | "PROCESSING" | "SUCCEEDED" | "FAILED";

export interface Task {
  id: string;
  templateId: string;
  templateTitle: string;
  status: TaskStatus;
  stage: string | null;
  failedStage: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  resultUrl: string | null;
  version: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface TaskPage {
  items: Task[];
  page: number;
  size: number;
  total: number;
}

export interface ApiErrorBody {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
  traceId?: string;
}