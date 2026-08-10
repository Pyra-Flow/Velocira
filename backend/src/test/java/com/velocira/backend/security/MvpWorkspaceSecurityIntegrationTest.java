package com.velocira.backend.security;

import com.velocira.backend.auth.security.JwtProvider;
import com.velocira.backend.documentation.service.DocumentationPackageService;
import com.velocira.backend.interview.service.InterviewService;
import com.velocira.backend.knowledge.service.KnowledgeSourceService;
import com.velocira.backend.knowledge.service.SrsService;
import com.velocira.backend.project.exceptions.ProjectAccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real JWT filter and owner boundary for the Phase 3-5 workspace APIs.
 * Service-level owner checks are represented by the same exception used in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("MVP workspace API security integration tests")
class MvpWorkspaceSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private InterviewService interviewService;

    @MockitoBean
    private KnowledgeSourceService knowledgeSourceService;

    @MockitoBean
    private SrsService srsService;

    @MockitoBean
    private DocumentationPackageService documentationPackageService;

    @Test
    void rejectsUnauthenticatedInterviewRequest() throws Exception {
        mockMvc.perform(post("/v1/projects/" + UUID.randomUUID() + "/interview/start"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsCrossUserInterviewRequest() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(interviewService.start(projectId, userId)).thenThrow(new ProjectAccessDeniedException());

        mockMvc.perform(post("/v1/projects/" + projectId + "/interview/start")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsCrossUserKnowledgeRequest() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(knowledgeSourceService.list(projectId, userId)).thenThrow(new ProjectAccessDeniedException());

        mockMvc.perform(get("/v1/projects/" + projectId + "/knowledge-sources")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsCrossUserSrsRequest() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(srsService.list(projectId, userId)).thenThrow(new ProjectAccessDeniedException());

        mockMvc.perform(get("/v1/projects/" + projectId + "/srs")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsCrossUserDocumentationPackageRequest() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(documentationPackageService.list(projectId, userId)).thenThrow(new ProjectAccessDeniedException());

        mockMvc.perform(get("/v1/projects/" + projectId + "/documentation-packages")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsMalformedProjectIdAcrossWorkspaceApi() throws Exception {
        String bearer = bearerFor(UUID.randomUUID());

        mockMvc.perform(get("/v1/projects/not-a-uuid/knowledge-sources")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/v1/projects/not-a-uuid/srs")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/v1/projects/not-a-uuid/documentation-packages")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String bearerFor(UUID userId) {
        return "Bearer " + jwtProvider.generateAccessToken(userId, "mvp-security@velocira.test", "USER");
    }
}
