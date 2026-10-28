package com.xiaoying.auth;

import com.xiaoying.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 会话签发、校验与过期清理。令牌仅在 Cookie 中下发，库里只存 SHA-256。
 */
@Service
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionMapper sessionMapper;
    private final AppProperties appProperties;
    private final Clock clock;

    public SessionService(SessionMapper sessionMapper, AppProperties appProperties, Clock clock) {
        this.sessionMapper = sessionMapper;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public String issue(String userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofDays(appProperties.getSession().getTtlDays()));
        sessionMapper.insert(hashToken(token), userId, expiresAt, now);
        return token;
    }

    public Optional<AuthUser> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        UserRow row = sessionMapper.findUserByTokenHash(hashToken(token), clock.instant());
        return Optional.ofNullable(row)
                .map(user -> new AuthUser(user.getId(), user.getUsername(), user.getDisplayName()));
    }

    public void delete(String token) {
        if (token != null && !token.isBlank()) {
            sessionMapper.deleteByTokenHash(hashToken(token));
        }
    }

    @Scheduled(fixedDelay = 3_600_000L)
    public int deleteExpired() {
        return sessionMapper.deleteExpired(clock.instant());
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}