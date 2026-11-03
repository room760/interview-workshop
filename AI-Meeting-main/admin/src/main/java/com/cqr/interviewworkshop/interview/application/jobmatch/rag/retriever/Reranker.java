package com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import java.util.List;

public interface Reranker {
    List<JobKnowledgeDocument> rerank(String query, List<JobKnowledgeDocument> candidates, long timeoutMillis);
}
