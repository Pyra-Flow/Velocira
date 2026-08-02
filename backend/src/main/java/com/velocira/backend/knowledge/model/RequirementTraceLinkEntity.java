package com.velocira.backend.knowledge.model;

import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Link from an SRS requirement to the exact project-evidence chunk that supports it. */
@Entity
@Table(name = "requirement_trace_links")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RequirementTraceLinkEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "srs_requirement_id", nullable = false)
    private SrsRequirementEntity srsRequirement;
    @Column(name = "knowledge_source_id") private UUID knowledgeSourceId;
    @Column(name = "knowledge_chunk_id") private UUID knowledgeChunkId;
    @Column(name = "link_type", nullable = false, length = 30)
    @Builder.Default private String linkType = "EVIDENCE";
}
