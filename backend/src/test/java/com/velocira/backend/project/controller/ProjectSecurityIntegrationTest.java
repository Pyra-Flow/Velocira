package com.velocira.backend.project.controller;

import com.velocira.backend.auth.security.JwtProvider;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import com.velocira.backend.project.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the project API's negative authorization and input paths with the real security chain. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Project API security integration tests")
class ProjectSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ProjectService projectService;

    @Test
    @DisplayName("Rejects an unauthenticated project request")
    void rejectsUnauthenticatedProjectRequest() throws Exception {
        mockMvc.perform(get("/v1/projects/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Rejects a cross-user project read")
    void rejectsCrossUserProjectRead() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        doThrow(new ProjectAccessDeniedException()).when(projectService).getProject(projectId, otherUserId);

        mockMvc.perform(get("/v1/projects/" + projectId)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(otherUserId, "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        verify(projectService).getProject(projectId, otherUserId);
    }

    @Test
    @DisplayName("Rejects an authenticated request with a malformed project identifier")
    void rejectsMalformedProjectIdentifier() throws Exception {
        mockMvc.perform(get("/v1/projects/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(UUID.randomUUID(), "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Rejects a non-admin user from the admin API")
    void rejectsWrongRole() throws Exception {
        mockMvc.perform(get("/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(UUID.randomUUID(), "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String bearerFor(UUID userId, String role) {
        return "Bearer " + jwtProvider.generateAccessToken(userId, "security-test@velocira.com", role);
    }
}
