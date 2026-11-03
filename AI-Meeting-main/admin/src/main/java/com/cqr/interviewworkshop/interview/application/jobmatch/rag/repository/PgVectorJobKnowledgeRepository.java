package com.cqr.interviewworkshop.interview.application.jobmatch.rag.repository;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.JobKnowledgeChunker;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.LuceneKeywordIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Repository
@Profile("!redis-rag")
@RequiredArgsConstructor
@Slf4j
public class PgVectorJobKnowledgeRepository implements JobKnowledgeRepository {
    private final VectorStore vectorStore;
    private final JobKnowledgeChunker chunker;
    private final ObjectProvider<LuceneKeywordIndex> keywordIndex;

    @Override
    public synchronized void save(JobKnowledgeDocument document) throws IllegalAccessException {
        if (document == null || document.getContent() == null || document.getContent().isBlank()) {
            throw new IllegalAccessException("岗位知识内容不能为空");
        }
        LuceneKeywordIndex index = index();
        upsert(chunker.chunk(document), index.snapshot(), index.pendingSnapshot(), index);
    }

    /** 传入完整源快照；删除未出现的文档。Lucene pending ID 保留异常重试所需的清理范围。 */
    @Override
    public synchronized void sync(List<JobKnowledgeDocument> documents) {
        LuceneKeywordIndex index = index();
        Map<String, List<JobKnowledgeDocument>> previous = index.snapshot();
        Map<String, List<String>> pending = index.pendingSnapshot();
        Map<String, List<JobKnowledgeDocument>> incoming = new HashMap<>();

        for (JobKnowledgeDocument source : documents) {
            List<JobKnowledgeDocument> chunks = chunker.chunk(source);

            String id = chunks.get(0).getDocumentId();
            if (incoming.putIfAbsent(id, chunks) != null){
                throw new IllegalArgumentException("重复 documentId: " + id);
            }
        }

        for (List<JobKnowledgeDocument> chunks : incoming.values()){
            upsert(chunks, previous, pending, index);
        }

        for (Map.Entry<String, List<JobKnowledgeDocument>> entry : previous.entrySet()) {
            if (!incoming.containsKey(entry.getKey())) {
                delete(entry.getKey(), entry.getValue(), pending.getOrDefault(entry.getKey(), List.of()), index);
            }
        }
        for (Map.Entry<String, List<String>> entry : pending.entrySet()) {
            if (!incoming.containsKey(entry.getKey()) && !previous.containsKey(entry.getKey())) {
                delete(entry.getKey(), List.of(), entry.getValue(), index);
            }
        }
    }

    private void upsert(List<JobKnowledgeDocument> chunks, Map<String, List<JobKnowledgeDocument>> previous,
                        Map<String, List<String>> pending, LuceneKeywordIndex index) {
        String id = chunks.get(0).getDocumentId();
        List<JobKnowledgeDocument> old = previous.getOrDefault(id, List.of());
        List<String> pendingIds = pending.getOrDefault(id, List.of());
        Map<String, JobKnowledgeDocument> oldById = new HashMap<>();
        old.forEach(chunk -> oldById.put(chunk.getChunkId(), chunk));

        if (pendingIds.isEmpty()
                && !old.isEmpty()
                && old.size() == chunks.size()
                && chunks.stream().allMatch(chunk -> {
            JobKnowledgeDocument prior = oldById.get(chunk.getChunkId());
            return prior != null && Objects.equals(prior.getContentHash(), chunk.getContentHash())
                    && Objects.equals(prior.getContent(), chunk.getContent())
                    && Objects.equals(prior.getSource(), safe(chunk.getSource()))
                    && Objects.equals(prior.getTag(), safe(chunk.getTag()))
                    && Objects.equals(prior.getTitle(), safe(chunk.getTitle()));
        })) {
            log.debug("RAG unchanged document skipped, documentId={}", id);
            return;
        }

        // 先提交本次目标 ID，再清理旧版与未完成写入的 ID；重跑时仍可找回全部目标。
        List<String> targetIds = chunks.stream()
                .map(JobKnowledgeDocument::getChunkId)
                .toList();

        index.markPending(id, targetIds);

        Set<String> cleanup = new LinkedHashSet<>(old.stream()
                .map(JobKnowledgeDocument::getChunkId)
                .toList());
        cleanup.addAll(pendingIds);
        cleanup.addAll(targetIds);

        deleteExistingVectorIds(cleanup);
        if (old.isEmpty() && chunks.get(0).getSource() != null && chunks.get(0).getTag() != null
                && !chunks.get(0).getSource().isBlank() && !chunks.get(0).getTag().isBlank()) {
            // 清理本项目前一版以 source::tag 派生 UUID 写入的整篇向量。
            String oldId = java.util.UUID.nameUUIDFromBytes((chunks.get(0).getSource().trim()
                    + "::" + chunks.get(0).getTag().trim()).getBytes(StandardCharsets.UTF_8)).toString();
            vectorStore.delete(List.of(oldId));
        }

        List<Document> vectors = new ArrayList<>();
        for (JobKnowledgeDocument chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", chunk.getDocumentId());
            metadata.put("chunkId", chunk.getChunkId());
            metadata.put("chunkIndex", chunk.getChunkIndex());
            metadata.put("contentHash", chunk.getContentHash());
            metadata.put("source", chunk.getSource() == null ? "" : chunk.getSource());
            metadata.put("tag", chunk.getTag() == null ? "" : chunk.getTag());
            metadata.put("title", chunk.getTitle() == null ? "" : chunk.getTitle());
            vectors.add(new Document(chunk.getChunkId(), chunk.getContent(), metadata));
        }
        vectorStore.add(vectors);

        index.replace(id, chunks);
        log.info("RAG indexed documentId={}, chunks={}", id, chunks.size());
    }

    private void delete(String id, List<JobKnowledgeDocument> old, List<String> pendingIds,
                        LuceneKeywordIndex index) {
        Set<String> cleanup = new LinkedHashSet<>(old.stream().map(JobKnowledgeDocument::getChunkId).toList());
        cleanup.addAll(pendingIds);
        deleteExistingVectorIds(cleanup);
        index.replace(id, List.of());
        log.info("RAG removed documentId={}", id);
    }

    @Override
    public List<JobKnowledgeDocument> search(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) return List.of();
        List<Document> found = vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build());
        if (found == null) return List.of();
        return found.stream().map(vector -> {
            JobKnowledgeDocument result = new JobKnowledgeDocument();
            result.setChunkId(vector.getId());
            result.setContent(vector.getText());
            result.setDocumentId(value(vector, "documentId"));
            Object ordinal = vector.getMetadata().get("chunkIndex");
            if (ordinal instanceof Number number) result.setChunkIndex(number.intValue());
            result.setSource(value(vector, "source"));
            result.setTag(value(vector, "tag"));
            result.setTitle(value(vector, "title"));
            return result;
        }).toList();
    }

    /** 老版 Lucene 索引可能存着 documentId:序号；PgVector 的主键只接受 UUID。 */
    private void deleteExistingVectorIds(Set<String> ids) {
        List<String> vectorIds = ids.stream().filter(this::isUuid).toList();
        if (!vectorIds.isEmpty()) {
            vectorStore.delete(vectorIds);
        }
    }

    private boolean isUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String value(Document vector, String key) {
        Object value = vector.getMetadata().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(String value) { return value == null ? "" : value; }

    private LuceneKeywordIndex index() {
        LuceneKeywordIndex value = keywordIndex.getIfAvailable();
        if (value == null) throw new IllegalStateException("Lucene index is not configured");
        return value;
    }
}
