"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { isAbortError, describeError } from "@/lib/api/client";
import { listCategories, listTemplates } from "@/lib/api/endpoints";
import type { Category, Template } from "@/lib/api/types";
import { formatBytes } from "@/lib/format";

type ListState = "loading" | "ready" | "error";

const SKELETON_COUNT = 6;

function TemplateCard({ template }: { template: Template }) {
  return (
    <Link href={`/templates/${template.id}`} className="template-card">
      <span className="card-media">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={template.coverUrl} alt="" loading="lazy" />
      </span>
      <span className="card-body">
        <span className="card-meta">
          <span className="badge badge-soft">{template.category}</span>
          <span className="card-count">{template.requiredImageCount} 张素材</span>
        </span>
        <span className="card-title">{template.title}</span>
        <span className="card-desc">{template.description ?? "暂无描述"}</span>
        <span className="card-foot">单张不超过 {formatBytes(template.maxFileSizeBytes)}</span>
      </span>
    </Link>
  );
}

function SkeletonCard() {
  return (
    <div className="template-card skeleton-card" aria-hidden="true">
      <span className="card-media skeleton" />
      <span className="card-body">
        <span className="skeleton skeleton-line skeleton-line-sm" />
        <span className="skeleton skeleton-line" />
        <span className="skeleton skeleton-line skeleton-line-md" />
      </span>
    </div>
  );
}

export function TemplateList() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoryError, setCategoryError] = useState<string | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [listState, setListState] = useState<ListState>("loading");
  const [listError, setListError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const requestSeq = useRef(0);

  useEffect(() => {
    const controller = new AbortController();
    setCategoryError(null);
    listCategories(controller.signal)
      .then((response) => setCategories(response.items))
      .catch((error: unknown) => {
        if (isAbortError(error)) return;
        setCategoryError(describeError(error));
      });
    return () => controller.abort();
  }, [reloadKey]);

  useEffect(() => {
    const seq = requestSeq.current + 1;
    requestSeq.current = seq;
    const controller = new AbortController();

    setListState("loading");
    setListError(null);

    listTemplates(selectedCategory, controller.signal)
      .then((response) => {
        // 请求序号 + abort 双保险：快速切换分类时旧响应直接丢弃。
        if (requestSeq.current !== seq) return;
        setTemplates(response.items);
        setListState("ready");
      })
      .catch((error: unknown) => {
        if (requestSeq.current !== seq || isAbortError(error)) return;
        setTemplates([]);
        setListError(describeError(error));
        setListState("error");
      });

    return () => controller.abort();
  }, [selectedCategory, reloadKey]);

  const totalCount = categories.reduce((sum, category) => sum + category.count, 0);

  return (
    <main className="page">
      <section className="page-head" aria-labelledby="catalog-title">
        <p className="eyebrow">模板库</p>
        <h1 id="catalog-title">选择一个模板开始制作</h1>
        <p className="page-lede">
          按分类浏览模板，进入详情上传素材。提交后由 Java 后台完成素材检查、模拟生成与结果整理。
        </p>
      </section>

      <section className="catalog" aria-labelledby="category-title">
        <h2 id="category-title" className="sr-only">
          模板分类
        </h2>

        {categoryError ? (
          <p className="inline-alert" role="alert">
            分类加载失败：{categoryError}
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setReloadKey((key) => key + 1)}>
              重试
            </button>
          </p>
        ) : categories.length === 0 ? (
          <div className="category-bar" aria-hidden="true">
            <span className="chip skeleton-chip" />
            <span className="chip skeleton-chip" />
            <span className="chip skeleton-chip" />
          </div>
        ) : (
          <div className="category-bar" role="group" aria-label="按分类筛选模板">
            <button
              type="button"
              className="chip"
              aria-pressed={selectedCategory === null}
              onClick={() => setSelectedCategory(null)}
            >
              全部
              <span className="chip-count">{totalCount}</span>
            </button>
            {categories.map((category) => (
              <button
                key={category.name}
                type="button"
                className="chip"
                aria-pressed={selectedCategory === category.name}
                onClick={() => setSelectedCategory(category.name)}
              >
                {category.name}
                <span className="chip-count">{category.count}</span>
              </button>
            ))}
          </div>
        )}

        <p className="sr-only" role="status">
          {listState === "loading" ? "正在加载模板" : listState === "ready" ? `共 ${templates.length} 个模板` : "模板加载失败"}
        </p>

        {listState === "loading" && (
          <div className="template-grid" aria-busy="true" aria-label="模板加载中">
            {Array.from({ length: SKELETON_COUNT }, (_, index) => (
              <SkeletonCard key={index} />
            ))}
          </div>
        )}

        {listState === "error" && (
          <div className="state-panel" role="alert">
            <h2>模板加载失败</h2>
            <p>{listError}</p>
            <div className="state-actions">
              <button type="button" className="btn btn-primary" onClick={() => setReloadKey((key) => key + 1)}>
                重新加载
              </button>
            </div>
          </div>
        )}

        {listState === "ready" && templates.length === 0 && (
          <div className="state-panel">
            <h2>该分类下暂时没有模板</h2>
            <p>换一个分类看看，或查看全部模板。</p>
            <div className="state-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setSelectedCategory(null)}>
                查看全部模板
              </button>
            </div>
          </div>
        )}

        {listState === "ready" && templates.length > 0 && (
          <div className="template-grid">
            {templates.map((template) => (
              <TemplateCard key={template.id} template={template} />
            ))}
          </div>
        )}
      </section>
    </main>
  );
}