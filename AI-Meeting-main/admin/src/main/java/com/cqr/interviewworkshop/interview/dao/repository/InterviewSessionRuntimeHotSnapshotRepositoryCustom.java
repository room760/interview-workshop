package com.cqr.interviewworkshop.interview.dao.repository;

import com.cqr.interviewworkshop.interview.application.runtime.InterviewSessionRuntimeHotPatch;

/**
 * 定义热快照字段级 Patch 持久化扩展接口，
 * 用于按 session 维度增量更新高频运行态字段。
 */
public interface InterviewSessionRuntimeHotSnapshotRepositoryCustom {

    void applyPatch(String sessionId, InterviewSessionRuntimeHotPatch patch);

    boolean compareAndSetPatch(String sessionId, Long expectedSnapshotVersion, InterviewSessionRuntimeHotPatch patch);
}
