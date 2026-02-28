package com.velocira.backend.user.controller;

import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.common.dto.ApiResponse;
import com.velocira.backend.user.dto.ChangePasswordRequest;
import com.velocira.backend.user.dto.UpdateProfileRequest;
import com.velocira.backend.user.dto.UserProfileResponse;
import com.velocira.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user profile management.
 *
 * <p>
 * All endpoints require an authenticated user (JWT Bearer token).
 * </p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 * <li>{@code GET /v1/users/me} — Get current user's full profile</li>
 * <li>{@code PUT /v1/users/me} — Update profile</li>
 * <li>{@code PUT /v1/users/me/password} — Change password</li>
 * <li>{@code DELETE /v1/users/me} — Delete account</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Profile", description = "User profile management endpoints")
public class UserController {

        private final UserService userService;

        @GetMapping("/me")
        @Operation(summary = "Get current user's full profile", description = "Returns the authenticated user's complete profile including project count")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
                        @AuthenticationPrincipal AuthenticatedUser principal) {
                UserProfileResponse profile = userService.getProfile(principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(profile, "Profile retrieved successfully"));
        }

        @PutMapping("/me")
        @Operation(summary = "Update profile", description = "Updates the authenticated user's profile. Only non-null fields are applied.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @Valid @RequestBody UpdateProfileRequest request) {
                UserProfileResponse profile = userService.updateProfile(principal.getUserId(), request);
                return ResponseEntity.ok(ApiResponse.success(profile, "Profile updated successfully"));
        }

        @PutMapping("/me/password")
        @Operation(summary = "Change password", description = "Changes the password for LOCAL auth users. Revokes all active sessions.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid current password or validation failed"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public ResponseEntity<ApiResponse<Void>> changePassword(
                        @AuthenticationPrincipal AuthenticatedUser principal,
                        @Valid @RequestBody ChangePasswordRequest request) {
                userService.changePassword(principal.getUserId(), request);
                return ResponseEntity.ok(ApiResponse.success(null,
                                "Password changed successfully. All sessions have been revoked. Please log in again."));
        }

        @DeleteMapping("/me")
        @Operation(summary = "Delete account", description = "Permanently deletes the authenticated user's account and all associated data")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public ResponseEntity<ApiResponse<Void>> deleteAccount(
                        @AuthenticationPrincipal AuthenticatedUser principal) {
                userService.deleteAccount(principal.getUserId());
                return ResponseEntity.ok(ApiResponse.success(null, "Account deleted successfully"));
        }
}
