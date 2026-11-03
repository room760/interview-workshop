package com.cqr.interviewworkshop.interview.application.jobmatch.rag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.RagProperties;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.LuceneKeywordIndex;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository.JobKnowledgeRepository;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.RetrievalMode;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.impl.JobKnowledgeRetrieverImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 仅在显式 rag-eval profile 下调用真实 Embedding/PgVector；不伪造评测数据。 */
@Component
@Profile("rag-eval & !redis-rag")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class RagEvaluationRunner implements ApplicationRunner {
    private final JobKnowledgeRetrieverImpl retriever;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;
    private final JobKnowledgeRepository repository;
    private final LuceneKeywordIndex keywordIndex;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<Case> cases;
        try (InputStream stream = new ClassPathResource("rag/evaluation.json").getInputStream()) {
            cases = objectMapper.readValue(stream, new TypeReference<List<Case>>() { });
        }

        if (keywordIndex.snapshot().isEmpty()) {
            throw new IllegalStateException("Lucene 索引为空，请先运行 rag-bootstrap");
        }
        List<JobKnowledgeDocument> probe = repository.search("Redis", Math.max(20, properties.getCandidateLimit()));
        if (probe == null || probe.stream().noneMatch(item -> item.getDocumentId() != null
                && !item.getDocumentId().isBlank())) {
            throw new IllegalStateException("PgVector 中没有新版 Chunk；真实评测未开始");
        }
        log.info("REAL RAG evaluation: mode,queries,recall@{},mrr@{},ndcg@{},avg_ms,p50_ms,p95_ms",
                properties.getTopK(), properties.getTopK(), properties.getTopK());

        for (RetrievalMode mode : RetrievalMode.values()) {
            List<RetrievalMetrics.Observation> observations = new ArrayList<>();
            for (Case item : cases) {
                long start = System.nanoTime();
                List<JobKnowledgeDocument> result = retriever.retrieve(item.query(), mode);
                long elapsed = System.nanoTime() - start;
                observations.add(new RetrievalMetrics.Observation(item.relevantDocumentIds(),
                        result.stream().map(JobKnowledgeDocument::getDocumentId).toList(), elapsed));
            }
            RetrievalMetrics.Summary summary = RetrievalMetrics.calculate(observations, properties.getTopK());
            log.info("REAL RAG evaluation: {},{},{},{},{},{},{},{}", mode, cases.size(),
                    summary.recall(), summary.mrr(), summary.ndcg(), summary.averageMs(),
                    summary.p50Ms(), summary.p95Ms());
        }
    }

    public record Case(String query, Set<String> relevantDocumentIds) { }
}
