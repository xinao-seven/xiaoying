package com.xiaoying.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.config.SimulationProperties;
import com.xiaoying.support.InMemorySimulationMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 失败计划快照、计数消费、持续失败模式、单一配置来源。 */
class SimulationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    private SimulationProperties properties;
    private InMemorySimulationMapper mapper;
    private SimulationService service;

    @BeforeEach
    void setUp() {
        properties = new SimulationProperties();
        properties.getDelays().setMaterialCheckMs(800);
        properties.getDelays().setGeneratingMs(2500);
        properties.getDelays().setAssemblingMs(800);
    }

    @Test
    void countModeConsumesOnceAndClearsWhenExhausted() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row("GENERATING", 2, 10, 20, 30));
        service = service();

        assertThat(service.snapshotFailPlan()).isEqualTo("GENERATING");
        assertThat(mapper.row.getFailStage()).isEqualTo("GENERATING");
        assertThat(mapper.row.getFailRemaining()).isEqualTo(1);

        assertThat(service.snapshotFailPlan()).isEqualTo("GENERATING");
        assertThat(mapper.row.getFailStage()).isNull();
        assertThat(mapper.row.getFailRemaining()).isZero();

        assertThat(service.snapshotFailPlan()).isNull();
        assertThat(mapper.row.getFailStage()).isNull();
        assertThat(mapper.row.getFailRemaining()).isZero();
    }

    @Test
    void persistentModeDoesNotConsumeOrChangeRow() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row("GENERATING", 0, 10, 20, 30));
        service = service();
        int updatesBefore = mapper.updates.get();

        assertThat(service.snapshotFailPlan()).isEqualTo("GENERATING");
        assertThat(service.snapshotFailPlan()).isEqualTo("GENERATING");

        assertThat(mapper.row.getFailStage()).isEqualTo("GENERATING");
        assertThat(mapper.row.getFailRemaining()).isZero();
        assertThat(mapper.updates.get()).isEqualTo(updatesBefore);
    }

    @Test
    void explicitNullFailStageClearsPlan() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row("GENERATING", 0, 10, 20, 30));
        service = service();

        SimulationUpdateRequest request = new SimulationUpdateRequest();
        request.setFailStage(null);

        SimulationDto updated = service.update(request);

        assertThat(updated.failStage()).isNull();
        assertThat(updated.failRemaining()).isZero();
        assertThat(service.snapshotFailPlan()).isNull();
    }

    @Test
    void settingCountModeStoresAndConsumes() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row(null, 0, 10, 20, 30));
        service = service();

        SimulationUpdateRequest request = new SimulationUpdateRequest();
        request.setFailStage("GENERATING");
        request.setFailNext(1);

        SimulationDto updated = service.update(request);
        assertThat(updated.failStage()).isEqualTo("GENERATING");
        assertThat(updated.failRemaining()).isEqualTo(1);

        assertThat(service.snapshotFailPlan()).isEqualTo("GENERATING");
        assertThat(mapper.row.getFailStage()).isNull();
        assertThat(mapper.row.getFailRemaining()).isZero();
    }

    @Test
    void updatesDelaysWithoutTouchingOthers() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row(null, 0, 10, 20, 30));
        service = service();

        SimulationUpdateRequest request = new SimulationUpdateRequest();
        request.setDelayGeneratingMs(8000);

        SimulationDto updated = service.update(request);

        assertThat(updated.delayGeneratingMs()).isEqualTo(8000);
        assertThat(updated.delayMaterialCheckMs()).isEqualTo(10);
        assertThat(updated.delayAssemblingMs()).isEqualTo(30);
    }

    @Test
    void runtimeValuesComeFromTableNotProperties() {
        properties.getDelays().setMaterialCheckMs(111);
        properties.getDelays().setGeneratingMs(222);
        properties.getDelays().setAssemblingMs(333);
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row(null, 0, 10, 20, 30));
        service = service();

        SimulationDto dto = service.get();

        assertThat(dto.delayMaterialCheckMs()).isEqualTo(10);
        assertThat(dto.delayGeneratingMs()).isEqualTo(20);
        assertThat(dto.delayAssemblingMs()).isEqualTo(30);
    }

    @Test
    void initializesFromPropertiesOnlyWhenRowMissing() {
        mapper = new InMemorySimulationMapper(null);
        service = service();

        service.ensureInitialized();
        assertThat(mapper.row.getDelayMaterialCheckMs()).isEqualTo(800);
        assertThat(mapper.row.getDelayGeneratingMs()).isEqualTo(2500);
        assertThat(mapper.row.getDelayAssemblingMs()).isEqualTo(800);

        properties.getDelays().setGeneratingMs(9999);
        service.ensureInitialized();
        assertThat(mapper.row.getDelayGeneratingMs()).isEqualTo(2500);
    }

    @Test
    void ensureInitializedDoesNotOverwriteExistingValues() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row("GENERATING", 0, 10, 20, 30));
        service = service();

        service.ensureInitialized();

        assertThat(mapper.row.getFailStage()).isEqualTo("GENERATING");
        assertThat(mapper.row.getDelayGeneratingMs()).isEqualTo(20);
    }

    @Test
    void rejectsInvalidStage() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row(null, 0, 10, 20, 30));
        service = service();
        SimulationUpdateRequest request = new SimulationUpdateRequest();
        request.setFailStage("DONE");

        assertValidation(() -> service.update(request));
    }

    @Test
    void rejectsFailNextWithoutStage() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row(null, 0, 10, 20, 30));
        service = service();
        SimulationUpdateRequest request = new SimulationUpdateRequest();
        request.setFailNext(1);

        assertValidation(() -> service.update(request));
    }

    @Test
    void rejectsNegativeFailNext() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row("GENERATING", 1, 10, 20, 30));
        service = service();
        SimulationUpdateRequest request = new SimulationUpdateRequest();
        request.setFailNext(-1);

        assertValidation(() -> service.update(request));
    }

    @Test
    void rejectsOutOfRangeDelay() {
        mapper = new InMemorySimulationMapper(
                InMemorySimulationMapper.row(null, 0, 10, 20, 30));
        service = service();
        SimulationUpdateRequest request = new SimulationUpdateRequest();
        request.setDelayGeneratingMs(120_001);

        assertValidation(() -> service.update(request));
    }

    private SimulationService service() {
        return new SimulationService(mapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void assertValidation(ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(ApiException.class);
        assertThat(((ApiException) thrown).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}