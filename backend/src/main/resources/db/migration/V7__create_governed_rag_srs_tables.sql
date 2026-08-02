-- ============================================================
-- Velocira Schema — V7
-- Governed project evidence, internal standards controls, and SRS traceability
-- ============================================================

CREATE TABLE knowledge_sources (
    id                    UUID PRIMARY KEY,
    project_id            UUID NOT NULL,
    owner_id              UUID NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    original_filename     VARCHAR(255) NOT NULL,
    media_type            VARCHAR(100) NOT NULL,
    classification        VARCHAR(40) NOT NULL DEFAULT 'PROJECT_EVIDENCE',
    status                VARCHAR(40) NOT NULL DEFAULT 'PENDING_REVIEW',
    extracted_text        TEXT NOT NULL,
    content_sha256        VARCHAR(64) NOT NULL,
    scan_metadata         JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_version        INT NOT NULL DEFAULT 1,
    expires_at            TIMESTAMP WITH TIME ZONE,
    approved_at           TIMESTAMP WITH TIME ZONE,
    deleted_at            TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_knowledge_sources_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_sources_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_knowledge_sources_status CHECK (status IN (
        'PENDING_REVIEW', 'APPROVED', 'QUARANTINED', 'REJECTED', 'EXPIRED', 'DELETED'
    )),
    CONSTRAINT ck_knowledge_sources_classification CHECK (classification IN ('PROJECT_EVIDENCE', 'INTERNAL_CONTROL'))
);

CREATE INDEX idx_knowledge_sources_owner_project_status
    ON knowledge_sources(owner_id, project_id, status);

CREATE TABLE knowledge_chunks (
    id                    UUID PRIMARY KEY,
    source_id             UUID NOT NULL,
    project_id            UUID NOT NULL,
    owner_id              UUID NOT NULL,
    chunk_ordinal         INT NOT NULL,
    content               TEXT NOT NULL,
    content_sha256        VARCHAR(64) NOT NULL,
    qdrant_point_id       UUID NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_knowledge_chunks_source FOREIGN KEY (source_id) REFERENCES knowledge_sources(id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_chunks_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_chunks_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_knowledge_chunks_source_ordinal UNIQUE (source_id, chunk_ordinal),
    CONSTRAINT ck_knowledge_chunks_ordinal CHECK (chunk_ordinal >= 0)
);

CREATE INDEX idx_knowledge_chunks_owner_project ON knowledge_chunks(owner_id, project_id);

CREATE TABLE standards_profiles (
    id                    UUID PRIMARY KEY,
    profile_key           VARCHAR(50) NOT NULL UNIQUE,
    name                  VARCHAR(120) NOT NULL,
    description           TEXT NOT NULL,
    controls              JSONB NOT NULL,
    source_license        VARCHAR(255) NOT NULL,
    owner_name            VARCHAR(120) NOT NULL,
    effective_date        DATE NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO standards_profiles (id, profile_key, name, description, controls, source_license, owner_name, effective_date)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'STARTER', 'Starter SRS controls',
     'Concise internal controls for a reviewable early-stage SRS. This is not a copy of any copyrighted standard.',
     '["Scope and exclusions are explicit", "Each requirement is atomic and testable", "Every requirement has priority, source or assumption, acceptance criteria, and verification", "Unknowns remain visible"]'::jsonb,
     'Internal control summary; no third-party standards text included', 'Velocira product team', CURRENT_DATE),
    ('10000000-0000-0000-0000-000000000002', 'STARTUP', 'Startup SRS controls',
     'Internal controls for a startup-ready SRS with risk, security, availability, and integration coverage. This is not a copy of any copyrighted standard.',
     '["All Starter controls", "Security and privacy constraints are stated or marked unknown", "Integration and operational failure behaviour is explicit", "Non-functional quality targets have measurable verification"]'::jsonb,
     'Internal control summary; no third-party standards text included', 'Velocira product team', CURRENT_DATE)
ON CONFLICT (profile_key) DO NOTHING;

CREATE TABLE srs_versions (
    id                    UUID PRIMARY KEY,
    project_id            UUID NOT NULL,
    owner_id              UUID NOT NULL,
    profile_id            UUID NOT NULL,
    version_number        INT NOT NULL,
    status                VARCHAR(40) NOT NULL DEFAULT 'NEEDS_REVIEW',
    brief_snapshot        JSONB NOT NULL,
    srs_content           JSONB NOT NULL,
    validation_outcome    JSONB NOT NULL DEFAULT '{}'::jsonb,
    citation_coverage     NUMERIC(5,2) NOT NULL DEFAULT 0,
    provider              VARCHAR(100) NOT NULL,
    model                 VARCHAR(150) NOT NULL,
    prompt_version        VARCHAR(64) NOT NULL,
    generated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    approved_at           TIMESTAMP WITH TIME ZONE,
    change_request        TEXT,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_srs_versions_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_srs_versions_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_srs_versions_profile FOREIGN KEY (profile_id) REFERENCES standards_profiles(id),
    CONSTRAINT uq_srs_versions_project_version UNIQUE (project_id, version_number),
    CONSTRAINT ck_srs_versions_status CHECK (status IN ('DRAFT', 'NEEDS_REVIEW', 'APPROVED', 'CHANGES_REQUESTED')),
    CONSTRAINT ck_srs_versions_coverage CHECK (citation_coverage >= 0 AND citation_coverage <= 100)
);

CREATE INDEX idx_srs_versions_owner_project ON srs_versions(owner_id, project_id, version_number DESC);

CREATE TABLE srs_requirements (
    id                    UUID PRIMARY KEY,
    srs_version_id        UUID NOT NULL,
    requirement_id        VARCHAR(80) NOT NULL,
    requirement_type      VARCHAR(30) NOT NULL,
    priority              VARCHAR(20) NOT NULL,
    statement             TEXT NOT NULL,
    rationale             TEXT NOT NULL,
    acceptance_criteria   TEXT NOT NULL,
    source_kind           VARCHAR(20) NOT NULL,
    source_detail         TEXT NOT NULL,
    verification_method   VARCHAR(30) NOT NULL,
    quality_outcome       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_srs_requirements_version FOREIGN KEY (srs_version_id) REFERENCES srs_versions(id) ON DELETE CASCADE,
    CONSTRAINT uq_srs_requirements_version_id UNIQUE (srs_version_id, requirement_id),
    CONSTRAINT ck_srs_requirements_source_kind CHECK (source_kind IN ('CITATION', 'ASSUMPTION'))
);

CREATE TABLE requirement_trace_links (
    id                    UUID PRIMARY KEY,
    srs_requirement_id    UUID NOT NULL,
    knowledge_source_id   UUID,
    knowledge_chunk_id    UUID,
    link_type             VARCHAR(30) NOT NULL DEFAULT 'EVIDENCE',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_requirement_trace_requirement FOREIGN KEY (srs_requirement_id) REFERENCES srs_requirements(id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_trace_source FOREIGN KEY (knowledge_source_id) REFERENCES knowledge_sources(id) ON DELETE SET NULL,
    CONSTRAINT fk_requirement_trace_chunk FOREIGN KEY (knowledge_chunk_id) REFERENCES knowledge_chunks(id) ON DELETE SET NULL,
    CONSTRAINT ck_requirement_trace_link_type CHECK (link_type IN ('EVIDENCE', 'ASSUMPTION', 'CONTROL'))
);

CREATE INDEX idx_requirement_trace_requirement ON requirement_trace_links(srs_requirement_id);
