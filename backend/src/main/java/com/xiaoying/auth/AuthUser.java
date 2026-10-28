package com.xiaoying.auth;

/** 通过会话解析出的当前用户；不包含密码哈希。 */
public record AuthUser(String id, String username, String displayName) {
}