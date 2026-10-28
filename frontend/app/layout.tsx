import type { Metadata } from "next";
import "./globals.css";

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
        <div className="shell">
          <header className="topbar">
            <div className="brand">
              <span className="brand-mark" aria-hidden="true">
                影
              </span>
              <span>小影素材站</span>
            </div>
            <span className="topbar-meta">模板 · 上传 · 异步制作</span>
          </header>
          {children}
        </div>
      </body>
    </html>
  );
}