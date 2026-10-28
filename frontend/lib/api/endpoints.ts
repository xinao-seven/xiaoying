import { apiRequest } from "./client";
import type { Category, Task, TaskPage, Template, User } from "./types";

export function login(username: string, password: string): Promise<{ user: User }> {
  return apiRequest<{ user: User }>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
}

export function logout(): Promise<void> {
  return apiRequest<void>("/api/auth/logout", { method: "POST" });
}

export function me(signal?: AbortSignal): Promise<User> {
  return apiRequest<User>("/api/auth/me", { signal });
}

export function listCategories(signal?: AbortSignal): Promise<{ items: Category[] }> {
  return apiRequest<{ items: Category[] }>("/api/templates/categories", { signal });
}

export function listTemplates(category: string | null, signal?: AbortSignal): Promise<{ items: Template[] }> {
  const query = category ? `?category=${encodeURIComponent(category)}` : "";
  return apiRequest<{ items: Template[] }>(`/api/templates${query}`, { signal });
}

export function getTemplate(id: string, signal?: AbortSignal): Promise<Template> {
  return apiRequest<Template>(`/api/templates/${encodeURIComponent(id)}`, { signal });
}

export interface CreateTaskInput {
  templateId: string;
  materialIds: string[];
  params?: { caption?: string };
}

export function createTask(input: CreateTaskInput, signal?: AbortSignal): Promise<Task> {
  return apiRequest<Task>("/api/tasks", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
    signal,
  });
}

export function getTask(id: string, signal?: AbortSignal): Promise<Task> {
  return apiRequest<Task>(`/api/tasks/${encodeURIComponent(id)}`, { signal });
}

export function listTasks(page = 0, size = 20, signal?: AbortSignal): Promise<TaskPage> {
  return apiRequest<TaskPage>(`/api/tasks?page=${page}&size=${size}`, { signal });
}

export function listActiveTasks(signal?: AbortSignal): Promise<{ items: Task[] }> {
  return apiRequest<{ items: Task[] }>("/api/tasks/active", { signal });
}