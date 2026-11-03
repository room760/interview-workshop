package com.cqr.interviewworkshop.interview.service.impl;

import com.cqr.interviewworkshop.interview.application.InterviewSessionOwnershipService;
import com.cqr.interviewworkshop.interview.application.finalize.InterviewFinalizeLockService;
import com.cqr.interviewworkshop.interview.application.runtime.InterviewSessionRuntimeRehydrateService;
import com.cqr.interviewworkshop.interview.application.runtime.InterviewSessionRuntimeSnapshotService;
import com.cqr.interviewworkshop.interview.dao.entity.InterviewRecordDO;
import com.cqr.interviewworkshop.interview.dao.entity.InterviewSession;
import com.cqr.interviewworkshop.interview.dao.mapper.InterviewRecordMapper;
import com.cqr.interviewworkshop.interview.flow.report.InterviewRecordServiceImpl;
import com.cqr.interviewworkshop.interview.service.InterviewQuestionCacheService;
import com.cqr.interviewworkshop.interview.service.InterviewQuestionService;
import com.cqr.interviewworkshop.interview.service.InterviewSessionService;
import com.cqr.interviewworkshop.interview.service.model.InterviewTurnLog;
import com.cqr.interviewworkshop.interview.service.model.InterviewSessionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewRecordServiceImplTest {

    @Test
    void shouldFinishInterviewSessionBeforePersistingRecordFromRedis() throws Exception {
        InterviewQuestionCacheService cacheService = mock(InterviewQuestionCacheService.class);
        InterviewSessionOwnershipService ownershipService = mock(InterviewSessionOwnershipService.class);
        InterviewSessionService sessionService = mock(InterviewSessionService.class);
        InterviewQuestionService questionService = mock(InterviewQuestionService.class);
        InterviewFinalizeLockService finalizeLockService = mock(InterviewFinalizeLockService.class);
        InterviewSessionRuntimeSnapshotService runtimeSnapshotService = mock(InterviewSessionRuntimeSnapshotService.class);
        InterviewSessionRuntimeRehydrateService runtimeRehydrateService = mock(InterviewSessionRuntimeRehydrateService.class);
        InterviewRecordMapper mapper = mock(InterviewRecordMapper.class);
        InterviewRecordServiceImpl service = new InterviewRecordServiceImpl(
                cacheService,
                ownershipService,
                sessionService,
                questionService,
                finalizeLockService,
                runtimeSnapshotService,
                runtimeRehydrateService
        );
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        RLock finalizeLock = mock(RLock.class);
        when(finalizeLockService.acquire("interview-session-1")).thenReturn(finalizeLock);

        InterviewSession readySession = new InterviewSession();
        readySession.setSessionId("interview-session-1");
        readySession.setUserId(1001L);
        readySession.setStatus(InterviewSessionStatus.READY.name());
        readySession.setInterviewerAgentId(9L);
        readySession.setInterviewType("backend");
        readySession.setResumeFileUrl("https://example.com/resume.pdf");
        readySession.setCreateTime(new Date(System.currentTimeMillis() - 120_000));
        readySession.setStartTime(new Date(System.currentTimeMillis() - 60_000));
        readySession.setEndTime(new Date());

        InterviewSession finishedSession = new InterviewSession();
        finishedSession.setSessionId("interview-session-1");
        finishedSession.setUserId(1001L);
        finishedSession.setStatus(InterviewSessionStatus.FINISHED.name());
        finishedSession.setInterviewerAgentId(9L);
        finishedSession.setInterviewType("backend");
        finishedSession.setResumeFileUrl("https://example.com/resume.pdf");
        finishedSession.setCreateTime(readySession.getCreateTime());
        finishedSession.setStartTime(readySession.getStartTime());
        finishedSession.setEndTime(readySession.getEndTime());

        when(ownershipService.requireOwnedSession("interview-session-1", 1001L))
                .thenReturn(readySession, readySession, finishedSession);

        when(cacheService.getSessionTotalScore("interview-session-1")).thenReturn(92);
        when(cacheService.getSessionInterviewSuggestions("interview-session-1")).thenReturn(Map.of("1", "Structured answer"));
        when(cacheService.getSessionResumeScore("interview-session-1")).thenReturn(86);
        when(cacheService.getSessionInterviewQuestions("interview-session-1")).thenReturn(Map.of("1", "Describe JVM tuning"));
        when(cacheService.getSessionInterviewDirection("interview-session-1")).thenReturn("backend");
        when(questionService.getBySessionId("interview-session-1")).thenReturn(null);
        when(cacheService.getInterviewTurns("interview-session-1")).thenReturn(List.of(
                InterviewTurnLog.builder()
                        .questionNumber("1")
                        .score(88)
                        .feedback("Answer structure is clear and supported by a concrete project example.")
                        .build()
        ));
        InterviewRecordDO existingRecord = new InterviewRecordDO();
        existingRecord.setId(1L);
        existingRecord.setUserId(1001L);
        existingRecord.setSessionId("interview-session-1");
        existingRecord.setCreateTime(new Date(System.currentTimeMillis() - 30_000));
        when(mapper.selectOne(any())).thenReturn(null, existingRecord);
        when(mapper.insert(any(InterviewRecordDO.class))).thenReturn(1);
        when(mapper.updateById(any(InterviewRecordDO.class))).thenReturn(1);

        service.saveInterviewRecordFromRedis("interview-session-1", 1001L);

        InOrder inOrder = inOrder(ownershipService, mapper, sessionService);
        inOrder.verify(ownershipService, calls(2)).requireOwnedSession("interview-session-1", 1001L);
        inOrder.verify(mapper, calls(1)).selectOne(any());
        inOrder.verify(mapper).insert(any(InterviewRecordDO.class));
        inOrder.verify(sessionService).finishSession("interview-session-1", 1001L);
        inOrder.verify(ownershipService, calls(1)).requireOwnedSession("interview-session-1", 1001L);
        inOrder.verify(mapper, calls(1)).selectOne(any());
        inOrder.verify(mapper).updateById(any(InterviewRecordDO.class));
        ArgumentCaptor<InterviewRecordDO> recordCaptor = ArgumentCaptor.forClass(InterviewRecordDO.class);
        verify(mapper).updateById(recordCaptor.capture());

        InterviewRecordDO record = recordCaptor.getValue();
        assertEquals("interview-session-1", record.getSessionId());
        assertEquals(1001L, record.getUserId());
        assertEquals(InterviewSessionStatus.FINISHED.name(), record.getInterviewStatus());
        assertEquals(9L, record.getInterviewerAgentId());
        assertEquals(92, record.getInterviewScore());
        assertEquals(86, record.getResumeScore());
        assertTrue(record.getSessionSnapshotJson().contains("\"sessionStatus\":\"FINISHED\""));
        assertTrue(record.getSessionSnapshotJson().contains("\"reviewFeedback\""));
    }
}
