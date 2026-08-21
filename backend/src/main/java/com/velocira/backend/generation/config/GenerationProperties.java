package com.velocira.backend.generation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Runtime limits and internal-service connection settings for generation. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "velocira.generation")
public class GenerationProperties {

    private Ai ai = new Ai();
    private Worker worker = new Worker();
    private Guard guard = new Guard();

    @Getter
    @Setter
    public static class Ai {
        private String baseUrl = "http://localhost:8000";
        private String sharedSecret = "";
        /** Enables the isolated AI planner; the deterministic catalog remains the safe fallback. */
        private boolean discoveryPlannerEnabled = false;
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofMinutes(30);
    }

    @Getter
    @Setter
    public static class Worker {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 50;
        private int maxAttempts = 3;
        private Duration retryBackoff = Duration.ofMillis(250);
        private Duration recoveryDelay = Duration.ofSeconds(30);
        private Duration staleAfter = Duration.ofMinutes(2);
    }

    @Getter
    @Setter
    public static class Guard {
        private int requestsPerHour = 10;
        private int maxActiveJobsPerUser = 2;
        private int estimatedCostCentsPerJob = 1;
        private int maxEstimatedCostCentsPerDay = 100;
    }
}
