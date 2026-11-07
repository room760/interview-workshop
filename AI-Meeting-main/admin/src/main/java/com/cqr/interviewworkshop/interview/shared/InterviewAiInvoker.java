package com.cqr.interviewworkshop.interview.shared;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.cqr.interviewworkshop.agent.dao.entity.AgentPropertiesDO;
import com.cqr.interviewworkshop.interview.application.guard.core.AiCallGuardService;
import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardStage;
import com.cqr.interviewworkshop.interview.application.guard.singleflight.service.DistributedInterviewAiSingleFlightService;
import com.cqr.interviewworkshop.interview.application.observability.agent.AgentNameResolver;
import com.cqr.interviewworkshop.interview.application.observability.exception.AiExceptionResolver;
import com.cqr.interviewworkshop.interview.application.observability.recorder.AiCallContext;
import com.cqr.interviewworkshop.interview.application.observability.recorder.SafeAiCallRecorder;
import com.cqr.interviewworkshop.interview.application.observability.trace.TraceIdGenerator;
import com.cqr.interviewworkshop.toolkit.xunfei.XingChenAIClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 面试 AI 的统一调用入口，负责生成请求指纹、串联限流熔断保护、
 * 分布式 single-flight 复用以及最终的模型调用过程。
 *
 */
@Component
@RequiredArgsConstructor
public class InterviewAiInvoker {

    private final XingChenAIClient xingChenAIClient;
    private final AiCallGuardService aiCallGuardService;
    private final DistributedInterviewAiSingleFlightService distributedInterviewAiSingleFlightService;
    private final SafeAiCallRecorder safeAiCallRecorder;
    private final TraceIdGenerator traceIdGenerator;
    private final AgentNameResolver agentNameResolver;
    private final AiExceptionResolver aiExceptionResolver;

    public String callAiSync(
            String prompt,
            String sessionId,
            AgentPropertiesDO agentProperties,
            String stage,
            String singleFlightKey) throws Exception {
        return guardedCall(stage,
                singleFlightKey,
                () -> doChat(prompt, sessionId, agentProperties, null, null));
    }

    public String callAiSyncWithFile(
            String prompt,
            String sessionId,
            AgentPropertiesDO agentProperties,
            String fileUrl,
            String stage,
            String singleFlightKey) throws Exception {
        return guardedCall(stage,
                singleFlightKey,
                () -> doChat(prompt, sessionId, agentProperties, fileUrl, null));
    }

    public String callAiSyncWithParameters(
            String sessionId,
            AgentPropertiesDO agentProperties,
            Map<String, Object> parameters,
            String stage,
            String singleFlightKey) throws Exception {
        Object rawInput = parameters == null ? null : parameters.get("AGENT_USER_INPUT");
        String input = rawInput == null ? "" : rawInput.toString().trim();
        return guardedCall(
                stage,
                singleFlightKey,
                () -> doChat(StrUtil.blankToDefault(input, ""), sessionId, agentProperties, null, parameters)
        );
    }

    public String buildSingleFlightKey(
            String stage,
            String sessionId,
            String questionNumber,
            String answerContent) {
        String safeStage = StrUtil.blankToDefault(stage, "interview-default");
        String safeSessionId = StrUtil.blankToDefault(StrUtil.trimToEmpty(sessionId), "no-session");
        String safeQuestionNumber = StrUtil.blankToDefault(StrUtil.trimToEmpty(questionNumber), "-");
        String safeAnswerHash = StrUtil.isBlank(answerContent)
                ? "-"
                : DigestUtil.sha256Hex(answerContent.trim()).substring(0, 16);
        return safeStage + "|" + safeSessionId + "|" + safeQuestionNumber + "|" + safeAnswerHash;
    }

    public String buildSingleFlightKey(
            String stage,
            String sessionId,
            String businessKey) {
        String safeStage = StrUtil.blankToDefault(stage, "interview-default");
        String safeSessionId = StrUtil.blankToDefault(StrUtil.trimToEmpty(sessionId), "no-session");
        String safeBusinessKey = StrUtil.blankToDefault(StrUtil.trimToEmpty(businessKey), "-");
        return safeStage + "|" + safeSessionId + "|" + safeBusinessKey;
    }

    private String guardedCall(String stage, String singleFlightKey, Callable<String> callable) throws Exception {
        String safeStage = StrUtil.blankToDefault(stage, "interview-default");
        String key = StrUtil.blankToDefault(singleFlightKey, safeStage + "|no-key");
        String traceId = traceIdGenerator.generate();
        AiCallContext context = safeAiCallRecorder.start(
                traceId,
                agentNameResolver.resolve(safeStage),
                "xingchen"
        );

        return distributedInterviewAiSingleFlightService.execute(
                safeStage,
                key,
                () -> {
                    try {
                        String response = aiCallGuardService.execute(safeStage, key, callable);
                        safeAiCallRecorder.success(context, null, null);
                        return response;
                    } catch (Exception e) {
                        String errorCode = aiExceptionResolver.resolve(e);
                        safeAiCallRecorder.failed(context, errorCode, e.getMessage());
                        throw e;
                    }
                }
        );
    }

    private String doChat(
            String input,
            String sessionId,
            AgentPropertiesDO agentProperties,
            String fileUrl,
            Map<String, Object> parameters) throws Exception {
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        xingChenAIClient.chat(
                input,
                StrUtil.isNotBlank(sessionId)
                        ? sessionId
                        : "evaluation_" + System.currentTimeMillis(),
                "{}",
                false,
                responseStream,
                data -> {},
                agentProperties.getApiKey(),
                agentProperties.getApiSecret(),
                agentProperties.getApiFlowId(),
                fileUrl,
                parameters
        );
        return responseStream.toString(StandardCharsets.UTF_8);
    }
}
