package com.cqr.interviewworkshop.interview.application.jobmatch;

import lombok.Data;

import java.util.List;

@Data
public class JobMatchResult {
    private Integer matchScore;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private String gapAnalysis;
    private String suggestedAction;
}
