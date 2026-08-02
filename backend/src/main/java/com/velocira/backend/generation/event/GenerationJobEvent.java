package com.velocira.backend.generation.event;

import java.time.Instant;
import java.util.UUID;

/** In-process event emitted whenever the durable job reaches a visible state. */
public record GenerationJobEvent(
        UUID jobId,
        UUID projectId,
        String status,
        String correlationId,
        Instant occurredAt) {
}
