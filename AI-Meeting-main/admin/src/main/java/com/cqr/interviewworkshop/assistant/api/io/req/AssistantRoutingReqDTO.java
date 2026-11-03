
package com.cqr.interviewworkshop.assistant.api.io.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssistantRoutingReqDTO {

    /**
     * 用户本次输入的自然语言。
     */
    @NotBlank(message = "message cannot be blank")
    @Size(max = 5000, message = "message length must be <= 5000")
    private String message;

    /**
     * 面试场景使用；岗位备战场景可以不传。
     */
    @Size(max = 64)
    private String sessionId;

    /**
     * 当前面试题号；岗位备战场景可以不传。
     */
    @Size(max = 32)
    private String questionNumber;

    /**
     * 用于同一请求重试时保持请求标识一致。
     */
    @Size(max = 64)
    private String requestId;
}
