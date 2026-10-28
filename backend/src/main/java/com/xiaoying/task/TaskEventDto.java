package com.xiaoying.task;

import java.time.Instant;

public record TaskEventDto(
        long id,
        String stage,
        String eventType,
        String message,
        Instant createdAt) {

    public static TaskEventDto from(TaskEventRow row) {
        return new TaskEventDto(
                row.getId() == null ? 0 : row.getId(),
                row.getStage(),
                row.getEventType(),
                row.getMessage(),
                row.getCreatedAt());
    }
}