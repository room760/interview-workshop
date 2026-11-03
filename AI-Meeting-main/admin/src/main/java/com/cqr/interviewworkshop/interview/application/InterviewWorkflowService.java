package com.cqr.interviewworkshop.interview.application;

import com.cqr.interviewworkshop.interview.api.io.req.DemeanorEvaluationReqDTO;
import com.cqr.interviewworkshop.interview.api.io.req.InterviewAnswerReqDTO;
import com.cqr.interviewworkshop.interview.api.io.req.InterviewQuestionReqDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewAnswerRespDTO;
import com.cqr.interviewworkshop.interview.api.io.resp.InterviewQuestionRespDTO;

public interface InterviewWorkflowService {

    InterviewQuestionRespDTO extractInterviewQuestions(InterviewQuestionReqDTO requestParam);

    InterviewAnswerRespDTO answerInterviewQuestion(String sessionId, InterviewAnswerReqDTO requestParam);

    InterviewAnswerRespDTO getNextQuestion(String sessionId);

    InterviewAnswerRespDTO getCurrentQuestion(String sessionId);

    String evaluateDemeanor(DemeanorEvaluationReqDTO requestParam);
}
