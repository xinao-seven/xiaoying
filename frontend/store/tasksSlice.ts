import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { Task } from "@/lib/api/types";

export type SyncState = "idle" | "syncing" | "error";

export interface TaskSync {
  state: SyncState;
  message?: string;
}

export interface TasksState {
  byId: Record<string, Task>;
  /** 按 createdAt 倒序保存任务 id，界面只依赖这个顺序。 */
  order: string[];
  /** 查询状态与任务状态分离：查询失败只写这里，不改任务本身。 */
  sync: Record<string, TaskSync>;
}

export const initialState: TasksState = {
  byId: {},
  order: [],
  sync: {},
};

function compareTasks(a: Task, b: Task): number {
  const created = b.createdAt.localeCompare(a.createdAt);
  return created !== 0 ? created : b.id.localeCompare(a.id);
}

function applyUpsert(state: TasksState, incoming: Task): void {
  const existing = state.byId[incoming.id];
  // 晚到的旧版本响应直接丢弃（TC-FE-06）。
  if (existing && incoming.version < existing.version) return;

  state.byId[incoming.id] = incoming;

  if (!existing) {
    state.order.push(incoming.id);
    state.order.sort((left, right) => {
      const a = state.byId[left];
      const b = state.byId[right];
      if (!a || !b) return 0;
      return compareTasks(a, b);
    });
  }
}

const tasksSlice = createSlice({
  name: "tasks",
  initialState,
  reducers: {
    upsertTask(state, action: PayloadAction<Task>) {
      applyUpsert(state, action.payload);
    },
    upsertMany(state, action: PayloadAction<Task[]>) {
      for (const task of action.payload) {
        applyUpsert(state, task);
      }
    },
    setSync(
      state,
      action: PayloadAction<{ id: string; state: SyncState; message?: string }>,
    ) {
      const { id, state: syncState, message } = action.payload;
      state.sync[id] = message ? { state: syncState, message } : { state: syncState };
    },
    removeTask(state, action: PayloadAction<string>) {
      const id = action.payload;
      delete state.byId[id];
      delete state.sync[id];
      state.order = state.order.filter((taskId) => taskId !== id);
    },
    resetAll() {
      return initialState;
    },
  },
});

export const { upsertTask, upsertMany, setSync, removeTask, resetAll } = tasksSlice.actions;

export default tasksSlice.reducer;