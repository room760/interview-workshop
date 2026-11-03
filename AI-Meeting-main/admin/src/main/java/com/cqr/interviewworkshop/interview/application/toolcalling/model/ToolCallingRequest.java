package com.cqr.interviewworkshop.interview.application.toolcalling.model;

import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
public class ToolCallingRequest {
    @ToString.Exclude
    private AiPropertiesDO aiProperties;
    private String systemPrompt;
    private String userPrompt;
    private String sessionId;
    private String requestId;
    private List<String> allowedToolNames = new ArrayList<>();
    private Integer maxIterations = 5;
}
