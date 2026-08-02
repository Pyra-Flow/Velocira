package com.velocira.backend.interview.repository;

import com.velocira.backend.interview.model.InterviewAnswerEntity;
import com.velocira.backend.interview.model.InterviewCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswerEntity, UUID> {

    List<InterviewAnswerEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<InterviewAnswerEntity> findBySessionIdAndCurrentTrueOrderByCreatedAtAsc(UUID sessionId);

    Optional<InterviewAnswerEntity> findByIdAndSessionId(UUID id, UUID sessionId);

    Optional<InterviewAnswerEntity> findBySessionIdAndQuestionKeyAndCurrentTrue(UUID sessionId, String questionKey);

    List<InterviewAnswerEntity> findBySessionIdAndCategoryAndCurrentTrue(
            UUID sessionId, InterviewCategory category);

    List<InterviewAnswerEntity> findBySessionIdAndCategoryInAndCurrentTrue(
            UUID sessionId, Collection<InterviewCategory> categories);
}
