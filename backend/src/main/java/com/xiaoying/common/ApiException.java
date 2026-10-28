package com.xiaoying.common;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }
}