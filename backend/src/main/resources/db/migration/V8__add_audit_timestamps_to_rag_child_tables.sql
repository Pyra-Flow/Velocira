-- V8: every JPA entity inherits BaseEntity.created_at and updated_at.
-- V7 intentionally created the governed-RAG child tables but omitted the
-- mutable audit timestamp on these two tables. Add it forward-only so an
-- already-migrated database remains valid and no Flyway checksum is changed.

ALTER TABLE knowledge_chunks
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

ALTER TABLE requirement_trace_links
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();
