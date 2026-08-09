package com.velocira.backend.documentation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.documentation.model.DocumentationArtifactType;
import com.velocira.backend.documentation.model.DocumentationExportFormat;
import com.velocira.backend.documentation.model.DocumentationExportLayout;
import com.velocira.backend.documentation.model.DocumentationExportStyle;
import com.velocira.backend.documentation.model.DocumentationExportTemplate;
import com.velocira.backend.documentation.model.DocumentationExportTheme;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentationDtos {
    private DocumentationDtos() { }

    public record CreatePackageRequest(@NotNull UUID srsVersionId) { }
    public record ExportRequest(@NotNull DocumentationExportFormat format,
                                DocumentationExportTemplate template,
                                DocumentationExportTheme theme,
                                DocumentationExportLayout layout) {
        /** Keeps in-process clients that only provide a format source-compatible. */
        public ExportRequest(DocumentationExportFormat format) {
            this(format, null, null, null);
        }

        public DocumentationExportStyle style() {
            return new DocumentationExportStyle(template, theme, layout);
        }
    }
    public record ArtifactResponse(DocumentationArtifactType type, String title, String content,
                                   String sourceFormat, String sourceContent, String checksum, JsonNode validation) { }
    public record TraceResponse(String requirementId, String useCaseId, String entityId, String apiOperationId,
                                String acceptanceCriterionId, String sourceKind) { }
    public record PackageResponse(UUID id, int versionNumber, String status, UUID srsVersionId, Instant generatedAt,
                                  Instant approvedAt, JsonNode canonicalModel, JsonNode validation,
                                  List<ArtifactResponse> artifacts, List<TraceResponse> traceLinks) { }
    public record ExportResponse(UUID id, String format, String template, String theme, String layout, String status, String filename, String contentType,
                                 long byteSize, String sha256, Instant completedAt, Instant createdAt) {
        /** Keeps Java callers compiled against the pre-style response constructor source-compatible. */
        public ExportResponse(UUID id, String format, String status, String filename, String contentType,
                              long byteSize, String sha256, Instant completedAt, Instant createdAt) {
            this(id, format, null, null, null, status, filename, contentType, byteSize, sha256, completedAt, createdAt);
        }
    }
}
