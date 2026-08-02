-- ============================================================
-- Velocira Schema — V6
-- Adaptive discovery interview and canonical project brief
-- ============================================================

CREATE TABLE interview_sessions (
    id                    UUID PRIMARY KEY,
    project_id            UUID NOT NULL,
    owner_id              UUID NOT NULL,
    status                VARCHAR(40) NOT NULL DEFAULT 'IN_PROGRESS',
    canonical_brief       JSONB NOT NULL DEFAULT '{}'::jsonb,
    readiness_snapshot    JSONB NOT NULL DEFAULT '{}'::jsonb,
    brief_version         INT NOT NULL DEFAULT 0,
    confirmed_at          TIMESTAMP WITH TIME ZONE,
    reopened_at           TIMESTAMP WITH TIME ZONE,
    lock_version          BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_interview_sessions_project UNIQUE (project_id),
    CONSTRAINT fk_interview_sessions_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_sessions_owner FOREIGN KEY (owner_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_interview_sessions_status CHECK (status IN (
        'IN_PROGRESS', 'READY_FOR_CONFIRMATION', 'CONFIRMED'
    )),
    CONSTRAINT ck_interview_sessions_brief_version CHECK (brief_version >= 0)
);

CREATE INDEX idx_interview_sessions_owner_status
    ON interview_sessions (owner_id, status);

CREATE TABLE interview_answers (
    id                    UUID PRIMARY KEY,
    session_id            UUID NOT NULL,
    category              VARCHAR(60) NOT NULL,
    question_key          VARCHAR(120) NOT NULL,
    question_text         TEXT NOT NULL,
    why_we_ask            TEXT NOT NULL,
    disposition           VARCHAR(20) NOT NULL,
    answer_text           TEXT,
    evidence              JSONB NOT NULL DEFAULT '{}'::jsonb,
    revision_number       INT NOT NULL DEFAULT 1,
    is_current            BOOLEAN NOT NULL DEFAULT TRUE,
    source                VARCHAR(30) NOT NULL DEFAULT 'USER',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_interview_answers_session FOREIGN KEY (session_id)
        REFERENCES interview_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_interview_answers_disposition CHECK (disposition IN ('ANSWERED', 'UNKNOWN', 'SKIPPED')),
    CONSTRAINT ck_interview_answers_revision CHECK (revision_number >= 1),
    CONSTRAINT ck_interview_answers_answer CHECK (
        (disposition = 'ANSWERED' AND char_length(btrim(COALESCE(answer_text, ''))) > 0)
        OR disposition IN ('UNKNOWN', 'SKIPPED')
    )
);

CREATE UNIQUE INDEX uq_interview_answers_current_question
    ON interview_answers (session_id, question_key) WHERE is_current;
CREATE INDEX idx_interview_answers_session_category
    ON interview_answers (session_id, category, created_at DESC);

CREATE TABLE assumptions (
    id                    UUID PRIMARY KEY,
    session_id            UUID NOT NULL,
    source_answer_id      UUID,
    category              VARCHAR(60) NOT NULL,
    statement             TEXT NOT NULL,
    rationale             TEXT NOT NULL,
    impact                VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    is_material           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_assumptions_session FOREIGN KEY (session_id)
        REFERENCES interview_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_assumptions_answer FOREIGN KEY (source_answer_id)
        REFERENCES interview_answers (id) ON DELETE SET NULL,
    CONSTRAINT ck_assumptions_impact CHECK (impact IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_assumptions_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_assumptions_session_status ON assumptions (session_id, status);

CREATE TABLE open_questions (
    id                    UUID PRIMARY KEY,
    session_id            UUID NOT NULL,
    source_answer_id      UUID,
    category              VARCHAR(60) NOT NULL,
    question_key          VARCHAR(120) NOT NULL,
    question_text         TEXT NOT NULL,
    reason                TEXT NOT NULL,
    risk_level            VARCHAR(20) NOT NULL,
    status                VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    is_material           BOOLEAN NOT NULL DEFAULT TRUE,
    origin                VARCHAR(30) NOT NULL DEFAULT 'READINESS_RULE',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_open_questions_session FOREIGN KEY (session_id)
        REFERENCES interview_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_open_questions_answer FOREIGN KEY (source_answer_id)
        REFERENCES interview_answers (id) ON DELETE SET NULL,
    CONSTRAINT uq_open_questions_session_key UNIQUE (session_id, question_key),
    CONSTRAINT ck_open_questions_risk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_open_questions_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED_UNKNOWN', 'RESOLVED'))
);

CREATE INDEX idx_open_questions_session_status ON open_questions (session_id, status, risk_level);

CREATE TABLE decisions (
    id                    UUID PRIMARY KEY,
    session_id            UUID NOT NULL,
    source_answer_id      UUID,
    category              VARCHAR(60) NOT NULL,
    statement             TEXT NOT NULL,
    rationale             TEXT NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_decisions_session FOREIGN KEY (session_id)
        REFERENCES interview_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_decisions_answer FOREIGN KEY (source_answer_id)
        REFERENCES interview_answers (id) ON DELETE SET NULL,
    CONSTRAINT ck_decisions_status CHECK (status IN ('ACTIVE', 'SUPERSEDED'))
);

CREATE INDEX idx_decisions_session_status ON decisions (session_id, status);
