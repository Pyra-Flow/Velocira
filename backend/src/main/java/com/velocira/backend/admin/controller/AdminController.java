package com.velocira.backend.admin.controller;

import com.velocira.backend.admin.dto.AdminAnalyticsResponse;
import com.velocira.backend.admin.dto.AdminUserResponse;
import com.velocira.backend.admin.dto.ChangeRoleRequest;
import com.velocira.backend.admin.service.AdminService;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.model.AuditLogEntity;
import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for admin operations.
 *
 * <p>
 * All endpoints require the {@code ROLE_ADMIN} authority. Unauthorized
 * access returns 403 Forbidden.
 * </p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>{@code GET    /v1/admin/users} — List all users (paginated)</li>
 * <li>{@code GET    /v1/admin/users/{id}} — Get user details</li>
 * <li>{@code POST   /v1/admin/users/{id}/suspend} — Suspend user</li>
 * <li>{@code POST   /v1/admin/users/{id}/activate} — Activate user</li>
 * <li>{@code PUT    /v1/admin/users/{id}/role} — Change user role</li>
 * <li>{@code DELETE /v1/admin/users/{id}} — Delete user</li>
 * <li>{@code GET    /v1/admin/analytics} — Platform analytics</li>
 * <li>{@code GET    /v1/admin/audit-logs} — Audit logs (paginated)</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "Admin panel operations (ADMIN role required)")
public class AdminController {

        private final AdminService adminService;

        @GetMapping("/users")
        @Operation(summary = "List all users", description = "Returns paginated list of all platform users with optional search")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Admin access required")
        })
        public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
                        @Parameter(description = "Search by name or email") @RequestParam(required = false) String search,
                        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
                        @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
                        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction) {

                Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending()
                                : Sort.by(sortBy).descending();
                Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);

                Page<AdminUserResponse> users = adminService.listUsers(search, pageable);
                return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved successfully"));
        }

        @GetMapping("/users/{id}")
        @Operation(summary = "Get user details", description = "Returns full details for a specific user")
        public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable UUID id) {
                AdminUserResponse user = adminService.getUser(id);
                return ResponseEntity.ok(ApiResponse.success(user, "User retrieved successfully"));
        }

        @PostMapping("/users/{id}/suspend")
        @Operation(summary = "Suspend user", description = "Locks a user account, preventing login")
        public ResponseEntity<ApiResponse<Void>> suspendUser(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID id) {
                adminService.suspendUser(id, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(null, "User suspended successfully"));
        }

        @PostMapping("/users/{id}/activate")
        @Operation(summary = "Activate user", description = "Unlocks a suspended user account")
        public ResponseEntity<ApiResponse<Void>> activateUser(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID id) {
                adminService.activateUser(id, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(null, "User activated successfully"));
        }

        @PutMapping("/users/{id}/role")
        @Operation(summary = "Change user role", description = "Updates the role of a user")
        public ResponseEntity<ApiResponse<Void>> changeUserRole(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID id,
                        @Valid @RequestBody ChangeRoleRequest request) {
                adminService.changeUserRole(id, request.getRole(), principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(null, "User role updated successfully"));
        }

        @DeleteMapping("/users/{id}")
        @Operation(summary = "Delete user", description = "Permanently deletes a user and all their data")
        public ResponseEntity<ApiResponse<Void>> deleteUser(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @PathVariable UUID id) {
                adminService.deleteUser(id, principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
        }

        @GetMapping("/analytics")
        @Operation(summary = "Platform analytics", description = "Returns platform-wide analytics for the admin dashboard")
        public ResponseEntity<ApiResponse<AdminAnalyticsResponse>> getAnalytics() {
                AdminAnalyticsResponse analytics = adminService.getAnalytics();
                return ResponseEntity.ok(ApiResponse.success(analytics, "Analytics retrieved successfully"));
        }

        @GetMapping("/audit-logs")
        @Operation(summary = "View audit logs", description = "Returns paginated audit logs with optional filters")
        public ResponseEntity<ApiResponse<Page<AuditLogEntity>>> getAuditLogs(
                        @Parameter(description = "Filter by user ID") @RequestParam(required = false) UUID userId,
                        @Parameter(description = "Filter by action") @RequestParam(required = false) AuditAction action,
                        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
                        @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {

                Pageable pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
                Page<AuditLogEntity> logs = adminService.getAuditLogs(userId, action, pageable);
                return ResponseEntity.ok(ApiResponse.success(logs, "Audit logs retrieved successfully"));
        }
}
