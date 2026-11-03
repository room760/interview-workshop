package com.cqr.interviewworkshop.interview.application.guard.singleflight.model;

import lombok.Builder;
import lombok.Value;

/**
 * 封装节点对同一 AI 请求执行抢占后的结果，
 * 用于区分当前节点是 owner、follower 还是直接回放历史结果。
 *
 */
@Value
@Builder
public class FlightAcquireResult {
    FlightAction action;
    Long ownerToken;
    FlightStatus status;
    Boolean retryable;
    FlightErrorType errorType;
    String errorCode;
}
