-- V13 reset wholly fabricated interviews. Also preserve any real answer in a
-- mixed session while retiring the remaining brief-derived rows.
UPDATE projects p
SET status = 'DISCOVERY', progress = 0
FROM interview_sessions s
WHERE p.id = s.project_id
  AND p.status NOT IN ('ARCHIVED', 'GENERATING')
  AND EXISTS (
      SELECT 1
      FROM interview_answers a
      WHERE a.session_id = s.id
        AND a.is_current = TRUE
        AND a.source = 'PROJECT_BRIEF'
        AND a.evidence ->> 'derived' = 'true'
  );

UPDATE interview_sessions s
SET status = 'IN_PROGRESS',
    confirmed_at = NULL,
    reopened_at = NOW(),
    canonical_brief = '{}'::jsonb,
    readiness_snapshot = '{}'::jsonb
WHERE EXISTS (
    SELECT 1
    FROM interview_answers a
    WHERE a.session_id = s.id
      AND a.is_current = TRUE
      AND a.source = 'PROJECT_BRIEF'
      AND a.evidence ->> 'derived' = 'true'
);

UPDATE interview_answers
SET is_current = FALSE
WHERE is_current = TRUE
  AND source = 'PROJECT_BRIEF'
  AND evidence ->> 'derived' = 'true';
