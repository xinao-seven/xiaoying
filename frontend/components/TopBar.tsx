"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { logout, me } from "@/lib/api/endpoints";
import type { User } from "@/lib/api/types";
import { isAbortError } from "@/lib/api/client";
import { useAppDispatch } from "@/store";
import { resetAll } from "@/store/tasksSlice";

export function TopBar() {
  const pathname = usePathname();
  const router = useRouter();
  const dispatch = useAppDispatch();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [userError, setUserError] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const isLoginPage = pathname === "/login";

  useEffect(() => {
    if (isLoginPage) {
      setUser(null);
      setUserError(false);
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    me(controller.signal)
      .then((current) => {
        setUser(current);
        setUserError(false);
      })
      .catch((error: unknown) => {
        // 401 会由全局处理器跳转登录；其他错误只说明服务不可用，保留最后一次已知用户。
        if (!isAbortError(error)) setUserError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [isLoginPage, pathname]);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      // 会话已失效也继续清理本地状态。
    } finally {
      dispatch(resetAll());
      setUser(null);
      setLoggingOut(false);
      router.replace("/login");
      router.refresh();
    }
  }

  return (
    <header className="topbar">
      <Link href="/" className="brand" aria-label="小影素材站首页">
        <span className="brand-mark" aria-hidden="true">
          影
        </span>
        <span>小影素材站</span>
      </Link>

      {!isLoginPage && (
        <nav className="topnav" aria-label="主导航">
          <Link href="/" aria-current={pathname === "/" ? "page" : undefined}>
            模板
          </Link>
          <Link href="/history" aria-current={pathname === "/history" ? "page" : undefined}>
            历史
          </Link>
        </nav>
      )}

      <div className="topbar-user">
        {isLoginPage || loading ? (
          <span className="topbar-meta">{isLoginPage ? "登录后开始制作" : "…"}</span>
        ) : user ? (
          <>
            <span className="topbar-user-name">{user.displayName}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={handleLogout} disabled={loggingOut}>
              {loggingOut ? "登出中…" : "登出"}
            </button>
          </>
        ) : userError ? (
          <span className="topbar-meta">服务暂不可用</span>
        ) : (
          <Link href="/login" className="btn btn-ghost btn-sm">
            去登录
          </Link>
        )}
      </div>
    </header>
  );
}