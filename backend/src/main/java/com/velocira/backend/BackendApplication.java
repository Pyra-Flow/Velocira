package com.velocira.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Velocira backend application.
 *
 * <p>
 * Configuration is split into dedicated config classes:
 * </p>
 * <ul>
 * <li>{@link com.velocira.backend.config.SecurityConfig} — Spring Security /
 * JWT</li>
 * <li>{@link com.velocira.backend.config.AsyncConfig} — Async + Scheduling</li>
 * <li>{@link com.velocira.backend.config.AuditConfig} — JPA Auditing</li>
 * <li>{@link com.velocira.backend.config.OpenApiConfig} — Swagger /
 * OpenAPI</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
