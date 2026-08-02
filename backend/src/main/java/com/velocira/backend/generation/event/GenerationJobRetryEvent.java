package com.velocira.backend.generation.event;

import java.util.UUID;

/** Internal event emitted only after a persisted backoff interval has elapsed. */
public record GenerationJobRetryEvent(UUID jobId) {
}
