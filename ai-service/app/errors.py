"""Safe, machine-readable errors for provider and request failures."""

from __future__ import annotations

from enum import Enum


class ErrorCode(str, Enum):
    INVALID_REQUEST = "invalid_request"
    UNAUTHORIZED_CALLER = "unauthorized_caller"
    SERVICE_NOT_READY = "service_not_ready"
    UNSUPPORTED_PROVIDER = "unsupported_provider"
    PROVIDER_AUTH = "provider_auth"
    PROVIDER_RATE_LIMIT = "provider_rate_limit"
    PROVIDER_TIMEOUT = "provider_timeout"
    PROVIDER_UNAVAILABLE = "provider_unavailable"
    PROVIDER_INVALID_OUTPUT = "provider_invalid_output"
    CONTENT_SAFETY_BLOCKED = "content_safety_blocked"
    INTERNAL_ERROR = "internal_error"
    RETRIEVAL_UNAVAILABLE = "retrieval_unavailable"
    INSUFFICIENT_EVIDENCE = "insufficient_evidence"


class AiServiceError(Exception):
    """Expected failure whose public message is safe to show to a user."""

    def __init__(
        self,
        code: ErrorCode,
        message: str,
        *,
        status_code: int,
        retryable: bool = False,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code
        self.retryable = retryable


class InvalidProviderOutputError(AiServiceError):
    def __init__(self) -> None:
        super().__init__(
            ErrorCode.PROVIDER_INVALID_OUTPUT,
            "The generation provider returned an invalid structured result.",
            status_code=502,
        )


class ContentSafetyBlockedError(AiServiceError):
    def __init__(self, message: str) -> None:
        super().__init__(
            ErrorCode.CONTENT_SAFETY_BLOCKED,
            message,
            status_code=422,
        )
