package com.velocira.backend.documentation.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.auth.security.JwtAccessDeniedHandler;
import com.velocira.backend.auth.security.JwtAuthenticationEntryPoint;
import com.velocira.backend.auth.security.JwtAuthenticationFilter;
import com.velocira.backend.common.exception.GlobalExceptionHandler;
import com.velocira.backend.documentation.dto.DocumentationDtos;
import com.velocira.backend.documentation.exceptions.DocumentationPackageException;
import com.velocira.backend.documentation.model.DocumentationExportFormat;
import com.velocira.backend.documentation.model.DocumentationExportStyle;
import com.velocira.backend.documentation.service.DocumentationExportService;
import com.velocira.backend.documentation.service.DocumentationPackageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc coverage for documentation-package export request compatibility and validation. */
@WebMvcTest(DocumentationPackageController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("DocumentationPackageController export tests")
class DocumentationPackageControllerExportTest {

    private final UUID projectId = UUID.randomUUID();
    private final UUID packageId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentationPackageService packageService;

    @MockitoBean
    private DocumentationExportService exportService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @BeforeEach
    void authenticateRequest() {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "owner@velocira.com", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("POST /v1/projects/{projectId}/documentation-packages/{packageId}/exports")
    class ExportEndpoint {

        @Test
        @DisplayName("Should apply defaults to a legacy format-only PDF request")
        void shouldApplyDefaultsToLegacyFormatOnlyPdfRequest() throws Exception {
            DocumentationExportStyle defaultStyle = DocumentationExportStyle.defaults();
            DocumentationDtos.ExportResponse response = new DocumentationDtos.ExportResponse(
                    UUID.randomUUID(), "PDF", defaultStyle.template().name(), defaultStyle.theme().name(),
                    defaultStyle.layout().name(), "READY", "documentation-package.pdf", "application/pdf",
                    512L, "checksum", Instant.now(), Instant.now());
            when(exportService.create(eq(projectId), eq(packageId), eq(userId),
                    eq(DocumentationExportFormat.PDF), any(DocumentationExportStyle.class))).thenReturn(response);

            mockMvc.perform(post(exportPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"format\":\"PDF\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.template").value(defaultStyle.template().name()))
                    .andExpect(jsonPath("$.data.theme").value(defaultStyle.theme().name()))
                    .andExpect(jsonPath("$.data.layout").value(defaultStyle.layout().name()));

            ArgumentCaptor<DocumentationExportStyle> styleCaptor = ArgumentCaptor.forClass(DocumentationExportStyle.class);
            verify(exportService).create(eq(projectId), eq(packageId), eq(userId),
                    eq(DocumentationExportFormat.PDF), styleCaptor.capture());
            assertThat(styleCaptor.getValue()).isEqualTo(defaultStyle);
        }

        @Test
        @DisplayName("Should return 400 when a source export is given a non-default theme")
        void shouldRejectStyledSourceExport() throws Exception {
            when(exportService.create(eq(projectId), eq(packageId), eq(userId),
                    eq(DocumentationExportFormat.OPENAPI_JSON), any(DocumentationExportStyle.class)))
                    .thenThrow(new DocumentationPackageException(
                            "Template, theme, and layout are available only for ZIP, PDF, and DOCX exports.",
                            HttpStatus.BAD_REQUEST));

            mockMvc.perform(post(exportPath())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"format\":\"OPENAPI_JSON\",\"theme\":\"OCEAN\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(
                            "Template, theme, and layout are available only for ZIP, PDF, and DOCX exports."));
        }

        private String exportPath() {
            return "/v1/projects/" + projectId + "/documentation-packages/" + packageId + "/exports";
        }
    }
}
