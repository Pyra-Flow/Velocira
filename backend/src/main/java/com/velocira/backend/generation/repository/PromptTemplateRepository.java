package com.velocira.backend.generation.repository;

import com.velocira.backend.generation.model.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence access for versioned, server-controlled prompt templates. */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, UUID> {

    Optional<PromptTemplateEntity> findByTemplateKeyAndTemplateVersion(String templateKey, String templateVersion);

    Optional<PromptTemplateEntity> findFirstByTemplateKeyAndEnabledTrueOrderByCreatedAtDesc(String templateKey);

    List<PromptTemplateEntity> findByTemplateKeyOrderByCreatedAtDesc(String templateKey);
}
