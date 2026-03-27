"""Unit tests for CortexMemClient (mocked HTTP)."""

import json
import pytest
import responses

from cortex_mem import CortexMemClient, APIError, NotFoundError, RateLimitError
from cortex_mem.dto import (
    SessionStartResponse,
    Experience,
    ICLPromptResult,
    SearchResult,
    ObservationsResponse,
    ObservationUpdate,
    QualityDistribution,
    VersionResponse,
    ProjectsResponse,
    StatsResponse,
    ModesResponse,
    ExtractionResult,
)

BASE = "http://localhost:37777"


def _client():
    return CortexMemClient(base_url=BASE, max_retries=1)


# ==================== Session ====================


class TestSession:
    @responses.activate
    def test_start_session(self):
        responses.add(
            responses.POST,
            f"{BASE}/api/session/start",
            json={"session_db_id": "db-1", "session_id": "s1", "prompt_number": 0},
            status=200,
        )
        c = _client()
        resp = c.start_session("s1", "/project")
        assert isinstance(resp, SessionStartResponse)
        assert resp.session_id == "s1"
        assert resp.session_db_id == "db-1"

    @responses.activate
    def test_update_session_user_id(self):
        responses.add(
            responses.PATCH,
            f"{BASE}/api/session/s1/user",
            json={"status": "ok", "sessionId": "s1", "userId": "u1"},
            status=200,
        )
        c = _client()
        resp = c.update_session_user_id("s1", "u1")
        assert resp["status"] == "ok"


# ==================== Capture ====================


class TestCapture:
    @responses.activate
    def test_record_observation(self):
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=204)
        c = _client()
        c.record_observation("s1", "/p", "Read", tool_input={"file": "a.txt"})

    @responses.activate
    def test_record_observation_wire_format(self):
        """Verify camelCase: project_path→cwd, extractedData stays camelCase."""
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=204)
        c = _client()
        c.record_observation(
            "s1", "/p", "Read",
            extracted_data={"key": "val"},
            source="test",
        )
        body = json.loads(responses.calls[0].request.body)
        assert "cwd" in body  # NOT project_path
        assert body["cwd"] == "/p"
        assert "extractedData" in body  # camelCase
        assert body["source"] == "test"

    @responses.activate
    def test_record_session_end(self):
        responses.add(responses.POST, f"{BASE}/api/ingest/session-end", status=204)
        c = _client()
        c.record_session_end("s1", "/p")

    @responses.activate
    def test_record_user_prompt(self):
        responses.add(responses.POST, f"{BASE}/api/ingest/user-prompt", status=204)
        c = _client()
        c.record_user_prompt("s1", "hello", project_path="/p")
        body = json.loads(responses.calls[0].request.body)
        assert "cwd" in body  # NOT project_path

    @responses.activate
    def test_fire_and_forget_swallows_error(self):
        """Fire-and-forget should NOT raise on API errors."""
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=500)
        c = _client()
        # Should not raise
        c.record_observation("s1", "/p", "Read")


# ==================== Retrieval ====================


