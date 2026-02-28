-- ============================================================
-- Velocira Auth Schema — V1
-- Creates: users, refresh_tokens, otp_tokens
-- ============================================================

-- ======================== Users ==============================

CREATE TABLE users (
    id              UUID            PRIMARY KEY,
    full_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password        VARCHAR(255),   -- nullable for Google OAuth users
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER',
    auth_provider   VARCHAR(20)     NOT NULL DEFAULT 'LOCAL',
    email_verified  BOOLEAN         NOT NULL DEFAULT FALSE,
    account_locked  BOOLEAN         NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    university_name VARCHAR(200),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Unique index on email (case-insensitive lookup)
CREATE UNIQUE INDEX idx_users_email ON users (LOWER(email));

-- ======================== Refresh Tokens =====================

CREATE TABLE refresh_tokens (
    id                UUID            PRIMARY KEY,
    user_id           UUID            NOT NULL,
    token_hash        VARCHAR(255)    NOT NULL,
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked           BOOLEAN         NOT NULL DEFAULT FALSE,
    replaced_by_token VARCHAR(255),
    device_info       VARCHAR(512),
    ip_address        VARCHAR(45),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

-- Fast lookup by hashed token
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

-- Fast lookup of all tokens for a user
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ======================== OTP Tokens =========================

CREATE TABLE otp_tokens (
    id          UUID            PRIMARY KEY,
    user_id     UUID            NOT NULL,
    otp_hash    VARCHAR(255)    NOT NULL,
    otp_type    VARCHAR(30)     NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used        BOOLEAN         NOT NULL DEFAULT FALSE,
    attempts    INT             NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_otp_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

-- Composite index for looking up valid OTPs
CREATE INDEX idx_otp_tokens_user_type ON otp_tokens (user_id, otp_type);

-- ======================== Audit Logs =========================

CREATE TABLE audit_logs (
    id          UUID            PRIMARY KEY,
    user_id     UUID,
    email       VARCHAR(255),
    action      VARCHAR(50)     NOT NULL,
    details     VARCHAR(1000),
    ip_address  VARCHAR(45),
    device_info VARCHAR(512),
    success     BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
