package com.xiaoying.pipeline;

import com.xiaoying.task.TaskMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

/** 原子认领：条件更新影响 1 行才算认领成功，保证同一任务不会被两个 worker 处理。 */
@Service
public class TaskClaimService {

    private final TaskMapper taskMapper;
    private final Clock clock;

    public TaskClaimService(TaskMapper taskMapper, Clock clock) {
        this.taskMapper = taskMapper;
        this.clock = clock;
    }

    public boolean claim(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        return taskMapper.claim(taskId, now()) == 1;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}