package com.xiaoying.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoying.support.InMemoryTaskMapper;
import com.xiaoying.task.TaskEventRow;
import com.xiaoying.task.TaskRow;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TC-BE-21/22：残留 PROCESSING 收口为 FAILED/SERVER_RESTART；PENDING 可重新拾起。 */
class RestartReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private InMemoryTaskMapper taskMapper;
    private RestartReconciler reconciler;

    @BeforeEach
    void setUp() {
        taskMapper = new InMemoryTaskMapper();
        reconciler = new RestartReconciler(taskMapper, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void marksProcessingTaskFailedAndKeepsPendingClaimable() {
        seed("tsk_processing", "PROCESSING", "GENERATING", 4);
        seed("tsk_pending", "PENDING", null, 1);

        int reconciled = reconciler.reconcile();

        assertThat(reconciled).isEqualTo(1);

        TaskRow failed = taskMapper.tasks.get("tsk_processing");
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailedStage()).isEqualTo("GENERATING");
        assertThat(failed.getErrorCode()).isEqualTo("SERVER_RESTART");
        assertThat(failed.getErrorMessage()).isNotBlank();
        assertThat(failed.getFinishedAt()).isEqualTo(NOW);
        assertThat(failed.getVersion()).isEqualTo(5);

        List<TaskEventRow> events = taskMapper.eventsFor("tsk_processing");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("STAGE_FAILED");
        assertThat(events.get(0).getStage()).isEqualTo("GENERATING");

        TaskRow pending = taskMapper.tasks.get("tsk_pending");
        assertThat(pending.getStatus()).isEqualTo("PENDING");
        assertThat(pending.getVersion()).isEqualTo(1);

        TaskClaimService claimService = new TaskClaimService(taskMapper, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(claimService.claim("tsk_pending")).isTrue();
        assertThat(taskMapper.tasks.get("tsk_pending").getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void reconcileIsIdempotent() {
        seed("tsk_processing", "PROCESSING", "ASSEMBLING", 3);

        assertThat(reconciler.reconcile()).isEqualTo(1);
        assertThat(reconciler.reconcile()).isZero();
        assertThat(taskMapper.eventsFor("tsk_processing")).hasSize(1);
    }

    private void seed(String id, String status, String stage, int version) {
        TaskRow row = new TaskRow();
        row.setId(id);
        row.setUserId("usr_demo");
        row.setTemplateId("tpl");
        row.setStatus(status);
        row.setStage(stage);
        row.setVersion(version);
        row.setCreatedAt(NOW);
        row.setUpdatedAt(NOW);
        if ("PROCESSING".equals(status)) {
            row.setStartedAt(NOW);
        }
        taskMapper.tasks.put(id, row);
    }
}