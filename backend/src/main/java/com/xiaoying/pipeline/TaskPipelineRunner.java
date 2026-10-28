package com.xiaoying.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.simulation.SimulationDto;
import com.xiaoying.simulation.SimulationService;
import com.xiaoying.storage.StorageService;
import com.xiaoying.task.TaskEventRow;
import com.xiaoying.task.TaskMapper;
import com.xiaoying.task.TaskMaterialRow;
import com.xiaoying.task.TaskRow;
import com.xiaoying.template.TemplateMapper;
import com.xiaoying.template.TemplateRow;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 三阶段 worker。
 *
 * <p>关键约束：
 * <ul>
 *   <li>每个阶段先写 stage/version 并提交，再 sleep，再执行工作（FR-PIPE-02 / TC-BE-07）。</li>
 *   <li>任一阶段失败立即 markFailed 并返回，后续阶段不执行（FR-PIPE-04）。</li>
 *   <li>所有写入都带 {@code status='PROCESSING'} 条件，终态不会被回退（FR-PIPE-07）。</li>
 *   <li>方法不包 @Transactional：sleep 绝不进入数据库事务。</li>
 *   <li>顶层捕获 Throwable，绝不留下永久 PROCESSING（FR-PIPE-10）。</li>
 * </ul>
 */
