package com.cqr.interviewworkshop.interview.application.toolcalling;

import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolDefinition;

import java.util.Map;

public interface AgentTool {
    ToolDefinition getDefinition();
    Object execute(Map<String,Object> arguments);
}
