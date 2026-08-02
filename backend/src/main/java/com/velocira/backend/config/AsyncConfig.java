package com.velocira.backend.config;

import com.velocira.backend.generation.config.GenerationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Async and scheduling configuration.
 *
 * <p>
 * Enables:
 * </p>
 * <ul>
 * <li>{@code @Async} — for non-blocking email sending</li>
 * <li>{@code @Scheduled} — for token/OTP cleanup jobs</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /** Bounded executor so a burst of requests cannot exhaust app threads. */
    @Bean(name = "generationTaskExecutor")
    public TaskExecutor generationTaskExecutor(GenerationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorker().getCorePoolSize());
        executor.setMaxPoolSize(properties.getWorker().getMaxPoolSize());
        executor.setQueueCapacity(properties.getWorker().getQueueCapacity());
        executor.setThreadNamePrefix("generation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    /** Schedules bounded retry backoff without sleeping a worker thread. */
    @Bean(name = "generationRetryExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService generationRetryExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "generation-retry-");
            thread.setDaemon(true);
            return thread;
        });
    }
}
