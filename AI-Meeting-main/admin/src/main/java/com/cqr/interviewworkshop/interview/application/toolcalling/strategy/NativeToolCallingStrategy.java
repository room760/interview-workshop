
package com.cqr.interviewworkshop.interview.application.toolcalling.strategy;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
import com.cqr.interviewworkshop.ai.enums.AiPropritiesType;
import com.cqr.interviewworkshop.ai.service.chat.UniversalAiChatHandler;
import com.cqr.interviewworkshop.interview.application.guard.core.AiCallGuardService;
import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardStage;
import com.cqr.interviewworkshop.interview.application.observability.agent.AgentNameResolver;
import com.cqr.interviewworkshop.interview.application.observability.exception.AiExceptionResolver;
import com.cqr.interviewworkshop.interview.application.observability.recorder.AiCallContext;
import com.cqr.interviewworkshop.interview.application.observability.recorder.SafeAiCallRecorder;
import com.cqr.interviewworkshop.interview.application.observability.trace.TraceIdGenerator;
import com.cqr.interviewworkshop.interview.application.toolcalling.AgentTool;
import com.cqr.interviewworkshop.interview.application.toolcalling.ToolExecutor;
import com.cqr.interviewworkshop.interview.application.toolcalling.ToolRegistry;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolCall;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolCallingRequest;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolCallingResponse;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Spring AI 的原生 Tool Calling 策略。
 *
 * 模型调用经过 AiCallGuardService；
 * 工具执行复用 ToolRegistry 和 ToolExecutor；
 * 多轮调用由本策略控制。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NativeToolCallingStrategy implements ToolCallingStrategy {

    private static final int DEFAULT_MAX_ITERATIONS = 5;

    private static final int HARD_MAX_ITERATIONS = 10;

    private static final int MAX_TOTAL_TOOL_CALLS = 20;

    private final UniversalAiChatHandler universalAiChatHandler;

    private final ToolRegistry toolRegistry;

    private final ToolExecutor toolExecutor;

    private final AiCallGuardService aiCallGuardService;

    private final SafeAiCallRecorder safeAiCallRecorder;

    private final TraceIdGenerator traceIdGenerator;

    private final AgentNameResolver agentNameResolver;

    private final AiExceptionResolver aiExceptionResolver;

    /**
     * 每个策略实例共享管理器，但每次请求使用独立的 Prompt 和回调列表。
     */
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    @Override
    public String getName() {
        return "native";
    }

    /**
     * 这里检查直接模型配置是否完整。
     *
     * Native 能力本身必须通过显式路由配置确认，
     * 不能仅凭 aiType 或 modelName 推断。
     */
    @Override
    public boolean supports(AiPropertiesDO aiProperties) {
        return aiProperties != null
                && aiProperties.getId() != null
                && StrUtil.isNotBlank(aiProperties.getAiType())
                && AiPropritiesType.isSupported(
                aiProperties.getAiType()
        )
                && StrUtil.isNotBlank(aiProperties.getApiKey())
                && StrUtil.isNotBlank(aiProperties.getModelName());
    }

    @Override
    public ToolCallingResponse execute(ToolCallingRequest request) {
        List<ToolCall> recordedCalls = new CopyOnWriteArrayList<>();
        List<ToolResult> recordedResults = new CopyOnWriteArrayList<>();
        if (request == null) {
            return failure(
                    "REQUEST_MISSING",
                    "Tool Calling request must not be null",
                    0,
                    recordedCalls,
                    recordedResults
            );
        }

        AiPropertiesDO aiProperties = request.getAiProperties();
        if (!supports(aiProperties)) {
            return failure(
                    "MODEL_CONFIG_INVALID",
                    "AI model configuration is incomplete or unsupported",
                    0,
                    recordedCalls,
                    recordedResults
            );
        }
        if (StrUtil.isBlank(request.getUserPrompt())) {
            return failure(
                    "USER_PROMPT_MISSING",
                    "User prompt must not be blank",
                    0,
                    recordedCalls,
                    recordedResults
            );
        }
        if (request.getAllowedToolNames() == null
                || request.getAllowedToolNames().isEmpty()) {
            return failure(
                    "NO_ALLOWED_TOOLS",
                    "No tools are allowed for this request",
                    0,
                    recordedCalls,
                    recordedResults
            );
        }

        AiCallContext recordContext = null;
        int iterations = 0;
        try {
            // 1. 复用项目已有的直接模型客户端构建逻辑。
            ChatClient chatClient = universalAiChatHandler.createChatClientForToolCalling(aiProperties);

            // 2. 只暴露当前请求明确允许使用的工具。
            AtomicInteger totalToolCalls = new AtomicInteger();
            List<ToolCallback> callbacks = buildCallbacks(
                    request.getAllowedToolNames(),
                    recordedCalls,
                    recordedResults,
                    totalToolCalls
            );

            // 3. 关闭模型层自动执行，由当前策略控制工具执行循环。
            ToolCallingChatOptions.Builder optionsBuilder = ToolCallingChatOptions.builder()
                            .toolCallbacks(callbacks)
                            .internalToolExecutionEnabled(false)
                            .model(aiProperties.getModelName());
            if (aiProperties.getMaxTokens() != null) {
                optionsBuilder.maxTokens(aiProperties.getMaxTokens()
                );
            }
            if (aiProperties.getTemperature() != null) {
                optionsBuilder.temperature(aiProperties.getTemperature().doubleValue()
                );
            }
            ToolCallingChatOptions options = optionsBuilder.build();

            // 4. 初始化对话。
            List<Message> messages = new ArrayList<>();
            if (StrUtil.isNotBlank(request.getSystemPrompt())) {
                messages.add(new SystemMessage(request.getSystemPrompt()));
            }
            messages.add(new UserMessage(request.getUserPrompt()));
            Prompt prompt = new Prompt(messages, options);

            // 5. 记录整个 Native Agent 任务。
            recordContext = safeAiCallRecorder.start(
                    traceIdGenerator.generate(),
                    agentNameResolver.resolve(InterviewAiGuardStage.INTERVIEW_JOB_MATCH),
                    aiProperties.getModelName()
            );
            int configuredMax = request.getMaxIterations() == null ? DEFAULT_MAX_ITERATIONS : request.getMaxIterations();
            int maxIterations = Math.max(1, Math.min(configuredMax, HARD_MAX_ITERATIONS));

            // 6. 每轮模型调用均通过现有 AI Guard。
            ChatResponse chatResponse = callModel(
                    chatClient,
                    prompt,
                    request,
                    0
            );

            // 7. 模型提出工具调用时，执行工具并继续下一轮。
            while (chatResponse != null && chatResponse.hasToolCalls()) {
                if (iterations >= maxIterations) {
                    ToolCallingResponse failed = failure(
                            "MAX_TOOL_ITERATIONS_EXCEEDED",
                            "Tool Calling exceeded the configured iteration limit",
                            iterations,
                            recordedCalls,
                            recordedResults
                    );
                    recordFailure(
                            recordContext,
                            "MAX_TOOL_ITERATIONS_EXCEEDED",
                            failed.getErrorMessage()
                    );
                    return failed;
                }
                ToolExecutionResult executionResult = toolCallingManager.executeToolCalls(prompt, chatResponse);
                prompt = new Prompt(executionResult.conversationHistory(), options);
                iterations++;
                chatResponse = callModel(chatClient, prompt, request, iterations);
            }

            // 8. 没有剩余工具调用时，读取模型最终答案。
            String finalAnswer = chatResponse == null
                    || chatResponse.getResult() == null
                    || chatResponse.getResult().getOutput() == null
                    ? null
                    : chatResponse.getResult()
                    .getOutput()
                    .getText();
            if (StrUtil.isBlank(finalAnswer)) {
                throw new IllegalStateException("Native Tool Calling returned an empty final answer");
            }

            ToolCallingResponse response = new ToolCallingResponse();
            response.setFinalAnswer(finalAnswer);
            response.setSuccess(true);
            response.setIterations(iterations);
            response.setToolCalls(new ArrayList<>(recordedCalls));
            response.setToolResults(new ArrayList<>(recordedResults));

            safeAiCallRecorder.success(recordContext, null, null);
            return response;
        } catch (Exception ex) {
            String errorCode = aiExceptionResolver.resolve(ex);
            log.error(
                    "Native Tool Calling failed, requestId={}, modelId={}",
                    request.getRequestId(),
                    aiProperties.getId(),
                    ex
            );
            recordFailure(
                    recordContext,
                    errorCode,
                    ex.getMessage()
            );
            return failure(
                    "NATIVE_TOOL_CALLING_FAILED",
                    "Native Tool Calling failed; see server logs",
                    iterations,
                    recordedCalls,
                    recordedResults
            );
        }
    }

    /**
     * 将当前请求允许使用的工具转换为 Spring AI ToolCallback。
     */
    private List<ToolCallback> buildCallbacks(
            List<String> allowedToolNames,
            List<ToolCall> recordedCalls,
            List<ToolResult> recordedResults,
            AtomicInteger totalToolCalls) {

        Set<String> uniqueNames = new LinkedHashSet<>(allowedToolNames);
        List<ToolCallback> callbacks = new ArrayList<>();

        for (String name : uniqueNames) {
            if (StrUtil.isBlank(name)) {
                throw new IllegalArgumentException("Allowed tool name must not be blank");
            }

            AgentTool tool = toolRegistry.find(name)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Allowed tool is not registered: " + name)
                    );

            if (tool.getDefinition() == null || tool.getDefinition().getInputSchema() == null) {
                throw new IllegalArgumentException("Tool definition or input schema is missing: " + name);
            }

            callbacks.add(
                    new RegistryToolCallback(
                            tool.getDefinition(),
                            toolExecutor,
                            recordedCalls,
                            recordedResults,
                            totalToolCalls
                    )
            );
        }
        if (callbacks.isEmpty()) {
            throw new IllegalArgumentException("No valid ToolCallback was created");
        }
        return callbacks;
    }

    /**
     * 单轮模型请求。
     *
     * 实际模型 I/O 由 AiCallGuardService 调度到
     * interviewAiIoExecutor；这里不直接提交到 AgentExecutor。
     */
    private ChatResponse callModel(
            ChatClient chatClient,
            Prompt prompt,
            ToolCallingRequest request,
            int round) {

        String requestKey = StrUtil.blankToDefault(request.getRequestId(), "no-request") + "|native-round-" + round;
        List<Message> messages = prompt.getInstructions();

        return aiCallGuardService.execute(
                InterviewAiGuardStage.INTERVIEW_JOB_MATCH,
                requestKey,
                () -> chatClient.prompt()
                        .messages(messages)
                        .options(prompt.getOptions())
                        .call()
                        .chatResponse()
        );
    }

    private void recordFailure(
            AiCallContext context,
            String errorCode,
            String message) {

        if (context != null) {
            safeAiCallRecorder.failed(
                    context,
                    errorCode,
                    message
            );
        }
    }

    private ToolCallingResponse failure(
            String errorCode,
            String message,
            int iterations,
            List<ToolCall> calls,
            List<ToolResult> results) {
        ToolCallingResponse response = new ToolCallingResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(message);
        response.setIterations(iterations);
        response.setToolCalls(new ArrayList<>(calls));
        response.setToolResults(new ArrayList<>(results));
        return response;
    }

    /**
     * Spring AI 原生 ToolCallback 适配器。
     *
     * 负责将 Spring AI 的 JSON 工具输入转换成项目自己的
     * ToolCall，再复用 ToolExecutor 执行。
     */
    private static final class RegistryToolCallback implements ToolCallback {

        private final String toolName;
        private final org.springframework.ai.tool.definition.ToolDefinition
                springDefinition;
        private final ToolExecutor toolExecutor;
        private final List<ToolCall> recordedCalls;
        private final List<ToolResult> recordedResults;
        private final AtomicInteger totalToolCalls;

        private RegistryToolCallback(
                com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolDefinition definition,
                ToolExecutor toolExecutor,
                List<ToolCall> recordedCalls,
                List<ToolResult> recordedResults,
                AtomicInteger totalToolCalls) {
            this.toolName = definition.getName();
            this.toolExecutor = toolExecutor;
            this.recordedCalls = recordedCalls;
            this.recordedResults = recordedResults;
            this.totalToolCalls = totalToolCalls;
            this.springDefinition =
                    org.springframework.ai.tool.definition.ToolDefinition
                            .builder()
                            .name(definition.getName())
                            .description(definition.getDescription())
                            .inputSchema(
                                    JSON.toJSONString(
                                            definition.getInputSchema()
                                    )
                            )
                            .build();
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition
        getToolDefinition() {
            return springDefinition;
        }

        @Override
        public String call(String toolInput) {
            String callId = UUID.randomUUID().toString();
            Map<String, Object> arguments;

            try {
                arguments = JSON.parseObject(toolInput, new TypeReference<Map<String, Object>>() {});
                if (arguments == null) {
                    throw new IllegalArgumentException("Tool arguments must be a JSON object");
                }
            } catch (Exception ex) {
                ToolCall invalidCall = new ToolCall();
                invalidCall.setId(callId);
                invalidCall.setName(toolName);
                invalidCall.setArguments(Collections.emptyMap());
                ToolResult invalidResult = ToolResult.failure(
                        callId,
                        toolName,
                        "Invalid tool arguments JSON"
                );
                recordedCalls.add(invalidCall);
                recordedResults.add(invalidResult);
                return JSON.toJSONString(invalidResult);
            }
            ToolCall call = new ToolCall();
            call.setId(callId);
            call.setName(toolName);
            call.setArguments(arguments);
            recordedCalls.add(call);

            // 限制单次 Agent 任务的工具调用总量。
            if (totalToolCalls.incrementAndGet() > MAX_TOTAL_TOOL_CALLS) {
                ToolResult limitResult = ToolResult.failure(
                        callId,
                        toolName,
                        "Tool call limit exceeded"
                );
                recordedResults.add(limitResult);
                return JSON.toJSONString(limitResult);
            }

            ToolResult result = toolExecutor.execute(call);
            if (result == null) {
                result = ToolResult.failure(
                        callId,
                        toolName,
                        "Tool executor returned no result"
                );
            }

            recordedResults.add(result);
            // 将统一结果作为 JSON 内容返回给 Spring AI。
            return JSON.toJSONString(result);
        }
    }
}
