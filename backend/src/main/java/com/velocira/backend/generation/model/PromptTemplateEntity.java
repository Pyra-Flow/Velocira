package com.velocira.backend.generation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.velocira.backend.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Versioned server-side prompt template.
 *
 * <p>Template contents, schemas, checksum, and identity are immutable once
 * persisted. The enabled flag permits a revision to be retired without
 * deleting evidence required by historical generation runs.</p>
 */
@Entity
@Table(name = "prompt_templates", indexes = {
        @Index(name = "idx_prompt_templates_key_enabled", columnList = "template_key,enabled")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_prompt_templates_key_version", columnNames = { "template_key", "template_version" })
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplateEntity extends BaseEntity {

    /** Stable family name used by trusted generation configuration. */
    @Column(name = "template_key", nullable = false, length = 100, updatable = false)
    private String templateKey;

    /** Immutable revision identifier within a template family. */
    @Column(name = "template_version", nullable = false, length = 50, updatable = false)
    private String templateVersion;

    /** Prompt source text; it is never supplied by a browser request. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    /** JSON schema accepted by this revision. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", nullable = false, updatable = false)
    @Builder.Default
    private JsonNode inputSchema = JsonNodeFactory.instance.objectNode();

    /** JSON schema expected from this revision. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", nullable = false, updatable = false)
    @Builder.Default
    private JsonNode outputSchema = JsonNodeFactory.instance.objectNode();

    /** SHA-256 of the canonical prompt content and schemas. */
    @Column(name = "checksum", nullable = false, length = 64, updatable = false)
    private String checksum;

    /** Whether new jobs may select this revision. Historical runs remain valid. */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Retires or restores this revision without changing its stored content. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
