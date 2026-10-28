package com.xiaoying.task;

import java.time.Instant;

/** 对前端暴露的任务视图；不包含 user_id、params_json、planned_fail_stage 等内部字段。 */
public record TaskDto(
        String id,
        String templateId,
        String templateTitle,
        String status,
        String stage,
        String failedStage,
        String errorCode,
        String errorMessage,
        String resultUrl,
        int version,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public static TaskDto from(TaskRow row) {
        return new TaskDto(
                row.getId(),
                row.getTemplateId(),
                row.getTemplateTitle(),
                row.getStatus(),
                row.getStage(),
                row.getFailedStage(),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getResultUrl(),
                row.getVersion() == null ? 0 : row.getVersion(),
                row.getCreatedAt(),
                row.getStartedAt(),
                row.getFinishedAt());
    }
}