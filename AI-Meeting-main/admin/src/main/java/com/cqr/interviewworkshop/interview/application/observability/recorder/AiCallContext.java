package com.cqr.interviewworkshop.interview.application.observability.recorder;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiCallContext {
    private String traceId;
    private String agentName;
    private String modelName;
    private LocalDateTime startTime;
}
