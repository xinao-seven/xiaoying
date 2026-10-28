package com.xiaoying.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.material.MaterialService;
import com.xiaoying.simulation.SimulationService;
import com.xiaoying.support.InMemoryMaterialMapper;
import com.xiaoying.support.InMemoryTaskMapper;
import com.xiaoying.support.InMemoryTemplateMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TC-BE-09～14 与十步校验链。 */
class TaskCreateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final String USER = "usr_demo";
    private static final String TEMPLATE_ID = "tpl-two";

    private InMemoryTaskMapper taskMapper;
    private InMemoryMaterialMapper materialMapper;
    private InMemoryTemplateMapper templateMapper;
    private SimulationService simulationService;
    private TaskCreateService service;

    @BeforeEach
    void setUp() {
        taskMapper = new InMemoryTaskMapper();
        materialMapper = new InMemoryMaterialMapper();
        templateMapper = new InMemoryTemplateMapper();
        templateMapper.rows.put(TEMPLATE_ID,
                InMemoryTemplateMapper.template(TEMPLATE_ID, "双图模板", 2, 5 * 1024 * 1024, true));
        materialMapper.rows.put("mat_1", InMemoryMaterialMapper.material(
                "mat_1", USER, "image/png", 1_000, MaterialService.STATUS_UPLOADED));
        materialMapper.rows.put("mat_2", InMemoryMaterialMapper.material(
                "mat_2", USER, "image/png", 2_000, MaterialService.STATUS_UPLOADED));

        simulationService = mock(SimulationService.class);
        when(simulationService.snapshotFailPlan()).thenReturn(null);

        service = new TaskCreateService(taskMapper, templateMapper, materialMapper, simulationService,
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsPendingTaskWithSlotOrderAndSnapshot() {
        when(simulationService.snapshotFailPlan()).thenReturn("GENERATING");

        TaskDto dto = service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_2"),
                Map.of("caption", "夏日旅行")));

        assertThat(dto.id()).startsWith("tsk_").hasSize(30);
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.stage()).isNull();
        assertThat(dto.failedStage()).isNull();
        assertThat(dto.version()).isEqualTo(1);
        assertThat(dto.templateTitle()).isEqualTo("双图模板");
        assertThat(dto.createdAt()).isEqualTo(NOW);

        TaskRow row = taskMapper.tasks.get(dto.id());
        assertThat(row.getPlannedFailStage()).isEqualTo("GENERATING");
        assertThat(row.getParamsJson()).contains("夏日旅行");
        assertThat(row.getStartedAt()).isNull();
        assertThat(row.getFinishedAt()).isNull();
        assertThat(row.getUpdatedAt()).isEqualTo(NOW);

        List<TaskMaterialRow> slots = taskMapper.materials.get(dto.id());
        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).getMaterialId()).isEqualTo("mat_1");
        assertThat(slots.get(0).getSlotIndex()).isZero();
        assertThat(slots.get(1).getMaterialId()).isEqualTo("mat_2");
        assertThat(slots.get(1).getSlotIndex()).isEqualTo(1);
    }

    @Test
    void rejectsWrongMaterialCount() {
        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1"), null)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.MATERIAL_COUNT_MISMATCH);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsDuplicateMaterial() {
        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_1"), null)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_MATERIAL);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsMaterialOwnedByAnotherUser() {
        materialMapper.rows.get("mat_2").setUserId("usr_alice");

        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_2"), null)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.MATERIAL_FORBIDDEN);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsMaterialNotYetUploaded() {
        materialMapper.rows.get("mat_2").setStatus("PROCESSING");

        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_2"), null)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.MATERIAL_NOT_FOUND);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsMimeOutsideTemplateList() {
        materialMapper.rows.get("mat_2").setMimeType("image/gif");

        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_2"), null)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.MATERIAL_MIME_NOT_ALLOWED);
        assertThat(error.getCode().status().value()).isEqualTo(415);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsMaterialOverTemplateLimit() {
        materialMapper.rows.get("mat_2").setSizeBytes(6 * 1024 * 1024L);

        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_2"), null)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.MATERIAL_TOO_LARGE);
        assertThat(error.getCode().status().value()).isEqualTo(413);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsMissingTemplate() {
        ApiException error = assertApiError(() ->
                service.create(USER, request("nope", List.of("mat_1", "mat_2"), null)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.TEMPLATE_NOT_FOUND);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsUnknownParams() {
        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_2"),
                        Map.of("unknown", "x"))));

        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAMS);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsTooLongCaption() {
        String caption = "x".repeat(201);

        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_2"),
                        Map.of("caption", caption))));

        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAMS);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsNonStringCaption() {
        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", "mat_2"),
                        Map.of("caption", 42))));

        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAMS);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsBlankMaterialId() {
        ApiException error = assertApiError(() ->
                service.create(USER, request(TEMPLATE_ID, List.of("mat_1", " "), null)));

        assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(taskMapper.tasks).isEmpty();
    }

    @Test
    void rejectsNullRequest() {
        ApiException error = assertApiError(() -> service.create(USER, null));

        assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(taskMapper.tasks).isEmpty();
    }

    private static TaskCreateRequest request(String templateId, List<String> materialIds,
                                             Map<String, Object> params) {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setTemplateId(templateId);
        request.setMaterialIds(materialIds);
        request.setParams(params);
        return request;
    }

    private static ApiException assertApiError(ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(ApiException.class);
        return (ApiException) thrown;
    }
}