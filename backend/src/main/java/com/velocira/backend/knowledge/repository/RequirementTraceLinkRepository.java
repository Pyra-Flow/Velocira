package com.velocira.backend.knowledge.repository;

import com.velocira.backend.knowledge.model.RequirementTraceLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequirementTraceLinkRepository extends JpaRepository<RequirementTraceLinkEntity, UUID> {
    List<RequirementTraceLinkEntity> findBySrsRequirementId(UUID requirementId);
}
