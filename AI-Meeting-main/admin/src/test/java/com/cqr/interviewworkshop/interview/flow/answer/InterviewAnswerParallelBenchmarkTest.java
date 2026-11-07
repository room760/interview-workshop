package com.cqr.interviewworkshop.interview.flow.answer;

import com.alibaba.fastjson2.JSON;
import com.cqr.interviewworkshop.agent.application.BusinessAgentResolver;
import com.cqr.interviewworkshop.agent.dao.entity.AgentPropertiesDO;
import com.cqr.interviewworkshop.interview.api.io.req.InterviewAnswerReqDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewAnswerRespDTO;
import com.cqr.interviewworkshop.interview.application.flow.InterviewFlowStateMachine;
import com.cqr.interviewworkshop.interview.application.jobmatch.application.JobMatchService;
import com.cqr.interviewworkshop.interview.service.InterviewQuestionCacheService;
import com.cqr.interviewworkshop.interview.shared.InterviewResponseParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewAnswerParallelBenchmarkTest {
    private static final long MOCK_AGENT_SLEEP_MS = 120L;
    private static final int PERFORMANCE_RUNS = 100;
    private final BusinessAgentResolver resolver = mock(BusinessAgentResolver.class);
    private final InterviewQuestionCacheService questionCache = mock(InterviewQuestionCacheService.class);
    private final InterviewEvaluationService evaluation = mock(InterviewEvaluationService.class);
    private final InterviewFlowStateMachine flow = mock(InterviewFlowStateMachine.class);
    private final JobMatchService jobMatch = mock(JobMatchService.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private InterviewAnswerPipeline pipeline;

    @BeforeEach
    void setUp() throws Exception {
        pipeline = new InterviewAnswerPipeline(resolver, questionCache, evaluation, null,
                new InterviewResponseParser(), flow, null, null, null, null, null, null, jobMatch);
        Field executorField = InterviewAnswerPipeline.class.getDeclaredField("agentExecutor");
        executorField.setAccessible(true);
        executorField.set(pipeline, executor);
        when(resolver.resolveRequired(any())).thenReturn(new AgentPropertiesDO());
        when(questionCache.getSessionTotalScore(anyString())).thenReturn(0);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void measuresActualParallelBranchAgainstSyntheticSerialBaseline() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        when(evaluation.evaluateAnswer(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(ignored -> {
                    mockWork(running, maxRunning);
                    return Map.of("score", 80, "feedback", "ok", "follow_up_needed", false);
                });
        when(jobMatch.analyze(anyString(), anyString(), anyString(), any(), any()))
                .thenAnswer(ignored -> {
                    mockWork(running, maxRunning);
                    return null;
                });

        Path raw = rawPath("multi-agent.jsonl");
        Files.deleteIfExists(raw);
        for (int run = 1; run <= PERFORMANCE_RUNS; run++) {
            long serialStart = System.nanoTime();
            Thread.sleep(MOCK_AGENT_SLEEP_MS);
            Thread.sleep(MOCK_AGENT_SLEEP_MS);
            double serialMs = elapsedMs(serialStart);

            long parallelStart = System.nanoTime();
            Object context = context();
            assertTrue(evaluateStep(context));
            double parallelMs = elapsedMs(parallelStart);
            assertEquals(2, maxRunning.get(), "both real pipeline tasks must overlap");
            assertEquals(80, ((InterviewAnswerRespDTO) field(context, "response")).getScore());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("run", run);
            row.put("serialBaselineMs", serialMs);
            row.put("productionParallelBranchMs", parallelMs);
            row.put("mockAgentSleepMs", MOCK_AGENT_SLEEP_MS);
            row.put("maxSimultaneouslyRunning", maxRunning.get());
            row.put("realModelCalls", 0);
            Files.writeString(raw, JSON.toJSONString(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    @Test
    void nonCriticalAgentFailureFallsBackButEvaluationFailurePropagates() throws Exception {
        when(evaluation.evaluateAnswer(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Map.of("score", 80, "follow_up_needed", false));
        when(jobMatch.analyze(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("job match unavailable"));
        Object fallback = context();
        assertTrue(evaluateStep(fallback));
        assertEquals(80, ((InterviewAnswerRespDTO) field(fallback, "response")).getScore());
        assertEquals(null, field(fallback, "jobMatchResult"));

        org.mockito.Mockito.reset(evaluation, jobMatch);
        when(evaluation.evaluateAnswer(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("evaluation unavailable"));
        Object criticalFailure = context();
        assertFalse(evaluateStep(criticalFailure));
        assertTrue(((InterviewAnswerRespDTO) field(criticalFailure, "response"))
                .getErrorMessage().contains("evaluation unavailable"));
    }

    @Test
    void nonCriticalAgentTimesOutAtProductionThirtySecondLimit() throws Exception {
        CountDownLatch neverCompletes = new CountDownLatch(1);
        when(evaluation.evaluateAnswer(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Map.of("score", 80, "follow_up_needed", false));
        when(jobMatch.analyze(anyString(), anyString(), anyString(), any(), any()))
                .thenAnswer(ignored -> {
                    neverCompletes.await(35, TimeUnit.SECONDS);
                    return null;
                });

        Object context = context();
        long started = System.nanoTime();
        assertTrue(evaluateStep(context));
        double elapsed = elapsedMs(started);
        assertEquals(80, ((InterviewAnswerRespDTO) field(context, "response")).getScore());
        assertEquals(null, field(context, "jobMatchResult"));
        assertTrue(elapsed >= 29_000, "the actual production timeout must fire");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenario", "job_match_timeout_fallback");
        row.put("productionTimeoutSeconds", 30);
        row.put("observedElapsedMs", elapsed);
        row.put("mainEvaluationSucceeded", true);
        row.put("jobMatchAvailable", false);
        row.put("realModelCalls", 0);
        Files.writeString(rawPath("multi-agent-timeout.json"), JSON.toJSONString(row) + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void mockWork(AtomicInteger running, AtomicInteger maxRunning) throws InterruptedException {
        int current = running.incrementAndGet();
        maxRunning.accumulateAndGet(current, Math::max);
        try {
            Thread.sleep(MOCK_AGENT_SLEEP_MS);
        } finally {
            running.decrementAndGet();
        }
    }

    private static double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    private static Object context() throws Exception {
        Class<?> type = Class.forName(InterviewAnswerPipeline.class.getName() + "$InterviewAnswerPipelineContext");
        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object context = ctor.newInstance();
        InterviewAnswerReqDTO request = new InterviewAnswerReqDTO();
        request.setAnswerContent("fixed answer");
        request.setQuestionNumber("1");
        setField(context, "sessionId", "benchmark-session");
        setField(context, "requestId", "benchmark-request");
        setField(context, "currentQuestionNumber", "1");
        setField(context, "currentQuestion", "fixed question");
        setField(context, "requestParam", request);
        setField(context, "response", InterviewAnswerRespDTO.init());
        return context;
    }

    private boolean evaluateStep(Object context) throws Exception {
        Method method = InterviewAnswerPipeline.class.getDeclaredMethod("stepEvaluateAndScore", context.getClass());
        method.setAccessible(true);
        return (boolean) method.invoke(pipeline, context);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Path rawPath(String name) throws Exception {
        Path directory = Path.of("..", "..", "docs", "benchmark", "raw").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        return directory.resolve(name);
    }
}
