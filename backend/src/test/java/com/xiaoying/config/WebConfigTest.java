package com.xiaoying.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.xiaoying.auth.AuthInterceptor;
import com.xiaoying.auth.CurrentUserArgumentResolver;
import com.xiaoying.auth.SessionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

/** 校验拦截器只覆盖 /api/**，并正确放行登录、模板与开发接口。 */
class WebConfigTest {

    @Test
    void authInterceptorProtectsApiAndSkipsPublicPaths() {
        WebConfig config = new WebConfig(new AuthInterceptor(mock(SessionService.class)),
                new CurrentUserArgumentResolver());
        ExposingRegistry registry = new ExposingRegistry();

        config.addInterceptors(registry);

        List<Object> interceptors = registry.interceptors();
        assertThat(interceptors).hasSize(1);
        MappedInterceptor mapped = (MappedInterceptor) interceptors.get(0);
        assertThat(mapped.getIncludePathPatterns()).containsExactly("/api/**");
        assertThat(mapped.getExcludePathPatterns()).contains(
                "/api/auth/login",
                "/api/templates",
                "/api/templates/**",
                "/api/dev/**");
    }

    /** getInterceptors() 是 protected，测试里暴露出来做断言。 */
    static final class ExposingRegistry extends InterceptorRegistry {

        List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
