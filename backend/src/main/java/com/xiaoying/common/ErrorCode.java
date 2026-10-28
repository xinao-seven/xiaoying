package com.xiaoying.common;

import org.springframework.http.HttpStatus;

/**
 * 稳定错误码。对外响应中的 code 字段只取这里的枚举名。
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND),
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND),
    MATERIAL_FORBIDDEN(HttpStatus.NOT_FOUND),
    MATERIAL_MIME_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    MATERIAL_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    MATERIAL_UNREADABLE(HttpStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    INVALID_PARAMS(HttpStatus.BAD_REQUEST),
    QUEUE_REJECTED(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}