package com.xiaoying.pipeline;

/** 阶段等待抽象；测试注入 fake，避免真实 sleep。 */
public interface Sleeper {

    void sleep(long millis) throws InterruptedException;
}