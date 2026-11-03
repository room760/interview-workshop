package com.cqr.interviewworkshop.assistant.application.routing;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.cqr.interviewworkshop.interview.api.io.req.InterviewAnswerReqDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewAnswerRespDTO;
import com.cqr.interviewworkshop.interview.application.jobmatch.application.JobPreparationService;
import com.cqr.interviewworkshop.interview.flow.session.InterviewSessionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssistantCoordinator {
    private final IntentRouter intentRouter;
    private final InterviewSessionFacade interviewSessionFacade;
    private final JobPreparationService jobPreparationService;

    public AssistantCoordinatorResult handle(
            String userMessage,
            String requestId,
            String sessionId,
            String questionNumber,
            Long userId) throws Exception {
        String safeRequestId = normalizeRequestId(requestId);

        String routingMessage = userMessage;

        if (StrUtil.isNotBlank(sessionId)
                && StrUtil.isNotBlank(questionNumber)) {

            routingMessage = """
            请求上下文：
            用户正在面试会话中提交当前题目的回答。
            如果用户没有明确提出独立的岗位备战需求，
            应优先判断为 INTERVIEW_ANSWER。

            用户原始消息：
            %s
            """.formatted(userMessage);
        }

        AssistantRouteDecision decision =
                intentRouter.route(routingMessage, safeRequestId);

        AssistantCoordinatorResult result = new AssistantCoordinatorResult();
        result.setIntent(decision.getIntent());
        result.setDecision(decision);

        switch (decision.getIntent()) {
            case INTERVIEW_ANSWER -> {
                // 标准面试必须具有有效的会话、题号和用户身份。
                if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(questionNumber) || userId == null) {
                    result.setIntent(AssistantIntent.CLARIFY);
                    result.setMessage("请提供有效的面试会话、当前题号和用户身份，" + "再提交面试回答。");
                    return result;
                }

                InterviewAnswerReqDTO request = new InterviewAnswerReqDTO();

                request.setSessionId(sessionId);
                request.setQuestionNumber(questionNumber);
                request.setAnswerContent(userMessage);
                request.setRequestId(normalizeRequestId(requestId));
                // 保留用户或路由器识别出的岗位信息。
                request.setCompanyName(decision.getCompanyName());
                request.setJobTitle(decision.getJobTitle());
                request.setOfficialDomain(decision.getOfficialDomain());

                InterviewAnswerRespDTO interviewResponse =
                        interviewSessionFacade.answerInterviewQuestion(
                                sessionId,
                                request,
                                userId
                        );
                result.setInterviewResponse(interviewResponse);
                result.setMessage("已交由标准面试链路处理。");
            }

            case JOB_PREPARATION -> {
                // 岗位名称是备战分析的必要信息。
                if (StrUtil.isBlank(decision.getJobTitle())) {
                    result.setIntent(AssistantIntent.CLARIFY);
                    result.setMessage("请告诉我你想准备的具体岗位，例如 Java 后端开发。");
                    return result;
                }

                // 调用独立岗位备战链路。
                String preparationResult = jobPreparationService.prepare(
                                userMessage,
                                safeRequestId,
                                decision.getCompanyName(),
                                decision.getJobTitle(),
                                decision.getOfficialDomain()
                        );

                // 统一结果对象暂时用 message 承载岗位备战输出。
                result.setMessage(preparationResult);
            }

            case CLARIFY -> {
                result.setMessage(
                        StrUtil.isNotBlank(decision.getReason())
                                ? decision.getReason()
                                : "请进一步说明你希望完成的任务。"
                );
            }
        }

        return result;
    }

    private String normalizeRequestId(String requestId) {
        if (StrUtil.isBlank(requestId)) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}
