package com.velocira.backend.user.dto;

import com.velocira.backend.auth.model.UserEntity;

/**
 * Maps {@link UserEntity} to {@link UserProfileResponse}.
 *
 * @author Velocira Team
 * @since 1.0
 */
public final class UserProfileMapper {

    private UserProfileMapper() {
    }

    /**
     * Converts a {@link UserEntity} to a full profile DTO.
     *
     * @param entity       the user entity
     * @param projectCount the total number of projects this user owns
     * @return the profile response DTO, or {@code null} if entity is null
     */
    public static UserProfileResponse toProfile(UserEntity entity, long projectCount) {
        if (entity == null)
            return null;

        return UserProfileResponse.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .role(entity.getRole())
                .authProvider(entity.getAuthProvider())
                .emailVerified(entity.isEmailVerified())
                .universityName(entity.getUniversityName())
                .bio(entity.getBio())
                .jobTitle(entity.getJobTitle())
                .company(entity.getCompany())
                .location(entity.getLocation())
                .websiteUrl(entity.getWebsiteUrl())
                .projectCount(projectCount)
                .createdAt(entity.getCreatedAt())
                .lastLoginAt(entity.getLastLoginAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
