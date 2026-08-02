package com.velocira.backend.knowledge.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.project.model.ProjectEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.math.BigDecimal;

/** Immutable generated SRS snapshot; review transitions are recorded on the snapshot. */
@Entity
@Table(name = "srs_versions", uniqueConstraints = @UniqueConstraint(
        name = "uq_srs_versions_project_version", columnNames = {"project_id", "version_number"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SrsVersionEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "profile_id", nullable = false)
    private StandardsProfileEntity profile;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    @Builder.Default private SrsVersionStatus status = SrsVersionStatus.NEEDS_REVIEW;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "brief_snapshot", nullable = false)
    private JsonNode briefSnapshot;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "srs_content", nullable = false)
    private JsonNode srsContent;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "validation_outcome", nullable = false)
    private JsonNode validationOutcome;
    /** Percentage stored as NUMERIC(5,2) so validation and persistence agree exactly. */
    @Column(name = "citation_coverage", nullable = false, precision = 5, scale = 2)
    private BigDecimal citationCoverage;
    @Column(nullable = false, length = 100) private String provider;
    @Column(nullable = false, length = 150) private String model;
    @Column(name = "prompt_version", nullable = false, length = 64) private String promptVersion;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "change_request", columnDefinition = "TEXT") private String changeRequest;
}
