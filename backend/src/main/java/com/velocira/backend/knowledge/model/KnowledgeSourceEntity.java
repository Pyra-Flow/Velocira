package com.velocira.backend.knowledge.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.project.model.ProjectEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Owner-scoped extracted evidence. Raw files are intentionally not retained in the MVP. */
@Entity
@Table(name = "knowledge_sources", indexes = {
        @Index(name = "idx_knowledge_sources_owner_project_status", columnList = "owner_id,project_id,status")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class KnowledgeSourceEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 100)
    private String mediaType;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String classification = "PROJECT_EVIDENCE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private KnowledgeSourceStatus status = KnowledgeSourceStatus.PENDING_REVIEW;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scan_metadata", nullable = false)
    @Builder.Default
    private JsonNode scanMetadata = JsonNodeFactory.instance.objectNode();

    @Column(name = "source_version", nullable = false)
    @Builder.Default
    private int sourceVersion = 1;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
