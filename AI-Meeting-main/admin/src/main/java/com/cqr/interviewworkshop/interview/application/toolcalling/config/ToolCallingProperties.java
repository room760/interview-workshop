package com.cqr.interviewworkshop.interview.application.toolcalling.config;

import com.cqr.interviewworkshop.interview.application.toolcalling.strategy.ToolCallingMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "interview-workshop-agent.tool-calling")
public class ToolCallingProperties {
    private Map<String, ToolCallingMode> modelModes = new HashMap<>();
    private Long jobMatchAiPropertiesId;
}
