ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS creation_idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_projects_owner_creation_idempotency_key
    ON projects (owner_id, creation_idempotency_key)
    WHERE creation_idempotency_key IS NOT NULL;

-- The brief auto-fill experiment recorded one description as every answer.
-- Retire only sessions made entirely of those derived rows; owner-entered
-- evidence is preserved, and the project resumes at its real questions.
UPDATE projects p
SET status = 'DISCOVERY', progress = 0
FROM interview_sessions s
WHERE p.id = s.project_id
  AND p.status NOT IN ('ARCHIVED', 'GENERATING')
  AND s.id IN (
      SELECT session_id
      FROM interview_answers
      WHERE is_current = TRUE
      GROUP BY session_id
      HAVING bool_and(source = 'PROJECT_BRIEF' AND evidence ->> 'derived' = 'true')
  );

UPDATE interview_sessions s
SET status = 'IN_PROGRESS',
    confirmed_at = NULL,
    reopened_at = NOW(),
    canonical_brief = '{}'::jsonb,
    readiness_snapshot = '{}'::jsonb
WHERE s.id IN (
    SELECT session_id
    FROM interview_answers
    WHERE is_current = TRUE
    GROUP BY session_id
    HAVING bool_and(source = 'PROJECT_BRIEF' AND evidence ->> 'derived' = 'true')
);

UPDATE interview_answers
SET is_current = FALSE
WHERE is_current = TRUE
  AND session_id IN (
      SELECT session_id
      FROM interview_answers
      WHERE is_current = TRUE
      GROUP BY session_id
      HAVING bool_and(source = 'PROJECT_BRIEF' AND evidence ->> 'derived' = 'true')
  );
