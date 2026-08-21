package com.velocira.backend.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** API contracts keep evidence and assumption status visible to the review UI. */
public final class KnowledgeDtos {
    private KnowledgeDtos() { }

    public record SourceResponse(UUID id, String title, String originalFilename, String mediaType,
                                 String classification, String status, JsonNode scanMetadata, int chunkCount,
                                 Instant approvedAt, Instant createdAt) { }
    public record StandardsProfileResponse(String key, String name, String description, List<String> controls,
                                           String sourceLicense, String ownerName, LocalDate effectiveDate) { }
    public record GenerateSrsRequest(@NotBlank @Size(max = 50) String profileKey,
                                     @jakarta.validation.constraints.Pattern(regexp = "STANDARD|EXHAUSTIVE") String generationMode) { }
    public record ReviewSrsRequest(@NotBlank @Size(max = 2000) String message) { }
    public record RequirementResponse(UUID id, String requirementId, String type, String priority,
                                      String statement, String rationale, String acceptanceCriteria,
                                      String sourceKind, String sourceDetail, String verificationMethod,
                                      JsonNode qualityOutcome, List<TraceLinkResponse> traceLinks) { }
    public record TraceLinkResponse(UUID sourceId, UUID chunkId, String linkType) { }
    public record SrsVersionResponse(UUID id, int versionNumber, String status, String profileKey,
                                     JsonNode content, JsonNode validation, double citationCoverage,
                                     String provider, String model, String promptVersion, Instant generatedAt,
                                     Instant approvedAt, String changeRequest, List<RequirementResponse> requirements) { }
}
