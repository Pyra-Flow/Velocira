"""Minimal request safety boundary with an explicit, extendable taxonomy."""

from __future__ import annotations

import json
import re
from typing import Any

from app.errors import ContentSafetyBlockedError
from app.models import GenerationRequest, SafetyOutcome


class ContentSafetyGate:
    """Blocks accidental credentials before they can reach a model provider.

    This is intentionally a narrow first control, not a substitute for a
    policy engine or data-classification service. Its checks are recorded in
    the response so the orchestrator can persist validator/safety outcomes.
    """

    _credential_patterns = (
        re.compile(r"\bsk-[A-Za-z0-9_-]{16,}\b"),
        re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
        re.compile(r"(?i)-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----"),
        re.compile(r"(?i)(?:api[_-]?key|password|secret)\s*[:=]\s*[^\s]{12,}"),
    )
    _override_pattern = re.compile(
        r"(?i)\b(ignore|override|discard)\s+(all\s+)?(previous|system)\s+instructions\b"
    )

    @classmethod
    def inspect(cls, request: GenerationRequest) -> SafetyOutcome:
        text = "\n".join(
            (
                request.prompt.content,
                json.dumps(request.project.model_dump(mode="json"), ensure_ascii=False, default=str),
            )
        )
        if any(pattern.search(text) for pattern in cls._credential_patterns):
            raise ContentSafetyBlockedError(
                "The generation request appears to include a credential. Remove it and try again."
            )
        if cls._override_pattern.search(request.prompt.content):
            raise ContentSafetyBlockedError(
                "The generation prompt contains an unsafe instruction override. Revise it and try again."
            )
        return SafetyOutcome(
            passed=True,
            checks=["credential-pattern-scan", "prompt-override-scan"],
        )
