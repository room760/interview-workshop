package com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.JobKnowledgeChunker;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class ReciprocalRankFusion {
    private ReciprocalRankFusion() { }

    public static List<ScoredChunk> fuse(List<JobKnowledgeDocument> vector,
                                          List<JobKnowledgeDocument> keyword, int k) {
        if (k < 1) throw new IllegalArgumentException("RRF k must be positive");
        Map<String, ScoredChunk> scores = new LinkedHashMap<>();
        add(scores, vector, k);
        add(scores, keyword, k);
        return scores.values().stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(chunk -> chunk.document().getChunkId()))
                .toList();
    }

    private static void add(Map<String, ScoredChunk> scores, List<JobKnowledgeDocument> ranked, int k) {
        if (ranked == null) return;
        Set<String> seenInRoute = new HashSet<>();
        for (int i = 0; i < ranked.size(); i++) {
            JobKnowledgeDocument document = ranked.get(i);
            if (document == null || document.getContent() == null) continue;
            String id = document.getChunkId();
            if (id == null || id.isBlank()) id = JobKnowledgeChunker.sha256(document.getContent());
            if (!seenInRoute.add(id)) continue;
            double increment = 1.0 / (k + i + 1);
            ScoredChunk old = scores.get(id);
            if (old == null) {
                document.setChunkId(id);
                scores.put(id, new ScoredChunk(document, increment));
            } else {
                scores.put(id, new ScoredChunk(old.document(), old.score() + increment));
            }
        }
    }

    public record ScoredChunk(JobKnowledgeDocument document, double score) { }
}
