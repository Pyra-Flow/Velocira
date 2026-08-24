from __future__ import annotations

from unittest.mock import AsyncMock
from uuid import uuid4

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.models import RetrievalHit


def _generation_payload() -> dict[str, object]:
    return {
        "job_id": str(uuid4()),
        "project": {
            "id": str(uuid4()),
            "name": "Negative path test",
            "description": "A bounded project context.",
            "type": "WEB_APPLICATION",
        },
        "artifact_type": "test-artifact",
        "prompt": {
            "key": "small-structured-test-artifact",
            "version": "1.0.0",
            "content": "Create a small structured test artifact.",
        },
    }


def _retrieval_ids() -> dict[str, str]:
    return {
        "project_id": str(uuid4()),
        "owner_id": str(uuid4()),
        "source_id": str(uuid4()),
        "chunk_id": str(uuid4()),
    }


def test_production_without_internal_token_stays_live_but_rejects_work() -> None:
    client = TestClient(create_app(settings=Settings(environment="production")))

    assert client.get("/health").status_code == 200
    readiness = client.get("/ready")
    assert readiness.status_code == 200
    assert readiness.json()["status"] == "not_ready"
    assert readiness.json()["internal_auth_configured"] is False

    response = client.post("/v1/generate", json=_generation_payload())
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "service_not_ready"


def test_unsupported_provider_reports_not_ready_without_crashing_liveness() -> None:
    client = TestClient(create_app(settings=Settings(environment="test", provider="unsupported")))

    assert client.get("/health").json() == {"status": "ok"}
    assert client.get("/ready").json()["status"] == "not_ready"
    response = client.post("/v1/generate", json=_generation_payload())
    assert response.status_code == 503
    assert response.json()["error"]["retryable"] is True


def test_generation_size_limit_and_invalid_correlation_id_are_safe() -> None:
    client = TestClient(create_app(settings=Settings(environment="test", max_input_bytes=32)))

    response = client.post(
        "/v1/generate",
        json=_generation_payload(),
        headers={"X-Correlation-Id": "invalid correlation id with spaces"},
    )

    assert response.status_code == 413
    assert response.json()["error"]["code"] == "invalid_request"
    assert response.headers["X-Correlation-Id"] != "invalid correlation id with spaces"
    assert len(response.headers["X-Correlation-Id"]) == 36


def test_retrieval_endpoints_use_typed_contracts() -> None:
    app = create_app(settings=Settings(environment="test"))
    app.state.retriever.index = AsyncMock()
    app.state.retriever.delete = AsyncMock()
    ids = _retrieval_ids()
    hit = RetrievalHit(
        source_id=ids["source_id"],
        chunk_id=ids["chunk_id"],
        source_title="Approved evidence",
        content="A user submits a request and receives a decision.",
        score=0.91,
    )
    app.state.retriever.search = AsyncMock(return_value=[hit])
    client = TestClient(app)

    index = client.post(
        "/v1/retrieval/index",
        json={
            "chunk": {
                **ids,
                "source_title": "Approved evidence",
                "content": "A user submits a request and receives a decision.",
            }
        },
    )
    assert index.status_code == 200
    assert index.json() == {"indexed": True}
    app.state.retriever.index.assert_awaited_once()

    search = client.post(
        "/v1/retrieval/search",
        json={
            "project_id": ids["project_id"],
            "owner_id": ids["owner_id"],
            "query": "decision workflow",
            "limit": 8,
        },
    )
    assert search.status_code == 200
    assert search.json()["hits"][0]["chunk_id"] == ids["chunk_id"]
    app.state.retriever.search.assert_awaited_once()

    delete = client.post(
        "/v1/retrieval/delete",
        json={
            "project_id": ids["project_id"],
            "owner_id": ids["owner_id"],
            "chunk_id": ids["chunk_id"],
        },
    )
    assert delete.status_code == 204
    assert delete.content == b""
    app.state.retriever.delete.assert_awaited_once_with(chunk_id=ids["chunk_id"])


def test_srs_endpoint_returns_validated_cited_requirements() -> None:
    ids = _retrieval_ids()
    client = TestClient(create_app(settings=Settings(environment="test")))
    response = client.post(
        "/v1/srs/generate",
        json={
            "project": {
                "id": ids["project_id"],
                "name": "Governed SRS",
                "description": "A confirmed project.",
                "type": "WEB_APPLICATION",
            },
            "confirmed_brief": {"problem": "Reduce processing delays."},
            "profile": {
                "key": "STARTER",
                "name": "Starter",
                "controls": ["Trace requirements to approved evidence."],
            },
            "evidence": [
                {
                    "source_id": ids["source_id"],
                    "chunk_id": ids["chunk_id"],
                    "source_title": "Confirmed workflow",
                    "content": "A user submits a request and receives a decision.",
                    "score": 0.94,
                }
            ],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["validation"]["valid"] is True
    assert body["validation"]["citation_coverage"] == 100
    assert body["artifact"]["requirements"][0]["citations"][0]["chunk_id"] == ids["chunk_id"]


def test_srs_generation_enforces_the_same_request_size_limit() -> None:
    ids = _retrieval_ids()
    client = TestClient(create_app(settings=Settings(environment="test", max_input_bytes=32)))
    response = client.post(
        "/v1/srs/generate",
        json={
            "project": {
                "id": ids["project_id"], "name": "Bounded SRS", "description": "A confirmed project.", "type": "WEB_APPLICATION",
            },
            "confirmed_brief": {"problem": "Reduce processing delays."},
            "profile": {"key": "STARTER", "name": "Starter", "controls": ["Trace requirements."]},
            "evidence": [{
                "source_id": ids["source_id"], "chunk_id": ids["chunk_id"], "source_title": "Confirmed workflow",
                "content": "A user submits a request and receives a decision.", "score": 0.94,
            }],
        },
    )

    assert response.status_code == 413
    assert response.json()["error"]["code"] == "invalid_request"


class _CrashingProvider:
    name = "crashing-test-provider"
    model = "crashing-test-model"

    async def generate(self, request, *, correlation_id):  # type: ignore[no-untyped-def]
        del request, correlation_id
        raise RuntimeError("private provider detail")


def test_unexpected_provider_failure_returns_sanitized_error() -> None:
    client = TestClient(
        create_app(settings=Settings(environment="test"), provider=_CrashingProvider()),
        raise_server_exceptions=False,
    )

    response = client.post("/v1/generate", json=_generation_payload())

    assert response.status_code == 500
    body = response.json()
    assert body["error"]["code"] == "internal_error"
    assert "private provider detail" not in response.text
