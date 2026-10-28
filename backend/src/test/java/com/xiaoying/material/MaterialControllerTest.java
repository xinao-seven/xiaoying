package com.xiaoying.material;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoying.auth.AuthUser;
import com.xiaoying.auth.CurrentUserArgumentResolver;
import com.xiaoying.common.ApiException;
import com.xiaoying.common.ApiError;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.common.GlobalExceptionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 覆盖素材接口的成功、415/400/404 与 413 JSON。 */
@ExtendWith(MockitoExtension.class)
class MaterialControllerTest {

    private static final AuthUser DEMO = new AuthUser("usr_demo", "demo", "Demo User");

    @Mock
    private MaterialService materialService;

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MaterialController(materialService))
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
    void uploadReturns201WithMaterial() throws Exception {
        when(materialService.upload(any(), any())).thenReturn(new MaterialDto(
                "mat_01", "/api/materials/mat_01/content", "image/png", 1024, 640, 480,
                java.time.Instant.parse("2026-10-28T00:00:00Z")));

        mockMvc.perform(multipart("/api/materials")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("mat_01"))
                .andExpect(jsonPath("$.url").value("/api/materials/mat_01/content"))
                .andExpect(jsonPath("$.width").value(640));
    }

    @Test
    void uploadRejectsUnreadableImageAsJson400() throws Exception {
        when(materialService.upload(any(), any()))
                .thenThrow(new ApiException(ErrorCode.MATERIAL_UNREADABLE, "图片无法解码，请更换文件后重试"));

        mockMvc.perform(multipart("/api/materials")
                        .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("MATERIAL_UNREADABLE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void contentOfAnotherUserReturns404() throws Exception {
        when(materialService.loadForUser("mat_x", "usr_demo"))
                .thenThrow(new ApiException(ErrorCode.MATERIAL_NOT_FOUND, "素材不存在"));

        mockMvc.perform(get("/api/materials/mat_x/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }

    @Test
    void contentStreamsImageWithPrivateCache() throws Exception {
        Path file = tempDir.resolve("a.png");
        Files.write(file, new byte[]{1, 2, 3, 4});
        when(materialService.loadForUser("mat_01", "usr_demo"))
                .thenReturn(new MaterialService.MaterialFile("image/png", file));

        mockMvc.perform(get("/api/materials/mat_01/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void oversizedUploadMapsToJson413() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiError> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(10 * 1024 * 1024), new MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(413);
        org.assertj.core.api.Assertions.assertThat(response.getBody()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(response.getBody().code()).isEqualTo("PAYLOAD_TOO_LARGE");
        org.assertj.core.api.Assertions.assertThat(response.getBody().traceId()).isNotEmpty();
    }
}