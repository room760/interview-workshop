package com.cqr.interviewworkshop.common.config.thread;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application-level thread-pool configuration.
 * Splits async workloads by task type for predictable isolation.
 */
@Data
@Component
@ConfigurationProperties(prefix = "interview-workshop-agent.thread-pool")
public class ApplicationThreadPoolProperties {

    private Pool general = new Pool(50, 200, 1000, 300, "interview-workshop-async-");

    private Pool aiIo = new Pool(24, 64, 400, 120, "interview-workshop-ai-io-");

    private Pool agent = new Pool(8,32,100,60,"interview-workshop-agent-");

    private Pool cpu = new Pool(defaultCpuCore(), defaultCpuMax(), 256, 60, "interview-workshop-cpu-");

    private Pool query = new Pool(16, 48, 600, 120, "interview-workshop-query-");

    private Integer scheduledPoolSize = 8;

    private String scheduledThreadNamePrefix = "interview-workshop-schedule-";

    private static int defaultCpuCore() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(2, processors);
    }

    private static int defaultCpuMax() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(4, processors * 2);
    }

    @Data
    public static class Pool {
        private Integer corePoolSize;
        private Integer maxPoolSize;
        private Integer queueCapacity;
        private Integer keepAliveSeconds;
        private String threadNamePrefix;

        public Pool() {
        }

        public Pool(
                Integer corePoolSize,
                Integer maxPoolSize,
                Integer queueCapacity,
                Integer keepAliveSeconds,
                String threadNamePrefix) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
            this.keepAliveSeconds = keepAliveSeconds;
            this.threadNamePrefix = threadNamePrefix;
        }
    }
}
