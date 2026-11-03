package com.cqr.interviewworkshop.interview.application.jobmatch.rag.loader;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository.JobKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobKnowledgeLoader {
    private final JobKnowledgeRepository repository;
    public void load(List<JobKnowledgeDocument> documents){
        if (documents == null) throw new IllegalArgumentException("知识源文档列表不能为空");
        repository.sync(documents);
    }
}
