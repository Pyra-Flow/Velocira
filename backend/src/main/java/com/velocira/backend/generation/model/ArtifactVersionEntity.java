package com.velocira.backend.generation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.document.model.DocumentEntity;
import com.velocira.backend.document.model.DocumentType;
import com.velocira.backend.project.model.ProjectEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable published result of a successful generation run.
 *
 * <p>Edits create another version rather than changing this row. Its embedded
 * provenance makes a completed artifact reproducible even if the project,
 * configured model, or active prompt template changes later.</p>
 */
@Entity
@Immutable
@Table(name = "artifact_versions", indexes = {
        @Index(name = "idx_artifact_versions_project_type", columnList = "project_id,artifact_type,version_number"),
        @Index(name = "idx_artifact_versions_job", columnList = "generation_job_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_artifact_versions_document_version", columnNames = { "document_id", "version_number" })
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactVersionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_job_id", nullable = false)
    private GenerationJobEntity generationJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_run_id")
    private GenerationRunEntity generationRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 40, updatable = false)
    private DocumentType artifactType;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "title", nullable = false, length = 255, updatable = false)
    private String title;

    /** Canonical artifact content, normally Markdown or structured JSON text. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "content_sha256", nullable = false, length = 64, updatable = false)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20, updatable = false)
    @Builder.Default
    private ArtifactValidationStatus validationStatus = ArtifactValidationStatus.PENDING;

    /** Exact request input captured before retrieval and provider execution. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_input_snapshot", nullable = false, updatable = false)
    private JsonNode sourceInputSnapshot;

    /** Non-secret raw output metadata needed for reproducibility. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_metadata", nullable = false, updatable = false)
    @Builder.Default
    private JsonNode outputMetadata = JsonNodeFactory.instance.objectNode();

    /** Full structural/policy validator record stored alongside the artifact. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validator_outcome", nullable = false, updatable = false)
    @Builder.Default
    private JsonNode validatorOutcome = JsonNodeFactory.instance.objectNode();

    @Column(name = "provider", nullable = false, length = 100, updatable = false)
    private String provider;

    @Column(name = "model", nullable = false, length = 150, updatable = false)
    private String model;

    @Column(name = "prompt_template_key", nullable = false, length = 100, updatable = false)
    private String promptTemplateKey;

    @Column(name = "prompt_template_version", nullable = false, length = 50, updatable = false)
    private String promptTemplateVersion;

    @Column(name = "prompt_checksum", nullable = false, length = 64, updatable = false)
    private String promptChecksum;

    /** Instant at which the completed content became this immutable version. */
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;
}
