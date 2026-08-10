package com.velocira.backend.generation;

import com.velocira.backend.auth.security.JwtProvider;
import com.velocira.backend.generation.dto.CreateGenerationJobRequest;
import com.velocira.backend.generation.service.GenerationJobService;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that the new generation API is protected by the real JWT filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Generation job API security integration tests")
class GenerationJobSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private GenerationJobService generationJobService;

    @Test
    void rejectsUnauthenticatedGenerationRequests() throws Exception {
        mockMvc.perform(post(path(UUID.randomUUID()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"SRS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsCrossUserGenerationRequests() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        when(generationJobService.requestJob(eq(projectId), eq(otherUserId), any(CreateGenerationJobRequest.class), anyString()))
                .thenThrow(new ProjectAccessDeniedException());

        mockMvc.perform(post(path(projectId))
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(otherUserId))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"SRS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsMalformedProjectIdentifiersBeforeTheServiceIsCalled() throws Exception {
        mockMvc.perform(post("/v1/projects/not-a-uuid/generation-jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(UUID.randomUUID()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"SRS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String path(UUID projectId) {
        return "/v1/projects/" + projectId + "/generation-jobs";
    }

    private String bearerFor(UUID userId) {
        return "Bearer " + jwtProvider.generateAccessToken(userId, "generation-security@velocira.test", "USER");
    }
}
