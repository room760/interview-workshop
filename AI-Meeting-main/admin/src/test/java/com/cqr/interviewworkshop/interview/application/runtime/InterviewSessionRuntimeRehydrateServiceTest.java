package com.cqr.interviewworkshop.interview.application.runtime;

import com.cqr.interviewworkshop.interview.dao.mapper.InterviewRecordMapper;
import com.cqr.interviewworkshop.interview.dao.entity.InterviewSessionRuntimeSnapshot;
import com.cqr.interviewworkshop.interview.service.InterviewQuestionCacheService;
import com.cqr.interviewworkshop.interview.service.InterviewQuestionService;
import com.cqr.interviewworkshop.interview.service.InterviewSessionService;
import com.cqr.interviewworkshop.interview.service.model.InterviewRuntimeConfidence;
import com.cqr.interviewworkshop.interview.service.model.InterviewRuntimeLoadMode;
import com.cqr.interviewworkshop.interview.service.model.InterviewFlowState;
import org.redisson.api.RLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class InterviewSessionRuntimeRehydrateServiceTest {

    @Mock
    private InterviewSessionRuntimeSnapshotService runtimeSnapshotService;

    @Mock
    private InterviewSessionRuntimeLockService runtimeLockService;

    @Mock
    private InterviewSessionService interviewSessionService;

    @Mock
    private InterviewQuestionService interviewQuestionService;

    @Mock
    private InterviewQuestionCacheService interviewQuestionCacheService;

    @Mock
    private InterviewRecordMapper interviewRecordMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private InterviewSessionRuntimeRehydrateService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSessionRuntimeRehydrateService(
                runtimeSnapshotService,
                runtimeLockService,
                interviewSessionService,
                interviewQuestionService,
                interviewQuestionCacheService,
                interviewRecordMapper,
                stringRedisTemplate
        );
    }

    @Test
    void shouldNotRebuildRuntimeWhenRehydrateLockCannotBeAcquired() throws InterruptedException {
        String sessionId = "session-1";

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.size(anyString())).thenReturn(0L);
        when(runtimeLockService.acquire(sessionId)).thenReturn(null);
        when(runtimeLockService.acquire(sessionId, 80L)).thenReturn(null);
        when(runtimeSnapshotService.findSnapshot(sessionId)).thenReturn(Optional.empty());

        InterviewSessionRuntimeView result = service.ensureRuntime(
                sessionId,
                InterviewRuntimeLoadMode.READ_WRITE_REQUIRED,
                InterviewRuntimeRehydrateScope.FULL_RUNTIME
        );

        assertAll(
                () -> assertEquals(InterviewRuntimeLoadMode.READ_WRITE_REQUIRED, result.getLoadMode()),
                () -> assertEquals(InterviewRuntimeRestoreSource.NONE, result.getRestoreSource()),
                () -> assertEquals(InterviewRuntimeConfidence.READ_ONLY, result.getConfidence()),
                () -> assertFalse(result.isCacheRebuilt()),
                () -> assertFalse(result.canWrite()),
                () -> assertNull(result.getSnapshot())
        );

        verify(runtimeLockService).acquire(sessionId);
        verify(runtimeLockService).acquire(sessionId, 80L);
        verify(runtimeLockService).release(isNull());
        verify(interviewRecordMapper, never()).selectOne(any());
        verify(interviewSessionService, never()).getBySessionId(anyString());
        verify(interviewQuestionService, never()).getBySessionId(anyString());
    }

    @Test
    void shouldRemainReadOnlyWhenRedisAndAllDurableSourcesAreMissing() throws InterruptedException {
        String sessionId = "missing-sources";
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.size(anyString())).thenReturn(0L);
        when(runtimeLockService.acquire(sessionId)).thenReturn(mock(RLock.class));
        when(runtimeSnapshotService.findSnapshot(sessionId)).thenReturn(Optional.empty());

        InterviewSessionRuntimeView result = service.ensureRuntime(
                sessionId, InterviewRuntimeLoadMode.READ_WRITE_REQUIRED, InterviewRuntimeRehydrateScope.FLOW_ONLY);

        assertEquals(InterviewRuntimeRestoreSource.NONE, result.getRestoreSource());
        assertEquals(InterviewRuntimeConfidence.READ_ONLY, result.getConfidence());
        assertFalse(result.isCacheRebuilt());
        assertFalse(result.canWrite());
    }

    @Test
    void shouldRestoreFlowOnlyFromCompleteSnapshot() throws InterruptedException {
        String sessionId = "complete-snapshot";
        InterviewFlowState flow = new InterviewFlowState();
        flow.setStatus("READY");
        flow.setCurrentIndex(0);
        flow.setTotalQuestions(1);
        InterviewSessionRuntimeSnapshot snapshot = new InterviewSessionRuntimeSnapshot();
        snapshot.setSessionId(sessionId);
        snapshot.setQuestions(Map.of("1", "Java question"));
        snapshot.setFlow(flow);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.size(anyString())).thenReturn(0L);
        when(runtimeLockService.acquire(sessionId)).thenReturn(mock(RLock.class));
        when(runtimeSnapshotService.findSnapshot(sessionId)).thenReturn(Optional.of(snapshot));
        when(runtimeSnapshotService.findHotSnapshot(sessionId)).thenReturn(Optional.empty());
        when(runtimeSnapshotService.findColdSnapshot(sessionId)).thenReturn(Optional.empty());

        InterviewSessionRuntimeView result = service.ensureRuntime(
                sessionId, InterviewRuntimeLoadMode.READ_WRITE_REQUIRED, InterviewRuntimeRehydrateScope.FLOW_ONLY);

        assertEquals(InterviewRuntimeRestoreSource.RUNTIME_SNAPSHOT, result.getRestoreSource());
        assertEquals(InterviewRuntimeConfidence.EXACT, result.getConfidence());
        assertEquals(true, result.isCacheRebuilt());
        verify(interviewQuestionCacheService).restoreInterviewFlow(sessionId, flow);
        verify(hashOperations).putAll(anyString(), any());
    }

    @Test
    void shouldNotCallIncompleteFlowSnapshotExact() throws InterruptedException {
        String sessionId = "incomplete-flow";
        InterviewSessionRuntimeSnapshot snapshot = new InterviewSessionRuntimeSnapshot();
        snapshot.setSessionId(sessionId);
        snapshot.setQuestions(Map.of("1", "Java question"));
        // Flow and turns are absent: the snapshot cannot prove an exact runtime restore.
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.size(anyString())).thenReturn(0L);
        when(runtimeLockService.acquire(sessionId)).thenReturn(mock(RLock.class));
        when(runtimeSnapshotService.findSnapshot(sessionId)).thenReturn(Optional.of(snapshot));

        InterviewSessionRuntimeView result = service.ensureRuntime(
                sessionId, InterviewRuntimeLoadMode.READ_WRITE_REQUIRED, InterviewRuntimeRehydrateScope.FLOW_ONLY);

        assertFalse(result.getConfidence() == InterviewRuntimeConfidence.EXACT);
        assertFalse(result.canWrite());
        verify(interviewQuestionCacheService, never()).restoreInterviewFlow(anyString(), any());
    }

    @Test
    void shouldReportFailureWhenRedisWriteBreaksDuringSnapshotRestore() throws InterruptedException {
        String sessionId = "partial-redis-write";
        InterviewFlowState flow = new InterviewFlowState();
        flow.setStatus("READY");
        flow.setCurrentIndex(0);
        flow.setTotalQuestions(1);
        InterviewSessionRuntimeSnapshot snapshot = new InterviewSessionRuntimeSnapshot();
        snapshot.setSessionId(sessionId);
        snapshot.setQuestions(Map.of("1", "Java question"));
        snapshot.setFlow(flow);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.size(anyString())).thenReturn(0L);
        when(runtimeLockService.acquire(sessionId)).thenReturn(mock(RLock.class));
        when(runtimeSnapshotService.findSnapshot(sessionId)).thenReturn(Optional.of(snapshot));
        when(runtimeSnapshotService.findHotSnapshot(sessionId)).thenReturn(Optional.empty());
        when(runtimeSnapshotService.findColdSnapshot(sessionId)).thenReturn(Optional.empty());
        // Questions are written first; fail the later Flow write to leave a partial cache.
        doThrow(new IllegalStateException("redis flow write interrupted"))
                .when(interviewQuestionCacheService).restoreInterviewFlow(sessionId, flow);

        InterviewSessionRuntimeView result = service.ensureRuntime(
                sessionId, InterviewRuntimeLoadMode.READ_WRITE_REQUIRED, InterviewRuntimeRehydrateScope.FLOW_ONLY);

        assertEquals(InterviewRuntimeRestoreSource.NONE, result.getRestoreSource());
        assertEquals(InterviewRuntimeConfidence.READ_ONLY, result.getConfidence());
        assertFalse(result.isCacheRebuilt());
        assertFalse(result.canWrite());
        verify(hashOperations).putAll(anyString(), any());
        verify(interviewQuestionCacheService).restoreInterviewFlow(sessionId, flow);
    }
}
