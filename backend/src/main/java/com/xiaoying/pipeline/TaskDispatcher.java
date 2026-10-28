package com.xiaoying.pipeline;

import com.xiaoying.config.ExecutorConfig;
import com.xiaoying.task.TaskMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据库队列调度器：轮询 PENDING → 原子认领 → 提交线程池。
 *
 * <p>用信号量把“已认领但未完成”的任务数限制在并发度内，
 * 因此串行（默认）时其余任务保持 PENDING，符合需求对排队状态的预期。
 */
@Component
public class TaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);
    private static final int BATCH_SIZE = 20;

    private final TaskMapper taskMapper;
    private final TaskClaimService taskClaimService;
    private final TaskPipelineRunner taskPipelineRunner;
    private final ExecutorService executor;
    private final Semaphore permits;
    private final Clock clock;

    public TaskDispatcher(TaskMapper taskMapper,
                          TaskClaimService taskClaimService,
                          TaskPipelineRunner taskPipelineRunner,
                          @Qualifier(ExecutorConfig.PIPELINE_EXECUTOR) ExecutorService executor,
                          com.xiaoying.config.AppProperties appProperties,
                          Clock clock) {
        this.taskMapper = taskMapper;
        this.taskClaimService = taskClaimService;
        this.taskPipelineRunner = taskPipelineRunner;
        this.executor = executor;
        this.permits = new Semaphore(Math.max(1, appProperties.getPipeline().getConcurrency()));
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.pipeline.poll-interval-ms:1000}")
    public void dispatch() {
        try {
            dispatchOnce();
        } catch (RuntimeException ex) {
            log.error("dispatch tick failed", ex);
        }
    }

    /** 供测试直接调用；返回本次成功提交的任务数。 */
    public int dispatchOnce() {
        List<String> pendingIds = taskMapper.findPendingIds(BATCH_SIZE);
        int submitted = 0;
        for (String taskId : pendingIds) {
            if (!permits.tryAcquire()) {
                break;
            }
            boolean claimed = false;
            try {
                claimed = taskClaimService.claim(taskId);
                if (!claimed) {
                    permits.release();
                    continue;
                }
                executor.execute(() -> {
                    try {
                        taskPipelineRunner.process(taskId);
                    } catch (Throwable ex) {
                        // runner 自身已兜底；这里只防止线程池吞掉异常
                        log.error("pipeline worker crashed taskId={}", taskId, ex);
                    } finally {
                        permits.release();
                    }
                });
                submitted++;
            } catch (RejectedExecutionException ex) {
                permits.release();
                if (claimed) {
                    taskMapper.releaseClaim(taskId, now());
                }
                log.warn("pipeline queue rejected taskId={}, released to PENDING", taskId);
            } catch (RuntimeException ex) {
                permits.release();
                if (claimed) {
                    try {
                        taskMapper.releaseClaim(taskId, now());
                    } catch (RuntimeException releaseFailure) {
                        log.error("failed to release claim taskId={}", taskId, releaseFailure);
                    }
                }
                log.error("dispatch failed taskId={}", taskId, ex);
            }
        }
        return submitted;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}