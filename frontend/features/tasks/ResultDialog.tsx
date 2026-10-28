"use client";

import { useEffect, useRef, useState } from "react";

interface ResultDialogProps {
  open: boolean;
  title: string;
  imageUrl: string;
  onClose: () => void;
}

/**
 * 结果图预览：用轻量自绘对话框而不是原生 <dialog>，
 * 以便统一处理 Esc、遮罩点击、焦点返回与滚动锁定（原生 showModal 在 jsdom 中不可用）。
 */
export function ResultDialog({ open, title, imageUrl, onClose }: ResultDialogProps) {
  const closeRef = useRef<HTMLButtonElement | null>(null);
  const restoreFocusRef = useRef<HTMLElement | null>(null);
  const [broken, setBroken] = useState(false);

  useEffect(() => {
    if (!open) return;

    setBroken(false);
    restoreFocusRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
    };
    document.addEventListener("keydown", handleKeyDown);

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      restoreFocusRef.current?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="dialog-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="result-dialog" role="dialog" aria-modal="true" aria-label={`${title}制作结果预览`}>
        <div className="dialog-head">
          <h2>{title}</h2>
          <button ref={closeRef} type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
            关闭
          </button>
        </div>
        {broken ? (
          <p className="result-placeholder" role="status">
            结果图暂时无法加载，请稍后刷新页面重试。
          </p>
        ) : (
          // 结果图受会话 Cookie 保护，必须直接请求后端，不能经过 next/image 优化。
          // eslint-disable-next-line @next/next/no-img-element
          <img
            className="dialog-image"
            src={imageUrl}
            alt={`${title}的制作结果大图`}
            onError={() => setBroken(true)}
          />
        )}
      </div>
    </div>
  );
}