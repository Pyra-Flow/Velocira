"""Tenant-filtered Qdrant indexing and retrieval for owner-approved project evidence."""

from __future__ import annotations

import hashlib
import math
import re
from typing import Any

import httpx

from app.config import Settings
from app.errors import AiServiceError, ErrorCode
from app.models import (
    KnowledgeChunkInput,
    RetrievalHit,
    RetrievalSearchRequest,
)


class GovernedRetriever:
    """A narrow vector-store adapter: every operation includes owner + project filters."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def index(self, chunk: KnowledgeChunkInput) -> None:
        vector = await self._embed(chunk.content, task_type="RETRIEVAL_DOCUMENT", title=chunk.source_title)
        await self._ensure_collection()
        await self._request(
            "PUT",
            f"/collections/{self._settings.qdrant_collection}/points?wait=true",
            json={
                "points": [{
                    "id": str(chunk.chunk_id),
                    "vector": vector,
                    "payload": {
                        "project_id": str(chunk.project_id),
                        "owner_id": str(chunk.owner_id),
                        "source_id": str(chunk.source_id),
                        "source_title": chunk.source_title,
                        "content": chunk.content,
                    },
                }]
            },
        )

    async def delete(self, *, chunk_id: str) -> None:
        await self._request(
            "POST",
            f"/collections/{self._settings.qdrant_collection}/points/delete?wait=true",
            json={"points": [chunk_id]},
        )

    async def search(self, request: RetrievalSearchRequest) -> list[RetrievalHit]:
        vector = await self._embed(request.query, task_type="RETRIEVAL_QUERY")
        response = await self._request(
            "POST",
            f"/collections/{self._settings.qdrant_collection}/points/query",
            json={
                "query": vector,
                "limit": min(request.limit * 3, 48),
                "with_payload": True,
                "filter": {
                    "must": [
                        {"key": "project_id", "match": {"value": str(request.project_id)}},
                        {"key": "owner_id", "match": {"value": str(request.owner_id)}},
                    ]
                },
            },
        )
        result = response.get("result", {})
        points = result.get("points", result if isinstance(result, list) else [])
        hits: list[RetrievalHit] = []
        seen_sources: set[str] = set()
        for point in points:
            payload = point.get("payload") or {}
            # Defence in depth: never trust the vector database to enforce the filter.
            if payload.get("project_id") != str(request.project_id) or payload.get("owner_id") != str(request.owner_id):
                continue
            source_id = payload.get("source_id")
            content = payload.get("content")
            title = payload.get("source_title")
            if not all(isinstance(value, str) and value for value in (source_id, content, title)):
                continue
            if source_id in seen_sources and len(hits) < max(1, request.limit // 2):
                continue
            seen_sources.add(source_id)
            hits.append(RetrievalHit(
                source_id=source_id,
                chunk_id=str(point.get("id")),
                source_title=title,
                content=content,
                score=float(point.get("score", 0)),
            ))
            if len(hits) >= request.limit:
                break
        return hits

    async def _ensure_collection(self) -> None:
        status, _ = await self._raw_request("GET", f"/collections/{self._settings.qdrant_collection}")
        if status == 200:
            return
        if status != 404:
            raise self._unavailable()
        await self._request(
            "PUT",
            f"/collections/{self._settings.qdrant_collection}",
            json={"vectors": {"size": self._settings.embedding_dimensions, "distance": "Cosine"}},
        )

    async def _embed(self, text: str, *, task_type: str, title: str | None = None) -> list[float]:
        if self._settings.provider == "deterministic":
            return _deterministic_embedding(text, self._settings.embedding_dimensions)
        if self._settings.provider != "gemini" or not self._settings.gemini_api_key:
            raise AiServiceError(ErrorCode.SERVICE_NOT_READY, "The embedding provider is not configured.", status_code=503)
        payload: dict[str, Any] = {
            "model": f"models/{self._settings.embedding_model}",
            "content": {"parts": [{"text": text[:24_000]}]},
            "embedContentConfig": {
                "taskType": task_type,
                "outputDimensionality": self._settings.embedding_dimensions,
            },
        }
        if title and task_type == "RETRIEVAL_DOCUMENT":
            payload["embedContentConfig"]["title"] = title
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=5.0)) as client:
                response = await client.post(
                    f"{self._settings.gemini_base_url}/models/{self._settings.embedding_model}:embedContent",
                    headers={"x-goog-api-key": self._settings.gemini_api_key}, json=payload,
                )
            if response.status_code >= 400:
                raise self._unavailable()
            values = response.json().get("embedding", {}).get("values", [])
            if not isinstance(values, list) or len(values) != self._settings.embedding_dimensions:
                raise self._unavailable()
            return [float(value) for value in values]
        except (httpx.HTTPError, ValueError, TypeError):
            raise self._unavailable()

    async def _request(self, method: str, path: str, *, json: dict[str, Any]) -> dict[str, Any]:
        status, result = await self._raw_request(method, path, json=json)
        if status < 200 or status >= 300:
            raise self._unavailable()
        return result

    async def _raw_request(self, method: str, path: str, *, json: dict[str, Any] | None = None) -> tuple[int, dict[str, Any]]:
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(10.0, connect=3.0)) as client:
                response = await client.request(method, self._settings.qdrant_url + path, json=json)
            try:
                body = response.json()
            except ValueError:
                body = {}
            return response.status_code, body if isinstance(body, dict) else {}
        except httpx.HTTPError:
            raise self._unavailable()

    @staticmethod
    def _unavailable() -> AiServiceError:
        return AiServiceError(
            ErrorCode.RETRIEVAL_UNAVAILABLE,
            "The evidence index is temporarily unavailable. No document was generated.",
            status_code=503,
            retryable=True,
        )


def _deterministic_embedding(text: str, dimensions: int) -> list[float]:
    """Stable local vector for contract tests; never use it as a semantic-production model."""
    values = [0.0] * dimensions
    for token in re.findall(r"[\w-]{2,}", text.lower()):
        digest = hashlib.blake2b(token.encode("utf-8"), digest_size=16).digest()
        index = int.from_bytes(digest[:4], "big") % dimensions
        values[index] += -1.0 if digest[4] & 1 else 1.0
    norm = math.sqrt(sum(value * value for value in values)) or 1.0
    return [value / norm for value in values]
