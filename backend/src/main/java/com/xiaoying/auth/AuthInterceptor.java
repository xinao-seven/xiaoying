package com.xiaoying.auth;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 保护 /api/**（登录与模板接口放行）。未携带有效会话时返回 401 JSON。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;

    public AuthInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = readToken(request);
        Optional<AuthUser> user = sessionService.resolve(token);
        if (user.isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        request.setAttribute(CurrentUserArgumentResolver.CURRENT_USER_ATTRIBUTE, user.get());
        return true;
    }

    static String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CookieFactory.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}