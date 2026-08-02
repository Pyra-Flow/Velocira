package com.velocira.backend.generation.service;

import com.velocira.backend.generation.client.AiFailureCode;
import com.velocira.backend.generation.client.AiGenerationClient;
import com.velocira.backend.generation.client.AiGenerationException;
import com.velocira.backend.generation.client.AiGenerationRequest;
import com.velocira.backend.generation.client.AiGenerationResponse;
import com.velocira.backend.generation.observability.GenerationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Executes one already-persisted generation job outside the HTTP request thread. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationJobWorker {

    private final GenerationExecutionService executionService;
    private final AiGenerationClient aiGenerationClient;
    private final GenerationMetrics metrics;
    private final GenerationRetryScheduler generationRetryScheduler;

    /**
     * The state-changing methods live in a separate transactional bean, so
     * every handoff to FastAPI happens after the previous durable state commits.
     */
    @Async("generationTaskExecutor")
    public void process(UUID jobId) {
        if (!executionService.claimQueuedJob(jobId)) {
            return;
        }

        Optional<GenerationExecutionService.AttemptContext> maybeAttempt = executionService.beginDrafting(jobId);
        if (maybeAttempt.isEmpty()) {
            return;
        }
        GenerationExecutionService.AttemptContext attempt = maybeAttempt.get();
        String previousCorrelationId = MDC.get("correlationId");
        if (attempt.correlationId() != null && !attempt.correlationId().isBlank()) {
            MDC.put("correlationId", attempt.correlationId());
        }
        try {
            AiGenerationResponse response = aiGenerationClient.generate(new AiGenerationRequest(
                    attempt.jobId(),
                    new AiGenerationRequest.ProjectContext(
                            attempt.projectId(),
                            attempt.projectName(),
                            attempt.projectDescription(),
                            attempt.projectType()),
                    attempt.artifactType(),
                    new AiGenerationRequest.PromptContext(
                            attempt.promptKey(),
                            attempt.promptVersion(),
                            attempt.promptContent()),
                    attempt.providerIdempotencyKey()));

            if (!executionService.beginValidating(attempt.jobId(), attempt.runId())) {
                return;
            }
            if (!hasUsableArtifact(response)) {
                GenerationExecutionService.FailureOutcome outcome = executionService.handleFailure(
                        attempt.jobId(), attempt.runId(), AiFailureCode.INVALID_OUTPUT, false);
                if (outcome.terminal()) {
                    metrics.failed(outcome.errorCode().name());
                }
                return;
            }
            if (response.validation() == null || !response.validation().valid()) {
                boolean needsInput = executionService.markNeedsInput(
                        attempt.jobId(),
                        attempt.runId(),
                        response,
                        "The generated response needs additional project information before it can be published.");
                if (needsInput) {
                    metrics.failed("VALIDATION_FAILED");
                }
                return;
            }

            GenerationExecutionService.CompletionOutcome outcome = executionService
                    .completeSuccess(attempt.jobId(), attempt.runId(), response);
            if (outcome.published()) {
                metrics.completed(outcome.latencyMs(), safeDouble(outcome.costCents()));
            }
        } catch (AiGenerationException ex) {
            GenerationExecutionService.FailureOutcome outcome = executionService.handleFailure(
                    attempt.jobId(), attempt.runId(), ex.getCode(), ex.isRetryable());
            if (outcome.terminal()) {
                metrics.failed(outcome.errorCode().name());
            } else if (outcome.retryAt() != null) {
                generationRetryScheduler.schedule(attempt.jobId(), outcome.retryAt());
            }
            log.warn("Generation job [{}] provider failure [{}], terminal={}", jobId, ex.getCode(), outcome.terminal());
        } catch (RuntimeException ex) {
            GenerationExecutionService.FailureOutcome outcome = executionService.handleFailure(
                    attempt.jobId(), attempt.runId(), AiFailureCode.INTERNAL_ERROR, false);
            if (outcome.terminal()) {
                metrics.failed(outcome.errorCode().name());
            }
            log.error("Generation job [{}] failed in the worker", jobId, ex);
        } finally {
            if (previousCorrelationId == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previousCorrelationId);
            }
        }
    }

    private boolean hasUsableArtifact(AiGenerationResponse response) {
        return response != null
                && response.success()
                && response.artifact() != null
                && response.artifact().title() != null
                && !response.artifact().title().isBlank()
                && response.artifact().content() != null
                && !response.artifact().content().isBlank()
                && response.artifact().sections() != null
                && !response.artifact().sections().isEmpty();
    }

    private double safeDouble(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }
}
