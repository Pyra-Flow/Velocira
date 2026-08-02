package com.velocira.backend.knowledge.model;

import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.project.model.ProjectEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** A stable, citable excerpt indexed separately from transactional data. */
@Entity
@Table(name = "knowledge_chunks", uniqueConstraints = @UniqueConstraint(
        name = "uq_knowledge_chunks_source_ordinal", columnNames = {"source_id", "chunk_ordinal"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class KnowledgeChunkEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSourceEntity source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @Column(name = "chunk_ordinal", nullable = false)
    private int chunkOrdinal;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(name = "qdrant_point_id", nullable = false)
    private UUID qdrantPointId;
}
