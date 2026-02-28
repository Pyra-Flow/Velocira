package com.velocira.backend.auth.dto;

import com.velocira.backend.auth.model.UserEntity;

/**
 * Mapper utility for converting between {@link UserEntity} and {@link UserDto}.
 *
 * <p>
 * Uses static factory methods instead of MapStruct to keep the dependency
 * footprint minimal. This can be replaced with MapStruct in future versions
 * if mapping complexity increases.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
public final class UserMapper {

    private UserMapper() {
        // Utility class — prevent instantiation
    }

    /**
     * Converts a {@link UserEntity} to a client-safe {@link UserDto}.
     *
     * @param entity the JPA user entity
     * @return a DTO containing only client-safe fields
     */
    public static UserDto toDto(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserDto.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .email(entity.getEmail())
                .role(entity.getRole())
                .authProvider(entity.getAuthProvider())
                .emailVerified(entity.isEmailVerified())
                .universityName(entity.getUniversityName())
                .createdAt(entity.getCreatedAt())
                .lastLoginAt(entity.getLastLoginAt())
                .build();
    }
}
