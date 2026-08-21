"""Configuration loaded only from process environment variables."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Settings:
    """Runtime configuration with a credential-free local default.

    The deterministic provider keeps a clean clone runnable. Gemini credentials
    are read only from the process environment and never exposed to Spring or
    the browser.
    """

    environment: str = "development"
    provider: str = "deterministic"
    # Used for SRS and other generated project documents.
    model: str = "velocira-deterministic-v1"
    # Used only to select the next server-owned discovery question.
    discovery_model: str = "velocira-deterministic-v1"
    fallback_model: str = ""
    gemini_api_key: str = ""
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    provider_timeout_seconds: float = 180.0
    internal_service_token: str = ""
    max_input_bytes: int = 65_536
    log_level: str = "INFO"
    qdrant_url: str = "http://localhost:6333"
    qdrant_collection: str = "velocira_project_evidence_v1"
    embedding_model: str = "gemini-embedding-2"
    embedding_dimensions: int = 768

    @classmethod
    def from_environment(cls) -> "Settings":
        raw_max_input_bytes = os.getenv("AI_SERVICE_MAX_INPUT_BYTES", "65536")
        try:
            max_input_bytes = int(raw_max_input_bytes)
        except ValueError as exc:
            raise ValueError("AI_SERVICE_MAX_INPUT_BYTES must be an integer") from exc

        if max_input_bytes <= 0:
            raise ValueError("AI_SERVICE_MAX_INPUT_BYTES must be positive")

        raw_embedding_dimensions = os.getenv("AI_SERVICE_EMBEDDING_DIMENSIONS", "768")
        try:
            embedding_dimensions = int(raw_embedding_dimensions)
        except ValueError as exc:
            raise ValueError("AI_SERVICE_EMBEDDING_DIMENSIONS must be an integer") from exc
        if embedding_dimensions < 128 or embedding_dimensions > 3072:
            raise ValueError("AI_SERVICE_EMBEDDING_DIMENSIONS must be between 128 and 3072")

        raw_provider_timeout = os.getenv("AI_SERVICE_PROVIDER_TIMEOUT_SECONDS", "180")
        try:
            provider_timeout_seconds = float(raw_provider_timeout)
        except ValueError as exc:
            raise ValueError("AI_SERVICE_PROVIDER_TIMEOUT_SECONDS must be numeric") from exc
        if provider_timeout_seconds < 10 or provider_timeout_seconds > 900:
            raise ValueError("AI_SERVICE_PROVIDER_TIMEOUT_SECONDS must be between 10 and 900")

        return cls(
            environment=os.getenv("AI_SERVICE_ENVIRONMENT", "development").lower(),
            provider=os.getenv("AI_SERVICE_PROVIDER", "deterministic").lower(),
            model=os.getenv("AI_SERVICE_MODEL", "velocira-deterministic-v1"),
            discovery_model=os.getenv(
                "AI_SERVICE_DISCOVERY_MODEL",
                os.getenv("AI_SERVICE_MODEL", "velocira-deterministic-v1"),
            ),
            fallback_model=os.getenv("AI_SERVICE_FALLBACK_MODEL", ""),
            gemini_api_key=os.getenv("GEMINI_API_KEY", os.getenv("GOOGLE_API_KEY", "")),
            gemini_base_url=os.getenv("AI_SERVICE_GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta").rstrip("/"),
            provider_timeout_seconds=provider_timeout_seconds,
            internal_service_token=os.getenv("AI_SERVICE_INTERNAL_TOKEN", ""),
            max_input_bytes=max_input_bytes,
            log_level=os.getenv("AI_SERVICE_LOG_LEVEL", "INFO").upper(),
            qdrant_url=os.getenv("AI_SERVICE_QDRANT_URL", "http://localhost:6333").rstrip("/"),
            qdrant_collection=os.getenv("AI_SERVICE_QDRANT_COLLECTION", "velocira_project_evidence_v1"),
            embedding_model=os.getenv("AI_SERVICE_EMBEDDING_MODEL", "gemini-embedding-2"),
            embedding_dimensions=embedding_dimensions,
        )

    @property
    def production_like(self) -> bool:
        return self.environment in {"production", "staging"}
