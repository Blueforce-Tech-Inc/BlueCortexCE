"""Tests for DTO wire format serialization."""

import json
from cortex_mem.dto import (
    Experience,
    ICLPromptResult,
    Observation,
    SearchResult,
    ObservationsResponse,
    BatchObservationsResponse,
    QualityDistribution,
    ExtractionResult,
    VersionResponse,
    ProjectsResponse,
    StatsResponse,
    ModesResponse,
    SessionStartResponse,
    _to_int,
    _to_float,
)


class TestTypeConversionHelpers:
    """Tests for _to_int and _to_float wire format helpers."""

    def test_to_int_with_int(self):
        assert _to_int(42) == 42

    def test_to_int_with_string_number(self):
        assert _to_int("42") == 42

    def test_to_int_with_invalid_string(self):
        assert _to_int("not_a_number") == 0
        assert _to_int("not_a_number", default=99) == 99

    def test_to_int_with_none(self):
        assert _to_int(None) == 0

    def test_to_int_with_float_string(self):
        """Float string like '3.14' should truncate to 3 (via float conversion)."""
        assert _to_int("3.14") == 3

    def test_to_int_with_float_value(self):
        """Float value 3.7 should truncate to 3."""
        assert _to_int(3.7) == 3

    def test_to_float_with_float(self):
        assert _to_float(3.14) == 3.14

    def test_to_float_with_int(self):
        assert _to_float(42) == 42.0

    def test_to_float_with_string_number(self):
        assert _to_float("3.14") == 3.14

    def test_to_float_with_invalid_string(self):
        assert _to_float("bad") == 0.0
        assert _to_float("bad", default=9.9) == 9.9

    def test_to_float_with_none(self):
        assert _to_float(None) == 0.0


