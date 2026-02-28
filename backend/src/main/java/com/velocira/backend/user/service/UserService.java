package com.velocira.backend.user.service;

import com.velocira.backend.audit.model.AuditAction;
import com.velocira.backend.audit.service.AuditService;
import com.velocira.backend.auth.exceptions.UserNotFoundException;
import com.velocira.backend.auth.model.AuthProvider;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.auth.repository.UserRepository;
import com.velocira.backend.auth.service.RefreshTokenService;
import com.velocira.backend.project.repository.ProjectRepository;
import com.velocira.backend.user.dto.*;
import com.velocira.backend.user.exceptions.InvalidCurrentPasswordException;
import com.velocira.backend.user.exceptions.PasswordChangeNotAllowedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for user profile management operations.
 *
 * <p>
 * Handles profile retrieval, updates, password changes, and account
 * deletion. All significant actions are audit-logged.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    /**
     * Retrieves the full profile for the given user ID.
     *
     * @param userId the user's UUID
     * @return the user profile response with project count
     * @throws UserNotFoundException if user does not exist
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        log.debug("Fetching profile for user [{}]", userId);
        UserEntity user = findUserOrThrow(userId);
        long projectCount = projectRepository.countByOwnerId(userId);
        return UserProfileMapper.toProfile(user, projectCount);
    }

    /**
     * Updates the authenticated user's profile fields.
     *
     * <p>
     * Only non-null fields in the request are applied.
     * </p>
     *
     * @param userId  the authenticated user's UUID
     * @param request the update request
     * @return the updated profile
     * @throws UserNotFoundException if user does not exist
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        log.info("Updating profile for user [{}]", userId);
        UserEntity user = findUserOrThrow(userId);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getUniversityName() != null) {
            user.setUniversityName(request.getUniversityName().trim());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        if (request.getJobTitle() != null) {
            user.setJobTitle(request.getJobTitle().trim());
        }
        if (request.getCompany() != null) {
            user.setCompany(request.getCompany().trim());
        }
        if (request.getLocation() != null) {
            user.setLocation(request.getLocation().trim());
        }
        if (request.getWebsiteUrl() != null) {
            user.setWebsiteUrl(request.getWebsiteUrl().trim());
        }

        user = userRepository.save(user);
        log.info("Profile updated for user [{}]", user.getEmail());

        auditService.record(userId, user.getEmail(), AuditAction.PROFILE_UPDATED,
                "Profile fields updated");

        long projectCount = projectRepository.countByOwnerId(userId);
        return UserProfileMapper.toProfile(user, projectCount);
    }

    /**
     * Changes the password for a LOCAL-auth user.
     *
     * <p>
     * Validates the current password before applying the new one.
     * After a successful change, all refresh tokens are revoked for security.
     * </p>
     *
     * @param userId  the authenticated user's UUID
     * @param request the change password request
     * @throws UserNotFoundException             if user does not exist
     * @throws PasswordChangeNotAllowedException if user is OAuth-only
     * @throws InvalidCurrentPasswordException   if current password is incorrect
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        log.info("Processing password change for user [{}]", userId);
        UserEntity user = findUserOrThrow(userId);

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new PasswordChangeNotAllowedException();
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            auditService.record(userId, user.getEmail(), AuditAction.PASSWORD_CHANGED,
                    "Failed — incorrect current password", null, null, false);
            throw new InvalidCurrentPasswordException();
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all sessions for security
        refreshTokenService.revokeAllTokens(user);

        auditService.record(userId, user.getEmail(), AuditAction.PASSWORD_CHANGED,
                "Password changed — all sessions revoked");

        log.info("Password changed for user [{}]. All sessions revoked.", user.getEmail());
    }

    /**
     * Permanently deletes a user account and all associated data.
     *
     * @param userId the authenticated user's UUID
     * @throws UserNotFoundException if user does not exist
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        log.warn("Processing account deletion for user [{}]", userId);
        UserEntity user = findUserOrThrow(userId);

        String email = user.getEmail();
        userRepository.delete(user);

        auditService.record(userId, email, AuditAction.ADMIN_USER_DELETED,
                "Account self-deleted by user");

        log.warn("Account deleted for user [{}]", email);
    }

    private UserEntity findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
    }
}
