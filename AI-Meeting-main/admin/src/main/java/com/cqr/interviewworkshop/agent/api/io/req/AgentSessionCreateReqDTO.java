package com.cqr.interviewworkshop.agent.api.io.req;

import lombok.Data;

@Data
public class AgentSessionCreateReqDTO {

    private String userName;

    private String firstMessage;
}
