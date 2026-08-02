-- Server-controlled prompt revision for the deliberately small Phase 2 test artifact.
-- The checksum is SHA-256 of the exact UTF-8 prompt content below; the worker
-- verifies it before every request so a changed prompt cannot silently alter a run.

INSERT INTO prompt_templates (
    id,
    template_key,
    template_version,
    content,
    input_schema,
    output_schema,
    checksum,
    enabled
) VALUES (
    '00000000-0000-0000-0000-000000000401',
    'phase2-small-structured-artifact',
    '1.0.0',
    'You are Velocira''s Phase 2 structured-artifact test generator. Produce a concise Markdown software-requirements test artifact from the supplied project context. Clearly label assumptions. Do not invent implementation facts. Return a title, content, and ordered sections that match the output schema.',
    '{"type":"object","required":["project","artifactType"]}'::jsonb,
    '{"type":"object","required":["title","sections"]}'::jsonb,
    '399e04022492711e0255282983ff0ade4f428d91f135b297ec6e84e16748e2d7',
    TRUE
) ON CONFLICT (template_key, template_version) DO NOTHING;
