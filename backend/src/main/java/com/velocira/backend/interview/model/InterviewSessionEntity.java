package com.velocira.backend.interview.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.velocira.backend.auth.model.UserEntity;
import com.velocira.backend.common.model.BaseEntity;
import com.velocira.backend.project.model.ProjectEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** The owner-scoped root of the durable discovery evidence and project brief. */
@Entity
@Table(name = "interview_sessions", indexes = {
        @Index(name = "idx_interview_sessions_owner_status", columnList = "owner_id,status")
}, uniqueConstraints = @UniqueConstraint(name = "uq_interview_sessions_project", columnNames = "project_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserEntity owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private InterviewSessionStatus status = InterviewSessionStatus.IN_PROGRESS;

    /** Current canonical brief assembled only from current answer evidence. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canonical_brief", nullable = false)
    @Builder.Default
    private JsonNode canonicalBrief = JsonNodeFactory.instance.objectNode();

    /** Deterministic readiness evidence shown to the owner and generation gate. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "readiness_snapshot", nullable = false)
    @Builder.Default
    private JsonNode readinessSnapshot = JsonNodeFactory.instance.objectNode();

    /** Current validated planner decision shown to the owner and copied into answer evidence. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_question_plan", nullable = false)
    @Builder.Default
    private JsonNode currentQuestionPlan = JsonNodeFactory.instance.objectNode();

    @Column(name = "brief_version", nullable = false)
    @Builder.Default
    private int briefVersion = 0;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
}
