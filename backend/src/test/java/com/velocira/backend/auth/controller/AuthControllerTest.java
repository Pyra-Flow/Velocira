package com.velocira.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocira.backend.auth.dto.*;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.auth.security.JwtAccessDeniedHandler;
import com.velocira.backend.auth.security.JwtAuthenticationEntryPoint;
import com.velocira.backend.auth.security.JwtAuthenticationFilter;
import com.velocira.backend.auth.security.JwtProvider;
import com.velocira.backend.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link AuthController} using MockMvc.
 *
 * <p>
 * Tests endpoint routing, validation, and response format.
 * Security filters are disabled ({@code addFilters = false}) to isolate
 * controller logic.
 * All security beans are mocked to prevent context loading issues.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // ======================== Registration Endpoints ========================

    @Nested
    @DisplayName("POST /v1/auth/register")
    class RegisterEndpoint {

        @Test
        @DisplayName("Should return 201 for valid registration")
        void shouldReturn201ForValidRegistration() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setFullName("John Doe");
            request.setEmail("john@velocira.com");
            request.setPassword("StrongP@ss1");
            request.setUniversityName("MIT");

            UserDto userDto = UserDto.builder()
                    .id(UUID.randomUUID())
                    .fullName("John Doe")
                    .email("john@velocira.com")
                    .role(Role.USER)
                    .emailVerified(false)
                    .build();

            when(authService.register(any(RegisterRequest.class))).thenReturn(userDto);

            mockMvc.perform(post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value("john@velocira.com"));
        }

        @Test
        @DisplayName("Should return 400 for missing required fields")
        void shouldReturn400ForMissingFields() throws Exception {
            RegisterRequest request = new RegisterRequest();
            // Missing all required fields

            mockMvc.perform(post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for invalid email format")
        void shouldReturn400ForInvalidEmail() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setFullName("John Doe");
            request.setEmail("not-an-email");
            request.setPassword("StrongP@ss1");

            mockMvc.perform(post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ======================== Login Endpoints ========================

    @Nested
    @DisplayName("POST /v1/auth/login")
    class LoginEndpoint {

        @Test
        @DisplayName("Should return 200 for valid login")
        void shouldReturn200ForValidLogin() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setEmail("john@velocira.com");
            request.setPassword("StrongP@ss1");

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken("jwt-access-token")
                    .refreshToken("refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(900)
                    .user(UserDto.builder()
                            .id(UUID.randomUUID())
                            .email("john@velocira.com")
                            .fullName("John Doe")
                            .role(Role.USER)
                            .emailVerified(true)
                            .build())
                    .build();

            when(authService.login(any(LoginRequest.class), anyString(), anyString()))
                    .thenReturn(authResponse);

            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("jwt-access-token"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
        }
    }

    // ======================== Forgot Password Endpoints ========================

    @Nested
    @DisplayName("POST /v1/auth/forgot-password")
    class ForgotPasswordEndpoint {

        @Test
        @DisplayName("Should always return 200 (anti-enumeration)")
        void shouldAlwaysReturn200() throws Exception {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("any@velocira.com");

            mockMvc.perform(post("/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ======================== Refresh Token Endpoints ========================

    @Nested
    @DisplayName("POST /v1/auth/refresh")
    class RefreshEndpoint {

        @Test
        @DisplayName("Should return 200 for valid refresh")
        void shouldReturn200ForValidRefresh() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("valid-refresh-token");

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken("new-access-token")
                    .refreshToken("new-refresh-token")
                    .tokenType("Bearer")
                    .expiresIn(900)
                    .user(UserDto.builder()
                            .id(UUID.randomUUID())
                            .email("john@velocira.com")
                            .fullName("John Doe")
                            .role(Role.USER)
                            .emailVerified(true)
                            .build())
                    .build();

            when(authService.refreshToken(any(RefreshTokenRequest.class), anyString(), anyString()))
                    .thenReturn(authResponse);

            mockMvc.perform(post("/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
        }
    }

    // ======================== Verify Email Endpoints ========================

    @Nested
    @DisplayName("POST /v1/auth/verify-email")
    class VerifyEmailEndpoint {

        @Test
        @DisplayName("Should return 200 for valid OTP verification")
        void shouldReturn200ForValidOtp() throws Exception {
            VerifyOtpRequest request = new VerifyOtpRequest();
            request.setEmail("john@velocira.com");
            request.setOtp("123456");

            mockMvc.perform(post("/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
