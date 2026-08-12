package com.velocira.backend.project.controller;

import com.velocira.backend.auth.security.JwtProvider;
import com.velocira.backend.project.dto.ProjectBriefRequest;
import com.velocira.backend.project.dto.ProjectGenerationJobResponse;
import com.velocira.backend.project.dto.ProjectGenerationResponse;
import com.velocira.backend.project.dto.ProjectGenerationStage;
import com.velocira.backend.project.dto.ProjectRefinementRequest;
import com.velocira.backend.project.dto.ProjectResponse;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.model.ProjectType;
import com.velocira.backend.project.service.ProjectGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectGenerationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @MockitoBean private ProjectGenerationService projectGenerationService;

    @Test
    void rejects_unauthenticated_project_generation() throws Exception {
        mockMvc.perform(post("/v1/project-generations")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"A booking app for a salon\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validates_a_compact_brief_before_orchestration() throws Exception {
        mockMvc.perform(post("/v1/project-generations")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(UUID.randomUUID()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Too short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void accepts_a_single_brief_with_only_user_facing_generation_data() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectResponse project = project(projectId, 0);
        ProjectGenerationJobResponse job = new ProjectGenerationJobResponse(UUID.randomUUID(), null);
        when(projectGenerationService.createAndGenerate(any(), any(ProjectBriefRequest.class), anyString()))
                .thenReturn(new ProjectGenerationResponse(project, job, ProjectGenerationStage.UNDERSTANDING,
                        "Understanding your project...", "We're turning your idea into a clear starting point.", true, false));

        mockMvc.perform(post("/v1/project-generations")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(UUID.randomUUID()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"A booking app for a salon where clients choose services and staff.\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.project.id").value(projectId.toString()))
                .andExpect(jsonPath("$.data.stage").value("UNDERSTANDING"))
                .andExpect(jsonPath("$.data.headline").value("Understanding your project..."))
                .andExpect(jsonPath("$.data.generation.status").doesNotExist())
                .andExpect(jsonPath("$.data.generation.correlationId").doesNotExist());
    }

    @Test
    void accepts_a_short_refinement_without_requiring_a_new_setup_flow() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectGenerationJobResponse job = new ProjectGenerationJobResponse(UUID.randomUUID(), null);
        when(projectGenerationService.refine(any(), any(), any(ProjectRefinementRequest.class), anyString()))
                .thenReturn(new ProjectGenerationResponse(project(projectId, 1), job, ProjectGenerationStage.CREATING,
                        "Creating the first version...", "We're shaping the main goals, flows, and requirements.", true, false));

        mockMvc.perform(post("/v1/projects/{projectId}/refinements", projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(UUID.randomUUID()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Add a simple staff availability calendar.\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.stage").value("CREATING"));
    }

    private ProjectResponse project(UUID projectId, long documentCount) {
        Instant now = Instant.now();
        return ProjectResponse.builder().id(projectId).name("Salon booking app")
                .description("A booking app for a salon where clients choose services and staff.")
                .type(ProjectType.WEB_APP).status(ProjectStatus.GENERATING).progress(0).documentCount(documentCount)
                .ownerId(UUID.randomUUID()).ownerName("Test owner").createdAt(now).updatedAt(now).build();
    }

    private String bearerFor(UUID userId) {
        return "Bearer " + jwtProvider.generateAccessToken(userId, "generation-flow@velocira.test", "USER");
    }
}
