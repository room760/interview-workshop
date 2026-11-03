package com.cqr.interviewworkshop.assistant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cqr.interviewworkshop.assistant.api.io.req.AssistantRoutingReqDTO;
import com.cqr.interviewworkshop.assistant.application.routing.AssistantCoordinator;
import com.cqr.interviewworkshop.assistant.application.routing.AssistantCoordinatorResult;
import com.cqr.interviewworkshop.assistant.application.routing.AssistantIntent;
import com.cqr.interviewworkshop.common.convention.annotation.CurrentUser;
import com.cqr.interviewworkshop.common.convention.context.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantRoutingControllerTest {

    private MockMvc mockMvc;
    private AssistantCoordinator assistantCoordinator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        assistantCoordinator = mock(AssistantCoordinator.class);
        AssistantRoutingController controller = new AssistantRoutingController(assistantCoordinator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new MockCurrentUserResolver())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldRouteRequestWithAuthenticatedUserId() throws Exception {
        AssistantRoutingReqDTO request = new AssistantRoutingReqDTO();
        request.setMessage("这是我的面试回答");
        request.setRequestId("request-1");
        request.setSessionId("session-1");
        request.setQuestionNumber("1");

        AssistantCoordinatorResult coordinatorResult = new AssistantCoordinatorResult();
        coordinatorResult.setIntent(AssistantIntent.CLARIFY);
        coordinatorResult.setMessage("请进一步说明你希望完成的任务。");
        when(assistantCoordinator.handle(
                "这是我的面试回答",
                "request-1",
                "session-1",
                "1",
                7001L
        )).thenReturn(coordinatorResult);

        mockMvc.perform(post("/api/interview-workshop/v1/assistant/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.intent").value("CLARIFY"))
                .andExpect(jsonPath("$.data.message").value("请进一步说明你希望完成的任务。"));

        verify(assistantCoordinator).handle(
                "这是我的面试回答",
                "request-1",
                "session-1",
                "1",
                7001L
        );
    }

    private static final class MockCurrentUserResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class)
                    && UserContext.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return new UserContext(7001L, "routing-user");
        }
    }
}
