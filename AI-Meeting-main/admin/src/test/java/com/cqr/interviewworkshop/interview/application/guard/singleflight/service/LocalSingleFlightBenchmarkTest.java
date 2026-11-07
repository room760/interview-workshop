package com.cqr.interviewworkshop.interview.application.guard.singleflight.service;

import com.cqr.interviewworkshop.interview.config.InterviewAiSingleFlightConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production local single-flight with a counted, fixed-delay mock model task. */
class LocalSingleFlightBenchmarkTest {
    private static final int MOCK_DELAY_MS = 120;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compareDirectAndLocalDeduplicatedConcurrency() throws Exception {
        List<String> raw = new ArrayList<>();
        for (int concurrency : List.of(1, 5, 10, 20, 50)) {
            for (int run = 1; run <= 3; run++) {
                Map<String, Object> direct = measure(concurrency, run, false);
                Map<String, Object> dedup = measure(concurrency, run, true);
                int baselineCalls = (int) direct.get("modelExecutions");
                int actualCalls = (int) dedup.get("modelExecutions");
                dedup.put("duplicateReduction", (double) (baselineCalls - actualCalls) / baselineCalls);
                raw.add(mapper.writeValueAsString(direct));
                raw.add(mapper.writeValueAsString(dedup));
                assertEquals(concurrency, baselineCalls);
                assertEquals(1, actualCalls);
                assertEquals(concurrency, dedup.get("successes"));
                assertEquals(0, dedup.get("failures"));
            }
        }
        Path output = findBenchmarkDirectory().resolve("raw/local-singleflight.jsonl");
        Files.createDirectories(output.getParent());
        Files.writeString(output, String.join("\n", raw) + "\n", StandardCharsets.UTF_8);
    }

    private Map<String, Object> measure(int concurrency, int run, boolean deduplicated) throws Exception {
        InterviewAiSingleFlightConfiguration config = new InterviewAiSingleFlightConfiguration();
        config.setEnable(true);
        config.setTtlMillis(5000L);
        config.setWaitTimeoutMillis(5000L);
        config.setCleanupThreshold(256);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InterviewAiSingleFlightService service = new InterviewAiSingleFlightService(config, registry);
        AtomicInteger modelExecutions = new AtomicInteger();
        Supplier<String> model = () -> {
            modelExecutions.incrementAndGet();
            try {
                Thread.sleep(MOCK_DELAY_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return "mock-answer";
        };
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Sample>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                Callable<Sample> task = () -> {
                    ready.countDown();
                    start.await();
                    long began = System.nanoTime();
                    try {
                        String result = deduplicated ? service.execute("same-key", model) : model.get();
                        return new Sample(result.equals("mock-answer"), System.nanoTime() - began);
                    } catch (RuntimeException ex) {
                        return new Sample(false, System.nanoTime() - began);
                    }
                };
                futures.add(executor.submit(task));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Long> times = new ArrayList<>();
            int successes = 0;
            for (Future<Sample> future : futures) {
                Sample sample = future.get(10, TimeUnit.SECONDS);
                if (sample.success()) successes++;
                times.add(sample.elapsedNanos());
            }
            times.sort(Comparator.naturalOrder());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mode", deduplicated ? "LOCAL_SINGLE_FLIGHT" : "DIRECT_MOCK");
            row.put("concurrency", concurrency);
            row.put("run", run);
            row.put("requests", concurrency);
            row.put("mockDelayMs", MOCK_DELAY_MS);
            row.put("modelExecutions", modelExecutions.get());
            row.put("owners", deduplicated ? count(registry, "ai_singleflight_miss_total") : null);
            row.put("followersOrCacheHits", deduplicated ? count(registry, "ai_singleflight_hit_total") : null);
            row.put("successfulReuses", deduplicated ? successes - modelExecutions.get() : null);
            row.put("successes", successes);
            row.put("failures", concurrency - successes);
            row.put("p50Ms", percentile(times, 0.50));
            row.put("p95Ms", percentile(times, 0.95));
            return row;
        } finally {
            executor.shutdownNow();
            registry.close();
        }
    }

    private int count(SimpleMeterRegistry registry, String name) {
        Counter counter = registry.find(name).counter();
        return counter == null ? 0 : (int) counter.count();
    }

    private double percentile(List<Long> sorted, double fraction) {
        return sorted.get((int) Math.ceil(sorted.size() * fraction) - 1) / 1_000_000.0;
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

    private record Sample(boolean success, long elapsedNanos) { }
}
