package com.xiaoying.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id, username, display_name, password_hash, created_at
            FROM users
            WHERE username = #{username}
            """)
    UserRow findByUsername(@Param("username") String username);

    @Select("""
            SELECT id, username, display_name, password_hash, created_at
            FROM users
            WHERE id = #{id}
            """)
    UserRow findById(@Param("id") String id);
}