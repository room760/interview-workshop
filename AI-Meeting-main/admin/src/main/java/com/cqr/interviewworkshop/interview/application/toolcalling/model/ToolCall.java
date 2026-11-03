package com.cqr.interviewworkshop.interview.application.toolcalling.model;

import lombok.Data;

import java.util.Map;

@Data
public class ToolCall {
    private String id;
    private String name;
    private Map<String,Object> arguments;
}
