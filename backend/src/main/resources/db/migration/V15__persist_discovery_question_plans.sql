-- Persist the exact contextual question and its audit rationale before it is
-- shown. Completed plans are copied into immutable interview-answer evidence.
ALTER TABLE interview_sessions
    ADD COLUMN current_question_plan JSONB NOT NULL DEFAULT '{}'::jsonb;
