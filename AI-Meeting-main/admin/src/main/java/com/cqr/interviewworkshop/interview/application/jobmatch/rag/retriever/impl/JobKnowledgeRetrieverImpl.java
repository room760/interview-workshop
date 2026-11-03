package com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.impl;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.LuceneKeywordIndex;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.RagProperties;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository.JobKnowledgeRepository;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.JobKnowledgeRetriever;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.ReciprocalRankFusion;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.Reranker;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.RetrievalMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobKnowledgeRetrieverImpl implements JobKnowledgeRetriever {
    private final JobKnowledgeRepository repository;
    private final ObjectProvider<LuceneKeywordIndex> keywordIndex;
    private final RagProperties properties;
    private final Reranker reranker;

    @Override
    public List<JobKnowledgeDocument> retrieve(String query) {
        RetrievalMode mode = !properties.isEnabled() ? RetrievalMode.VECTOR
                : properties.isRerankEnabled() ? RetrievalMode.HYBRID_RERANK : RetrievalMode.HYBRID;
        return retrieve(query, mode);
    }

    /** 三种模式供相同标注集离线评测；业务入口仍是 retrieve(query)。 */
    public List<JobKnowledgeDocument> retrieve(String query, RetrievalMode mode) {
        if (query == null || query.isBlank()) return List.of();
        int candidateLimit = Math.max(1, properties.getCandidateLimit());
        int topK = Math.max(1, properties.getTopK());
        long started = System.nanoTime();
        List<JobKnowledgeDocument> vector = List.of();

        try {
            vector = repository.search(query, mode == RetrievalMode.VECTOR ? topK : candidateLimit);
        } catch (RuntimeException ex) {
            log.warn("Vector search unavailable", ex);
        }
        long vectorEnd = System.nanoTime();

        if (mode == RetrievalMode.VECTOR) {
            return vector == null ? List.of() : vector;
        }
        List<JobKnowledgeDocument> keyword = List.of();
        LuceneKeywordIndex index = null;

        try {
            index = keywordIndex.getIfAvailable();
        } catch (RuntimeException ex) {
            log.warn("BM25 index unavailable", ex);
        }
        if (index == null) {
            return properties.isFallbackToVector() && vector != null
                    ? vector.stream().limit(topK).toList() : List.of();
        }

        try {
            keyword = index.search(query, candidateLimit);
        } catch (RuntimeException ex) {
            log.warn("BM25 search unavailable", ex);
        }
        long keywordEnd = System.nanoTime();
        if ((keyword == null || keyword.isEmpty()) && !properties.isFallbackToVector()) {
            return List.of();
        }

        List<JobKnowledgeDocument> fused = ReciprocalRankFusion.fuse(vector, keyword,
                Math.max(1, properties.getRrfK())).stream()
                .map(ReciprocalRankFusion.ScoredChunk::document).toList();
        long fusionEnd = System.nanoTime();

        List<JobKnowledgeDocument> output = fused;
        if (mode == RetrievalMode.HYBRID_RERANK && !fused.isEmpty()) {
            List<JobKnowledgeDocument> candidates = fused.stream()
                    .limit(Math.max(1, properties.getRerankLimit())).toList();
            try {
                output = reranker.rerank(query, candidates, properties.getRerankTimeoutMillis());
            } catch (RuntimeException ex) {
                log.warn("Rerank failed; using RRF order", ex);
            }
        }
        long ended = System.nanoTime();

        log.info("RAG stages ms vector={}, bm25={}, rrf={}, rerank={}, mode={}",
                millis(vectorEnd - started), millis(keywordEnd - vectorEnd),
                millis(fusionEnd - keywordEnd), millis(ended - fusionEnd), mode);
        return output.stream().limit(topK).toList();
    }

    private long millis(long nanos) { return nanos / 1_000_000L; }
}
