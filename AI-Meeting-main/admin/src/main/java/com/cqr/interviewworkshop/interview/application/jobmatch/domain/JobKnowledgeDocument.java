package com.cqr.interviewworkshop.interview.application.jobmatch.domain;

import lombok.Data;

@Data
public class JobKnowledgeDocument {
    private String documentId;
    private String chunkId;
    private Integer chunkIndex;
    private String contentHash;
    private String title;
    private String content;
    private String source;
    private String tag;
}
