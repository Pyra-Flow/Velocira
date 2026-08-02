package com.velocira.backend.generation.observability;

import com.velocira.backend.generation.client.AiGenerationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports readiness of the isolated generation service without exposing its configuration. */
@Component("generationAi")
@RequiredArgsConstructor
public class GenerationAiHealthIndicator implements HealthIndicator {

    private final AiGenerationClient aiGenerationClient;

    @Override
    public Health health() {
        return aiGenerationClient.isReady()
                ? Health.up().withDetail("service", "ai-generation").build()
                : Health.down().withDetail("service", "ai-generation").build();
    }
}
