package com.xiaoying.task;

import com.xiaoying.auth.AuthUser;
import com.xiaoying.auth.CurrentUser;
import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskCreateService taskCreateService;
    private final TaskQueryService taskQueryService;

    public TaskController(TaskCreateService taskCreateService, TaskQueryService taskQueryService) {
        this.taskCreateService = taskCreateService;
        this.taskQueryService = taskQueryService;
    }

    /** 受理立即返回 201，不等待任何阶段。 */
    @PostMapping
    public ResponseEntity<TaskDto> create(@Valid @RequestBody TaskCreateRequest request,
                                          @CurrentUser AuthUser user) {
        return ResponseEntity.status(201).body(taskCreateService.create(user.id(), request));
    }

    @GetMapping("/{id}")
    public TaskDto detail(@PathVariable("id") String id, @CurrentUser AuthUser user) {
        return taskQueryService.get(id, user.id());
    }

    @GetMapping
    public TaskQueryService.TaskPage history(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @CurrentUser AuthUser user) {
        return taskQueryService.history(user.id(), page, size);
    }

    @GetMapping("/active")
    public TaskListResponse active(@CurrentUser AuthUser user) {
        return new TaskListResponse(taskQueryService.active(user.id()));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<Resource> result(@PathVariable("id") String id,
                                           @CurrentUser AuthUser user) {
        TaskQueryService.TaskFile file = taskQueryService.result(id, user.id());
        long length;
        try {
            length = Files.size(file.path());
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.TASK_NOT_FOUND, "任务结果不存在");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .contentLength(length)
                .body(new FileSystemResource(file.path()));
    }

    @GetMapping("/{id}/events")
    public TaskEventListResponse events(@PathVariable("id") String id,
                                        @CurrentUser AuthUser user) {
        return new TaskEventListResponse(taskQueryService.events(id, user.id()));
    }

    public record TaskListResponse(List<TaskDto> items) {
    }

    public record TaskEventListResponse(List<TaskEventDto> items) {
    }
}