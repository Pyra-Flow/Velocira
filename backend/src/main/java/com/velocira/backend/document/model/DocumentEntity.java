package com.velocira.backend.document.model;

import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.project.model.ProjectEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * JPA entity representing a generated document within a project.
 *
 * <p>
 * Each document belongs to a {@link ProjectEntity} and contains the
 * AI-generated (or user-edited) markdown content for a specific
 * {@link DocumentType}.
 * </p>
 *
 * <p>
 * Documents are created in {@code PENDING} status and transition to
 * {@code GENERATING → COMPLETED} (or {@code FAILED}) once the AI pipeline
 * processes them.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Entity
@Table(name = "documents", indexes = {
                @Index(name = "idx_documents_project_id", columnList = "project_id"),
                @Index(name = "idx_documents_type", columnList = "type"),
                @Index(name = "idx_documents_status", columnList = "status")
}, uniqueConstraints = {
                @UniqueConstraint(name = "uq_documents_project_type", columnNames = { "project_id", "type" })
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntity extends BaseEntity {

        /** The project this document belongs to. */
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "project_id", nullable = false)
        private ProjectEntity project;

        /** The type of document (SRS, BRD, API_SPECIFICATION, etc.). */
        @Enumerated(EnumType.STRING)
        @Column(name = "type", nullable = false, length = 40)
        private DocumentType type;

        /** Current generation/lifecycle status. */
        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 20)
        @Builder.Default
        private DocumentStatus status = DocumentStatus.PENDING;

        /** Human-readable document title (auto-generated or user-provided). */
        @Column(name = "title", nullable = false, length = 255)
        private String title;

        /** The full document content in Markdown format. May be large. */
        @Column(name = "content", columnDefinition = "TEXT")
        private String content;

        /** Version number for tracking edits. Incremented on each save. */
        @Column(name = "version", nullable = false)
        @Builder.Default
        private int version = 1;

        /** Word count of the content (updated on save). */
        @Column(name = "word_count", nullable = false)
        @Builder.Default
        private int wordCount = 0;

        /** Optional AI model identifier used for generation (e.g. "gpt-4o"). */
        @Column(name = "ai_model", length = 50)
        private String aiModel;

        /** Generation time in milliseconds (for analytics). */
        @Column(name = "generation_time_ms")
        private Long generationTimeMs;
}
