package com.cqr.interviewworkshop.interview.application.runtime;

/**
 * 定义面试运行态懒恢复时可选的数据恢复来源，
 * 用于标识当前会话数据来自缓存、快照、报告或持久化主数据回补。
 *
 */
public enum InterviewRuntimeRestoreSource {

    CACHE,
    RUNTIME_SNAPSHOT,
    INTERVIEW_RECORD,
    SESSION_QUESTION,
    NONE
}
