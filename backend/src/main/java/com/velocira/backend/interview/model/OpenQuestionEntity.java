package com.velocira.backend.interview.model;

import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A visible missing, unknown, or contradictory requirement; it is never silently discarded. */
@Entity
@Table(name = "open_questions", indexes = @Index(name = "idx_open_questions_session_status", columnList = "session_id,status,risk_level"), uniqueConstraints =
        @UniqueConstraint(name = "uq_open_questions_session_key", columnNames = { "session_id", "question_key" }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenQuestionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;

    @Column(name = "source_answer_id")
    private UUID sourceAnswerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private InterviewCategory category;

    @Column(name = "question_key", nullable = false, length = 120)
    private String questionKey;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OpenQuestionStatus status = OpenQuestionStatus.OPEN;

    @Column(name = "is_material", nullable = false)
    @Builder.Default
    private boolean material = true;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String origin = "READINESS_RULE";
}
