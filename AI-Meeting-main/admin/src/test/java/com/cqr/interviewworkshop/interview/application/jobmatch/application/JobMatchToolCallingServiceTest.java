package com.cqr.interviewworkshop.interview.application.jobmatch.application;

import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
import com.cqr.interviewworkshop.ai.service.AiPropertiesService;
import com.cqr.interviewworkshop.interview.application.jobmatch.JobMatchContext;
import com.cqr.interviewworkshop.interview.application.toolcalling.config.ToolCallingProperties;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolCallingRequest;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolCallingResponse;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolResult;
import com.cqr.interviewworkshop.interview.application.toolcalling.strategy.ToolCallingStrategy;
import com.cqr.interviewworkshop.interview.application.toolcalling.strategy.ToolCallingStrategyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobMatchToolCallingServiceTest {

    private AiPropertiesService aiPropertiesService;
    private ToolCallingStrategy strategy;
    private JobMatchToolCallingService service;

    @BeforeEach
    void setUp() {
        aiPropertiesService = mock(AiPropertiesService.class);
        ToolCallingProperties properties = new ToolCallingProperties();
        properties.setJobMatchAiPropertiesId(4L);
        ToolCallingStrategyResolver resolver = mock(ToolCallingStrategyResolver.class);
        strategy = mock(ToolCallingStrategy.class);

        AiPropertiesDO aiProperties = new AiPropertiesDO();
        aiProperties.setId(4L);
        aiProperties.setIsEnabled(1);
        aiProperties.setDelFlag(0);
        when(aiPropertiesService.getById(4L)).thenReturn(aiProperties);
        when(resolver.resolve(aiProperties)).thenReturn(strategy);

        service = new JobMatchToolCallingService(
                aiPropertiesService,
                properties,
                resolver
        );
    }

    @Test
    void shouldIgnoreKnowledgeToolWithZeroResults() {
        when(strategy.execute(any(ToolCallingRequest.class))).thenReturn(successResponse(
                ToolResult.success("call-1", "job_knowledge_search", "{\"count\":0,\"results\":[]}")
        ));

        assertNull(service.collectResearch("s1", "r1", "1", context()));
    }

    @Test
    void shouldIgnoreJobPostingToolWithZeroResults() {
        when(strategy.execute(any(ToolCallingRequest.class))).thenReturn(successResponse(
                ToolResult.success("call-2", "job_posting_search", "{\"resultCount\":0,\"results\":[]}")
        ));

        assertNull(service.collectResearch("s1", "r1", "1", context()));
    }

    @Test
    void shouldIgnoreFailedToolExecution() {
        when(strategy.execute(any(ToolCallingRequest.class))).thenReturn(successResponse(
                ToolResult.failure("call-3", "job_knowledge_search", "Tool execution failed")
        ));

        assertNull(service.collectResearch("s1", "r1", "1", context()));
    }

    @Test
    void shouldReturnOnlyUsableEvidenceAndPassAllowedTools() {
        when(strategy.execute(any(ToolCallingRequest.class))).thenReturn(successResponse(
                ToolResult.success("call-4", "job_knowledge_search", "{\"count\":1,\"results\":[{\"content\":\"Spring\"}]}")
        ));

        String research = service.collectResearch("s1", "r1", "1", context());

        assertTrue(research.contains("Spring"));
        ArgumentCaptor<ToolCallingRequest> captor = ArgumentCaptor.forClass(ToolCallingRequest.class);
        verify(strategy).execute(captor.capture());
        assertTrue(captor.getValue().getAllowedToolNames().contains("job_knowledge_search"));
        assertTrue(captor.getValue().getAllowedToolNames().contains("job_posting_search"));
    }

    private ToolCallingResponse successResponse(ToolResult result) {
        ToolCallingResponse response = new ToolCallingResponse();
        response.setSuccess(true);
        response.setFinalAnswer("research complete");
        response.setToolResults(List.of(result));
        return response;
    }

    private JobMatchContext context() {
        JobMatchContext context = new JobMatchContext();
        context.setJobTitle("Java Engineer");
        context.setJobRequirement("Spring and Redis");
        context.setQuestion("How do you design a cache?");
        context.setAnswer("Use cache-aside.");
        return context;
    }
}
