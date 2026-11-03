package com.cqr.interviewworkshop.interview.application.toolcalling.model;

import lombok.Data;

import java.util.Map;

@Data
public class ToolDefinition {
    private String name;
    private String description;
    private Map<String,Object> inputSchema;
}
