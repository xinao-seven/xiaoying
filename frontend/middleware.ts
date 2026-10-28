import { NextResponse, type NextRequest } from "next/server";

const SESSION_COOKIE = "XIAOYING_SESSION";

/**
 * 只做粗判：没有会话 Cookie 就跳登录。
 * Cookie 是否有效由后端校验；这里不能把 /api 拦掉，否则登录请求本身会被重定向。
 */
export function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (pathname === "/login" || request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.next();
  }

  const loginUrl = new URL("/login", request.url);
  const next = `${pathname}${search}`;
  if (next !== "/") {
    loginUrl.searchParams.set("next", next);
  }
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/((?!api|_next|favicon.ico|demo).*)"],
};