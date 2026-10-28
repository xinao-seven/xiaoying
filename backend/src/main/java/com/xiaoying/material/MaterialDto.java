package com.xiaoying.material;

import java.time.Instant;

public record MaterialDto(
        String id,
        String url,
        String mimeType,
        long sizeBytes,
        int width,
        int height,
        Instant createdAt) {
}