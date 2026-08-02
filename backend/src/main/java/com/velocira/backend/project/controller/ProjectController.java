package com.velocira.backend.project.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.project.dto.CreateProjectRequest;
import com.velocira.backend.project.dto.ProjectResponse;
import com.velocira.backend.project.dto.UpdateProjectRequest;
import com.velocira.backend.project.dto.UpdateProjectStatusRequest;
import com.velocira.backend.project.model.ProjectStatus;
import com.velocira.backend.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

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
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Projects", description = "Project management endpoints")
public class ProjectController {

        private final ProjectService projectService;
        private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "updatedAt", "name", "status");

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
                        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
                        @Parameter(description = "Page size") @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size,
                        @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
                        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

                String safeSortField = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
                Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(safeSortField).ascending()
                                : Sort.by(safeSortField).descending();
                Pageable pageable = PageRequest.of(page, size, sort);

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

        @PatchMapping("/{id}/status")
        @Operation(summary = "Transition project lifecycle", description = "Moves a project through an allowed lifecycle transition. Use the archive and restore endpoints for archival.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project status updated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Lifecycle transition is not allowed")
        })
        public ResponseEntity<ApiResponse<ProjectResponse>> updateProjectStatus(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable java.util.UUID id,
                        @Valid @RequestBody UpdateProjectStatusRequest request) {
                ProjectResponse project = projectService.transitionProjectStatus(id, principal.getUserId(), request.getStatus());
                return ResponseEntity.ok(ApiResponse.success(project, "Project status updated successfully"));
        }

        @PostMapping("/{id}/archive")
        @Operation(summary = "Archive project", description = "Soft-archives a project. It remains available only through the archived filter and can be restored.")
        public ResponseEntity<ApiResponse<ProjectResponse>> archiveProject(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable java.util.UUID id) {
                ProjectResponse project = projectService.archiveProject(id, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(project, "Project archived successfully"));
        }

        @PostMapping("/{id}/restore")
        @Operation(summary = "Restore archived project", description = "Restores a project to the lifecycle state it had before archival.")
        public ResponseEntity<ApiResponse<ProjectResponse>> restoreProject(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable java.util.UUID id) {
                ProjectResponse project = projectService.restoreProject(id, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(project, "Project restored successfully"));
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
