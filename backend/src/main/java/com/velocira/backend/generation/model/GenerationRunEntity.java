package com.velocira.backend.generation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.velocira.backend.common.model.BaseEntity;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One provider execution attempt of a {@link GenerationJobEntity}.
 *
 * <p>Input and prompt provenance are insert-only. Completion fields make a
 * failed or timed-out attempt inspectable without modifying the original
 * request or a later retry attempt.</p>
 */
@Entity
@Table(name = "generation_runs", indexes = {
        @Index(name = "idx_generation_runs_job_attempt", columnList = "generation_job_id,attempt_number"),
        @Index(name = "idx_generation_runs_correlation_id", columnList = "correlation_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_generation_runs_job_attempt", columnNames = { "generation_job_id", "attempt_number" })
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationRunEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_job_id", nullable = false)
    private GenerationJobEntity generationJob;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GenerationRunStatus status = GenerationRunStatus.RUNNING;

    /** Trusted provider identifier (for example, a configured internal adapter). */
    @Column(name = "provider", length = 100, updatable = false)
    private String provider;

    /** Trusted model identifier selected server-side. */
    @Column(name = "model", length = 150, updatable = false)
    private String model;

    /** Provider-side request ID for support correlation; never a credential. */
    @Column(name = "provider_request_id", length = 255)
    private String providerRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prompt_template_id")
    private PromptTemplateEntity promptTemplate;

    @Column(name = "prompt_template_key", nullable = false, length = 100, updatable = false)
    private String promptTemplateKey;

    @Column(name = "prompt_template_version", nullable = false, length = 50, updatable = false)
    private String promptTemplateVersion;

    @Column(name = "prompt_checksum", nullable = false, length = 64, updatable = false)
    private String promptChecksum;

    /** Exact input sent to the AI service/provider for this attempt. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, updatable = false)
    private JsonNode inputSnapshot;

    /** Non-secret structured provider response metadata. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_metadata", nullable = false)
    @Builder.Default
    private JsonNode outputMetadata = JsonNodeFactory.instance.objectNode();

    /** Structured validator outcome kept with the provider attempt. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validator_outcome", nullable = false)
    @Builder.Default
    private JsonNode validatorOutcome = JsonNodeFactory.instance.objectNode();

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    /** USD cost recorded at provider response time, never estimated client-side. */
    @Column(name = "cost_usd", precision = 14, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "retryable", nullable = false)
    @Builder.Default
    private boolean retryable = false;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 80)
    private GenerationErrorCode failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "correlation_id", length = 128, updatable = false)
    private String correlationId;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
}
