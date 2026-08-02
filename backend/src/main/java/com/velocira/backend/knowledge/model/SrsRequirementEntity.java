package com.velocira.backend.knowledge.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Requirement quality fields are first-class so machine checks remain auditable. */
@Entity
@Table(name = "srs_requirements", uniqueConstraints = @UniqueConstraint(
        name = "uq_srs_requirements_version_id", columnNames = {"srs_version_id", "requirement_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SrsRequirementEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "srs_version_id", nullable = false)
    private SrsVersionEntity srsVersion;
    @Column(name = "requirement_id", nullable = false, length = 80) private String requirementId;
    @Column(name = "requirement_type", nullable = false, length = 30) private String requirementType;
    @Column(nullable = false, length = 20) private String priority;
    @Column(nullable = false, columnDefinition = "TEXT") private String statement;
    @Column(nullable = false, columnDefinition = "TEXT") private String rationale;
    @Column(name = "acceptance_criteria", nullable = false, columnDefinition = "TEXT") private String acceptanceCriteria;
    @Column(name = "source_kind", nullable = false, length = 20) private String sourceKind;
    @Column(name = "source_detail", nullable = false, columnDefinition = "TEXT") private String sourceDetail;
    @Column(name = "verification_method", nullable = false, length = 30) private String verificationMethod;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "quality_outcome", nullable = false)
    private JsonNode qualityOutcome;
}
