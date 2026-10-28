package com.xiaoying.template;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {

    private final TemplateMapper templateMapper;

    public TemplateService(TemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    public List<TemplateDto> list(String category) {
        List<TemplateRow> rows = (category == null || category.isBlank())
                ? templateMapper.findEnabled()
                : templateMapper.findEnabledByCategory(category.trim());
        return rows.stream().map(TemplateService::toDto).toList();
    }

    public TemplateDto get(String id) {
        TemplateRow row = templateMapper.findEnabledById(id);
        if (row == null) {
            throw new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在或已下架");
        }
        return toDto(row);
    }

    public List<CategoryDto> categories() {
        return templateMapper.countByCategory().stream()
                .map(row -> new CategoryDto(row.getCategory(), row.getCnt() == null ? 0 : row.getCnt()))
                .toList();
    }

    static TemplateDto toDto(TemplateRow row) {
        return new TemplateDto(
                row.getId(),
                row.getTitle(),
                row.getCategory(),
                row.getCoverUrl(),
                row.getDescription(),
                row.getRequiredImageCount() == null ? 0 : row.getRequiredImageCount(),
                splitMimeTypes(row.getAcceptedMimeTypes()),
                row.getMaxFileSizeBytes() == null ? 0 : row.getMaxFileSizeBytes());
    }

    private static List<String> splitMimeTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}