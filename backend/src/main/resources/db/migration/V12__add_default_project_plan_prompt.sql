-- Keep prior prompt revisions for durable retries and make the calmer project-plan
-- prompt the active default for new work.
UPDATE prompt_templates
SET enabled = FALSE, updated_at = NOW()
WHERE template_key = 'phase2-small-structured-artifact'
  AND template_version = '1.0.0';

INSERT INTO prompt_templates (
    id, template_key, template_version, content, input_schema, output_schema, checksum, enabled
) VALUES (
    '00000000-0000-0000-0000-000000000402',
    'phase2-small-structured-artifact',
    '1.1.0',
    'Create a concise first project plan from the supplied owner brief. Use plain language. Include the problem, intended users, first-release goals, a main user flow, and clearly labelled assumptions. Do not invent implementation facts or expose model, pipeline, or internal tooling details. Return a title and ordered sections that match the output schema.',
    '{"type":"object","required":["project","artifactType"]}'::jsonb,
    '{"type":"object","required":["title","sections"]}'::jsonb,
    '2ad504854a93215886da94c265b48d5b4ae73118784802d18ba2b9d6636c8ecb',
    TRUE
) ON CONFLICT (template_key, template_version) DO NOTHING;
