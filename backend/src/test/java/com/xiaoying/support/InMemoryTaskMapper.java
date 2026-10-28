package com.xiaoying.support;

import com.xiaoying.task.TaskEventRow;
import com.xiaoying.task.TaskMapper;
import com.xiaoying.task.TaskMaterialRow;
import com.xiaoying.task.TaskRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存版 TaskMapper：模拟条件更新的行数语义，用于流水线/查询测试，不连数据库。
 */
public class InMemoryTaskMapper implements TaskMapper {

    public final Map<String, TaskRow> tasks = new ConcurrentHashMap<>();
    public final Map<String, List<TaskMaterialRow>> materials = new ConcurrentHashMap<>();
    public final List<TaskEventRow> events = new CopyOnWriteArrayList<>();
    public final AtomicInteger writeCalls = new AtomicInteger();
    private final AtomicLong eventSequence = new AtomicLong();

    @Override
    public void insert(TaskRow row) {
        tasks.put(row.getId(), copy(row));
        writeCalls.incrementAndGet();
    }

    @Override
    public void insertTaskMaterial(String taskId, String materialId, int slotIndex) {
        materials.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(
                materialRow(materialId, slotIndex, null, null, null));
        writeCalls.incrementAndGet();
    }

    public void addMaterial(String taskId, String materialId, String storageKey, String mimeType,
                            long sizeBytes) {
        int index = materials.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).size();
        materials.get(taskId).add(materialRow(materialId, index, storageKey, mimeType, sizeBytes));
    }

    @Override
    public List<String> findPendingIds(int limit) {
        return tasks.values().stream()
                .filter(row -> "PENDING".equals(row.getStatus()))
                .sorted(Comparator.comparing(TaskRow::getCreatedAt).thenComparing(TaskRow::getId))
                .limit(limit)
                .map(TaskRow::getId)
                .toList();
    }

    @Override
    public int claim(String id, Instant now) {
        AtomicBoolean affected = new AtomicBoolean();
        tasks.computeIfPresent(id, (key, row) -> {
            if ("PENDING".equals(row.getStatus())) {
                row.setStatus("PROCESSING");
                row.setStage("MATERIAL_CHECK");
                row.setStartedAt(now);
                row.setUpdatedAt(now);
                row.setVersion(version(row) + 1);
                affected.set(true);
            }
            return row;
        });
        if (affected.get()) {
            writeCalls.incrementAndGet();
        }
        return affected.get() ? 1 : 0;
    }

    @Override
    public int releaseClaim(String id, Instant now) {
        return mutate(id, row -> {
            if ("PROCESSING".equals(row.getStatus())) {
                row.setStatus("PENDING");
                row.setStage(null);
                row.setStartedAt(null);
                row.setUpdatedAt(now);
                row.setVersion(version(row) + 1);
                return true;
            }
            return false;
        });
    }

    @Override
    public int updateStage(String id, String stage, Instant now) {
        return mutate(id, row -> {
            if ("PROCESSING".equals(row.getStatus())) {
                row.setStage(stage);
                row.setUpdatedAt(now);
                row.setVersion(version(row) + 1);
                return true;
            }
            return false;
        });
    }

    @Override
    public int markSucceeded(String id, String resultUrl, int width, int height, Instant now) {
        return mutate(id, row -> {
            if ("PROCESSING".equals(row.getStatus())) {
                row.setStatus("SUCCEEDED");
                row.setStage("DONE");
                row.setResultUrl(resultUrl);
                row.setResultWidth(width);
                row.setResultHeight(height);
                row.setFailedStage(null);
                row.setErrorCode(null);
                row.setErrorMessage(null);
                row.setFinishedAt(now);
                row.setUpdatedAt(now);
                row.setVersion(version(row) + 1);
                return true;
            }
            return false;
        });
    }

    @Override
    public int markFailed(String id, String stage, String errorCode, String errorMessage, Instant now) {
        return mutate(id, row -> {
            if ("PROCESSING".equals(row.getStatus())) {
                row.setStatus("FAILED");
                row.setStage(stage);
                row.setFailedStage(stage);
                row.setErrorCode(errorCode);
                row.setErrorMessage(errorMessage);
                row.setFinishedAt(now);
                row.setUpdatedAt(now);
                row.setVersion(version(row) + 1);
                return true;
            }
            return false;
        });
    }

    @Override
    public TaskRow findById(String id) {
        TaskRow row = tasks.get(id);
        return row == null ? null : copy(row);
    }

    @Override
    public TaskRow findByIdAndUser(String id, String userId) {
        TaskRow row = tasks.get(id);
        return row == null || !userId.equals(row.getUserId()) ? null : copy(row);
    }

    @Override
    public List<TaskRow> findByUser(String userId, int size, long offset) {
        return tasks.values().stream()
                .filter(row -> userId.equals(row.getUserId()))
                .sorted(newestFirst())
                .skip(offset)
                .limit(size)
                .map(InMemoryTaskMapper::copy)
                .toList();
    }

    @Override
    public long countByUser(String userId) {
        return tasks.values().stream().filter(row -> userId.equals(row.getUserId())).count();
    }

    @Override
    public List<TaskRow> findActiveByUser(String userId) {
        return tasks.values().stream()
                .filter(row -> userId.equals(row.getUserId()))
                .filter(row -> "PENDING".equals(row.getStatus()) || "PROCESSING".equals(row.getStatus()))
                .sorted(newestFirst())
                .map(InMemoryTaskMapper::copy)
                .toList();
    }

    @Override
    public List<TaskMaterialRow> findMaterialsByTask(String taskId) {
        List<TaskMaterialRow> rows = materials.get(taskId);
        return rows == null ? List.of() : new ArrayList<>(rows);
    }

    @Override
    public void insertEvent(TaskEventRow row) {
        TaskEventRow stored = new TaskEventRow();
        stored.setId(eventSequence.incrementAndGet());
        stored.setTaskId(row.getTaskId());
        stored.setStage(row.getStage());
        stored.setEventType(row.getEventType());
        stored.setMessage(row.getMessage());
        stored.setCreatedAt(row.getCreatedAt());
        events.add(stored);
    }

    @Override
    public List<TaskEventRow> findEventsByTask(String taskId) {
        return events.stream()
                .filter(event -> taskId.equals(event.getTaskId()))
                .sorted(Comparator.comparing(TaskEventRow::getId))
                .toList();
    }

    @Override
    public List<TaskRow> findProcessing() {
        return tasks.values().stream()
                .filter(row -> "PROCESSING".equals(row.getStatus()))
                .map(InMemoryTaskMapper::copy)
                .toList();
    }

    @Override
    public int markFailedOnRestart(String id, String message, Instant now) {
        return mutate(id, row -> {
            if ("PROCESSING".equals(row.getStatus())) {
                row.setStatus("FAILED");
                row.setFailedStage(row.getStage());
                row.setErrorCode("SERVER_RESTART");
                row.setErrorMessage(message);
                row.setFinishedAt(now);
                row.setUpdatedAt(now);
                row.setVersion(version(row) + 1);
                return true;
            }
            return false;
        });
    }

    public List<TaskEventRow> eventsFor(String taskId) {
        return findEventsByTask(taskId);
    }

    private int mutate(String id, RowMutation mutation) {
        AtomicBoolean affected = new AtomicBoolean();
        tasks.computeIfPresent(id, (key, row) -> {
            if (mutation.apply(row)) {
                affected.set(true);
            }
            return row;
        });
        if (affected.get()) {
            writeCalls.incrementAndGet();
        }
        return affected.get() ? 1 : 0;
    }

    private static Comparator<TaskRow> newestFirst() {
        return Comparator.comparing(TaskRow::getCreatedAt).reversed()
                .thenComparing(TaskRow::getId, Comparator.reverseOrder());
    }

    private static int version(TaskRow row) {
        return row.getVersion() == null ? 0 : row.getVersion();
    }

    private static TaskMaterialRow materialRow(String materialId, int slotIndex, String storageKey,
                                               String mimeType, Long sizeBytes) {
        TaskMaterialRow row = new TaskMaterialRow();
        row.setMaterialId(materialId);
        row.setSlotIndex(slotIndex);
        row.setStorageKey(storageKey);
        row.setMimeType(mimeType);
        row.setSizeBytes(sizeBytes);
        return row;
    }

    public static TaskRow copy(TaskRow source) {
        TaskRow target = new TaskRow();
        target.setId(source.getId());
        target.setUserId(source.getUserId());
        target.setTemplateId(source.getTemplateId());
        target.setTemplateTitle(source.getTemplateTitle());
        target.setStatus(source.getStatus());
        target.setStage(source.getStage());
        target.setFailedStage(source.getFailedStage());
        target.setErrorCode(source.getErrorCode());
        target.setErrorMessage(source.getErrorMessage());
        target.setResultUrl(source.getResultUrl());
        target.setResultWidth(source.getResultWidth());
        target.setResultHeight(source.getResultHeight());
        target.setParamsJson(source.getParamsJson());
        target.setPlannedFailStage(source.getPlannedFailStage());
        target.setVersion(source.getVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setStartedAt(source.getStartedAt());
        target.setFinishedAt(source.getFinishedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    @FunctionalInterface
    private interface RowMutation {
        boolean apply(TaskRow row);
    }
}