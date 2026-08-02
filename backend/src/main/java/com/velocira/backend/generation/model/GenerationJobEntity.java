package com.velocira.backend.generation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.velocira.backend.auth.model.UserEntity;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Durable, idempotent request to generate one document artifact for a project.
 *
 * <p>All request-defining fields are insert-only. A worker may update only the
 * execution state, retry controls, safe status text, and terminal references.
 * This prevents later project edits from changing the evidence for an already
 * accepted generation request.</p>
 */
@Entity
@Table(name = "generation_jobs", indexes = {
        @Index(name = "idx_generation_jobs_project_created", columnList = "project_id,created_at"),
        @Index(name = "idx_generation_jobs_owner_status", columnList = "owner_id,status"),
        @Index(name = "idx_generation_jobs_queue", columnList = "status,next_attempt_at,created_at"),
        @Index(name = "idx_generation_jobs_correlation_id", columnList = "correlation_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_generation_jobs_owner_idempotency", columnNames = { "owner_id", "idempotency_key" })
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationJobEntity extends BaseEntity {

    /** Project whose immutable input snapshot was requested. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    /** Account that owns the request and scopes its idempotency key. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    /** Document type the job is expected to create or version. */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_document_type", nullable = false, length = 40, updatable = false)
    private DocumentType requestedDocumentType;

    /** Browser-supplied key used to resolve repeated POSTs to this same job. */
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    /** SHA-256 of the canonical request used to detect key reuse conflicts. */
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    /** Exact, canonical JSON input supplied to retrieval and the provider. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, updatable = false)
    private JsonNode inputSnapshot;

    /** SHA-256 of {@link #inputSnapshot}, retained for quick integrity checks. */
    @Column(name = "input_snapshot_hash", nullable = false, length = 64, updatable = false)
    private String inputSnapshotHash;

    /** Prompt family selected by trusted server-side configuration. */
    @Column(name = "prompt_template_key", nullable = false, length = 100, updatable = false)
    private String promptTemplateKey;

    /** Exact prompt template revision selected at acceptance time. */
    @Column(name = "prompt_template_version", nullable = false, length = 50, updatable = false)
    private String promptTemplateVersion;

    /** Trace value propagated to API logs, worker logs, and the AI service. */
    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;

    /** User-visible, restart-safe pipeline stage. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private GenerationJobStatus status = GenerationJobStatus.QUEUED;

    /** Current number of provider execution attempts. */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    /** Maximum number of provider execution attempts permitted for this job. */
    @Column(name = "max_attempts", nullable = false, updatable = false)
    @Builder.Default
    private int maxAttempts = 3;

    /** Earliest time a retry worker may claim this job. */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** Timestamp at which the request entered the durable queue. */
    @Column(name = "queued_at", nullable = false, updatable = false)
    private Instant queuedAt;

    /** First time a worker started active processing. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Time a terminal state was reached. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Whether cancellation has been requested by the owner. */
    @Column(name = "cancel_requested", nullable = false)
    @Builder.Default
    private boolean cancelRequested = false;

    /** When the owner requested cancellation. */
    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    /** Whether the latest failure is safe for automated or owner-initiated retry. */
    @Column(name = "retryable", nullable = false)
    @Builder.Default
    private boolean retryable = false;

    /** Safe progress text suitable for rendering in the browser. */
    @Column(name = "status_message", length = 500)
    private String statusMessage;

    /** Stable, non-sensitive error category for client logic and metrics. */
    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 80)
    private GenerationErrorCode errorCode;

    /** Helpful, safe-to-display message. Never put provider credentials here. */
    @Column(name = "user_message", length = 1000)
    private String userMessage;

    /** Protected structured diagnostics; never serialize this directly to clients. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", nullable = false)
    @Builder.Default
    private JsonNode errorDetails = JsonNodeFactory.instance.objectNode();

    /** Optional document record created or selected while publishing the artifact. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private DocumentEntity document;

    /** Terminal immutable artifact version, if the job completed successfully. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artifact_version_id")
    private ArtifactVersionEntity artifactVersion;

    /** Optimistic worker claim guard that prevents conflicting status updates. */
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
}
