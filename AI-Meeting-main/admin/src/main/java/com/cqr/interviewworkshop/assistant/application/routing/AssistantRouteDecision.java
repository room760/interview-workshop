package com.cqr.interviewworkshop.assistant.application.routing;

import lombok.Data;

@Data
public class AssistantRouteDecision {
    private AssistantIntent intent;
    private String companyName;
    private String jobTitle;
    private String officialDomain;
    private String reason;
}
