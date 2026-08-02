package com.velocira.backend.documentation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.knowledge.model.SrsVersionEntity;
import com.velocira.backend.project.model.ProjectEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Immutable canonical-model snapshot from which all Phase 5 artifacts are rendered. */
@Entity
@Table(name = "documentation_packages", uniqueConstraints = @UniqueConstraint(
        name = "uq_documentation_packages_project_version", columnNames = {"project_id", "version_number"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentationPackageEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "srs_version_id", nullable = false)
    private SrsVersionEntity srsVersion;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    @Builder.Default private DocumentationPackageStatus status = DocumentationPackageStatus.NEEDS_REVIEW;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "canonical_model", nullable = false)
    private JsonNode canonicalModel;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "validation_outcome", nullable = false)
    private JsonNode validationOutcome;
    @Column(name = "generated_at", nullable = false) private Instant generatedAt;
    @Column(name = "approved_at") private Instant approvedAt;
}
