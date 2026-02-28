package com.velocira.backend.auth.controller;

import com.velocira.backend.auth.dto.*;
import com.velocira.backend.auth.exceptions.RateLimitExceededException;
import com.velocira.backend.auth.exceptions.UserNotFoundException;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.auth.security.AuthenticatedUser;
import com.velocira.backend.auth.service.AuthService;
import com.velocira.backend.common.dto.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for all authentication endpoints.
 *
 * <p>
 * API versioning: all endpoints are prefixed with {@code /v1/auth}.
 * Rate limiting is applied per IP address using Bucket4j.
 * </p>
 *
 * <p>
 * Endpoints:
 * </p>
 * <ul>
 * <li>{@code POST /register} — Register a new user</li>
 * <li>{@code POST /login} — Log in with email/password</li>
 * <li>{@code POST /google} — Log in with Google OAuth2</li>
 * <li>{@code POST /verify-email} — Verify email with OTP</li>
 * <li>{@code POST /resend-otp} — Resend verification OTP</li>
 * <li>{@code POST /forgot-password} — Request password reset OTP</li>
 * <li>{@code POST /reset-password} — Reset password with OTP</li>
 * <li>{@code POST /refresh} — Refresh access token</li>
 * <li>{@code POST /logout} — Logout (single device)</li>
 * <li>{@code POST /logout-all} — Logout (all devices)</li>
 * <li>{@code GET /me} — Get current user profile</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and session management APIs")
public class AuthController {

        private final AuthService authService;
        private final UserRepository userRepository;

        /** In-memory rate limit buckets keyed by IP address. */
        private final Map<String, Bucket> rateLimitBuckets = new ConcurrentHashMap<>();

        @Value("${velocira.rate-limit.auth.capacity}")
        private int rateLimitCapacity;

        @Value("${velocira.rate-limit.auth.refill-tokens}")
        private int rateLimitRefillTokens;

        @Value("${velocira.rate-limit.auth.refill-duration-minutes}")
        private int rateLimitRefillMinutes;

        // ======================== Registration ========================

