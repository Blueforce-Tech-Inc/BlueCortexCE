"""Cortex CE SDK — Data Transfer Objects (dataclasses)."""

from __future__ import annotations

from dataclasses import dataclass, field


def _to_int(v: object, default: int = 0) -> int:
    """Safely convert wire value to int (handles string numbers and floats)."""
    if isinstance(v, int):
        return v
    if isinstance(v, float):
        return int(v)
    if isinstance(v, str):
        try:
            return int(float(v))  # handles "3.14" → 3, "42" → 42
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


# ==================== Session ====================


@dataclass
class SessionStartResponse:
    """Response from POST /api/session/start."""

    session_db_id: str = ""
    session_id: str = ""
    context: str = ""
    prompt_number: int = 0


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
        """Serialize to wire-compatible dict (consistent with Go/JS SDK JSON output)."""
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
            reuse_condition=data.get("reuse_condition") or "",
            quality_score=_to_float(data.get("quality_score"), 0.0),
            created_at=data.get("created_at") or "",
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
            experience_count=_to_int(
                data.get("experienceCount", data.get("experience_count", 0))
            ),
            max_chars=_to_int(data.get("maxChars", data.get("max_chars", 0))),
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
    narrative: str | None = None  # Alias for content — cross-SDK consistency (Go/JS/Java)
    facts: list[str] | None = None
    concepts: list[str] | None = None
    source: str | None = None
    extracted_data: dict | None = None

    def is_empty(self) -> bool:
        """Return True if no fields are set (nothing to send).

        Matches Go SDK's ObservationUpdate.IsEmpty() for cross-SDK parity.
        """
        return all(
            v is None
            for v in (
                self.title, self.subtitle, self.content, self.narrative,
                self.facts, self.concepts, self.source, self.extracted_data,
            )
        )

    def to_wire(self) -> dict:
        """Convert to wire format, omitting None fields.

        Both 'content' and 'narrative' are sent if set — backend accepts either.
        """
        body: dict = {}
        if self.title is not None:
            body["title"] = self.title
        if self.subtitle is not None:
            body["subtitle"] = self.subtitle
        if self.content is not None:
            body["content"] = self.content
        if self.narrative is not None:
            body["narrative"] = self.narrative
        if self.facts is not None:
            body["facts"] = self.facts
        if self.concepts is not None:
            body["concepts"] = self.concepts
        if self.source is not None:
            body["source"] = self.source
        if self.extracted_data is not None:
            body["extractedData"] = self.extracted_data
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
    quality_score: float = 0.0
    source: str = ""
    extracted_data: dict | None = None
    prompt_number: int = 0
    created_at: str = ""
    created_at_epoch: int = 0

    def to_dict(self) -> dict:
        """Serialize to wire-compatible dict (consistent with Go/JS SDK JSON output).

        Uses the same field names as the backend JSON wire format so that
        demo HTTP responses match across SDKs.

        Note: quality_score and prompt_number are always included (matching Go SDK
        which does NOT use omitempty for these fields). Other fields use omitempty
        semantics (skip when empty/zero) for consistency with Go.
        """
        d: dict = {"id": self.id}
        if self.session_id:
            d["content_session_id"] = self.session_id
        if self.project_path:
            d["project"] = self.project_path
        if self.type:
            d["type"] = self.type
        if self.title:
            d["title"] = self.title
        if self.subtitle:
            d["subtitle"] = self.subtitle
        if self.content:
            d["narrative"] = self.content  # wire name
        if self.facts:
            d["facts"] = self.facts
        if self.concepts:
            d["concepts"] = self.concepts
        # Always include quality_score and prompt_number (Go SDK: no omitempty)
        d["quality_score"] = self.quality_score
        d["prompt_number"] = self.prompt_number
        if self.source:
            d["source"] = self.source
        if self.extracted_data is not None:
            d["extractedData"] = self.extracted_data
        if self.created_at:
            d["created_at"] = self.created_at
        if self.created_at_epoch:
            d["created_at_epoch"] = self.created_at_epoch
        return d

    @classmethod
    def from_wire(cls, data: dict) -> Observation:
        # Wire format uses Jackson SNAKE_CASE naming strategy.
        # Key field renames: sessionId→content_session_id, projectPath→project, content→narrative
        # Use `or ""` for string fields to handle null values from backend.
        return cls(
            id=data.get("id") or "",
            session_id=data.get("content_session_id") or "",
            project_path=data.get("project") or "",
            type=data.get("type") or "",
            title=data.get("title") or "",
            subtitle=data.get("subtitle") or "",
            content=data.get("narrative") or "",
            facts=data.get("facts") or [],
            concepts=data.get("concepts") or [],
            quality_score=_to_float(data.get("quality_score")),
            source=data.get("source") or "",
            extracted_data=data.get("extractedData"),
            prompt_number=_to_int(data.get("prompt_number")),
            created_at=data.get("created_at") or "",
            created_at_epoch=_to_int(data.get("created_at_epoch")),
        )


# ==================== Search ====================


@dataclass
class SearchResult:
    """Response from GET /api/search."""

    observations: list[Observation] = field(default_factory=list)
    strategy: str = ""
    fell_back: bool = False
    count: int = 0

    @classmethod
    def from_wire(cls, data: dict) -> SearchResult:
        return cls(
            observations=[Observation.from_wire(o) for o in data.get("observations") or []],
            strategy=data.get("strategy") or "",
            fell_back=bool(
                data.get("fell_back") if data.get("fell_back") is not None
                else data.get("fellBack", False)
            ),
            count=_to_int(data.get("count"), 0),
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
            has_more=bool(data.get("has_more") if data.get("has_more") is not None else data.get("hasMore", False)),
            total=_to_int(data.get("total"), 0),
            offset=_to_int(data.get("offset"), 0),
            limit=_to_int(data.get("limit"), 0),
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
            count=_to_int(data.get("count"), 0),
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
            high=_to_int(data.get("high"), 0),
            medium=_to_int(data.get("medium"), 0),
            low=_to_int(data.get("low"), 0),
            unknown=_to_int(data.get("unknown"), 0),
        )


# ==================== Extraction ====================


@dataclass
class ExtractionResult:
    """A single extraction result."""

    status: str = ""
    template: str = ""
    message: str = ""
    session_id: str = ""
    extracted_data: dict | None = None
    created_at: int = 0
    observation_id: str = ""

    @classmethod
    def from_wire(cls, data: dict) -> ExtractionResult:
        return cls(
            status=data.get("status") or "",
            template=data.get("template") or "",
            message=data.get("message") or "",
            session_id=data.get("sessionId") or "",
            extracted_data=data.get("extractedData"),
            created_at=_to_int(data.get("createdAt"), 0),
            observation_id=data.get("observationId") or "",
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
        return cls(projects=data.get("projects", []))


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
        w = data.get("worker") or {}
        d = data.get("database") or {}
        return cls(
            worker=WorkerStats(
                is_processing=bool(w.get("isProcessing", False)),
                queue_depth=_to_int(w.get("queueDepth"), 0),
            ),
            database=DatabaseStats(
                total_observations=_to_int(d.get("totalObservations"), 0),
                total_summaries=_to_int(d.get("totalSummaries"), 0),
                total_sessions=_to_int(d.get("totalSessions"), 0),
                total_projects=_to_int(d.get("totalProjects"), 0),
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
            observation_types=data.get("observation_types") or data.get("observationTypes") or [],
            observation_concepts=data.get("observation_concepts") or data.get("observationConcepts") or [],
        )
