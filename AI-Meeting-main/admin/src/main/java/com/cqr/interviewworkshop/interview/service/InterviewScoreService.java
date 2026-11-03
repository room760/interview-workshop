package com.cqr.interviewworkshop.interview.service;

import com.cqr.interviewworkshop.interview.api.io.req.DemeanorScoreDTO;
import com.cqr.interviewworkshop.interview.service.model.InterviewTurnLog;

import java.util.List;

public interface InterviewScoreService {

    Integer getSessionTotalScore(String sessionId, List<InterviewTurnLog> turns);

    Integer addSessionScore(String sessionId, Integer score);

    void resetSessionScore(String sessionId);

    Integer getSessionDemeanorScore(String sessionId);

    DemeanorScoreDTO getSessionDemeanorScoreDetails(String sessionId);
}

