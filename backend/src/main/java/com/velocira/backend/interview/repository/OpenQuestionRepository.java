package com.velocira.backend.interview.repository;

import com.velocira.backend.interview.model.OpenQuestionEntity;
import com.velocira.backend.interview.model.OpenQuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OpenQuestionRepository extends JpaRepository<OpenQuestionEntity, UUID> {

    List<OpenQuestionEntity> findBySessionIdOrderByRiskLevelDescCreatedAtAsc(UUID sessionId);

    Optional<OpenQuestionEntity> findBySessionIdAndQuestionKey(UUID sessionId, String questionKey);

    long countBySessionIdAndStatusNotAndMaterialTrue(UUID sessionId, OpenQuestionStatus status);
}
