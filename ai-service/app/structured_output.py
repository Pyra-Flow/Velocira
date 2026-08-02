"""Provider output parsing and strict structured-artifact validation."""

from __future__ import annotations

import json
from hashlib import sha256
from typing import Any

from pydantic import ValidationError

from app.errors import InvalidProviderOutputError
from app.models import StructuredArtifact, ValidationOutcome


class StructuredOutputAdapter:
    """Converts provider output to the canonical artifact schema.

    Providers may return a mapping or a JSON string. Markdown code fences are
    accepted only as transport wrapping; the inner content still has to pass
    the strict Pydantic schema.
    """

    @staticmethod
    def validate(output: dict[str, Any] | str) -> tuple[StructuredArtifact, ValidationOutcome]:
        payload: Any = output
        if isinstance(output, str):
            candidate = output.strip()
            if candidate.startswith("```") and candidate.endswith("```"):
                fence_parts = candidate.split("\n", 1)
                if len(fence_parts) != 2:
                    raise InvalidProviderOutputError()
                candidate = fence_parts[1].rsplit("```", 1)[0].strip()
            try:
                payload = json.loads(candidate)
            except json.JSONDecodeError as exc:
                raise InvalidProviderOutputError() from exc
        try:
            artifact = StructuredArtifact.model_validate(payload)
        except ValidationError as exc:
            raise InvalidProviderOutputError() from exc
        return artifact, ValidationOutcome(
            valid=True,
            issues=[],
        )

    @staticmethod
    def digest(artifact: StructuredArtifact) -> str:
        encoded = json.dumps(
            artifact.model_dump(mode="json"),
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
        return sha256(encoded).hexdigest()

    @staticmethod
    def snapshot_digest(snapshot: dict[str, Any]) -> str:
        encoded = json.dumps(
            snapshot,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            default=str,
        ).encode("utf-8")
        return sha256(encoded).hexdigest()
