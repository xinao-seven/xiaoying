import type { Metadata } from "next";
import { TaskHistory } from "@/features/tasks/TaskHistory";

export const metadata: Metadata = {
  title: "历史任务 · 小影素材站",
};

export default function HistoryPage() {
  return <TaskHistory />;
}