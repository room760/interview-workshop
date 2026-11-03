package com.cqr.interviewworkshop.interview.application.toolcalling.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ToolCallingResponse {
    private String finalAnswer;
    private boolean success;
    private String errorCode;
    private String errorMessage;
    private int iterations;
    private List<ToolCall> toolCalls = new ArrayList<>();
    private List<ToolResult> toolResults = new ArrayList<>();
}
