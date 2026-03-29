"""Cortex CE SDK — Data Transfer Objects (dataclasses)."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import ClassVar


def _first_non_null(data: dict, *keys: str) -> object:
    """Return the first non-None value for any of the given keys.

    Useful for dual-format wire fields where the backend may return either
    snake_case (SNAKE_CASE naming strategy) or camelCase (@JsonProperty override).
    Returns None if all keys are missing or None.

    Example::

        val = _first_non_null(data, "observation_types", "observationTypes")
        # val is [] if "observation_types" is set to [], even if "observationTypes" exists
    """
    for key in keys:
        val = data.get(key)
        if val is not None:
            return val
    return None


def _to_int(v: object, default: int = 0) -> int:
    """Safely convert wire value to int (handles string numbers, floats, NaN, and Inf)."""
    if isinstance(v, int):
        return v
    if isinstance(v, float):
        if v != v or v == float('inf') or v == float('-inf'):  # NaN or Inf
            return default
        return int(v)
    if isinstance(v, str):
        try:
            f = float(v)
            if f != f or f == float('inf') or f == float('-inf'):  # NaN or Inf
                return default
            return int(f)  # handles "3.14" → 3, "42" → 42
        except ValueError:
            return default
    return default


def _to_float(v: object, default: float = 0.0) -> float:
    """Safely convert wire value to float (handles string numbers)."""
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, str):
        try:
            return float(v)
        except ValueError:
            return default
    return default


def _to_str_list(v: object, default: list[str] | None = None) -> list[str]:
    """Safely convert wire value to list[str].

    Returns default (or []) if v is None or not a list.
    Converts non-string items via str() for defensive parsing.
    Matches JS SDK's safeStringArray() for cross-SDK parity.
    """
    if not isinstance(v, list):
        return default if default is not None else []
    result: list[str] = []
    for item in v:
        if isinstance(item, str):
            result.append(item)
        elif item is not None:
            result.append(str(item))
    return result


def _to_dict(v: object, default: dict | None = None) -> dict:
    """Safely convert wire value to dict.

    Returns default (or {}) if v is None, not a dict, or is a list.
    Matches JS SDK's safeRecord() for cross-SDK parity.
    """
    if isinstance(v, dict):
        return v
    return default if default is not None else {}


# ==================== Session ====================


@dataclass
class SessionStartResponse:
    """Response from POST /api/session/start."""

    session_db_id: str = ""
    session_id: str = ""
    context: str = ""
    prompt_number: int = 0

    @classmethod
    def from_wire(cls, data: dict) -> SessionStartResponse:
        return cls(
            session_db_id=_first_non_null(data, "session_db_id", "sessionDbId") or "",
            session_id=_first_non_null(data, "session_id", "sessionId") or "",
            context=_first_non_null(data, "context") or "",
            prompt_number=_to_int(_first_non_null(data, "prompt_number", "promptNumber")),
        )


# ==================== Experience ====================


@dataclass
class Experience:
    """A retrieved experience from memory."""

    id: str = ""
    task: str = ""
    strategy: str = ""
    outcome: str = ""
    reuse_condition: str = ""
    quality_score: float = 0.0
    created_at: str = ""

    def to_dict(self) -> dict:
        """Serialize to a dict with Pythonic snake_case keys.

        Only non-empty fields are included (consistent with other SDK DTOs).
        For exact wire format matching the backend, see the Go/JS SDK serialization.
        """
        return {
            "id": self.id,
            "task": self.task,
            "strategy": self.strategy,
            "outcome": self.outcome,
            "reuse_condition": self.reuse_condition,
            "quality_score": self.quality_score,
            "created_at": self.created_at,
        }

    @classmethod
    def from_wire(cls, data: dict) -> Experience:
        # Wire format uses Jackson SNAKE_CASE naming strategy.
        # Use `or ""` instead of default "" to handle null values from backend.
        return cls(
            id=data.get("id") or "",
            task=data.get("task") or "",
            strategy=data.get("strategy") or "",
            outcome=data.get("outcome") or "",
            reuse_condition=_first_non_null(data, "reuse_condition", "reuseCondition") or "",
            quality_score=_to_float(_first_non_null(data, "quality_score", "qualityScore"), 0.0),
            created_at=_first_non_null(data, "created_at", "createdAt") or "",
        )


@dataclass
class ICLPromptResult:
    """Result from POST /api/memory/icl-prompt."""

    prompt: str = ""
    experience_count: int = 0
    max_chars: int = 0

    @classmethod
    def from_wire(cls, data: dict) -> ICLPromptResult:
        return cls(
            prompt=data.get("prompt") or "",
            experience_count=_to_int(_first_non_null(data, "experience_count", "experienceCount")),
            max_chars=_to_int(_first_non_null(data, "max_chars", "maxChars")),
        )


# ==================== Observation ====================


@dataclass
class ObservationUpdate:
    """Partial update for an existing observation (PATCH semantics).

    Only non-None fields are sent to the backend, matching Go's
    pointer-field-with-omitempty pattern.

    Usage::

        # Dataclass style (recommended for IDE autocomplete & type checking)
        update = ObservationUpdate(title="New Title", source="manual")
        client.update_observation("obs-123", update)

        # Kwargs style (convenience)
        client.update_observation("obs-123", title="New Title", source="manual")
    """

    title: str | None = None
    subtitle: str | None = None
    content: str | None = None
    narrative: str | None = None  # Alias for content; if both set, content takes priority
    facts: list[str] | None = None
    concepts: list[str] | None = None
    source: str | None = None
    extracted_data: dict | None = None

    def is_empty(self) -> bool:
        """Return True if no fields are set (nothing to send).

        Matches Go SDK's ObservationUpdate.IsEmpty() for cross-SDK parity.
        """
        return all(getattr(self, attr) is None for attr in self._WIRE_FIELDS)

    def __bool__(self) -> bool:
        """Return True if at least one field is set (Pythonic truthiness).

        Allows using ``if update:`` instead of ``if not update.is_empty():``.
        """
        return not self.is_empty()

    # Python attr name → wire format key (differs for extracted_data → extractedData)
    _WIRE_FIELDS: ClassVar[dict[str, str]] = {
        "title": "title",
        "subtitle": "subtitle",
        "content": "content",
        "narrative": "narrative",
        "facts": "facts",
        "concepts": "concepts",
        "source": "source",
        "extracted_data": "extractedData",
    }

    def to_wire(self) -> dict:
        """Convert to wire format, omitting None fields.

        Both 'content' and 'narrative' are sent if set — backend accepts either.
        """
        body: dict = {}
        for attr, wire_key in self._WIRE_FIELDS.items():
            val = getattr(self, attr)
            if val is not None:
                body[wire_key] = val
        return body

@dataclass
class Observation:
    """A single observation record."""

    id: str = ""
    session_id: str = ""
    project_path: str = ""
    type: str = ""
    title: str = ""
    subtitle: str = ""
    content: str = ""
    facts: list[str] = field(default_factory=list)
    concepts: list[str] = field(default_factory=list)
    files_read: list[str] = field(default_factory=list)
    files_modified: list[str] = field(default_factory=list)
    quality_score: float = 0.0
    feedback_type: str = ""  # SUCCESS/PARTIAL/FAILURE/UNKNOWN
    feedback_updated_at: str = ""
    source: str = ""
    extracted_data: dict = field(default_factory=dict)
    prompt_number: int = 0
    created_at: str = ""
    created_at_epoch: int = 0
    last_accessed_at: str = ""

    def to_dict(self) -> dict:
        """Serialize to a dict with mixed naming conventions.

        Most fields use snake_case (matching backend Jackson SNAKE_CASE strategy),
        except ``extractedData`` which uses camelCase (matching @JsonProperty override).
        For exact wire-compatible JSON across all SDKs, see Go SDK's ``toWire()``
        or JS SDK's ``toJSON()``.

        Field inclusion rules:
        - Always included: id, session_id (→content_session_id), project_path (→project),
          type, content (→narrative)
        - Omit when empty/zero: title, subtitle, facts, concepts, files_read, files_modified,
          quality_score, feedback_type, feedback_updated_at, source, extractedData,
          prompt_number, created_at, created_at_epoch, last_accessed_at
        """
        # Always-include fields (Go SDK: no omitempty)
        d: dict = {
            "id": self.id,
            "content_session_id": self.session_id,
            "project": self.project_path,
            "type": self.type,
            "narrative": self.content,
        }
        # omitempty fields (Go SDK: json:"...,omitempty")
        if self.title:
            d["title"] = self.title
        if self.subtitle:
            d["subtitle"] = self.subtitle
        if self.facts:
            d["facts"] = self.facts
        if self.concepts:
            d["concepts"] = self.concepts
        if self.files_read:
            d["files_read"] = self.files_read
        if self.files_modified:
            d["files_modified"] = self.files_modified
        if self.quality_score:
            d["quality_score"] = self.quality_score
        if self.feedback_type:
            d["feedback_type"] = self.feedback_type
        if self.feedback_updated_at:
            d["feedback_updated_at"] = self.feedback_updated_at
        if self.prompt_number:
            d["prompt_number"] = self.prompt_number
        if self.source:
            d["source"] = self.source
        if self.extracted_data:
            d["extractedData"] = self.extracted_data
        if self.created_at:
            d["created_at"] = self.created_at
        if self.created_at_epoch:
            d["created_at_epoch"] = self.created_at_epoch
        if self.last_accessed_at:
            d["last_accessed_at"] = self.last_accessed_at
        return d

    @classmethod
    def from_wire(cls, data: dict) -> Observation:
        # Wire format uses Jackson SNAKE_CASE naming strategy.
        # Key field renames: sessionId→content_session_id, projectPath→project, content→narrative
        # Use `or ""` for string fields to handle null values from backend.
        # List/dict fields use defensive helpers (_to_str_list, _to_dict) to guard
        # against unexpected wire types (matches JS SDK's safeStringArray/safeRecord).
        return cls(
            id=data.get("id") or "",
            session_id=data.get("content_session_id") or "",
            project_path=data.get("project") or "",
            type=data.get("type") or "",
            title=data.get("title") or "",
            subtitle=data.get("subtitle") or "",
            content=data.get("narrative") or "",
            facts=_to_str_list(data.get("facts")),
            concepts=_to_str_list(data.get("concepts")),
            files_read=_to_str_list(_first_non_null(data, "files_read", "filesRead")),
            files_modified=_to_str_list(_first_non_null(data, "files_modified", "filesModified")),
            quality_score=_to_float(_first_non_null(data, "quality_score", "qualityScore")),
            feedback_type=_first_non_null(data, "feedback_type", "feedbackType") or "",
            feedback_updated_at=_first_non_null(data, "feedback_updated_at", "feedbackUpdatedAt") or "",
            source=data.get("source") or "",
            extracted_data=_to_dict(_first_non_null(data, "extractedData", "extracted_data")),
            prompt_number=_to_int(_first_non_null(data, "prompt_number", "promptNumber")),
            created_at=_first_non_null(data, "created_at", "createdAt") or "",
            created_at_epoch=_to_int(_first_non_null(data, "created_at_epoch", "createdAtEpoch")),
            last_accessed_at=_first_non_null(data, "last_accessed_at", "lastAccessedAt") or "",
        )


# ==================== Search ====================


@dataclass
class SearchResult:
    """Response from GET /api/search."""

    observations: list[Observation] = field(default_factory=list)
    strategy: str = ""
    fell_back: bool = False
    count: int = 0

    def to_dict(self) -> dict:
        """Serialize to wire-compatible dict."""
        return {
            "observations": [o.to_dict() for o in self.observations],
            "strategy": self.strategy,
            "fell_back": self.fell_back,
            "count": self.count,
        }

    @classmethod
    def from_wire(cls, data: dict) -> SearchResult:
        return cls(
            observations=[Observation.from_wire(o) for o in data.get("observations") or []],
            strategy=data.get("strategy") or "",
            fell_back=bool(_first_non_null(data, "fell_back", "fellBack") or False),
            count=_to_int(data.get("count")),
        )


# ==================== Observations (paginated) ====================


@dataclass
class ObservationsResponse:
    """Paginated response from GET /api/observations."""

    items: list[Observation] = field(default_factory=list)
    has_more: bool = False
    total: int = 0
    offset: int = 0
    limit: int = 0

    @classmethod
    def from_wire(cls, data: dict) -> ObservationsResponse:
        return cls(
            items=[Observation.from_wire(o) for o in data.get("items") or []],
            has_more=bool(_first_non_null(data, "has_more", "hasMore") or False),
            total=_to_int(_first_non_null(data, "total")),
            offset=_to_int(_first_non_null(data, "offset")),
            limit=_to_int(_first_non_null(data, "limit")),
        )


@dataclass
class BatchObservationsResponse:
    """Response from POST /api/observations/batch."""

    observations: list[Observation] = field(default_factory=list)
    count: int = 0

    @classmethod
    def from_wire(cls, data: dict) -> BatchObservationsResponse:
        return cls(
            observations=[Observation.from_wire(o) for o in data.get("observations") or []],
            count=_to_int(_first_non_null(data, "count")),
        )


# ==================== Quality ====================


@dataclass
class QualityDistribution:
    """Quality distribution for a project."""

    project: str = ""
    high: int = 0
    medium: int = 0
    low: int = 0
    unknown: int = 0

    @property
    def total(self) -> int:
        return self.high + self.medium + self.low + self.unknown

    @classmethod
    def from_wire(cls, data: dict) -> QualityDistribution:
        return cls(
            project=data.get("project") or "",
            high=_to_int(_first_non_null(data, "high")),
            medium=_to_int(_first_non_null(data, "medium")),
            low=_to_int(_first_non_null(data, "low")),
            unknown=_to_int(_first_non_null(data, "unknown")),
        )


# ==================== Extraction ====================


@dataclass
class ExtractionResult:
    """A single extraction result."""

    status: str = ""
    template: str = ""
    message: str = ""
    session_id: str = ""
    extracted_data: dict = field(default_factory=dict)
    created_at: int = 0
    observation_id: str = ""

    def to_dict(self) -> dict:
        """Serialize to a dict with camelCase keys.

        Uses camelCase keys (sessionId, extractedData, observationId) for
        JavaScript-ecosystem interop. For exact backend wire format (SNAKE_CASE),
        the Go SDK's ``toWire()`` is the canonical reference. Round-tripping through
        ``from_wire(to_dict())`` works because ``from_wire`` handles both formats.
        """
        d: dict = {}
        if self.status:
            d["status"] = self.status
        if self.template:
            d["template"] = self.template
        if self.message:
            d["message"] = self.message
        if self.session_id:
            d["sessionId"] = self.session_id
        if self.extracted_data:
            d["extractedData"] = self.extracted_data
        # createdAt is always included (Go SDK: no omitempty tag)
        d["createdAt"] = self.created_at
        if self.observation_id:
            d["observationId"] = self.observation_id
        return d

    @classmethod
    def from_wire(cls, data: dict) -> ExtractionResult:
        return cls(
            status=data.get("status") or "",
            template=data.get("template") or "",
            message=data.get("message") or "",
            session_id=_first_non_null(data, "session_id", "sessionId") or "",
            extracted_data=_to_dict(_first_non_null(data, "extracted_data", "extractedData")),
            created_at=_to_int(_first_non_null(data, "created_at", "createdAt")),
            observation_id=_first_non_null(data, "observation_id", "observationId") or "",
        )


# ==================== Version / Projects / Stats / Modes ====================


@dataclass
class VersionResponse:
    """Response from GET /api/version."""

    version: str = ""
    service: str = ""
    java: str = ""
    spring_boot: str = ""

    @classmethod
    def from_wire(cls, data: dict) -> VersionResponse:
        return cls(
            version=data.get("version") or "",
            service=data.get("service") or "",
            java=data.get("java") or "",
            spring_boot=data.get("springBoot") or "",
        )


@dataclass
class ProjectsResponse:
    """Response from GET /api/projects."""

    projects: list[str] = field(default_factory=list)

    @classmethod
    def from_wire(cls, data: dict) -> ProjectsResponse:
        return cls(projects=_to_str_list(data.get("projects")))


@dataclass
class WorkerStats:
    is_processing: bool = False
    queue_depth: int = 0


@dataclass
class DatabaseStats:
    total_observations: int = 0
    total_summaries: int = 0
    total_sessions: int = 0
    total_projects: int = 0


@dataclass
class StatsResponse:
    """Response from GET /api/stats."""

    worker: WorkerStats = field(default_factory=WorkerStats)
    database: DatabaseStats = field(default_factory=DatabaseStats)

    @classmethod
    def from_wire(cls, data: dict) -> StatsResponse:
        w = (data or {}).get("worker") or {}
        d = (data or {}).get("database") or {}
        return cls(
            worker=WorkerStats(
                is_processing=bool(w.get("isProcessing", False)),
                queue_depth=_to_int(w.get("queueDepth")),
            ),
            database=DatabaseStats(
                total_observations=_to_int(d.get("totalObservations")),
                total_summaries=_to_int(d.get("totalSummaries")),
                total_sessions=_to_int(d.get("totalSessions")),
                total_projects=_to_int(d.get("totalProjects")),
            ),
        )


@dataclass
class ModesResponse:
    """Response from GET /api/modes."""

    id: str = ""
    name: str = ""
    description: str = ""
    version: str = ""
    observation_types: list[str] = field(default_factory=list)
    observation_concepts: list[str] = field(default_factory=list)

    @classmethod
    def from_wire(cls, data: dict) -> ModesResponse:
        return cls(
            id=data.get("id") or "",
            name=data.get("name") or "",
            description=data.get("description") or "",
            version=data.get("version") or "",
            observation_types=_to_str_list(_first_non_null(data, "observation_types", "observationTypes")),
            observation_concepts=_to_str_list(_first_non_null(data, "observation_concepts", "observationConcepts")),
        )