@Component
public class TaskPipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskPipelineRunner.class);
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final int MAX_MESSAGE_LENGTH = 512;

    private final TaskMapper taskMapper;
    private final TemplateMapper templateMapper;
    private final SimulationService simulationService;
    private final ResultComposer resultComposer;
    private final StorageService storageService;
    private final Sleeper sleeper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TaskPipelineRunner(TaskMapper taskMapper,
                              TemplateMapper templateMapper,
                              SimulationService simulationService,
                              ResultComposer resultComposer,
                              StorageService storageService,
                              Sleeper sleeper,
                              ObjectMapper objectMapper,
                              Clock clock) {
        this.taskMapper = taskMapper;
        this.templateMapper = templateMapper;
        this.simulationService = simulationService;
        this.resultComposer = resultComposer;
        this.storageService = storageService;
        this.sleeper = sleeper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void process(String taskId) {
        Stage currentStage = null;
        try {
            TaskRow task = taskMapper.findById(taskId);
            if (task == null || !STATUS_PROCESSING.equals(task.getStatus())) {
                // 未知任务或已终态：幂等返回，不写任何内容
                return;
            }
            currentStage = Stage.from(task.getStage());
            TaskContext context = loadContext(task);
            SimulationDto simulation = simulationService.get();

            for (Stage stage : Stage.EXECUTION_ORDER) {
                currentStage = stage;
                int moved = taskMapper.updateStage(taskId, stage.name(), now());
                if (moved == 0) {
                    log.info("task no longer processing, stop taskId={} stage={}", taskId, stage);
                    return;
                }
                insertEventQuietly(taskId, stage, "STAGE_STARTED", null);
                log.info("stage started taskId={} stage={} status={}", taskId, stage, STATUS_PROCESSING);

                sleeper.sleep(delayFor(simulation, stage));

                if (stage.name().equals(task.getPlannedFailStage())) {
                    fail(taskId, stage, "SIMULATED_FAILURE", simulatedMessage(stage));
                    return;
                }

                switch (stage) {
                    case MATERIAL_CHECK -> checkMaterials(context);
                    case GENERATING -> context.rendered = generate(context);
                    case ASSEMBLING -> context.stored = assemble(context);
                    default -> throw new IllegalStateException("unexpected stage " + stage);
                }
                insertEventQuietly(taskId, stage, "STAGE_SUCCEEDED", null);
                log.info("stage succeeded taskId={} stage={}", taskId, stage);
            }

            if (context.stored == null) {
                throw new StageFailure("RESULT_WRITE_FAILED", "结果整理失败：未生成结果文件");
            }
            int finished = taskMapper.markSucceeded(taskId, context.stored.url(),
                    context.stored.width(), context.stored.height(), now());
            if (finished == 1) {
                insertEventQuietly(taskId, Stage.DONE, "TASK_SUCCEEDED", "制作完成");
                log.info("task succeeded taskId={} status=SUCCEEDED stage=DONE", taskId);
            }
        } catch (StageFailure failure) {
            fail(taskId, currentStage, failure.code(), failure.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fail(taskId, currentStage, "INTERNAL_ERROR", "任务处理被中断");
        } catch (Throwable ex) {
            log.error("pipeline failed taskId={} stage={}", taskId, currentStage, ex);
            fail(taskId, currentStage, "INTERNAL_ERROR", "服务器内部错误，任务处理失败");
        }
    }

    private TaskContext loadContext(TaskRow task) {
        TemplateRow template = templateMapper.findById(task.getTemplateId());
        if (template == null) {
            throw new StageFailure("TEMPLATE_NOT_FOUND", "素材检查失败：模板不存在");
        }
        List<TaskMaterialRow> materials = taskMapper.findMaterialsByTask(task.getId());
        String caption = extractCaption(task.getParamsJson());
        if (caption == null || caption.isBlank()) {
            caption = template.getTitle();
        }
        return new TaskContext(task, template, materials, caption);
    }

    private void checkMaterials(TaskContext context) {
        int required = context.template.getRequiredImageCount() == null
                ? 0 : context.template.getRequiredImageCount();
        if (context.materials.size() != required) {
            throw new StageFailure("MATERIAL_COUNT_MISMATCH",
                    "素材检查失败：需要 " + required + " 张图片，当前 " + context.materials.size() + " 张");
        }
        List<String> accepted = splitMimeTypes(context.template.getAcceptedMimeTypes());
        long maxBytes = context.template.getMaxFileSizeBytes() == null
                ? Long.MAX_VALUE : context.template.getMaxFileSizeBytes();

        for (int index = 0; index < context.materials.size(); index++) {
            TaskMaterialRow material = context.materials.get(index);
            int position = index + 1;
            if (!accepted.contains(material.getMimeType())) {
                throw new StageFailure("MATERIAL_MIME_NOT_ALLOWED",
                        "素材检查失败：第 " + position + " 张图片格式不支持");
            }
            if (material.getSizeBytes() != null && material.getSizeBytes() > maxBytes) {
                throw new StageFailure("MATERIAL_TOO_LARGE",
                        "素材检查失败：第 " + position + " 张图片超过大小限制");
            }
            if (material.getStorageKey() == null || material.getStorageKey().isBlank()) {
                throw new StageFailure("MATERIAL_NOT_FOUND",
                        "素材检查失败：第 " + position + " 张图片文件不存在");
            }
            Path path;
            try {
                path = storageService.resolve(material.getStorageKey());
            } catch (RuntimeException ex) {
                throw new StageFailure("MATERIAL_NOT_FOUND",
                        "素材检查失败：第 " + position + " 张图片文件不存在");
            }
            try {
                if (!Files.isReadable(path)) {
                    throw new StageFailure("MATERIAL_NOT_FOUND",
                            "素材检查失败：第 " + position + " 张图片文件不存在");
                }
                if (Files.size(path) > maxBytes) {
                    throw new StageFailure("MATERIAL_TOO_LARGE",
                            "素材检查失败：第 " + position + " 张图片超过大小限制");
                }
                BufferedImage image = ImageIO.read(path.toFile());
                if (image == null) {
                    throw new StageFailure("MATERIAL_UNREADABLE",
                            "素材检查失败：第 " + position + " 张图片无法解码");
                }
            } catch (IOException ex) {
                throw new StageFailure("MATERIAL_UNREADABLE",
                        "素材检查失败：第 " + position + " 张图片无法解码");
            }
            context.paths.add(path);
        }
    }

    private BufferedImage generate(TaskContext context) {
        try {
            BufferedImage image = resultComposer.compose(context.paths, context.caption);
            if (image == null) {
                throw new StageFailure("INTERNAL_ERROR", "模拟生成失败：结果为空");
            }
            return image;
        } catch (StageFailure failure) {
            throw failure;
        } catch (RuntimeException ex) {
            log.error("generate failed taskId={}", context.task.getId(), ex);
            throw new StageFailure("INTERNAL_ERROR", "模拟生成失败：" + safeDetail(ex));
        }
    }

    private ResultComposer.StoredResult assemble(TaskContext context) {
        if (context.rendered == null) {
            throw new StageFailure("INTERNAL_ERROR", "结果整理失败：没有生成结果");
        }
        try {
            return resultComposer.store(context.rendered, context.task.getId());
        } catch (IOException | RuntimeException ex) {
            log.error("assemble failed taskId={}", context.task.getId(), ex);
            throw new StageFailure("RESULT_WRITE_FAILED", "结果整理失败：无法写入结果文件");
        }
    }

    private void fail(String taskId, Stage stage, String code, String message) {
        Stage effective = stage == null ? Stage.MATERIAL_CHECK : stage;
        String safeMessage = truncate(message, MAX_MESSAGE_LENGTH);
        try {
            int updated = taskMapper.markFailed(taskId, effective.name(), code, safeMessage, now());
            if (updated == 1) {
                insertEventQuietly(taskId, effective, "STAGE_FAILED", safeMessage);
                log.warn("task failed taskId={} stage={} status=FAILED code={}",
                        taskId, effective, code);
            }
        } catch (RuntimeException ex) {
            // 失败落库本身失败时只能记录日志；重启对账会把残留 PROCESSING 收口
            log.error("failed to persist task failure taskId={} code={}", taskId, code, ex);
        }
    }

    private void insertEventQuietly(String taskId, Stage stage, String eventType, String message) {
        try {
            TaskEventRow event = new TaskEventRow();
            event.setTaskId(taskId);
            event.setStage(stage.name());
            event.setEventType(eventType);
            event.setMessage(message);
            event.setCreatedAt(now());
            taskMapper.insertEvent(event);
        } catch (RuntimeException ex) {
            // 事件是阶段证据，但事件写入失败不应把已成功的阶段改成失败
            log.warn("failed to write task event taskId={} stage={} type={}", taskId, stage, eventType, ex);
        }
    }

    private String extractCaption(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(paramsJson);
            if (node.isObject() && node.hasNonNull("caption")) {
                String text = node.get("caption").asText();
                return text.isBlank() ? null : text;
            }
        } catch (JsonProcessingException ignored) {
            // 创建时已校验；这里仅做容错
        }
        return null;
    }

    private static long delayFor(SimulationDto simulation, Stage stage) {
        return switch (stage) {
            case MATERIAL_CHECK -> simulation.delayMaterialCheckMs();
            case GENERATING -> simulation.delayGeneratingMs();
            case ASSEMBLING -> simulation.delayAssemblingMs();
            default -> 0;
        };
    }

    private static String simulatedMessage(Stage stage) {
        return switch (stage) {
            case MATERIAL_CHECK -> "素材检查失败：素材检查阶段失败（模拟注入）";
            case GENERATING -> "模拟生成失败：生成服务返回错误（模拟注入）";
            case ASSEMBLING -> "结果整理失败：结果整理阶段失败（模拟注入）";
            default -> "任务处理失败（模拟注入）";
        };
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

    private static String safeDetail(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "生成过程发生异常";
        }
        return truncate(message, 160);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static final class TaskContext {
        private final TaskRow task;
        private final TemplateRow template;
        private final List<TaskMaterialRow> materials;
        private final List<Path> paths = new ArrayList<>();
        private final String caption;
        private BufferedImage rendered;
        private ResultComposer.StoredResult stored;

        private TaskContext(TaskRow task, TemplateRow template, List<TaskMaterialRow> materials,
                            String caption) {
            this.task = task;
            this.template = template;
            this.materials = materials;
            this.caption = caption;
        }
    }
}