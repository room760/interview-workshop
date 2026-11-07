package com.cqr.interviewworkshop.interview.application.runtime;

import com.alibaba.fastjson2.JSON;
import com.cqr.interviewworkshop.interview.dao.entity.*;
import com.cqr.interviewworkshop.interview.dao.mapper.InterviewRecordMapper;
import com.cqr.interviewworkshop.interview.dao.repository.*;
import com.cqr.interviewworkshop.interview.service.InterviewQuestionService;
import com.cqr.interviewworkshop.interview.service.InterviewSessionService;
import com.cqr.interviewworkshop.interview.service.cache.InterviewCacheKeys;
import com.cqr.interviewworkshop.interview.service.impl.InterviewQuestionCacheServiceImpl;
import com.cqr.interviewworkshop.interview.service.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real Mongo repositories, Redis commands and Redisson lock; only MySQL fallback collaborators are mocked. */
@DataMongoTest(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:27017/interview_workshop_recovery_benchmark",
        "spring.data.mongodb.auto-index-creation=false"
})
@EnabledIfSystemProperty(named = "benchmark.recovery.integration", matches = "true")
@ContextConfiguration(classes = InterviewSessionRuntimeIntegrationTest.MongoSliceConfig.class)
class InterviewSessionRuntimeIntegrationTest {
    @SpringBootConfiguration @EnableAutoConfiguration
    @EnableMongoRepositories(basePackageClasses = InterviewSessionRuntimeHotSnapshotRepository.class)
    static class MongoSliceConfig { }

    private static final Path RESULTS = System.getenv("BENCHMARK_RUN_DIR") == null
            ? Path.of("..", "..", "docs", "benchmark", "raw", "recovery-integration.jsonl")
            : Path.of(System.getenv("BENCHMARK_RUN_DIR"), "recovery-integration.jsonl");
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static RedissonClient redisson;
    private static final List<Map<String, Object>> observations = new ArrayList<>();
    private static final ThreadLocal<Double> lastRestoreMs = new ThreadLocal<>();

    @Autowired private InterviewSessionRuntimeColdSnapshotRepository coldRepo;
    @Autowired private InterviewSessionRuntimeHotSnapshotRepository hotRepo;
    @Autowired private InterviewSessionTurnArchiveRepository archiveRepo;

    @BeforeAll static void connect() throws Exception {
        factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        assertNotNull(redis.getConnectionFactory().getConnection().ping());
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        redisson = Redisson.create(config);
        Files.createDirectories(RESULTS.getParent());
        Files.writeString(RESULTS, "", StandardCharsets.UTF_8);
    }

