package com.velocira.backend.project.service;

import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.generation.dto.CreateGenerationJobRequest;
import com.velocira.backend.generation.dto.GenerationJobResponse;
import com.velocira.backend.generation.exceptions.GenerationIdempotencyConflictException;
import com.velocira.backend.generation.exceptions.GenerationJobStateException;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.generation.service.GenerationJobService;
import com.velocira.backend.interview.service.InterviewService;
import com.velocira.backend.project.dto.CreateProjectRequest;
import com.velocira.backend.project.dto.ProjectBriefRequest;
import com.velocira.backend.project.dto.ProjectGenerationResponse;
import com.velocira.backend.project.dto.ProjectGenerationJobResponse;
import com.velocira.backend.project.dto.ProjectGenerationStage;
import com.velocira.backend.project.dto.ProjectRefinementRequest;
import com.velocira.backend.project.dto.ProjectResponse;
import com.velocira.backend.project.dto.UpdateProjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Orchestrates a plain-language brief into a durable, background project-plan generation. */
@Service
@RequiredArgsConstructor
public class ProjectGenerationService {

    private final ProjectService projectService;
    private final InterviewService interviewService;
    private final GenerationJobService generationJobService;

    @Transactional
    public ProjectGenerationResponse createAndGenerate(UUID ownerId, ProjectBriefRequest request, String idempotencyKey) {
        GenerationJobResponse replay = generationJobService.findByIdempotencyKey(ownerId, idempotencyKey);
        if (replay != null) {
            ProjectResponse existing = projectService.getProject(replay.getProjectId(), ownerId);
            if (!matchesInitialRequest(existing, request)) throw new GenerationIdempotencyConflictException();
            return response(existing, replay);
        }
        String description = ProjectBriefDefaults.description(request);
        ProjectResponse project = projectService.createProject(ownerId, CreateProjectRequest.builder()
                .name(ProjectBriefDefaults.title(request))
                .description(description)
                .type(ProjectBriefDefaults.type(description))
                .targetAudience(blankToNull(request.audience()))
                .build());
        interviewService.bootstrapFromBrief(project.getId(), ownerId, description);
        GenerationJobResponse job = generationJobService.requestJob(project.getId(), ownerId,
                CreateGenerationJobRequest.builder().documentType(DocumentType.SRS).build(), idempotencyKey).job();
        return response(projectService.getProject(project.getId(), ownerId), job);
    }

    @Transactional
    public ProjectGenerationResponse refine(UUID projectId, UUID ownerId, ProjectRefinementRequest request, String idempotencyKey) {
        GenerationJobResponse replay = generationJobService.findByIdempotencyKey(ownerId, idempotencyKey);
        if (replay != null) {
            if (!projectId.equals(replay.getProjectId())) throw new GenerationIdempotencyConflictException();
            return response(projectService.getProject(projectId, ownerId), replay);
        }
        if (generationJobService.hasActiveJob(projectId, ownerId)) {
            throw new GenerationJobStateException("An update is already being created. Wait for it to finish, then try your change again.");
        }
        ProjectResponse current = projectService.getProject(projectId, ownerId);
        String refinement = request.message().trim();
        String description = limit(current.getDescription() + "\n\nUpdate requested: " + refinement, 2_000);
        ProjectResponse project = projectService.updateProject(projectId, ownerId,
                UpdateProjectRequest.builder().description(description).build());
        interviewService.bootstrapFromBrief(projectId, ownerId, description);
        GenerationJobResponse job = generationJobService.requestJob(projectId, ownerId,
                CreateGenerationJobRequest.builder().documentType(DocumentType.SRS)
                        .additionalInstructions("Update the project plan to reflect this request: " + refinement).build(),
                idempotencyKey).job();
        return response(project, job);
    }

    public ProjectGenerationResponse status(UUID projectId, UUID ownerId) {
        ProjectResponse project = projectService.getProject(projectId, ownerId);
        GenerationJobResponse job = generationJobService.latestJob(projectId, ownerId);
        return response(project, job);
    }

    private ProjectGenerationResponse response(ProjectResponse project, GenerationJobResponse job) {
        GenerationJobStatus status = job == null ? null : job.getStatus();
        ProjectGenerationStage stage = stageFor(status);
        if (stage == ProjectGenerationStage.NEEDS_INPUT && job != null && job.getDocumentId() != null) {
            stage = ProjectGenerationStage.PARTIAL;
        }
        ProjectGenerationJobResponse safeJob = job == null ? null
                : new ProjectGenerationJobResponse(job.getId(), job.getDocumentId());
        return new ProjectGenerationResponse(project, safeJob, stage, headlineFor(stage), detailFor(stage, job),
                status != null && status.isCancellable(), status == GenerationJobStatus.FAILED && job.isRetryable());
    }

    static ProjectGenerationStage stageFor(GenerationJobStatus status) {
        return switch (status == null ? GenerationJobStatus.NEEDS_INPUT : status) {
            case QUEUED, RETRIEVING -> ProjectGenerationStage.UNDERSTANDING;
            case DRAFTING -> ProjectGenerationStage.CREATING;
            case VALIDATING -> ProjectGenerationStage.FINISHING;
            case READY -> ProjectGenerationStage.READY;
            case FAILED -> ProjectGenerationStage.FAILED;
            case CANCELLED -> ProjectGenerationStage.CANCELLED;
            case NEEDS_INPUT -> ProjectGenerationStage.NEEDS_INPUT;
        };
    }

    static String headlineFor(ProjectGenerationStage stage) {
        return switch (stage) {
            case UNDERSTANDING -> "Understanding your project...";
            case CREATING -> "Creating the first version...";
            case FINISHING -> "Adding the finishing touches...";
            case READY -> "Your project is ready.";
            case PARTIAL -> "Part of your project is ready.";
            case FAILED -> "We couldn't finish this version.";
            case CANCELLED -> "Generation was cancelled.";
            default -> "Your project is ready to generate.";
        };
    }

    static String detailFor(ProjectGenerationStage stage, GenerationJobResponse job) {
        return switch (stage) {
            case UNDERSTANDING -> "We're turning your idea into a clear starting point.";
            case CREATING -> "We're shaping the main goals, flows, and requirements.";
            case FINISHING -> "We're checking the result before showing it to you.";
            case READY -> "Review the first version, then refine it in your own words.";
            case PARTIAL -> "The earlier version is still available. Tell us what to complete or change.";
            case FAILED -> job == null || job.getUserMessage() == null ? "Your idea is saved. You can safely try again." : job.getUserMessage();
            case CANCELLED -> "Your project idea is still saved whenever you're ready.";
            default -> "Start with the description you already shared.";
        };
    }

    private static boolean matchesInitialRequest(ProjectResponse project, ProjectBriefRequest request) {
        return project.getName().equals(ProjectBriefDefaults.title(request))
                && project.getDescription().equals(ProjectBriefDefaults.description(request))
                && project.getType() == ProjectBriefDefaults.type(ProjectBriefDefaults.description(request))
                && java.util.Objects.equals(project.getTargetAudience(), blankToNull(request.audience()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1).trim() + "…";
    }
}
