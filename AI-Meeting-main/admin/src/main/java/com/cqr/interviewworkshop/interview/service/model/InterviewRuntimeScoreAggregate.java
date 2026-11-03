package com.cqr.interviewworkshop.interview.service.model;

import lombok.Data;

/**
 * 定义面试运行态中的得分聚合结果对象，
 * 用于承载总分、累计得分和计分次数等恢复所需的聚合信息。
 *
 */
@Data
public class InterviewRuntimeScoreAggregate {

    private Integer scoreSum;

    private Integer scoreCount;

    private Integer totalScore;
}
