package com.xiaoying.template;

import java.util.List;

public record TemplateDto(
        String id,
        String title,
        String category,
        String coverUrl,
        String description,
        int requiredImageCount,
        List<String> acceptedMimeTypes,
        long maxFileSizeBytes) {
}