package com.velocira.backend.admin.service;

import com.velocira.backend.admin.dto.AdminAnalyticsResponse;
import com.velocira.backend.admin.dto.AdminUserResponse;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.repository.AuditLogRepository;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.auth.exceptions.UserNotFoundException;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.document.model.DocumentStatus;
import com.velocira.backend.document.repository.DocumentRepository;
import com.velocira.backend.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AdminService}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService Tests")
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AdminService adminService;

    private UserEntity testUser;
    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        testUser = UserEntity.builder()
                .fullName("Test User")
                .email("test@velocira.com")
                .password("$2a$12$hashed")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build();
        testUser.setId(userId);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    // ── List Users ──────────────────────────────────────────────

    @Nested
    @DisplayName("List Users")
    class ListUsers {

        @Test
        @DisplayName("Should return paginated users")
        void shouldReturnPaginatedUsers() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserEntity> page = new PageImpl<>(List.of(testUser), pageable, 1);
            when(userRepository.findAllFiltered(isNull(), any(Pageable.class))).thenReturn(page);
            when(projectRepository.countByOwnerId(userId)).thenReturn(3L);

            Page<AdminUserResponse> result = adminService.listUsers(null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("test@velocira.com");
            assertThat(result.getContent().get(0).getProjectCount()).isEqualTo(3);
        }
    }

    // ── Get User ────────────────────────────────────────────────

    @Nested
    @DisplayName("Get User")
    class GetUser {

        @Test
        @DisplayName("Should return user details")
        void shouldReturnUserDetails() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(projectRepository.countByOwnerId(userId)).thenReturn(5L);

            AdminUserResponse result = adminService.getUser(userId);

            assertThat(result.getFullName()).isEqualTo("Test User");
            assertThat(result.getProjectCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should throw UserNotFoundException")
        void shouldThrowOnUnknownUser() {
            UUID unknownId = UUID.randomUUID();
            when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.getUser(unknownId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    // ── Suspend User ────────────────────────────────────────────

    @Nested
    @DisplayName("Suspend User")
    class SuspendUser {

        @Test
        @DisplayName("Should lock and disable user account")
        void shouldSuspendUser() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            adminService.suspendUser(userId, adminId);

            verify(userRepository).save(argThat(user -> user.isAccountLocked() && !user.isEnabled()));
            verify(auditService).record(eq(adminId), isNull(), eq(AuditAction.ADMIN_USER_SUSPENDED), anyString());
        }
    }

    // ── Activate User ───────────────────────────────────────────

    @Nested
    @DisplayName("Activate User")
    class ActivateUser {

        @Test
        @DisplayName("Should unlock and enable user account")
        void shouldActivateUser() {
            testUser.setAccountLocked(true);
            testUser.setEnabled(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            adminService.activateUser(userId, adminId);

            verify(userRepository).save(argThat(user -> !user.isAccountLocked() && user.isEnabled()));
            verify(auditService).record(eq(adminId), isNull(), eq(AuditAction.ADMIN_USER_ACTIVATED), anyString());
        }
    }

    // ── Change Role ─────────────────────────────────────────────

    @Nested
    @DisplayName("Change User Role")
    class ChangeRole {

        @Test
        @DisplayName("Should change role successfully")
        void shouldChangeRole() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            adminService.changeUserRole(userId, Role.ADMIN, adminId);

            verify(userRepository).save(argThat(user -> user.getRole() == Role.ADMIN));
            verify(auditService).record(eq(adminId), isNull(), eq(AuditAction.ADMIN_ROLE_CHANGED), anyString());
        }
    }

    // ── Delete User ─────────────────────────────────────────────

    @Nested
    @DisplayName("Delete User")
    class DeleteUser {

        @Test
        @DisplayName("Should delete user permanently")
        void shouldDeleteUser() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

            adminService.deleteUser(userId, adminId);

            verify(userRepository).delete(testUser);
            verify(auditService).record(eq(adminId), isNull(), eq(AuditAction.ADMIN_USER_DELETED), anyString());
        }
    }

    // ── Analytics ───────────────────────────────────────────────

    @Nested
    @DisplayName("Analytics")
    class Analytics {

        @Test
        @DisplayName("Should return platform analytics")
        void shouldReturnAnalytics() {
            when(userRepository.count()).thenReturn(1500L);
            when(userRepository.countCreatedSince(any())).thenReturn(87L);
            when(userRepository.countActiveUsersSince(any())).thenReturn(324L);
            when(projectRepository.countAll()).thenReturn(3421L);
            when(projectRepository.countCreatedSince(any())).thenReturn(156L);
            when(projectRepository.countByType()).thenReturn(List.of(
                    new Object[] { "WEB_APP", 1200L },
                    new Object[] { "MOBILE_APP", 800L }));
            when(documentRepository.countAll()).thenReturn(12305L);
            when(documentRepository.countByStatus(DocumentStatus.COMPLETED)).thenReturn(11890L);
            when(auditLogRepository.count()).thenReturn(54321L);

            AdminAnalyticsResponse result = adminService.getAnalytics();

            assertThat(result.getTotalUsers()).isEqualTo(1500);
            assertThat(result.getNewUsersLast30Days()).isEqualTo(87);
            assertThat(result.getTotalProjects()).isEqualTo(3421);
            assertThat(result.getActiveUsersLast7Days()).isEqualTo(324);
            assertThat(result.getProjectsByType()).containsEntry("WEB_APP", 1200L);
        }
    }
}
