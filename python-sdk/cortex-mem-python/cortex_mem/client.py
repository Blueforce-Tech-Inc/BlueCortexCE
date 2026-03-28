"""Cortex CE Python SDK — HTTP client implementation."""

from __future__ import annotations

import json
import logging
import random
import time
from typing import Any
from urllib.parse import quote

import requests

from .dto import (
    BatchObservationsResponse,
    Experience,
    ExtractionResult,
    ICLPromptResult,
    ModesResponse,
    ObservationUpdate,
    ObservationsResponse,
    ProjectsResponse,
    QualityDistribution,
    SearchResult,
    SessionStartResponse,
    StatsResponse,
    VersionResponse,
)
from .error import APIError, CortexError, is_retryable, raise_for_status
from .version import __version__

logger = logging.getLogger("cortex_mem")


class CortexMemClient:
    """Client for the Cortex CE memory system.

    All 26 API methods from the Go/Java SDK are available.
    Capture operations use fire-and-forget semantics (retries internally,
    swallows errors). Retrieval and management operations propagate errors.

    Usage::

        client = CortexMemClient(base_url="http://localhost:37777")
        session = client.start_session("s1", "/project")
        experiences = client.retrieve_experiences("task", "/project")
        client.close()

    Or as a context manager::

        with CortexMemClient() as client:
            client.health_check()
    """

    def __init__(
        self,
        base_url: str = "http://127.0.0.1:37777",
        timeout: float = 30.0,
        max_retries: int = 3,
        retry_backoff: float = 0.5,
        api_key: str | None = None,
        session: requests.Session | None = None,
    ) -> None:
        # Normalize: strip trailing slash
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout
        self._max_retries = max(1, max_retries)
        self._retry_backoff = retry_backoff
        self._api_key = api_key

        if session is not None:
            self._session = session
            self._owns_session = False
        else:
            self._session = requests.Session()
            self._owns_session = True

        self._session.headers.update(
            {
                "Accept": "application/json",
                "User-Agent": f"cortex-mem-python/{__version__}",
            }
        )
        if api_key:
            self._session.headers["Authorization"] = f"Bearer {api_key}"

        self._closed = False

    # ==================== HTTP helpers ====================

    def _assert_not_closed(self) -> None:
        """Raise if the client has been closed."""
        if self._closed:
            raise CortexError("client is closed")

    def _request(
        self,
        method: str,
        path: str,
        json_body: Any = None,
        params: dict[str, str] | None = None,
    ) -> requests.Response:
        """Make an HTTP request. Raises APIError on 4xx/5xx."""
        url = self._base_url + path
        resp = self._session.request(
            method,
            url,
            json=json_body,
            params=params,
            timeout=self._timeout,
        )
        if resp.status_code >= 400:
            raise_for_status(resp.status_code, resp.content)
        return resp

    def _request_json(
        self,
        method: str,
        path: str,
        json_body: Any = None,
        params: dict[str, str] | None = None,
    ) -> Any:
        """Make a request and return parsed JSON.

        Returns None for 204 No Content or non-JSON responses (graceful degradation).
        """
        resp = self._request(method, path, json_body, params)
        if resp.status_code == 204 or not resp.content:
            return None
        try:
            return resp.json()
        except (json.JSONDecodeError, ValueError):
            return None

    def _request_no_content(
        self,
        method: str,
        path: str,
        json_body: Any = None,
        params: dict[str, str] | None = None,
    ) -> None:
        """Make a request expecting no response body."""
        self._request(method, path, json_body, params)

    def _fire_and_forget(self, name: str, method: str, path: str, json_body: Any = None) -> None:
        """Execute a capture operation with retry and error swallowing.

        Matches Go SDK's doFireAndForget: linear backoff with ±25% jitter,
        only retries transient errors (429, 502, 503, 504 + network errors).
        """
        last_exc: Exception | None = None
        for attempt in range(1, self._max_retries + 1):
            try:
                self._request_no_content(method, path, json_body)
                return  # success
            except APIError as e:
                last_exc = e
                if not is_retryable(e.status_code):
                    logger.warning(
                        "%s failed with non-retryable error, giving up: %s (attempt %d/%d)",
                        name, e, attempt, self._max_retries,
                    )
                    return  # swallow
            except requests.RequestException as e:
                last_exc = e
                # network errors are always retryable

            if attempt < self._max_retries:
                base = self._retry_backoff * attempt
                jitter = base * random.uniform(-0.25, 0.25)
                delay = max(0, base + jitter)
                logger.warning(
                    "%s failed, retrying in %.2fs (attempt %d/%d): %s",
                    name, delay, attempt, self._max_retries, last_exc,
                )
                time.sleep(delay)

        logger.warning(
            "%s failed after %d attempts: %s",
            name, self._max_retries, last_exc,
        )
        # Fire-and-forget: swallow all errors

    # ==================== Session ====================

    def start_session(
        self,
        session_id: str,
        project_path: str,
        user_id: str | None = None,
    ) -> SessionStartResponse:
        """Start or resume a session. POST /api/session/start."""
        self._assert_not_closed()
        body: dict[str, Any] = {
            "session_id": session_id,
            "project_path": project_path,
        }
        if user_id:
            body["user_id"] = user_id
        data = self._request_json("POST", "/api/session/start", json_body=body) or {}
        return SessionStartResponse(
            session_db_id=data.get("session_db_id", ""),
            session_id=data.get("session_id", ""),
            context=data.get("context", ""),
            prompt_number=data.get("prompt_number", 0),
        )

    def update_session_user_id(self, session_id: str, user_id: str) -> dict:
        """Update session userId. PATCH /api/session/{session_id}/user."""
        self._assert_not_closed()
        if not session_id:
            raise CortexError("session_id is required")
        if not user_id:
            raise CortexError("user_id is required")
        path = f"/api/session/{quote(session_id, safe='')}/user"
        return self._request_json("PATCH", path, json_body={"user_id": user_id}) or {}

    # ==================== Capture (fire-and-forget) ====================

    def record_observation(
        self,
        session_id: str,
        project_path: str,
        tool_name: str,
        *,
        tool_input: Any = None,
        tool_response: Any = None,
        prompt_number: int = 0,
        source: str = "",
        extracted_data: dict | None = None,
    ) -> None:
        """Record a tool-use observation. POST /api/ingest/tool-use (fire-and-forget).

        Wire format: project_path → "cwd", tool_name → "tool_name",
        extracted_data → "extractedData" (camelCase).
        """
        self._assert_not_closed()
        if not session_id:
            raise CortexError("session_id is required")
        body: dict[str, Any] = {
            "session_id": session_id,
            "cwd": project_path,
            "tool_name": tool_name,
        }
        if tool_input is not None:
            body["tool_input"] = tool_input
        if tool_response is not None:
            body["tool_response"] = tool_response
        if prompt_number:
            body["prompt_number"] = prompt_number
        if source:
            body["source"] = source
        if extracted_data is not None:
            body["extractedData"] = extracted_data
        self._fire_and_forget("RecordObservation", "POST", "/api/ingest/tool-use", json_body=body)

    def record_session_end(
        self,
        session_id: str,
        project_path: str,
        last_assistant_message: str | None = None,
    ) -> None:
        """Signal session end. POST /api/ingest/session-end (fire-and-forget).

        Wire format: project_path → "cwd".
        """
        self._assert_not_closed()
        if not session_id:
            raise CortexError("session_id is required")
        body: dict[str, Any] = {
            "session_id": session_id,
            "cwd": project_path,
        }
        if last_assistant_message:
            body["last_assistant_message"] = last_assistant_message
        self._fire_and_forget("RecordSessionEnd", "POST", "/api/ingest/session-end", json_body=body)

    def record_user_prompt(
        self,
        session_id: str,
        prompt_text: str,
        project_path: str = "",
        prompt_number: int = 0,
    ) -> None:
        """Record a user prompt. POST /api/ingest/user-prompt (fire-and-forget).

        Wire format: project_path → "cwd".
        """
        self._assert_not_closed()
        if not session_id:
            raise CortexError("session_id is required")
        if not prompt_text:
            raise CortexError("prompt_text is required")
        body: dict[str, Any] = {
            "session_id": session_id,
            "prompt_text": prompt_text,
        }
        if project_path:
            body["cwd"] = project_path
        if prompt_number:
            body["prompt_number"] = prompt_number
        self._fire_and_forget("RecordUserPrompt", "POST", "/api/ingest/user-prompt", json_body=body)

    # ==================== Retrieval ====================

    def retrieve_experiences(
        self,
        task: str,
        project: str = "",
        *,
        count: int = 0,
        source: str = "",
        required_concepts: list[str] | None = None,
        user_id: str = "",
    ) -> list[Experience]:
        """Retrieve relevant experiences. POST /api/memory/experiences.

        Wire format: required_concepts → "requiredConcepts", user_id → "userId".
        """
        self._assert_not_closed()
        body: dict[str, Any] = {"task": task}
        if project:
            body["project"] = project
        if count:
            body["count"] = count
        if source:
            body["source"] = source
        if required_concepts:
            body["requiredConcepts"] = required_concepts
        if user_id:
            body["userId"] = user_id
        data = self._request_json("POST", "/api/memory/experiences", json_body=body)
        if not isinstance(data, list):
            return []
        return [Experience.from_wire(e) for e in data]

    def build_icl_prompt(
        self,
        task: str,
        project: str = "",
        *,
        max_chars: int = 0,
        user_id: str = "",
    ) -> ICLPromptResult:
        """Build an ICL prompt. POST /api/memory/icl-prompt.

        Wire format: max_chars → "maxChars", user_id → "userId".
        """
        self._assert_not_closed()
        body: dict[str, Any] = {"task": task}
        if project:
            body["project"] = project
        if max_chars:
            body["maxChars"] = max_chars
        if user_id:
            body["userId"] = user_id
        data = self._request_json("POST", "/api/memory/icl-prompt", json_body=body)
        return ICLPromptResult.from_wire(data or {})

    def search(
        self,
        project: str,
        *,
        query: str = "",
        type: str = "",
        concept: str = "",
        source: str = "",
        limit: int = 0,
        offset: int = 0,
    ) -> SearchResult:
        """Semantic search. GET /api/search with query params."""
        self._assert_not_closed()
        params: dict[str, str] = {"project": project}
        if query:
            params["query"] = query
        if type:
            params["type"] = type
        if concept:
            params["concept"] = concept
        if source:
            params["source"] = source
        if limit:
            params["limit"] = str(limit)
        if offset:
            params["offset"] = str(offset)
        data = self._request_json("GET", "/api/search", params=params)
        return SearchResult.from_wire(data or {})

    def list_observations(
        self,
        project: str = "",
        *,
        offset: int = 0,
        limit: int = 0,
    ) -> ObservationsResponse:
        """List observations with pagination. GET /api/observations."""
        self._assert_not_closed()
        params: dict[str, str] = {}
        if project:
            params["project"] = project
        if offset:
            params["offset"] = str(offset)
        if limit:
            params["limit"] = str(limit)
        data = self._request_json("GET", "/api/observations", params=params)
        return ObservationsResponse.from_wire(data or {})

    def get_observations_by_ids(self, ids: list[str]) -> BatchObservationsResponse:
        """Batch get observations by IDs. POST /api/observations/batch."""
        self._assert_not_closed()
        if not ids:
            raise CortexError("ids must not be empty")
        if len(ids) > 100:
            raise CortexError(f"batch size exceeds maximum of 100 (got {len(ids)})")
        for i, id_ in enumerate(ids):
            if not id_ or not id_.strip():
                raise CortexError(f"ids[{i}] is empty")
        data = self._request_json("POST", "/api/observations/batch", json_body={"ids": ids})
        return BatchObservationsResponse.from_wire(data or {})

    # ==================== Management ====================

    def trigger_refinement(self, project_path: str) -> None:
        """Trigger memory refinement. POST /api/memory/refine?project=..."""
        self._assert_not_closed()
        if not project_path:
            raise CortexError("project_path is required")
        self._request_no_content(
            "POST", "/api/memory/refine", params={"project": project_path}
        )

    def submit_feedback(
        self,
        observation_id: str,
        feedback_type: str,
        comment: str = "",
    ) -> None:
        """Submit feedback for an observation. POST /api/memory/feedback.

        Wire format: observation_id → "observationId", feedback_type → "feedbackType".
        """
        self._assert_not_closed()
        if not observation_id:
            raise CortexError("observation_id is required")
        if not feedback_type:
            raise CortexError("feedback_type is required")
        body: dict[str, Any] = {
            "observationId": observation_id,
            "feedbackType": feedback_type,
        }
        if comment:
            body["comment"] = comment
        self._request_no_content("POST", "/api/memory/feedback", json_body=body)

    def update_observation(
        self, observation_id: str, update: "ObservationUpdate | None" = None, **kwargs: Any
    ) -> None:
        """Update an observation. PATCH /api/memory/observations/{id}.

        Supports two calling styles::

            # Dataclass style (recommended — IDE autocomplete + type checking)
            from cortex_mem import ObservationUpdate
            client.update_observation("obs-123", ObservationUpdate(title="New", source="manual"))

            # Kwargs style (convenience)
            client.update_observation("obs-123", title="New", source="manual")

        If both ``update`` and ``kwargs`` are provided, kwargs override update fields.
        """
        self._assert_not_closed()
        if not observation_id:
            raise CortexError("observation_id is required")
        body: dict[str, Any] = {}
        if update is not None:
            body.update(update.to_wire())
        # Kwargs override (or used standalone)
        kwarg_map = {
            "title": "title",
            "subtitle": "subtitle",
            "content": "content",
            "narrative": "narrative",
            "facts": "facts",
            "concepts": "concepts",
            "source": "source",
            "extracted_data": "extractedData",
        }
        unknown = set(kwargs) - set(kwarg_map)
        if unknown:
            raise CortexError(f"unknown update fields: {', '.join(sorted(unknown))}")
        for kwarg, wire_key in kwarg_map.items():
            if kwarg in kwargs:
                body[wire_key] = kwargs[kwarg]
        if not body:
            raise CortexError("at least one field must be provided for update")
        path = f"/api/memory/observations/{quote(observation_id, safe='')}"
        self._request_no_content("PATCH", path, json_body=body)

    def delete_observation(self, observation_id: str) -> None:
        """Delete an observation. DELETE /api/memory/observations/{id}."""
        self._assert_not_closed()
        if not observation_id:
            raise CortexError("observation_id is required")
        path = f"/api/memory/observations/{quote(observation_id, safe='')}"
        self._request_no_content("DELETE", path)

    def get_quality_distribution(self, project_path: str) -> QualityDistribution:
        """Get quality distribution. GET /api/memory/quality-distribution?project=..."""
        self._assert_not_closed()
        if not project_path:
            raise CortexError("project_path is required")
        data = self._request_json(
            "GET", "/api/memory/quality-distribution", params={"project": project_path}
        )
        return QualityDistribution.from_wire(data or {})

    # ==================== Health ====================

    def health_check(self) -> None:
        """Check backend health. GET /api/health.

        Raises APIError if backend is unhealthy.
        """
        self._assert_not_closed()
        data = self._request_json("GET", "/api/health")
        if isinstance(data, dict):
            status = data.get("status")
            if status != "ok":
                raise CortexError(f"unhealthy: {data}")

    # ==================== Extraction ====================

    def trigger_extraction(self, project_path: str) -> None:
        """Manually trigger extraction. POST /api/extraction/run."""
        self._assert_not_closed()
        if not project_path:
            raise CortexError("project_path is required")
        self._request_no_content(
            "POST", "/api/extraction/run", params={"projectPath": project_path}
        )

    def get_latest_extraction(
        self,
        project_path: str,
        template_name: str,
        user_id: str = "",
    ) -> ExtractionResult:
        """Get latest extraction result. GET /api/extraction/{template}/latest."""
        self._assert_not_closed()
        if not project_path:
            raise CortexError("project_path is required")
        if not template_name:
            raise CortexError("template_name is required")
        path = f"/api/extraction/{quote(template_name, safe='')}/latest"
        params: dict[str, str] = {"projectPath": project_path}
        if user_id:
            params["userId"] = user_id
        data = self._request_json("GET", path, params=params)
        return ExtractionResult.from_wire(data or {})

    def get_extraction_history(
        self,
        project_path: str,
        template_name: str,
        user_id: str = "",
        limit: int = 0,
    ) -> list[ExtractionResult]:
        """Get extraction history. GET /api/extraction/{template}/history."""
        self._assert_not_closed()
        if not project_path:
            raise CortexError("project_path is required")
        if not template_name:
            raise CortexError("template_name is required")
        path = f"/api/extraction/{quote(template_name, safe='')}/history"
        params: dict[str, str] = {"projectPath": project_path}
        if user_id:
            params["userId"] = user_id
        if limit > 0:
            params["limit"] = str(limit)
        data = self._request_json("GET", path, params=params)
        if not isinstance(data, list):
            return []
        return [ExtractionResult.from_wire(e) for e in data]

    # ==================== Version ====================

    def get_version(self) -> VersionResponse:
        """Get backend version info. GET /api/version."""
        self._assert_not_closed()
        data = self._request_json("GET", "/api/version")
        return VersionResponse.from_wire(data or {})

    # ==================== P1 Management ====================

    def get_projects(self) -> ProjectsResponse:
        """Get all projects. GET /api/projects."""
        self._assert_not_closed()
        data = self._request_json("GET", "/api/projects")
        return ProjectsResponse.from_wire(data or {})

    def get_stats(self, project_path: str = "") -> StatsResponse:
        """Get project statistics. GET /api/stats."""
        self._assert_not_closed()
        params: dict[str, str] = {}
        if project_path:
            params["project"] = project_path
        data = self._request_json("GET", "/api/stats", params=params)
        return StatsResponse.from_wire(data or {})

    def get_modes(self) -> ModesResponse:
        """Get memory mode settings. GET /api/modes."""
        self._assert_not_closed()
        data = self._request_json("GET", "/api/modes")
        return ModesResponse.from_wire(data or {})

    def get_settings(self) -> dict:
        """Get current settings. GET /api/settings."""
        self._assert_not_closed()
        data = self._request_json("GET", "/api/settings")
        return data if isinstance(data, dict) else {}

    # ==================== Lifecycle ====================

    def close(self) -> None:
        """Close the underlying HTTP session."""
        self._closed = True
        if self._owns_session:
            self._session.close()

    def __repr__(self) -> str:
        status = "closed" if self._closed else "open"
        return f"CortexMemClient({self._base_url!r}, {status})"

    def __enter__(self) -> CortexMemClient:
        return self

    def __exit__(self, *args: Any) -> None:
        self.close()
