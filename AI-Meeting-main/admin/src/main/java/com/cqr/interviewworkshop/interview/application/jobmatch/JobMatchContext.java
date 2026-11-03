package com.cqr.interviewworkshop.interview.application.jobmatch;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import lombok.Data;

import java.util.List;

@Data
public class JobMatchContext {
    private String jobTitle;
    private String jobRequirement;
    private String question;
    private String answer;
    private List<JobKnowledgeDocument> retrievedDocuments;
    private String companyName;
    private String officialDomain;
}
