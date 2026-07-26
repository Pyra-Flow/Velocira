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
  discovery question. The planner cannot author a business rule or change
  question wording.
- Optional Gemini provider: `gemini-3.6-flash` primary with
  `gemini-3.5-flash` fallback for retryable provider failures. The API key is
  read only by this service through `GEMINI_API_KEY`.

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
`AI_SERVICE_PROVIDER=gemini`, `AI_SERVICE_MODEL=gemini-3.6-flash`, and
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
