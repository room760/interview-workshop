package com.cqr.interviewworkshop.interview.application.guard.singleflight.cache;

import cn.hutool.core.util.StrUtil;
import com.cqr.interviewworkshop.interview.config.InterviewAiSingleFlightConfiguration;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 结果的本地 L1 回放缓存，用于在短时间内直接复用已成功返回的结果，
 * 减少 follower 或重复请求再次访问 Redis 的开销。
 *
 */
@Component
public class FlightReplayLocalCache {

    private final Map<String, CacheEntry> cache;
    private volatile int maxSize;

    public FlightReplayLocalCache(InterviewAiSingleFlightConfiguration configuration) {
        Integer configuredMaxSize = configuration.getL1CacheMaxSize();
        this.maxSize = configuredMaxSize != null && configuredMaxSize > 0
                ? configuredMaxSize
                : 1000;

        this.cache = new LinkedHashMap<>(128, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > FlightReplayLocalCache.this.maxSize;
            }
        };
    }

    public synchronized String get(String stage, String requestKey) {
        String cacheKey = cacheKey(stage, requestKey);
        CacheEntry entry = cache.get(cacheKey);
        if (entry == null) {
            return null;
        }
        if (entry.expireAtMillis <= System.currentTimeMillis()) {
            cache.remove(cacheKey);
            return null;
        }
        return entry.value;
    }

    public synchronized void put(String stage, String requestKey, String value,
                                 InterviewAiSingleFlightConfiguration.StageFlightPolicy policy) {
        if (StrUtil.isBlank(stage) || StrUtil.isBlank(requestKey) || value == null || policy == null
                || !Boolean.TRUE.equals(policy.getL1CacheEnabled())) {
            return;
        }
        long ttlMillis = policy.getL1CacheTtlMillis() == null || policy.getL1CacheTtlMillis() <= 0
                ? 30000L
                : policy.getL1CacheTtlMillis();
        cache.put(cacheKey(stage, requestKey), new CacheEntry(value, System.currentTimeMillis() + ttlMillis));
    }

    private String cacheKey(String stage, String requestKey) {
        return StrUtil.blankToDefault(stage, "-") + "|" + StrUtil.blankToDefault(requestKey, "-");
    }

    /**
     * 本地缓存条目，保存可回放内容及其失效时间。
     *
     */
    private record CacheEntry(String value, long expireAtMillis) {
    }
}
