package com.velocira.backend.generation.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.velocira.backend.generation.event.GenerationJobRetryEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Schedules a persisted retry at its durable due time without blocking a worker thread. */
@Component
public class GenerationRetryScheduler {

    private final ScheduledExecutorService retryExecutor;
    private final ApplicationEventPublisher eventPublisher;

    public GenerationRetryScheduler(
            @Qualifier("generationRetryExecutor") ScheduledExecutorService retryExecutor,
            ApplicationEventPublisher eventPublisher) {
        this.retryExecutor = retryExecutor;
        this.eventPublisher = eventPublisher;
    }

    public void schedule(UUID jobId, Instant runAt) {
        if (runAt == null) {
            return;
        }
        long delayMillis = Math.max(0, Duration.between(Instant.now(), runAt).toMillis());
        retryExecutor.schedule(() -> eventPublisher.publishEvent(new GenerationJobRetryEvent(jobId)), delayMillis, TimeUnit.MILLISECONDS);
    }
}
