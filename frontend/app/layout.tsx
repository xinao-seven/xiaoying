import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "@/components/Providers";
import { TopBar } from "@/components/TopBar";
import { TaskSync } from "@/features/tasks/TaskSync";

export const metadata: Metadata = {
  title: "小影素材站",
  description: "浏览模板、上传素材、异步制作与结果预览",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>
        <Providers>
          <div className="shell">
            <TopBar />
            <TaskSync>{children}</TaskSync>
          </div>
        </Providers>
      </body>
    </html>
  );
}