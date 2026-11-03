package com.cqr.interviewworkshop.interview.application.toolcalling.strategy;

import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
import com.cqr.interviewworkshop.interview.application.toolcalling.config.ToolCallingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ToolCallingStrategyResolver {
    private final ToolCallingProperties properties;
    private final List<ToolCallingStrategy> strategies;

    public ToolCallingMode resolveMode(AiPropertiesDO aiProperties){
        if(aiProperties == null){
            throw new IllegalArgumentException("AI properties must not be null");
        }
        if (aiProperties.getId() == null) {
            throw new IllegalArgumentException("AI properties ID must not be null");
        }

        String configId = String.valueOf(aiProperties.getId());
        ToolCallingMode mode = properties.getModelModes().get(configId);
        if(mode == null){
            throw new IllegalStateException(
                    "Tool Calling mode is not configured for "
                            + "AI properties ID: " + configId
                            + ". Configure "
                            + "interview-workshop-agent.tool-calling.model-modes."
                            + configId
            );
        }

        return mode;
    }

    public ToolCallingStrategy resolve(AiPropertiesDO aiProperties){
        ToolCallingMode mode = resolveMode(aiProperties);
        if (mode == ToolCallingMode.DISABLED) {
            throw new IllegalStateException("Tool Calling is disabled for AI properties ID: " + aiProperties.getId());
        }

        String strategyName = mode.getStrategyName();
        List<ToolCallingStrategy> candidates = strategies
                .stream()
                .filter(Objects::nonNull)
                .filter(strategy -> strategyName.equalsIgnoreCase(strategy.getName()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No ToolCallingStrategy registered for mode: " + strategyName);
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException("Multiple ToolCallingStrategy beans registered " + "for mode: " + strategyName);
        }
        ToolCallingStrategy strategy = candidates.get(0);
        if (!strategy.supports(aiProperties)) {
            throw new IllegalStateException(
                    "ToolCallingStrategy does not support "
                            + "AI properties ID: "
                            + aiProperties.getId()
                            + ", strategy: " + strategyName
            );
        }
        return strategy;
    }
}
