package com.velocira.backend.documentation.model;

import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Immutable export bytes and reproducibility metadata for a completed package export. */
@Entity
@Table(name = "documentation_export_jobs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentationExportJobEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "package_id", nullable = false)
    private DocumentationPackageEntity documentationPackage;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DocumentationExportFormat format;
    /** Null identifies an export created before visual-style metadata existed. */
    @Enumerated(EnumType.STRING) @Column(name = "export_template", updatable = false, length = 30)
    @Builder.Default private DocumentationExportTemplate template = DocumentationExportStyle.DEFAULT_TEMPLATE;
    @Enumerated(EnumType.STRING) @Column(name = "export_theme", updatable = false, length = 30)
    @Builder.Default private DocumentationExportTheme theme = DocumentationExportStyle.DEFAULT_THEME;
    @Enumerated(EnumType.STRING) @Column(name = "export_layout", updatable = false, length = 30)
    @Builder.Default private DocumentationExportLayout layout = DocumentationExportStyle.DEFAULT_LAYOUT;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    @Builder.Default private DocumentationExportStatus status = DocumentationExportStatus.READY;
    @Column(nullable = false, length = 255) private String filename;
    @Column(name = "content_type", nullable = false, length = 150) private String contentType;
    @Column(name = "byte_size", nullable = false) private long byteSize;
    @Column(name = "content_sha256", nullable = false, length = 64) private String contentSha256;
    /** A large immutable package payload. PostgreSQL and H2 PostgreSQL mode both support BYTEA. */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "BYTEA")
    private byte[] content;
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "completed_at") private Instant completedAt;
}
