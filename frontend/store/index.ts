import { configureStore } from "@reduxjs/toolkit";
import { useDispatch, useSelector, useStore } from "react-redux";
import tasksReducer, { type TasksState } from "./tasksSlice";

export function makeStore() {
  return configureStore({
    reducer: {
      tasks: tasksReducer,
    },
  });
}

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];

export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
export const useAppStore = useStore.withTypes<AppStore>();

export function selectOrderedTasks(state: { tasks: TasksState }) {
  return state.tasks.order
    .map((id) => state.tasks.byId[id])
    .filter((task): task is NonNullable<typeof task> => Boolean(task));
}

export function selectActiveTaskIds(state: { tasks: TasksState }) {
  return selectOrderedTasks(state)
    .filter((task) => task.status === "PENDING" || task.status === "PROCESSING")
    .map((task) => task.id);
}