package com.cqr.interviewworkshop.interview.application.observability.agent;

import com.cqr.interviewworkshop.interview.application.guard.core.InterviewAiGuardStage;
import org.springframework.stereotype.Component;

@Component
public class AgentNameResolver {
    public String resolve(String stage){
        if(stage == null){
            return "UnknownAgent";
        }
        if(InterviewAiGuardStage.INTERVIEW_EVALUATION.equals(stage)){
            return "EvaluationAgent";
        }
        if (InterviewAiGuardStage.INTERVIEW_JOB_MATCH.equals(stage)) {
            return "JobMatchAgent";
        }
        if(InterviewAiGuardStage.INTERVIEW_DEMEANOR.equals(stage)){
            return "DemeanorAgent";
        }
        if(InterviewAiGuardStage.INTERVIEW_FOLLOWUP.equals(stage)){
            return "FollowUpAgent";
        }
        if(InterviewAiGuardStage.INTERVIEW_EXTRACTION.equals(stage)){
            return "ExtractionAgent";
        }
        return "UnknownAgent";
    }
}

