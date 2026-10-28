package com.xiaoying.auth;

import java.time.Instant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SessionMapper {

    @Insert("""
            INSERT INTO sessions (token_hash, user_id, expires_at, created_at)
            VALUES (#{tokenHash}, #{userId}, #{expiresAt}, #{createdAt})
            """)
    void insert(@Param("tokenHash") String tokenHash,
                @Param("userId") String userId,
                @Param("expiresAt") Instant expiresAt,
                @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT u.id, u.username, u.display_name
            FROM sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.token_hash = #{tokenHash} AND s.expires_at > #{now}
            """)
    UserRow findUserByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Delete("DELETE FROM sessions WHERE token_hash = #{tokenHash}")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Delete("DELETE FROM sessions WHERE expires_at <= #{now}")
    int deleteExpired(@Param("now") Instant now);
}