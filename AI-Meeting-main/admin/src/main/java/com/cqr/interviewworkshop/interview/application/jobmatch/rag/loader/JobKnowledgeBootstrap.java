package com.cqr.interviewworkshop.interview.application.jobmatch.rag.loader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.JobKnowledgeRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@Profile("rag-bootstrap")
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class JobKnowledgeBootstrap implements ApplicationRunner {
    private final JobKnowledgeLoader jobKnowledgeLoader;
    private final JobKnowledgeRetriever jobKnowledgeRetriever;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始 JobMatch RAG knowledge bootstrap");
        ClassPathResource resource = new ClassPathResource("rag/job-knowledge.json");
        if (!resource.exists()) {
            throw new IllegalStateException("RAG知识文件不存在: rag/job-knowledge.json");
        }

        // 2. JSON → JobKnowledgeDocument
        List<JobKnowledgeDocument> documents;
        try (InputStream inputStream = resource.getInputStream()) {
            documents = objectMapper.readValue(inputStream, new TypeReference<List<JobKnowledgeDocument>>() {});
        }
        if (documents == null) throw new IllegalStateException("RAG知识文件不能为 null");
        if (documents.isEmpty()) {
            log.warn("No RAG knowledge documents found");
        }
        log.info("Loaded {} RAG knowledge documents", documents.size());

        // 全量源快照同步：未变化文档跳过，源文件删除的文档清理索引。
        jobKnowledgeLoader.load(documents);
        log.info("RAG knowledge bootstrap completed, documentCount={}", documents.size());

        // 4. 做一次实际检索探针
        List<JobKnowledgeDocument> results = jobKnowledgeRetriever.retrieve("Java后端 Redis 缓存穿透 缓存击穿");
        log.info("RAG retrieval probe completed, resultCount={}", results == null ? 0 : results.size());
        if (results != null) {
            for (JobKnowledgeDocument document : results) {
                log.info("RAG retrieval result: {}", document.getContent());
            }
        }
    }
}
