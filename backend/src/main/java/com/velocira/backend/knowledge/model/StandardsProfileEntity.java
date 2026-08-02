package com.velocira.backend.knowledge.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/** Versioned internal control summary. It deliberately stores no third-party standards text. */
@Entity
@Table(name = "standards_profiles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StandardsProfileEntity extends BaseEntity {
    @Column(name = "profile_key", nullable = false, unique = true, length = 50)
    private String profileKey;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode controls;
    @Column(name = "source_license", nullable = false, length = 255)
    private String sourceLicense;
    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
