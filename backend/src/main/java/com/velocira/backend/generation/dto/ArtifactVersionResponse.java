package com.velocira.backend.generation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.generation.model.ArtifactValidationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Immutable artifact plus the provenance needed to reproduce or review it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactVersionResponse {

    private UUID id;
    private UUID projectId;
    private UUID documentId;
    private UUID generationJobId;
    private UUID generationRunId;
    private DocumentType artifactType;
    private int versionNumber;
    private String title;
    private String content;
    private String contentSha256;
    private ArtifactValidationStatus validationStatus;
    private JsonNode validatorOutcome;
    private String provider;
    private String model;
    private String promptTemplateKey;
    private String promptTemplateVersion;
    private Instant generatedAt;
}
