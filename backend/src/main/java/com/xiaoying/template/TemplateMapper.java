package com.xiaoying.template;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TemplateMapper {

    String COLUMNS = """
            id, title, category, cover_url, description, required_image_count,
            accepted_mime_types, max_file_size_bytes, enabled, sort_order, created_at
            """;

    @Select("SELECT " + COLUMNS + " FROM templates WHERE enabled = 1 ORDER BY sort_order, id")
    List<TemplateRow> findEnabled();

    @Select("SELECT " + COLUMNS + " FROM templates WHERE enabled = 1 AND category = #{category} "
            + "ORDER BY sort_order, id")
    List<TemplateRow> findEnabledByCategory(@Param("category") String category);

    @Select("SELECT " + COLUMNS + " FROM templates WHERE enabled = 1 AND id = #{id}")
    TemplateRow findEnabledById(@Param("id") String id);

    @Select("""
            SELECT category, COUNT(*) AS cnt
            FROM templates
            WHERE enabled = 1
            GROUP BY category
            ORDER BY MIN(sort_order), category
            """)
    List<CategoryCountRow> countByCategory();
}