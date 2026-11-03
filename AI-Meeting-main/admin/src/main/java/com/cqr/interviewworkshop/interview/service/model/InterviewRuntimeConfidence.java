package com.cqr.interviewworkshop.interview.service.model;

/**
 * 定义面试运行态恢复结果的置信度等级，
 * 用于标识恢复后的会话是否可写、是否仅可读以及是否处于终态。
 */
public enum InterviewRuntimeConfidence {

    EXACT,
    DERIVED,
    READ_ONLY,
    TERMINAL
}