        /**
         * Registers a new user with email and password.
         *
         * @param request     the registration request
         * @param httpRequest the HTTP request (for rate limiting)
         * @return 201 Created with the new user details
         */
        @PostMapping("/register")
        @Operation(summary = "Register a new user", description = "Creates a new user account and sends email verification OTP")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already exists"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        public ResponseEntity<ApiResponse<UserDto>> register(
                        @Valid @RequestBody RegisterRequest request,
                        HttpServletRequest httpRequest) {
                consumeRateLimit(httpRequest);
                UserDto user = authService.register(request);
                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(ApiResponse.success(user,
                                                "Registration successful. Please check your email for the verification code.",
                                                201));
        }

        // ======================== Login ========================

        /**
         * Authenticates a user with email and password.
         *
         * @param request     the login request
         * @param httpRequest the HTTP request (for device info and rate limiting)
         * @return 200 OK with access and refresh tokens
         */
        @PostMapping("/login")
        @Operation(summary = "Log in with email and password", description = "Authenticates user and returns JWT tokens")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Email not verified or account locked"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        public ResponseEntity<ApiResponse<AuthResponse>> login(
                        @Valid @RequestBody LoginRequest request,
                        HttpServletRequest httpRequest) {
                consumeRateLimit(httpRequest);
                AuthResponse response = authService.login(
                                request, getDeviceInfo(httpRequest), getClientIp(httpRequest));
                return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
        }

        // ======================== Google OAuth2 ========================

        /**
         * Authenticates or registers a user via Google OAuth2.
         *
         * @param request     the Google login request with ID token
         * @param httpRequest the HTTP request
         * @return 200 OK with access and refresh tokens
         */
        @PostMapping("/google")
        @Operation(summary = "Log in with Google", description = "Validates Google ID token and issues JWT tokens")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Google login successful"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid Google token"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(
                        @Valid @RequestBody GoogleLoginRequest request,
                        HttpServletRequest httpRequest) {
                consumeRateLimit(httpRequest);
                AuthResponse response = authService.googleLogin(
                                request, getDeviceInfo(httpRequest), getClientIp(httpRequest));
                return ResponseEntity.ok(ApiResponse.success(response, "Google login successful"));
        }

        // ======================== Email Verification ========================

        /**
         * Verifies a user's email address using OTP.
         *
         * @param request     the verification request with email and OTP
         * @param httpRequest the HTTP request
         * @return 200 OK on successful verification
         */
        @PostMapping("/verify-email")
        @Operation(summary = "Verify email address", description = "Validates OTP and marks email as verified")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired OTP"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many attempts")
        })
        public ResponseEntity<ApiResponse<Void>> verifyEmail(
                        @Valid @RequestBody VerifyOtpRequest request,
                        HttpServletRequest httpRequest) {
                consumeRateLimit(httpRequest);
                authService.verifyEmail(request);
                return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully"));
        }

        /**
         * Resends the email verification OTP.
         *
         * @param request     the resend request with email
         * @param httpRequest the HTTP request
         * @return 200 OK
         */
        @PostMapping("/resend-otp")
        @Operation(summary = "Resend verification OTP", description = "Generates and sends a new email verification code")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP sent successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        public ResponseEntity<ApiResponse<Void>> resendOtp(
                        @Valid @RequestBody ResendOtpRequest request,
                        HttpServletRequest httpRequest) {
                consumeRateLimit(httpRequest);
                authService.resendVerificationOtp(request);
                return ResponseEntity.ok(ApiResponse.success(null,
                                "If the email is registered and unverified, a new verification code has been sent."));
        }

        // ======================== Password Reset ========================

        /**
         * Initiates the forgot-password flow.
         *
         * @param request     the forgot password request with email
         * @param httpRequest the HTTP request
         * @return 200 OK (always, to prevent email enumeration)
         */
        @PostMapping("/forgot-password")
        @Operation(summary = "Forgot password", description = "Sends a password reset OTP to the registered email")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "If the email is registered, a reset code has been sent"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        public ResponseEntity<ApiResponse<Void>> forgotPassword(
                        @Valid @RequestBody ForgotPasswordRequest request,
                        HttpServletRequest httpRequest) {
                consumeRateLimit(httpRequest);
                authService.forgotPassword(request);
                return ResponseEntity.ok(ApiResponse.success(null,
                                "If the email is registered, a password reset code has been sent."));
        }

        /**
         * Resets a user's password using OTP.
         *
         * @param request     the reset request with email, OTP, and new password
         * @param httpRequest the HTTP request
         * @return 200 OK on successful reset
         */
        @PostMapping("/reset-password")
        @Operation(summary = "Reset password", description = "Validates OTP and updates password. Revokes all sessions.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid OTP or validation failed"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
        public ResponseEntity<ApiResponse<Void>> resetPassword(
                        @Valid @RequestBody ResetPasswordRequest request,
                        HttpServletRequest httpRequest) {
                consumeRateLimit(httpRequest);
                authService.resetPassword(request);
                return ResponseEntity.ok(ApiResponse.success(null,
                                "Password reset successfully. Please log in with your new password."));
        }

        // ======================== Token Management ========================

        /**
         * Refreshes an access token using a valid refresh token.
         *
         * @param request     the refresh request
         * @param httpRequest the HTTP request
         * @return 200 OK with new tokens
         */
        @PostMapping("/refresh")
        @Operation(summary = "Refresh access token", description = "Exchanges a refresh token for new access and refresh tokens (rotation)")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tokens refreshed successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
        })
        public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
                        @Valid @RequestBody RefreshTokenRequest request,
                        HttpServletRequest httpRequest) {
                AuthResponse response = authService.refreshToken(
                                request, getDeviceInfo(httpRequest), getClientIp(httpRequest));
                return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
        }

        /**
         * Logs out by revoking the provided refresh token (single device).
         *
         * @param request the logout request
         * @return 200 OK
         */
        @PostMapping("/logout")
        @Operation(summary = "Logout (single device)", description = "Revokes the provided refresh token")
        @SecurityRequirement(name = "bearerAuth")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid token")
        })
        public ResponseEntity<ApiResponse<Void>> logout(
                        @Valid @RequestBody LogoutRequest request) {
                authService.logout(request);
                return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
        }

        /**
         * Logs out from all devices by revoking all refresh tokens.
         *
         * @param principal the currently authenticated user
         * @return 200 OK
         */
        @PostMapping("/logout-all")
        @Operation(summary = "Logout (all devices)", description = "Revokes all refresh tokens for the current user")
        @SecurityRequirement(name = "bearerAuth")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All sessions terminated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public ResponseEntity<ApiResponse<Void>> logoutAll(
                        @AuthenticationPrincipal AuthenticatedUser principal) {
                UserEntity user = userRepository.findById(principal.getUserId())
                                .orElseThrow(() -> new UserNotFoundException(principal.getEmail()));
                authService.logoutAll(user);
                return ResponseEntity.ok(ApiResponse.success(null, "All sessions have been terminated"));
        }

        // ======================== User Profile ========================

        /**
         * Returns the current authenticated user's profile.
         *
         * @param principal the currently authenticated user
         * @return 200 OK with user details
         */
        @GetMapping("/me")
        @Operation(summary = "Get current user", description = "Returns the authenticated user's profile information")
        @SecurityRequirement(name = "bearerAuth")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile retrieved"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
        })
        public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(
                        @AuthenticationPrincipal AuthenticatedUser principal) {
                UserEntity user = userRepository.findById(principal.getUserId())
                                .orElseThrow(() -> new UserNotFoundException(principal.getEmail()));
                return ResponseEntity.ok(ApiResponse.success(UserMapper.toDto(user), "User profile retrieved"));
        }

        // ======================== Rate Limiting ========================

        /**
         * Consumes a rate limit token for the requesting IP.
         *
         * @param request the HTTP request
         * @throws RateLimitExceededException if the rate limit is exceeded
         */
        private void consumeRateLimit(HttpServletRequest request) {
                String ip = getClientIp(request);
                Bucket bucket = rateLimitBuckets.computeIfAbsent(ip, this::createBucket);
                if (!bucket.tryConsume(1)) {
                        log.warn("Rate limit exceeded for IP [{}]", ip);
                        throw new RateLimitExceededException();
                }
        }

        /**
         * Creates a new rate limit bucket for an IP address.
         *
         * @param ip the IP address
         * @return a new Bucket4j bucket
         */
        @SuppressWarnings("deprecation")
        private Bucket createBucket(String ip) {
                Bandwidth limit = Bandwidth.classic(
                                rateLimitCapacity,
                                Refill.greedy(rateLimitRefillTokens, Duration.ofMinutes(rateLimitRefillMinutes)));
                return Bucket.builder().addLimit(limit).build();
        }

        /**
         * Extracts the client's real IP address, respecting proxy headers.
         *
         * @param request the HTTP request
         * @return the client IP address
         */
        private String getClientIp(HttpServletRequest request) {
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                        return xForwardedFor.split(",")[0].trim();
                }
                String xRealIp = request.getHeader("X-Real-IP");
                if (xRealIp != null && !xRealIp.isBlank()) {
                        return xRealIp;
                }
                return request.getRemoteAddr();
        }

        /**
         * Extracts device info from the User-Agent header.
         *
         * @param request the HTTP request
         * @return the device info string
         */
        private String getDeviceInfo(HttpServletRequest request) {
                String userAgent = request.getHeader("User-Agent");
                return userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 512)) : "unknown";
        }
}
