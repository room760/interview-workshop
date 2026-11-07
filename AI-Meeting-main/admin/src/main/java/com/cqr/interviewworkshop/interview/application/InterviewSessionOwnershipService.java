package com.cqr.interviewworkshop.interview.application;

import cn.hutool.core.util.StrUtil;
import com.cqr.interviewworkshop.common.convention.exception.ClientException;
import com.cqr.interviewworkshop.common.enums.InterviewErrorCodeEnum;
import com.cqr.interviewworkshop.interview.dao.entity.InterviewSession;
import com.cqr.interviewworkshop.interview.dao.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewSessionOwnershipService {

    private final InterviewSessionRepository interviewSessionRepository;

    public InterviewSession requireOwnedSession(String sessionId, Long userId) {
        if (StrUtil.isBlank(sessionId)) {
            throw new ClientException(InterviewErrorCodeEnum.SESSION_ID_EMPTY);
        }
        if (userId == null || userId <= 0) {
            throw new ClientException(InterviewErrorCodeEnum.INVALID_USER_ID);
        }

        InterviewSession session = interviewSessionRepository.findBySessionIdAndDelFlag(sessionId, 0)
                .orElseThrow(() -> new ClientException(InterviewErrorCodeEnum.INTERVIEW_SESSION_NOT_FOUND));
        if (!userId.equals(session.getUserId())) {
            throw new ClientException(InterviewErrorCodeEnum.INTERVIEW_SESSION_ACCESS_DENIED);
        }

        return session;
    }
}
