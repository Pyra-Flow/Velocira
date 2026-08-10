-- Persist the exact visual settings used to create each new immutable styled export.
-- Existing rows intentionally remain NULL: they predate the renderer style contract, and
-- relabelling their immutable bytes with a newer default would be historically inaccurate.

ALTER TABLE documentation_export_jobs
    ADD COLUMN export_template VARCHAR(30);

ALTER TABLE documentation_export_jobs
    ADD COLUMN export_theme VARCHAR(30);

ALTER TABLE documentation_export_jobs
    ADD COLUMN export_layout VARCHAR(30);

ALTER TABLE documentation_export_jobs
    ADD CONSTRAINT ck_documentation_export_template
        CHECK (export_template IN ('EXECUTIVE', 'TECHNICAL', 'MINIMAL'));

ALTER TABLE documentation_export_jobs
    ADD CONSTRAINT ck_documentation_export_theme
        CHECK (export_theme IN ('SIGNAL', 'OCEAN', 'VIOLET', 'EMERALD', 'MONOCHROME', 'COMMAND'));

ALTER TABLE documentation_export_jobs
    ADD CONSTRAINT ck_documentation_export_layout
        CHECK (export_layout IN ('STANDARD', 'COMPACT', 'PRESENTATION'));
