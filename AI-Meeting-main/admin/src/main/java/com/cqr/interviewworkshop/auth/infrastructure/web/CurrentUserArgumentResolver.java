package com.cqr.interviewworkshop.auth.infrastructure.web;

import com.cqr.interviewworkshop.auth.application.CurrentUserService;
import com.cqr.interviewworkshop.auth.domain.CurrentPrincipal;
import com.cqr.interviewworkshop.common.convention.annotation.CurrentUser;
import com.cqr.interviewworkshop.common.convention.context.UserContext;
import com.cqr.interviewworkshop.common.convention.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final CurrentUserService currentUserService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentPrincipal principal = currentUserService.requireCurrentPrincipal();
        Class<?> parameterType = parameter.getParameterType();

        if (parameterType.equals(String.class)) {
            return principal.getUsername();
        }
        if (parameterType.equals(Long.class) || parameterType.equals(long.class)) {
            return principal.getUserId();
        }
        if (parameterType.equals(UserContext.class)) {
            return new UserContext(principal.getUserId(), principal.getUsername());
        }

        throw new ClientException("@CurrentUser only supports String, Long/long and UserContext");
    }
}
