package com.xiaoying.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 统一错误体：{@code code, message, fieldErrors?, traceId}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors,
        String traceId) {

    public static ApiError of(ErrorCode code, String message, Map<String, String> fieldErrors, String traceId) {
        return new ApiError(code.name(), message, fieldErrors, traceId);
    }
}