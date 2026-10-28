package com.xiaoying.pipeline;

import com.xiaoying.task.TaskEventRow;
import com.xiaoying.task.TaskMapper;
import com.xiaoying.task.TaskRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动对账：上次进程退出时残留的 PROCESSING 任务统一标记为 FAILED/SERVER_RESTART。
 * 题目不要求断点续跑；PENDING 不受影响，调度器会重新拾起。
 */
@Component
public class RestartReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RestartReconciler.class);
    private static final String MESSAGE = "服务重启导致处理中断";

    private final TaskMapper taskMapper;
    private final Clock clock;

    public RestartReconciler(TaskMapper taskMapper, Clock clock) {
        this.taskMapper = taskMapper;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        int reconciled = reconcile();
        if (reconciled > 0) {
            log.warn("reconciled {} interrupted PROCESSING tasks as FAILED/SERVER_RESTART", reconciled);
        }
    }

    /** 供测试直接调用；返回被收口的任务数。 */
    public int reconcile() {
        List<TaskRow> processing = taskMapper.findProcessing();
        int reconciled = 0;
        for (TaskRow row : processing) {
            Instant now = now();
            if (taskMapper.markFailedOnRestart(row.getId(), MESSAGE, now) == 1) {
                insertEventQuietly(row.getId(), row.getStage(), now);
                reconciled++;
            }
        }
        return reconciled;
    }

    private void insertEventQuietly(String taskId, String stage, Instant now) {
        try {
            TaskEventRow event = new TaskEventRow();
            event.setTaskId(taskId);
            event.setStage(stage == null ? Stage.MATERIAL_CHECK.name() : stage);
            event.setEventType("STAGE_FAILED");
            event.setMessage(MESSAGE);
            event.setCreatedAt(now);
            taskMapper.insertEvent(event);
        } catch (RuntimeException ex) {
            log.warn("failed to write restart event taskId={}", taskId, ex);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}