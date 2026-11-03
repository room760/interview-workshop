package com.cqr.interviewworkshop.interview.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cqr.interviewworkshop.interview.api.io.req.InterviewConversationPageReqDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewConversationRespDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewSessionCreateRespDTO;
import com.cqr.interviewworkshop.interview.dao.entity.InterviewSession;

public interface InterviewSessionService {

    InterviewSessionCreateRespDTO createSession(Long userId);

    IPage<InterviewConversationRespDTO> pageConversations(Long userId, InterviewConversationPageReqDTO requestParam);

    InterviewSession getBySessionId(String sessionId);

    InterviewSession requireOwnedSession(String sessionId, Long userId);

    void markResumeUploading(String sessionId, Long userId);

    void markReady(String sessionId, Long userId, String resumeFileUrl, String interviewType);

    void markDraft(String sessionId, Long userId);

    void markInProgressIfReady(String sessionId, Long userId);

    void finishSession(String sessionId, Long userId);

    void abandonActiveSessions(Long userId);
}
