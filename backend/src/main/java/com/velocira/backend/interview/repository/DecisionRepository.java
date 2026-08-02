package com.velocira.backend.interview.repository;

import com.velocira.backend.interview.model.DecisionEntity;
import com.velocira.backend.interview.model.DecisionStatus;
import com.velocira.backend.interview.model.InterviewCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DecisionRepository extends JpaRepository<DecisionEntity, UUID> {

    List<DecisionEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<DecisionEntity> findBySessionIdAndCategoryAndStatus(
            UUID sessionId, InterviewCategory category, DecisionStatus status);
}
