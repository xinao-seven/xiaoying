package com.xiaoying.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.common.Ids;
import com.xiaoying.material.MaterialMapper;
import com.xiaoying.material.MaterialRow;
import com.xiaoying.material.MaterialService;
import com.xiaoying.simulation.SimulationService;
import com.xiaoying.template.TemplateMapper;
import com.xiaoying.template.TemplateRow;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务受理。严格按需求 4.5 的十步顺序校验，只做校验与落库，不等待任何阶段。
 */
@Service
public class TaskCreateService {

    public static final String STATUS_PENDING = "PENDING";

    private static final Set<String> ALLOWED_PARAMS = Set.of("caption");
    private static final int MAX_CAPTION_LENGTH = 200;

    private final TaskMapper taskMapper;
    private final TemplateMapper templateMapper;
    private final MaterialMapper materialMapper;
    private final SimulationService simulationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TaskCreateService(TaskMapper taskMapper,
                             TemplateMapper templateMapper,
                             MaterialMapper materialMapper,
                             SimulationService simulationService,
                             ObjectMapper objectMapper,
                             Clock clock) {
        this.taskMapper = taskMapper;
        this.templateMapper = templateMapper;
        this.materialMapper = materialMapper;
        this.simulationService = simulationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 事务内完成：失败计划快照 → tasks 行 → task_materials 槽位。
     * 失败计划在创建时固化到 {@code tasks.planned_fail_stage}，之后修改模拟配置不影响该任务。
     */
    @Transactional
    public TaskDto create(String userId, TaskCreateRequest request) {
        // 1. 登录态由 AuthInterceptor 保证；这里只防御 userId 为空
        if (userId == null || userId.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        // 2. 模板存在且启用
        String templateId = trimToNull(request == null ? null : request.getTemplateId());
        if (templateId == null) {
            throw fieldError(ErrorCode.VALIDATION_ERROR, "templateId 不能为空", "templateId", "不能为空");
        }
        TemplateRow template = templateMapper.findEnabledById(templateId);
        if (template == null) {
            throw new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在或已下架");
        }

        List<String> materialIds = request.getMaterialIds();
        // 3. 数量与模板要求一致
        int required = template.getRequiredImageCount() == null ? 0 : template.getRequiredImageCount();
        if (materialIds == null || materialIds.size() != required) {
            int actual = materialIds == null ? 0 : materialIds.size();
            throw fieldError(ErrorCode.MATERIAL_COUNT_MISMATCH,
                    "需要 " + required + " 张图片，当前 " + actual + " 张",
                    "materialIds", "数量不匹配");
        }

        // 4. 无重复素材 id
        Set<String> seen = new HashSet<>();
        for (String materialId : materialIds) {
            if (materialId == null || materialId.isBlank()) {
                throw fieldError(ErrorCode.VALIDATION_ERROR, "素材 id 不能为空", "materialIds", "素材 id 不能为空");
            }
            if (!seen.add(materialId)) {
                throw fieldError(ErrorCode.DUPLICATE_MATERIAL, "存在重复的素材", "materialIds", "素材重复");
            }
        }

        // 5～8. 归属、存在与状态、MIME、大小
        List<String> accepted = splitMimeTypes(template.getAcceptedMimeTypes());
        long maxBytes = template.getMaxFileSizeBytes() == null ? Long.MAX_VALUE : template.getMaxFileSizeBytes();
        List<MaterialRow> materials = new ArrayList<>(materialIds.size());
        for (int index = 0; index < materialIds.size(); index++) {
            String materialId = materialIds.get(index);
            MaterialRow material = materialMapper.findById(materialId);
            if (material == null || !userId.equals(material.getUserId())) {
                // 不存在与越权统一 404，不泄露资源存在性
                throw new ApiException(ErrorCode.MATERIAL_FORBIDDEN, "素材不存在");
            }
            if (!MaterialService.STATUS_UPLOADED.equals(material.getStatus())) {
                throw new ApiException(ErrorCode.MATERIAL_NOT_FOUND, "素材不存在或尚未上传完成");
            }
            if (!accepted.contains(material.getMimeType())) {
                throw new ApiException(ErrorCode.MATERIAL_MIME_NOT_ALLOWED,
                        "第 " + (index + 1) + " 张图片格式不支持");
            }
            if (material.getSizeBytes() != null && material.getSizeBytes() > maxBytes) {
                throw new ApiException(ErrorCode.MATERIAL_TOO_LARGE,
                        "第 " + (index + 1) + " 张图片超过模板大小限制");
            }
            materials.add(material);
        }

        // 9. 制作参数：未知字段、非字符串 caption、超长 caption 一律拒绝
        Map<String, Object> params = validateParams(request.getParams());

        // 10. 写 tasks + task_materials；失败计划快照在同一事务内完成
        String plannedFailStage = simulationService.snapshotFailPlan();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String taskId = Ids.task();

        TaskRow row = new TaskRow();
        row.setId(taskId);
        row.setUserId(userId);
        row.setTemplateId(template.getId());
        row.setTemplateTitle(template.getTitle());
        row.setStatus(STATUS_PENDING);
        row.setStage(null);
        row.setFailedStage(null);
        row.setErrorCode(null);
        row.setErrorMessage(null);
        row.setResultUrl(null);
        row.setParamsJson(toJson(params));
        row.setPlannedFailStage(plannedFailStage);
        row.setVersion(1);
        row.setCreatedAt(now);
        row.setStartedAt(null);
        row.setFinishedAt(null);
        row.setUpdatedAt(now);

        taskMapper.insert(row);
        for (int index = 0; index < materialIds.size(); index++) {
            taskMapper.insertTaskMaterial(taskId, materialIds.get(index), index);
        }

        return TaskDto.from(row);
    }

    private static Map<String, Object> validateParams(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!ALLOWED_PARAMS.contains(entry.getKey())) {
                throw fieldError(ErrorCode.INVALID_PARAMS,
                        "不支持的制作参数：" + entry.getKey(), "params", "不支持的参数：" + entry.getKey());
            }
        }
        Object caption = params.get("caption");
        if (caption != null) {
            if (!(caption instanceof String text)) {
                throw fieldError(ErrorCode.INVALID_PARAMS, "caption 必须是字符串", "params.caption", "必须是字符串");
            }
            if (text.length() > MAX_CAPTION_LENGTH) {
                throw fieldError(ErrorCode.INVALID_PARAMS,
                        "caption 最长 " + MAX_CAPTION_LENGTH + " 个字符",
                        "params.caption", "长度超限");
            }
        }
        return params.isEmpty() ? null : params;
    }

    private String toJson(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            throw fieldError(ErrorCode.INVALID_PARAMS, "制作参数无法序列化", "params", "格式不正确");
        }
    }

    private static List<String> splitMimeTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApiException fieldError(ErrorCode code, String message, String field, String detail) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(field, detail);
        return new ApiException(code, message, fields);
    }
}