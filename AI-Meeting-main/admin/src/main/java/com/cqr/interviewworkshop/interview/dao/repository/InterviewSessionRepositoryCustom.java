package com.cqr.interviewworkshop.interview.dao.repository;

import java.util.Collection;
import java.util.Date;

public interface InterviewSessionRepositoryCustom {
    // DRAFT -> RESUME_UPLOADING
    boolean tryMarkResumeUploading(
            String sessionId,
            Long userId
    );

    // RESUME_UPLOADING -> READY
    boolean markReadyIfUploading(
            String sessionId,
            Long userId,
            String resumeFileUrl,
            String interviewType
    );

    // RESUME_UPLOADING -> DRAFT
    boolean markDraftIfUploading(
            String sessionId,
            Long userId
    );

    // READY -> IN_PROGRESS
    boolean markInProgressIfReady(
            String sessionId,
            Long userId,
            Date startTime
    );

    // 根据读取时的状态执行 FINISHED 转换
    boolean finishIfCurrentStatus(
            String sessionId,
            Long userId,
            String expectedStatus,
            Date startTime,
            Date endTime
    );

    // 批量放弃用户当前的活跃会话
    void abandonActiveSessions(
            Long userId,
            Collection<String> activeStatuses,
            Date endTime
    );
}
