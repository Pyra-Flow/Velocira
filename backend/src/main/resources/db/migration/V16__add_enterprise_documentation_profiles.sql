-- Original Velocira control summaries informed by public standards metadata.
-- No copyrighted standards text is stored and selecting a profile does not
-- constitute certification or legal/compliance advice.
INSERT INTO standards_profiles (
    id, profile_key, name, description, controls, source_license, owner_name, effective_date
)
VALUES
    ('10000000-0000-0000-0000-000000000003', 'ENTERPRISE', 'Enterprise documentation controls',
     'Exhaustive, traceable documentation controls for commercial multi-role products and services.',
     '[
       "Use a generation manifest, applicability decisions, section contracts, terminology, sources, assumptions, decisions, and cross-document traceability",
       "Cover business, functional, data, interface, experience, accessibility, security, privacy, quality, test, deployment, operations, support, and retirement concerns when applicable",
       "Give every normative requirement a stable type-correct identifier, source classification, rationale, failure behavior, acceptance evidence, and verification method",
       "Reject duplicate obligations, unsupported numeric targets, vague quality claims, hidden assumptions, broken trace links, invalid contracts, and malformed diagrams",
       "Keep generated guidance separate from certification, legal, and regulatory claims"
     ]'::jsonb,
     'Original internal summary informed by official public metadata; no third-party standards text included',
     'Velocira documentation engineering', DATE '2026-08-16'),
    ('10000000-0000-0000-0000-000000000004', 'REGULATED', 'Regulated-system documentation controls',
     'Enterprise controls plus conservative privacy, security, continuity, risk, audit, and evidence requirements for potentially regulated contexts.',
     '[
       "Apply all Enterprise controls",
       "Record regulatory and standards applicability as a review decision; never infer a legal obligation from industry keywords alone",
       "Maintain data classification, purpose, ownership, access, retention, deletion, export, audit, incident, continuity, and recovery decisions",
       "Trace security, privacy, accessibility, resilience, and operational requirements to risk and verification evidence",
       "Block conformance claims when thresholds, responsible owners, jurisdictions, or approval evidence are unresolved"
     ]'::jsonb,
     'Original internal summary informed by official public metadata; no third-party standards text included',
     'Velocira documentation engineering', DATE '2026-08-16'),
    ('10000000-0000-0000-0000-000000000005', 'AI_SYSTEM', 'AI-enabled product documentation controls',
     'Regulated-grade documentation controls extended for AI governance, data/model lifecycle, evaluation, human oversight, and monitoring.',
     '[
       "Apply all Enterprise controls and risk-based Regulated controls",
       "Document AI purpose, affected stakeholders, prohibited uses, data/model provenance, evaluation limits, human oversight, fallback, monitoring, incidents, and retirement",
       "Separate observed model behavior from intended requirements and recommendations",
       "Do not invent fairness, accuracy, latency, safety, or reliability thresholds; record the decision and evaluation evidence required",
       "Trace AI risks and controls through governance, mapping, measurement, management, testing, release, and production monitoring"
     ]'::jsonb,
     'Original internal summary informed by ISO/IEC 42001 and NIST AI RMF public metadata; no standards text included',
     'Velocira documentation engineering', DATE '2026-08-16')
ON CONFLICT (profile_key) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    controls = EXCLUDED.controls,
    source_license = EXCLUDED.source_license,
    owner_name = EXCLUDED.owner_name,
    effective_date = EXCLUDED.effective_date,
    active = TRUE,
    updated_at = NOW();
