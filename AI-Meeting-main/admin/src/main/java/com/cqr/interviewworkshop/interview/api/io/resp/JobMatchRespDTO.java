package com.cqr.interviewworkshop.interview.api.io.resp;

import lombok.Data;

import java.util.List;

@Data
public class JobMatchRespDTO {

    private Integer matchScore;

    private List<String> matchedSkills;

    private List<String> missingSkills;

    private String gapAnalysis;

    private String suggestedAction;
}
