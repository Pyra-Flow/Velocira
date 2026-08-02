package com.velocira.backend.generation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.document.model.DocumentEntity;
import com.velocira.backend.document.model.DocumentStatus;
import com.velocira.backend.document.repository.DocumentRepository;
import com.velocira.backend.generation.client.AiFailureCode;
import com.velocira.backend.generation.client.AiGenerationResponse;
import com.velocira.backend.generation.config.GenerationProperties;
import com.velocira.backend.generation.model.ArtifactValidationStatus;
import com.velocira.backend.generation.model.ArtifactVersionEntity;
import com.velocira.backend.generation.model.GenerationErrorCode;
import com.velocira.backend.generation.model.GenerationJobEntity;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.generation.model.GenerationRunEntity;
import com.velocira.backend.generation.model.GenerationRunStatus;
import com.velocira.backend.generation.model.PromptTemplateEntity;
import com.velocira.backend.generation.repository.ArtifactVersionRepository;
import com.velocira.backend.generation.repository.GenerationJobRepository;
import com.velocira.backend.generation.repository.GenerationRunRepository;
import com.velocira.backend.generation.repository.PromptTemplateRepository;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional state changes for the asynchronous worker. Each visible phase
 * is committed before the next remote step, which makes recovery after an app
 * restart deterministic and lets the browser read honest, durable state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationExecutionService {

    private static final EnumSet<GenerationJobStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationJobStatus.RETRIEVING,
            GenerationJobStatus.DRAFTING,
            GenerationJobStatus.VALIDATING);

    private final GenerationJobRepository generationJobRepository;
    private final GenerationRunRepository generationRunRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final GenerationProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /** Immutable worker input detached from JPA before the remote provider call. */
    public record AttemptContext(
            UUID jobId,
            UUID runId,
            int attemptNumber,
            UUID projectId,
            String projectName,
            String projectDescription,
            String projectType,
            String artifactType,
            String promptKey,
            String promptVersion,
            String promptContent,
            String correlationId) {

        public String providerIdempotencyKey() {
            return jobId + ":" + attemptNumber;
        }
    }

    public record CompletionOutcome(boolean published, long latencyMs, BigDecimal costCents) {
    }

    public record FailureOutcome(boolean terminal, GenerationErrorCode errorCode, Instant retryAt) {
    }

    /** Claims a due queue item. Only one concurrent worker can progress it. */
    @Transactional
    public boolean claimQueuedJob(UUID jobId) {
        GenerationJobEntity job = generationJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != GenerationJobStatus.QUEUED) {
            return false;
        }
        Instant now = Instant.now();
        if (job.isCancelRequested() || isProjectArchived(job)) {
            markCancelled(job, null, now);
            return false;
        }
        if (job.getNextAttemptAt() != null && job.getNextAttemptAt().isAfter(now)) {
            return false;
        }
        job.setStatus(GenerationJobStatus.RETRIEVING);
        job.setStatusMessage("Collecting the immutable project context.");
        job.setUserMessage(null);
        job.setNextAttemptAt(null);
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        ProjectEntity project = job.getProject();
        if (project.getStatus() != ProjectStatus.ARCHIVED) {
            project.setStatus(ProjectStatus.GENERATING);
            project.setProgress(0);
            projectRepository.save(project);
        }
        generationJobRepository.save(job);
        return true;
    }

    /** Persists or recovers an execution attempt before calling FastAPI. */
    @Transactional
    public Optional<AttemptContext> beginDrafting(UUID jobId) {
        GenerationJobEntity job = generationJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != GenerationJobStatus.RETRIEVING) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (job.isCancelRequested() || isProjectArchived(job)) {
            markCancelled(job, null, now);
            return Optional.empty();
        }

        PromptTemplateEntity template = promptTemplateRepository
                .findByTemplateKeyAndTemplateVersion(job.getPromptTemplateKey(), job.getPromptTemplateVersion())
                .orElse(null);
        if (template == null || !template.getChecksum().equals(GenerationHashing.sha256(template.getContent()))) {
            markNeedsInput(job, null, now, "The stored prompt revision is unavailable or failed its integrity check.");
            return Optional.empty();
        }

        GenerationRunEntity run = reusableRunningAttempt(job).orElseGet(() -> createAttempt(job, template, now));
        if (run == null) {
            return Optional.empty();
        }
        job.setStatus(GenerationJobStatus.DRAFTING);
        job.setStatusMessage("Drafting a structured artifact with the isolated generation service.");
        job.setRetryable(false);
        generationJobRepository.save(job);

        ProjectEntity project = job.getProject();
        String projectDescription = project.getDescription() == null ? "" : project.getDescription();
        String additionalInstructions = job.getInputSnapshot().path("additionalInstructions").asText("");
        if (!additionalInstructions.isBlank()) {
            projectDescription += "\n\nAdditional generation context:\n" + additionalInstructions;
        }
        return Optional.of(new AttemptContext(
                job.getId(),
                run.getId(),
                run.getAttemptNumber(),
                project.getId(),
                project.getName(),
                projectDescription,
                project.getType().name(),
                job.getRequestedDocumentType().name(),
                template.getTemplateKey(),
                template.getTemplateVersion(),
                template.getContent(),
                job.getCorrelationId()));
    }

    /** Records the validation phase immediately before a response is published. */
    @Transactional
    public boolean beginValidating(UUID jobId, UUID runId) {
        GenerationJobEntity job = generationJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != GenerationJobStatus.DRAFTING) {
            return false;
        }
        GenerationRunEntity run = generationRunRepository.findById(runId).orElse(null);
        if (run == null || !run.getGenerationJob().getId().equals(jobId)) {
            return false;
        }
        if (job.isCancelRequested() || isProjectArchived(job)) {
            markCancelled(job, run, Instant.now());
            return false;
        }
        job.setStatus(GenerationJobStatus.VALIDATING);
        job.setStatusMessage("Validating the structured response before publishing it.");
        generationJobRepository.save(job);
        return true;
    }

    /** Stores a model response that is structurally valid and passed validation. */
    @Transactional
    public CompletionOutcome completeSuccess(UUID jobId, UUID runId, AiGenerationResponse response) {
        GenerationJobEntity job = generationJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != GenerationJobStatus.VALIDATING) {
            return new CompletionOutcome(false, 0, BigDecimal.ZERO);
        }
        GenerationRunEntity run = generationRunRepository.findById(runId).orElse(null);
        if (run == null || !run.getGenerationJob().getId().equals(jobId)) {
            return new CompletionOutcome(false, 0, BigDecimal.ZERO);
        }
        Instant now = Instant.now();
        if (job.isCancelRequested() || isProjectArchived(job)) {
            markCancelled(job, run, now);
            return new CompletionOutcome(false, 0, BigDecimal.ZERO);
        }
        if (documentRepository.existsByProjectIdAndType(job.getProject().getId(), job.getRequestedDocumentType())) {
            markNeedsInput(job, run, now,
                    "An artifact of this type was created while this job was running, so this response was not published.");
            return new CompletionOutcome(false, 0, BigDecimal.ZERO);
        }

        applyProviderResponse(run, response);
        run.setStatus(GenerationRunStatus.SUCCEEDED);
        run.setRetryable(false);
        run.setCompletedAt(now);
        generationRunRepository.save(run);

        DocumentEntity document = DocumentEntity.builder()
                .project(job.getProject())
                .type(job.getRequestedDocumentType())
                .status(DocumentStatus.COMPLETED)
                .title(response.artifact().title().trim())
                .content(response.artifact().content())
                .version(1)
                .wordCount(wordCount(response.artifact().content()))
                .aiModel(response.model())
                .generationTimeMs(response.latencyMs())
                .build();
        document = documentRepository.save(document);

        ArtifactVersionEntity artifact = ArtifactVersionEntity.builder()
                .project(job.getProject())
                .document(document)
                .generationJob(job)
                .generationRun(run)
                .artifactType(job.getRequestedDocumentType())
                .versionNumber(1)
                .title(response.artifact().title().trim())
                .content(response.artifact().content())
                .contentSha256(GenerationHashing.sha256(response.artifact().content()))
                .validationStatus(ArtifactValidationStatus.PASSED)
                .sourceInputSnapshot(job.getInputSnapshot().deepCopy())
                .outputMetadata(toOutputMetadata(response))
                .validatorOutcome(toValidatorOutcome(response))
                .provider(response.provider())
                .model(response.model())
                .promptTemplateKey(job.getPromptTemplateKey())
                .promptTemplateVersion(job.getPromptTemplateVersion())
                .promptChecksum(run.getPromptChecksum())
                .generatedAt(now)
                .build();
        artifact = artifactVersionRepository.save(artifact);

        job.setDocument(document);
        job.setArtifactVersion(artifact);
        job.setStatus(GenerationJobStatus.READY);
        job.setCompletedAt(now);
        job.setRetryable(false);
        job.setErrorCode(null);
        job.setStatusMessage("The structured artifact is ready for review.");
        job.setUserMessage(null);
        generationJobRepository.save(job);

        ProjectEntity project = job.getProject();
        project.setStatus(ProjectStatus.NEEDS_REVIEW);
        project.setProgress(0);
        projectRepository.save(project);
        auditService.record(job.getOwner().getId(), job.getOwner().getEmail(), AuditAction.GENERATION_JOB_COMPLETED,
                "Completed " + job.getRequestedDocumentType() + " generation for project: " + project.getName());

        long latency = response.latencyMs() == null ? 0 : response.latencyMs();
        BigDecimal cost = response.usage() == null || response.usage().costCents() == null
                ? BigDecimal.ZERO
                : response.usage().costCents();
        return new CompletionOutcome(true, latency, cost);
    }

    /** Persists a validator result that needs human/project input instead of publishing content. */
    @Transactional
    public boolean markNeedsInput(UUID jobId, UUID runId, AiGenerationResponse response, String message) {
        GenerationJobEntity job = generationJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus().isTerminal()) {
            return false;
        }
        GenerationRunEntity run = runId == null ? null : generationRunRepository.findById(runId).orElse(null);
        markNeedsInput(job, run, Instant.now(), message);
        if (run != null && response != null) {
            applyProviderResponse(run, response);
            generationRunRepository.save(run);
        }
        return true;
    }

    /** Records a classified provider or transport failure and schedules backoff when safe. */
    @Transactional
    public FailureOutcome handleFailure(
            UUID jobId,
            UUID runId,
            AiFailureCode failureCode,
            boolean retryable) {
        GenerationJobEntity job = generationJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus().isTerminal()) {
            return new FailureOutcome(false, toGenerationErrorCode(failureCode), null);
        }
        GenerationRunEntity run = runId == null ? null : generationRunRepository.findById(runId).orElse(null);
        Instant now = Instant.now();
        if (job.isCancelRequested() || isProjectArchived(job)) {
            markCancelled(job, run, now);
            return new FailureOutcome(false, GenerationErrorCode.CANCELLED, null);
        }

        GenerationErrorCode errorCode = toGenerationErrorCode(failureCode);
        if (run != null) {
            run.setStatus(failureCode == AiFailureCode.TIMEOUT ? GenerationRunStatus.TIMED_OUT : GenerationRunStatus.FAILED);
            run.setRetryable(retryable);
            run.setFailureCode(errorCode);
            run.setFailureMessage(safeFailureMessage(errorCode));
            run.setCompletedAt(now);
            generationRunRepository.save(run);
        }

        job.setErrorCode(errorCode);
        job.setErrorDetails(safeFailureDetails(errorCode));
        if (retryable && job.getAttemptCount() < job.getMaxAttempts()) {
            Duration delay = retryDelay(job.getAttemptCount());
            job.setStatus(GenerationJobStatus.QUEUED);
            job.setNextAttemptAt(now.plus(delay));
            job.setRetryable(true);
            job.setStatusMessage("The generation service is temporarily unavailable; retrying automatically.");
            job.setUserMessage("The request is still queued and will retry automatically.");
            generationJobRepository.save(job);
            return new FailureOutcome(false, errorCode, job.getNextAttemptAt());
        }

        job.setStatus(GenerationJobStatus.FAILED);
        job.setCompletedAt(now);
        job.setNextAttemptAt(null);
        job.setRetryable(retryable);
        job.setStatusMessage("Generation stopped before an artifact could be published.");
        job.setUserMessage(safeFailureMessage(errorCode));
        generationJobRepository.save(job);
        ProjectEntity project = job.getProject();
        if (project.getStatus() == ProjectStatus.GENERATING) {
            project.setStatus(ProjectStatus.FAILED);
            project.setProgress(0);
            projectRepository.save(project);
        }
        auditService.record(job.getOwner().getId(), job.getOwner().getEmail(), AuditAction.GENERATION_JOB_FAILED,
                "Generation failed for project: " + project.getName() + " (" + errorCode + ")");
        return new FailureOutcome(true, errorCode, null);
    }

    /** Requeues a stale in-flight job after a process restart without creating a second run. */
    @Transactional
    public void requeueStaleJob(UUID jobId) {
        GenerationJobEntity job = generationJobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || !ACTIVE_STATUSES.contains(job.getStatus())) {
            return;
        }
        if (job.isCancelRequested() || isProjectArchived(job)) {
            markCancelled(job, null, Instant.now());
            return;
        }
        job.setStatus(GenerationJobStatus.QUEUED);
        job.setNextAttemptAt(Instant.now());
        job.setStatusMessage("The worker restarted; resuming this durable generation request.");
        job.setUserMessage(null);
        generationJobRepository.save(job);
    }

    private Optional<GenerationRunEntity> reusableRunningAttempt(GenerationJobEntity job) {
        if (job.getAttemptCount() < 1) {
            return Optional.empty();
        }
        return generationRunRepository.findByGenerationJobIdAndAttemptNumber(job.getId(), job.getAttemptCount())
                .filter(run -> run.getStatus() == GenerationRunStatus.RUNNING);
    }

    private GenerationRunEntity createAttempt(GenerationJobEntity job, PromptTemplateEntity template, Instant now) {
        if (job.getAttemptCount() >= job.getMaxAttempts()) {
            job.setStatus(GenerationJobStatus.FAILED);
            job.setCompletedAt(now);
            job.setRetryable(true);
            job.setErrorCode(GenerationErrorCode.RETRIES_EXHAUSTED);
            job.setStatusMessage("The automatic retry budget was exhausted.");
            job.setUserMessage("Automatic retries were exhausted. You can start a new retry from this job.");
            generationJobRepository.save(job);
            return null;
        }
        int attempt = job.getAttemptCount() + 1;
        GenerationRunEntity run = GenerationRunEntity.builder()
                .generationJob(job)
                .attemptNumber(attempt)
                .status(GenerationRunStatus.RUNNING)
                .promptTemplate(template)
                .promptTemplateKey(template.getTemplateKey())
                .promptTemplateVersion(template.getTemplateVersion())
                .promptChecksum(template.getChecksum())
                .inputSnapshot(job.getInputSnapshot().deepCopy())
                .outputMetadata(objectMapper.createObjectNode())
                .validatorOutcome(objectMapper.createObjectNode())
                .startedAt(now)
                .correlationId(job.getCorrelationId())
                .build();
        run = generationRunRepository.save(run);
        job.setAttemptCount(attempt);
        return run;
    }

    private void markNeedsInput(GenerationJobEntity job, GenerationRunEntity run, Instant now, String message) {
        if (run != null && !run.getStatus().isTerminal()) {
            run.setStatus(GenerationRunStatus.FAILED);
            run.setFailureCode(GenerationErrorCode.VALIDATION_FAILED);
            run.setFailureMessage(message);
            run.setRetryable(false);
            run.setCompletedAt(now);
            generationRunRepository.save(run);
        }
        job.setStatus(GenerationJobStatus.NEEDS_INPUT);
        job.setCompletedAt(now);
        job.setRetryable(false);
        job.setErrorCode(GenerationErrorCode.VALIDATION_FAILED);
        job.setStatusMessage("More project input is required before this artifact can be published.");
        job.setUserMessage(message);
        generationJobRepository.save(job);
        ProjectEntity project = job.getProject();
        if (project.getStatus() == ProjectStatus.GENERATING) {
            project.setStatus(ProjectStatus.DISCOVERY);
            project.setProgress(0);
            projectRepository.save(project);
        }
        auditService.record(job.getOwner().getId(), job.getOwner().getEmail(), AuditAction.GENERATION_JOB_FAILED,
                "Generation needs more input for project: " + project.getName());
    }

    private void markCancelled(GenerationJobEntity job, GenerationRunEntity run, Instant now) {
        if (run != null && !run.getStatus().isTerminal()) {
            run.setStatus(GenerationRunStatus.CANCELLED);
            run.setRetryable(false);
            run.setFailureCode(GenerationErrorCode.CANCELLED);
            run.setFailureMessage("Generation was cancelled before publication.");
            run.setCompletedAt(now);
            generationRunRepository.save(run);
        }
        job.setCancelRequested(true);
        if (job.getCancelRequestedAt() == null) {
            job.setCancelRequestedAt(now);
        }
        job.setStatus(GenerationJobStatus.CANCELLED);
        job.setCompletedAt(now);
        job.setRetryable(false);
        job.setErrorCode(GenerationErrorCode.CANCELLED);
        job.setStatusMessage("Generation was cancelled.");
        job.setUserMessage("The generation request was cancelled before an artifact was published.");
        generationJobRepository.save(job);
        ProjectEntity project = job.getProject();
        if (project.getStatus() == ProjectStatus.GENERATING) {
            project.setStatus(ProjectStatus.READY_FOR_GENERATION);
            project.setProgress(0);
            projectRepository.save(project);
        }
    }

    private boolean isProjectArchived(GenerationJobEntity job) {
        return job.getProject().getStatus() == ProjectStatus.ARCHIVED;
    }

    private void applyProviderResponse(GenerationRunEntity run, AiGenerationResponse response) {
        run.setProvider(response.provider());
        run.setModel(response.model());
        run.setLatencyMs(response.latencyMs());
        run.setOutputMetadata(toOutputMetadata(response));
        run.setValidatorOutcome(toValidatorOutcome(response));
        if (response.usage() != null) {
            run.setInputTokens(response.usage().inputTokens());
            run.setOutputTokens(response.usage().outputTokens());
            Long input = response.usage().inputTokens();
            Long output = response.usage().outputTokens();
            run.setTotalTokens((input == null ? 0 : input) + (output == null ? 0 : output));
            if (response.usage().costCents() != null) {
                run.setCostUsd(response.usage().costCents().movePointLeft(2).setScale(6, RoundingMode.HALF_UP));
            }
        }
    }

    private ObjectNode toOutputMetadata(AiGenerationResponse response) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("provider", response.provider());
        metadata.put("model", response.model());
        metadata.put("promptVersion", response.promptVersion());
        metadata.put("latencyMs", response.latencyMs() == null ? 0 : response.latencyMs());
        if (response.artifact() != null && response.artifact().sections() != null) {
            metadata.put("sectionCount", response.artifact().sections().size());
            metadata.set("sections", objectMapper.valueToTree(response.artifact().sections()));
        }
        return metadata;
    }

    private ObjectNode toValidatorOutcome(AiGenerationResponse response) {
        ObjectNode outcome = objectMapper.createObjectNode();
        boolean valid = response.validation() != null && response.validation().valid();
        outcome.put("valid", valid);
        ArrayNode issues = outcome.putArray("issues");
        if (response.validation() != null && response.validation().issues() != null) {
            response.validation().issues().forEach(issues::add);
        }
        return outcome;
    }

    private Duration retryDelay(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 4);
        return properties.getWorker().getRetryBackoff().multipliedBy(multiplier);
    }

    private GenerationErrorCode toGenerationErrorCode(AiFailureCode code) {
        if (code == null) {
            return GenerationErrorCode.INTERNAL_ERROR;
        }
        return switch (code) {
            case UNAVAILABLE -> GenerationErrorCode.PROVIDER_UNAVAILABLE;
            case TIMEOUT -> GenerationErrorCode.PROVIDER_TIMEOUT;
            case INVALID_OUTPUT -> GenerationErrorCode.INVALID_PROVIDER_OUTPUT;
            case SAFETY_REJECTED -> GenerationErrorCode.CONTENT_SAFETY_BLOCKED;
            case INVALID_REQUEST -> GenerationErrorCode.REQUEST_INVALID;
            case INTERNAL_ERROR -> GenerationErrorCode.INTERNAL_ERROR;
        };
    }

    private String safeFailureMessage(GenerationErrorCode code) {
        return switch (code) {
            case PROVIDER_UNAVAILABLE -> "The generation service is temporarily unavailable. You can retry this request.";
            case PROVIDER_TIMEOUT -> "The generation service timed out. You can retry this request.";
            case INVALID_PROVIDER_OUTPUT -> "The generation service returned an invalid structured response.";
            case CONTENT_SAFETY_BLOCKED -> "The generation request could not pass the configured content-safety checks.";
            case REQUEST_INVALID -> "The generation service could not process this request as submitted.";
            default -> "Generation could not be completed. Please try again or contact support with the correlation ID.";
        };
    }

    private ObjectNode safeFailureDetails(GenerationErrorCode code) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("code", code.name());
        details.put("recordedAt", Instant.now().toString());
        return details;
    }

    private int wordCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }
}
