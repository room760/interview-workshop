package com.cqr.interviewworkshop.interview.application.jobmatch.rag.retriever;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeoutException;

/** 轻量规则打分，非 Cross-Encoder 或模型 Rerank。 */
@Component
public class RuleReranker implements Reranker {
    private final SmartChineseAnalyzer analyzer = new SmartChineseAnalyzer();

    @Override
    public List<JobKnowledgeDocument> rerank(String query, List<JobKnowledgeDocument> candidates, long timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("rerank timeout must be positive");

        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        List<String> terms = terms(query, deadline);
        List<Ranked> ranked = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            if (System.nanoTime() > deadline) throw new IllegalStateException(new TimeoutException("rerank timed out"));

            JobKnowledgeDocument doc = candidates.get(i);
            String content = safe(doc.getContent());
            String labels = safe(doc.getTitle()) + " " + safe(doc.getTag());
            double score = 0;

            for (String term : terms) {
                if (content.contains(term)) score += 1;
                if (labels.contains(term)) score += 2;
            }
            ranked.add(new Ranked(doc, score, i));
        }
        return ranked.stream().sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparingInt(Ranked::originalRank))
                .map(Ranked::document).toList();
    }

    private String safe(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }

    private List<String> terms(String query, long deadline) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try (TokenStream tokens = analyzer.tokenStream("content", safe(query))) {
            CharTermAttribute term = tokens.addAttribute(CharTermAttribute.class);
            tokens.reset();
            while (tokens.incrementToken() && result.size() < 100) {
                if (System.nanoTime() > deadline) throw new IllegalStateException(new TimeoutException("rerank timed out"));
                String value = term.toString().toLowerCase(Locale.ROOT);
                if (!value.isBlank()) result.add(value);
            }
            tokens.end();
            return new ArrayList<>(result);
        } catch (IOException ex) {
            throw new IllegalStateException("中文查询分词失败", ex);
        }
    }

    @PreDestroy
    public void close() { analyzer.close(); }

    private record Ranked(JobKnowledgeDocument document, double score, int originalRank) { }
}
