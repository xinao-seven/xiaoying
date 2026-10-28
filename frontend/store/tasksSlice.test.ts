import { describe, expect, it } from "vitest";
import reducer, {
  initialState,
  removeTask,
  resetAll,
  setSync,
  upsertMany,
  upsertTask,
} from "@/store/tasksSlice";
import { makeTask } from "@/test/fixtures";

describe("tasksSlice（TC-FE-01/06 版本合并）", () => {
  it("多任务按 id 独立合并，版本与状态互不串扰（TC-FE-01）", () => {
    const first = makeTask({ createdAt: "2026-10-28T09:00:00.000Z", version: 2, status: "PROCESSING" });
    const second = makeTask({ createdAt: "2026-10-28T10:00:00.000Z", version: 5, status: "PENDING" });

    let state = reducer(initialState, upsertMany([first, second]));
    state = reducer(
      state,
      upsertMany([
        { ...first, version: 3, stage: "GENERATING" },
        { ...second, version: 1, status: "PENDING" },
      ]),
    );

    expect(state.order).toEqual([second.id, first.id]);
    expect(state.byId[first.id]?.version).toBe(3);
    expect(state.byId[first.id]?.stage).toBe("GENERATING");
    expect(state.byId[second.id]?.version).toBe(5);
    expect(state.byId[second.id]?.status).toBe("PENDING");
  });

  it("按创建时间倒序插入新任务", () => {
    const older = makeTask({ createdAt: "2026-10-28T09:00:00.000Z" });
    const newer = makeTask({ createdAt: "2026-10-28T11:00:00.000Z", version: 2, status: "PROCESSING" });

    let state = reducer(initialState, upsertTask(older));
    state = reducer(state, upsertTask(newer));

    expect(state.order).toEqual([newer.id, older.id]);
    expect(state.byId[newer.id]?.status).toBe("PROCESSING");
  });

  it("合并更高版本的同一任务", () => {
    const base = makeTask({ version: 3 });
    const updated = { ...base, version: 5, status: "SUCCEEDED" as const, resultUrl: "/api/tasks/x/result" };

    let state = reducer(initialState, upsertTask(base));
    state = reducer(state, upsertTask(updated));

    expect(state.byId[base.id]?.version).toBe(5);
    expect(state.byId[base.id]?.status).toBe("SUCCEEDED");
    expect(state.order).toHaveLength(1);
  });

  it("丢弃晚到的旧版本响应", () => {
    const latest = makeTask({ version: 6, status: "SUCCEEDED" });
    const stale = { ...latest, version: 4, status: "PROCESSING" as const };

    let state = reducer(initialState, upsertTask(latest));
    state = reducer(state, upsertTask(stale));

    expect(state.byId[latest.id]?.version).toBe(6);
    expect(state.byId[latest.id]?.status).toBe("SUCCEEDED");
  });

  it("批量合并时逐条应用版本规则", () => {
    const current = makeTask({ version: 4, status: "PROCESSING" });
    const stale = { ...current, version: 2, status: "PENDING" as const };

    let state = reducer(initialState, upsertTask(current));
    state = reducer(state, upsertMany([stale, { ...current, version: 5, stage: "GENERATING" }]));

    expect(state.byId[current.id]?.version).toBe(5);
    expect(state.byId[current.id]?.stage).toBe("GENERATING");
  });

  it("查询错误只写 sync，不改变任务状态（TC-FE-02 前置）", () => {
    const task = makeTask({ status: "PROCESSING", stage: "GENERATING", version: 4 });
    let state = reducer(initialState, upsertTask(task));
    state = reducer(state, setSync({ id: task.id, state: "error", message: "状态刷新失败" }));

    expect(state.sync[task.id]?.state).toBe("error");
    expect(state.byId[task.id]?.status).toBe("PROCESSING");
    expect(state.byId[task.id]?.stage).toBe("GENERATING");
  });

  it("resetAll 清空任务、顺序与同步状态", () => {
    const task = makeTask();
    let state = reducer(initialState, upsertTask(task));
    state = reducer(state, setSync({ id: task.id, state: "error" }));
    state = reducer(state, resetAll());

    expect(state).toEqual(initialState);
  });

  it("removeTask 同时移除任务与同步状态", () => {
    const task = makeTask();
    let state = reducer(initialState, upsertTask(task));
    state = reducer(state, removeTask(task.id));

    expect(state.byId[task.id]).toBeUndefined();
    expect(state.sync[task.id]).toBeUndefined();
    expect(state.order).toHaveLength(0);
  });
});