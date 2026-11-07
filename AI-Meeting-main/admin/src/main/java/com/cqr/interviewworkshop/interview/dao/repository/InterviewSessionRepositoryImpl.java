package com.cqr.interviewworkshop.interview.dao.repository;

import com.cqr.interviewworkshop.interview.dao.entity.InterviewSession;
import com.cqr.interviewworkshop.interview.service.model.InterviewSessionStatus;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Date;

@Repository
@RequiredArgsConstructor
public class InterviewSessionRepositoryImpl implements InterviewSessionRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    /**
     * 根据会话、用户、当前状态构造更新条件。
     */
    private Query buildStatusQuery(String sessionId, Long userId, String expectedStatus) {
        return Query.query(
                Criteria.where("sessionId").is(sessionId)
                        .and("userId").is(userId)
                        .and("delFlag").is(0)
                        .and("status").is(expectedStatus)
        );
    }

    /**
     * 执行单文档原子条件更新。
     */
    private boolean updateOne(Query query, Update update) {
        UpdateResult result = mongoTemplate.updateFirst(query, update, InterviewSession.class);
        return result != null && result.getMatchedCount() == 1;
    }

    /**
     * DRAFT -> RESUME_UPLOADING
     */
    @Override
    public boolean tryMarkResumeUploading(String sessionId, Long userId) {
        Query query = buildStatusQuery(
                sessionId,
                userId,
                InterviewSessionStatus.DRAFT.name()
        );
        Date now = new Date();
        Update update = new Update()
                .set("status", InterviewSessionStatus.RESUME_UPLOADING.name())
                .set("updateTime", now);
        return updateOne(query, update);
    }

    /**
     * RESUME_UPLOADING -> READY
     */
    @Override
    public boolean markReadyIfUploading(String sessionId, Long userId, String resumeFileUrl, String interviewType) {
        Query query = buildStatusQuery(
                sessionId,
                userId,
                InterviewSessionStatus.RESUME_UPLOADING.name()
        );
        Date now = new Date();
        Update update = new Update()
                .set("status", InterviewSessionStatus.READY.name())
                .set("resumeFileUrl", resumeFileUrl)
                .set("interviewType", interviewType)
                .set("updateTime", now);
        return updateOne(query, update);
    }

    /**
     * RESUME_UPLOADING -> DRAFT
     */
    @Override
    public boolean markDraftIfUploading(String sessionId, Long userId) {
        Query query = buildStatusQuery(
                sessionId,
                userId,
                InterviewSessionStatus.RESUME_UPLOADING.name()
        );
        Date now = new Date();
        Update update = new Update()
                .set("status", InterviewSessionStatus.DRAFT.name())
                .set("updateTime", now);
        return updateOne(query, update);
    }

    /**
     * READY -> IN_PROGRESS
     */
    @Override
    public boolean markInProgressIfReady(String sessionId, Long userId, Date startTime) {
        Query query = buildStatusQuery(
                sessionId,
                userId,
                InterviewSessionStatus.READY.name()
        );
        Date now = new Date();
        Update update = new Update()
                .set("status", InterviewSessionStatus.IN_PROGRESS.name())
                .set("startTime", startTime)
                .set("updateTime", now);
        return updateOne(query, update);
    }

    /**
     * 当前状态必须与读取时一致，才允许结束会话。
     */
    @Override
    public boolean finishIfCurrentStatus(String sessionId, Long userId, String expectedStatus, Date startTime, Date endTime) {
        Query query = buildStatusQuery(
                sessionId,
                userId,
                expectedStatus
        );
        Update update = new Update()
                .set("status", InterviewSessionStatus.FINISHED.name())
                .set("startTime", startTime)
                .set("endTime", endTime)
                .set("updateTime", endTime);
        return updateOne(query, update);
    }

    /**
     * 批量将活跃会话标记为 ABANDONED。
     */
    @Override
    public void abandonActiveSessions(Long userId, Collection<String> activeStatuses, Date endTime) {
        Query query = Query.query(
                Criteria.where("userId").is(userId)
                        .and("delFlag").is(0)
                        .and("status").in(activeStatuses)
        );
        Update update = new Update()
                .set("status", InterviewSessionStatus.ABANDONED.name())
                .set("endTime", endTime)
                .set("updateTime", endTime);
        mongoTemplate.updateMulti(
                query,
                update,
                InterviewSession.class
        );
    }
}
