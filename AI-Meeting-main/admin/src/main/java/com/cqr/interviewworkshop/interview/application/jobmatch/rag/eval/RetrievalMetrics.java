package com.cqr.interviewworkshop.interview.application.jobmatch.rag.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 二元相关性标注，按文档 ID 评价 Chunk 结果；同一文档的多个 Chunk 只计一次。 */
public final class RetrievalMetrics {
    private RetrievalMetrics() { }

    public static Summary calculate(List<Observation> observations, int k) {
        if (observations.isEmpty() || k < 1) throw new IllegalArgumentException("observations and k required");
        double recall = 0, mrr = 0, ndcg = 0;
        List<Long> latencies = new ArrayList<>();
        for (Observation observation : observations) {
            Set<String> relevant = observation.relevantDocumentIds();
            Set<String> seen = new HashSet<>();
            int hits = 0;
            double dcg = 0;
            List<String> results = observation.rankedDocumentIds();
            for (int i = 0; i < Math.min(k, results.size()); i++) {
                String id = results.get(i);
                if (!seen.add(id)) continue;
                if (relevant.contains(id)) {
                    hits++;
                    dcg += 1.0 / log2(i + 2);
                }
            }
            int first = -1;
            for (int i = 0; i < Math.min(k, results.size()); i++) {
                if (relevant.contains(results.get(i))) { first = i; break; }
            }
            recall += relevant.isEmpty() ? 0 : (double) hits / relevant.size();
            mrr += first < 0 ? 0 : 1.0 / (first + 1);
            double ideal = 0;
            for (int i = 0; i < Math.min(k, relevant.size()); i++) ideal += 1.0 / log2(i + 2);
            ndcg += ideal == 0 ? 0 : dcg / ideal;
            latencies.add(observation.elapsedNanos());
        }
        latencies.sort(Comparator.naturalOrder());
        double average = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        int n = observations.size();
        return new Summary(recall / n, mrr / n, ndcg / n, average,
                percentile(latencies, 0.50), percentile(latencies, 0.95));
    }

    private static double percentile(List<Long> values, double p) {
        int index = Math.max(0, (int) Math.ceil(p * values.size()) - 1);
        return values.get(index) / 1_000_000.0;
    }

    private static double log2(int value) { return Math.log(value) / Math.log(2); }

    public record Observation(Set<String> relevantDocumentIds, List<String> rankedDocumentIds,
                              long elapsedNanos) { }
    public record Summary(double recall, double mrr, double ndcg, double averageMs,
                          double p50Ms, double p95Ms) { }
}
