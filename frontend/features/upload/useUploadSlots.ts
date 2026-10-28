"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiError, describeError, isAbortError } from "@/lib/api/client";
import { uploadMaterial } from "@/lib/api/upload";
import type { Template } from "@/lib/api/types";
import { createEmptySlot, type UploadSlotState } from "./types";
import { validateSelection } from "./uploadValidation";

function uploadErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 413) return "图片超过服务器允许的大小";
    if (error.status === 415) return "图片格式不被服务器接受";
    return describeError(error);
  }
  return "上传失败，请重试";
}

/**
 * 单槽位状态机：EMPTY → SELECTED → UPLOADING → UPLOADED，失败进入 ERROR 可重试。
 * 预览 URL 与在途请求都按槽位记录，移除/替换/卸载时统一回收。
 */
export function useUploadSlots(template: Template) {
  const [slots, setSlots] = useState<UploadSlotState[]>(() =>
    Array.from({ length: template.requiredImageCount }, (_, index) => createEmptySlot(index)),
  );

  const previewsRef = useRef(new Map<number, string>());
  const controllersRef = useRef(new Map<number, AbortController>());
  const tokensRef = useRef(new Map<number, number>());

  const updateSlot = useCallback(
    (index: number, patch: Partial<UploadSlotState> | ((slot: UploadSlotState) => Partial<UploadSlotState>)) => {
      setSlots((previous) =>
        previous.map((slot, slotIndex) => {
          if (slotIndex !== index) return slot;
          const next = typeof patch === "function" ? patch(slot) : patch;
          return { ...slot, ...next };
        }),
      );
    },
    [],
  );

  const startUpload = useCallback(
    (index: number, file: File) => {
      const token = (tokensRef.current.get(index) ?? 0) + 1;
      tokensRef.current.set(index, token);

      controllersRef.current.get(index)?.abort();
      const controller = new AbortController();
      controllersRef.current.set(index, controller);

      updateSlot(index, { phase: "UPLOADING", progress: 0, materialId: null, error: null });

      uploadMaterial(file, {
        signal: controller.signal,
        onProgress: (percent) => {
          if (tokensRef.current.get(index) !== token) return;
          updateSlot(index, { progress: percent });
        },
      })
        .then((material) => {
          if (tokensRef.current.get(index) !== token) return;
          controllersRef.current.delete(index);
          updateSlot(index, {
            phase: "UPLOADED",
            materialId: material.id,
            progress: 100,
            error: null,
          });
        })
        .catch((error: unknown) => {
          if (tokensRef.current.get(index) !== token || isAbortError(error)) return;
          controllersRef.current.delete(index);
          updateSlot(index, { phase: "ERROR", progress: 0, error: uploadErrorMessage(error) });
        });
    },
    [updateSlot],
  );

  const selectFile = useCallback(
    (index: number, file: File) => {
      const validation = validateSelection(file, template);
      if (!validation.ok) {
        // 校验失败不发请求；保留该槽位原有素材，只在槽位内提示。
        updateSlot(index, { error: validation.message ?? "文件不符合要求" });
        return;
      }

      const previousPreview = previewsRef.current.get(index);
      if (previousPreview) {
        URL.revokeObjectURL(previousPreview);
      }
      const previewUrl = URL.createObjectURL(file);
      previewsRef.current.set(index, previewUrl);

      updateSlot(index, {
        phase: "SELECTED",
        file,
        previewUrl,
        materialId: null,
        progress: 0,
        error: null,
      });
      startUpload(index, file);
    },
    [startUpload, template, updateSlot],
  );

  const removeSlot = useCallback(
    (index: number) => {
      tokensRef.current.set(index, (tokensRef.current.get(index) ?? 0) + 1);
      controllersRef.current.get(index)?.abort();
      controllersRef.current.delete(index);

      const preview = previewsRef.current.get(index);
      if (preview) {
        URL.revokeObjectURL(preview);
        previewsRef.current.delete(index);
      }

      setSlots((previous) =>
        previous.map((slot, slotIndex) => (slotIndex === index ? createEmptySlot(index) : slot)),
      );
    },
    [],
  );

  const retrySlot = useCallback(
    (index: number) => {
      const file = slots[index]?.file;
      if (!file) return;
      startUpload(index, file);
    },
    [slots, startUpload],
  );

  useEffect(() => {
    const previews = previewsRef.current;
    const controllers = controllersRef.current;
    const tokens = tokensRef.current;
    return () => {
      controllers.forEach((controller) => controller.abort());
      controllers.clear();
      previews.forEach((url) => URL.revokeObjectURL(url));
      previews.clear();
      tokens.clear();
    };
  }, []);

  const allUploaded = slots.every((slot) => slot.phase === "UPLOADED");
  const uploading = slots.some((slot) => slot.phase === "UPLOADING");
  const uploadedCount = slots.filter((slot) => slot.phase === "UPLOADED").length;

  return useMemo(
    () => ({
      slots,
      selectFile,
      removeSlot,
      retrySlot,
      allUploaded,
      uploading,
      uploadedCount,
    }),
    [allUploaded, removeSlot, retrySlot, selectFile, slots, uploadedCount, uploading],
  );
}