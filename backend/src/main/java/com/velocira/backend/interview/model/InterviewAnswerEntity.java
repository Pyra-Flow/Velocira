package com.velocira.backend.interview.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable revision of a user response, including explicit unknown and skip evidence. */
@Entity
@Table(name = "interview_answers", indexes = {
        @Index(name = "idx_interview_answers_session_category", columnList = "session_id,category,created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAnswerEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private InterviewCategory category;

    @Column(name = "question_key", nullable = false, length = 120)
    private String questionKey;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(name = "why_we_ask", nullable = false, columnDefinition = "TEXT")
    private String whyWeAsk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewAnswerDisposition disposition;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private JsonNode evidence = JsonNodeFactory.instance.objectNode();

    @Column(name = "revision_number", nullable = false)
    @Builder.Default
    private int revisionNumber = 1;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private boolean current = true;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String source = "USER";
}