class TestDTOFromWire:
    def test_experience_from_wire(self):
        # Wire format uses Jackson SNAKE_CASE naming strategy
        data = {
            "id": "e1",
            "task": "t1",
            "strategy": "s1",
            "outcome": "o1",
            "reuse_condition": "r1",
            "quality_score": 0.85,
            "created_at": "2026-01-01",
        }
        exp = Experience.from_wire(data)
        assert exp.id == "e1"
        assert exp.reuse_condition == "r1"
        assert exp.quality_score == 0.85
        assert exp.created_at == "2026-01-01"

    def test_icl_prompt_result_from_wire(self):
        data = {"prompt": "hello", "experienceCount": "5", "maxChars": 4000}
        result = ICLPromptResult.from_wire(data)
        assert result.experience_count == 5  # parsed from string
        assert result.prompt == "hello"

    def test_icl_prompt_result_from_wire_snake_case_fallback(self):
        """Backend may use snake_case keys — SDK must accept both naming conventions."""
        data = {"prompt": "hello", "experience_count": "5", "max_chars": 4000}
        result = ICLPromptResult.from_wire(data)
        assert result.experience_count == 5  # parsed from string
        assert result.max_chars == 4000
        assert result.prompt == "hello"

    def test_observation_from_wire(self):
        # Wire format uses Jackson SNAKE_CASE naming strategy:
        # content_session_id (not sessionId), project (not projectPath), narrative (not content)
        data = {
            "id": "o1",
            "content_session_id": "s1",
            "project": "/p",
            "type": "feature",
            "title": "Test",
            "narrative": "Content",
            "facts": ["f1"],
            "concepts": ["c1"],
            "quality_score": 0.9,
            "source": "manual",
            "extractedData": {"key": "val"},
            "prompt_number": 5,
            "created_at": "2026-01-01",
            "created_at_epoch": 1710000000,
        }
        obs = Observation.from_wire(data)
        assert obs.id == "o1"
        assert obs.session_id == "s1"
        assert obs.project_path == "/p"
        assert obs.content == "Content"
        assert obs.quality_score == 0.9
        assert obs.extracted_data == {"key": "val"}
        assert obs.prompt_number == 5
        assert obs.created_at_epoch == 1710000000

    def test_observation_from_wire_missing_optional_fields(self):
        """Optional fields (prompt_number, created_at_epoch) default to 0."""
        data = {"id": "o1"}
        obs = Observation.from_wire(data)
        assert obs.prompt_number == 0
        assert obs.created_at_epoch == 0

    def test_search_result_from_wire(self):
        data = {
            "observations": [{"id": "o1", "narrative": "test", "content_session_id": "s1"}],
            "strategy": "hybrid",
            "fell_back": False,
            "count": 1,
        }
        sr = SearchResult.from_wire(data)
        assert sr.count == 1
        assert sr.strategy == "hybrid"
        assert len(sr.observations) == 1
        assert sr.observations[0].content == "test"

    def test_observations_response_from_wire(self):
        data = {
            "items": [{"id": "o1", "narrative": "test", "content_session_id": "s1"}],
            "has_more": True,
            "total": 100,
            "offset": 0,
            "limit": 20,
        }
        resp = ObservationsResponse.from_wire(data)
        assert resp.has_more is True
        assert resp.total == 100
        assert resp.items[0].content == "test"

    def test_quality_distribution_from_wire(self):
        data = {"project": "/p", "high": 10, "medium": 5, "low": 2, "unknown": 1}
        qd = QualityDistribution.from_wire(data)
        assert qd.total == 18

    def test_extraction_result_from_wire(self):
        data = {
            "status": "ok",
            "template": "user_pref",
            "extractedData": {"lang": "zh"},
            "sessionId": "s1",
            "createdAt": 1710000000000,
            "observationId": "o1",
        }
        er = ExtractionResult.from_wire(data)
        assert er.status == "ok"
        assert er.extracted_data == {"lang": "zh"}

    def test_extraction_result_from_wire_created_at_string(self):
        """createdAt may arrive as a string from some backend responses."""
        data = {"createdAt": "1710000000000", "sessionId": "s1", "observationId": "o1"}
        er = ExtractionResult.from_wire(data)
        assert er.created_at == 1710000000000  # parsed from string via _to_int

    def test_version_response_from_wire(self):
        data = {"version": "1.0.0", "service": "claude-mem-java"}
        vr = VersionResponse.from_wire(data)
        assert vr.version == "1.0.0"

    def test_projects_response_from_wire(self):
        data = {"projects": ["/a", "/b", "/c"]}
        pr = ProjectsResponse.from_wire(data)
        assert len(pr.projects) == 3

    def test_stats_response_from_wire(self):
        data = {
            "worker": {"isProcessing": True, "queueDepth": 5},
            "database": {"totalObservations": 100, "totalSummaries": 10, "totalSessions": 20, "totalProjects": 3},
        }
        sr = StatsResponse.from_wire(data)
        assert sr.worker.is_processing is True
        assert sr.database.total_observations == 100

    def test_modes_response_from_wire(self):
        data = {
            "id": "m1",
            "name": "default",
            "description": "Default mode",
            "version": "1.0",
            "observation_types": ["feature", "bug"],
            "observation_concepts": ["testing"],
        }
        mr = ModesResponse.from_wire(data)
        assert mr.name == "default"
        assert len(mr.observation_types) == 2

    def test_modes_response_from_wire_camelcase_fallback(self):
        """Backend Map.of() responses use camelCase keys — fallback must work."""
        data = {
            "id": "m1",
            "name": "default",
            "observationTypes": ["feature"],
            "observationConcepts": ["test"],
        }
        mr = ModesResponse.from_wire(data)
        assert mr.observation_types == ["feature"]
        assert mr.observation_concepts == ["test"]

    def test_observations_response_has_more_camelcase_fallback(self):
        """Backend Map.of() uses 'hasMore' (camelCase) — SDK must accept both."""
        data = {
            "items": [],
            "hasMore": True,
            "total": 50,
            "offset": 0,
            "limit": 20,
        }
        resp = ObservationsResponse.from_wire(data)
        assert resp.has_more is True
        assert resp.total == 50

    def test_session_start_response(self):
        data = {"session_db_id": "db-1", "session_id": "s-1", "prompt_number": 0}
        sr = SessionStartResponse(**data)
        assert sr.session_id == "s-1"

    def test_batch_observations_response_from_wire(self):
        data = {
            "observations": [
                {"id": "o1", "project": "/p", "narrative": "content1"},
                {"id": "o2", "project": "/p2", "narrative": "content2"},
            ],
            "count": 2,
        }
        from cortex_mem.dto import BatchObservationsResponse
        resp = BatchObservationsResponse.from_wire(data)
        assert resp.count == 2
        assert len(resp.observations) == 2
        assert resp.observations[0].content == "content1"
        assert resp.observations[1].project_path == "/p2"

    def test_observation_from_wire_null_strings(self):
        """Backend may return null for string fields — SDK must return '' not None."""
        data = {
            "id": None,
            "content_session_id": None,
            "project": None,
            "type": None,
            "title": None,
            "subtitle": None,
            "narrative": None,
            "source": None,
            "created_at": None,
        }
        obs = Observation.from_wire(data)
        # All string fields must be "" (not None)
        assert obs.id == ""
        assert obs.session_id == ""
        assert obs.project_path == ""
        assert obs.type == ""
        assert obs.title == ""
        assert obs.subtitle == ""
        assert obs.content == ""
        assert obs.source == ""
        assert obs.created_at == ""

    def test_experience_from_wire_null_strings(self):
        """Backend may return null for string fields in Experience."""
        data = {
            "id": None,
            "task": None,
            "strategy": None,
            "outcome": None,
            "reuse_condition": None,
            "quality_score": None,
            "created_at": None,
        }
        exp = Experience.from_wire(data)
        assert exp.id == ""
        assert exp.task == ""
        assert exp.strategy == ""
        assert exp.outcome == ""
        assert exp.reuse_condition == ""
        assert exp.quality_score == 0.0
        assert exp.created_at == ""

    def test_extraction_result_from_wire_null_strings(self):
        """Backend may return null for string fields in ExtractionResult."""
        data = {
            "status": None,
            "template": None,
            "message": None,
            "sessionId": None,
            "extractedData": None,
            "observationId": None,
        }
        er = ExtractionResult.from_wire(data)
        assert er.status == ""
        assert er.template == ""
        assert er.message == ""
        assert er.session_id == ""
        assert er.extracted_data is None  # Optional field — None is correct
        assert er.observation_id == ""
