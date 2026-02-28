package com.velocira.backend.auth.repository;

import com.velocira.backend.auth.model.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserEntity} persistence operations.
 *
 * <p>
 * Provides standard CRUD operations plus custom queries for authentication
 * flows.
 * All queries use parameterized bindings to prevent SQL injection.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

        /**
         * Finds a user by their email address (case-insensitive).
         *
         * @param email the email address to search for
         * @return an {@link Optional} containing the user if found
         */
        Optional<UserEntity> findByEmailIgnoreCase(String email);

        /**
         * Checks whether a user with the given email already exists.
         *
         * @param email the email address to check
         * @return {@code true} if a user with this email exists
         */
        boolean existsByEmailIgnoreCase(String email);

        /**
         * Updates the last login timestamp for a user.
         *
         * @param userId      the user's ID
         * @param lastLoginAt the login timestamp
         */
        @Modifying
        @Query("UPDATE UserEntity u SET u.lastLoginAt = :lastLoginAt WHERE u.id = :userId")
        void updateLastLoginAt(@Param("userId") UUID userId, @Param("lastLoginAt") Instant lastLoginAt);

        /**
         * Marks a user's email as verified.
         *
         * @param userId the user's ID
         */
        @Modifying
        @Query("UPDATE UserEntity u SET u.emailVerified = true WHERE u.id = :userId")
        void markEmailVerified(@Param("userId") UUID userId);

        // ── Admin Queries ───────────────────────────────────────────

        /**
         * Searches users with optional filters for admin panel.
         *
         * @param search   optional search term (matches name or email)
         * @param pageable pagination parameters
         * @return a page of matching users
         */
        @Query(value = "SELECT u.* FROM users u " +
                        "WHERE (CAST(:search AS VARCHAR) IS NULL " +
                        "   OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) " +
                        "   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%'))) " +
                        "ORDER BY u.created_at DESC", countQuery = "SELECT COUNT(*) FROM users u " +
                                        "WHERE (CAST(:search AS VARCHAR) IS NULL " +
                                        "   OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')) "
                                        +
                                        "   OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS VARCHAR), '%')))", nativeQuery = true)
        Page<UserEntity> findAllFiltered(@Param("search") String search, Pageable pageable);

        /**
         * Counts users created since a given timestamp.
         */
        @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.createdAt >= :since")
        long countCreatedSince(@Param("since") Instant since);

        /**
         * Counts users who logged in since a given timestamp.
         */
        @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.lastLoginAt >= :since")
        long countActiveUsersSince(@Param("since") Instant since);
}
