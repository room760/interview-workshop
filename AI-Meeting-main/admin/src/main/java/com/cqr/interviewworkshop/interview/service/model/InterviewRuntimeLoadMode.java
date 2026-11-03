package com.cqr.interviewworkshop.interview.service.model;

/**
 * 定义面试运行态恢复时的加载模式，
 * 用于区分只读查询场景和要求可写恢复的业务场景。
 */
public enum InterviewRuntimeLoadMode {

    READ_ONLY,
    READ_WRITE_REQUIRED
}
