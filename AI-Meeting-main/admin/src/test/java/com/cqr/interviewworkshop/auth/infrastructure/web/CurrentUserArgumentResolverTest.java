package com.cqr.interviewworkshop.auth.infrastructure.web;

import com.cqr.interviewworkshop.auth.application.CurrentUserService;
import com.cqr.interviewworkshop.auth.domain.CurrentPrincipal;
import com.cqr.interviewworkshop.common.convention.annotation.CurrentUser;
import com.cqr.interviewworkshop.common.convention.context.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserArgumentResolverTest {

    @Test
    void shouldResolveSupportedCurrentUserParameterTypes() throws Exception {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireCurrentPrincipal()).thenReturn(new CurrentPrincipal(7L, "alice"));
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(currentUserService);

        Method method = TestController.class.getDeclaredMethod("handle", String.class, Long.class, UserContext.class);

        Object username = resolver.resolveArgument(new MethodParameter(method, 0), null, null, null);
        Object userId = resolver.resolveArgument(new MethodParameter(method, 1), null, null, null);
        Object userContext = resolver.resolveArgument(new MethodParameter(method, 2), null, null, null);

        assertEquals("alice", username);
        assertEquals(7L, userId);
        assertEquals(new UserContext(7L, "alice"), userContext);
    }

    private static class TestController {

        @SuppressWarnings("unused")
        public void handle(@CurrentUser String username,
                           @CurrentUser Long userId,
                           @CurrentUser UserContext userContext) {
        }
    }
}
