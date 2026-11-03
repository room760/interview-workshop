package com.cqr.interviewworkshop.interview.application.jobmatch.application;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.JobKnowledgeChunker;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.LuceneKeywordIndex;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.RagProperties;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.eval.RetrievalMetrics;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.ReciprocalRankFusion;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.RuleReranker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RagCoreTest {
    @Test
    void chunkingHonorsShortTextBoundariesOverlapAndStableIds() {
        RagProperties options = new RagProperties();
        options.setChunkSize(40);
        options.setChunkOverlap(8);
        JobKnowledgeChunker chunker = new JobKnowledgeChunker(options);
        JobKnowledgeDocument shortDoc = doc("stable", "Redis CAS");
        assertEquals(1, chunker.chunk(shortDoc).size());
        assertEquals(chunker.chunk(shortDoc).get(0).getChunkId(),
                java.util.UUID.fromString(chunker.chunk(shortDoc).get(0).getChunkId()).toString());
        String longText = "Redis 缓存穿透。\n\n缓存击穿需要互斥锁。\n\n" + "JVM G1 回收 Region。".repeat(8);
        List<JobKnowledgeDocument> chunks = chunker.chunk(doc("stable", longText));
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getContent().length() <= 40));
        assertEquals(chunks.get(0).getContentHash(), chunks.get(1).getContentHash());
        assertEquals(chunks.get(0).getChunkId(), chunker.chunk(doc("stable", longText)).get(0).getChunkId());
        options.setChunkOverlap(40);
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk(shortDoc));
    }

    @Test
    void suppliedKnowledgeActuallyUsesChunkingAndLabelsAreFixed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JobKnowledgeDocument> documents;
        try (var stream = new ClassPathResource("rag/job-knowledge.json").getInputStream()) {
            documents = mapper.readValue(stream, new TypeReference<List<JobKnowledgeDocument>>() { });
        }
        assertTrue(new JobKnowledgeChunker(new RagProperties()).chunk(documents.get(0)).size() > 1);
        try (var stream = new ClassPathResource("rag/evaluation.json").getInputStream()) {
            List<java.util.Map<String, Object>> cases = mapper.readValue(stream, new TypeReference<>() { });
            assertEquals(24, cases.size());
        }
    }

    @Test
    void luceneBm25RecallsExactTechnicalTerms() throws Exception {
        RagProperties options = new RagProperties();
        options.setIndexPath(Files.createTempDirectory(Path.of("target"), "rag-bm25-").toString());
        LuceneKeywordIndex index = new LuceneKeywordIndex(options);
        try {
            index.replace("redis", List.of(doc("redis", "Redis 缓存与分布式锁")));
            index.replace("jvm", List.of(doc("jvm", "JVM G1 Region 垃圾回收")));
            index.replace("cas", List.of(doc("cas", "Java CAS ABA 并发更新")));
            index.replace("cloud", List.of(doc("cloud", "Spring Cloud 服务注册发现")));
            assertEquals("redis", index.search("Redis", 3).get(0).getDocumentId());
            assertEquals("jvm", index.search("JVM G1", 3).get(0).getDocumentId());
            assertEquals("cas", index.search("CAS", 3).get(0).getDocumentId());
            assertEquals("cloud", index.search("Spring Cloud", 3).get(0).getDocumentId());
        } finally { index.close(); }
    }

    @Test
    void rrfDeduplicatesAndUsesReciprocalRankFormula() {
        JobKnowledgeDocument a = doc("a", "A");
        JobKnowledgeDocument b = doc("b", "B");
        var fused = ReciprocalRankFusion.fuse(List.of(a, b), List.of(b, a), 60);
        assertEquals(2, fused.size());
        assertEquals(1.0 / 61 + 1.0 / 62, fused.get(0).score(), 1e-9);
    }

    @Test
    void ruleRerankMovesExactTermAhead() {
        RuleReranker reranker = new RuleReranker();
        assertEquals("redis", reranker.rerank("Redis", List.of(doc("other", "JVM"), doc("redis", "Redis 缓存")),
                100).get(0).getDocumentId());
        assertEquals("cache", reranker.rerank("Redis缓存穿透",
                List.of(doc("lock", "Redis 分布式锁"), doc("cache", "Redis 缓存发生穿透时会回源")),
                100).get(0).getDocumentId());
        assertEquals("gc", reranker.rerank("JVM垃圾回收",
                List.of(doc("loader", "JVM 类加载机制"), doc("gc", "JVM 垃圾处理与回收机制")),
                100).get(0).getDocumentId());
    }

    @Test
    void metricsAreCalculatedFromFixedLabels() {
        var summary = RetrievalMetrics.calculate(List.of(
                new RetrievalMetrics.Observation(Set.of("a"), List.of("x", "a"), 1_000_000),
                new RetrievalMetrics.Observation(Set.of("b"), List.of("b"), 3_000_000)), 2);
        assertEquals(1, summary.recall(), 1e-9);
        assertEquals(0.75, summary.mrr(), 1e-9);
        assertEquals((1.0 / (Math.log(3) / Math.log(2)) + 1) / 2, summary.ndcg(), 1e-9);
        assertEquals(2, summary.averageMs(), 1e-9);
        assertEquals(1, summary.p50Ms(), 1e-9);
        assertEquals(3, summary.p95Ms(), 1e-9);
    }

    private JobKnowledgeDocument doc(String id, String content) {
        JobKnowledgeDocument value = new JobKnowledgeDocument();
        value.setDocumentId(id);
        value.setChunkId(id + ":0");
        value.setContentHash(JobKnowledgeChunker.sha256(content));
        value.setContent(content);
        return value;
    }
}
