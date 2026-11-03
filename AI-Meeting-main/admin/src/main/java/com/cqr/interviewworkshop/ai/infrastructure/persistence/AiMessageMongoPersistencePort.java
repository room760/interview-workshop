package com.cqr.interviewworkshop.ai.infrastructure.persistence;

import com.cqr.interviewworkshop.ai.dao.entity.AiMessage;
import com.cqr.interviewworkshop.ai.dao.repository.AiMessageRepository;
import com.cqr.interviewworkshop.conversation.application.port.AiMessagePersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AiMessageMongoPersistencePort implements AiMessagePersistencePort {

    private final AiMessageRepository aiMessageRepository;

    @Override
    public List<AiMessage> findHistoryBySessionId(String sessionId) {
        return aiMessageRepository.findBySessionIdAndDelFlagOrderByMessageSeqAsc(sessionId, 0);
    }

    @Override
    public Page<AiMessage> pageHistoryBySessionId(String sessionId, Pageable pageable) {
        return aiMessageRepository.findBySessionIdAndDelFlagOrderByCreateTimeAsc(sessionId, 0, pageable);
    }

    @Override
    public Page<AiMessage> pageHistoryBySessionIds(List<String> sessionIds, Pageable pageable) {
        return aiMessageRepository.findBySessionIdInAndDelFlagOrderByCreateTimeDesc(sessionIds, 0, pageable);
    }

    @Override
    public Page<AiMessage> pageAllHistory(Pageable pageable) {
        return aiMessageRepository.findByDelFlagOrderByCreateTimeDesc(0, pageable);
    }

    @Override
    public void save(AiMessage message) {
        aiMessageRepository.save(message);
    }
}
