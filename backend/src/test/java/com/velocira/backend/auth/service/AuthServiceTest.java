package com.velocira.backend.auth.service;

import com.velocira.backend.auth.dto.*;
import com.velocira.backend.auth.exceptions.*;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.auth.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private OtpService otpService;
    @Mock
    private GoogleOAuthService googleOAuthService;

    @InjectMocks
    private AuthService authService;

    private static final String DEVICE_INFO = "TestAgent/1.0";
    private static final String IP_ADDRESS = "127.0.0.1";

    // ======================== Registration ========================

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("Should register a new user successfully")
        void shouldRegisterNewUser() {
            RegisterRequest request = new RegisterRequest();
            request.setFullName("John Doe");
            request.setEmail("john@velocira.com");
            request.setPassword("StrongP@ss1");
            request.setUniversityName("MIT");

            when(userRepository.existsByEmailIgnoreCase("john@velocira.com")).thenReturn(false);
            when(passwordEncoder.encode("StrongP@ss1")).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
                UserEntity user = invocation.getArgument(0);
                user.setId(UUID.randomUUID());
                return user;
            });

            UserDto result = authService.register(request);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john@velocira.com");
            assertThat(result.getFullName()).isEqualTo("John Doe");

            verify(userRepository).save(any(UserEntity.class));
            verify(otpService).generateAndSendOtp(any(UserEntity.class), any());
        }

        @Test
        @DisplayName("Should throw EmailAlreadyExistsException for duplicate email")
        void shouldThrowOnDuplicateEmail() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("existing@velocira.com");
            request.setPassword("StrongP@ss1");
            request.setFullName("Test User");

            when(userRepository.existsByEmailIgnoreCase("existing@velocira.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(EmailAlreadyExistsException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // ======================== Login ========================

    @Nested
    @DisplayName("Login")
    class Login {

        private UserEntity verifiedUser;

        @BeforeEach
        void setUp() {
            verifiedUser = new UserEntity();
            verifiedUser.setId(UUID.randomUUID());
            verifiedUser.setEmail("john@velocira.com");
            verifiedUser.setFullName("John Doe");
            verifiedUser.setPassword("$2a$12$hashed");
            verifiedUser.setRole(Role.USER);
            verifiedUser.setAuthProvider(AuthProvider.LOCAL);
            verifiedUser.setEmailVerified(true);
            verifiedUser.setAccountLocked(false);
            verifiedUser.setEnabled(true);
        }

        @Test
        @DisplayName("Should login successfully with correct credentials")
        void shouldLoginSuccessfully() {
            LoginRequest request = new LoginRequest();
            request.setEmail("john@velocira.com");
            request.setPassword("StrongP@ss1");

            when(userRepository.findByEmailIgnoreCase("john@velocira.com"))
                    .thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("StrongP@ss1", "$2a$12$hashed")).thenReturn(true);
            when(jwtProvider.generateAccessToken(any(UUID.class), eq("john@velocira.com"), eq("USER")))
                    .thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(any(UserEntity.class), eq(DEVICE_INFO), eq(IP_ADDRESS)))
                    .thenReturn("raw-refresh-token");
            when(jwtProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
            when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AuthResponse response = authService.login(request, DEVICE_INFO, IP_ADDRESS);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
            assertThat(response.getUser().getEmail()).isEqualTo("john@velocira.com");

            verify(userRepository).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException for wrong password")
        void shouldThrowOnWrongPassword() {
            LoginRequest request = new LoginRequest();
            request.setEmail("john@velocira.com");
            request.setPassword("WrongPass1!");

            when(userRepository.findByEmailIgnoreCase("john@velocira.com"))
                    .thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("WrongPass1!", "$2a$12$hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request, DEVICE_INFO, IP_ADDRESS))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException for non-existent user")
        void shouldThrowOnNonExistentUser() {
            LoginRequest request = new LoginRequest();
            request.setEmail("nonexistent@velocira.com");
            request.setPassword("StrongP@ss1");

            when(userRepository.findByEmailIgnoreCase("nonexistent@velocira.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request, DEVICE_INFO, IP_ADDRESS))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("Should throw EmailNotVerifiedException for unverified email")
        void shouldThrowOnUnverifiedEmail() {
            verifiedUser.setEmailVerified(false);

            LoginRequest request = new LoginRequest();
            request.setEmail("john@velocira.com");
            request.setPassword("StrongP@ss1");

            when(userRepository.findByEmailIgnoreCase("john@velocira.com"))
                    .thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("StrongP@ss1", "$2a$12$hashed")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(request, DEVICE_INFO, IP_ADDRESS))
                    .isInstanceOf(EmailNotVerifiedException.class);
        }

        @Test
        @DisplayName("Should throw AccountLockedException for locked account")
        void shouldThrowOnLockedAccount() {
            verifiedUser.setAccountLocked(true);

            LoginRequest request = new LoginRequest();
            request.setEmail("john@velocira.com");
            request.setPassword("StrongP@ss1");

            when(userRepository.findByEmailIgnoreCase("john@velocira.com"))
                    .thenReturn(Optional.of(verifiedUser));
            when(passwordEncoder.matches("StrongP@ss1", "$2a$12$hashed")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(request, DEVICE_INFO, IP_ADDRESS))
                    .isInstanceOf(AccountLockedException.class);
        }
    }

    // ======================== Logout ========================

    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        @DisplayName("Should logout by revoking refresh token")
        void shouldLogoutSuccessfully() {
            LogoutRequest request = new LogoutRequest();
            request.setRefreshToken("some-refresh-token");

            authService.logout(request);

            verify(refreshTokenService).revokeToken(eq("some-refresh-token"));
        }

        @Test
        @DisplayName("Should logout all sessions for a user")
        void shouldLogoutAllSessions() {
            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setEmail("john@velocira.com");

            authService.logoutAll(user);

            verify(refreshTokenService).revokeAllTokens(eq(user));
        }
    }

    // ======================== Forgot Password ========================

    @Nested
    @DisplayName("Forgot Password")
    class ForgotPassword {

        @Test
        @DisplayName("Should silently succeed for non-existent email (anti-enumeration)")
        void shouldNotRevealNonExistentEmail() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("nonexistent@velocira.com");

            when(userRepository.findByEmailIgnoreCase("nonexistent@velocira.com"))
                    .thenReturn(Optional.empty());

            // Should NOT throw — anti-enumeration
            assertThatCode(() -> authService.forgotPassword(request))
                    .doesNotThrowAnyException();

            verify(otpService, never()).generateAndSendOtp(any(), any());
        }

        @Test
        @DisplayName("Should send OTP for existing local user")
        void shouldSendOtpForExistingUser() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("john@velocira.com");

            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setEmail("john@velocira.com");
            user.setAuthProvider(AuthProvider.LOCAL);

            when(userRepository.findByEmailIgnoreCase("john@velocira.com"))
                    .thenReturn(Optional.of(user));

            authService.forgotPassword(request);

            verify(otpService).generateAndSendOtp(eq(user), any());
        }

        @Test
        @DisplayName("Should skip OTP for OAuth user (anti-enumeration)")
        void shouldSkipOtpForOAuthUser() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("google@velocira.com");

            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setEmail("google@velocira.com");
            user.setAuthProvider(AuthProvider.GOOGLE);

            when(userRepository.findByEmailIgnoreCase("google@velocira.com"))
                    .thenReturn(Optional.of(user));

            // Should NOT throw — silently skip OAuth users
            assertThatCode(() -> authService.forgotPassword(request))
                    .doesNotThrowAnyException();

            verify(otpService, never()).generateAndSendOtp(any(), any());
        }
    }

    // ======================== Refresh Token ========================

    @Nested
    @DisplayName("Refresh Token")
    class RefreshToken {

        @Test
        @DisplayName("Should refresh token successfully")
        void shouldRefreshTokenSuccessfully() {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("old-refresh-token");

            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setEmail("john@velocira.com");
            user.setFullName("John Doe");
            user.setRole(Role.USER);

            RefreshTokenService.RotationResult rotationResult = new RefreshTokenService.RotationResult("new-raw-token",
                    user);
            when(refreshTokenService.rotateRefreshToken(
                    eq("old-refresh-token"), eq(DEVICE_INFO), eq(IP_ADDRESS)))
                    .thenReturn(rotationResult);
            when(jwtProvider.generateAccessToken(user.getId(), "john@velocira.com", "USER"))
                    .thenReturn("new-access-token");
            when(jwtProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);

            AuthResponse response = authService.refreshToken(request, DEVICE_INFO, IP_ADDRESS);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-raw-token");
            assertThat(response.getUser().getEmail()).isEqualTo("john@velocira.com");
        }
    }
}
