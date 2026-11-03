package com.cqr.interviewworkshop.assistant.application.routing;

import com.cqr.interviewworkshop.interview.api.io.resp.InterviewAnswerRespDTO;
import lombok.Data;

@Data
public class AssistantCoordinatorResult {
    private AssistantIntent intent;
    private String message;
    private AssistantRouteDecision decision;
    private InterviewAnswerRespDTO interviewResponse;
}
