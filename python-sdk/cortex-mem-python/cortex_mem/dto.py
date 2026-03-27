"""Cortex CE SDK — Data Transfer Objects (dataclasses)."""

from __future__ import annotations

from dataclasses import dataclass, field


def _to_int(v: object, default: int = 0) -> int:
    """Safely convert wire value to int (handles string numbers)."""
    if isinstance(v, int):
        return v
    if isinstance(v, str):
        try:
            return int(v)
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

    @classmethod
    def from_wire(cls, data: dict) -> Experience:
        # Wire format uses Jackson SNAKE_CASE naming strategy
        return cls(
            id=data.get("id", ""),
            task=data.get("task", ""),
            strategy=data.get("strategy", ""),
            outcome=data.get("outcome", ""),
            reuse_condition=data.get("reuse_condition", ""),
            quality_score=_to_float(data.get("quality_score", 0.0)),
            created_at=data.get("created_at", ""),
        )


@dataclass
class ICLPromptResult:
    """Result from POST /api/memory/icl-prompt."""

    prompt: str = ""
    experience_count: int = 0
    max_chars: int = 0

    @classmethod
    def from_wire(cls, data: dict) -> ICLPromptResult:
        # experienceCount may come as string or int
        ec = data.get("experienceCount", 0)
        if isinstance(ec, str):
            try:
                ec = int(ec)
            except ValueError:
                ec = 0
        return cls(
            prompt=data.get("prompt", ""),
            experience_count=ec,
            max_chars=data.get("maxChars", 0),
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
    facts: list[str] | None = None
    concepts: list[str] | None = None
    source: str | None = None
    extracted_data: dict | None = None

    def to_wire(self) -> dict:
        """Convert to wire format, omitting None fields."""
        body: dict = {}
        if self.title is not None:
            body["title"] = self.title
        if self.subtitle is not None:
            body["subtitle"] = self.subtitle
        if self.content is not None:
            body["content"] = self.content
        if self.facts is not None:
            body["facts"] = self.facts
        if self.concepts is not None:
            body["concepts"] = self.concepts
        if self.source is not None:
            body["source"] = self.source
        if self.extracted_data is not None:
            body["extractedData"] = self.extracted_data
        return body

    @classmethod
    def from_kwargs(cls, **kwargs: "str | list[str] | dict | None") -> "ObservationUpdate":
        """Create from keyword arguments."""
        return cls(
            title=kwargs.get("title"),
            subtitle=kwargs.get("subtitle"),
            content=kwargs.get("content"),
            facts=kwargs.get("facts"),
            concepts=kwargs.get("concepts"),
            source=kwargs.get("source"),
            extracted_data=kwargs.get("extracted_data"),
        )


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

    @classmethod
    def from_wire(cls, data: dict) -> Observation:
        # Wire format uses Jackson SNAKE_CASE naming strategy.
        # Key field renames: sessionId→content_session_id, projectPath→project, content→narrative
        return cls(
            id=data.get("id", ""),
            session_id=data.get("content_session_id", ""),
            project_path=data.get("project", ""),
            type=data.get("type", ""),
            title=data.get("title", ""),
            subtitle=data.get("subtitle", ""),
            content=data.get("narrative", ""),
            facts=data.get("facts") or [],
            concepts=data.get("concepts") or [],
            quality_score=_to_float(data.get("quality_score", 0.0)),
            source=data.get("source", ""),
            extracted_data=data.get("extractedData"),
            prompt_number=_to_int(data.get("prompt_number", 0)),
            created_at=data.get("created_at", ""),
            created_at_epoch=_to_int(data.get("created_at_epoch", 0)),
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
            observations=[Observation.from_wire(o) for o in data.get("observations", [])],
            strategy=data.get("strategy", ""),
            fell_back=data.get("fell_back", False),
            count=data.get("count", 0),
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
            items=[Observation.from_wire(o) for o in data.get("items", [])],
            has_more=data.get("hasMore", False),
            total=data.get("total", 0),
            offset=data.get("offset", 0),
            limit=data.get("limit", 0),
        )


@dataclass
class BatchObservationsResponse:
    """Response from POST /api/observations/batch."""

    observations: list[Observation] = field(default_factory=list)
    count: int = 0

    @classmethod
    def from_wire(cls, data: dict) -> BatchObservationsResponse:
        return cls(
            observations=[Observation.from_wire(o) for o in data.get("observations", [])],
            count=data.get("count", 0),
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
            project=data.get("project", ""),
            high=data.get("high", 0),
            medium=data.get("medium", 0),
            low=data.get("low", 0),
            unknown=data.get("unknown", 0),
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
            status=data.get("status", ""),
            template=data.get("template", ""),
            message=data.get("message", ""),
            session_id=data.get("sessionId", ""),
            extracted_data=data.get("extractedData"),
            created_at=data.get("createdAt", 0),
            observation_id=data.get("observationId", ""),
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
            version=data.get("version", ""),
            service=data.get("service", ""),
            java=data.get("java", ""),
            spring_boot=data.get("springBoot", ""),
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
        w = data.get("worker", {})
        d = data.get("database", {})
        return cls(
            worker=WorkerStats(
                is_processing=w.get("isProcessing", False),
                queue_depth=w.get("queueDepth", 0),
            ),
            database=DatabaseStats(
                total_observations=d.get("totalObservations", 0),
                total_summaries=d.get("totalSummaries", 0),
                total_sessions=d.get("totalSessions", 0),
                total_projects=d.get("totalProjects", 0),
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
            id=data.get("id", ""),
            name=data.get("name", ""),
            description=data.get("description", ""),
            version=data.get("version", ""),
            observation_types=data.get("observationTypes", []),
            observation_concepts=data.get("observationConcepts", []),
        )
