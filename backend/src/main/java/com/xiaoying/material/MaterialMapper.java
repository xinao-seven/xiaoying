package com.xiaoying.material;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialMapper {

    @Insert("""
            INSERT INTO materials (id, user_id, original_name, storage_key, mime_type, size_bytes,
                                   width, height, status, created_at)
            VALUES (#{id}, #{userId}, #{originalName}, #{storageKey}, #{mimeType}, #{sizeBytes},
                    #{width}, #{height}, #{status}, #{createdAt})
            """)
    void insert(MaterialRow row);

    @Select("""
            SELECT id, user_id, original_name, storage_key, mime_type, size_bytes,
                   width, height, status, created_at
            FROM materials
            WHERE id = #{id}
            """)
    MaterialRow findById(@Param("id") String id);
}