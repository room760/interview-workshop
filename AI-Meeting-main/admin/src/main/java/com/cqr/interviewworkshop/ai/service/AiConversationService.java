package com.cqr.interviewworkshop.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cqr.interviewworkshop.ai.api.io.req.AiConversationPageReqDTO;
import com.cqr.interviewworkshop.ai.api.io.resp.AiConversationRespDTO;
import com.cqr.interviewworkshop.ai.api.io.resp.AiSessionCreateRespDTO;

public interface AiConversationService {

    String createConversation(String username, Long aiId, String firstMessage);

    AiSessionCreateRespDTO createConversationWithTitle(String username, Long aiId, String firstMessage);

    IPage<AiConversationRespDTO> pageConversations(String username, AiConversationPageReqDTO requestParam);

    void updateConversation(String sessionId, Integer messageSeq, String title);

    void updateConversation(String sessionId, Integer messageSeq, String title, String username);

    void endConversation(String sessionId);

    void endConversation(String sessionId, String username);

    void deleteConversation(String sessionId);

    void deleteConversation(String sessionId, String username);

    AiConversationRespDTO getConversationBySessionId(String sessionId);

    AiConversationRespDTO getConversationBySessionId(String sessionId, String username);

    void requireOwnedConversation(String sessionId, String username);

    java.util.List<String> listOwnedSessionIds(String username);
}
