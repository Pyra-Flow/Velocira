from __future__ import annotations

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def _payload() -> dict[str, object]:
    return {
        "job_id": "e4d4f791-72e8-40b3-a555-86f511665d25",
        "project": {
            "id": "8b6c4dd8-0a96-4896-b1d9-65fd0b488ad3",
            "name": "Example",
            "description": "A project used to test the job path",
            "type": "WEB_APPLICATION",
        },
        "artifact_type": "test-artifact",
        "prompt": {
            "key": "small-structured-test-artifact",
            "version": "1.0.0",
            "content": "Create a small structured test artifact.",
        },
    }


def test_health_and_readiness_are_available_on_clean_clone() -> None:
    client = TestClient(create_app(settings=Settings(environment="test")))

    assert client.get("/health").json() == {"status": "ok"}
    ready = client.get("/ready")
    assert ready.status_code == 200
    assert ready.json()["status"] == "ready"


def test_generation_returns_validated_reproducible_artifact() -> None:
    client = TestClient(create_app(settings=Settings(environment="test")))

    response = client.post(
        "/v1/generate",
        json=_payload(),
        headers={"X-Correlation-Id": "phase2-local-test"},
    )

    assert response.status_code == 200
    body = response.json()
    assert response.headers["X-Correlation-Id"] == "phase2-local-test"
    assert body["success"] is True
    assert body["provider"] == "deterministic"
    assert body["validation"]["valid"] is True
    assert body["error"] is None
    assert set(body) == {
        "success",
        "provider",
        "model",
        "prompt_version",
        "artifact",
        "validation",
        "usage",
        "latency_ms",
        "error",
    }
    assert body["artifact"]["sections"][0]["id"] == "purpose"


def test_internal_token_and_credential_safety_are_enforced() -> None:
    protected = TestClient(
        create_app(settings=Settings(environment="production", internal_service_token="local-secret"))
    )

    unauthenticated = protected.post("/v1/generate", json=_payload())
    assert unauthenticated.status_code == 401
    assert unauthenticated.json()["error"]["code"] == "unauthorized_caller"

    payload = _payload()
    prompt = dict(payload["prompt"])
    prompt["content"] = "Use api_key=super-secret-value-here"
    payload["prompt"] = prompt
    blocked = protected.post(
        "/v1/generate",
        json=payload,
        headers={"X-Internal-Token": "local-secret"},
    )
    assert blocked.status_code == 422
    assert blocked.json()["error"]["code"] == "content_safety_blocked"


def test_invalid_request_uses_the_safe_error_envelope() -> None:
    client = TestClient(create_app(settings=Settings(environment="test")))

    response = client.post("/v1/generate", json={"job_id": "not-a-uuid"})

    assert response.status_code == 422
    body = response.json()
    assert body["success"] is False
    assert body["error"] == {
        "code": "invalid_request",
        "message": "The generation request is invalid.",
        "retryable": False,
    }


def test_discovery_planner_returns_one_typed_high_value_question() -> None:
    client = TestClient(create_app(settings=Settings(environment="test")))

    response = client.post(
        "/v1/discovery/plan",
        json={
            "project": _payload()["project"],
            "answers": [{"category": "PROBLEM", "disposition": "ANSWERED", "answer_text": "Reduce support delays."}],
            "visible_open_question_keys": [],
        },
        headers={"X-Correlation-Id": "phase3-planner-test"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["planner"] == "deterministic-risk-aware-v1"
    assert body["next_question"]["category"] == "USERS"
    assert body["assumptions"] == []
