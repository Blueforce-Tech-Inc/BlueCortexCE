# coding: utf-8
"""Cortex CE SDK — Data Transfer Objects (dataclasses)."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import ClassVar

from .error import ValidationError


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


def _to_str(v: object, default: str = "") -> str:
    """Safely convert a wire value to ``str``.

    The scalar counterpart of :func:`_to_int`, :func:`_to_float` and
    :func:`_to_dict`, which every other field type here already had.

    It exists because ``_first_non_null(data, "created_at") or ""`` — the
    idiom this file used for all 17 string fields — only normalises ``None``
    and empty values. Any other wrong type passed straight through into a
    field annotated ``str``: a JSON number stayed an ``int`` and an object
    stayed a ``dict``, so a caller doing ``obs.created_at.upper()`` or
    ``obs.created_at.split("T")[0]`` got an ``AttributeError`` on data the
    type annotation promised was a string.

    The two other SDKs that coerce, JS's ``safeString`` and Go's
    ``encoding/json``, both end up with a string or nothing: JS stringifies
    numbers and booleans and drops objects and arrays, Go raises and drops
    every mismatched type. Converting scalars and dropping the rest keeps
    the annotation true in all three.

    ``bool`` is checked before ``int`` only for clarity — ``str(True)`` is
    ``"True"`` either way.
    """
    if v is None:
        return default
    if isinstance(v, str):
        return v
    if isinstance(v, (int, float, bool)):
        return str(v)
    # dict, list and anything else have no sensible string form here.
    return default


def _str_field(data: dict, *keys: str) -> str:
    """``_first_non_null`` + :func:`_to_str` for a string-typed field."""
    return _to_str(_first_non_null(data, *keys))


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
    """Safely convert wire value to float (handles string numbers, NaN, and Inf).

    Returns default for NaN/Inf values to prevent invalid JSON output.
    Matches _to_int()'s NaN/Inf handling for cross-function consistency.
    """
    if isinstance(v, (int, float)):
        f = float(v)
        if f != f or f == float('inf') or f == float('-inf'):  # NaN or Inf
            return default
        return f
    if isinstance(v, str):
        try:
            f = float(v)
            if f != f or f == float('inf') or f == float('-inf'):  # NaN or Inf
                return default
            return f
        except ValueError:
            return default
    return default


def _parse_nullable_float(v: object) -> float | None:
    """Safely convert wire value to float | None (preserves null from backend).

    Returns None if v is None (backend sent null).
    Returns None for NaN/Inf to prevent invalid JSON output.
    Matches _to_float()'s NaN/Inf sanitization for cross-function consistency.
    """
    if v is None:
        return None
    if isinstance(v, (int, float)):
        f = float(v)
        if f != f or f == float("inf") or f == float("-inf"):  # NaN or Inf
            return None
        return f
    if isinstance(v, str):
        try:
            f = float(v)
            if f != f or f == float("inf") or f == float("-inf"):
                return None
            return f
        except ValueError:
            return None
    return None


def _to_str_list(v: object, default: list[str] | None = None) -> list[str]:
    """Safely convert wire value to list[str].

    Handles the two shapes the backend actually sends for JSONB list columns:

    - a real JSON array: ``["a", "b"]``
    - a **JSON-encoded string**: ``'"[\\"a\\", \\"b\\"]"'`` — mem_observations' facts,
      concepts, files_read and files_modified are serialized as strings for the
      WebUI, so a plain ``isinstance(v, list)`` check silently discarded every
      non-empty value. Verified live: the wire carried
      ``concepts: '["allergy","peanut"]'`` and this helper returned ``[]``.
    - a comma-separated string, as a last resort (matches the JS SDK's
      ``safeStringOrStringList``), so an unexpected shape degrades to partial
      data rather than none.

    Returns default (or []) if v is None or cannot be interpreted as a list.
    Skips None values and converts non-string items via str() for defensive parsing.
    """
    fallback = default if default is not None else []

    if isinstance(v, str):
        stripped = v.strip()
        if not stripped:
            return fallback
        try:
            decoded = json.loads(stripped)
        except (ValueError, TypeError):
            # Not JSON — treat as a comma-separated list.
            return [part.strip() for part in stripped.split(",") if part.strip()]
        if decoded is None:
            return fallback
        if isinstance(decoded, list):
            return _str_list_from_sequence(decoded, fallback)
        return fallback

    if not isinstance(v, list):
        return fallback
    return _str_list_from_sequence(v, fallback)


def _str_list_from_sequence(items: list, fallback: list[str]) -> list[str]:
    """Convert an already-decoded sequence to list[str], skipping None items."""
    result: list[str] = []
    for item in items:
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


def _to_dict_list(v: object, default: list[dict] | None = None) -> list[dict]:
    """Safely convert wire value to list[dict] (for updateFiles field).

    Returns default (or []) if v is None or not a list.
    Each item that is not a dict is replaced with {} (defensive parsing).
    """
    if not isinstance(v, list):
        return default if default is not None else []
    result: list[dict] = []
    for item in v:
        if isinstance(item, dict):
            result.append(item)
        elif item is not None:
            # Defensive: convert non-dict items to empty dict
            result.append({})
        # else: item is None → skip (None items are filtered)
    return result


def _sanitize_for_json(obj: object) -> object:
    """Recursively replace NaN/Inf floats with None for valid JSON output.

    Python's json.dumps() outputs NaN/Infinity/-Infinity which are not valid JSON.
    This helper ensures to_dict() methods produce standards-compliant JSON.
    """
    if isinstance(obj, dict):
        return {k: _sanitize_for_json(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [_sanitize_for_json(item) for item in obj]
    if isinstance(obj, float):
        if obj != obj or obj == float("inf") or obj == float("-inf"):  # NaN or Inf
            return None
    return obj


# ==================== Session ====================


@dataclass
class SessionStartResponse:
    """Response from POST /api/session/start.

    Wire format uses snake_case keys (``session_db_id``, ``session_id``),
    matching the backend Jackson SNAKE_CASE naming strategy.
    ``update_files`` uses camelCase (``updateFiles``) to match the backend's
    ``@JsonProperty("updateFiles")`` annotation — MUST stay camelCase for
    proxy.js compatibility.
    """

    session_db_id: str = ""
    session_id: str = ""
    context: str = ""
    update_files: list[dict] = field(default_factory=list)
    prompt_number: int = 0

    def __repr__(self) -> str:
        return f"SessionStartResponse(session_id={self.session_id!r}, session_db_id={self.session_db_id!r}, update_files={len(self.update_files)})"

    def to_dict(self) -> dict:
        """Serialize to wire-compatible dict with snake_case keys.

        ``prompt_number`` is always included to match the backend's
        ``@JsonProperty("promptNumber")`` expectation.
        ``update_files`` is always included as ``updateFiles`` (camelCase)
        to match the backend's ``@JsonProperty("updateFiles")`` annotation.
        """
        d: dict = {
            "session_db_id": self.session_db_id,
            "session_id": self.session_id,
            "context": self.context,
            "updateFiles": _sanitize_for_json(self.update_files),
            "prompt_number": self.prompt_number,
        }
        return d

    @classmethod
    def from_wire(cls, data: dict) -> SessionStartResponse:
        return cls(
            session_db_id=_str_field(data, "session_db_id", "sessionDbId"),
            session_id=_str_field(data, "session_id", "sessionId"),
            context=_str_field(data, "context"),
            update_files=_to_dict_list(_first_non_null(data, "updateFiles")),
            prompt_number=_to_int(_first_non_null(data, "prompt_number", "promptNumber")),
        )


@dataclass
class SessionUserUpdateResponse:
    """Response from PATCH /api/session/{sessionId}/user.

    Wire format uses camelCase keys (``sessionId``, ``userId``),
    matching the backend Jackson @JsonProperty annotations.

    Cross-SDK parity: Go SessionUserUpdateResponse, JS SessionUserUpdateResponse.
    """

    status: str = ""
    session_id: str = ""
    user_id: str = ""

    def __repr__(self) -> str:
        return f"SessionUserUpdateResponse(status={self.status!r}, session_id={self.session_id!r})"

    def to_dict(self) -> dict:
        """Serialize to wire-compatible dict with camelCase keys.

        Matches backend ``@JsonProperty("sessionId")`` and ``@JsonProperty("userId")``.
        """
        d: dict = {"status": self.status}
        if self.session_id:
            d["sessionId"] = self.session_id
        if self.user_id:
            d["userId"] = self.user_id
        return d

    @classmethod
    def from_wire(cls, data: dict) -> SessionUserUpdateResponse:
        return cls(
            status=_to_str(data.get("status")),
            session_id=_str_field(data, "session_id", "sessionId"),
            user_id=_str_field(data, "user_id", "userId"),
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
    quality_score: float | None = None
    created_at: str = ""

    def __repr__(self) -> str:
        return f"Experience(id={self.id!r}, task={self.task[:50]!r}, quality_score={self.quality_score})"

    def to_dict(self) -> dict:
        """Serialize to a dict with Pythonic snake_case keys.

        All fields are always included (unlike Observation.to_dict() which omits
        empty fields). This is intentional for Experience since it is a read-only
        DTO — users construct Experience objects themselves, so there is no wire-format
        round-trip concern. quality_score is sanitized via _sanitize_for_json to
        prevent NaN/Inf from leaking into output (not valid JSON per RFC 7159).
        """
        return _sanitize_for_json({
            "id": self.id,
            "task": self.task,
            "strategy": self.strategy,
            "outcome": self.outcome,
            "reuse_condition": self.reuse_condition,
            "quality_score": self.quality_score,
            "created_at": self.created_at,
        })

    @classmethod
    def from_wire(cls, data: dict) -> Experience:
        # Wire format uses Jackson SNAKE_CASE naming strategy.
        # _to_str handles both the null the backend sends and any wrong type.
        return cls(
            id=_to_str(data.get("id")),
            task=_to_str(data.get("task")),
            strategy=_to_str(data.get("strategy")),
            outcome=_to_str(data.get("outcome")),
            reuse_condition=_str_field(data, "reuse_condition", "reuseCondition"),
            quality_score=_parse_nullable_float(_first_non_null(data, "quality_score", "qualityScore")),
            created_at=_str_field(data, "created_at", "createdAt"),
        )


@dataclass
class ICLPromptResult:
    """Result from POST /api/memory/icl-prompt."""

    prompt: str = ""
    experience_count: int = 0
    max_chars: int = 0

    def __repr__(self) -> str:
        return f"ICLPromptResult(experience_count={self.experience_count}, max_chars={self.max_chars})"

    @classmethod
    def from_wire(cls, data: dict) -> ICLPromptResult:
        return cls(
            prompt=_to_str(data.get("prompt")),
            experience_count=_to_int(_first_non_null(data, "experience_count", "experienceCount")),
            max_chars=_to_int(_first_non_null(data, "max_chars", "maxChars")),
        )


# ==================== Observation ====================


@dataclass
class ObservationUpdate:
    """Partial update for an existing observation (PATCH semantics).

    Only non-None fields are sent to the backend. The Go SDK reaches the same
    outcome through ``omitempty``, but it is not the same mechanism and the two
    do not agree everywhere:

    * Go's string fields are real pointers (``*string``); the equivalent here is
      ``Optional[str]``, which is a nullable value rather than a pointer.
    * Go's ``Facts``/``Concepts`` are slices, and ``omitempty`` drops a
      zero-length slice, so Go cannot clear those two fields at all. Python can:
      ``facts=[]`` is not None, so it is sent as ``"facts": []`` and the backend
      clears the column. Go's behaviour is tracked as P2-26.
    * For ``extracted_data`` the two do agree, and neither can clear it: Python
      skips both ``None`` and ``{}`` (see :meth:`to_wire`), and Go's ``omitempty``
      drops the empty map too. Java and JS can both put ``{}`` on the wire.

    Usage::

        # Dataclass style (recommended for IDE autocomplete & type checking)
        update = ObservationUpdate(title="New Title", source="manual")
        client.update_observation("obs-123", update)

        # Kwargs style (convenience)
        client.update_observation("obs-123", title="New Title", source="manual")

    .. note::

       ``content`` and ``narrative`` are aliases for the same backend field.
       Setting both will raise ``ValidationError`` — use one or the other,
       not both (matches Java SDK behavior for cross-SDK parity).
    """

    title: str | None = None
    subtitle: str | None = None
    content: str | None = None
    narrative: str | None = None  # Parallel to content; backend accepts either, but not both
    facts: list[str] | None = None
    concepts: list[str] | None = None
    source: str | None = None
    extracted_data: dict | None = None

    def __post_init__(self) -> None:
        if self.content is not None and self.narrative is not None:
            # ValidationError also subclasses ValueError, so handlers written
            # against the previous bare ValueError keep working.
            raise ValidationError(
                "content and narrative cannot both be set — they are aliases for the same "
                "backend field. Use either content=... or narrative=..., but not both.",
                field="content|narrative",
            )

    def is_empty(self) -> bool:
        """Return True if no fields are set (nothing to send).

        Matches Go SDK's ObservationUpdate.IsEmpty() for cross-SDK parity.
        ``extracted_data={}`` is treated as "unset". The two really are
        equivalent when the value is *read* — ExtractionController normalises
        both with ``getExtractedData() != null ? ... : Map.of()``, and the live
        table has 0 rows where extracted_data is an empty object — but that
        equivalence is why this field cannot be cleared, not a reason it does
        not need clearing. The backend accepts ``{}`` and stores it, and also
        accepts ``null`` and stores NULL; this SDK sends neither. Java and JS
        can send ``{}``, JS can send ``null``. Tracked as P2-27.
        """
        for attr in self._WIRE_FIELDS:
            val = getattr(self, attr)
            # Reading-wise {} and None are the same; writing-wise {} is the
            # only thing this SDK could send to clear the column, so the skip
            # below is a real capability gap, not just a tidy-up.
            if attr == "extracted_data" and isinstance(val, dict) and not val:
                continue
            if val is not None:
                return False
        return True

    def __bool__(self) -> bool:
        """Return True if at least one field is set (Pythonic truthiness).

        Allows using ``if update:`` instead of ``if not update.is_empty():``.
        """
        return not self.is_empty()

    # Python attr name → wire format key (differs for extracted_data → extractedData)
    _WIRE_FIELDS: ClassVar[dict[str, str]] = {
        "title": "title",
        "subtitle": "subtitle",
        # "content" → "narrative" (backend ObservationEntity has @JsonProperty("narrative"))
        "content": "narrative",
        "narrative": "narrative",
        "facts": "facts",
        "concepts": "concepts",
        "source": "source",
        "extracted_data": "extractedData",
    }

    def to_wire(self) -> dict:
        """Convert to wire format, omitting None fields.

        Both 'content' and 'narrative' map to the backend's ``narrative`` field,
        so at most one of them may be present. ``__post_init__`` already rejects
        that combination at construction time; this method re-checks because a
        dataclass is mutable and ``update.narrative = ...`` can be assigned after
        construction, which would otherwise discard the earlier value silently.
        ``extracted_data={}`` is treated as "unset" (omitted), matching
        :meth:`is_empty`. Note the consequence: the backend would accept ``{}``
        and clear the column, but this SDK can send neither ``{}`` nor ``null``,
        so ``extracted_data`` cannot be cleared through this client at all.
        Tracked as P2-27.
        """
        if self.content is not None and self.narrative is not None:
            raise ValidationError(
                "content and narrative cannot both be set — they are aliases for the same "
                "backend field. Use either content=... or narrative=..., but not both.",
                field="content|narrative",
            )
        body: dict = {}
        for attr, wire_key in self._WIRE_FIELDS.items():
            val = getattr(self, attr)
            # Reading-wise {} and None are the same. Writing-wise {} is the only
            # value this SDK could send to clear the column — see P2-27.
            if attr == "extracted_data" and isinstance(val, dict) and not val:
                continue
            if val is not None:
                body[wire_key] = val
        return body

@dataclass
class Observation:
    """A single observation record.

    .. wire-format note::
       ``extracted_data`` is accepted in **both** camelCase (``extractedData``) and
       snake_case (``extracted_data``) from the backend, but ``to_dict()`` always
       emits ``extractedData`` (camelCase) to match the server's ``@JsonProperty``.
       This round-trip is intentional: ``from_wire(to_dict())`` is always a no-op.
    """

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
    quality_score: float | None = None
    feedback_type: str = ""  # SUCCESS/PARTIAL/FAILURE/UNKNOWN
    feedback_updated_at: str = ""
    source: str = ""
    extracted_data: dict = field(default_factory=dict)
    prompt_number: int = 0
    created_at: str = ""
    created_at_epoch: int = 0
    last_accessed_at: str = ""
    access_count: int = 0
    refined_at: str = ""
    refined_from_ids: list[str] = field(default_factory=list)
    user_comment: str = ""

    def __repr__(self) -> str:
        return f"Observation(id={self.id!r}, type={self.type!r}, title={self.title[:50]!r})"

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
          prompt_number, created_at, created_at_epoch, last_accessed_at,
          access_count, refined_at, refined_from_ids, user_comment
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
        if self.quality_score is not None:
            d["quality_score"] = _sanitize_for_json(self.quality_score)
        if self.feedback_type:
            d["feedback_type"] = self.feedback_type
        if self.feedback_updated_at:
            d["feedback_updated_at"] = self.feedback_updated_at
        if self.prompt_number:
            d["prompt_number"] = self.prompt_number
        if self.source:
            d["source"] = self.source
        if self.extracted_data:
            d["extractedData"] = _sanitize_for_json(self.extracted_data)
        if self.created_at:
            d["created_at"] = self.created_at
        if self.created_at_epoch:
            d["created_at_epoch"] = self.created_at_epoch
        if self.last_accessed_at:
            d["last_accessed_at"] = self.last_accessed_at
        if self.access_count:
            d["access_count"] = self.access_count
        if self.refined_at:
            d["refined_at"] = self.refined_at
        if self.refined_from_ids:
            d["refined_from_ids"] = self.refined_from_ids
        if self.user_comment:
            d["user_comment"] = self.user_comment
        return d

    @classmethod
    def from_wire(cls, data: dict) -> Observation:
        # Wire format uses Jackson SNAKE_CASE naming strategy.
        # Key field renames: sessionId→content_session_id, projectPath→project, content→narrative
        # _to_str handles both the null the backend sends and any wrong type.
        # List/dict fields use defensive helpers (_to_str_list, _to_dict) to guard
        # against unexpected wire types (matches JS SDK's safeStringArray/safeRecord).
        return cls(
            id=_to_str(data.get("id")),
            session_id=_to_str(data.get("content_session_id")),
            project_path=_to_str(data.get("project")),
            type=_to_str(data.get("type")),
            title=_to_str(data.get("title")),
            subtitle=_to_str(data.get("subtitle")),
            content=_to_str(data.get("narrative")),
            facts=_to_str_list(data.get("facts")),
            concepts=_to_str_list(data.get("concepts")),
            files_read=_to_str_list(_first_non_null(data, "files_read", "filesRead")),
            files_modified=_to_str_list(_first_non_null(data, "files_modified", "filesModified")),
            quality_score=_parse_nullable_float(_first_non_null(data, "quality_score", "qualityScore")),
            feedback_type=_str_field(data, "feedback_type", "feedbackType"),
            feedback_updated_at=_str_field(data, "feedback_updated_at", "feedbackUpdatedAt"),
            source=_to_str(data.get("source")),
            extracted_data=_to_dict(_first_non_null(data, "extractedData", "extracted_data")),
            prompt_number=_to_int(_first_non_null(data, "prompt_number", "promptNumber")),
            created_at=_str_field(data, "created_at", "createdAt"),
            created_at_epoch=_to_int(_first_non_null(data, "created_at_epoch", "createdAtEpoch")),
            last_accessed_at=_str_field(data, "last_accessed_at", "lastAccessedAt"),
            access_count=_to_int(_first_non_null(data, "access_count", "accessCount")),
            refined_at=_str_field(data, "refined_at", "refinedAt"),
            refined_from_ids=_to_str_list(_first_non_null(data, "refined_from_ids", "refinedFromIds")),
            user_comment=_str_field(data, "user_comment", "userComment"),
        )


# ==================== Search ====================


@dataclass
class SearchResult:
    """Response from GET /api/search."""

    observations: list[Observation] = field(default_factory=list)
    strategy: str = ""
    fell_back: bool = False
    count: int = 0

    def __repr__(self) -> str:
        return f"SearchResult(count={self.count}, strategy={self.strategy!r}, fell_back={self.fell_back})"

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
            strategy=_to_str(data.get("strategy")),
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

    def __repr__(self) -> str:
        return f"ObservationsResponse(items={len(self.items)}, has_more={self.has_more}, total={self.total})"

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

    def __repr__(self) -> str:
        return f"QualityDistribution(project={self.project!r}, high={self.high}, medium={self.medium}, low={self.low}, unknown={self.unknown}, total={self.total})"

    @property
    def total(self) -> int:
        return self.high + self.medium + self.low + self.unknown

    @classmethod
    def from_wire(cls, data: dict) -> QualityDistribution:
        return cls(
            project=_to_str(data.get("project")),
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

    def __repr__(self) -> str:
        return f"ExtractionResult(status={self.status!r}, template={self.template!r}, session_id={self.session_id!r})"

    def to_dict(self) -> dict:
        """Serialize to a dict with camelCase keys.

        Uses camelCase keys (sessionId, extractedData, observationId) for
        JavaScript-ecosystem interop. Round-tripping through
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
            d["extractedData"] = _sanitize_for_json(self.extracted_data)
        # createdAt is always included (Go SDK: no omitempty tag)
        d["createdAt"] = self.created_at
        if self.observation_id:
            d["observationId"] = self.observation_id
        return d

    @classmethod
    def from_wire(cls, data: dict) -> ExtractionResult:
        return cls(
            status=_to_str(data.get("status")),
            template=_to_str(data.get("template")),
            message=_to_str(data.get("message")),
            session_id=_str_field(data, "session_id", "sessionId"),
            extracted_data=_to_dict(_first_non_null(data, "extractedData", "extracted_data")),
            created_at=_to_int(_first_non_null(data, "created_at", "createdAt")),
            observation_id=_str_field(data, "observation_id", "observationId"),
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
            version=_to_str(data.get("version")),
            service=_to_str(data.get("service")),
            java=_to_str(data.get("java")),
            spring_boot=_to_str(data.get("springBoot")),
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
class ObservationType:
    """Structured observation type from GET /api/modes.

    Backend returns observation_types as an array of objects carrying five keys:
    ``{id, label, description, emoji, work_emoji}``. ``emoji`` and ``work_emoji``
    are independent -- the backend sends both for every type, and neither
    substitutes for the other.
    """

    id: str = ""
    label: str = ""
    description: str = ""
    #: Badge emoji, e.g. "🔴". Distinct from :attr:`work_emoji`.
    emoji: str = ""
    #: Emoji used while the agent is working on this type, e.g. "🛠️".
    #: The backend sends both; neither is a fallback for the other.
    work_emoji: str = ""

    @classmethod
    def from_wire(cls, data: object) -> "ObservationType":
        if isinstance(data, dict):
            return cls(
                id=_to_str(data.get("id")),
                label=_to_str(data.get("label")),
                description=_to_str(data.get("description")),
                # Read independently on purpose. A single shared lookup would let
                # one field stand in for the other, so a type carrying only
                # work_emoji would report it as its badge too.
                emoji=_str_field(data, "emoji"),
                work_emoji=_str_field(data, "work_emoji", "workEmoji"),
            )
        # Backward compatibility: string input
        if isinstance(data, str):
            return cls(id=data, label=data, description="")
        return cls()

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "label": self.label,
            "description": self.description,
            "emoji": self.emoji,
            "work_emoji": self.work_emoji,
        }


