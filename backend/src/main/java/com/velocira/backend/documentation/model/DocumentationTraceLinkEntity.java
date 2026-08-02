package com.velocira.backend.documentation.model;

import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.knowledge.model.SrsRequirementEntity;
import jakarta.persistence.*;
import lombok.*;

/** One explicit trace row per approved requirement, linking every relevant MVP artifact. */
@Entity
@Table(name = "documentation_trace_links", uniqueConstraints = @UniqueConstraint(
        name = "uq_documentation_trace_requirement", columnNames = {"package_id", "srs_requirement_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentationTraceLinkEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "package_id", nullable = false)
    private DocumentationPackageEntity documentationPackage;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "srs_requirement_id", nullable = false)
    private SrsRequirementEntity srsRequirement;
    @Column(name = "requirement_key", nullable = false, length = 80) private String requirementKey;
    @Column(name = "use_case_id", length = 80) private String useCaseId;
    @Column(name = "entity_id", length = 80) private String entityId;
    @Column(name = "api_operation_id", length = 120) private String apiOperationId;
    @Column(name = "acceptance_criterion_id", nullable = false, length = 120) private String acceptanceCriterionId;
    @Column(name = "source_kind", nullable = false, length = 30) private String sourceKind;
}
