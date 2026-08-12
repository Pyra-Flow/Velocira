-- Persist the exact visual settings used to create each new immutable styled export.
-- Existing rows intentionally remain NULL: they predate the renderer style contract, and
-- relabelling their immutable bytes with a newer default would be historically inaccurate.

ALTER TABLE documentation_export_jobs
    ADD COLUMN IF NOT EXISTS export_template VARCHAR(30);

ALTER TABLE documentation_export_jobs
    ADD COLUMN IF NOT EXISTS export_theme VARCHAR(30);

ALTER TABLE documentation_export_jobs
    ADD COLUMN IF NOT EXISTS export_layout VARCHAR(30);

-- The Spring Boot 4 Flyway module is now explicit. Some development databases
-- therefore have these columns from a prior schema update but no Flyway row.
-- Make the historical transition safe to apply exactly once in either state.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_documentation_export_template') THEN
        ALTER TABLE documentation_export_jobs
            ADD CONSTRAINT ck_documentation_export_template
                CHECK (export_template IN ('EXECUTIVE', 'TECHNICAL', 'MINIMAL'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_documentation_export_theme') THEN
        ALTER TABLE documentation_export_jobs
            ADD CONSTRAINT ck_documentation_export_theme
                CHECK (export_theme IN ('SIGNAL', 'OCEAN', 'VIOLET', 'EMERALD', 'MONOCHROME', 'COMMAND'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_documentation_export_layout') THEN
        ALTER TABLE documentation_export_jobs
            ADD CONSTRAINT ck_documentation_export_layout
                CHECK (export_layout IN ('STANDARD', 'COMPACT', 'PRESENTATION'));
    END IF;
END $$;
