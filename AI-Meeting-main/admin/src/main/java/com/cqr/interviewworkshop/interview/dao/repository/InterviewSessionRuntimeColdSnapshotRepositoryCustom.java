package com.cqr.interviewworkshop.interview.dao.repository;

import com.cqr.interviewworkshop.interview.application.runtime.InterviewSessionRuntimeColdPatch;

/**
 * 定义冷快照字段级 Patch 持久化扩展接口，
 * 用于按 session 维度增量更新低频材料字段。
 *
 */
public interface InterviewSessionRuntimeColdSnapshotRepositoryCustom {

    void applyPatch(String sessionId, InterviewSessionRuntimeColdPatch patch);
}
