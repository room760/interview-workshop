package com.cqr.interviewworkshop.interview.dao.repository;

import com.cqr.interviewworkshop.interview.dao.entity.InterviewSessionRuntimeColdSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 定义面试会话冷快照的持久化访问接口，
 * 用于查询和维护低频材料层的字段级持久化数据。
 *
 */
@Repository
public interface InterviewSessionRuntimeColdSnapshotRepository extends MongoRepository<InterviewSessionRuntimeColdSnapshot, String>,
        InterviewSessionRuntimeColdSnapshotRepositoryCustom {

    Optional<InterviewSessionRuntimeColdSnapshot> findBySessionId(String sessionId);
}
