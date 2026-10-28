"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError, describeError, isAbortError } from "@/lib/api/client";
import { getTemplate } from "@/lib/api/endpoints";
import type { Template } from "@/lib/api/types";
import { TemplateWorkbench } from "@/features/screens/TemplateWorkbench";

type DetailState = "loading" | "ready" | "error" | "not-found";

export function TemplateDetail({ templateId }: { templateId: string }) {
  const [state, setState] = useState<DetailState>("loading");
  const [template, setTemplate] = useState<Template | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setState("loading");
    setError(null);

    getTemplate(templateId, controller.signal)
      .then((response) => {
        setTemplate(response);
        setState("ready");
      })
      .catch((caught: unknown) => {
        if (isAbortError(caught)) return;
        if (caught instanceof ApiError && caught.status === 404) {
          setState("not-found");
          return;
        }
        setError(describeError(caught));
        setState("error");
      });

    return () => controller.abort();
  }, [templateId, reloadKey]);

  if (state === "loading") {
    return (
      <main className="page detail-page">
        <p className="sr-only" role="status">
          正在加载模板
        </p>
        <div className="detail-grid" aria-busy="true">
          <div className="detail-main">
            <div className="detail-hero skeleton-hero">
              <span className="skeleton detail-cover skeleton" />
              <div className="detail-intro">
                <span className="skeleton skeleton-line skeleton-line-sm" />
                <span className="skeleton skeleton-line skeleton-line-lg" />
                <span className="skeleton skeleton-line" />
                <span className="skeleton skeleton-line skeleton-line-md" />
              </div>
            </div>
          </div>
          <aside className="detail-side">
            <span className="skeleton skeleton-line" />
            <span className="skeleton skeleton-line skeleton-line-md" />
          </aside>
        </div>
      </main>
    );
  }

  if (state === "not-found") {
    return (
      <main className="page">
        <Link href="/" className="back-link">
          ← 返回模板列表
        </Link>
        <div className="state-panel" role="alert">
          <h1>模板不存在</h1>
          <p>该模板可能已下架，或链接不正确。</p>
          <div className="state-actions">
            <Link href="/" className="btn btn-primary">
              返回模板列表
            </Link>
          </div>
        </div>
      </main>
    );
  }

  if (state === "error" || !template) {
    return (
      <main className="page">
        <Link href="/" className="back-link">
          ← 返回模板列表
        </Link>
        <div className="state-panel" role="alert">
          <h1>模板加载失败</h1>
          <p>{error}</p>
          <div className="state-actions">
            <button type="button" className="btn btn-primary" onClick={() => setReloadKey((key) => key + 1)}>
              重新加载
            </button>
            <Link href="/" className="btn btn-ghost">
              返回列表
            </Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="page detail-page">
      <Link href="/" className="back-link">
        ← 返回模板列表
      </Link>
      <TemplateWorkbench template={template} />
    </main>
  );
}