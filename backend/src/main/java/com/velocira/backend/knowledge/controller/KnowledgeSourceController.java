package com.velocira.backend.knowledge.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.knowledge.dto.KnowledgeDtos;
import com.velocira.backend.knowledge.service.KnowledgeSourceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Project evidence ingestion. All retrieval candidates originate from an approved source here. */
@RestController
@RequestMapping("/v1/projects/{projectId}/knowledge-sources")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class KnowledgeSourceController {
    private final KnowledgeSourceService sourceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<KnowledgeDtos.SourceResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(sourceService.list(projectId, principal.getUserId()), "Evidence sources retrieved."));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<KnowledgeDtos.SourceResponse>> upload(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file, @RequestParam(required = false) @Size(max = 255) String title) {
        KnowledgeDtos.SourceResponse source = sourceService.upload(projectId, principal.getUserId(), title, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(source, "Evidence uploaded for review.", 201));
    }

    @PostMapping("/{sourceId}/approve")
    public ResponseEntity<ApiResponse<KnowledgeDtos.SourceResponse>> approve(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID sourceId) {
        return ResponseEntity.ok(ApiResponse.success(sourceService.approve(projectId, sourceId, principal.getUserId()), "Evidence approved and indexed."));
    }

    @PostMapping("/{sourceId}/reject")
    public ResponseEntity<ApiResponse<KnowledgeDtos.SourceResponse>> reject(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID sourceId) {
        return ResponseEntity.ok(ApiResponse.success(sourceService.reject(projectId, sourceId, principal.getUserId()), "Evidence rejected."));
    }

    @DeleteMapping("/{sourceId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID sourceId) {
        sourceService.delete(projectId, sourceId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
