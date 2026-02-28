package com.velocira.backend.user.service;

import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.auth.exceptions.UserNotFoundException;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.auth.service.RefreshTokenService;
import com.velocira.backend.project.repository.ProjectRepository;
import com.velocira.backend.user.dto.ChangePasswordRequest;
import com.velocira.backend.user.dto.UpdateProfileRequest;
import com.velocira.backend.user.dto.UserProfileResponse;
import com.velocira.backend.user.exceptions.InvalidCurrentPasswordException;
import com.velocira.backend.user.exceptions.PasswordChangeNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    private UserEntity testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = UserEntity.builder()
                .fullName("Jane Doe")
                .email("jane@velocira.com")
                .password("$2a$12$hashedPassword")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .bio("Developer")
                .jobTitle("Software Engineer")
                .company("Velocira Inc.")
                .location("Berlin, Germany")
                .websiteUrl("https://janedoe.dev")
                .build();
        testUser.setId(userId);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    // ── Get Profile ─────────────────────────────────────────────

    @Nested
    @DisplayName("Get Profile")
    class GetProfile {

        @Test
        @DisplayName("Should return user profile successfully")
        void shouldReturnProfile() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(projectRepository.countByOwnerId(userId)).thenReturn(5L);

            UserProfileResponse response = userService.getProfile(userId);

            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo("jane@velocira.com");
            assertThat(response.getFullName()).isEqualTo("Jane Doe");
            assertThat(response.getBio()).isEqualTo("Developer");
            assertThat(response.getProjectCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException for unknown user")
        void shouldThrowOnUnknownUser() {
            UUID unknownId = UUID.randomUUID();
            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile(unknownId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    // ── Update Profile ──────────────────────────────────────────

    @Nested
    @DisplayName("Update Profile")
    class UpdateProfile {

        @Test
        @DisplayName("Should update profile fields selectively")
        void shouldUpdateFieldsSelectively() {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .fullName("Jane Smith")
                    .bio("Updated bio")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(projectRepository.countByOwnerId(userId)).thenReturn(3L);

            UserProfileResponse response = userService.updateProfile(userId, request);

            assertThat(response.getFullName()).isEqualTo("Jane Smith");
            assertThat(response.getBio()).isEqualTo("Updated bio");
            // unchanged fields should remain
            assertThat(response.getCompany()).isEqualTo("Velocira Inc.");
            verify(auditService).record(eq(userId), eq("jane@velocira.com"), any(), anyString());
        }

        @Test
        @DisplayName("Should skip null fields during update")
        void shouldSkipNullFields() {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .bio("New bio only")
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(projectRepository.countByOwnerId(userId)).thenReturn(0L);

            UserProfileResponse response = userService.updateProfile(userId, request);

            assertThat(response.getFullName()).isEqualTo("Jane Doe"); // unchanged
            assertThat(response.getBio()).isEqualTo("New bio only");
        }
    }

    // ── Change Password ─────────────────────────────────────────

    @Nested
    @DisplayName("Change Password")
    class ChangePassword {

        @Test
        @DisplayName("Should change password successfully")
        void shouldChangePassword() {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("OldP@ss123");
            request.setNewPassword("NewP@ss456");

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("OldP@ss123", "$2a$12$hashedPassword")).thenReturn(true);
            when(passwordEncoder.encode("NewP@ss456")).thenReturn("$2a$12$newHash");

            userService.changePassword(userId, request);

            verify(userRepository).save(argThat(user -> user.getPassword().equals("$2a$12$newHash")));
            verify(auditService).record(eq(userId), eq("jane@velocira.com"), any(), anyString());
        }

        @Test
        @DisplayName("Should throw InvalidCurrentPasswordException on wrong password")
        void shouldThrowOnWrongCurrentPassword() {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("WrongPassword");
            request.setNewPassword("NewP@ss456");

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("WrongPassword", "$2a$12$hashedPassword")).thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(InvalidCurrentPasswordException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PasswordChangeNotAllowedException for OAuth users")
        void shouldThrowForOAuthUsers() {
            testUser.setAuthProvider(AuthProvider.GOOGLE);
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setCurrentPassword("AnyPass");
            request.setNewPassword("NewP@ss456");

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(PasswordChangeNotAllowedException.class);
        }
    }

    // ── Delete Account ──────────────────────────────────────────

    @Nested
    @DisplayName("Delete Account")
    class DeleteAccount {

        @Test
        @DisplayName("Should delete account successfully")
        void shouldDeleteAccount() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            userService.deleteAccount(userId);

            verify(userRepository).delete(testUser);
            verify(auditService).record(eq(userId), eq("jane@velocira.com"), any(), anyString());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException for unknown user")
        void shouldThrowOnUnknownUser() {
            UUID unknownId = UUID.randomUUID();
            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteAccount(unknownId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }
}
