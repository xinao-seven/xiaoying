import type { Category, Task, Template } from "@/lib/api/types";

export const postcardTemplate: Template = {
  id: "demo-postcard",
  title: "旅行明信片",
  category: "创意图片",
  coverUrl: "/demo/postcard-cover.png",
  description: "上传两张图片，模拟生成一张旅行明信片",
  requiredImageCount: 2,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 5 * 1024 * 1024,
};

export const petTemplate: Template = {
  id: "pet-portrait",
  title: "萌宠写真",
  category: "宠物写真",
  coverUrl: "/demo/pet-portrait-cover.png",
  description: "上传一张宠物照片，生成柔和背景的写真",
  requiredImageCount: 1,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 2 * 1024 * 1024,
};

export const cityTemplate: Template = {
  id: "city-poster",
  title: "城市海报",
  category: "创意图片",
  coverUrl: "/demo/city-cover.png",
  description: "上传一张城市照片，生成一张复古风格海报",
  requiredImageCount: 1,
  acceptedMimeTypes: ["image/jpeg", "image/png"],
  maxFileSizeBytes: 2 * 1024 * 1024,
};

export const categoriesFixture: Category[] = [
  { name: "创意图片", count: 2 },
  { name: "宠物写真", count: 1 },
];

export function makeFile(name: string, type: string, size: number): File {
  const file = new File([new Uint8Array(1)], name, { type });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

let taskSeq = 0;

export function makeTask(overrides: Partial<Task> = {}): Task {
  taskSeq += 1;
  return {
    id: `tsk_${String(taskSeq).padStart(3, "0")}`,
    templateId: postcardTemplate.id,
    templateTitle: postcardTemplate.title,
    status: "PENDING",
    stage: null,
    failedStage: null,
    errorCode: null,
    errorMessage: null,
    resultUrl: null,
    version: 1,
    createdAt: new Date(Date.UTC(2026, 9, 28, 10, 0, taskSeq)).toISOString(),
    startedAt: null,
    finishedAt: null,
    ...overrides,
  };
}