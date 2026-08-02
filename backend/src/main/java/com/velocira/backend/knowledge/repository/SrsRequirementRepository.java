package com.velocira.backend.knowledge.repository;

import com.velocira.backend.knowledge.model.SrsRequirementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SrsRequirementRepository extends JpaRepository<SrsRequirementEntity, UUID> {
    List<SrsRequirementEntity> findBySrsVersionIdOrderByRequirementIdAsc(UUID srsVersionId);
}
