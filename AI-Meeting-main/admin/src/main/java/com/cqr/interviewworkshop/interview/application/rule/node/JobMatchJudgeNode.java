package com.cqr.interviewworkshop.interview.application.rule.node;

import com.cqr.interviewworkshop.interview.application.jobmatch.JobMatchResult;
import com.cqr.interviewworkshop.interview.application.rule.InterviewFollowUpRuleContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

@LiteflowComponent("jobMatchJudge")
public class JobMatchJudgeNode extends NodeComponent {
    private static final int MATCH_THRESHOLD = 70;
    @Override
    public void process() throws Exception {
        InterviewFollowUpRuleContext context = getContextBean(InterviewFollowUpRuleContext.class);
        if (context.isTerminated()) {
            return;
        }
        JobMatchResult jobMatchResult = context.getJobMatchResult();
        if(jobMatchResult == null){
            return;
        }
        Integer matchScore = jobMatchResult.getMatchScore();
        if(matchScore != null && matchScore < MATCH_THRESHOLD){
            context.markNeedFollowUp(
                    "JOB_MATCH_GAP",
                    "job match score below threshold: "
                            + matchScore
            );

        }
    }
}
