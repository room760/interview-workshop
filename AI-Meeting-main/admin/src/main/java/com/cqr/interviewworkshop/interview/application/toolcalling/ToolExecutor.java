
package com.cqr.interviewworkshop.interview.application.toolcalling;

import com.alibaba.fastjson2.JSON;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolCall;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolDefinition;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 统一工具执行器。
 *
 * 负责：
 * 1. 检查工具调用请求；
 * 2. 从 ToolRegistry 获取工具；
 * 3. 校验输入参数；
 * 4. 执行具体工具；
 * 5. 将执行结果统一转换为 ToolResult。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolExecutor {

    private final ToolRegistry toolRegistry;

    /**
     * 执行一次工具调用。
     */
    public ToolResult execute(ToolCall call) {
        // 1. 检查调用请求。
        if (call == null) {
            return ToolResult.failure(null, null, "Tool call must not be null");
        }
        String toolCallId = call.getId();
        String toolName = call.getName();
        if (isBlank(toolCallId)) {
            return ToolResult.failure(toolCallId, toolName, "Tool call id must not be blank");
        }
        if (isBlank(toolName)) {
            return ToolResult.failure(toolCallId, toolName, "Tool name must not be blank");
        }

        // 2. 根据名称查找工具。
        AgentTool tool;
        try {
            tool = toolRegistry.getRequired(toolName);
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Tool lookup failed, toolName={}, toolCallId={}",
                    toolName,
                    toolCallId
            );
            return ToolResult.failure(toolCallId, toolName, "Unknown tool: " + toolName);
        }

        // 3. 规范化参数。
        Map<String, Object> arguments = call.getArguments();
        if (arguments == null) {
            arguments = Collections.emptyMap();
        }

        // 4. 根据工具定义校验参数。
        String validationError = validateArguments(tool.getDefinition(), arguments);
        if (validationError != null) {
            return ToolResult.failure(toolCallId, toolName, validationError);
        }

        // 5. 执行工具并统一转换结果。
        try {
            Object output = tool.execute(arguments);
            if (output == null) {
                return ToolResult.failure(toolCallId, toolName, "Tool returned null result");
            }
            String content = output instanceof String ? (String) output : JSON.toJSONString(output);
            return ToolResult.success(toolCallId, toolName, content);
        } catch (Exception ex) {
            log.error(
                    "Tool execution failed, toolName={}, toolCallId={}",
                    toolName,
                    toolCallId,
                    ex
            );
            // 不把异常堆栈、密钥等内部信息直接返回给模型。
            return ToolResult.failure(toolCallId, toolName, "Tool execution failed"
            );
        }
    }

    /**
     * 校验参数是否符合工具定义中的 JSON Schema。
     *
     * 当前支持常见的 object、string、integer、
     * number、boolean、array、null 类型。
     */
    private String validateArguments(ToolDefinition definition, Map<String, Object> arguments) {
        if (definition == null || definition.getInputSchema() == null) {
            return "Tool input schema is missing";
        }

        Map<String, Object> schema = definition.getInputSchema();
        if (!"object".equals(schema.get("type"))) {
            return "Tool input schema must have type=object";
        }
        return validateValue(arguments, schema, "arguments");
    }

    /**
     * 递归校验 JSON Schema 描述的值。
     */
    private String validateValue(Object value, Map<String, Object> schema, String path) {
        Object typeValue = schema.get("type");
        String type = typeValue instanceof String ? (String) typeValue : null;
        if (type != null && !isSupportedType(type)) {
            return "Unsupported schema type: " + type;
        }
        if (type != null && !matchesType(value, type)) {
            return path + " must be of type " + type;
        }

        // object：检查必填参数、未知参数和子属性。
        if ("object".equals(type)) {
            Map<?, ?> object = (Map<?, ?>) value;
            Object requiredValue = schema.get("required");

            if (requiredValue != null && !(requiredValue instanceof List<?>)) {
                return "Invalid schema: required must be an array";
            }
            if (requiredValue instanceof List<?>) {
                List<?> required = (List<?>) requiredValue;
                for (Object item : required) {
                    if (!(item instanceof String)) {
                        return "Invalid schema: required item must be a string";
                    }
                    String requiredName = (String) item;
                    if (!object.containsKey(requiredName)) {
                        return path + "." + requiredName + " is required";
                    }
                }
            }
            Object propertiesValue = schema.get("properties");
            if (propertiesValue != null && !(propertiesValue instanceof Map<?, ?>)) {
                return "Invalid schema: properties must be an object";
            }

            Map<?, ?> properties = propertiesValue instanceof Map<?, ?>
                    ? (Map<?, ?>) propertiesValue
                    : Collections.emptyMap();

            boolean rejectUnknownProperties = Boolean.FALSE.equals(schema.get("additionalProperties"));

            for (Map.Entry<?, ?> entry : object.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    return "Invalid argument name";
                }
                String key = (String) entry.getKey();
                Object propertySchemaValue = properties.get(key);
                if (propertySchemaValue == null) {
                    if (rejectUnknownProperties) {
                        return path + "." + key + " is not allowed";
                    }
                    continue;
                }

                if (!(propertySchemaValue instanceof Map<?, ?>)) {
                    return "Invalid schema for property: " + key;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> propertySchema = (Map<String, Object>) propertySchemaValue;
                String error = validateValue(entry.getValue(), propertySchema, path + "." + key);
                if (error != null) {
                    return error;
                }
            }
        }

        // array：如果定义了 items，则继续校验每个元素。
        if ("array".equals(type)) {
            Object itemsValue = schema.get("items");
            if (itemsValue != null && !(itemsValue instanceof Map<?, ?>)) {
                return "Invalid schema: items must be an object";
            }
            if (itemsValue instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> itemSchema = (Map<String, Object>) itemsValue;
                if (value instanceof Collection<?>) {
                    int index = 0;
                    for (Object item : (Collection<?>) value) {
                        String error = validateValue(
                                item,
                                itemSchema,
                                path + "[" + index + "]"
                        );
                        if (error != null) {
                            return error;
                        }
                        index++;
                    }
                } else if (value != null && value.getClass().isArray()) {
                    for (int i = 0; i < Array.getLength(value); i++) {
                        String error = validateValue(
                                Array.get(value, i),
                                itemSchema,
                                path + "[" + i + "]"
                        );
                        if (error != null) {
                            return error;
                        }
                    }
                }
            }
        }
        // enum：检查值是否属于允许的枚举集合。
        Object enumValue = schema.get("enum");
        if (enumValue instanceof List<?> && !((List<?>) enumValue).contains(value)) {
            return path + " contains an unsupported value";
        }
        return null;
    }

    private boolean isSupportedType(String type) {
        return "object".equals(type)
                || "string".equals(type)
                || "integer".equals(type)
                || "number".equals(type)
                || "boolean".equals(type)
                || "array".equals(type)
                || "null".equals(type);
    }

    private boolean matchesType(Object value, String type) {
        if ("null".equals(type)) {
            return value == null;
        }
        if (value == null) {
            return false;
        }
        switch (type) {
            case "object":
                return value instanceof Map<?, ?>;
            case "array":
                return value instanceof Collection<?>
                        || value.getClass().isArray();
            case "string":
                return value instanceof String;
            case "integer":
                if (!(value instanceof Number)) {
                    return false;
                }
                try {
                    return new BigDecimal(value.toString())
                            .stripTrailingZeros()
                            .scale() <= 0;
                } catch (NumberFormatException ex) {
                    return false;
                }
            case "number":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            default:
                return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
