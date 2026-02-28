package com.velocira.backend.project.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.project.dto.CreateProjectRequest;
import com.velocira.backend.project.dto.ProjectResponse;
import com.velocira.backend.project.dto.UpdateProjectRequest;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.service.ProjectService;
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

/**
 * REST controller for project CRUD operations.
 *
 * <p>
 * All endpoints require authentication and enforce ownership checks.
 * </p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>{@code GET    /v1/projects} — List user's projects (paginated,
 * filterable)</li>
 * <li>{@code POST   /v1/projects} — Create a new project</li>
 * <li>{@code GET    /v1/projects/{id}} — Get project by ID</li>
 * <li>{@code PUT    /v1/projects/{id}} — Update project</li>
 * <li>{@code DELETE /v1/projects/{id}} — Delete project</li>
 * <li>{@code POST   /v1/projects/{id}/duplicate} — Duplicate project</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Projects", description = "Project management endpoints")
public class ProjectController {

        private final ProjectService projectService;

        @GetMapping
        @Operation(summary = "List user's projects", description = "Returns paginated projects owned by the authenticated user with optional filters")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Projects retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public ResponseEntity<ApiResponse<Page<ProjectResponse>>> listProjects(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @Parameter(description = "Filter by project status") @RequestParam(required = false) ProjectStatus status,
                        @Parameter(description = "Search by project name") @RequestParam(required = false) String search,
                        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Page size") @RequestParam(defaultValue = "12") int size,
                        @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
                        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

                Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending()
                                : Sort.by(sortBy).descending();
                Pageable pageable = PageRequest.of(page, Math.min(size, 50), sort);

                Page<ProjectResponse> projects = projectService.listUserProjects(
                                principal.getUserId(), status, search, pageable);

                return ResponseEntity.ok(ApiResponse.success(projects, "Projects retrieved successfully"));
        }

        @PostMapping
        @Operation(summary = "Create a new project", description = "Creates a new project owned by the authenticated user in DRAFT status")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Project created successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @Valid @RequestBody CreateProjectRequest request) {
                ProjectResponse project = projectService.createProject(principal.getUserId(), request);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(project, "Project created successfully", 201));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Get project by ID", description = "Returns a specific project. User must be the owner.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found")
        })
        public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable java.util.UUID id) {
                ProjectResponse project = projectService.getProject(id, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(project, "Project retrieved successfully"));
        }

        @PutMapping("/{id}")
        @Operation(summary = "Update project", description = "Updates project fields. Only non-null fields are applied. User must be the owner.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found")
        })
        public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable java.util.UUID id,
                        @Valid @RequestBody UpdateProjectRequest request) {
                ProjectResponse project = projectService.updateProject(id, principal.getUserId(), request);
                return ResponseEntity.ok(ApiResponse.success(project, "Project updated successfully"));
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "Delete project", description = "Permanently deletes a project and all associated documents. User must be the owner.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project deleted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found")
        })
        public ResponseEntity<ApiResponse<Void>> deleteProject(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable java.util.UUID id) {
                projectService.deleteProject(id, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(null, "Project deleted successfully"));
        }

        @PostMapping("/{id}/duplicate")
        @Operation(summary = "Duplicate project", description = "Creates a copy of the project in DRAFT status (without documents)")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Project duplicated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found")
        })
        public ResponseEntity<ApiResponse<ProjectResponse>> duplicateProject(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable java.util.UUID id) {
                ProjectResponse project = projectService.duplicateProject(id, principal.getUserId());
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(project, "Project duplicated successfully", 201));
        }
}
