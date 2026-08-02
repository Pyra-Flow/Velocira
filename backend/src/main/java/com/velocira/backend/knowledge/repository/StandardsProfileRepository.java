package com.velocira.backend.knowledge.repository;

import com.velocira.backend.knowledge.model.StandardsProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StandardsProfileRepository extends JpaRepository<StandardsProfileEntity, UUID> {
    List<StandardsProfileEntity> findByActiveTrueOrderByProfileKeyAsc();
    Optional<StandardsProfileEntity> findByProfileKeyAndActiveTrue(String profileKey);
}