    @AfterAll static void finish() throws Exception {
        for (Map<String, Object> row : observations)
            Files.writeString(RESULTS, JSON.toJSONString(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        if (redisson != null) redisson.shutdown();
        if (factory != null) factory.destroy();
    }

    private InterviewSessionRuntimeRehydrateService service(StringRedisTemplate template) {
        InterviewQuestionService questions = mock(InterviewQuestionService.class);
        InterviewQuestionCacheServiceImpl cache = new InterviewQuestionCacheServiceImpl(template, questions, null, null);
        InterviewSessionRuntimeSnapshotService snapshots = new InterviewSessionRuntimeSnapshotService(
                hotRepo, coldRepo, archiveRepo, mock(InterviewSessionService.class), questions, cache);
        return new InterviewSessionRuntimeRehydrateService(snapshots,
                new InterviewSessionRuntimeLockService(redisson), mock(InterviewSessionService.class),
                questions, cache, mock(InterviewRecordMapper.class), template);
    }
    private InterviewSessionRuntimeRehydrateService service() { return service(redis); }
    private String id(String name) { return "benchmark-recovery-" + name; }

    private void clear(String id) {
        coldRepo.findBySessionId(id).ifPresent(coldRepo::delete);
        hotRepo.findBySessionId(id).ifPresent(hotRepo::delete);
        archiveRepo.deleteAll(archiveRepo.findBySessionIdOrderBySeqAsc(id));
        redis.delete(List.of(InterviewCacheKeys.questions(id), InterviewCacheKeys.suggestions(id),
                InterviewCacheKeys.resumeContext(id), InterviewCacheKeys.resumeScore(id),
                InterviewCacheKeys.direction(id), InterviewCacheKeys.demeanorScore(id),
                InterviewCacheKeys.demeanorPanic(id), InterviewCacheKeys.demeanorSeriousness(id),
                InterviewCacheKeys.demeanorEmoticon(id), InterviewCacheKeys.demeanorComposite(id),
                InterviewCacheKeys.flow(id), InterviewCacheKeys.followUpQuestions(id),
                InterviewCacheKeys.turns(id), InterviewCacheKeys.answerRequest(id),
                InterviewCacheKeys.turnRequest(id), InterviewCacheKeys.sessionScore(id),
                InterviewCacheKeys.sessionScoreSum(id), InterviewCacheKeys.sessionScoreCount(id)));
    }

    private InterviewTurnLog turn(String id) {
        return InterviewTurnLog.builder().timestamp(1_700_000_000_000L)
                .requestId("request-" + id).questionNumber("1").questionContent("Q1")
                .answerContent("fixed answer").score(80).totalScore(80)
                .nextQuestionNumber("2").nextQuestion("Q2").finished(false).build();
    }
    private void completeSnapshot(String id, boolean recentTurn) {
        InterviewSessionRuntimeColdSnapshot cold = new InterviewSessionRuntimeColdSnapshot();
        cold.setSessionId(id);
        cold.setQuestions(Map.of("1", "Q1", "2", "Q2"));
        cold.setSuggestions(Map.of("1", "S1"));
        cold.setDirection("backend");
        coldRepo.save(cold);
        InterviewFlowState flow = new InterviewFlowState();
        flow.setStatus("ASKING"); flow.setCurrentIndex(1); flow.setCurrentQuestionNumber("2");
        flow.setTotalQuestions(2); flow.setFollowUpCount(0); flow.setMaxFollowUp(2); flow.setVersion(2);
        InterviewRuntimeScoreAggregate score = new InterviewRuntimeScoreAggregate();
        score.setScoreSum(80); score.setScoreCount(1); score.setTotalScore(80);
        InterviewSessionRuntimeHotSnapshot hot = new InterviewSessionRuntimeHotSnapshot();
        hot.setSessionId(id); hot.setFlow(flow); hot.setScoreAggregate(score);
        hot.setRebuildConfidence(InterviewRuntimeConfidence.EXACT);
        hot.setRecentTurns(recentTurn ? List.of(turn(id)) : List.of());
        hot.setArchiveWatermark(recentTurn ? 0L : 1L);
        hotRepo.save(hot);
    }
    private void archiveTurn(String id) {
        InterviewSessionTurnArchive archive = new InterviewSessionTurnArchive();
        archive.setSessionId(id); archive.setSeq(1L); archive.setRequestId("request-" + id);
        archive.setTurnPayload(turn(id)); archiveRepo.save(archive);
    }
    private InterviewSessionRuntimeView restore(InterviewSessionRuntimeRehydrateService target, String id) {
        long start = System.nanoTime();
        try {
            return target.ensureRuntime(id, InterviewRuntimeLoadMode.READ_WRITE_REQUIRED,
                    InterviewRuntimeRehydrateScope.HOT_RUNTIME);
        } finally {
            lastRestoreMs.set((System.nanoTime() - start) / 1_000_000.0);
        }
    }
    private boolean completeInRedis(String id) {
        return Boolean.TRUE.equals(redis.hasKey(InterviewCacheKeys.questions(id)))
                && Boolean.TRUE.equals(redis.hasKey(InterviewCacheKeys.flow(id)))
                && Boolean.TRUE.equals(redis.hasKey(InterviewCacheKeys.turns(id)))
                && Boolean.TRUE.equals(redis.hasKey(InterviewCacheKeys.sessionScore(id)))
                && "2".equals(redis.opsForHash().get(InterviewCacheKeys.flow(id), "currentQuestionNumber"));
    }
    private void observe(String scenario, boolean expectedRecoverable, long start,
                         InterviewSessionRuntimeView view, boolean backfill, int duplicates, String initialState) {
        observations.add(Map.of("scenario", scenario, "expectedRecoverable", expectedRecoverable,
                "latencyMs", lastRestoreMs.get(),
                "confidence", view.getConfidence().name(), "source", view.getRestoreSource().name(),
                "cacheRebuilt", view.isCacheRebuilt(), "canWrite", view.canWrite(),
                "backfillComplete", backfill, "duplicateInitializations", duplicates,
                "initialState", initialState));
    }

    @Test void completeRedisLossRestoresFromMongoSnapshot() {
        String id = id("complete-loss"); clear(id);
        try {
            completeSnapshot(id, true);
            long start = System.nanoTime();
            InterviewSessionRuntimeView view = restore(service(), id);
            boolean backfill = completeInRedis(id);
            observe("complete_redis_loss", true, start, view, backfill, 0, "Mongo hot+cold complete, Redis absent");
            assertEquals(InterviewRuntimeConfidence.EXACT, view.getConfidence());
            assertEquals(InterviewRuntimeRestoreSource.RUNTIME_SNAPSHOT, view.getRestoreSource());
            assertTrue(view.canWrite()); assertTrue(backfill);
        } finally { clear(id); }
    }
    @Test void missingRecentTurnsUsesMongoTurnArchive() {
        String id = id("partial-snapshot"); clear(id);
        try {
            completeSnapshot(id, false); archiveTurn(id);
            long start = System.nanoTime();
            InterviewSessionRuntimeView view = restore(service(), id);
            boolean backfill = completeInRedis(id);
            observe("partial_snapshot_archive", true, start, view, backfill, 0,
                    "hot recentTurns empty, archiveWatermark=1, Mongo TurnArchive present");
            assertEquals(InterviewRuntimeConfidence.EXACT, view.getConfidence());
            assertTrue(backfill); assertEquals(1L, redis.opsForList().size(InterviewCacheKeys.turns(id)));
        } finally { clear(id); }
    }
    @Test void missingRedisFlowRetainsTurnsAndCurrentQuestion() {
        String id = id("flow-loss"); clear(id);
        try {
            completeSnapshot(id, true);
            redis.opsForHash().put(InterviewCacheKeys.questions(id), "1", "Q1");
            redis.opsForList().rightPush(InterviewCacheKeys.turns(id), JSON.toJSONString(turn(id)));
            long start = System.nanoTime();
            InterviewSessionRuntimeView view = restore(service(), id);
            boolean backfill = completeInRedis(id);
            observe("redis_flow_loss", true, start, view, backfill, 0,
                    "Redis questions+turns retained; flow absent; Mongo snapshot complete");
            assertEquals(InterviewRuntimeConfidence.EXACT, view.getConfidence());
            assertEquals("2", redis.opsForHash().get(InterviewCacheKeys.flow(id), "currentQuestionNumber"));
        } finally { clear(id); }
    }
    @Test void missingRedisTurnsUsesMongoArchive() {
        String id = id("turns-loss"); clear(id);
        try {
            completeSnapshot(id, false); archiveTurn(id);
            redis.opsForHash().put(InterviewCacheKeys.questions(id), "1", "Q1");
            redis.opsForHash().put(InterviewCacheKeys.flow(id), "status", "ASKING");
            long start = System.nanoTime();
            InterviewSessionRuntimeView view = restore(service(), id);
            boolean backfill = completeInRedis(id);
            observe("redis_turns_loss", true, start, view, backfill, 0,
                    "Redis questions+flow retained; turns absent; Mongo archive present");
            assertEquals(InterviewRuntimeConfidence.EXACT, view.getConfidence());
            assertEquals(1L, redis.opsForList().size(InterviewCacheKeys.turns(id)));
        } finally { clear(id); }
    }
    @Test void concurrentRestoreUsesOneRealDistributedLock() throws Exception {
        String id = id("concurrent"); clear(id);
        try {
            completeSnapshot(id, true);
            ExecutorService pool = Executors.newFixedThreadPool(10);
            CountDownLatch ready = new CountDownLatch(10), fire = new CountDownLatch(1);
            try {
                List<Future<InterviewSessionRuntimeView>> futures = new ArrayList<>();
                for (int i = 0; i < 10; i++) futures.add(pool.submit(() -> {
                    ready.countDown(); fire.await(); return restore(service(), id);
                }));
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                long start = System.nanoTime(); fire.countDown();
                int initialized = 0;
                InterviewSessionRuntimeView lastView = null;
                for (Future<InterviewSessionRuntimeView> future : futures) {
                    InterviewSessionRuntimeView view = future.get(10, TimeUnit.SECONDS);
                    assertTrue(view.canWrite());
                    if (view.isCacheRebuilt()) initialized++;
                    lastView = view;
                }
                lastRestoreMs.set((System.nanoTime() - start) / 1_000_000.0);
                boolean backfill = completeInRedis(id);
                observe("concurrent_10_threads", true, start, lastView, backfill,
                        Math.max(0, initialized - 1), "10 threads; cacheRebuilt=" + initialized);
                assertEquals(1, initialized); assertTrue(backfill);
            } finally { pool.shutdownNow(); }
        } finally { clear(id); }
    }
    @Test void lockContentionReturnsReadOnlyWithoutBackfill() throws Exception {
        String id = id("lock-contention"); clear(id);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            completeSnapshot(id, true);
            Future<?> held = holder.submit(() -> {
                RLock lock = redisson.getLock("interview:runtime:rehydrate:lock:" + id);
                lock.lock(); locked.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { lock.unlock(); }
            });
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            long start = System.nanoTime();
            InterviewSessionRuntimeView view = restore(service(), id);
            boolean backfill = completeInRedis(id);
            observe("lock_contention", false, start, view, backfill, 0, "another thread holds real Redisson lock");
            assertEquals(InterviewRuntimeConfidence.READ_ONLY, view.getConfidence());
            assertFalse(view.canWrite()); assertFalse(backfill);
            release.countDown(); held.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); holder.shutdownNow(); clear(id); }
    }
    @Test void partialRedisWriteExceptionCanRetry() {
        String id = id("partial-write"); clear(id);
        try {
            completeSnapshot(id, true);
            AtomicBoolean failOnce = new AtomicBoolean(true);
            StringRedisTemplate faulty = new StringRedisTemplate(factory) {
                @Override public Boolean delete(String key) {
                    if (InterviewCacheKeys.flow(id).equals(key) && failOnce.compareAndSet(true, false))
                        throw new IllegalStateException("test-only client failure after questions backfill");
                    return super.delete(key);
                }
            };
            faulty.afterPropertiesSet();
            long firstStart = System.nanoTime();
            InterviewSessionRuntimeView first = restore(service(faulty), id);
            boolean partial = Boolean.TRUE.equals(redis.hasKey(InterviewCacheKeys.questions(id)))
                    && !Boolean.TRUE.equals(redis.hasKey(InterviewCacheKeys.flow(id)));
            observe("partial_write_first", false, firstStart, first, completeInRedis(id), 0,
                    "one-shot Redis client exception before flow write; question key already written");
            assertEquals(InterviewRuntimeConfidence.READ_ONLY, first.getConfidence());
            assertTrue(partial);
            long retryStart = System.nanoTime();
            InterviewSessionRuntimeView retry = restore(service(), id);
            boolean backfill = completeInRedis(id);
            observe("partial_write_retry", true, retryStart, retry, backfill, 0,
                    "normal real Redis client retries incomplete cache using Mongo snapshot");
            assertEquals(InterviewRuntimeConfidence.EXACT, retry.getConfidence()); assertTrue(backfill);
        } finally { clear(id); }
    }
    @Test void missingAllPersistentDataIsReadOnly() {
        String id = id("unrecoverable"); clear(id);
        try {
            long start = System.nanoTime();
            InterviewSessionRuntimeView view = restore(service(), id);
            boolean backfill = completeInRedis(id);
            observe("unrecoverable_no_snapshot", false, start, view, backfill, 0,
                    "no Redis or Mongo; isolated MySQL fallback absent");
            assertEquals(InterviewRuntimeConfidence.READ_ONLY, view.getConfidence());
            assertFalse(view.canWrite()); assertFalse(backfill);
        } finally { clear(id); }
    }
    @Test void terminalSnapshotDoesNotAllowWrites() {
        String id = id("terminal"); clear(id);
        try {
            completeSnapshot(id, true);
            InterviewSessionRuntimeHotSnapshot hot = hotRepo.findBySessionId(id).orElseThrow();
            hot.setRebuildConfidence(InterviewRuntimeConfidence.TERMINAL); hotRepo.save(hot);
            long start = System.nanoTime();
            InterviewSessionRuntimeView view = restore(service(), id);
            boolean backfill = completeInRedis(id);
            observe("terminal_snapshot", false, start, view, backfill, 0,
                    "complete Mongo snapshot with TERMINAL confidence");
            assertEquals(InterviewRuntimeConfidence.TERMINAL, view.getConfidence());
            assertFalse(view.canWrite());
            InterviewSessionRuntimeView cached = restore(service(), id);
            observe("terminal_cached_second_call", false, System.nanoTime(), cached,
                    completeInRedis(id), 0, "same terminal session, second call after cache backfill");
            assertFalse(cached.canWrite(), "a warm Redis cache must not upgrade a TERMINAL session to writable");
        } finally { clear(id); }
    }

