package com.xiaoying.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.config.AppProperties;
import com.xiaoying.storage.StorageService;
import com.xiaoying.support.InMemoryTaskMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TC-BE-08/19/20：查询只读、历史排序分页、用户隔离、结果归属。 */
class TaskQueryServiceTest {

    private static final String DEMO = "usr_demo";
    private static final String ALICE = "usr_alice";
    private static final Instant T1 = Instant.parse("2026-10-28T00:00:01Z");
    private static final Instant T2 = Instant.parse("2026-10-28T00:00:02Z");
    private static final Instant T3 = Instant.parse("2026-10-28T00:00:03Z");
    private static final Instant T4 = Instant.parse("2026-10-28T00:00:04Z");

    @TempDir
    Path tempDir;

    private InMemoryTaskMapper taskMapper;
    private StorageService storageService;
    private TaskQueryService queryService;

    @BeforeEach
    void setUp() throws IOException {
        taskMapper = new InMemoryTaskMapper();
        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir);
        storageService = new StorageService(properties);
        queryService = new TaskQueryService(taskMapper, storageService);

        seed("tsk_demo_1", DEMO, "SUCCEEDED", "DONE", T1);
        taskMapper.tasks.get("tsk_demo_1").setResultUrl("/api/tasks/tsk_demo_1/result");
        Path resultPath = storageService.resultPath("tsk_demo_1");
        Files.createDirectories(resultPath.getParent());
        Files.write(resultPath, new byte[]{1, 2, 3, 4});
        seed("tsk_demo_2", DEMO, "PROCESSING", "GENERATING", T2);
        seed("tsk_demo_3", DEMO, "FAILED", "GENERATING", T3);
        taskMapper.tasks.get("tsk_demo_3").setFailedStage("GENERATING");
        taskMapper.tasks.get("tsk_demo_3").setErrorCode("SIMULATED_FAILURE");
        taskMapper.tasks.get("tsk_demo_3").setErrorMessage("模拟生成失败");
        seed("tsk_alice_1", ALICE, "PENDING", null, T4);

        taskMapper.insertEvent(event("tsk_demo_1", "MATERIAL_CHECK", "STAGE_STARTED"));
        taskMapper.insertEvent(event("tsk_demo_1", "GENERATING", "STAGE_STARTED"));
        taskMapper.insertEvent(event("tsk_demo_1", "DONE", "TASK_SUCCEEDED"));
    }

    @Test
    void queriesAreReadOnly() {
        Map<String, Integer> versions = new HashMap<>();
        Map<String, Instant> updatedAt = new HashMap<>();
        for (TaskRow row : taskMapper.tasks.values()) {
            versions.put(row.getId(), row.getVersion());
            updatedAt.put(row.getId(), row.getUpdatedAt());
        }

        queryService.get("tsk_demo_1", DEMO);
        queryService.history(DEMO, 0, 20);
        queryService.active(DEMO);
        queryService.events("tsk_demo_1", DEMO);
        queryService.result("tsk_demo_1", DEMO);

        assertThat(taskMapper.writeCalls.get()).isZero();
        for (TaskRow row : taskMapper.tasks.values()) {
            assertThat(row.getVersion()).isEqualTo(versions.get(row.getId()));
            assertThat(row.getUpdatedAt()).isEqualTo(updatedAt.get(row.getId()));
        }
    }

    @Test
    void historyIsNewestFirstAndPaged() {
        TaskQueryService.TaskPage first = queryService.history(DEMO, 0, 2);

        assertThat(first.total()).isEqualTo(3);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.items()).extracting(TaskDto::id)
                .containsExactly("tsk_demo_3", "tsk_demo_2");

        TaskQueryService.TaskPage second = queryService.history(DEMO, 1, 2);
        assertThat(second.items()).extracting(TaskDto::id).containsExactly("tsk_demo_1");
        assertThat(second.total()).isEqualTo(3);
    }

    @Test
    void paginationRejectsInvalidValues() {
        assertValidationError(() -> queryService.history(DEMO, -1, 20));
        assertValidationError(() -> queryService.history(DEMO, 0, 0));
        assertValidationError(() -> queryService.history(DEMO, 0, 101));
    }

    @Test
    void userIsolationReturns404ForOtherUsersTask() {
        ApiException getError = assertApiError(() -> queryService.get("tsk_demo_1", ALICE));
        assertThat(getError.getCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);

        ApiException resultError = assertApiError(() -> queryService.result("tsk_demo_1", ALICE));
        assertThat(resultError.getCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);

        ApiException eventError = assertApiError(() -> queryService.events("tsk_demo_1", ALICE));
        assertThat(eventError.getCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);

        assertThat(queryService.history(ALICE, 0, 20).items())
                .extracting(TaskDto::id).containsExactly("tsk_alice_1");
    }

    @Test
    void activeOnlyContainsNonTerminalTasks() {
        List<TaskDto> active = queryService.active(DEMO);

        assertThat(active).extracting(TaskDto::id).containsExactly("tsk_demo_2");
        assertThat(active).allMatch(task -> "PENDING".equals(task.status())
                || "PROCESSING".equals(task.status()));
    }

    @Test
    void resultIsAvailableOnlyForSucceededTask() {
        TaskQueryService.TaskFile file = queryService.result("tsk_demo_1", DEMO);

        assertThat(file.mimeType()).isEqualTo("image/png");
        assertThat(Files.isReadable(file.path())).isTrue();

        ApiException failed = assertApiError(() -> queryService.result("tsk_demo_3", DEMO));
        assertThat(failed.getCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);
        ApiException processing = assertApiError(() -> queryService.result("tsk_demo_2", DEMO));
        assertThat(processing.getCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void eventsAreOrderedByAscendingId() {
        List<TaskEventDto> events = queryService.events("tsk_demo_1", DEMO);

        assertThat(events).hasSize(3);
        assertThat(events).extracting(TaskEventDto::id).isSorted();
        assertThat(events.get(0).stage()).isEqualTo("MATERIAL_CHECK");
    }

    private void seed(String id, String userId, String status, String stage, Instant createdAt) {
        TaskRow row = new TaskRow();
        row.setId(id);
        row.setUserId(userId);
        row.setTemplateId("tpl");
        row.setTemplateTitle("模板");
        row.setStatus(status);
        row.setStage(stage);
        row.setVersion(3);
        row.setCreatedAt(createdAt);
        row.setUpdatedAt(createdAt);
        taskMapper.tasks.put(id, row);
    }

    private static TaskEventRow event(String taskId, String stage, String type) {
        TaskEventRow row = new TaskEventRow();
        row.setTaskId(taskId);
        row.setStage(stage);
        row.setEventType(type);
        row.setCreatedAt(T1);
        return row;
    }

    private static ApiException assertApiError(ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(ApiException.class);
        return (ApiException) thrown;
    }

    private static void assertValidationError(ThrowingCallable callable) {
        assertThat(assertApiError(callable).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}