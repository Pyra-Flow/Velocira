package com.velocira.backend.project.service;

import com.velocira.backend.generation.dto.GenerationJobResponse;
import com.velocira.backend.generation.exceptions.GenerationJobStateException;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.generation.service.GenerationJobService;
import com.velocira.backend.interview.service.InterviewService;
import com.velocira.backend.project.dto.ProjectBriefRequest;
import com.velocira.backend.project.dto.ProjectRefinementRequest;
import com.velocira.backend.project.dto.ProjectResponse;
import com.velocira.backend.project.dto.UpdateProjectRequest;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.model.ProjectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationWorkflowTest {

    @Mock private ProjectService projectService;
    @Mock private InterviewService interviewService;
    @Mock private GenerationJobService generationJobService;
    @InjectMocks private ProjectGenerationService service;

    @Test
    void replays_an_identical_create_without_creating_a_second_project() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String key = "create-" + UUID.randomUUID();
        ProjectBriefRequest request = new ProjectBriefRequest(
                "A booking app for a salon where clients choose services and staff.", null, null, null, null);
        ProjectResponse project = project(projectId, request);
        GenerationJobResponse job = GenerationJobResponse.builder().id(UUID.randomUUID()).projectId(projectId)
                .status(GenerationJobStatus.QUEUED).build();
        when(generationJobService.findByIdempotencyKey(ownerId, key)).thenReturn(job);
        when(projectService.getProject(projectId, ownerId)).thenReturn(project);

        service.createAndGenerate(ownerId, request, key);

        verify(projectService, never()).createProject(any(), any());
        verify(interviewService, never()).bootstrapFromBrief(any(), any(), any());
    }

    @Test
    void rejects_a_refinement_while_another_generation_is_active_without_mutating_the_project() {
        UUID ownerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String key = "refine-" + UUID.randomUUID();
        when(generationJobService.findByIdempotencyKey(ownerId, key)).thenReturn(null);
        when(generationJobService.hasActiveJob(projectId, ownerId)).thenReturn(true);

        assertThrows(GenerationJobStateException.class,
                () -> service.refine(projectId, ownerId, new ProjectRefinementRequest("Add a staff calendar."), key));

        verify(projectService, never()).updateProject(eq(projectId), eq(ownerId), any(UpdateProjectRequest.class));
        verify(interviewService, never()).bootstrapFromBrief(any(), any(), any());
    }

    private ProjectResponse project(UUID projectId, ProjectBriefRequest request) {
        String description = ProjectBriefDefaults.description(request);
        return ProjectResponse.builder().id(projectId).name(ProjectBriefDefaults.title(request)).description(description)
                .type(ProjectBriefDefaults.type(description)).status(ProjectStatus.GENERATING)
                .targetAudience(null).build();
    }
}