    @Test void repeatFourRecoverablePathsForLatencyDistribution() {
        for (int n = 0; n < 100; n++) {
            int kind = n % 4;
            String id = id("repeat-" + String.format("%03d", n));
            clear(id);
            try {
                completeSnapshot(id, kind != 1 && kind != 3);
                if (kind == 1 || kind == 3) archiveTurn(id);
                if (kind == 2) {
                    redis.opsForHash().put(InterviewCacheKeys.questions(id), "1", "Q1");
                    redis.opsForList().rightPush(InterviewCacheKeys.turns(id), JSON.toJSONString(turn(id)));
                }
                if (kind == 3) {
                    redis.opsForHash().put(InterviewCacheKeys.questions(id), "1", "Q1");
                    redis.opsForHash().put(InterviewCacheKeys.flow(id), "status", "ASKING");
                }
                long start = System.nanoTime();
                InterviewSessionRuntimeView view = restore(service(), id);
                boolean backfill = completeInRedis(id);
                observe("repeat_" + kind, true, start, view, backfill, 0,
                        "fixed fixture trial=" + n + "; kind=0 full Redis loss,1 missing recentTurns,2 flow loss,3 turns loss");
                assertEquals(InterviewRuntimeConfidence.EXACT, view.getConfidence());
                assertTrue(backfill);
            } finally { clear(id); }
        }
    }
}
