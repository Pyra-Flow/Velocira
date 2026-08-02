package com.velocira.backend.generation.service;

import com.velocira.backend.generation.config.GenerationProperties;
import com.velocira.backend.generation.exceptions.GenerationGuardrailException;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.generation.repository.GenerationJobRepository;
import com.velocira.backend.generation.repository.GenerationRunRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces inexpensive, server-side admission controls before a provider call
 * can be queued. Browser code cannot bypass these limits.
 *
 * <p>The request bucket is intentionally process-local for this MVP. A
 * multi-instance production deployment must replace it with a shared limiter
 * before relying on it as an account-wide enforcement boundary.</p>
 */
@Service
@RequiredArgsConstructor
public class GenerationGuardService {

    private static final EnumSet<GenerationJobStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationJobStatus.QUEUED,
            GenerationJobStatus.RETRIEVING,
            GenerationJobStatus.DRAFTING,
            GenerationJobStatus.VALIDATING);

    private final GenerationJobRepository generationJobRepository;
    private final GenerationRunRepository generationRunRepository;
    private final GenerationProperties properties;
    private final Map<UUID, Bucket> requestBuckets = new ConcurrentHashMap<>();

    /** Throws before persistence or a provider call when a guardrail is exceeded. */
    public void ensureCanQueue(UUID ownerId) {
        if (generationJobRepository.countByOwnerIdAndStatusIn(ownerId, ACTIVE_STATUSES)
                >= properties.getGuard().getMaxActiveJobsPerUser()) {
            throw new GenerationGuardrailException(
                    "You already have the maximum number of active generation jobs. Wait for one to finish or cancel it.");
        }

        BigDecimal incurredUsd = generationRunRepository.sumCostUsdByOwnerSince(
                ownerId, Instant.now().minus(Duration.ofDays(1)));
        BigDecimal incurredCents = (incurredUsd == null ? BigDecimal.ZERO : incurredUsd).movePointRight(2);
        BigDecimal projectedCents = incurredCents.add(BigDecimal.valueOf(properties.getGuard().getEstimatedCostCentsPerJob()));
        if (projectedCents.compareTo(BigDecimal.valueOf(properties.getGuard().getMaxEstimatedCostCentsPerDay())) > 0) {
            throw new GenerationGuardrailException(
                    "The daily generation budget for this account has been reached. Please try again later.");
        }

        Bucket bucket = requestBuckets.computeIfAbsent(ownerId, ignored -> createBucket());
        if (!bucket.tryConsume(1)) {
            throw new GenerationGuardrailException(
                    "Generation requests are limited for this account. Please try again later.");
        }
    }

    @SuppressWarnings("deprecation")
    private Bucket createBucket() {
        int capacity = Math.max(1, properties.getGuard().getRequestsPerHour());
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
