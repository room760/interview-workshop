package com.cqr.interviewworkshop.assistant.api;

import com.cqr.interviewworkshop.assistant.api.io.req.AssistantRoutingReqDTO;
import com.cqr.interviewworkshop.assistant.application.routing.AssistantCoordinator;
import com.cqr.interviewworkshop.assistant.application.routing.AssistantCoordinatorResult;
import com.cqr.interviewworkshop.common.convention.annotation.CurrentUser;
import com.cqr.interviewworkshop.common.convention.context.UserContext;
import com.cqr.interviewworkshop.common.convention.result.Result;
import com.cqr.interviewworkshop.common.convention.result.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-workshop/v1/assistant")
@RequiredArgsConstructor
public class AssistantRoutingController {

    private final AssistantCoordinator assistantCoordinator;

    @PostMapping("/route")
    public Result<AssistantCoordinatorResult> route(
            @Valid @RequestBody AssistantRoutingReqDTO requestParam,
            @CurrentUser UserContext currentUser) throws Exception {
        return Results.success(assistantCoordinator.handle(
                requestParam.getMessage(),
                requestParam.getRequestId(),
                requestParam.getSessionId(),
                requestParam.getQuestionNumber(),
                currentUser.getUserId()
        ));
    }
}
