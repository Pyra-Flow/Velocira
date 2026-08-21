# Velocira AI Service

An isolated FastAPI service for the Phase 2 generation path. The Spring Boot
application remains the public API, owner of authorization, jobs, persistence,
and idempotency. This service accepts an already-authorized internal request,
calls a provider through a narrow interface, checks structured output, and
returns a reproducible artifact result.

## What is included

- Liveness and readiness endpoints: `GET /health` and `GET /ready`.
- Typed generation request and response schemas.
- A deterministic local provider (`deterministic`) that has no network access
  and requires no provider key. It is suitable for integration tests only, not
  standards-quality document generation.
- Provider abstraction, structured-output validation, content-safety gate,
  and retry/error taxonomy.
- Correlation-ID propagation via `X-Correlation-Id`; raw prompts and input
  snapshots are never logged.
- Optional internal caller token via `X-Internal-Token`.
- Typed `POST /v1/discovery/plan` contract that selects one server-owned
  discovery category and returns validated, context-specific wording and
  answer options. It considers prior answers, selected options, visible gaps,
  and bounded owner-approved evidence excerpts while treating all supplied
  content as untrusted data.
- Discovery planner output cannot add categories, change server-owned risk or
  answer cardinality, cite unknown context anchors, or turn an unconfirmed
  detail into a project fact. The v3 quality gate also rejects generic or
  mechanically interpolated wording, semantic repeats, unsupported compliance
  claims, shallow answer labels, missing uncertainty choices, and overloaded
  questions. It also rejects unsupported numeric targets and category-incomplete
  questions: scope must include what waits, workflows include recovery, quality
  includes a measurable threshold, metrics include baseline/target/review
  window, and constraints identify what is fixed and what may move. A
  decision-specific deterministic strategist remains the outage, invalid-output,
  and clean-clone fallback.
- Optional Gemini provider: `gemini-3.1-pro-preview` for SRS and document
  generation, `gemini-3.6-flash` for discovery-question selection, and
  `gemini-3.5-flash` only as the discovery fallback for retryable provider
  failures. Document requests never fall through to a Flash model. The API key
  is read only by this service through `GEMINI_API_KEY`.

## Local run

Use Python 3.11 or newer.

```bash
cd ai-service
python -m venv .venv
# PowerShell
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

The deterministic provider is the safe default. To use Gemini during local
development, keep `GEMINI_API_KEY` in an uncommitted `.env`, set
`AI_SERVICE_PROVIDER=gemini`, `AI_SERVICE_MODEL=gemini-3.1-pro-preview`,
`AI_SERVICE_DISCOVERY_MODEL=gemini-3.6-flash`, and
`AI_SERVICE_FALLBACK_MODEL=gemini-3.5-flash`. In staging and production, set `AI_SERVICE_INTERNAL_TOKEN` to a secret
provided through the deployment secret manager. The Spring Boot orchestrator
must send the same value in `X-Internal-Token`.

## Test

```bash
cd ai-service
python -m pytest -q
```

## API contract

`POST /v1/generate` is an **internal-only** endpoint. It accepts a job ID,
project context, artifact type, and prompt `{key, version, content}`. It
returns a stable envelope with `success`, provider/model metadata, a structured
artifact, validation result, usage, latency, and a safe error object when the
request fails. The Spring orchestrator owns durable idempotency and must persist
the request snapshot, provider/model, prompt version, validator outcome,
timing, and usage; it must not trust browser-supplied provider data.

Example request:

```json
{
  "job_id": "e4d4f791-72e8-40b3-a555-86f511665d25",
  "project": {
    "id": "8b6c4dd8-0a96-4896-b1d9-65fd0b488ad3",
    "name": "Example",
    "description": "A test project",
    "type": "WEB_APPLICATION"
  },
  "artifact_type": "test-artifact",
  "prompt": {
    "key": "small-structured-test-artifact",
    "version": "1.0.0",
    "content": "Create a small structured test artifact."
  }
}
```

This endpoint deliberately does not expose a provider credential or model
configuration to browsers. Provider credentials remain server-side only.

The Spring interview service persists the exact validated question plan before
display and copies its selection reason, missing-requirement description,
source anchors, confirmed context, assumptions requiring validation, ranked
candidate scores, planner, model, wording, and options into the immutable
answer evidence. Suggested choices are consequence-bearing decision patterns,
not fabricated founder answers; legitimate uncertainty remains an explicit
`not-decided` choice and the UI always provides a custom write-in path. Simple
products require only the core discovery areas; deterministic complexity
signals add deeper stakeholder, data, integration, business-rule, exclusion,
and risk questions for regulated or technically complex projects. Features
explicitly excluded or deferred do not inflate complexity, while a material
rule such as a cutoff or override adds only the targeted business-rule question.
`not-decided` cannot be mixed with confirmed choices and remains a generation
blocker. Tentative or contradictory answers remain visible and can trigger a
precise revision question instead of becoming an invented requirement.

## Governed SRS compiler

`POST /v1/srs/generate` compiles the confirmed brief and approved evidence into
the versioned `srs-compiler-v2` schema. `STANDARD` mode uses one bounded Pro
drafting pass. `EXHAUSTIVE` mode uses four focused Pro workstreams—product,
data/interfaces, trust, and quality/operations—and deterministically merges
them before normalization and validation. The 25,000–50,000 word range is a
depth target only when the supplied context supports it; validation rejects
duplicates, malformed identifiers, vague or non-atomic requirements,
unsupported numeric targets, missing evidence and acceptance coverage, and
secret-like output.

The compiler records requested and actual models, prompt version, timestamps,
retry count, source context, workstream provenance, section contracts, and
section validation. Final SRS content always uses the configured Pro model and
never routes through either discovery Flash model. The standards applicability
engine in `app/standards.py` selects current guidance by project conditions and
stores only original structural rules plus official-source metadata; it does
not reproduce standards text or claim certification.

The Spring service persists the validated canonical SRS, requirements,
evidence anchors, and generated package snapshot. The package compiler derives
16 synchronized artifacts—including architecture, C4/workflows, data,
security, testing, delivery, operations, manuals, risks, traceability, and a
non-fabricating OpenAPI shell—from that snapshot. DOCX, PDF, Markdown, source
diagrams, JSON/YAML and ZIP exports are rendered from the same canonical model.
