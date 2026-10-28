package com.xiaoying.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoying.support.InMemoryTaskMapper;
import com.xiaoying.task.TaskRow;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TC-BE-15：条件更新让两个并发认领只有一个成功。 */
class TaskClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final String TASK_ID = "tsk_claim";

    private InMemoryTaskMapper taskMapper;
    private TaskClaimService claimService;

    @BeforeEach
    void setUp() {
        taskMapper = new InMemoryTaskMapper();
        TaskRow row = new TaskRow();
        row.setId(TASK_ID);
        row.setUserId("usr_demo");
        row.setTemplateId("tpl");
        row.setStatus("PENDING");
        row.setVersion(1);
        row.setCreatedAt(NOW);
        row.setUpdatedAt(NOW);
        taskMapper.tasks.put(TASK_ID, row);
        claimService = new TaskClaimService(taskMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void firstClaimWinsAndSecondLoses() {
        assertThat(claimService.claim(TASK_ID)).isTrue();
        assertThat(claimService.claim(TASK_ID)).isFalse();

        TaskRow row = taskMapper.tasks.get(TASK_ID);
        assertThat(row.getStatus()).isEqualTo("PROCESSING");
        assertThat(row.getStage()).isEqualTo("MATERIAL_CHECK");
        assertThat(row.getStartedAt()).isEqualTo(NOW);
        assertThat(row.getVersion()).isEqualTo(2);
    }

    @Test
    void concurrentClaimsOnlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return claimService.claim(TASK_ID);
                }));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(taskMapper.tasks.get(TASK_ID).getStatus()).isEqualTo("PROCESSING");
            assertThat(taskMapper.tasks.get(TASK_ID).getVersion()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }
}