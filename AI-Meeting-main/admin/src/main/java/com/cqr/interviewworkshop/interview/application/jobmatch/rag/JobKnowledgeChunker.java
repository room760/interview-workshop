package com.cqr.interviewworkshop.interview.application.jobmatch.rag;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class JobKnowledgeChunker {
    private final RagProperties properties;

    public JobKnowledgeChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<JobKnowledgeDocument> chunk(JobKnowledgeDocument source) {
        if (source == null || source.getContent() == null || source.getContent().isBlank()) {
            throw new IllegalArgumentException("岗位知识内容不能为空");
        }

        int size = properties.getChunkSize();
        int overlap = properties.getChunkOverlap();
        if (size < 1 || overlap < 0 || overlap >= size) {
            throw new IllegalArgumentException("chunkSize 必须为正数且 overlap 小于 chunkSize");
        }

        String content = source.getContent().trim().replace("\r\n", "\n");
        String identity = source.getDocumentId();
        if (identity == null || identity.isBlank()) {
            String sourceKey = source.getSource() == null ? "" : source.getSource().trim();
            String tagKey = source.getTag() == null ? "" : source.getTag().trim();
            if (sourceKey.isBlank() && tagKey.isBlank()) {
                throw new IllegalArgumentException("增量索引需要 documentId 或 source/tag");
            }
            identity = "job-" + sha256(sourceKey + "\n" + tagKey).substring(0, 32);
        }

        String hash = sha256(content);
        List<String> parts = split(content, size, overlap);

        List<JobKnowledgeDocument> chunks = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            JobKnowledgeDocument chunk = new JobKnowledgeDocument();
            chunk.setDocumentId(identity);
            chunk.setChunkId(UUID.nameUUIDFromBytes(("job-knowledge-chunk:" + identity + ":" + i)
                    .getBytes(StandardCharsets.UTF_8)).toString());
            chunk.setChunkIndex(i);
            chunk.setContentHash(hash);
            chunk.setTitle(source.getTitle());
            chunk.setSource(source.getSource());
            chunk.setTag(source.getTag());
            chunk.setContent(parts.get(i));
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<String> split(String text, int size, int overlap) {
        if (text.length() <= size) return List.of(text);

        List<String> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            if (end < text.length()) {
                int boundary = text.lastIndexOf("\n\n", end - 1);
                if (boundary < start + size / 2) boundary = text.lastIndexOf('\n', end - 1);
                if (boundary < start + size / 2) boundary = Math.max(text.lastIndexOf('。', end - 1), text.lastIndexOf('；', end - 1));
                if (boundary >= start + size / 2 && boundary > start + overlap) end = boundary + 1;
            }
            result.add(text.substring(start, end).trim());

            if (end == text.length()) break;
            start = end - overlap;
        }
        return result;
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
