package com.cqr.interviewworkshop.conversation.application.port;

import com.cqr.interviewworkshop.ai.dao.entity.AiMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AiMessagePersistencePort {

    List<AiMessage> findHistoryBySessionId(String sessionId);

    Page<AiMessage> pageHistoryBySessionId(String sessionId, Pageable pageable);

    Page<AiMessage> pageHistoryBySessionIds(List<String> sessionIds, Pageable pageable);

    Page<AiMessage> pageAllHistory(Pageable pageable);

    void save(AiMessage message);
}
