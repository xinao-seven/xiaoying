package com.xiaoying.auth;

import com.xiaoying.config.AppProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieFactory {

    public static final String COOKIE_NAME = "XIAOYING_SESSION";

    private final AppProperties appProperties;

    public CookieFactory(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public ResponseCookie create(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(appProperties.getSession().getTtlDays()))
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}