package com.cqr.interviewworkshop.interview.application.observability.recorder;

public interface AiCallRecorder {
    AiCallContext start(
            String traceId,
            String agentName,
            String modelName
    );
    void success(
            AiCallContext context,
            Integer inputTokens,
            Integer outputTokens
    );
    void failed(
            AiCallContext context,
            String errorCode,
            String errorMessage
    );
}