class TestRetrieval:
    @responses.activate
    def test_retrieve_experiences(self):
        # Wire format uses Jackson SNAKE_CASE naming strategy
        responses.add(
            responses.POST,
            f"{BASE}/api/memory/experiences",
            json=[
                {"id": "e1", "task": "t1", "strategy": "s1", "outcome": "o1", "reuse_condition": "r1", "quality_score": 0.9},
                {"id": "e2", "task": "t2", "strategy": "s2", "outcome": "o2", "reuse_condition": "r2", "quality_score": 0.5},
            ],
            status=200,
        )
        c = _client()
        exps = c.retrieve_experiences("task", "/p", count=2)
        assert len(exps) == 2
        assert exps[0].id == "e1"
        assert exps[0].quality_score == 0.9

    @responses.activate
    def test_retrieve_experiences_wire_format(self):
        """Verify camelCase: requiredConcepts, userId."""
        responses.add(responses.POST, f"{BASE}/api/memory/experiences", json=[], status=200)
        c = _client()
        c.retrieve_experiences("t", "/p", required_concepts=["c1"], user_id="u1")
        body = json.loads(responses.calls[0].request.body)
        assert "requiredConcepts" in body
        assert "userId" in body

    @responses.activate
    def test_build_icl_prompt(self):
        responses.add(
            responses.POST,
            f"{BASE}/api/memory/icl-prompt",
            json={"prompt": "context here", "experienceCount": 3, "maxChars": 4000},
            status=200,
        )
        c = _client()
        result = c.build_icl_prompt("task", "/p")
        assert isinstance(result, ICLPromptResult)
        assert result.prompt == "context here"
        assert result.experience_count == 3

    @responses.activate
    def test_build_icl_prompt_wire_format(self):
        """Verify camelCase: maxChars, userId."""
        responses.add(responses.POST, f"{BASE}/api/memory/icl-prompt", json={}, status=200)
        c = _client()
        c.build_icl_prompt("t", "/p", max_chars=500, user_id="u1")
        body = json.loads(responses.calls[0].request.body)
        assert "maxChars" in body
        assert "userId" in body

    @responses.activate
    def test_search(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/search",
            json={"observations": [{"id": "o1", "content": "test"}], "strategy": "filter", "count": 1},
            status=200,
        )
        c = _client()
        result = c.search("/p", query="test", limit=5)
        assert isinstance(result, SearchResult)
        assert result.count == 1
        assert result.strategy == "filter"

    @responses.activate
    def test_list_observations(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/observations",
            json={"items": [{"id": "o1", "content": "c"}], "hasMore": False, "offset": 0, "limit": 20},
            status=200,
        )
        c = _client()
        resp = c.list_observations("/p")
        assert isinstance(resp, ObservationsResponse)
        assert len(resp.items) == 1

    @responses.activate
    def test_get_observations_by_ids(self):
        responses.add(
            responses.POST,
            f"{BASE}/api/observations/batch",
            json={"observations": [{"id": "o1", "content": "c"}], "count": 1},
            status=200,
        )
        c = _client()
        resp = c.get_observations_by_ids(["o1"])
        assert resp.count == 1


# ==================== Management ====================


