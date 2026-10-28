package com.xiaoying.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    public static final String PIPELINE_EXECUTOR = "pipelineExecutor";

    /**
     * 有界 worker 线程池。默认并发 1（串行队列）；拒绝策略为 AbortPolicy，
     * 由调度器捕获并把任务回退 PENDING，任务不会进入无主状态。
     */
    @Bean(name = PIPELINE_EXECUTOR, destroyMethod = "shutdown")
    public ExecutorService pipelineExecutor(AppProperties appProperties) {
        int concurrency = Math.max(1, appProperties.getPipeline().getConcurrency());
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "pipeline-worker-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }
}