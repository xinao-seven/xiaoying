package com.xiaoying.pipeline;

/** 阶段内的可预期失败；code 写入 tasks.error_code，message 面向用户。 */
public class StageFailure extends RuntimeException {

    private final String code;

    public StageFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}