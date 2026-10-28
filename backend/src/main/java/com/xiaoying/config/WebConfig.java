package com.xiaoying.config;

import com.xiaoying.auth.AuthInterceptor;
import com.xiaoying.auth.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(AuthInterceptor authInterceptor, CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.authInterceptor = authInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/templates",
                        "/api/templates/**",
                        "/api/dev/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}