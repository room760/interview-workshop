package com.cqr.interviewworkshop.interview.application.strategy;

import com.cqr.interviewworkshop.interview.api.io.resp.RadarChartDTO;

public interface RadarComputationStrategy {

    RadarChartDTO compute(Integer resumeScore, Integer interviewScore, Integer demeanorScore);
}

