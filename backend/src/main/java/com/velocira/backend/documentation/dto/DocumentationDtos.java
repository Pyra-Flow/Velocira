package com.velocira.backend.documentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.documentation.model.DocumentationArtifactType;
import com.velocira.backend.documentation.model.DocumentationExportFormat;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentationDtos {
    private DocumentationDtos() { }

    public record CreatePackageRequest(@NotNull UUID srsVersionId) { }
    public record ExportRequest(@NotNull DocumentationExportFormat format) { }
    public record ArtifactResponse(DocumentationArtifactType type, String title, String content,
                                   String sourceFormat, String sourceContent, String checksum, JsonNode validation) { }
    public record TraceResponse(String requirementId, String useCaseId, String entityId, String apiOperationId,
                                String acceptanceCriterionId, String sourceKind) { }
    public record PackageResponse(UUID id, int versionNumber, String status, UUID srsVersionId, Instant generatedAt,
                                  Instant approvedAt, JsonNode canonicalModel, JsonNode validation,
                                  List<ArtifactResponse> artifacts, List<TraceResponse> traceLinks) { }
    public record ExportResponse(UUID id, String format, String status, String filename, String contentType,
                                 long byteSize, String sha256, Instant completedAt, Instant createdAt) { }
}
