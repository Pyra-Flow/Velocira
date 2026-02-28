package com.velocira.backend.auth.model;

import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity representing a user account in the Velocira platform.
 *
 * <p>
 * This entity owns authentication-related fields (credentials, security state,
 * verification status). Profile-level data (university, bio, etc.) will be
 * separated into a User Profile entity in the {@code user} module in future
 * versions.
 * </p>
 *
 * <p>
 * Key security decisions:
 * </p>
 * <ul>
 * <li>Passwords are stored as BCrypt hashes (never plain text)</li>
 * <li>{@code emailVerified} must be {@code true} before login is allowed</li>
 * <li>{@code accountLocked} supports admin-initiated account suspension</li>
 * <li>{@code authProvider} tracks whether the user signed up locally or via
 * OAuth2</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 * @see Role
 * @see AuthProvider
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity extends BaseEntity {

    /** User's full name as provided during registration. */
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /**
     * User's email address — serves as the primary login identifier. Must be
     * unique.
     */
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt-hashed password. Null for OAuth2-only accounts.
     * <p>
     * Never store or log plain-text passwords.
     * </p>
     */
    @Column(name = "password", length = 255)
    private String password;

    /**
     * User's role determining authorization level. Defaults to {@link Role#USER}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    /** Authentication provider used during registration. */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /** Whether the user's email address has been verified via OTP. */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /** Whether the account has been locked by an administrator. */
    @Column(name = "account_locked", nullable = false)
    @Builder.Default
    private boolean accountLocked = false;

    /**
     * Whether the account is enabled. Can be toggled for soft-delete or suspension.
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Timestamp of the user's last successful login. Null if never logged in. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Optional university name (for student/education context). */
    @Column(name = "university_name", length = 255)
    private String universityName;

    /** Short user bio / about text. */
    @Column(name = "bio", length = 500)
    private String bio;

    /** Professional job title. */
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    /** Company or organization name. */
    @Column(name = "company", length = 100)
    private String company;

    /** User's location (city, country, etc.). */
    @Column(name = "location", length = 255)
    private String location;

    /** Personal website or portfolio URL. */
    @Column(name = "website_url", length = 500)
    private String websiteUrl;
}
