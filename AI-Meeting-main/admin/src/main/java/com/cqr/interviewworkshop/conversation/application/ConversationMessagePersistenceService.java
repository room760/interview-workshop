package com.cqr.interviewworkshop.conversation.application;

import com.cqr.interviewworkshop.agent.dao.entity.AgentMessage;
import com.cqr.interviewworkshop.ai.dao.entity.AiMessage;
import com.cqr.interviewworkshop.conversation.application.port.AgentMessagePersistencePort;
import com.cqr.interviewworkshop.conversation.application.port.AiMessagePersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class ConversationMessagePersistenceService {

    private final MessageSequenceService messageSequenceService;
    private final AiMessagePersistencePort aiMessagePersistencePort;
    private final AgentMessagePersistencePort agentMessagePersistencePort;

    public int saveAiUserMessage(String sessionId, String content) {
        int messageSeq = messageSequenceService.nextAiMessageSeq(sessionId);
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setMessageType(1);
        message.setMessageContent(content);
        message.setMessageSeq(messageSeq);
        message.setCreateTime(new Date());
        message.setDelFlag(0);
        aiMessagePersistencePort.save(message);
        return messageSeq;
    }

    public int saveAiAssistantMessage(
            String sessionId,
            String content,
            String reasoningContent,
            int responseTime,
            String errorMessage) {
        int messageSeq = messageSequenceService.nextAiMessageSeq(sessionId);
        AiMessage message = new AiMessage();
        message.setSessionId(sessionId);
        message.setMessageType(2);
        message.setMessageContent(content);
        message.setReasoningContent(reasoningContent);
        message.setMessageSeq(messageSeq);
        message.setResponseTime(responseTime);
        message.setErrorMessage(errorMessage);
        message.setCreateTime(new Date());
        message.setDelFlag(0);
        aiMessagePersistencePort.save(message);
        return messageSeq;
    }

    public int saveAgentUserMessage(String sessionId, String content) {
        int messageSeq = messageSequenceService.nextAgentMessageSeq(sessionId);
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setMessageType(1);
        message.setMessageContent(content);
        message.setMessageSeq(messageSeq);
        message.setCreateTime(new Date());
        message.setDelFlag(0);
        agentMessagePersistencePort.save(message);
        return messageSeq;
    }

    public int saveAgentAssistantMessage(String sessionId, String content, int responseTime, String errorMessage) {
        int messageSeq = messageSequenceService.nextAgentMessageSeq(sessionId);
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setMessageType(2);
        message.setMessageContent(content);
        message.setMessageSeq(messageSeq);
        message.setResponseTime(responseTime);
        message.setErrorMessage(errorMessage);
        message.setCreateTime(new Date());
        message.setDelFlag(0);
        agentMessagePersistencePort.save(message);
        return messageSeq;
    }
}
