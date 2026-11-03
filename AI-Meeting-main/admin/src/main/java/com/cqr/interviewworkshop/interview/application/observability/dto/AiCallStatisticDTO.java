package com.cqr.interviewworkshop.interview.application.observability.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiCallStatisticDTO {
    private Long totalCalls;
    private Long successCalls;
    private Long failedCalls;
    private Long avgLatency;
    private Long totalTokens;
    private List<AgentStatisticDTO> agentStatistics;
}
