package com.velocira.backend.generation.service;

import com.velocira.backend.generation.config.GenerationProperties;
import com.velocira.backend.generation.model.GenerationJobEntity;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.generation.repository.GenerationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Resumes due retries and interrupted jobs without holding work in memory. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationJobRecovery {

    private static final EnumSet<GenerationJobStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationJobStatus.RETRIEVING,
            GenerationJobStatus.DRAFTING,
            GenerationJobStatus.VALIDATING);

    private final GenerationJobRepository generationJobRepository;
    private final GenerationExecutionService generationExecutionService;
    private final GenerationJobWorker generationJobWorker;
    private final GenerationProperties properties;

    @Scheduled(
            fixedDelayString = "${velocira.generation.worker.recovery-delay:30s}",
            initialDelayString = "${velocira.generation.worker.recovery-delay:30s}")
    public void resumeDurableWork() {
        Instant now = Instant.now();
        List<UUID> staleIds = generationJobRepository.findStaleActiveJobs(
                        ACTIVE_STATUSES,
                        now.minus(properties.getWorker().getStaleAfter()),
                        PageRequest.of(0, 25))
                .stream()
                .map(GenerationJobEntity::getId)
                .toList();
        staleIds.forEach(generationExecutionService::requeueStaleJob);
        if (!staleIds.isEmpty()) {
            log.info("Requeued {} stale generation job(s) after worker recovery", staleIds.size());
        }

        List<UUID> dueIds = generationJobRepository.findClaimableJobs(
                        List.of(GenerationJobStatus.QUEUED), now, PageRequest.of(0, 25))
                .stream()
                .map(GenerationJobEntity::getId)
                .toList();
        dueIds.forEach(generationJobWorker::process);
    }
}
