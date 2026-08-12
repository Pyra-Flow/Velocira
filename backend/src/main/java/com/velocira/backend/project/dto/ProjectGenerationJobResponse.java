package com.velocira.backend.project.dto;

import java.util.UUID;

/** Minimal job identity needed by the default workspace; diagnostics stay on the advanced jobs API. */
public record ProjectGenerationJobResponse(UUID id, UUID documentId) {
}
