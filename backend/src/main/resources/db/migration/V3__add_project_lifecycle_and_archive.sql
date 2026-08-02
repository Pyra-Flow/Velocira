-- Project lifecycle vocabulary and reversible archival.
-- V2 stored status as VARCHAR, so this migration is safe for existing data.

ALTER TABLE projects
    ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS archived_from_status VARCHAR(30);

-- COMPLETE was the pre-Phase-1 terminal name. Preserve its intent as APPROVED.
UPDATE projects
SET status = 'APPROVED'
WHERE status = 'COMPLETE';

CREATE INDEX IF NOT EXISTS idx_projects_owner_status ON projects (owner_id, status);
CREATE INDEX IF NOT EXISTS idx_projects_archived_at ON projects (archived_at);
