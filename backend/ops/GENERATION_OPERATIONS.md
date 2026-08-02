# Generation Platform Operations (Phase 2)

## Scope and safety boundary

The Spring Boot service is the public, authenticated orchestration boundary.
It owns project authorization, idempotency, the durable job ledger, artifact
persistence, and all provider-cost records. The FastAPI service is internal
only; browser code never receives a provider credential or a provider endpoint.

The checked-in provider is deliberately deterministic and credential-free. It
is useful for local integration and reliability testing only. It is **not** a
standards-quality document generator and must be replaced by a reviewed,
server-side provider adapter before production content generation is enabled.

## Local integration path

1. Copy `backend/.env.example` to `backend/.env` and replace the database and
   JWT values. Set one long random value for `AI_SERVICE_SHARED_SECRET`.
2. From `backend`, start the database and internal service:

   ```powershell
   docker compose up --build postgres ai-service
   ```

   Compose passes `AI_SERVICE_SHARED_SECRET` only to FastAPI as
   `AI_SERVICE_INTERNAL_TOKEN`. Spring sends the same value in
   `X-Internal-Token`; neither value is exposed to the frontend.
3. Start Spring Boot from `backend` and the Next.js app from `frontend` using
   their normal development commands. On a clean database, Flyway applies V4
   and V5, including the controlled Phase 2 prompt revision.
4. Register, create a project, open the project workspace, and choose
   **Generate test artifact**. The job should move through a durable state,
   then show `READY` and one completed SRS document after refresh.

The local Spring integration test is also runnable without Docker or a model
credential. It uses the same typed internal client boundary with a controlled
test double:

```powershell
cd backend
.\mvnw.cmd -DforkCount=0 -Dtest=GenerationJobLifecycleIntegrationTest test
```

It covers successful publication plus idempotent replay, provider timeout with
automatic backoff and manual retry, and cancellation while a provider response
is in flight.

## Lifecycle and recovery

| Job state | Meaning | Operator behavior |
|---|---|---|
| `QUEUED` | Persisted and awaiting/delayed for a worker | The worker or recovery scan claims it. |
| `RETRIEVING` | Capturing the approved immutable context | A stale job is safely requeued. |
| `DRAFTING` | An internal provider request is in progress | A persisted attempt/idempotency key prevents duplicate provider work after recovery. |
| `VALIDATING` | Response is checked before publication | A bad response becomes `NEEDS_INPUT` or `FAILED`; it is never silently published. |
| `READY` | Artifact version and document were persisted | Review the artifact; no worker continues it. |
| `NEEDS_INPUT` | More project evidence is needed | Update the project and start a new request. |
| `FAILED` | Durable failed-job/dead-letter state | Inspect the safe error and correlation ID; retry only when `retryable=true`. |
| `CANCELLED` | Cancellation was persisted | A late provider response is discarded and cannot publish an artifact. |

Automatic retries are scheduled with persisted exponential backoff. The periodic
recovery scan also requeues stale active jobs and due retries after a process
restart. A provider adapter must honor the forwarded `job-id:attempt` key when
it supports provider-side idempotency.

## Observability and alerts

Every API request receives `X-Correlation-Id`. The job stores it, worker logs
restore it, and FastAPI receives it. Use the value exposed in a job response to
join browser/API/worker/FastAPI logs without logging the raw prompt or secret.

Actuator exposes the following real metrics (not a simulated product dashboard):

| Metric | Dashboard panel / alert |
|---|---|
| `velocira.generation.queue.depth` | Queue depth; alert when it stays above the worker capacity threshold. |
| `velocira.generation.jobs.requested` | Accepted workload rate. |
| `velocira.generation.jobs.completed` | Completion rate. |
| `velocira.generation.jobs.failed{code=...}` | Failure count by safe error taxonomy; alert on sustained provider timeout/unavailable growth. |
| `velocira.generation.jobs.cancelled` | Cancellation rate. |
| `velocira.generation.latency` | p50/p95 generation latency. |
| `velocira.generation.cost.cents` | Provider cost rate and per-account budget investigation. |
| `health/generationAi` | Internal AI readiness; page only after confirming the service and secret configuration. |

Suggested initial alerts:

- AI readiness is down for 5 minutes.
- `FAILED / (FAILED + COMPLETED)` exceeds 10% for 15 minutes.
- Queue depth remains above 25 for 10 minutes.
- p95 generation latency exceeds the configured provider timeout window.
- Cost growth exceeds the daily account budget policy.

## Incident handling

1. Locate the job by project and status; record its correlation ID.
2. Check `health/generationAi`, then provider availability and secret rollout.
3. Do not modify an input snapshot, prompt revision, run, or artifact version.
   These are evidence records. Retry creates a new job from the original
   immutable snapshot only when the failure is classified retryable.
4. If jobs were interrupted during deployment, let the recovery scan requeue
   them. Do not manually mark a job ready or attach an unvalidated document.
5. For budget or rate-limit incidents, adjust server-side guardrails; never
   weaken them in browser code.
