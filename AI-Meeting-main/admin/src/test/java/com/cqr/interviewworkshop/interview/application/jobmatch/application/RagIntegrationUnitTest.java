package com.cqr.interviewworkshop.interview.application.jobmatch.application;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.JobKnowledgeChunker;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.LuceneKeywordIndex;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.RagProperties;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository.JobKnowledgeRepository;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository.PgVectorJobKnowledgeRepository;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.Reranker;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.RetrievalMode;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever.impl.JobKnowledgeRetrieverImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RagIntegrationUnitTest {
    @Test
    void syncSkipsUnchangedAndRemovesChangedAndDeletedChunks() throws Exception {
        RagProperties options = new RagProperties();
        options.setChunkSize(40);
        options.setChunkOverlap(5);
        options.setIndexPath(Files.createTempDirectory(Path.of("target"), "rag-sync-").toString());
        VectorStore vectorStore = mock(VectorStore.class);
        LuceneKeywordIndex index = new LuceneKeywordIndex(options);
        try {
            @SuppressWarnings("unchecked") ObjectProvider<LuceneKeywordIndex> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(index);
            PgVectorJobKnowledgeRepository repository = new PgVectorJobKnowledgeRepository(
                    vectorStore, new JobKnowledgeChunker(options), provider);
            JobKnowledgeDocument original = doc("redis", "Redis 缓存穿透与缓存击穿。".repeat(8));
            repository.sync(List.of(original));
            int originalCount = index.snapshot().get("redis").size();
            assertTrue(originalCount > 1);
            List<String> originalIds = index.snapshot().get("redis").stream()
                    .map(JobKnowledgeDocument::getChunkId).toList();
            repository.sync(List.of(original));
            verify(vectorStore, times(1)).add(anyList());

            JobKnowledgeDocument changed = doc("redis", "Redis 新版内容");
            repository.sync(List.of(changed));
            verify(vectorStore, times(2)).add(anyList());
            verify(vectorStore, atLeastOnce()).delete(eq(originalIds));
            assertEquals(1, index.snapshot().get("redis").size());
            assertEquals("Redis 新版内容", index.snapshot().get("redis").get(0).getContent());

            List<String> changedIds = index.snapshot().get("redis").stream()
                    .map(JobKnowledgeDocument::getChunkId).toList();
            repository.sync(List.of());
            assertTrue(index.snapshot().isEmpty());
            verify(vectorStore).delete(eq(changedIds));
        } finally { index.close(); }
    }

    @Test
    void retryAfterPartialVectorWriteRemovesNewChunksNotInOldLuceneSnapshot() throws Exception {
        RagProperties options = new RagProperties();
        options.setChunkSize(20);
        options.setChunkOverlap(4);
        Path path = Files.createTempDirectory(Path.of("target"), "rag-recovery-");
        options.setIndexPath(path.toString());
        VectorStore vectorStore = mock(VectorStore.class);
        Set<String> vectorIds = new HashSet<>();
        AtomicBoolean failNextAdd = new AtomicBoolean();
        doAnswer(call -> {
            List<org.springframework.ai.document.Document> values = call.getArgument(0);
            int count = failNextAdd.getAndSet(false) ? Math.min(3, values.size()) : values.size();
            for (int i = 0; i < count; i++) vectorIds.add(values.get(i).getId());
            if (count < values.size()) throw new IllegalStateException("simulated partial add");
            return null;
        }).when(vectorStore).add(anyList());
        doAnswer(call -> {
            List<String> ids = call.getArgument(0);
            vectorIds.removeAll(ids);
            return null;
        }).when(vectorStore).delete(anyList());

        JobKnowledgeDocument old = doc("recovery", "旧版内容。".repeat(7));
        JobKnowledgeDocument expanded = doc("recovery", "新版扩展内容。".repeat(12));
        LuceneKeywordIndex firstIndex = new LuceneKeywordIndex(options);
        try {
            PgVectorJobKnowledgeRepository first = repository(vectorStore, options, firstIndex);
            first.sync(List.of(old));
            assertTrue(firstIndex.snapshot().get("recovery").size() <
                    new JobKnowledgeChunker(options).chunk(expanded).size());
            failNextAdd.set(true);
            assertThrows(IllegalStateException.class, () -> first.sync(List.of(expanded)));
            assertFalse(firstIndex.pendingSnapshot().get("recovery").isEmpty());
        } finally { firstIndex.close(); }

        LuceneKeywordIndex reopened = new LuceneKeywordIndex(options);
        try {
            PgVectorJobKnowledgeRepository recovered = repository(vectorStore, options, reopened);
            recovered.sync(List.of(doc("recovery", "最终版")));
            assertEquals(Set.of(new JobKnowledgeChunker(options)
                    .chunk(doc("recovery", "最终版")).get(0).getChunkId()), vectorIds);
            assertEquals("最终版", reopened.snapshot().get("recovery").get(0).getContent());
            assertTrue(reopened.pendingSnapshot().isEmpty());
        } finally { reopened.close(); }
    }

    @Test
    void deletedSourceClearsPendingChunksAfterFailedFirstImport() throws Exception {
        RagProperties options = new RagProperties();
        options.setChunkSize(20);
        options.setChunkOverlap(4);
        options.setIndexPath(Files.createTempDirectory(Path.of("target"), "rag-pending-delete-").toString());
        VectorStore vectorStore = mock(VectorStore.class);
        doThrow(new IllegalStateException("partial import")).when(vectorStore).add(anyList());
        LuceneKeywordIndex index = new LuceneKeywordIndex(options);
        try {
            PgVectorJobKnowledgeRepository repository = repository(vectorStore, options, index);
            assertThrows(IllegalStateException.class,
                    () -> repository.sync(List.of(doc("removed", "临时内容。".repeat(8)))));
            assertTrue(index.snapshot().isEmpty());
            List<String> pending = index.pendingSnapshot().get("removed");
            assertFalse(pending.isEmpty());
            repository.sync(List.of());
            assertTrue(index.pendingSnapshot().isEmpty());
            verify(vectorStore, times(2)).delete(eq(pending));
        } finally { index.close(); }
    }

    @Test
    void migratesLegacyChunkIdsWithoutPassingThemToPgVector() throws Exception {
        RagProperties options = new RagProperties();
        options.setIndexPath(Files.createTempDirectory(Path.of("target"), "rag-legacy-").toString());
        VectorStore vectorStore = mock(VectorStore.class);
        LuceneKeywordIndex index = new LuceneKeywordIndex(options);
        try {
            JobKnowledgeDocument legacy = doc("distributed-lock", "旧版内容");
            legacy.setChunkId("distributed-lock:0");
            legacy.setChunkIndex(0);
            legacy.setContentHash(JobKnowledgeChunker.sha256(legacy.getContent()));
            index.replace("distributed-lock", List.of(legacy));

            PgVectorJobKnowledgeRepository repository = repository(vectorStore, options, index);
            repository.sync(List.of(doc("distributed-lock", "新版内容")));

            verify(vectorStore, never()).delete(eq(List.of("distributed-lock:0")));
            verify(vectorStore).add(anyList());
            String migratedId = index.snapshot().get("distributed-lock").get(0).getChunkId();
            assertEquals(migratedId, java.util.UUID.fromString(migratedId).toString());
            verify(vectorStore).delete(eq(List.of(migratedId)));
        } finally { index.close(); }
    }

    @Test
    void hybridSurvivesVectorFailureAndRerankFailure() {
        JobKnowledgeRepository repository = mock(JobKnowledgeRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<LuceneKeywordIndex> provider = mock(ObjectProvider.class);
        LuceneKeywordIndex index = mock(LuceneKeywordIndex.class);
        Reranker reranker = mock(Reranker.class);
        RagProperties options = new RagProperties();
        JobKnowledgeDocument keyword = doc("redis", "Redis 缓存");
        when(provider.getIfAvailable()).thenReturn(index);
        when(repository.search("Redis", 20)).thenThrow(new IllegalStateException("db down"));
        when(index.search("Redis", 20)).thenReturn(List.of(keyword));
        when(reranker.rerank(anyString(), anyList(), anyLong()))
                .thenThrow(new IllegalStateException("rerank failed"));
        JobKnowledgeRetrieverImpl retriever = new JobKnowledgeRetrieverImpl(repository, provider, options, reranker);
        assertEquals("redis", retriever.retrieve("Redis", RetrievalMode.HYBRID_RERANK).get(0).getDocumentId());
        verify(reranker).rerank(anyString(), anyList(), anyLong());
    }

    @Test
    void hybridFallsBackToVectorWhenKeywordFails() {
        JobKnowledgeRepository repository = mock(JobKnowledgeRepository.class);
        @SuppressWarnings("unchecked") ObjectProvider<LuceneKeywordIndex> provider = mock(ObjectProvider.class);
        LuceneKeywordIndex index = mock(LuceneKeywordIndex.class);
        when(provider.getIfAvailable()).thenReturn(index);
        when(index.search("CAS", 20)).thenThrow(new IllegalStateException("index locked"));
        when(repository.search("CAS", 20)).thenReturn(List.of(doc("cas", "CAS")));
        JobKnowledgeRetrieverImpl retriever = new JobKnowledgeRetrieverImpl(repository, provider,
                new RagProperties(), mock(Reranker.class));
        assertEquals("cas", retriever.retrieve("CAS", RetrievalMode.HYBRID).get(0).getDocumentId());
    }

    private PgVectorJobKnowledgeRepository repository(VectorStore vectorStore, RagProperties options,
                                                      LuceneKeywordIndex index) {
        @SuppressWarnings("unchecked") ObjectProvider<LuceneKeywordIndex> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(index);
        return new PgVectorJobKnowledgeRepository(vectorStore, new JobKnowledgeChunker(options), provider);
    }

    private JobKnowledgeDocument doc(String id, String content) {
        JobKnowledgeDocument document = new JobKnowledgeDocument();
        document.setDocumentId(id);
        document.setContent(content);
        return document;
    }
}
