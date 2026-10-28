package com.xiaoying.task;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.storage.StorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 只读查询。本类不得调用任何写方法；Mapper 侧也只声明 SELECT（FR-PIPE-08 / TC-BE-08）。
 */
@Service
public class TaskQueryService {

    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskMapper taskMapper;
    private final StorageService storageService;

    public TaskQueryService(TaskMapper taskMapper, StorageService storageService) {
        this.taskMapper = taskMapper;
        this.storageService = storageService;
    }

    public TaskDto get(String taskId, String userId) {
        TaskRow row = taskMapper.findByIdAndUser(taskId, userId);
        if (row == null) {
            throw taskNotFound();
        }
        return TaskDto.from(row);
    }

    public TaskPage history(String userId, int page, int size) {
        if (page < 0) {
            throw fieldError("page", "page 不能小于 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw fieldError("size", "size 必须在 1～" + MAX_PAGE_SIZE + " 之间");
        }
        long total = taskMapper.countByUser(userId);
        long offset = (long) page * size;
        List<TaskDto> items = taskMapper.findByUser(userId, size, offset).stream()
                .map(TaskDto::from)
                .toList();
        return new TaskPage(items, page, size, total);
    }

    public List<TaskDto> active(String userId) {
        return taskMapper.findActiveByUser(userId).stream()
                .map(TaskDto::from)
                .toList();
    }

    public TaskFile result(String taskId, String userId) {
        TaskRow row = taskMapper.findByIdAndUser(taskId, userId);
        if (row == null || !STATUS_SUCCEEDED.equals(row.getStatus()) || row.getResultUrl() == null) {
            throw taskNotFound();
        }
        Path path = storageService.resultPath(taskId);
        if (!Files.isReadable(path)) {
            throw taskNotFound();
        }
        return new TaskFile("image/png", path);
    }

    public List<TaskEventDto> events(String taskId, String userId) {
        TaskRow row = taskMapper.findByIdAndUser(taskId, userId);
        if (row == null) {
            throw taskNotFound();
        }
        return taskMapper.findEventsByTask(taskId).stream()
                .map(TaskEventDto::from)
                .toList();
    }

    private static ApiException taskNotFound() {
        return new ApiException(ErrorCode.TASK_NOT_FOUND, "任务不存在");
    }

    private static ApiException fieldError(String field, String message) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(field, message);
        return new ApiException(ErrorCode.VALIDATION_ERROR, message, fields);
    }

    public record TaskPage(List<TaskDto> items, int page, int size, long total) {
    }

    public record TaskFile(String mimeType, Path path) {
    }
}