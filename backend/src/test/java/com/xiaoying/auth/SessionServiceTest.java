package com.xiaoying.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoying.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 覆盖会话签发、令牌哈希、过期与删除；不依赖数据库。 */
class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private final FakeSessionMapper mapper = new FakeSessionMapper();
    private final AppProperties properties = new AppProperties();
    private final SessionService service = new SessionService(mapper, properties,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issuesTokenAndStoresOnlyHash() {
        String token = service.issue("usr_1");

        assertThat(token).hasSize(43);
        assertThat(mapper.sessions).containsOnlyKeys(SessionService.hashToken(token));
        assertThat(mapper.sessions).doesNotContainKey(token);
        assertThat(mapper.sessions.get(SessionService.hashToken(token)).userId()).isEqualTo("usr_1");
        assertThat(mapper.sessions.get(SessionService.hashToken(token)).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void resolvesActiveSessionToUser() {
        mapper.users.put("usr_1", user("usr_1", "demo"));
        String token = service.issue("usr_1");

        assertThat(service.resolve(token))
                .hasValueSatisfying(user -> {
                    assertThat(user.id()).isEqualTo("usr_1");
                    assertThat(user.username()).isEqualTo("demo");
                });
    }

    @Test
    void ignoresUnknownBlankAndExpiredTokens() {
        String token = service.issue("usr_1");
        assertThat(service.resolve("unknown")).isEmpty();
        assertThat(service.resolve(" ")).isEmpty();
        assertThat(service.resolve(null)).isEmpty();

        SessionService later = new SessionService(mapper, properties,
                Clock.fixed(NOW.plus(Duration.ofDays(8)), ZoneOffset.UTC));
        assertThat(later.resolve(token)).isEmpty();
    }

    @Test
    void deletesSingleSessionAndExpiredSessions() {
        String token = service.issue("usr_1");
        service.delete(token);
        assertThat(mapper.sessions).isEmpty();

        service.issue("usr_1");
        SessionService later = new SessionService(mapper, properties,
                Clock.fixed(NOW.plus(Duration.ofDays(8)), ZoneOffset.UTC));
        assertThat(later.deleteExpired()).isEqualTo(1);
        assertThat(mapper.sessions).isEmpty();
    }

    private static UserRow user(String id, String username) {
        UserRow row = new UserRow();
        row.setId(id);
        row.setUsername(username);
        row.setDisplayName(username);
        return row;
    }

    /** 内存 fake：只实现 SessionService 用到的方法。 */
    static final class FakeSessionMapper implements SessionMapper {

        record Session(String userId, Instant expiresAt) {
        }

        final Map<String, Session> sessions = new HashMap<>();
        final Map<String, UserRow> users = new HashMap<>();

        @Override
        public void insert(String tokenHash, String userId, Instant expiresAt, Instant createdAt) {
            sessions.put(tokenHash, new Session(userId, expiresAt));
        }

        @Override
        public UserRow findUserByTokenHash(String tokenHash, Instant now) {
            Session session = sessions.get(tokenHash);
            if (session == null || !session.expiresAt().isAfter(now)) {
                return null;
            }
            return users.get(session.userId());
        }

        @Override
        public int deleteByTokenHash(String tokenHash) {
            return sessions.remove(tokenHash) == null ? 0 : 1;
        }

        @Override
        public int deleteExpired(Instant now) {
            int before = sessions.size();
            sessions.values().removeIf(session -> !session.expiresAt().isAfter(now));
            return before - sessions.size();
        }
    }
}