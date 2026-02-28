package com.velocira.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * JPA Auditing configuration.
 *
 * <p>
 * Enables {@code @CreatedDate} and {@code @LastModifiedDate} on entities
 * extending {@link com.velocira.backend.common.model.BaseEntity}.
 * </p>
 *
 * <p>
 * Provides an {@link AuditorAware} that resolves the current user from
 * the Spring Security context. This can be used with {@code @CreatedBy}
 * and {@code @LastModifiedBy} if needed in the future.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditConfig {

    /**
     * Provides the current auditor (user email) from the security context.
     *
     * @return the auditor-aware provider
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()
                    || "anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.of("system");
            }
            return Optional.of(authentication.getName());
        };
    }
}
