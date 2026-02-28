package com.velocira.backend.document.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.document.dto.CreateDocumentRequest;
import com.velocira.backend.document.dto.DocumentResponse;
import com.velocira.backend.document.dto.UpdateDocumentRequest;
import com.velocira.backend.document.model.DocumentStatus;
import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for document CRUD operations within a project.
 *
 * <p>
 * All endpoints are nested under {@code /v1/projects/{projectId}/documents}
 * to enforce the project–document relationship. Ownership is validated on
 * every request.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/projects/{projectId}/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Documents", description = "Document management within projects")
public class DocumentController {

        private final DocumentService documentService;

        @GetMapping
        @Operation(summary = "List project documents", description = "Returns paginated documents for a project with optional filters (no content)")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Documents retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found")
        })
        public ResponseEntity<ApiResponse<Page<DocumentResponse>>> listDocuments(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID projectId,
                        @Parameter(description = "Filter by status") @RequestParam(required = false) DocumentStatus status,
                        @Parameter(description = "Filter by type") @RequestParam(required = false) DocumentType type,
                        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
                        @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
                        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

                Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending()
                                : Sort.by(sortBy).descending();
                Pageable pageable = PageRequest.of(page, Math.min(size, 50), sort);

                Page<DocumentResponse> documents = documentService.listDocuments(
                                projectId, principal.getUserId(), status, type, pageable);

                return ResponseEntity.ok(ApiResponse.success(documents, "Documents retrieved successfully"));
        }

        @GetMapping("/all")
        @Operation(summary = "List all project documents", description = "Returns all documents for a project as a flat list (no content)")
        public ResponseEntity<ApiResponse<List<DocumentResponse>>> listAllDocuments(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID projectId) {

                List<DocumentResponse> documents = documentService.listAllDocuments(
                                projectId, principal.getUserId());

                return ResponseEntity.ok(ApiResponse.success(documents, "Documents retrieved successfully"));
        }

        @PostMapping
        @Operation(summary = "Create a document placeholder", description = "Creates a new document in PENDING status. One document per type per project.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Document created successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Document of this type already exists")
        })
        public ResponseEntity<ApiResponse<DocumentResponse>> createDocument(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID projectId,
                        @Valid @RequestBody CreateDocumentRequest request) {
                DocumentResponse document = documentService.createDocument(
                                projectId, principal.getUserId(), request);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(document, "Document created successfully", 201));
        }

        @GetMapping("/{documentId}")
        @Operation(summary = "Get document with content", description = "Returns a single document including its full Markdown content")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
        })
        public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID projectId,
                        @PathVariable UUID documentId) {
                DocumentResponse document = documentService.getDocument(
                                projectId, documentId, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(document, "Document retrieved successfully"));
        }

        @PutMapping("/{documentId}")
        @Operation(summary = "Update document", description = "Updates document title and/or content. Version is auto-incremented on content changes.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
        })
        public ResponseEntity<ApiResponse<DocumentResponse>> updateDocument(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID projectId,
                        @PathVariable UUID documentId,
                        @Valid @RequestBody UpdateDocumentRequest request) {
                DocumentResponse document = documentService.updateDocument(
                                projectId, documentId, principal.getUserId(), request);
                return ResponseEntity.ok(ApiResponse.success(document, "Document updated successfully"));
        }

        @DeleteMapping("/{documentId}")
        @Operation(summary = "Delete document", description = "Permanently deletes a document from the project")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document deleted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
        })
        public ResponseEntity<ApiResponse<Void>> deleteDocument(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID projectId,
                        @PathVariable UUID documentId) {
                documentService.deleteDocument(projectId, documentId, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(null, "Document deleted successfully"));
        }
}
