package com.xiaoying.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoying.auth.AuthUser;
import com.xiaoying.auth.CurrentUserArgumentResolver;
import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.common.GlobalExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 任务接口状态码、归属 404、结果响应头、分页参数与路由优先级。 */
@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    private static final AuthUser DEMO = new AuthUser("usr_demo", "demo", "Demo User");
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final TaskDto TASK = new TaskDto("tsk_1", "tpl", "模板", "SUCCEEDED", "DONE",
            null, null, null, "/api/tasks/tsk_1/result", 5, NOW, NOW, NOW);

    @Mock
    private TaskCreateService taskCreateService;

    @Mock
    private TaskQueryService taskQueryService;

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TaskController(taskCreateService, taskQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .addInterceptors(new org.springframework.web.servlet.HandlerInterceptor() {
                    @Override
                    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                             jakarta.servlet.http.HttpServletResponse response,
                                             Object handler) {
                        request.setAttribute(CurrentUserArgumentResolver.CURRENT_USER_ATTRIBUTE, DEMO);
                        return true;
                    }
                })
                .build();
    }

    @Test
    void createReturns201Task() throws Exception {
        when(taskCreateService.create(eq("usr_demo"), any())).thenReturn(TASK);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateId":"tpl","materialIds":["mat_1"],"params":{"caption":"x"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("tsk_1"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultUrl").value("/api/tasks/tsk_1/result"));
    }

    @Test
    void detailOfOtherUserReturns404Json() throws Exception {
        when(taskQueryService.get("tsk_1", "usr_demo"))
                .thenThrow(new ApiException(ErrorCode.TASK_NOT_FOUND, "任务不存在"));

        mockMvc.perform(get("/api/tasks/tsk_1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void historyReturnsPagedPayload() throws Exception {
        when(taskQueryService.history("usr_demo", 0, 20))
                .thenReturn(new TaskQueryService.TaskPage(List.of(TASK), 0, 20, 1));

        mockMvc.perform(get("/api/tasks").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].id").value("tsk_1"));
    }

    @Test
    void invalidPageTypeReturns400Json() throws Exception {
        mockMvc.perform(get("/api/tasks").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.page").isNotEmpty());
    }

    @Test
    void activeUsesLiteralRouteNotTaskId() throws Exception {
        when(taskQueryService.active("usr_demo")).thenReturn(List.of(TASK));

        mockMvc.perform(get("/api/tasks/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("tsk_1"));
    }

    @Test
    void resultStreamsPngWithPrivateCache() throws Exception {
        Path file = tempDir.resolve("tsk_1.png");
        Files.write(file, new byte[]{1, 2, 3});
        when(taskQueryService.result("tsk_1", "usr_demo"))
                .thenReturn(new TaskQueryService.TaskFile("image/png", file));

        mockMvc.perform(get("/api/tasks/tsk_1/result"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, max-age=60"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void resultOfFailedTaskReturns404() throws Exception {
        when(taskQueryService.result("tsk_1", "usr_demo"))
                .thenThrow(new ApiException(ErrorCode.TASK_NOT_FOUND, "任务不存在"));

        mockMvc.perform(get("/api/tasks/tsk_1/result"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void eventsReturnsItems() throws Exception {
        when(taskQueryService.events("tsk_1", "usr_demo"))
                .thenReturn(List.of(new TaskEventDto(1, "GENERATING", "STAGE_STARTED", null, NOW)));

        mockMvc.perform(get("/api/tasks/tsk_1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stage").value("GENERATING"));
    }
}