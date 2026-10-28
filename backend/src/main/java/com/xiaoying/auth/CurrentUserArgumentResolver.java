package com.xiaoying.auth;

import com.xiaoying.common.ApiException;
import com.xiaoying.common.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String CURRENT_USER_ATTRIBUTE = "xiaoying.currentUser";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object value = webRequest.getAttribute(CURRENT_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (value instanceof AuthUser user) {
            return user;
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "请先登录");
    }
}