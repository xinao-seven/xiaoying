package com.xiaoying.simulation;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.config.SimulationProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模拟配置读写。
 *
 * <p>运行期唯一权威来源是 {@code simulation_config} 表；{@link SimulationProperties} 只在单例行缺失时
 * 提供首次初始化值。失败计划在任务创建事务内快照并原子消费。
 */
@Service
public class SimulationService {

    private static final Set<String> FAIL_STAGES = Set.of("MATERIAL_CHECK", "GENERATING", "ASSEMBLING");
    private static final int MAX_DELAY_MS = 120_000;
    private static final int MAX_FAIL_NEXT = 1_000;

    private final SimulationMapper simulationMapper;
    private final SimulationProperties properties;
    private final Clock clock;

    public SimulationService(SimulationMapper simulationMapper,
                             SimulationProperties properties,
                             Clock clock) {
        this.simulationMapper = simulationMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public SimulationDto get() {
        SimulationRow row = simulationMapper.find();
        if (row == null) {
            ensureInitialized();
            row = simulationMapper.find();
        }
        if (row == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "模拟配置初始化失败");
        }
        return SimulationDto.from(row);
    }

    /**
     * 在任务创建事务内调用：锁定单例行，读取失败阶段并消费一次计数。
     *
     * <p>计数模式（fail_remaining &gt; 0）消费后减一；减到 0 时同时清空 fail_stage，
     * 避免下一次任务被误判为“持续失败”模式。持续失败模式（fail_stage 非空且 remaining = 0）
     * 不消费、不修改，直到显式清空或重新设置计数。
     */
    @Transactional
    public String snapshotFailPlan() {
        SimulationRow row = simulationMapper.findForUpdate();
        if (row == null || row.getFailStage() == null) {
            return null;
        }
        int remaining = row.getFailRemaining() == null ? 0 : row.getFailRemaining();
        String planned = row.getFailStage();
        if (remaining > 0) {
            int left = remaining - 1;
            row.setFailRemaining(left);
            if (left == 0) {
                row.setFailStage(null);
            }
            row.setUpdatedAt(now());
            simulationMapper.update(row);
        }
        return planned;
    }

    @Transactional
    public SimulationDto update(SimulationUpdateRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "请求体不能为空");
        }
        SimulationRow row = simulationMapper.findForUpdate();
        if (row == null) {
            ensureInitialized();
            row = simulationMapper.findForUpdate();
        }
        if (row == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "模拟配置初始化失败");
        }

        String stage = row.getFailStage();
        int remaining = row.getFailRemaining() == null ? 0 : row.getFailRemaining();

        if (request.isFailStagePresent()) {
            String requested = request.getFailStage();
            if (requested == null) {
                if (request.getFailNext() != null && request.getFailNext() > 0) {
                    throw fieldError("failStage", "清空 failStage 时 failNext 必须为 0 或缺省");
                }
                stage = null;
                remaining = 0;
            } else {
                stage = normalizeStage(requested);
                if (request.getFailNext() != null) {
                    remaining = validateFailNext(request.getFailNext());
                }
            }
        } else if (request.getFailNext() != null) {
            remaining = validateFailNext(request.getFailNext());
            if (remaining > 0 && stage == null) {
                throw fieldError("failStage", "failNext 大于 0 时必须同时提供 failStage");
            }
        }

        if (request.getDelayMaterialCheckMs() != null) {
            row.setDelayMaterialCheckMs(validateDelay("delayMaterialCheckMs", request.getDelayMaterialCheckMs()));
        }
        if (request.getDelayGeneratingMs() != null) {
            row.setDelayGeneratingMs(validateDelay("delayGeneratingMs", request.getDelayGeneratingMs()));
        }
        if (request.getDelayAssemblingMs() != null) {
            row.setDelayAssemblingMs(validateDelay("delayAssemblingMs", request.getDelayAssemblingMs()));
        }

        row.setFailStage(stage);
        row.setFailRemaining(remaining);
        row.setUpdatedAt(now());
        simulationMapper.update(row);
        return SimulationDto.from(row);
    }

    /** 仅当单例行不存在时用配置类初始化；绝不覆盖开发接口已修改的值。 */
    public void ensureInitialized() {
        if (simulationMapper.find() != null) {
            return;
        }
        SimulationRow row = new SimulationRow();
        row.setFailStage(null);
        row.setFailRemaining(0);
        row.setDelayMaterialCheckMs((int) properties.getDelays().getMaterialCheckMs());
        row.setDelayGeneratingMs((int) properties.getDelays().getGeneratingMs());
        row.setDelayAssemblingMs((int) properties.getDelays().getAssemblingMs());
        row.setUpdatedAt(now());
        try {
            simulationMapper.insert(row);
        } catch (DuplicateKeyException ignored) {
            // 并发初始化时以已存在的行准
        }
    }

    private static String normalizeStage(String value) {
        String trimmed = value.trim();
        if (!FAIL_STAGES.contains(trimmed)) {
            throw fieldError("failStage", "failStage 必须是 MATERIAL_CHECK、GENERATING、ASSEMBLING 或 null");
        }
        return trimmed;
    }

    private static int validateFailNext(int value) {
        if (value < 0 || value > MAX_FAIL_NEXT) {
            throw fieldError("failNext", "failNext 必须在 0～" + MAX_FAIL_NEXT + " 之间");
        }
        return value;
    }

    private static int validateDelay(String field, int value) {
        if (value < 0 || value > MAX_DELAY_MS) {
            throw fieldError(field, field + " 必须在 0～" + MAX_DELAY_MS + " 毫秒之间");
        }
        return value;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static ApiException fieldError(String field, String message) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(field, message);
        return new ApiException(ErrorCode.VALIDATION_ERROR, message, fields);
    }
}