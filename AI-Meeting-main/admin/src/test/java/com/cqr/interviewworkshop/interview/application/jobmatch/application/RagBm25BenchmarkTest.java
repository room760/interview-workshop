package com.cqr.interviewworkshop.interview.application.jobmatch.application;

import com.cqr.interviewworkshop.interview.application.jobmatch.domain.JobKnowledgeDocument;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.JobKnowledgeChunker;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.LuceneKeywordIndex;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.RagProperties;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.eval.RetrievalMetrics;
import com.cqr.interviewworkshop.interview.application.jobmatch.rag.eval.RagEvaluationRunner;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit offline benchmark: production Lucene/chunker, no model or vector API. */
class RagBm25BenchmarkTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void candidateDatasetCanBeReadByRealEvaluationRunnerCaseType() throws Exception {
        Path dataset = findBenchmarkDirectory().resolve("data/candidate120.json");
        List<RagEvaluationRunner.Case> cases = mapper.readValue(dataset.toFile(), new TypeReference<>() { });
        assertEquals(120, cases.size());
    }

    @Test
    void measureBm25OnFrozenDatasets() throws Exception {
        Path benchmark = findBenchmarkDirectory();
        Path localTemp = benchmark.getParent().getParent().resolve("tmp/benchmark");
        Files.createDirectories(localTemp);
        Path indexDir = Files.createTempDirectory(localTemp, "lucene-");
        RagProperties properties = new RagProperties();
        properties.setIndexPath(indexDir.toString());
        List<JobKnowledgeDocument> documents;
        try (InputStream source = getClass().getResourceAsStream("/rag/job-knowledge.json")) {
            if (source == null) throw new IllegalStateException("Knowledge corpus missing");
            documents = mapper.readValue(source, new TypeReference<>() { });
        }
        assertEquals(8, documents.size());
        JobKnowledgeChunker chunker = new JobKnowledgeChunker(properties);
        List<String> raw = new ArrayList<>();
        List<Map<String, Object>> summaries = new ArrayList<>();
        LuceneKeywordIndex index = new LuceneKeywordIndex(properties);
        try {
            for (JobKnowledgeDocument document : documents) {
                index.replace(document.getDocumentId(), chunker.chunk(document));
            }
            assertEquals(8, index.snapshot().size());
            for (String dataset : List.of("historical24", "existing_additions36", "proposed_queries", "candidate120")) {
                List<Case> cases = mapper.readValue(
                        benchmark.resolve("data/" + dataset + ".json").toFile(), new TypeReference<>() { });
                if (dataset.equals("candidate120")) assertEquals(120, cases.size());
                List<RetrievalMetrics.Observation> positives = new ArrayList<>();
                int negatives = 0, negativeWithResults = 0;
                for (int run = 1; run <= 3; run++) {
                    for (int i = 0; i < cases.size(); i++) {
                        Case item = cases.get(i);
                        long start = System.nanoTime();
                        List<String> ids = index.search(item.query(), properties.getCandidateLimit()).stream()
                                .limit(properties.getTopK()).map(JobKnowledgeDocument::getDocumentId).toList();
                        long elapsed = System.nanoTime() - start;
                        if (item.relevantDocumentIds().isEmpty()) {
                            negatives++;
                            if (!ids.isEmpty()) negativeWithResults++;
                        } else {
                            positives.add(new RetrievalMetrics.Observation(item.relevantDocumentIds(), ids, elapsed));
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("dataset", dataset);
                        row.put("run", run);
                        row.put("case", i + 1);
                        row.put("query", item.query());
                        row.put("relevantDocumentIds", item.relevantDocumentIds());
                        row.put("rankedDocumentIds", ids);
                        row.put("elapsedNanos", elapsed);
                        raw.add(mapper.writeValueAsString(row));
                    }
                }
                RetrievalMetrics.Summary metrics = RetrievalMetrics.calculate(positives, properties.getTopK());
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("mode", "BM25");
                summary.put("dataset", dataset);
                summary.put("queries", cases.size());
                summary.put("positiveObservations", positives.size());
                summary.put("runs", 3);
                summary.put("recallAt5", metrics.recall());
                summary.put("mrrAt5", metrics.mrr());
                summary.put("ndcgAt5", metrics.ndcg());
                summary.put("averageMs", metrics.averageMs());
                summary.put("p50Ms", metrics.p50Ms());
                summary.put("p95Ms", metrics.p95Ms());
                summary.put("negativeQueries", negatives / 3);
                summary.put("negativeFalsePositiveRate", negatives == 0 ? null : (double) negativeWithResults / negatives);
                summaries.add(summary);
            }
        } finally {
            index.close();
        }
        Path output = benchmark.resolve("raw");
        Files.createDirectories(output);
        Files.writeString(output.resolve("bm25-queries.jsonl"), String.join("\n", raw) + "\n", StandardCharsets.UTF_8);
        Files.writeString(output.resolve("bm25-summary.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summaries) + "\n", StandardCharsets.UTF_8);
    }

    private Path findBenchmarkDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("docs/benchmark");
            if (Files.exists(candidate.resolve("data/historical24.json"))) return candidate;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("docs/benchmark/data not found");
    }

    private record Case(String query, Set<String> relevantDocumentIds, String category, String labelStatus) { }
}
