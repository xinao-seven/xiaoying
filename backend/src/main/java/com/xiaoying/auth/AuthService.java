package com.xiaoying.auth;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthUser authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        UserRow row = userMapper.findByUsername(username.trim());
        if (row == null || !passwordEncoder.matches(password, row.getPasswordHash())) {
            // 不区分“用户不存在”和“密码错误”，避免枚举账号
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        return new AuthUser(row.getId(), row.getUsername(), row.getDisplayName());
    }
}