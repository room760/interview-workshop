package com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;

import java.util.List;

public interface JobKnowledgeRepository {
    void save(JobKnowledgeDocument document) throws IllegalAccessException;
    List<JobKnowledgeDocument> search(String query,int topK);
    default void sync(List<JobKnowledgeDocument> documents) {
        for (JobKnowledgeDocument document : documents) {
            try {
                save(document);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("岗位知识入库失败", ex);
            }
        }
    }
}
