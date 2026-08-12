package com.velocira.backend.project.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.project.dto.ProjectBriefRequest;
import com.velocira.backend.project.dto.ProjectGenerationResponse;
import com.velocira.backend.project.dto.ProjectRefinementRequest;
import com.velocira.backend.project.service.ProjectGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Compact endpoints for the default brief-to-project experience. */
@RestController
@Validated
@RequiredArgsConstructor
public class ProjectGenerationController {

    private final ProjectGenerationService projectGenerationService;

    @PostMapping("/v1/project-generations")
    public ResponseEntity<ApiResponse<ProjectGenerationResponse>> createAndGenerate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ProjectBriefRequest request) {
        ProjectGenerationResponse result = projectGenerationService.createAndGenerate(
                principal.getUserId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(result, "Your project is being created.", HttpStatus.ACCEPTED.value()));
    }

    @GetMapping("/v1/projects/{projectId}/generation")
    public ResponseEntity<ApiResponse<ProjectGenerationResponse>> status(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(
                projectGenerationService.status(projectId, principal.getUserId()), "Project generation retrieved."));
    }

    @PostMapping("/v1/projects/{projectId}/refinements")
    public ResponseEntity<ApiResponse<ProjectGenerationResponse>> refine(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID projectId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody ProjectRefinementRequest request) {
        ProjectGenerationResponse result = projectGenerationService.refine(
                projectId, principal.getUserId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(result, "Your update is being applied.", HttpStatus.ACCEPTED.value()));
    }
}
