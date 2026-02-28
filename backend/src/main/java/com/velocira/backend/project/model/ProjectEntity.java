package com.velocira.backend.project.model;

import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.document.model.DocumentEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing a user's project in Velocira.
 *
 * <p>
 * A project holds the user's idea description, metadata, and configuration
 * that feed into the AI documentation generation pipeline. Each project can
 * own multiple {@link DocumentEntity} records (one per document type).
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_projects_owner_id", columnList = "owner_id"),
        @Index(name = "idx_projects_status", columnList = "status"),
        @Index(name = "idx_projects_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEntity extends BaseEntity {

    /** The user who owns this project. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    /** Human-readable project name. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Free-text project idea description (50–2000 chars). */
    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    /** Type of project (Web App, Mobile App, etc.). */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ProjectType type;

    /** Current lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.DRAFT;

    /** Optional preferred tech stack (free-text or comma-separated). */
    @Column(name = "tech_stack", length = 500)
    private String techStack;

    /** Target industry vertical (e.g., Healthcare, Education, Finance). */
    @Column(name = "industry", length = 100)
    private String industry;

    /** Target audience description. */
    @Column(name = "target_audience", length = 500)
    private String targetAudience;

    /** Estimated team size working on this project. */
    @Column(name = "team_size")
    private Integer teamSize;

    /** Generation progress percentage (0–100). Updated during AI processing. */
    @Column(name = "progress", nullable = false)
    @Builder.Default
    private int progress = 0;

    /** Associated generated documents. Cascade delete when project is deleted. */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentEntity> documents = new ArrayList<>();
}
