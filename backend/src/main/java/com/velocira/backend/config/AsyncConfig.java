package com.velocira.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Async and scheduling configuration.
 *
 * <p>
 * Enables:
 * </p>
 * <ul>
 * <li>{@code @Async} — for non-blocking email sending</li>
 * <li>{@code @Scheduled} — for token/OTP cleanup jobs</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    // Async and scheduling enabled by annotations.
    // Custom TaskExecutor can be added here if needed.
}
