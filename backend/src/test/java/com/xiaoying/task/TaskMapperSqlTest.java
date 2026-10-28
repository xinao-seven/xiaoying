package com.xiaoying.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * 回归：Java 文本块开头不含换行，{@code "SELECT " + COLUMNS + """FROM ..."""} 会把 FROM
 * 直接拼到最后一个列名后。这里直接检查注解里的 SQL 字符串。
 */
class TaskMapperSqlTest {

    @Test
    void selectStatementsKeepSpaceBetweenColumnsAndFrom() {
        for (String name : List.of("findById", "findByIdAndUser", "findByUser", "findActiveByUser")) {
            Method method = Arrays.stream(TaskMapper.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name))
                    .findFirst()
                    .orElseThrow();
            Select select = method.getAnnotation(Select.class);
            assertThat(select).as("missing @Select on %s", name).isNotNull();
            String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").trim();
            assertThat(sql)
                    .as("SQL for %s", name)
                    .contains("template_title FROM tasks t")
                    .doesNotContain("template_titleFROM");
        }
    }
}