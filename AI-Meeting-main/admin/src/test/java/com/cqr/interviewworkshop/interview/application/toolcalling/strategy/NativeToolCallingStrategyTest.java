
package com.cqr.interviewworkshop.interview.application.toolcalling.strategy;

import com.cqr.interviewworkshop.ai.dao.entity.AiPropertiesDO;
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
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolDefinition;
import com.cqr.interviewworkshop.interview.application.toolcalling.model.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NativeToolCallingStrategyTest {

    @Mock
    private UniversalAiChatHandler universalAiChatHandler;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolExecutor toolExecutor;

    @Mock
    private AiCallGuardService aiCallGuardService;

    @Mock
    private SafeAiCallRecorder safeAiCallRecorder;

    @Mock
    private TraceIdGenerator traceIdGenerator;

    @Mock
    private AgentNameResolver agentNameResolver;

    @Mock
    private AiExceptionResolver aiExceptionResolver;

    @Mock
    private AgentTool echoTool;

    @Mock
    private ChatModel chatModel;

    private NativeToolCallingStrategy strategy;

    private ToolDefinition echoDefinition;

    private AiCallContext recordContext;

    @BeforeEach
    void setUp() throws Exception {
        strategy = new NativeToolCallingStrategy(
                universalAiChatHandler,
                toolRegistry,
                toolExecutor,
                aiCallGuardService,
                safeAiCallRecorder,
                traceIdGenerator,
                agentNameResolver,
                aiExceptionResolver
        );

        echoDefinition = new ToolDefinition();
        echoDefinition.setName("echo_test");
        echoDefinition.setDescription(
                "Echo the supplied text for a test"
        );

        Map<String, Object> textSchema = new HashMap<>();
        textSchema.put("type", "string");

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put(
                "properties",
                Map.of("text", textSchema)
        );
        schema.put("required", List.of("text"));
        schema.put("additionalProperties", false);

        echoDefinition.setInputSchema(schema);

        when(echoTool.getDefinition()).thenReturn(echoDefinition);
        when(toolRegistry.find("echo_test"))
                .thenReturn(Optional.of(echoTool));

        ChatClient toolCallingChatClient = ChatClient.builder(chatModel).build();
        when(universalAiChatHandler
                .createChatClientForToolCalling(
                        any(AiPropertiesDO.class)))
                .thenReturn(toolCallingChatClient);

        when(traceIdGenerator.generate())
                .thenReturn("test-trace-id");

        when(agentNameResolver.resolve(anyString()))
                .thenReturn("JobMatchAgent");

        recordContext = new AiCallContext();
        recordContext.setTraceId("test-trace-id");

        when(safeAiCallRecorder.start(
                anyString(), anyString(), anyString()))
                .thenReturn(recordContext);

        // 测试中让 Guard 直接执行传入的 Callable，
        // 不创建真实 I/O 线程，也不调用外部模型。
        doAnswer(invocation -> {
            Callable<?> action = invocation.getArgument(2);
            return action.call();
        }).when(aiCallGuardService).execute(
                anyString(),
                anyString(),
                any(Callable.class)
        );

        when(toolExecutor.execute(any(ToolCall.class)))
                .thenAnswer(invocation -> {
                    ToolCall call = invocation.getArgument(0);

                    assertEquals("echo_test", call.getName());
                    assertEquals(
                            "hello",
                            call.getArguments().get("text")
                    );

                    return ToolResult.success(
                            call.getId(),
                            call.getName(),
                            "echo:hello"
                    );
                });
    }

    @Test
    void shouldExecuteToolAndReturnFinalModelAnswer() {
        // 第一轮：模型返回原生工具调用请求。
        AssistantMessage.ToolCall modelToolCall =
                new AssistantMessage.ToolCall(
                        "model-call-1",
                        "function",
                        "echo_test",
                        "{\"text\":\"hello\"}"
                );

        AssistantMessage toolCallMessage =
                new AssistantMessage(
                        "",
                        Map.of(),
                        List.of(modelToolCall)
                );

        ChatResponse toolCallResponse =
                new ChatResponse(
                        List.of(new Generation(toolCallMessage))
                );

        // 第二轮：模型根据工具结果生成最终答案。
        ChatResponse finalResponse =
                new ChatResponse(
                        List.of(
                                new Generation(
                                        new AssistantMessage(
                                                "The tool returned echo:hello."
                                        )
                                )
                        )
                );

        when(chatModel.call(any(Prompt.class)))
                .thenReturn(toolCallResponse, finalResponse);

        AiPropertiesDO aiProperties = new AiPropertiesDO();
        aiProperties.setId(999L);
        aiProperties.setAiType("deepseek");
        aiProperties.setApiKey("mock-api-key");
        aiProperties.setModelName("deepseek-chat");

        ToolCallingRequest request = new ToolCallingRequest();
        request.setAiProperties(aiProperties);
        request.setRequestId("test-request-1");
        request.setSystemPrompt(
                "Use the supplied tool when requested."
        );
        request.setUserPrompt(
                "Call echo_test with text hello, then answer."
        );
        request.setAllowedToolNames(List.of("echo_test"));
        request.setMaxIterations(3);

        ToolCallingResponse response = strategy.execute(request);

        assertTrue(
                response.isSuccess(),
                response.getErrorMessage()
        );

        assertEquals(
                "The tool returned echo:hello.",
                response.getFinalAnswer()
        );

        assertEquals(1, response.getIterations());
        assertEquals(1, response.getToolCalls().size());
        assertEquals(1, response.getToolResults().size());

        verify(toolExecutor, times(1))
                .execute(any(ToolCall.class));

        // 确认第二次模型请求包含工具执行后的对话历史。
        ArgumentCaptor<Prompt> promptCaptor =
                ArgumentCaptor.forClass(Prompt.class);

        verify(chatModel, times(2))
                .call(promptCaptor.capture());

        List<Message> secondRoundMessages =
                promptCaptor.getAllValues()
                        .get(1)
                        .getInstructions();

        assertTrue(
                secondRoundMessages.stream()
                        .anyMatch(
                                ToolResponseMessage.class::isInstance
                        ),
                "Second model request must include tool results"
        );

        verify(aiCallGuardService, times(2)).execute(
                eq(InterviewAiGuardStage.INTERVIEW_JOB_MATCH),
                anyString(),
                any(Callable.class)
        );

        verify(safeAiCallRecorder, times(1))
                .success(eq(recordContext), isNull(), isNull());
    }

}
