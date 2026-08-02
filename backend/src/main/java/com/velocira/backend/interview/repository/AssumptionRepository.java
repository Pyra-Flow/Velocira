package com.velocira.backend.interview.repository;

import com.velocira.backend.interview.model.AssumptionEntity;
import com.velocira.backend.interview.model.AssumptionStatus;
import com.velocira.backend.interview.model.InterviewCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssumptionRepository extends JpaRepository<AssumptionEntity, UUID> {

    List<AssumptionEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<AssumptionEntity> findBySessionIdAndCategoryAndStatus(
            UUID sessionId, InterviewCategory category, AssumptionStatus status);
}
