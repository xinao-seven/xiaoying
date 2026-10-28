package com.xiaoying.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.config.AppProperties;
import com.xiaoying.simulation.SimulationDto;
import com.xiaoying.simulation.SimulationService;
import com.xiaoying.storage.StorageService;
import com.xiaoying.support.FakeResultComposer;
import com.xiaoying.support.FakeSleeper;
import com.xiaoying.support.InMemoryTaskMapper;
import com.xiaoying.support.InMemoryTemplateMapper;
import com.xiaoying.task.TaskEventRow;
import com.xiaoying.task.TaskMaterialRow;
import com.xiaoying.task.TaskRow;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** TC-BE-01～07、16、18：阶段顺序、失败中断、先提交后等待、终态幂等、多任务隔离。 */
class TaskPipelineRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final String USER = "usr_demo";
    private static final String TEMPLATE_ID = "tpl-two";

    @TempDir
    Path tempDir;

    private InMemoryTaskMapper taskMapper;
    private InMemoryTemplateMapper templateMapper;
    private StorageService storageService;
    private FakeSleeper sleeper;
    private FakeResultComposer composer;
    private SimulationService simulationService;
    private TaskPipelineRunner runner;
    private String firstKey;
    private String secondKey;
    private long firstSize;
    private long secondSize;

    @BeforeEach
    void setUp() throws IOException {
        taskMapper = new InMemoryTaskMapper();
        templateMapper = new InMemoryTemplateMapper();
        templateMapper.rows.put(TEMPLATE_ID,
                InMemoryTemplateMapper.template(TEMPLATE_ID, "双图模板", 2, 5 * 1024 * 1024, true));

        AppProperties properties = new AppProperties();
        properties.setDataDir(tempDir);
        storageService = new StorageService(properties);

        byte[] first = pngBytes(320, 240);
        byte[] second = pngBytes(300, 400);
        firstKey = storageService.storeUpload(USER, first, "png");
        secondKey = storageService.storeUpload(USER, second, "png");
        firstSize = first.length;
        secondSize = second.length;

        sleeper = new FakeSleeper();
        composer = new FakeResultComposer();
        simulationService = mock(SimulationService.class);
        when(simulationService.get()).thenReturn(new SimulationDto(null, 0, 0, 0, 0, NOW));

        runner = new TaskPipelineRunner(taskMapper, templateMapper, simulationService, composer,
                storageService, sleeper, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void completesThreeStagesInOrderAndMarksSucceeded() {
        String taskId = seedTask("tsk_success", null);

        runner.process(taskId);

        TaskRow finished = taskMapper.tasks.get(taskId);
        assertThat(finished.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(finished.getStage()).isEqualTo("DONE");
        assertThat(finished.getFailedStage()).isNull();
        assertThat(finished.getResultUrl()).isEqualTo("/api/tasks/" + taskId + "/result");
        assertThat(finished.getFinishedAt()).isEqualTo(NOW);
        assertThat(finished.getVersion()).isGreaterThan(2);
        assertThat(composer.stored).isTrue();
        assertThat(sleeper.sleeps).hasSize(3);

        assertThat(distinctStages(taskId))
                .containsExactly("MATERIAL_CHECK", "GENERATING", "ASSEMBLING", "DONE");
        assertThat(eventTypes(taskId))
                .contains("STAGE_STARTED", "STAGE_SUCCEEDED", "TASK_SUCCEEDED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MATERIAL_CHECK", "GENERATING", "ASSEMBLING"})
    void plannedFailureStopsAtTargetStage(String failedStage) {
        String taskId = seedTask("tsk_fail_" + failedStage, failedStage);

        runner.process(taskId);

        TaskRow finished = taskMapper.tasks.get(taskId);
        assertThat(finished.getStatus()).isEqualTo("FAILED");
        assertThat(finished.getFailedStage()).isEqualTo(failedStage);
        assertThat(finished.getStage()).isEqualTo(failedStage);
        assertThat(finished.getErrorCode()).isEqualTo("SIMULATED_FAILURE");
        assertThat(finished.getErrorMessage()).isNotBlank().contains("模拟注入");
        assertThat(finished.getResultUrl()).isNull();

        List<String> stages = distinctStages(taskId);
        assertThat(stages).doesNotContain("DONE");
        int failedIndex = Stage.EXECUTION_ORDER.indexOf(Stage.valueOf(failedStage));
        for (String stage : stages) {
            Stage parsed = Stage.from(stage);
            if (parsed != null && Stage.EXECUTION_ORDER.contains(parsed)) {
                assertThat(Stage.EXECUTION_ORDER.indexOf(parsed))
                        .isLessThanOrEqualTo(failedIndex);
            }
        }
        assertThat(eventTypes(taskId)).doesNotContain("TASK_SUCCEEDED");
        assertThat(sleeper.sleeps).hasSize(failedIndex + 1);
        if ("ASSEMBLING".equals(failedStage)) {
            assertThat(composer.stored).isFalse();
        }
    }

    @Test
    void stageIsCommittedBeforeSleep() {
        String taskId = seedTask("tsk_commit", null);
        AtomicInteger call = new AtomicInteger();

        sleeper.onSleep = () -> {
            int index = call.getAndIncrement();
            TaskRow current = taskMapper.tasks.get(taskId);
            assertThat(current.getStatus()).isEqualTo("PROCESSING");
            assertThat(current.getStage()).isEqualTo(Stage.EXECUTION_ORDER.get(index).name());
            assertThat(current.getVersion()).isGreaterThan(2);
        };

        runner.process(taskId);

        assertThat(call.get()).isEqualTo(3);
    }

    @Test
    void terminalTaskIsIdempotent() {
        String taskId = seedTask("tsk_twice", null);
        runner.process(taskId);
        TaskRow afterFirst = InMemoryTaskMapper.copy(taskMapper.tasks.get(taskId));
        int eventCount = taskMapper.eventsFor(taskId).size();

        runner.process(taskId);

        TaskRow afterSecond = taskMapper.tasks.get(taskId);
        assertThat(afterSecond.getStatus()).isEqualTo(afterFirst.getStatus());
        assertThat(afterSecond.getVersion()).isEqualTo(afterFirst.getVersion());
        assertThat(afterSecond.getUpdatedAt()).isEqualTo(afterFirst.getUpdatedAt());
        assertThat(taskMapper.eventsFor(taskId)).hasSize(eventCount);
    }

    @Test
    void terminalFailedTaskIsNotReprocessed() {
        String taskId = seedTask("tsk_failed", "GENERATING");
        runner.process(taskId);
        TaskRow failed = InMemoryTaskMapper.copy(taskMapper.tasks.get(taskId));
        int eventCount = taskMapper.eventsFor(taskId).size();

        runner.process(taskId);

        assertThat(taskMapper.tasks.get(taskId).getVersion()).isEqualTo(failed.getVersion());
        assertThat(taskMapper.eventsFor(taskId)).hasSize(eventCount);
    }

    @Test
    void unexpectedExceptionIsPersistedAsInternalError() {
        String taskId = seedTask("tsk_boom", null);
        composer.composeFailure = new IllegalStateException("render boom");

        runner.process(taskId);

        TaskRow failed = taskMapper.tasks.get(taskId);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailedStage()).isEqualTo("GENERATING");
        assertThat(failed.getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(failed.getErrorMessage()).isNotBlank();
        assertThat(failed.getStatus()).isNotEqualTo("PROCESSING");
    }

    @Test
    void twoTasksDoNotInterfere() {
        String failedTask = seedTask("tsk_isolated_fail", "GENERATING");
        String successTask = seedTask("tsk_isolated_ok", null);

        runner.process(failedTask);
        runner.process(successTask);

        TaskRow failed = taskMapper.tasks.get(failedTask);
        TaskRow succeeded = taskMapper.tasks.get(successTask);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailedStage()).isEqualTo("GENERATING");
        assertThat(succeeded.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.getResultUrl()).isEqualTo("/api/tasks/" + successTask + "/result");
        assertThat(distinctStages(failedTask)).doesNotContain("ASSEMBLING", "DONE");
        assertThat(eventTypes(successTask)).contains("TASK_SUCCEEDED");
        assertThat(eventTypes(failedTask)).doesNotContain("TASK_SUCCEEDED");
    }

    @Test
    void missingMaterialFileFailsDuringMaterialCheck() {
        String taskId = seedTask("tsk_missing", null);
        taskMapper.materials.put(taskId, List.of(
                material("mat_x", 0, "uploads/" + USER + "/missing.png", "image/png", 1000L),
                material("mat_2", 1, secondKey, "image/png", secondSize)));

        runner.process(taskId);

        TaskRow failed = taskMapper.tasks.get(taskId);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailedStage()).isEqualTo("MATERIAL_CHECK");
        assertThat(failed.getErrorCode()).isEqualTo("MATERIAL_NOT_FOUND");
        assertThat(sleeper.sleeps).hasSize(1);
    }

    @Test
    void corruptedImageFailsDuringMaterialCheck() throws IOException {
        String taskId = seedTask("tsk_corrupt", null);
        String key = storageService.storeUpload(USER, "not an image".getBytes(), "png");
        taskMapper.materials.put(taskId, List.of(
                material("mat_a", 0, key, "image/png", 12L),
                material("mat_b", 1, secondKey, "image/png", secondSize)));

        runner.process(taskId);

        TaskRow failed = taskMapper.tasks.get(taskId);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailedStage()).isEqualTo("MATERIAL_CHECK");
        assertThat(failed.getErrorCode()).isEqualTo("MATERIAL_UNREADABLE");
    }

    private String seedTask(String taskId, String plannedFailStage) {
        TaskRow row = new TaskRow();
        row.setId(taskId);
        row.setUserId(USER);
        row.setTemplateId(TEMPLATE_ID);
        row.setTemplateTitle("双图模板");
        row.setStatus("PROCESSING");
        row.setStage("MATERIAL_CHECK");
        row.setPlannedFailStage(plannedFailStage);
        row.setParamsJson("{\"caption\":\"测试作品\"}");
        row.setVersion(2);
        row.setCreatedAt(NOW);
        row.setStartedAt(NOW);
        row.setUpdatedAt(NOW);
        taskMapper.tasks.put(taskId, row);
        taskMapper.addMaterial(taskId, "mat_1", firstKey, "image/png", firstSize);
        taskMapper.addMaterial(taskId, "mat_2", secondKey, "image/png", secondSize);
        return taskId;
    }

    private static TaskMaterialRow material(String id, int slot, String key, String mime, long size) {
        TaskMaterialRow row = new TaskMaterialRow();
        row.setMaterialId(id);
        row.setSlotIndex(slot);
        row.setStorageKey(key);
        row.setMimeType(mime);
        row.setSizeBytes(size);
        return row;
    }

    private List<String> distinctStages(String taskId) {
        return taskMapper.eventsFor(taskId).stream()
                .map(TaskEventRow::getStage)
                .distinct()
                .toList();
    }

    private List<String> eventTypes(String taskId) {
        return taskMapper.eventsFor(taskId).stream()
                .map(TaskEventRow::getEventType)
                .toList();
    }

    private static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}