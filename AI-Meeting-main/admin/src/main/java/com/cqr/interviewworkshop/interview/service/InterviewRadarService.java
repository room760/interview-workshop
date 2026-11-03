package com.cqr.interviewworkshop.interview.service;

import com.cqr.interviewworkshop.interview.api.io.resp.RadarChartDTO;

public interface InterviewRadarService {

    RadarChartDTO buildRadarChart(Integer resumeScore, Integer interviewScore, Integer demeanorScore);
}

