# Phase 4 — Governed RAG and SRS operations

## What is intentionally in scope

- Owner-approved UTF-8 text, Markdown, and JSON project evidence, limited to 1 MB per upload.
- A text extraction/chunking path with immutable source hashes and durable chunk/source provenance.
- Qdrant retrieval filtered by both `owner_id` and `project_id`, with a second filter check in the AI service and a Spring-side provenance check before generation.
- Concise internal Starter and Startup control summaries. They are not copies of IEEE, OWASP, or any other licensed standards text.
- A bounded SRS contract: IDs, priority, rationale, acceptance criteria, source/assumption, verification method, citations, validation outcome, and persisted trace links.

## Local Docker workflow

From the repository root, configure the uncommitted `.env` and run:

```powershell
docker compose up --build --wait
```

The Compose network gives the AI service `http://qdrant:6333`. When the AI service is run outside Docker, set `AI_SERVICE_QDRANT_URL=http://localhost:6333` instead. The default embedding configuration uses `gemini-embedding-2` with 768 dimensions; deterministic vectors remain available for local automated tests without a model key.

## Evidence lifecycle

1. Upload enters `PENDING_REVIEW` after text extraction and credential/prompt-injection/possible-PII scanning.
2. A credential-like value or instruction override makes the source `QUARANTINED`; it cannot be approved.
3. Owner approval chunks and indexes the source. Failure to index prevents approval.
4. Retrieval accepts only approved, non-expired chunks belonging to the same project and owner.
5. Removing a source removes its local chunks and its indexed points before marking it `DELETED`.

## Review and quality gate

The SRS endpoint requires a confirmed, generation-ready discovery brief and at least one approved evidence source. It rejects an empty retrieval set, unsafe evidence, malformed output, unknown citations, duplicate IDs, missing acceptance criteria, missing verification methods, and non-atomic or non-testable requirement statements. A successful SRS is saved as `NEEDS_REVIEW`; only a user can approve it or request changes.

## Remaining launch validation

- Run a fixed expert panel over at least ten briefs and publish groundedness, requirement-quality, and latency thresholds before a production claim.
- Exercise the real Gemini/Qdrant/Docker path with a non-sensitive test project; the checked-in automated suite uses deterministic mode and mocks the vector-store response.
- Add licensed, versioned standards packs only after the licence, owner, effective date, permitted-use scope, and deletion/expiry policy are recorded.
- Add non-text parsers only after malware scanning, extraction isolation, and document-format threat testing are in place.
