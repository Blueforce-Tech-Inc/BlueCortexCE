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

    def test_experience_from_wire_reuse_condition_camelcase_fallback(self):
        """reuseCondition camelCase fallback (consistent with fellBack/hasMore pattern)."""
        data = {"id": "e1", "reuseCondition": "only when safe"}
        exp = Experience.from_wire(data)
        assert exp.reuse_condition == "only when safe"

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
            "feedback_type": "SUCCESS",
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
        assert obs.feedback_type == "SUCCESS"  # SNAKE_CASE wire field → snake_case attribute
        assert obs.extracted_data == {"key": "val"}
        assert obs.prompt_number == 5
        assert obs.created_at_epoch == 1710000000

    def test_observation_from_wire_missing_optional_fields(self):
        """Optional fields (prompt_number, created_at_epoch) default to 0."""
        data = {"id": "o1"}
        obs = Observation.from_wire(data)
        assert obs.prompt_number == 0
        assert obs.created_at_epoch == 0

    def test_observation_from_wire_null_numeric_fields(self):
        """Backend may return null for numeric fields — SDK must default to 0."""
        data = {
            "id": "o1",
            "quality_score": None,
            "prompt_number": None,
            "created_at_epoch": None,
        }
        obs = Observation.from_wire(data)
        assert obs.quality_score == 0.0
        assert obs.prompt_number == 0
        assert obs.created_at_epoch == 0

    def test_observation_to_dict_wire_format(self):
        """to_dict() should output wire format field names, not Python attribute names."""
        obs = Observation(
            id="o1",
            session_id="s1",
            project_path="/tmp",
            type="fact",
            title="T",
            subtitle="S",
            content="narrative text",
            facts=["f1"],
            concepts=["c1"],
            quality_score=0.8,
            source="manual",
            extracted_data={"key": "val"},
            prompt_number=3,
            created_at="2026-01-01",
            created_at_epoch=1700000000,
        )
        d = obs.to_dict()
        # Wire format field names
        assert d["content_session_id"] == "s1"
        assert d["project"] == "/tmp"
        assert d["narrative"] == "narrative text"
        assert d["extractedData"] == {"key": "val"}
        # No Python attribute names
        assert "session_id" not in d
        assert "project_path" not in d
        assert "content" not in d
        assert "extracted_data" not in d
        # All fields present
        assert d["id"] == "o1"
        assert d["type"] == "fact"
        assert d["title"] == "T"
        assert d["source"] == "manual"
        assert d["quality_score"] == 0.8
        assert d["prompt_number"] == 3

    def test_observation_to_dict_with_feedback_type(self):
        """to_dict() should include feedback_type in wire format when set."""
        obs = Observation(
            id="o1",
            session_id="s1",
            project_path="/tmp",
            type="fact",
            feedback_type="SUCCESS",
            quality_score=0.85,
            source="verified",
        )
        d = obs.to_dict()
        assert d["feedback_type"] == "SUCCESS"
        assert d["quality_score"] == 0.85
        assert d["source"] == "verified"

    def test_observation_to_dict_omits_falsy(self):
        """to_dict() should omit empty optional fields (matching Go SDK omitempty behavior)."""
        obs = Observation(id="o1", session_id="s1", project_path="/tmp", type="fact")
        d = obs.to_dict()
        # omitempty fields omitted when zero/empty
        assert "title" not in d
        assert "subtitle" not in d
        assert "source" not in d
        assert "feedback_type" not in d
        assert "quality_score" not in d  # Go: omitempty
        assert "prompt_number" not in d  # Go: omitempty
        assert "extractedData" not in d  # Go: omitempty
        assert d["id"] == "o1"
        # Always-include fields (Go: no omitempty)
        assert d["content_session_id"] == "s1"
        assert d["project"] == "/tmp"
        assert d["type"] == "fact"
        assert d["narrative"] == ""

    def test_observation_to_dict_extracted_data(self):
        """to_dict() omits extractedData when empty (Go SDK omitempty parity)."""
        # Default (empty dict) → omitted
        obs_default = Observation(id="o1", session_id="s1", project_path="/p")
        d_default = obs_default.to_dict()
        assert "extractedData" not in d_default

        # Empty dict → omitted
        obs_empty = Observation(id="o1", session_id="s1", project_path="/p", extracted_data={})
        d_empty = obs_empty.to_dict()
        assert "extractedData" not in d_empty

        # Non-empty dict → included with data
        obs_data = Observation(id="o1", session_id="s1", project_path="/p", extracted_data={"k": "v"})
        d_data = obs_data.to_dict()
        assert d_data["extractedData"] == {"k": "v"}

    def test_experience_to_dict(self):
        exp = Experience(id="e1", task="t", strategy="s", outcome="o", quality_score=0.9)
        d = exp.to_dict()
        assert d["id"] == "e1"
        assert d["quality_score"] == 0.9
        assert isinstance(d, dict)

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

    def test_search_result_fell_back_camelcase_fallback(self):
        """Backend may use 'fellBack' (camelCase) — SDK must accept both naming conventions."""
        data = {
            "observations": [],
            "strategy": "keyword",
            "fellBack": True,
            "count": 0,
        }
        sr = SearchResult.from_wire(data)
        assert sr.fell_back is True

    def test_search_result_fell_back_snake_case_preferred(self):
        """When both 'fell_back' and 'fellBack' present, snake_case takes precedence."""
        data = {
            "observations": [],
            "fell_back": False,
            "fellBack": True,
        }
        sr = SearchResult.from_wire(data)
        assert sr.fell_back is False  # snake_case preferred

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

    def test_extraction_result_to_dict(self):
        """ExtractionResult.to_dict outputs camelCase wire format."""
        er = ExtractionResult(
            status="ok",
            template="user_pref",
            message="success",
            session_id="s1",
            extracted_data={"lang": "zh"},
            created_at=1710000000000,
            observation_id="o1",
        )
        d = er.to_dict()
        assert d["status"] == "ok"
        assert d["template"] == "user_pref"
        assert d["message"] == "success"
        assert d["sessionId"] == "s1"
        assert d["extractedData"] == {"lang": "zh"}
        assert d["createdAt"] == 1710000000000
        assert d["observationId"] == "o1"

    def test_extraction_result_to_dict_omits_empty(self):
        """ExtractionResult.to_dict omits empty/zero fields."""
        er = ExtractionResult(status="ok")
        d = er.to_dict()
        assert d == {"status": "ok"}
        assert "sessionId" not in d
        assert "extractedData" not in d

    def test_extraction_result_to_dict_includes_null_extracted_data(self):
        """extractedData=null should be included (explicit null, not missing)."""
        er = ExtractionResult(status="ok", extracted_data=None)
        d = er.to_dict()
        # extracted_data is None (explicit), should not be in dict (same as Go omitempty behavior)
        assert "extractedData" not in d

    def test_extraction_result_roundtrip(self):
        """from_wire → to_dict preserves wire format."""
        data = {
            "status": "ok",
            "template": "user_pref",
            "extractedData": {"lang": "zh"},
            "sessionId": "s1",
            "createdAt": 1710000000000,
            "observationId": "o1",
        }
        er = ExtractionResult.from_wire(data)
        d = er.to_dict()
        assert d["status"] == data["status"]
        assert d["template"] == data["template"]
        assert d["extractedData"] == data["extractedData"]
        assert d["sessionId"] == data["sessionId"]
        assert d["createdAt"] == data["createdAt"]
        assert d["observationId"] == data["observationId"]

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

    def test_modes_response_from_wire_empty_list_preserved(self):
        """Empty list in primary key should be preserved (not fall through to fallback)."""
        data = {
            "id": "m1",
            "name": "default",
            "observation_types": [],  # empty list — should be kept, not replaced
            "observationConcepts": ["fallback"],
        }
        mr = ModesResponse.from_wire(data)
        assert mr.observation_types == []  # empty list preserved
        assert mr.observation_concepts == ["fallback"]  # fallback used for missing key

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
            "feedback_updated_at": None,
            "last_accessed_at": None,
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
        assert obs.feedback_updated_at == ""
        assert obs.last_accessed_at == ""

    def test_observation_from_wire_files_and_timestamps(self):
        """Observation.from_wire correctly parses files_read, files_modified, feedback_updated_at, last_accessed_at."""
        data = {
            "id": "o1",
            "content_session_id": "s1",
            "project": "/p",
            "type": "feature",
            "files_read": ["main.py", "config.py"],
            "files_modified": ["output.txt"],
            "feedback_type": "PARTIAL",
            "feedback_updated_at": "2026-03-28T10:00:00Z",
            "last_accessed_at": "2026-03-28T12:00:00Z",
        }
        obs = Observation.from_wire(data)
        assert obs.files_read == ["main.py", "config.py"]
        assert obs.files_modified == ["output.txt"]
        assert obs.feedback_type == "PARTIAL"
        assert obs.feedback_updated_at == "2026-03-28T10:00:00Z"
        assert obs.last_accessed_at == "2026-03-28T12:00:00Z"

    def test_observation_from_wire_files_null_defaults(self):
        """Observation.from_wire defaults files_read/files_modified to empty list when null."""
        data = {"id": "o1", "files_read": None, "files_modified": None}
        obs = Observation.from_wire(data)
        assert obs.files_read == []
        assert obs.files_modified == []

    def test_observation_to_dict_includes_files_and_timestamps(self):
        """Observation.to_dict includes files_read, files_modified, feedback_updated_at, last_accessed_at when set."""
        obs = Observation(
            id="o1", session_id="s1", project_path="/p", type="fact",
            files_read=["a.py"], files_modified=["b.py"],
            feedback_type="SUCCESS",
            feedback_updated_at="2026-03-28T10:00:00Z",
            last_accessed_at="2026-03-28T12:00:00Z",
        )
        d = obs.to_dict()
        assert d["files_read"] == ["a.py"]
        assert d["files_modified"] == ["b.py"]
        assert d["feedback_type"] == "SUCCESS"
        assert d["feedback_updated_at"] == "2026-03-28T10:00:00Z"
        assert d["last_accessed_at"] == "2026-03-28T12:00:00Z"

    def test_observation_to_dict_omits_empty_files_and_timestamps(self):
        """Observation.to_dict omits empty files_read/files_modified and empty timestamps."""
        obs = Observation(id="o1", session_id="s1", project_path="/p", type="fact")
        d = obs.to_dict()
        assert "files_read" not in d
        assert "files_modified" not in d
        assert "feedback_updated_at" not in d
        assert "last_accessed_at" not in d

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

    def test_icl_prompt_result_from_wire_null_strings(self):
        """Backend may return null for string fields in ICLPromptResult."""
        data = {"prompt": None, "experienceCount": None, "maxChars": None}
        result = ICLPromptResult.from_wire(data)
        assert result.prompt == ""
        assert result.experience_count == 0
        assert result.max_chars == 0

    def test_search_result_from_wire_null_strategy(self):
        """SearchResult should handle null strategy gracefully."""
        data = {"observations": [], "strategy": None, "fell_back": None, "count": None}
        sr = SearchResult.from_wire(data)
        assert sr.strategy == ""
        assert sr.count == 0  # _to_int(None) → 0, but count uses data.get("count", 0) directly

    def test_quality_distribution_from_wire_null_project(self):
        """QualityDistribution should handle null project gracefully."""
        data = {"project": None, "high": None, "medium": None, "low": None, "unknown": None}
        qd = QualityDistribution.from_wire(data)
        assert qd.project == ""
        # None values for int fields default to 0 via data.get("high", 0)
        assert qd.total == 0

    def test_version_response_from_wire_null_strings(self):
        """VersionResponse should handle null string fields."""
        data = {"version": None, "service": None, "java": None, "springBoot": None}
        vr = VersionResponse.from_wire(data)
        assert vr.version == ""
        assert vr.service == ""
        assert vr.java == ""
        assert vr.spring_boot == ""

    def test_modes_response_from_wire_null_lists(self):
        """ModesResponse should handle null list fields."""
        data = {"id": None, "name": None, "observation_types": None, "observation_concepts": None}
        mr = ModesResponse.from_wire(data)
        assert mr.id == ""
        assert mr.name == ""
        assert mr.observation_types == []
        assert mr.observation_concepts == []


