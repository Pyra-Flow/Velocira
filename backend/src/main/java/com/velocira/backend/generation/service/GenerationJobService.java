package com.velocira.backend.generation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.document.repository.DocumentRepository;
import com.velocira.backend.generation.config.GenerationProperties;
import com.velocira.backend.generation.dto.CreateGenerationJobRequest;
import com.velocira.backend.generation.dto.GenerationJobMapper;
import com.velocira.backend.generation.dto.GenerationJobResponse;
import com.velocira.backend.generation.event.GenerationJobEvent;
import com.velocira.backend.generation.exceptions.GenerationIdempotencyConflictException;
import com.velocira.backend.generation.exceptions.GenerationJobNotFoundException;
import com.velocira.backend.generation.exceptions.GenerationJobStateException;
import com.velocira.backend.generation.exceptions.GenerationRequestInvalidException;
import com.velocira.backend.generation.model.GenerationErrorCode;
import com.velocira.backend.generation.model.GenerationJobEntity;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.generation.model.PromptTemplateEntity;
import com.velocira.backend.generation.observability.GenerationMetrics;
import com.velocira.backend.generation.repository.GenerationJobRepository;
import com.velocira.backend.generation.repository.PromptTemplateRepository;
import com.velocira.backend.interview.service.InterviewService;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.exceptions.ProjectNotFoundException;
import com.velocira.backend.project.model.ProjectEntity;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Owner-authorized API-facing lifecycle service for durable generation jobs.
 * It captures immutable request evidence before dispatching any asynchronous
 * work, so a page refresh cannot change or lose a generation request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationJobService {

    public static final String PHASE_TWO_PROMPT_KEY = "phase2-small-structured-artifact";
    private static final Set<ProjectStatus> ELIGIBLE_PROJECT_STATUSES = EnumSet.of(
            ProjectStatus.READY_FOR_GENERATION);
    private static final Set<GenerationJobStatus> ACTIVE_STATUSES = EnumSet.of(
            GenerationJobStatus.QUEUED,
            GenerationJobStatus.RETRIEVING,
            GenerationJobStatus.DRAFTING,
            GenerationJobStatus.VALIDATING);

    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final GenerationJobRepository generationJobRepository;
    private final PromptTemplateRepository promptTemplateRepository;
    private final GenerationGuardService generationGuardService;
    private final GenerationProperties properties;
    private final GenerationMetrics metrics;
    private final InterviewService interviewService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /** Result lets the HTTP adapter distinguish a newly accepted job from an idempotent replay. */
    public record JobAcceptance(GenerationJobResponse job, boolean newlyCreated) {
    }

    @Transactional
    public JobAcceptance requestJob(
            UUID projectId,
            UUID ownerId,
            CreateGenerationJobRequest request,
            String idempotencyKey) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        validatePhaseTwoRequest(request);

        ProjectEntity project = loadOwnedProject(projectId, ownerId);
        PromptTemplateEntity template = loadActiveTemplate();
        JsonNode snapshot = buildInputSnapshot(project, request);
        String snapshotHash = GenerationHashing.sha256(canonicalJson(snapshot));
        String requestHash = GenerationHashing.sha256(snapshotHash + "|" + request.getDocumentType().name()
                + "|" + template.getTemplateKey() + "|" + template.getTemplateVersion() + "|" + template.getChecksum());

        GenerationJobEntity idempotent = generationJobRepository
                .findByOwnerIdAndIdempotencyKey(ownerId, normalizedKey)
                .orElse(null);
        if (idempotent != null) {
            if (!requestHash.equals(idempotent.getRequestHash())) {
                throw new GenerationIdempotencyConflictException();
            }
            return new JobAcceptance(GenerationJobMapper.toResponse(idempotent), false);
        }

        // A different browser request for the same artifact reuses the active job instead of spending twice.
        GenerationJobEntity active = generationJobRepository
                .findFirstByProjectIdAndRequestedDocumentTypeAndStatusInOrderByCreatedAtDesc(
                        projectId, request.getDocumentType(), ACTIVE_STATUSES)
                .orElse(null);
        if (active != null) {
            return new JobAcceptance(GenerationJobMapper.toResponse(active), false);
        }

        ensureEligibleForNewJob(project);
        interviewService.assertGenerationReady(projectId, ownerId);

        // A plain generation never overwrites a document. An explicit refinement creates
        // the next revision of the project plan instead.
        boolean refinement = request.getAdditionalInstructions() != null && !request.getAdditionalInstructions().isBlank();
        if (documentRepository.existsByProjectIdAndType(projectId, request.getDocumentType()) && !refinement) {
            GenerationJobEntity needsInput = buildJob(project, template, request.getDocumentType(), normalizedKey,
                    snapshot, snapshotHash, requestHash, GenerationJobStatus.NEEDS_INPUT);
            Instant now = Instant.now();
            needsInput.setCompletedAt(now);
            needsInput.setStatusMessage("An artifact of this type already exists in this project.");
            needsInput.setUserMessage("This request was not sent to the model because an existing document would be overwritten.");
            needsInput.setErrorCode(GenerationErrorCode.REQUEST_INVALID);
            GenerationJobEntity saved = generationJobRepository.save(needsInput);
            return new JobAcceptance(GenerationJobMapper.toResponse(saved), true);
        }

        generationGuardService.ensureCanQueue(ownerId);

        GenerationJobEntity job = buildJob(project, template, request.getDocumentType(), normalizedKey,
                snapshot, snapshotHash, requestHash, GenerationJobStatus.QUEUED);
        job.setStatusMessage("Generation request accepted and waiting for a worker.");
        job.setNextAttemptAt(Instant.now());
        GenerationJobEntity saved = generationJobRepository.save(job);

        // Phase 2 deliberately permits a small test artifact from early workspace states.
        project.setStatus(ProjectStatus.GENERATING);
        project.setProgress(0);
        projectRepository.save(project);

        metrics.requested();
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.GENERATION_JOB_REQUESTED,
                "Requested " + request.getDocumentType() + " generation for project: " + project.getName());
        publishQueued(saved);
        log.info("Accepted generation job [{}] for project [{}]", saved.getId(), projectId);
        return new JobAcceptance(GenerationJobMapper.toResponse(saved), true);
    }

    @Transactional(readOnly = true)
    public Page<GenerationJobResponse> listJobs(UUID projectId, UUID ownerId, Pageable pageable) {
        loadOwnedProject(projectId, ownerId);
        return generationJobRepository.findByProjectIdAndOwnerId(projectId, ownerId, pageable)
                .map(GenerationJobMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public GenerationJobResponse getJob(UUID projectId, UUID jobId, UUID ownerId) {
        loadOwnedProject(projectId, ownerId);
        return GenerationJobMapper.toResponse(loadOwnedJob(projectId, jobId, ownerId));
    }

    /** Latest job for the concise project workspace; attempt diagnostics remain on the jobs API. */
    @Transactional(readOnly = true)
    public GenerationJobResponse latestJob(UUID projectId, UUID ownerId) {
        loadOwnedProject(projectId, ownerId);
        return generationJobRepository.findFirstByProjectIdAndOwnerIdOrderByCreatedAtDesc(projectId, ownerId)
                .map(GenerationJobMapper::toResponse)
                .orElse(null);
    }

    /** Returns whether an existing artifact needs the next project-plan run to be a revision. */
    @Transactional(readOnly = true)
    public boolean hasDocument(UUID projectId, UUID ownerId, DocumentType documentType) {
        loadOwnedProject(projectId, ownerId);
        return documentRepository.existsByProjectIdAndType(projectId, documentType);
    }

    /** Resolves a repeated top-level create/refine request before it can mutate project state. */
    @Transactional(readOnly = true)
    public GenerationJobResponse findByIdempotencyKey(UUID ownerId, String idempotencyKey) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        return generationJobRepository.findByOwnerIdAndIdempotencyKey(ownerId, normalizedKey)
                .map(GenerationJobMapper::toResponse)
                .orElse(null);
    }

    /** Prevents a refinement from being accepted but silently omitted from an in-flight job. */
    @Transactional(readOnly = true)
    public boolean hasActiveJob(UUID projectId, UUID ownerId) {
        loadOwnedProject(projectId, ownerId);
        return generationJobRepository
                .findFirstByProjectIdAndRequestedDocumentTypeAndStatusInOrderByCreatedAtDesc(
                        projectId, DocumentType.SRS, ACTIVE_STATUSES)
                .isPresent();
    }

    @Transactional
    public GenerationJobResponse cancelJob(UUID projectId, UUID jobId, UUID ownerId) {
        ProjectEntity project = loadOwnedProject(projectId, ownerId);
        GenerationJobEntity job = lockOwnedJob(projectId, jobId, ownerId);
        if (!job.getStatus().isCancellable()) {
            return GenerationJobMapper.toResponse(job);
        }

        Instant now = Instant.now();
        job.setCancelRequested(true);
        job.setCancelRequestedAt(now);
        job.setStatus(GenerationJobStatus.CANCELLED);
        job.setCompletedAt(now);
        job.setRetryable(false);
        job.setErrorCode(GenerationErrorCode.CANCELLED);
        job.setStatusMessage("Generation was cancelled.");
        job.setUserMessage("The generation request was cancelled before an artifact was published.");
        generationJobRepository.save(job);
        if (project.getStatus() == ProjectStatus.GENERATING) {
            project.setStatus(ProjectStatus.READY_FOR_GENERATION);
            project.setProgress(0);
            projectRepository.save(project);
        }
        metrics.cancelled();
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.GENERATION_JOB_CANCELLED,
                "Cancelled generation job " + jobId + " for project: " + project.getName());
        return GenerationJobMapper.toResponse(job);
    }

    /**
     * Retry is a new durable job with a new caller-supplied idempotency key;
     * the failed job stays immutable evidence. This avoids both duplicated work
     * and mutating a job whose attempt budget has already been exhausted.
     */
    @Transactional
    public JobAcceptance retryJob(UUID projectId, UUID jobId, UUID ownerId, String retryIdempotencyKey) {
        String normalizedKey = requireIdempotencyKey(retryIdempotencyKey);
        ProjectEntity project = loadOwnedProject(projectId, ownerId);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new GenerationJobStateException("An archived project must be restored before its generation job can be retried.");
        }
        GenerationJobEntity failedJob = loadOwnedJob(projectId, jobId, ownerId);
        if (failedJob.getStatus() != GenerationJobStatus.FAILED || !failedJob.isRetryable()) {
            throw new GenerationJobStateException("Only a retryable failed generation job can be retried.");
        }
        interviewService.assertGenerationReady(projectId, ownerId);

        GenerationJobEntity idempotent = generationJobRepository
                .findByOwnerIdAndIdempotencyKey(ownerId, normalizedKey)
                .orElse(null);
        if (idempotent != null) {
            if (!failedJob.getRequestHash().equals(idempotent.getRequestHash())) {
                throw new GenerationIdempotencyConflictException();
            }
            return new JobAcceptance(GenerationJobMapper.toResponse(idempotent), false);
        }
        if (documentRepository.existsByProjectIdAndType(projectId, failedJob.getRequestedDocumentType())) {
            throw new GenerationJobStateException("A document of this type already exists, so this failed job cannot be retried safely.");
        }
        GenerationJobEntity active = generationJobRepository
                .findFirstByProjectIdAndRequestedDocumentTypeAndStatusInOrderByCreatedAtDesc(
                        projectId, failedJob.getRequestedDocumentType(), ACTIVE_STATUSES)
                .orElse(null);
        if (active != null) {
            return new JobAcceptance(GenerationJobMapper.toResponse(active), false);
        }

        generationGuardService.ensureCanQueue(ownerId);
        PromptTemplateEntity template = promptTemplateRepository
                .findByTemplateKeyAndTemplateVersion(failedJob.getPromptTemplateKey(), failedJob.getPromptTemplateVersion())
                .orElseThrow(() -> new GenerationJobStateException(
                        "The original prompt revision is no longer available for a reproducible retry."));
        if (!template.getChecksum().equals(GenerationHashing.sha256(template.getContent()))) {
            throw new GenerationJobStateException("The original prompt revision did not pass its integrity check.");
        }

        GenerationJobEntity retry = buildJob(project, template, failedJob.getRequestedDocumentType(), normalizedKey,
                failedJob.getInputSnapshot().deepCopy(), failedJob.getInputSnapshotHash(), failedJob.getRequestHash(),
                GenerationJobStatus.QUEUED);
        retry.setStatusMessage("Retry request accepted and waiting for a worker.");
        retry.setNextAttemptAt(Instant.now());
        GenerationJobEntity saved = generationJobRepository.save(retry);
        project.setStatus(ProjectStatus.GENERATING);
        project.setProgress(0);
        projectRepository.save(project);
        metrics.requested();
        auditService.record(ownerId, project.getOwner().getEmail(), AuditAction.GENERATION_JOB_RETRIED,
                "Retried failed generation job " + jobId + " for project: " + project.getName());
        publishQueued(saved);
        return new JobAcceptance(GenerationJobMapper.toResponse(saved), true);
    }

    private GenerationJobEntity buildJob(
            ProjectEntity project,
            PromptTemplateEntity template,
            DocumentType documentType,
            String idempotencyKey,
            JsonNode snapshot,
            String snapshotHash,
            String requestHash,
            GenerationJobStatus status) {
        return GenerationJobEntity.builder()
                .project(project)
                .owner(project.getOwner())
                .requestedDocumentType(documentType)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .inputSnapshot(snapshot.deepCopy())
                .inputSnapshotHash(snapshotHash)
                .promptTemplateKey(template.getTemplateKey())
                .promptTemplateVersion(template.getTemplateVersion())
                .correlationId(currentCorrelationId())
                .status(status)
                .maxAttempts(Math.max(1, properties.getWorker().getMaxAttempts()))
                .queuedAt(Instant.now())
                .errorDetails(objectMapper.createObjectNode())
                .build();
    }

    private PromptTemplateEntity loadActiveTemplate() {
        PromptTemplateEntity template = promptTemplateRepository
                .findFirstByTemplateKeyAndEnabledTrueOrderByCreatedAtDesc(PHASE_TWO_PROMPT_KEY)
                .orElseThrow(() -> new GenerationRequestInvalidException(
                        "Generation is not configured yet. Contact support before trying again."));
        if (!template.getChecksum().equals(GenerationHashing.sha256(template.getContent()))) {
            throw new GenerationRequestInvalidException("The configured generation prompt did not pass its integrity check.");
        }
        return template;
    }

    private ProjectEntity loadOwnedProject(UUID projectId, UUID ownerId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId.toString()));
        if (!project.getOwner().getId().equals(ownerId)) {
            throw new ProjectAccessDeniedException();
        }
        return project;
    }

    private GenerationJobEntity loadOwnedJob(UUID projectId, UUID jobId, UUID ownerId) {
        return generationJobRepository.findByIdAndProjectIdAndOwnerId(jobId, projectId, ownerId)
                .orElseThrow(() -> new GenerationJobNotFoundException(jobId.toString()));
    }

    private GenerationJobEntity lockOwnedJob(UUID projectId, UUID jobId, UUID ownerId) {
        GenerationJobEntity job = generationJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new GenerationJobNotFoundException(jobId.toString()));
        if (!job.getProject().getId().equals(projectId) || !job.getOwner().getId().equals(ownerId)) {
            throw new GenerationJobNotFoundException(jobId.toString());
        }
        return job;
    }

    private void validatePhaseTwoRequest(CreateGenerationJobRequest request) {
        if (request == null || request.getDocumentType() == null) {
            throw new GenerationRequestInvalidException("A document type is required.");
        }
        if (request.getDocumentType() != DocumentType.SRS) {
            throw new GenerationRequestInvalidException(
                    "Phase 2 currently supports only the small SRS test artifact.");
        }
    }

    private void ensureEligibleForNewJob(ProjectEntity project) {
        if (!ELIGIBLE_PROJECT_STATUSES.contains(project.getStatus())) {
            throw new GenerationJobStateException(
                    "This project is " + project.getStatus() + " and cannot start a new generation job. "
                            + "Move it to discovery or ready for generation first.");
        }
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new GenerationRequestInvalidException("An Idempotency-Key header is required.");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 128) {
            throw new GenerationRequestInvalidException("Idempotency-Key must not exceed 128 characters.");
        }
        return normalized;
    }

    private JsonNode buildInputSnapshot(ProjectEntity project, CreateGenerationJobRequest request) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        ObjectNode projectNode = snapshot.putObject("project");
        projectNode.put("id", project.getId().toString());
        projectNode.put("name", project.getName());
        projectNode.put("description", project.getDescription());
        projectNode.put("type", project.getType().name());
        putIfPresent(projectNode, "techStack", project.getTechStack());
        putIfPresent(projectNode, "industry", project.getIndustry());
        putIfPresent(projectNode, "targetAudience", project.getTargetAudience());
        if (project.getTeamSize() != null) {
            projectNode.put("teamSize", project.getTeamSize());
        }
        snapshot.put("artifactType", request.getDocumentType().name());
        if (request.getAdditionalInstructions() != null && !request.getAdditionalInstructions().isBlank()) {
            snapshot.put("additionalInstructions", request.getAdditionalInstructions().trim());
        }
        return snapshot;
    }

    private void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value.trim());
        }
    }

    private String canonicalJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the generation input snapshot", ex);
        }
    }

    private void publishQueued(GenerationJobEntity job) {
        eventPublisher.publishEvent(new GenerationJobEvent(
                job.getId(), job.getProject().getId(), job.getStatus().name(), job.getCorrelationId(), Instant.now()));
    }

    private String currentCorrelationId() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}