@dataclass
class ObservationConcept:
    """Structured observation concept from GET /api/modes.

    Backend returns observation_concepts as array of objects: [{id, label, description}, ...]
    """

    id: str = ""
    label: str = ""
    description: str = ""

    @classmethod
    def from_wire(cls, data: object) -> "ObservationConcept":
        if isinstance(data, dict):
            return cls(
                id=_to_str(data.get("id")),
                label=_to_str(data.get("label")),
                description=_to_str(data.get("description")),
            )
        if isinstance(data, str):
            return cls(id=data, label=data, description="")
        return cls()

    def to_dict(self) -> dict:
        return {"id": self.id, "label": self.label, "description": self.description}


def _parse_observation_type_list(v: object) -> list[ObservationType]:
    """Parse observation_types array (objects or strings) into ObservationType list."""
    if not isinstance(v, list):
        return []
    result: list[ObservationType] = []
    for item in v:
        result.append(ObservationType.from_wire(item))
    return result


def _parse_observation_concept_list(v: object) -> list[ObservationConcept]:
    """Parse observation_concepts array (objects or strings) into ObservationConcept list."""
    if not isinstance(v, list):
        return []
    result: list[ObservationConcept] = []
    for item in v:
        result.append(ObservationConcept.from_wire(item))
    return result


@dataclass
class ModesResponse:
    """Response from GET /api/modes."""

    id: str = ""
    name: str = ""
    description: str = ""
    version: str = ""
    observation_types: list[ObservationType] = field(default_factory=list)
    observation_concepts: list[ObservationConcept] = field(default_factory=list)

    @classmethod
    def from_wire(cls, data: dict) -> ModesResponse:
        return cls(
            id=_to_str(data.get("id")),
            name=_to_str(data.get("name")),
            description=_to_str(data.get("description")),
            version=_to_str(data.get("version")),
            observation_types=_parse_observation_type_list(_first_non_null(data, "observation_types", "observationTypes")),
            observation_concepts=_parse_observation_concept_list(_first_non_null(data, "observation_concepts", "observationConcepts")),
        )

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "description": self.description,
            "version": self.version,
            "observation_types": [t.to_dict() for t in self.observation_types],
            "observation_concepts": [c.to_dict() for c in self.observation_concepts],
        }
