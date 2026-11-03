package com.cqr.interviewworkshop.interview.application.jobmatch.application;

import com.cqr.interviewworkshop.agent.dao.entity.AgentPropertiesDO;
import com.cqr.interviewworkshop.interview.application.jobmatch.JobMatchContext;
import com.cqr.interviewworkshop.interview.application.jobmatch.JobMatchResult;
import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.JobKnowledgeRetriever;
import com.cqr.interviewworkshop.interview.application.prompt.core.PromptService;
import com.cqr.interviewworkshop.interview.shared.InterviewAiInvoker;
import com.cqr.interviewworkshop.interview.shared.InterviewResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobMatchServiceTest {

    private InterviewAiInvoker interviewAiInvoker;
    private InterviewResponseParser responseParser;
    private JobKnowledgeRetriever retriever;
    private PromptService promptService;
    private JobMatchToolCallingService toolCallingService;
    private JobMatchService service;

    @BeforeEach
    void setUp() throws Exception {
        interviewAiInvoker = mock(InterviewAiInvoker.class);
        responseParser = mock(InterviewResponseParser.class);
        retriever = mock(JobKnowledgeRetriever.class);
        promptService = mock(PromptService.class);
        toolCallingService = mock(JobMatchToolCallingService.class);
        service = new JobMatchService(
                interviewAiInvoker,
                responseParser,
                retriever,
                promptService,
                toolCallingService
        );

        when(promptService.renderPrompt(anyString(), anyMap())).thenReturn("rendered prompt");
        when(interviewAiInvoker.buildSingleFlightKey(
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn("single-flight-key");
        when(interviewAiInvoker.callAiSyncWithParameters(
                anyString(),
                any(AgentPropertiesDO.class),
                anyMap(),
                anyString(),
                anyString()
        )).thenReturn("workflow response");
    }

    @Test
    void shouldFallbackToLegacyRagWhenToolCallingHasNoEvidence() {
        JobKnowledgeDocument document = new JobKnowledgeDocument();
        document.setContent("Redis cache-aside reference");
        document.setSource("knowledge-base");
        when(toolCallingService.collectResearch(anyString(), anyString(), anyString(), any()))
                .thenReturn(null);
        when(retriever.retrieve(anyString())).thenReturn(List.of(document));
        JobMatchResult expected = result(78);
        when(responseParser.parseAnalyzeResult("workflow response")).thenReturn(expected);

        JobMatchResult actual = service.analyze("s1", "r1", "1", context(), agent());

        assertSame(expected, actual);
        verify(retriever).retrieve(anyString());
        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(promptService).renderPrompt(eq("INTERVIEW_JOB_MATCH"), captor.capture());
        assertTrue(String.valueOf(captor.getValue().get("retrieved_knowledge")).contains("Redis"));
    }

    @Test
    void shouldUseToolEvidenceWithoutRepeatingLegacyRag() {
        when(toolCallingService.collectResearch(anyString(), anyString(), anyString(), any()))
                .thenReturn("verified tool evidence");
        JobMatchResult expected = result(91);
        when(responseParser.parseAnalyzeResult("workflow response")).thenReturn(expected);

        JobMatchResult actual = service.analyze("s1", "r1", "1", context(), agent());

        assertSame(expected, actual);
        verify(retriever, never()).retrieve(anyString());
        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(promptService).renderPrompt(eq("INTERVIEW_JOB_MATCH"), captor.capture());
        assertTrue(String.valueOf(captor.getValue().get("retrieved_knowledge")).contains("verified tool evidence"));
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }

    private JobMatchContext context() {
        JobMatchContext context = new JobMatchContext();
        context.setJobTitle("Java Engineer");
        context.setJobRequirement("Spring and Redis");
        context.setQuestion("How do you design a cache?");
        context.setAnswer("Use cache-aside.");
        return context;
    }

    private AgentPropertiesDO agent() {
        AgentPropertiesDO agent = new AgentPropertiesDO();
        agent.setApiFlowId("flow-1");
        return agent;
    }

    private JobMatchResult result(int score) {
        JobMatchResult result = new JobMatchResult();
        result.setMatchScore(score);
        return result;
    }
}
