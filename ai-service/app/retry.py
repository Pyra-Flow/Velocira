"""Retry classification shared by a future worker and API error responses."""

from __future__ import annotations

from app.errors import AiServiceError, ErrorCode
from app.models import RetryAdvice


class RetryClassifier:
    """Classifies only transient provider failures as retryable.

    Actual retry scheduling belongs to the durable Spring job worker. This
    classifier keeps its policy close to the provider error taxonomy so a
    worker can apply exponential backoff consistently.
    """

    _retryable_codes = {
        ErrorCode.PROVIDER_RATE_LIMIT,
        ErrorCode.PROVIDER_TIMEOUT,
        ErrorCode.PROVIDER_UNAVAILABLE,
    }

    @classmethod
    def classify(cls, error: AiServiceError, *, attempt: int = 0) -> RetryAdvice:
        retryable = error.retryable or error.code in cls._retryable_codes
        if not retryable:
            return RetryAdvice(retryable=False)

        bounded_attempt = min(max(attempt, 0), 6)
        # 1, 2, 4, ... 64 seconds; no jitter is added here so persisted job
        # attempts remain reproducible. The orchestrator can add queue jitter.
        return RetryAdvice(retryable=True, retry_after_seconds=float(2**bounded_attempt))
