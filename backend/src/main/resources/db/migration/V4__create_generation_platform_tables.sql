-- ============================================================
-- Velocira Schema — V4
-- Generation platform persistence: jobs, attempts, prompts, artifacts
-- ============================================================
--
-- The rows in this migration form the durable execution ledger for document
-- generation.  Input snapshots and artifact versions are deliberately stored
-- separately from mutable project data so a completed artifact can always be
-- traced back to the exact request, provider/model, and prompt revision.

-- ======================== Prompt templates ==================

CREATE TABLE prompt_templates (
    id                    UUID            PRIMARY KEY,
    template_key          VARCHAR(100)    NOT NULL,
    template_version      VARCHAR(50)     NOT NULL,
    content               TEXT            NOT NULL,
    input_schema          JSONB           NOT NULL DEFAULT '{}'::jsonb,
    output_schema         JSONB           NOT NULL DEFAULT '{}'::jsonb,
    checksum              VARCHAR(64)     NOT NULL,
    enabled               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_prompt_templates_key_version
        UNIQUE (template_key, template_version),
    CONSTRAINT ck_prompt_templates_key_not_blank
        CHECK (char_length(btrim(template_key)) > 0),
    CONSTRAINT ck_prompt_templates_version_not_blank
        CHECK (char_length(btrim(template_version)) > 0),
    CONSTRAINT ck_prompt_templates_checksum_sha256
        CHECK (checksum ~ '^[A-Fa-f0-9]{64}$')
);

CREATE INDEX idx_prompt_templates_key_enabled
    ON prompt_templates (template_key, enabled);


-- ======================== Generation jobs ===================

CREATE TABLE generation_jobs (
    id                    UUID            PRIMARY KEY,
    project_id            UUID            NOT NULL,
    owner_id              UUID            NOT NULL,
    requested_document_type VARCHAR(40)   NOT NULL,
    status                VARCHAR(30)     NOT NULL DEFAULT 'QUEUED',
    idempotency_key       VARCHAR(128)    NOT NULL,
    request_hash          VARCHAR(64)     NOT NULL,
    input_snapshot        JSONB           NOT NULL,
    input_snapshot_hash   VARCHAR(64)     NOT NULL,
    prompt_template_key   VARCHAR(100)    NOT NULL,
    prompt_template_version VARCHAR(50)   NOT NULL,
    correlation_id        VARCHAR(128),
    attempt_count         INT             NOT NULL DEFAULT 0,
    max_attempts          INT             NOT NULL DEFAULT 3,
    next_attempt_at       TIMESTAMP WITH TIME ZONE,
    queued_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    started_at            TIMESTAMP WITH TIME ZONE,
    completed_at          TIMESTAMP WITH TIME ZONE,
    cancel_requested      BOOLEAN         NOT NULL DEFAULT FALSE,
    cancel_requested_at   TIMESTAMP WITH TIME ZONE,
    retryable             BOOLEAN         NOT NULL DEFAULT FALSE,
    status_message        VARCHAR(500),
    error_code            VARCHAR(80),
    user_message          VARCHAR(1000),
    error_details         JSONB           NOT NULL DEFAULT '{}'::jsonb,
    document_id           UUID,
    artifact_version_id   UUID,
    lock_version          BIGINT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_generation_jobs_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_generation_jobs_owner
        FOREIGN KEY (owner_id) REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_generation_jobs_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
        ON DELETE SET NULL,
    -- A retry from a client must resolve to the same job for that account.
    CONSTRAINT uq_generation_jobs_owner_idempotency
        UNIQUE (owner_id, idempotency_key),
    CONSTRAINT ck_generation_jobs_status
        CHECK (status IN (
            'QUEUED', 'RETRIEVING', 'DRAFTING', 'VALIDATING',
            'NEEDS_INPUT', 'READY', 'FAILED', 'CANCELLED'
        )),
    CONSTRAINT ck_generation_jobs_idempotency_not_blank
        CHECK (char_length(btrim(idempotency_key)) BETWEEN 1 AND 128),
    CONSTRAINT ck_generation_jobs_request_hash_sha256
        CHECK (request_hash ~ '^[A-Fa-f0-9]{64}$'),
    CONSTRAINT ck_generation_jobs_input_snapshot_hash_sha256
        CHECK (input_snapshot_hash ~ '^[A-Fa-f0-9]{64}$'),
    CONSTRAINT ck_generation_jobs_attempt_counts
        CHECK (attempt_count >= 0 AND max_attempts >= 1 AND attempt_count <= max_attempts)
);

CREATE INDEX idx_generation_jobs_project_created
    ON generation_jobs (project_id, created_at DESC);
CREATE INDEX idx_generation_jobs_owner_status
    ON generation_jobs (owner_id, status);
CREATE INDEX idx_generation_jobs_queue
    ON generation_jobs (status, next_attempt_at, created_at)
    WHERE status IN ('QUEUED', 'FAILED');
CREATE INDEX idx_generation_jobs_correlation_id
    ON generation_jobs (correlation_id)
    WHERE correlation_id IS NOT NULL;


-- ======================== Generation runs ===================

