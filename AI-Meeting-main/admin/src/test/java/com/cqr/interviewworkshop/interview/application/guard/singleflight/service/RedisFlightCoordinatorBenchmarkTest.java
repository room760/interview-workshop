package com.cqr.interviewworkshop.interview.application.guard.singleflight.service;

import com.cqr.interviewworkshop.interview.application.guard.singleflight.cache.FlightResultSerializer;
import com.cqr.interviewworkshop.interview.application.guard.singleflight.coordinator.FlightCoordinatorRepository;
import com.cqr.interviewworkshop.interview.application.guard.singleflight.model.FlightAcquireResult;
import com.cqr.interviewworkshop.interview.application.guard.singleflight.model.FlightAction;
import com.cqr.interviewworkshop.interview.application.guard.singleflight.model.FlightStatus;
import com.cqr.interviewworkshop.interview.config.InterviewAiSingleFlightConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real Redis 6.2 Lua/CAS evidence; only the model task and logical nodes are simulated. */
class RedisFlightCoordinatorBenchmarkTest {
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static FlightCoordinatorRepository repository;
    private static final FlightResultSerializer SERIALIZER = new FlightResultSerializer();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void connect() {
        assumeTrue(Boolean.getBoolean("benchmark.redis"), "Set -Dbenchmark.redis=true for isolated test Redis");
        int port = Integer.getInteger("benchmark.redis.port", 16379);
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        assertNotNull(redis.getConnectionFactory().getConnection().ping());
        repository = new FlightCoordinatorRepository(redis);
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void concurrentLuaAcquisitionAndResultReplay() throws Exception {
        List<String> raw = new ArrayList<>();
        for (int concurrency : List.of(1, 5, 10, 20, 50)) {
            for (int run = 1; run <= 3; run++) {
                Map<String, Object> result = measure(concurrency, run);
                raw.add(mapper.writeValueAsString(result));
                assertEquals(1, result.get("owners"));
                assertEquals(concurrency - 1, result.get("followers"));
                assertEquals(1, result.get("mockModelExecutions"));
                assertEquals(concurrency, result.get("successes"));
            }
        }
        Path output = findBenchmarkDirectory().resolve("raw/redis-coordinator.jsonl");
        Files.createDirectories(output.getParent());
        Files.writeString(output, String.join("\n", raw) + "\n", StandardCharsets.UTF_8);
    }

    @Test
    void missedHeartbeatTakeoverRejectsStaleOwnerWrites() throws Exception {
        String key = "benchmark:takeover:" + UUID.randomUUID();
        InterviewAiSingleFlightConfiguration.StageFlightPolicy policy = policy();
        policy.setTakeoverDetectMillis(50L);
        try {
            FlightAcquireResult first = repository.acquireOrJoin("benchmark", key, "node-a", "session", policy);
            assertEquals(FlightAction.OWNER_NEW, first.getAction());
            assertTrue(repository.markRunning(key, "node-a", first.getOwnerToken(), 5000L));
            assertTrue(repository.heartbeat(key, "node-a", first.getOwnerToken(), 5000L));
            Thread.sleep(100L); // Simulate a missed heartbeat, not a real provider timeout.
            FlightAcquireResult second = repository.acquireOrJoin("benchmark", key, "node-b", "session", policy);
            assertEquals(FlightAction.OWNER_TAKEOVER, second.getAction());
            assertTrue(second.getOwnerToken() > first.getOwnerToken());
            assertFalse(repository.heartbeat(key, "node-a", first.getOwnerToken(), 5000L));
            assertFalse(repository.storeResult(key, "node-a", first.getOwnerToken(),
                    SERIALIZER.serialize("stale", first.getOwnerToken(), policy), 5000L));
            assertFalse(repository.finishSuccess(key, "node-a", first.getOwnerToken(), 5000L));
            assertTrue(repository.markRunning(key, "node-b", second.getOwnerToken(), 5000L));
            assertTrue(repository.storeResult(key, "node-b", second.getOwnerToken(),
                    SERIALIZER.serialize("fresh", second.getOwnerToken(), policy), 5000L));
            assertTrue(repository.finishSuccess(key, "node-b", second.getOwnerToken(), 5000L));
            assertEquals("fresh", SERIALIZER.deserialize(repository.getStoredResult(key)));
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("scenario", "missed_heartbeat_takeover_fencing");
            raw.put("oldToken", first.getOwnerToken());
            raw.put("newToken", second.getOwnerToken());
            raw.put("oldOwnerHeartbeatAccepted", false);
            raw.put("oldOwnerWriteAccepted", false);
            raw.put("newOwnerResult", "fresh");
            Files.writeString(findBenchmarkDirectory().resolve("raw/redis-takeover.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(raw) + "\n", StandardCharsets.UTF_8);
        } finally {
            cleanup(key);
        }
    }

    private Map<String, Object> measure(int concurrency, int run) throws Exception {
        String key = "benchmark:concurrent:" + UUID.randomUUID();
        InterviewAiSingleFlightConfiguration.StageFlightPolicy policy = policy();
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(concurrency);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger modelExecutions = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        List<Future<Sample>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                String node = "logical-node-" + i;
                Callable<Sample> task = () -> {
                    ready.countDown();
                    start.await();
                    long began = System.nanoTime();
                    FlightAcquireResult claim;
                    try {
                        claim = repository.acquireOrJoin("benchmark", key, node, "session", policy);
                    } finally {
                        acquired.countDown();
                    }
                    if (claim.getAction() == FlightAction.OWNER_NEW) {
                        try {
                            if (!acquired.await(10, TimeUnit.SECONDS)) return new Sample(claim.getAction(), false, System.nanoTime() - began);
                            if (!repository.markRunning(key, node, claim.getOwnerToken(), 5000L))
                                return new Sample(claim.getAction(), false, System.nanoTime() - began);
                            modelExecutions.incrementAndGet();
                            Thread.sleep(120L);
                            boolean stored = repository.storeResult(key, node, claim.getOwnerToken(),
                                    SERIALIZER.serialize("mock-answer", claim.getOwnerToken(), policy), 5000L);
                            boolean done = repository.finishSuccess(key, node, claim.getOwnerToken(), 5000L);
                            return new Sample(claim.getAction(), stored && done, System.nanoTime() - began);
                        } finally {
                            finished.countDown();
                        }
                    }
                    boolean signaled = finished.await(10, TimeUnit.SECONDS);
                    boolean reused = signaled && repository.getMeta(key).getStatus() == FlightStatus.SUCCEEDED
                            && "mock-answer".equals(SERIALIZER.deserialize(repository.getStoredResult(key)));
                    return new Sample(claim.getAction(), reused, System.nanoTime() - began);
                };
                futures.add(executor.submit(task));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Long> elapsed = new ArrayList<>();
            int owners = 0, followers = 0, successes = 0;
            for (Future<Sample> future : futures) {
                Sample sample = future.get(15, TimeUnit.SECONDS);
                if (sample.action() == FlightAction.OWNER_NEW) owners++;
                if (sample.action() == FlightAction.FOLLOWER_WAIT) followers++;
                if (sample.success()) successes++;
                elapsed.add(sample.elapsedNanos());
            }
            elapsed.sort(Comparator.naturalOrder());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("scenario", "redis_lua_acquire_and_replay_mock_model");
            raw.put("concurrency", concurrency);
            raw.put("run", run);
            raw.put("requests", concurrency);
            raw.put("owners", owners);
            raw.put("followers", followers);
            raw.put("successfulReuses", successes - owners);
            raw.put("mockModelExecutions", modelExecutions.get());
            raw.put("duplicateReduction", (double) (concurrency - modelExecutions.get()) / concurrency);
            raw.put("successes", successes);
            raw.put("failures", concurrency - successes);
            raw.put("p50Ms", percentile(elapsed, 0.50));
            raw.put("p95Ms", percentile(elapsed, 0.95));
            return raw;
        } finally {
            executor.shutdownNow();
            cleanup(key);
        }
    }

    private InterviewAiSingleFlightConfiguration.StageFlightPolicy policy() {
        InterviewAiSingleFlightConfiguration.StageFlightPolicy policy = new InterviewAiSingleFlightConfiguration.StageFlightPolicy();
        policy.setRunningTtlMillis(5000L);
        policy.setTakeoverDetectMillis(5000L);
        policy.setResultTtlMillis(5000L);
        return policy;
    }

    private void cleanup(String key) {
        redis.delete(List.of("ai:flight:meta:" + key, "ai:flight:result:" + key, "ai:flight:stream:" + key));
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

    private record Sample(FlightAction action, boolean success, long elapsedNanos) { }
}
