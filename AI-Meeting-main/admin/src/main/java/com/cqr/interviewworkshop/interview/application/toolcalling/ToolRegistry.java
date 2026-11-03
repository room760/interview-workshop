package com.cqr.interviewworkshop.interview.application.toolcalling;

import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class ToolRegistry {

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");

    private final Map<String,AgentTool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(ObjectProvider<AgentTool> toolProvider){
        toolProvider.orderedStream().forEach(this::register);
    }

    public void register(AgentTool tool){
        ToolDefinition definition = validateAndGetDefinition(tool);
        String name = definition.getName();
        AgentTool existing = tools.putIfAbsent(name,tool);
        if(existing != null){
            throw new IllegalArgumentException("Duplicate tool name:" + name);
        }
    }

    public ToolDefinition validateAndGetDefinition(AgentTool tool){
        if(tool == null){
            throw new IllegalArgumentException("Tool must not be null");
        }
        ToolDefinition definition = tool.getDefinition();
        if(definition == null){
            throw new IllegalArgumentException("Tool definition must not be null");
        }

        String name = definition.getName();
        if(name == null || !TOOL_NAME_PATTERN.matcher(name).matches()){
            throw new IllegalArgumentException("Invalid tool name:" + name);
        }
        if (definition.getDescription() == null || definition.getDescription().isBlank()) {
            throw new IllegalArgumentException("Tool description must not be blank: " + name);
        }

        Map<String, Object> inputSchema = definition.getInputSchema();
        if (inputSchema == null || !"object".equals(inputSchema.get("type"))) {
            throw new IllegalArgumentException("Tool inputSchema must have type=object: " + name);
        }
        return definition;
    }

    public AgentTool getRequired(String name){
        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        return find(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknow tool:" + name));
    }
    public Optional<AgentTool> find(String name){
        if(name == null || name.isBlank()){
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

}
