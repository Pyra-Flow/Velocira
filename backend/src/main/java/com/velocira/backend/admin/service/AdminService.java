package com.velocira.backend.admin.service;

import com.velocira.backend.admin.dto.AdminAnalyticsResponse;
import com.velocira.backend.admin.dto.AdminUserMapper;
import com.velocira.backend.admin.dto.AdminUserResponse;
import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.model.AuditLogEntity;
import com.velocira.backend.audit.repository.AuditLogRepository;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.auth.exceptions.UserNotFoundException;
import com.velocira.backend.auth.model.Role;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.document.model.DocumentStatus;
import com.velocira.backend.document.repository.DocumentRepository;
import com.velocira.backend.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service providing admin panel operations.
 *
 * <p>
 * Requires the calling user to have {@link Role#ADMIN}. Authorization
 * is enforced at the controller level via {@code @PreAuthorize}.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

        private final UserRepository userRepository;
        private final ProjectRepository projectRepository;
        private final DocumentRepository documentRepository;
        private final AuditLogRepository auditLogRepository;
        private final AuditService auditService;

        /**
         * Lists all users with optional search filtering.
         *
         * @param search   optional search term (matches name or email)
         * @param pageable pagination parameters
         * @return page of admin user responses
         */
        @Transactional(readOnly = true)
        public Page<AdminUserResponse> listUsers(String search, Pageable pageable) {
                log.debug("Admin listing users, search=[{}]", search);
                // Native query has ORDER BY built in; strip Sort to avoid column-name mismatch
                Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
                return userRepository.findAllFiltered(search, unsorted)
                                .map(user -> AdminUserMapper.toResponse(user,
                                                projectRepository.countByOwnerId(user.getId())));
        }

        /**
         * Gets a single user's full details.
         *
         * @param userId the user UUID
         * @return admin user response
         */
        @Transactional(readOnly = true)
        public AdminUserResponse getUser(UUID userId) {
                log.debug("Admin fetching user [{}]", userId);
                UserEntity user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
                long projectCount = projectRepository.countByOwnerId(userId);
                return AdminUserMapper.toResponse(user, projectCount);
        }

        /**
         * Suspends (locks) a user account.
         *
         * @param userId  the user to suspend
         * @param adminId the admin performing the action
         */
        @Transactional
        public void suspendUser(UUID userId, UUID adminId) {
                log.info("Admin [{}] suspending user [{}]", adminId, userId);
                UserEntity user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                user.setAccountLocked(true);
                user.setEnabled(false);
                userRepository.save(user);

                auditService.record(adminId, null, AuditAction.ADMIN_USER_SUSPENDED,
                                "Suspended user: " + user.getEmail());
        }

        /**
         * Activates (unlocks) a user account.
         *
         * @param userId  the user to activate
         * @param adminId the admin performing the action
         */
        @Transactional
        public void activateUser(UUID userId, UUID adminId) {
                log.info("Admin [{}] activating user [{}]", adminId, userId);
                UserEntity user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                user.setAccountLocked(false);
                user.setEnabled(true);
                userRepository.save(user);

                auditService.record(adminId, null, AuditAction.ADMIN_USER_ACTIVATED,
                                "Activated user: " + user.getEmail());
        }

        /**
         * Changes a user's role.
         *
         * @param userId  the user whose role to change
         * @param newRole the new role
         * @param adminId the admin performing the action
         */
        @Transactional
        public void changeUserRole(UUID userId, Role newRole, UUID adminId) {
                log.info("Admin [{}] changing role of user [{}] to [{}]", adminId, userId, newRole);
                UserEntity user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                Role oldRole = user.getRole();
                user.setRole(newRole);
                userRepository.save(user);

                auditService.record(adminId, null, AuditAction.ADMIN_ROLE_CHANGED,
                                "Changed role of " + user.getEmail() + " from " + oldRole + " to " + newRole);
        }

        /**
         * Deletes a user account permanently.
         *
         * @param userId  the user to delete
         * @param adminId the admin performing the action
         */
        @Transactional
        public void deleteUser(UUID userId, UUID adminId) {
                log.info("Admin [{}] deleting user [{}]", adminId, userId);
                UserEntity user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(userId.toString()));

                String email = user.getEmail();
                userRepository.delete(user);

                auditService.record(adminId, null, AuditAction.ADMIN_USER_DELETED,
                                "Deleted user: " + email);
        }

        /**
         * Retrieves platform-wide analytics for the admin dashboard.
         *
         * @return analytics response
         */
        @Transactional(readOnly = true)
        public AdminAnalyticsResponse getAnalytics() {
                log.debug("Admin fetching platform analytics");

                Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
                Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

                // Project count by type
                Map<String, Long> projectsByType = new LinkedHashMap<>();
                for (Object[] row : projectRepository.countByType()) {
                        projectsByType.put(row[0].toString(), (Long) row[1]);
                }

                return AdminAnalyticsResponse.builder()
                                .totalUsers(userRepository.count())
                                .newUsersLast30Days(userRepository.countCreatedSince(thirtyDaysAgo))
                                .totalProjects(projectRepository.countAll())
                                .newProjectsLast30Days(projectRepository.countCreatedSince(thirtyDaysAgo))
                                .totalDocuments(documentRepository.countAll())
                                .completedDocuments(documentRepository.countByStatus(DocumentStatus.COMPLETED))
                                .projectsByType(projectsByType)
                                .activeUsersLast7Days(userRepository.countActiveUsersSince(sevenDaysAgo))
                                .totalAuditEvents(auditLogRepository.count())
                                .build();
        }

        /**
         * Retrieves paginated audit logs with optional filters.
         *
         * @param userId   optional user filter
         * @param action   optional action filter
         * @param pageable pagination parameters
         * @return page of audit log entities
         */
        @Transactional(readOnly = true)
        public Page<AuditLogEntity> getAuditLogs(UUID userId, AuditAction action, Pageable pageable) {
                log.debug("Admin fetching audit logs, userId=[{}] action=[{}]", userId, action);
                return auditLogRepository.findFiltered(userId, action, null, pageable);
        }
}
