export type SlotPhase = "EMPTY" | "SELECTED" | "UPLOADING" | "UPLOADED" | "ERROR";

export interface UploadSlotState {
  index: number;
  phase: SlotPhase;
  file: File | null;
  previewUrl: string | null;
  materialId: string | null;
  progress: number;
  error: string | null;
}

export function createEmptySlot(index: number): UploadSlotState {
  return {
    index,
    phase: "EMPTY",
    file: null,
    previewUrl: null,
    materialId: null,
    progress: 0,
    error: null,
  };
}