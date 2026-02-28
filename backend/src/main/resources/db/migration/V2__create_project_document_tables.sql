-- ============================================================
-- Velocira Schema — V2
-- Creates: projects, documents
-- Alters:  users (new profile columns)
-- ============================================================

-- ======================== Users: Profile Columns =============

ALTER TABLE users ADD COLUMN IF NOT EXISTS bio           VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_title     VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS company       VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS location      VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS website_url   VARCHAR(500);


-- ======================== Projects ===========================

CREATE TABLE projects (
    id               UUID            PRIMARY KEY,
    owner_id         UUID            NOT NULL,
    name             VARCHAR(255)    NOT NULL,
    description      VARCHAR(2000)   NOT NULL,
    type             VARCHAR(30)     NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    tech_stack       VARCHAR(500),
    industry         VARCHAR(100),
    target_audience  VARCHAR(500),
    team_size        INT,
    progress         INT             NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_projects_owner
        FOREIGN KEY (owner_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_projects_owner_id   ON projects (owner_id);
CREATE INDEX idx_projects_status     ON projects (status);
CREATE INDEX idx_projects_created_at ON projects (created_at);


-- ======================== Documents ==========================

CREATE TABLE documents (
    id                 UUID            PRIMARY KEY,
    project_id         UUID            NOT NULL,
    type               VARCHAR(40)     NOT NULL,
    status             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    title              VARCHAR(255)    NOT NULL,
    content            TEXT,
    version            INT             NOT NULL DEFAULT 1,
    word_count         INT             NOT NULL DEFAULT 0,
    ai_model           VARCHAR(50),
    generation_time_ms BIGINT,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_documents_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE,

    CONSTRAINT uq_documents_project_type
        UNIQUE (project_id, type)
);

CREATE INDEX idx_documents_project_id ON documents (project_id);
CREATE INDEX idx_documents_type       ON documents (type);
CREATE INDEX idx_documents_status     ON documents (status);
