package com.cqr.interviewworkshop.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cqr.interviewworkshop.ai.api.io.req.AiMessageReqDTO;
import com.cqr.interviewworkshop.ai.api.io.resp.AiMessageHistoryRespDTO;
import reactor.core.publisher.Flux;

import java.util.List;

public interface AiMessageService {


    Flux<String> aiChatFlux(AiMessageReqDTO requestParam);

    Flux<String> aiChatFlux(AiMessageReqDTO requestParam, String username);

    List<AiMessageHistoryRespDTO> getConversationHistory(String sessionId);

    List<AiMessageHistoryRespDTO> getConversationHistory(String sessionId, String username);

    IPage<AiMessageHistoryRespDTO> pageHistoryMessages(String sessionId, Integer current, Integer size);

    IPage<AiMessageHistoryRespDTO> pageHistoryMessages(String sessionId, Integer current, Integer size, String username);

    Long getUserIdByUsername(String username);
}