CREATE TABLE generation_runs (
    id                    UUID            PRIMARY KEY,
    generation_job_id     UUID            NOT NULL,
    attempt_number        INT             NOT NULL,
    status                VARCHAR(20)     NOT NULL DEFAULT 'RUNNING',
    provider              VARCHAR(100),
    model                 VARCHAR(150),
    provider_request_id   VARCHAR(255),
    prompt_template_id    UUID,
    prompt_template_key   VARCHAR(100)    NOT NULL,
    prompt_template_version VARCHAR(50)   NOT NULL,
    prompt_checksum       VARCHAR(64)     NOT NULL,
    input_snapshot        JSONB           NOT NULL,
    output_metadata       JSONB           NOT NULL DEFAULT '{}'::jsonb,
    validator_outcome     JSONB           NOT NULL DEFAULT '{}'::jsonb,
    input_tokens          BIGINT,
    output_tokens         BIGINT,
    total_tokens          BIGINT,
    cost_usd              NUMERIC(14, 6),
    latency_ms            BIGINT,
    retryable             BOOLEAN         NOT NULL DEFAULT FALSE,
    started_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMP WITH TIME ZONE,
    failure_code          VARCHAR(80),
    failure_message       VARCHAR(1000),
    correlation_id        VARCHAR(128),
    lock_version          BIGINT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_generation_runs_job
        FOREIGN KEY (generation_job_id) REFERENCES generation_jobs (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_generation_runs_prompt_template
        FOREIGN KEY (prompt_template_id) REFERENCES prompt_templates (id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_generation_runs_job_attempt
        UNIQUE (generation_job_id, attempt_number),
    CONSTRAINT ck_generation_runs_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED')),
    CONSTRAINT ck_generation_runs_attempt_number
        CHECK (attempt_number >= 1),
    CONSTRAINT ck_generation_runs_prompt_checksum_sha256
        CHECK (prompt_checksum ~ '^[A-Fa-f0-9]{64}$'),
    CONSTRAINT ck_generation_runs_token_counts
        CHECK (
            (input_tokens IS NULL OR input_tokens >= 0)
            AND (output_tokens IS NULL OR output_tokens >= 0)
            AND (total_tokens IS NULL OR total_tokens >= 0)
        ),
    CONSTRAINT ck_generation_runs_cost
        CHECK (cost_usd IS NULL OR cost_usd >= 0),
    CONSTRAINT ck_generation_runs_latency
        CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

CREATE INDEX idx_generation_runs_job_attempt
    ON generation_runs (generation_job_id, attempt_number DESC);
CREATE INDEX idx_generation_runs_correlation_id
    ON generation_runs (correlation_id)
    WHERE correlation_id IS NOT NULL;


-- ======================== Artifact versions =================

CREATE TABLE artifact_versions (
    id                    UUID            PRIMARY KEY,
    project_id            UUID            NOT NULL,
    document_id           UUID            NOT NULL,
    generation_job_id     UUID            NOT NULL,
    generation_run_id     UUID,
    artifact_type         VARCHAR(40)     NOT NULL,
    version_number        INT             NOT NULL,
    title                 VARCHAR(255)    NOT NULL,
    content               TEXT            NOT NULL,
    content_sha256        VARCHAR(64)     NOT NULL,
    validation_status     VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    source_input_snapshot JSONB           NOT NULL,
    output_metadata       JSONB           NOT NULL DEFAULT '{}'::jsonb,
    validator_outcome     JSONB           NOT NULL DEFAULT '{}'::jsonb,
    provider              VARCHAR(100)    NOT NULL,
    model                 VARCHAR(150)    NOT NULL,
    prompt_template_key   VARCHAR(100)    NOT NULL,
    prompt_template_version VARCHAR(50)   NOT NULL,
    prompt_checksum       VARCHAR(64)     NOT NULL,
    generated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_artifact_versions_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_artifact_versions_document
        FOREIGN KEY (document_id) REFERENCES documents (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_artifact_versions_job
        FOREIGN KEY (generation_job_id) REFERENCES generation_jobs (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_artifact_versions_run
        FOREIGN KEY (generation_run_id) REFERENCES generation_runs (id)
        ON DELETE SET NULL,
    CONSTRAINT uq_artifact_versions_document_version
        UNIQUE (document_id, version_number),
    CONSTRAINT ck_artifact_versions_version_number
        CHECK (version_number >= 1),
    CONSTRAINT ck_artifact_versions_validation_status
        CHECK (validation_status IN ('PENDING', 'PASSED', 'FAILED', 'NEEDS_REVIEW')),
    CONSTRAINT ck_artifact_versions_content_sha256
        CHECK (content_sha256 ~ '^[A-Fa-f0-9]{64}$'),
    CONSTRAINT ck_artifact_versions_prompt_checksum_sha256
        CHECK (prompt_checksum ~ '^[A-Fa-f0-9]{64}$')
);

CREATE INDEX idx_artifact_versions_project_type
    ON artifact_versions (project_id, artifact_type, version_number DESC);
CREATE INDEX idx_artifact_versions_job
    ON artifact_versions (generation_job_id);

-- This foreign key is added after artifact_versions because the job keeps a
-- direct pointer to the terminal artifact for fast status reads.
ALTER TABLE generation_jobs
    ADD CONSTRAINT fk_generation_jobs_artifact_version
    FOREIGN KEY (artifact_version_id) REFERENCES artifact_versions (id)
    ON DELETE SET NULL;
