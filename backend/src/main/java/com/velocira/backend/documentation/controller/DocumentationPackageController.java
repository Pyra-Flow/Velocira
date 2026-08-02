package com.velocira.backend.documentation.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.documentation.dto.DocumentationDtos;
import com.velocira.backend.documentation.service.DocumentationExportService;
import com.velocira.backend.documentation.service.DocumentationPackageService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Owner-only endpoints for linked MVP documentation packages and their exports. */
@RestController
@RequestMapping("/v1/projects/{projectId}/documentation-packages")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DocumentationPackageController {
    private final DocumentationPackageService packageService;
    private final DocumentationExportService exportService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentationDtos.PackageResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(packageService.list(projectId, principal.getUserId()), "Documentation packages retrieved."));
    }
    @PostMapping
    public ResponseEntity<ApiResponse<DocumentationDtos.PackageResponse>> generate(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId,
                                                                                     @Valid @RequestBody DocumentationDtos.CreatePackageRequest request) {
        DocumentationDtos.PackageResponse result = packageService.generate(projectId, principal.getUserId(), request.srsVersionId());
        return ResponseEntity.status(201).body(ApiResponse.success(result, "Linked documentation package generated for review.", 201));
    }
    @GetMapping("/{packageId}")
    public ResponseEntity<ApiResponse<DocumentationDtos.PackageResponse>> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID packageId) {
        return ResponseEntity.ok(ApiResponse.success(packageService.get(projectId, packageId, principal.getUserId()), "Documentation package retrieved."));
    }
    @PostMapping("/{packageId}/approve")
    public ResponseEntity<ApiResponse<DocumentationDtos.PackageResponse>> approve(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID packageId) {
        return ResponseEntity.ok(ApiResponse.success(packageService.approve(projectId, packageId, principal.getUserId()), "Documentation package approved."));
    }
    @GetMapping("/{packageId}/exports")
    public ResponseEntity<ApiResponse<List<DocumentationDtos.ExportResponse>>> exports(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId, @PathVariable UUID packageId) {
        return ResponseEntity.ok(ApiResponse.success(exportService.list(projectId, packageId, principal.getUserId()), "Documentation exports retrieved."));
    }
    @PostMapping("/{packageId}/exports")
    public ResponseEntity<ApiResponse<DocumentationDtos.ExportResponse>> export(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId,
                                                                                   @PathVariable UUID packageId, @Valid @RequestBody DocumentationDtos.ExportRequest request) {
        DocumentationDtos.ExportResponse result = exportService.create(projectId, packageId, principal.getUserId(), request.format());
        return ResponseEntity.status(201).body(ApiResponse.success(result, "Immutable export created.", 201));
    }
    @GetMapping("/{packageId}/exports/{exportId}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID projectId,
                                            @PathVariable UUID packageId, @PathVariable UUID exportId) {
        DocumentationExportService.Download file = exportService.download(projectId, packageId, exportId, principal.getUserId());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .contentLength(file.bytes().length).body(file.bytes());
    }
}
