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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** User-provided decision extracted transparently from answer evidence, never invented by a rule. */
@Entity
@Table(name = "decisions", indexes = @Index(name = "idx_decisions_session_status", columnList = "session_id,status"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;

    @Column(name = "source_answer_id")
    private UUID sourceAnswerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private InterviewCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DecisionStatus status = DecisionStatus.ACTIVE;
}
