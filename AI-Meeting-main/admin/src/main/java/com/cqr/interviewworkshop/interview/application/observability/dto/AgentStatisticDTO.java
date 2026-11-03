package com.cqr.interviewworkshop.interview.application.observability.dto;

import lombok.Data;

@Data
public class AgentStatisticDTO {
    private String agentName;
    private Long callCount;
    private Long successCount;
    private Long failedCount;
    private Long avgLatency;
}
