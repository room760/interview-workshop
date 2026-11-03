package com.cqr.interviewworkshop.interview.application.toolcalling.model;

import lombok.Data;

@Data
public class ToolResult {
    private String toolCallId;
    private String toolName;
    private boolean success;
    private String content;

    public static ToolResult success(String toolCallId, String toolName, String content) {
        return create(toolCallId, toolName, true, content);
    }

    public static ToolResult failure(String toolCallId, String toolName, String errorMessage) {
        return create(toolCallId, toolName, false, errorMessage);
    }
    private static ToolResult create(String toolCallId, String toolName, boolean success, String content) {
        ToolResult result = new ToolResult();
        result.setToolCallId(toolCallId);
        result.setToolName(toolName);
        result.setSuccess(success);
        result.setContent(content);
        return result;
    }
}
