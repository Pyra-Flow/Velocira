package com.velocira.backend.admin.dto;

import com.velocira.backend.auth.model.UserEntity;

/**
 * Maps {@link UserEntity} to {@link AdminUserResponse}.
 *
 * @author Velocira Team
 * @since 1.0
 */
public final class AdminUserMapper {

    private AdminUserMapper() {
    }

    /**
     * Converts a {@link UserEntity} to an admin-facing response DTO.
     *
     * @param entity       the user entity
     * @param projectCount the number of projects owned by the user
     * @return the admin user response
     */
    public static AdminUserResponse toResponse(UserEntity entity, long projectCount) {
        if (entity == null)
            return null;

        return AdminUserResponse.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .role(entity.getRole())
                .authProvider(entity.getAuthProvider())
                .emailVerified(entity.isEmailVerified())
                .accountLocked(entity.isAccountLocked())
                .enabled(entity.isEnabled())
                .lastLoginAt(entity.getLastLoginAt())
                .universityName(entity.getUniversityName())
                .projectCount(projectCount)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
