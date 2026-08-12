package com.velocira.backend.project.service;

import com.velocira.backend.generation.dto.GenerationJobResponse;
import com.velocira.backend.generation.model.GenerationJobStatus;
import com.velocira.backend.project.dto.ProjectGenerationStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectGenerationServiceTest {

    @Test
    void maps_internal_generation_statuses_to_short_user_facing_stages() {
        assertEquals(ProjectGenerationStage.UNDERSTANDING, ProjectGenerationService.stageFor(GenerationJobStatus.QUEUED));
        assertEquals(ProjectGenerationStage.UNDERSTANDING, ProjectGenerationService.stageFor(GenerationJobStatus.RETRIEVING));
        assertEquals(ProjectGenerationStage.CREATING, ProjectGenerationService.stageFor(GenerationJobStatus.DRAFTING));
        assertEquals(ProjectGenerationStage.FINISHING, ProjectGenerationService.stageFor(GenerationJobStatus.VALIDATING));
        assertEquals(ProjectGenerationStage.READY, ProjectGenerationService.stageFor(GenerationJobStatus.READY));
        assertEquals(ProjectGenerationStage.FAILED, ProjectGenerationService.stageFor(GenerationJobStatus.FAILED));
        assertEquals(ProjectGenerationStage.CANCELLED, ProjectGenerationService.stageFor(GenerationJobStatus.CANCELLED));
        assertEquals(ProjectGenerationStage.NEEDS_INPUT, ProjectGenerationService.stageFor(null));
    }

    @Test
    void keeps_diagnostic_failure_detail_out_of_the_default_flow_when_absent() {
        assertEquals("Your idea is saved. You can safely try again.",
                ProjectGenerationService.detailFor(ProjectGenerationStage.FAILED, GenerationJobResponse.builder().build()));
        assertEquals("The service is temporarily unavailable.",
                ProjectGenerationService.detailFor(ProjectGenerationStage.FAILED, GenerationJobResponse.builder()
                        .userMessage("The service is temporarily unavailable.").build()));
    }

    @Test
    void gives_an_existing_partial_artifact_a_recovery_path() {
        assertEquals("Part of your project is ready.", ProjectGenerationService.headlineFor(ProjectGenerationStage.PARTIAL));
        assertEquals("The earlier version is still available. Tell us what to complete or change.",
                ProjectGenerationService.detailFor(ProjectGenerationStage.PARTIAL, null));
    }
}
