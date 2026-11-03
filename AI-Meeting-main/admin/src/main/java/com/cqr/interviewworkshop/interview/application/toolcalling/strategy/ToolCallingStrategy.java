package com.cqr.interviewworkshop.interview.application.toolcalling.strategy;

import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolCallingRequest;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolCallingResponse;

public interface ToolCallingStrategy {
    String getName();
    boolean supports(AiPropertiesDO aiProperties);
    ToolCallingResponse execute(ToolCallingRequest request);
}
