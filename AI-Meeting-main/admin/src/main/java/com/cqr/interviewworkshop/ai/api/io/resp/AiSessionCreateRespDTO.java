package com.cqr.interviewworkshop.ai.api.io.resp;

import lombok.Data;

/**
 * AI会话创建响应DTO
 */
@Data
public class AiSessionCreateRespDTO {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 会话标题
     */
    private String conversationTitle;
}