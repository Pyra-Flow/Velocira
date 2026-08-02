package com.velocira.backend.documentation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Rendered artifact and editable source retained beside the canonical model. */
@Entity
@Table(name = "documentation_artifacts", uniqueConstraints = @UniqueConstraint(
        name = "uq_documentation_artifacts_package_type", columnNames = {"package_id", "artifact_type"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentationArtifactEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "package_id", nullable = false)
    private DocumentationPackageEntity documentationPackage;
    @Enumerated(EnumType.STRING) @Column(name = "artifact_type", nullable = false, length = 40)
    private DocumentationArtifactType artifactType;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "source_format", nullable = false, length = 30) private String sourceFormat;
    @Column(name = "source_content", nullable = false, columnDefinition = "TEXT") private String sourceContent;
    @Column(nullable = false, length = 64) private String checksum;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "validation_outcome", nullable = false)
    private JsonNode validationOutcome;
}