class TestManagement:
    @responses.activate
    def test_trigger_refinement(self):
        responses.add(responses.POST, f"{BASE}/api/memory/refine", status=204)
        c = _client()
        c.trigger_refinement("/p")
        # Verify query param
        assert "project=" in responses.calls[0].request.url

    @responses.activate
    def test_submit_feedback_wire_format(self):
        """Verify camelCase: observationId, feedbackType."""
        responses.add(responses.POST, f"{BASE}/api/memory/feedback", status=204)
        c = _client()
        c.submit_feedback("obs-1", "useful", "good")
        body = json.loads(responses.calls[0].request.body)
        assert body["observationId"] == "obs-1"
        assert body["feedbackType"] == "useful"

    @responses.activate
    def test_update_observation(self):
        responses.add(responses.PATCH, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        c.update_observation("o1", title="New Title", extracted_data={"k": "v"})
        body = json.loads(responses.calls[0].request.body)
        assert body["title"] == "New Title"
        assert "extractedData" in body

    @responses.activate
    def test_delete_observation(self):
        responses.add(responses.DELETE, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        c.delete_observation("o1")

    @responses.activate
    def test_get_quality_distribution(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/memory/quality-distribution",
            json={"project": "/p", "high": 10, "medium": 5, "low": 2, "unknown": 1},
            status=200,
        )
        c = _client()
        qd = c.get_quality_distribution("/p")
        assert isinstance(qd, QualityDistribution)
        assert qd.total == 18


# ==================== Health ====================


class TestHealth:
    @responses.activate
    def test_health_check_ok(self):
        responses.add(responses.GET, f"{BASE}/api/health", json={"status": "ok"}, status=200)
        c = _client()
        c.health_check()  # should not raise

    @responses.activate
    def test_health_check_fail(self):
        responses.add(responses.GET, f"{BASE}/api/health", json={"status": "degraded"}, status=200)
        c = _client()
        with pytest.raises(Exception):
            c.health_check()


# ==================== Extraction ====================


class TestExtraction:
    @responses.activate
    def test_trigger_extraction(self):
        responses.add(responses.POST, f"{BASE}/api/extraction/run", status=204)
        c = _client()
        c.trigger_extraction("/p")
        assert "projectPath=" in responses.calls[0].request.url

    @responses.activate
    def test_get_latest_extraction(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/extraction/user_pref/latest",
            json={"status": "ok", "template": "user_pref", "extractedData": {"lang": "zh"}, "sessionId": "s1", "createdAt": 0, "observationId": "o1"},
            status=200,
        )
        c = _client()
        result = c.get_latest_extraction("/p", "user_pref", user_id="u1")
        assert result.status == "ok"
        assert result.extracted_data == {"lang": "zh"}

    @responses.activate
    def test_get_extraction_history(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/extraction/user_pref/history",
            json=[{"extractedData": {}, "sessionId": "s1", "createdAt": 0, "observationId": "o1"}],
            status=200,
        )
        c = _client()
        results = c.get_extraction_history("/p", "user_pref", limit=5)
        assert len(results) == 1


# ==================== Version / P1 ====================


class TestVersionAndP1:
    @responses.activate
    def test_get_version(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/version",
            json={"version": "1.0.0", "service": "claude-mem-java"},
            status=200,
        )
        c = _client()
        v = c.get_version()
        assert isinstance(v, VersionResponse)
        assert v.version == "1.0.0"

    @responses.activate
    def test_get_projects(self):
        responses.add(responses.GET, f"{BASE}/api/projects", json={"projects": ["/a", "/b"]}, status=200)
        c = _client()
        p = c.get_projects()
        assert isinstance(p, ProjectsResponse)
        assert len(p.projects) == 2

    @responses.activate
    def test_get_stats(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/stats",
            json={"worker": {"isProcessing": False, "queueDepth": 0}, "database": {"totalObservations": 100}},
            status=200,
        )
        c = _client()
        s = c.get_stats()
        assert isinstance(s, StatsResponse)
        assert s.database.total_observations == 100

    @responses.activate
    def test_get_modes(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/modes",
            json={"id": "m1", "name": "default", "observationTypes": ["feature"], "observationConcepts": ["test"]},
            status=200,
        )
        c = _client()
        m = c.get_modes()
        assert isinstance(m, ModesResponse)
        assert m.name == "default"

    @responses.activate
    def test_get_settings(self):
        responses.add(responses.GET, f"{BASE}/api/settings", json={"embedding_model": "test"}, status=200)
        c = _client()
        s = c.get_settings()
        assert s["embedding_model"] == "test"


# ==================== Error handling ====================


class TestErrors:
    @responses.activate
    def test_api_error(self):
        responses.add(responses.GET, f"{BASE}/api/health", json={"error": "down"}, status=500)
        c = _client()
        with pytest.raises(APIError) as exc_info:
            c.health_check()
        assert exc_info.value.status_code == 500

    @responses.activate
    def test_not_found(self):
        responses.add(responses.DELETE, f"{BASE}/api/memory/observations/x", json={"error": "not found"}, status=404)
        c = _client()
        with pytest.raises(NotFoundError):
            c.delete_observation("x")

    @responses.activate
    def test_rate_limited(self):
        responses.add(responses.GET, f"{BASE}/api/search", json={"error": "rate limited"}, status=429)
        c = _client()
        with pytest.raises(RateLimitError):
            c.search("/p")


# ==================== Lifecycle ====================


class TestLifecycle:
    def test_context_manager(self):
        with CortexMemClient(base_url=BASE) as c:
            assert c is not None

    def test_close(self):
        c = CortexMemClient(base_url=BASE)
        c.close()

    def test_closed_client_raises(self):
        """Operations on a closed client should raise CortexError."""
        c = CortexMemClient(base_url=BASE)
        c.close()
        with pytest.raises(Exception, match="closed"):
            c.health_check()
        with pytest.raises(Exception, match="closed"):
            c.get_version()
        with pytest.raises(Exception, match="closed"):
            c.search("/p", query="test")

    def test_context_manager_closes(self):
        """Client should be closed after exiting context."""
        c = CortexMemClient(base_url=BASE)
        with c:
            pass
        with pytest.raises(Exception, match="closed"):
            c.get_projects()


# ==================== Extended Management ====================


class TestManagementExtended:
    @responses.activate
    def test_update_observation_with_dataclass(self):
        """ObservationUpdate dataclass style — only non-None fields sent."""
        responses.add(responses.PATCH, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        update = ObservationUpdate(title="New Title", source="manual", concepts=["important"])
        c.update_observation("o1", update)
        body = json.loads(responses.calls[0].request.body)
        assert body["title"] == "New Title"
        assert body["source"] == "manual"
        assert body["concepts"] == ["important"]
        # None fields should NOT be present
        assert "subtitle" not in body
        assert "content" not in body

    @responses.activate
    def test_update_observation_kwargs_override_dataclass(self):
        """When both dataclass and kwargs provided, kwargs win."""
        responses.add(responses.PATCH, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        update = ObservationUpdate(title="From Dataclass")
        c.update_observation("o1", update, title="From Kwargs")
        body = json.loads(responses.calls[0].request.body)
        assert body["title"] == "From Kwargs"

    @responses.activate
    def test_update_observation_extracted_data_wire_format(self):
        """Verify extractedData stays camelCase in wire format."""
        responses.add(responses.PATCH, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        update = ObservationUpdate(extracted_data={"preference": "dark_mode"})
        c.update_observation("o1", update)
        body = json.loads(responses.calls[0].request.body)
        assert "extractedData" in body
        assert body["extractedData"] == {"preference": "dark_mode"}

    @responses.activate
    def test_update_observation_empty_id_raises(self):
        """Empty observation_id should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.update_observation("", title="x")

    @responses.activate
    def test_update_observation_no_fields_raises(self):
        """Calling update_observation with no fields should raise."""
        c = _client()
        with pytest.raises(Exception, match="at least one field"):
            c.update_observation("o1")
        with pytest.raises(Exception, match="at least one field"):
            c.update_observation("o1", ObservationUpdate())

    @responses.activate
    def test_delete_observation_empty_raises(self):
        """Empty observation_id should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.delete_observation("")

    @responses.activate
    def test_trigger_refinement_empty_raises(self):
        """Empty project_path should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.trigger_refinement("")

    @responses.activate
    def test_submit_feedback_empty_raises(self):
        """Empty observation_id or feedback_type should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.submit_feedback("", "useful")
        with pytest.raises(Exception):
            c.submit_feedback("o1", "")

    @responses.activate
    def test_get_quality_distribution_empty_raises(self):
        """Empty project_path should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.get_quality_distribution("")

    @responses.activate
    def test_quality_distribution_total_property(self):
        """QualityDistribution.total is a computed property."""
        responses.add(
            responses.GET,
            f"{BASE}/api/memory/quality-distribution",
            json={"project": "/p", "high": 10, "medium": 5, "low": 3, "unknown": 2},
            status=200,
        )
        c = _client()
        qd = c.get_quality_distribution("/p")
        assert qd.total == 20
        assert qd.high == 10
        assert qd.medium == 5
        assert qd.low == 3
        assert qd.unknown == 2


# ==================== Extended Extraction ====================


class TestExtractionExtended:
    @responses.activate
    def test_trigger_extraction_empty_raises(self):
        """Empty project_path should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.trigger_extraction("")

    @responses.activate
    def test_get_latest_extraction_with_all_fields(self):
        """Verify all ExtractionResult fields are parsed."""
        responses.add(
            responses.GET,
            f"{BASE}/api/extraction/user_pref/latest",
            json={
                "status": "ok",
                "template": "user_pref",
                "message": "extracted 3 preferences",
                "extractedData": {"lang": "zh", "theme": "dark"},
                "sessionId": "s-123",
                "createdAt": 1710000000,
                "observationId": "obs-456",
            },
            status=200,
        )
        c = _client()
        result = c.get_latest_extraction("/p", "user_pref", user_id="u1")
        assert isinstance(result, ExtractionResult)
        assert result.status == "ok"
        assert result.template == "user_pref"
        assert result.message == "extracted 3 preferences"
        assert result.extracted_data == {"lang": "zh", "theme": "dark"}
        assert result.session_id == "s-123"
        assert result.created_at == 1710000000
        assert result.observation_id == "obs-456"

    @responses.activate
    def test_get_latest_extraction_query_params(self):
        """Verify userId and projectPath are passed as query params."""
        responses.add(responses.GET, f"{BASE}/api/extraction/tpl/latest", json={}, status=200)
        c = _client()
        c.get_latest_extraction("/my/project", "tpl", user_id="user-1")
        url = responses.calls[0].request.url
        assert "userId=user-1" in url
        assert "projectPath=" in url

    @responses.activate
    def test_get_extraction_history_limit(self):
        """Verify limit query param is sent."""
        responses.add(responses.GET, f"{BASE}/api/extraction/tpl/history", json=[], status=200)
        c = _client()
        c.get_extraction_history("/p", "tpl", user_id="u1", limit=25)
        url = responses.calls[0].request.url
        assert "limit=25" in url
        assert "userId=u1" in url

    @responses.activate
    def test_get_extraction_history_multiple_results(self):
        """Verify multiple extraction results are parsed."""
        responses.add(
            responses.GET,
            f"{BASE}/api/extraction/tpl/history",
            json=[
                {"extractedData": {"v": 1}, "sessionId": "s1", "createdAt": 1, "observationId": "o1"},
                {"extractedData": {"v": 2}, "sessionId": "s2", "createdAt": 2, "observationId": "o2"},
                {"extractedData": {"v": 3}, "sessionId": "s3", "createdAt": 3, "observationId": "o3"},
            ],
            status=200,
        )
        c = _client()
        results = c.get_extraction_history("/p", "tpl")
        assert len(results) == 3
        assert all(isinstance(r, ExtractionResult) for r in results)
        assert results[0].extracted_data == {"v": 1}
        assert results[2].extracted_data == {"v": 3}


# ==================== Extended Session ====================


class TestSessionExtended:
    @responses.activate
    def test_start_session_with_user_id(self):
        """Verify user_id is sent in wire format."""
        responses.add(responses.POST, f"{BASE}/api/session/start", json={"session_db_id": "db-1", "session_id": "s1", "prompt_number": 0}, status=200)
        c = _client()
        c.start_session("s1", "/project", user_id="u1")
        body = json.loads(responses.calls[0].request.body)
        assert body["user_id"] == "u1"
        assert body["project_path"] == "/project"

    @responses.activate
    def test_update_session_user_id_empty_raises(self):
        """Empty session_id or user_id should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.update_session_user_id("", "u1")
        with pytest.raises(Exception):
            c.update_session_user_id("s1", "")


# ==================== Extended Retrieval ====================


class TestRetrievalExtended:
    @responses.activate
    def test_retrieve_experiences_with_source_filter(self):
        """Verify source filter is sent in wire format."""
        responses.add(responses.POST, f"{BASE}/api/memory/experiences", json=[], status=200)
        c = _client()
        c.retrieve_experiences("t", "/p", source="manual")
        body = json.loads(responses.calls[0].request.body)
        assert body["source"] == "manual"

    @responses.activate
    def test_search_with_source_filter(self):
        """Verify source filter is sent as query param."""
        responses.add(responses.GET, f"{BASE}/api/search", json={"observations": [], "count": 0}, status=200)
        c = _client()
        c.search("/p", query="test", source="tool_result")
        url = responses.calls[0].request.url
        assert "source=tool_result" in url

    @responses.activate
    def test_list_observations_pagination(self):
        """Verify offset and limit are sent as query params."""
        responses.add(responses.GET, f"{BASE}/api/observations", json={"items": [], "hasMore": False, "offset": 50, "limit": 25}, status=200)
        c = _client()
        resp = c.list_observations("/p", offset=50, limit=25)
        url = responses.calls[0].request.url
        assert "offset=50" in url
        assert "limit=25" in url
        assert resp.offset == 50
        assert resp.limit == 25

    @responses.activate
    def test_get_observations_by_ids_empty_raises(self):
        """Empty ids list should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.get_observations_by_ids([])

    @responses.activate
    def test_get_observations_by_ids_batch_too_large(self):
        """Batch size > 100 should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.get_observations_by_ids([f"id-{i}" for i in range(101)])

    @responses.activate
    def test_build_icl_prompt_with_max_chars(self):
        """Verify maxChars is sent in wire format."""
        responses.add(responses.POST, f"{BASE}/api/memory/icl-prompt", json={"prompt": "", "experienceCount": 0}, status=200)
        c = _client()
        c.build_icl_prompt("t", "/p", max_chars=2000)
        body = json.loads(responses.calls[0].request.body)
        assert body["maxChars"] == 2000

    @responses.activate
    def test_search_with_type_filter(self):
        """Verify type filter is sent as query param (type is a Python keyword, works as kwonly arg)."""
        responses.add(responses.GET, f"{BASE}/api/search", json={"observations": [], "count": 0}, status=200)
        c = _client()
        c.search("/p", query="test", type="feature")
        url = responses.calls[0].request.url
        assert "type=feature" in url

    @responses.activate
    def test_search_with_all_filters(self):
        """Verify all search filters are sent correctly."""
        responses.add(responses.GET, f"{BASE}/api/search", json={"observations": [], "count": 0}, status=200)
        c = _client()
        c.search("/p", query="q", type="feature", concept="test", source="manual", limit=10, offset=5)
        url = responses.calls[0].request.url
        assert "type=feature" in url
        assert "concept=test" in url
        assert "source=manual" in url
        assert "limit=10" in url
        assert "offset=5" in url


# ==================== JSON Decode Resilience ====================


class TestJSONDecodeResilience:
    @responses.activate
    def test_non_json_response_returns_none(self):
        """_request_json should return None for non-JSON responses instead of raising."""
        responses.add(
            responses.GET,
            f"{BASE}/api/version",
            body="<html>Internal Server Error</html>",
            status=200,
            content_type="text/html",
        )
        c = _client()
        # Should return None instead of raising JSONDecodeError
        result = c._request_json("GET", "/api/version")
        assert result is None


# ==================== DTO Tests ====================


class TestDTOs:
    def test_observation_update_to_wire_omits_none(self):
        """ObservationUpdate.to_wire() should only include non-None fields."""
        update = ObservationUpdate(title="T", source="s")
        wire = update.to_wire()
        assert wire == {"title": "T", "source": "s"}
        assert "subtitle" not in wire
        assert "content" not in wire
        assert "extracted_data" not in wire

    def test_observation_update_from_kwargs(self):
        """ObservationUpdate.from_kwargs() should create a valid update."""
        update = ObservationUpdate.from_kwargs(title="T", extracted_data={"k": "v"})
        assert update.title == "T"
        assert update.extracted_data == {"k": "v"}
        assert update.source is None

    def test_observation_update_empty_to_wire(self):
        """Empty ObservationUpdate should produce empty dict."""
        update = ObservationUpdate()
        assert update.to_wire() == {}
