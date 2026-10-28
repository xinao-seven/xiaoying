package com.xiaoying.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import com.xiaoying.common.GlobalExceptionHandler;
import com.xiaoying.config.AppProperties;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** G1：登录成功/失败、Cookie 属性、登出、未登录 401，全部输出 JSON。 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final AuthUser DEMO = new AuthUser("usr_demo", "demo", "Demo User");

    @Mock
    private AuthService authService;
    @Mock
    private SessionService sessionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        AuthController controller = new AuthController(authService, sessionService, new CookieFactory(properties));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .addInterceptors(new AuthInterceptor(sessionService) {
                    @Override
                    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                             jakarta.servlet.http.HttpServletResponse response,
                                             Object handler) {
                        // standalone MockMvc 没有路径匹配，这里复刻登录接口放行规则
                        if ("/api/auth/login".equals(request.getRequestURI())) {
                            return true;
                        }
                        return super.preHandle(request, response, handler);
                    }
                })
                .build();
    }

    @Test
    void loginReturnsUserAndSessionCookie() throws Exception {
        when(authService.authenticate("demo", "demo1234")).thenReturn(DEMO);
        when(sessionService.issue("usr_demo")).thenReturn("token-value");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"demo1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("usr_demo"))
                .andExpect(jsonPath("$.user.username").value("demo"))
                .andExpect(jsonPath("$.user.displayName").value("Demo User"))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("XIAOYING_SESSION=token-value"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"),
                                org.hamcrest.Matchers.containsString("Path=/"),
                                org.hamcrest.Matchers.containsString("Max-Age=604800"))));
    }

    @Test
    void loginWithWrongPasswordReturnsJson401() throws Exception {
        when(authService.authenticate("demo", "bad"))
                .thenThrow(new ApiException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"demo\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void loginWithBlankFieldsReturnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void meWithoutSessionReturnsJson401() throws Exception {
        when(sessionService.resolve(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void meWithValidSessionReturnsCurrentUser() throws Exception {
        when(sessionService.resolve("token-value")).thenReturn(Optional.of(DEMO));

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("XIAOYING_SESSION", "token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("usr_demo"))
                .andExpect(jsonPath("$.username").value("demo"));
    }

    @Test
    void logoutDeletesSessionAndClearsCookie() throws Exception {
        when(sessionService.resolve("token-value")).thenReturn(Optional.of(DEMO));

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("XIAOYING_SESSION", "token-value")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("XIAOYING_SESSION="),
                                org.hamcrest.Matchers.containsString("Max-Age=0"))));

        verify(sessionService).delete("token-value");
    }

    @Test
    void logoutWithoutSessionReturns401() throws Exception {
        when(sessionService.resolve(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}