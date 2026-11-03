package com.cqr.interviewworkshop.interview.application.strategy;

import com.cqr.interviewworkshop.interview.service.model.InterviewTurnLog;

import java.util.List;

public interface InterviewScoreAggregatorStrategy {

    int clampScore(Integer score);

    int averageFromAggregate(Long scoreSum, Long answerCount);

    Integer averageFromTurns(List<InterviewTurnLog> turns);
}

