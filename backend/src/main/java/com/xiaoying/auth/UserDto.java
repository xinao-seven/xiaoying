package com.xiaoying.auth;

public record UserDto(String id, String username, String displayName) {

    public static UserDto from(AuthUser user) {
        return new UserDto(user.id(), user.username(), user.displayName());
    }
}