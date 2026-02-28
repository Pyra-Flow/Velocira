package com.velocira.backend.auth.service;

import com.velocira.backend.auth.dto.*;
import com.velocira.backend.auth.exceptions.*;
import com.velocira.backend.auth.model.*;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.auth.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Core authentication service orchestrating all auth flows.
 *
 * <p>
 * This is the main entry point for all authentication operations:
 * </p>
 * <ul>
 * <li><strong>Register</strong> — creates a new account and sends email
 * verification OTP</li>
 * <li><strong>Login</strong> — validates credentials, checks email
 * verification, issues tokens</li>
 * <li><strong>Google OAuth2</strong> — validates Google ID token, creates/finds
 * user, issues tokens</li>
 * <li><strong>Email verification</strong> — validates OTP and marks email as
 * verified</li>
 * <li><strong>Forgot password</strong> — sends password reset OTP</li>
 * <li><strong>Reset password</strong> — validates OTP and updates password</li>
 * <li><strong>Refresh token</strong> — rotates refresh token and issues new
 * access token</li>
 * <li><strong>Logout</strong> — revokes refresh token (single device)</li>
 * <li><strong>Logout all</strong> — revokes all refresh tokens (all
 * devices)</li>
 * <li><strong>Resend OTP</strong> — generates and sends a new verification
 * OTP</li>
 * </ul>
 *
 * <p>
 * Transaction boundaries: each public method runs in a single transaction.
 * Read-only operations use {@code readOnly = true} for performance.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final GoogleOAuthService googleOAuthService;

    /**
     * Registers a new user with email and password.
     *
     * <p>
     * Flow:
     * </p>
     * <ol>
     * <li>Check if email is already registered</li>
     * <li>Create user with hashed password (email unverified)</li>
     * <li>Generate and send email verification OTP</li>
     * </ol>
     *
     * @param request the registration request DTO
     * @return a DTO representing the newly created user
     * @throws EmailAlreadyExistsException if the email is already in use
     */
    @Transactional
    public UserDto register(RegisterRequest request) {
        log.info("Processing registration for email [{}]", request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        UserEntity user = UserEntity.builder()
                .fullName(request.getFullName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .universityName(request.getUniversityName())
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: [{}]", user.getEmail());

        // Send verification OTP
        otpService.generateAndSendOtp(user, OtpType.EMAIL_VERIFICATION);

        return UserMapper.toDto(user);
    }

    /**
     * Authenticates a user with email and password.
     *
     * <p>
     * Validates credentials, checks email verification status and account state,
     * then issues access and refresh tokens.
     * </p>
     *
     * @param request    the login request DTO
     * @param deviceInfo client device information (User-Agent)
     * @param ipAddress  client IP address
     * @return an {@link AuthResponse} with tokens and user info
     * @throws InvalidCredentialsException if email/password don't match
     * @throws EmailNotVerifiedException   if the email hasn't been verified
     * @throws AccountLockedException      if the account is suspended
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo, String ipAddress) {
        log.info("Processing login for email [{}]", request.getEmail());

        UserEntity user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        // Check auth provider
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new InvalidCredentialsException(
                    "This account was registered via " + user.getAuthProvider() +
                            ". Please use that method to log in.");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        // Check account state
        validateAccountState(user);

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return buildAuthResponse(user, deviceInfo, ipAddress);
    }

    /**
     * Authenticates or registers a user via Google OAuth2.
     *
     * <p>
     * If the user doesn't exist, a new account is created with email auto-verified.
     * If the user exists with a LOCAL provider, the account is linked to Google
     * (provider updated, email verified) and logged in.
     * </p>
     *
     * @param request    the Google login request with ID token
     * @param deviceInfo client device information
     * @param ipAddress  client IP address
     * @return an {@link AuthResponse} with tokens and user info
     * @throws GoogleAuthenticationException if Google token verification fails
     */
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request, String deviceInfo, String ipAddress) {
        log.info("Processing Google OAuth2 login");

        GoogleOAuthService.GoogleUserInfo googleUser = googleOAuthService.verifyIdToken(request.getIdToken());

        UserEntity user = userRepository.findByEmailIgnoreCase(googleUser.email())
                .map(existingUser -> {
                    // User exists — if LOCAL, link Google provider
                    if (existingUser.getAuthProvider() == AuthProvider.LOCAL) {
                        log.info("Linking Google provider to existing LOCAL account [{}]", existingUser.getEmail());
                        existingUser.setAuthProvider(AuthProvider.GOOGLE);
                        existingUser.setEmailVerified(true);
                        return userRepository.save(existingUser);
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    // New user — create account
                    UserEntity newUser = UserEntity.builder()
                            .fullName(googleUser.name() != null ? googleUser.name() : "Google User")
                            .email(googleUser.email().toLowerCase().trim())
                            .authProvider(AuthProvider.GOOGLE)
                            .emailVerified(true) // Google already verified the email
                            .role(Role.USER)
                            .build();
                    return userRepository.save(newUser);
                });

        // Check account state
        validateAccountState(user);

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("Google OAuth2 login successful for [{}]", user.getEmail());
        return buildAuthResponse(user, deviceInfo, ipAddress);
    }

    /**
     * Verifies a user's email address using an OTP code.
     *
     * @param request the verification request containing email and OTP
     * @throws UserNotFoundException if the user is not found
     * @throws InvalidOtpException   if the OTP is invalid or expired
     */
    @Transactional
    public void verifyEmail(VerifyOtpRequest request) {
        log.info("Processing email verification for [{}]", request.getEmail());

        UserEntity user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException(request.getEmail()));

        if (user.isEmailVerified()) {
            log.info("Email already verified for user [{}]", user.getEmail());
            return; // Idempotent — no error
        }

        otpService.validateOtp(user, request.getOtp(), OtpType.EMAIL_VERIFICATION);
        userRepository.markEmailVerified(user.getId());

        log.info("Email verified successfully for user [{}]", user.getEmail());
    }

    /**
     * Resends the email verification OTP.
     *
     * @param request the resend request containing the email
     * @throws UserNotFoundException if the user is not found
     * @throws OtpRateLimitException if the resend rate limit is exceeded
     */
    @Transactional
    public void resendVerificationOtp(ResendOtpRequest request) {
        log.info("Processing OTP resend for [{}]", request.getEmail());

        UserEntity user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException(request.getEmail()));

        if (user.isEmailVerified()) {
            log.info("Email already verified for user [{}]. Skipping resend.", user.getEmail());
            return;
        }

        otpService.generateAndSendOtp(user, OtpType.EMAIL_VERIFICATION);
    }

    /**
     * Initiates the forgot-password flow by sending a password reset OTP.
     *
     * <p>
     * <strong>Security note:</strong> This method always returns success, even if
     * the email
     * doesn't exist. This prevents email enumeration attacks.
     * </p>
     *
     * @param request the forgot password request containing the email
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Processing forgot password for [{}]", request.getEmail());

        userRepository.findByEmailIgnoreCase(request.getEmail()).ifPresent(user -> {
            if (user.getAuthProvider() != AuthProvider.LOCAL) {
                log.info("Forgot password requested for OAuth user [{}]. Skipping.", user.getEmail());
                return;
            }
            otpService.generateAndSendOtp(user, OtpType.PASSWORD_RESET);
        });

        // Always return success to prevent email enumeration
    }

    /**
     * Resets a user's password after validating the OTP.
     *
     * <p>
     * After a successful reset, all existing refresh tokens are revoked
     * to force re-authentication on all devices.
     * </p>
     *
     * @param request the reset password request with email, OTP, and new password
     * @throws UserNotFoundException if the user is not found
     * @throws InvalidOtpException   if the OTP is invalid or expired
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Processing password reset for [{}]", request.getEmail());

        UserEntity user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException(request.getEmail()));

        // Validate OTP
        otpService.validateOtp(user, request.getOtp(), OtpType.PASSWORD_RESET);

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all sessions for security
        refreshTokenService.revokeAllTokens(user);

        log.info("Password reset successfully for user [{}]", user.getEmail());
    }

    /**
     * Refreshes an access token using a valid refresh token.
     *
     * <p>
     * Implements refresh token rotation: the old token is revoked and a new one is
     * issued.
     * If the old token was already revoked (potential theft), all sessions are
     * killed.
     * </p>
     *
     * @param request    the refresh token request
     * @param deviceInfo client device information
     * @param ipAddress  client IP address
     * @return an {@link AuthResponse} with new tokens
     * @throws InvalidTokenException if the refresh token is invalid
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String deviceInfo, String ipAddress) {
        log.debug("Processing token refresh");

        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(request.getRefreshToken(),
                deviceInfo, ipAddress);

        UserEntity user = result.user();
        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(result.newPlainToken())
                .expiresIn(jwtProvider.getAccessTokenExpirationSeconds())
                .user(UserMapper.toDto(user))
                .build();
    }

    /**
     * Logs out a user by revoking a specific refresh token (single device).
     *
     * @param request the logout request containing the refresh token
     */
    @Transactional
    public void logout(LogoutRequest request) {
        log.info("Processing single-device logout");
        refreshTokenService.revokeToken(request.getRefreshToken());
    }

    /**
     * Logs out a user from all devices by revoking all refresh tokens.
     *
     * @param user the user to log out
     */
    @Transactional
    public void logoutAll(UserEntity user) {
        log.info("Processing all-device logout for user [{}]", user.getEmail());
        refreshTokenService.revokeAllTokens(user);
    }

    // ======================== Private Helpers ========================

    /**
     * Validates that a user's account is in a valid state for authentication.
     *
     * @param user the user entity to validate
     * @throws EmailNotVerifiedException if email is not verified
     * @throws AccountLockedException    if the account is suspended
     */
    private void validateAccountState(UserEntity user) {
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        if (user.isAccountLocked() || !user.isEnabled()) {
            throw new AccountLockedException();
        }
    }

    /**
     * Builds an {@link AuthResponse} with access token, refresh token, and user
     * info.
     *
     * @param user       the authenticated user
     * @param deviceInfo client device information
     * @param ipAddress  client IP address
     * @return a complete authentication response
     */
    private AuthResponse buildAuthResponse(UserEntity user, String deviceInfo, String ipAddress) {
        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user, deviceInfo, ipAddress);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtProvider.getAccessTokenExpirationSeconds())
                .user(UserMapper.toDto(user))
                .build();
    }
}
