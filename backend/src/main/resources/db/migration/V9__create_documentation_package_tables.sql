-- Phase 5: versioned linked documentation packages and immutable exports.

CREATE TABLE documentation_packages (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    srs_version_id UUID NOT NULL REFERENCES srs_versions(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    canonical_model JSONB NOT NULL,
    validation_outcome JSONB NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_documentation_packages_project_version UNIQUE (project_id, version_number)
);
CREATE INDEX idx_documentation_packages_project_owner ON documentation_packages(project_id, owner_id);

CREATE TABLE documentation_artifacts (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES documentation_packages(id) ON DELETE CASCADE,
    artifact_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    source_format VARCHAR(30) NOT NULL,
    source_content TEXT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    validation_outcome JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_documentation_artifacts_package_type UNIQUE (package_id, artifact_type),
    CONSTRAINT ck_documentation_artifacts_checksum CHECK (checksum ~ '^[A-Fa-f0-9]{64}$')
);
CREATE INDEX idx_documentation_artifacts_package ON documentation_artifacts(package_id);

CREATE TABLE documentation_trace_links (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES documentation_packages(id) ON DELETE CASCADE,
    srs_requirement_id UUID NOT NULL REFERENCES srs_requirements(id) ON DELETE RESTRICT,
    requirement_key VARCHAR(80) NOT NULL,
    use_case_id VARCHAR(80),
    entity_id VARCHAR(80),
    api_operation_id VARCHAR(120),
    acceptance_criterion_id VARCHAR(120) NOT NULL,
    source_kind VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_documentation_trace_requirement UNIQUE (package_id, srs_requirement_id)
);
CREATE INDEX idx_documentation_trace_package ON documentation_trace_links(package_id);

CREATE TABLE documentation_export_jobs (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES documentation_packages(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    format VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    byte_size BIGINT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    error_message VARCHAR(1000),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_documentation_export_checksum CHECK (content_sha256 ~ '^[A-Fa-f0-9]{64}$')
);
CREATE INDEX idx_documentation_exports_package_owner ON documentation_export_jobs(package_id, owner_id);
