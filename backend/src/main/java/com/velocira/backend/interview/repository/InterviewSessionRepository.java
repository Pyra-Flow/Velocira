package com.velocira.backend.interview.repository;

import com.velocira.backend.interview.model.InterviewSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, UUID> {

    Optional<InterviewSessionEntity> findByProjectIdAndOwnerId(UUID projectId, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InterviewSessionEntity s WHERE s.project.id = :projectId AND s.owner.id = :ownerId")
    Optional<InterviewSessionEntity> findByProjectIdAndOwnerIdForUpdate(
            @Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId);
}
