package com.xiaoying.template;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.common.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 模板接口不需要登录；分类、筛选、详情与 404。 */
@ExtendWith(MockitoExtension.class)
class TemplateControllerTest {

    private static final TemplateDto POSTCARD = new TemplateDto(
            "demo-postcard", "旅行明信片", "创意图片", "/demo/postcard-cover.png",
            "上传两张图片，模拟生成一张旅行明信片", 2,
            List.of("image/jpeg", "image/png"), 5_242_880L);

    @Mock
    private TemplateService templateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TemplateController(templateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsCategoriesWithCounts() throws Exception {
        when(templateService.categories()).thenReturn(List.of(new CategoryDto("创意图片", 2)));

        mockMvc.perform(get("/api/templates/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("创意图片"))
                .andExpect(jsonPath("$.items[0].count").value(2));
    }

    @Test
    void listsTemplatesFilteredByCategory() throws Exception {
        when(templateService.list("创意图片")).thenReturn(List.of(POSTCARD));

        mockMvc.perform(get("/api/templates").param("category", "创意图片"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("demo-postcard"))
                .andExpect(jsonPath("$.items[0].requiredImageCount").value(2))
                .andExpect(jsonPath("$.items[0].acceptedMimeTypes[0]").value("image/jpeg"));

        verify(templateService).list("创意图片");
    }

    @Test
    void listsAllTemplatesWithoutCategory() throws Exception {
        when(templateService.list(null)).thenReturn(List.of(POSTCARD));

        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        verify(templateService).list(null);
    }

    @Test
    void returnsTemplateDetail() throws Exception {
        when(templateService.get("demo-postcard")).thenReturn(POSTCARD);

        mockMvc.perform(get("/api/templates/demo-postcard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("旅行明信片"))
                .andExpect(jsonPath("$.maxFileSizeBytes").value(5_242_880));
    }

    @Test
    void missingTemplateReturns404Json() throws Exception {
        when(templateService.get("nope"))
                .thenThrow(new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, "模板不存在或已下架"));

        mockMvc.perform(get("/api/templates/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unsupportedMethodReturns405Json() throws Exception {
        mockMvc.perform(post("/api/templates"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}