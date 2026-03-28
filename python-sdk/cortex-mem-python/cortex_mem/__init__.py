"""Cortex CE Python SDK."""

from .client import CortexMemClient
from .dto import (
    BatchObservationsResponse,
    DatabaseStats,
    Experience,
    ExtractionResult,
    ICLPromptResult,
    ModesResponse,
    Observation,
    ObservationUpdate,
    ObservationsResponse,
    ProjectsResponse,
    QualityDistribution,
    SearchResult,
    SessionStartResponse,
    StatsResponse,
    VersionResponse,
    WorkerStats,
)
from .error import (
    APIError,
    AuthError,
    ConflictError,
    CortexError,
    NotFoundError,
    RateLimitError,
    ServerError,
    ValidationError,
    is_bad_gateway,
    is_client_error,
    is_gateway_timeout,
    is_retryable,
    is_retryable_error,
    is_server_error,
    is_service_unavailable,
)
from .version import __version__

__all__ = [
    # Client
    "CortexMemClient",
    # DTOs
    "SessionStartResponse",
    "Experience",
    "ICLPromptResult",
    "Observation",
    "ObservationUpdate",
    "SearchResult",
    "ObservationsResponse",
    "BatchObservationsResponse",
    "QualityDistribution",
    "ExtractionResult",
    "VersionResponse",
    "ProjectsResponse",
    "StatsResponse",
    "WorkerStats",
    "DatabaseStats",
    "ModesResponse",
    # Errors
    "CortexError",
    "APIError",
    "AuthError",
    "NotFoundError",
    "ConflictError",
    "RateLimitError",
    "ServerError",
    "ValidationError",
    # Error predicates (cross-SDK parity with Go Is* / JS is*)
    "is_retryable",
    "is_retryable_error",
    "is_bad_gateway",
    "is_service_unavailable",
    "is_gateway_timeout",
    "is_client_error",
    "is_server_error",
    # Version
    "__version__",
]
