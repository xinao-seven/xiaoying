package com.xiaoying.support;

import com.xiaoying.template.CategoryCountRow;
import com.xiaoying.template.TemplateMapper;
import com.xiaoying.template.TemplateRow;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryTemplateMapper implements TemplateMapper {

    public final Map<String, TemplateRow> rows = new LinkedHashMap<>();

    public static TemplateRow template(String id, String title, int requiredImageCount,
                                       long maxFileSizeBytes, boolean enabled) {
        TemplateRow row = new TemplateRow();
        row.setId(id);
        row.setTitle(title);
        row.setCategory("创意图片");
        row.setCoverUrl("/demo/" + id + ".png");
        row.setDescription(title);
        row.setRequiredImageCount(requiredImageCount);
        row.setAcceptedMimeTypes("image/jpeg,image/png");
        row.setMaxFileSizeBytes(maxFileSizeBytes);
        row.setEnabled(enabled);
        row.setSortOrder(10);
        return row;
    }

    @Override
    public List<TemplateRow> findEnabled() {
        return rows.values().stream()
                .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                .sorted(Comparator.comparing(TemplateRow::getSortOrder))
                .toList();
    }

    @Override
    public List<TemplateRow> findEnabledByCategory(String category) {
        return findEnabled().stream().filter(row -> category.equals(row.getCategory())).toList();
    }

    @Override
    public TemplateRow findEnabledById(String id) {
        TemplateRow row = rows.get(id);
        return row != null && Boolean.TRUE.equals(row.getEnabled()) ? row : null;
    }

    @Override
    public TemplateRow findById(String id) {
        return rows.get(id);
    }

    @Override
    public List<CategoryCountRow> countByCategory() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TemplateRow row : findEnabled()) {
            counts.merge(row.getCategory(), 1, Integer::sum);
        }
        return counts.entrySet().stream().map(entry -> {
            CategoryCountRow countRow = new CategoryCountRow();
            countRow.setCategory(entry.getKey());
            countRow.setCnt(entry.getValue().longValue());
            return countRow;
        }).toList();
    }
}