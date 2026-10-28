package com.xiaoying.common;

import java.security.SecureRandom;

/**
 * 生成带类型前缀的 ULID 标识：{@code usr_/mat_/tsk_/req_} + 26 位 Crockford Base32，共 30 字符。
 */
public final class Ids {

    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String ulid() {
        long timestamp = System.currentTimeMillis();
        char[] value = new char[26];
        for (int i = 9; i >= 0; i--) {
            value[i] = CROCKFORD[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }
        for (int i = 10; i < 26; i++) {
            value[i] = CROCKFORD[RANDOM.nextInt(32)];
        }
        return new String(value);
    }

    public static String user() {
        return "usr_" + ulid();
    }

    public static String material() {
        return "mat_" + ulid();
    }

    public static String task() {
        return "tsk_" + ulid();
    }

    public static String traceId() {
        return "req_" + ulid();
    }
}