package com.velocira.backend.knowledge.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.knowledge.dto.KnowledgeDtos;
import com.velocira.backend.knowledge.service.SrsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Reviewable, cited SRS versions. Generation is blocked until brief and evidence gates pass. */
@RestController
@RequestMapping("/v1/projects/{projectId}/srs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SrsController {
    private final SrsService srsService;

    @GetMapping("/profiles")
    public ResponseEntity<ApiResponse<List<KnowledgeDtos.StandardsProfileResponse>>> profiles() {
        return ResponseEntity.ok(ApiResponse.success(srsService.profiles(), "Standards profiles retrieved."));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<KnowledgeDtos.SrsVersionResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(srsService.list(projectId, principal.getUserId()), "SRS versions retrieved."));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<KnowledgeDtos.SrsVersionResponse>> generate(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId,
            @Valid @RequestBody KnowledgeDtos.GenerateSrsRequest request) {
        KnowledgeDtos.SrsVersionResponse result = srsService.generate(
                projectId, principal.getUserId(), request.profileKey(), request.generationMode());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result, "SRS generated for review.", 201));
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<ApiResponse<KnowledgeDtos.SrsVersionResponse>> get(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.success(srsService.get(projectId, versionId, principal.getUserId()), "SRS version retrieved."));
    }

    @PostMapping("/{versionId}/approve")
    public ResponseEntity<ApiResponse<KnowledgeDtos.SrsVersionResponse>> approve(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.success(srsService.approve(projectId, versionId, principal.getUserId()), "SRS approved."));
    }

    @PostMapping("/{versionId}/request-changes")
    public ResponseEntity<ApiResponse<KnowledgeDtos.SrsVersionResponse>> requestChanges(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID versionId,
            @Valid @RequestBody KnowledgeDtos.ReviewSrsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(srsService.requestChanges(projectId, versionId, principal.getUserId(), request.message()), "Changes requested."));
    }
}