class TestNullSafetyExtra:
    """Additional null-safety tests for edge cases not covered elsewhere."""

    def test_stats_response_from_wire_null_data(self):
        """StatsResponse.from_wire should handle None data without crashing."""
        sr = StatsResponse.from_wire(None)
        assert sr.worker.is_processing is False
        assert sr.worker.queue_depth == 0
        assert sr.database.total_observations == 0

    def test_stats_response_from_wire_null_nested(self):
        """StatsResponse.from_wire should handle null worker/database dicts."""
        sr = StatsResponse.from_wire({"worker": None, "database": None})
        assert sr.worker.is_processing is False
        assert sr.database.total_observations == 0

    def test_search_result_count_null(self):
        """SearchResult.count should be 0 when backend sends null (not None)."""
        data = {"observations": [], "count": None}
        sr = SearchResult.from_wire(data)
        assert sr.count == 0  # _to_int(None, 0) → 0, not None

    def test_search_result_fell_back_null(self):
        """SearchResult.fell_back should be False when backend sends null."""
        data = {"observations": [], "fell_back": None}
        sr = SearchResult.from_wire(data)
        assert sr.fell_back is False

    def test_batch_observations_response_count_null(self):
        """BatchObservationsResponse.count should be 0 when backend sends null."""
        data = {"observations": [], "count": None}
        resp = BatchObservationsResponse.from_wire(data)
        assert resp.count == 0

    def test_experience_quality_score_null(self):
        """Experience.quality_score should be 0.0 when backend sends null."""
        data = {"id": "e1", "quality_score": None}
        exp = Experience.from_wire(data)
        assert exp.quality_score == 0.0
