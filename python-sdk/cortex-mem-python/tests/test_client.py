"""Unit tests for CortexMemClient (mocked HTTP)."""

import json
import pytest
import responses

from cortex_mem import CortexMemClient, APIError, NotFoundError, RateLimitError, CortexError
from cortex_mem.error import is_retryable, raise_for_status, ServerError, ConflictError, AuthError, ValidationError
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

    @responses.activate
    def test_fire_and_forget_no_retry_on_non_retryable(self):
        """Fire-and-forget should NOT retry on 400/401/404 (only on 429/502-504)."""
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=400)
        c = CortexMemClient(base_url=BASE, max_retries=3)
        # Should not raise, and should not retry (only 1 call made)
        c.record_observation("s1", "/p", "Read")
        assert len(responses.calls) == 1  # No retries for non-retryable errors

    @responses.activate
    def test_fire_and_forget_retries_on_429(self):
        """Fire-and-forget should retry on 429 (rate limited)."""
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=429)
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=204)
        c = CortexMemClient(base_url=BASE, max_retries=3, retry_backoff=0.01)
        c.record_observation("s1", "/p", "Read")
        assert len(responses.calls) == 2  # First 429, then retry succeeds

    @responses.activate
    def test_fire_and_forget_retries_on_503(self):
        """Fire-and-forget should retry on 503 (service unavailable)."""
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=503)
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=503)
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=204)
        c = CortexMemClient(base_url=BASE, max_retries=3, retry_backoff=0.01)
        c.record_observation("s1", "/p", "Read")
        assert len(responses.calls) == 3  # Two 503s, then success

    @responses.activate
    def test_fire_and_forget_exhausts_retries(self):
        """Fire-and-forget should swallow error after exhausting retries."""
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=502)
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=502)
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=502)
        c = CortexMemClient(base_url=BASE, max_retries=3, retry_backoff=0.01)
        # Should NOT raise even after all retries exhausted (fire-and-forget)
        c.record_observation("s1", "/p", "Read")
        assert len(responses.calls) == 3

    @responses.activate
    def test_fire_and_forget_connection_error_retries(self):
        """Fire-and-forget should retry on connection errors and swallow after exhaustion."""
        import responses as resp_lib
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", body=resp_lib.ConnectionError("connection refused"))
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", body=resp_lib.ConnectionError("connection refused"))
        responses.add(responses.POST, f"{BASE}/api/ingest/tool-use", body=resp_lib.ConnectionError("connection refused"))
        c = CortexMemClient(base_url=BASE, max_retries=3, retry_backoff=0.01)
        # Should NOT raise even on connection errors (fire-and-forget)
        c.record_observation("s1", "/p", "Read")

    @responses.activate
    def test_retrieval_connection_error_propagates(self):
        """Retrieval operations should propagate connection errors (not fire-and-forget)."""
        import responses as resp_lib
        responses.add(responses.GET, f"{BASE}/api/search", body=resp_lib.ConnectionError("connection refused"))
        c = _client()
        import requests
        with pytest.raises(requests.ConnectionError):
            c.search("/p", query="test")

    def test_record_observation_empty_session_id_raises(self):
        """Empty session_id should raise CortexError."""
        c = _client()
        with pytest.raises(Exception, match="session_id is required"):
            c.record_observation("", "/p", "Read")

    def test_record_session_end_empty_session_id_raises(self):
        """Empty session_id should raise CortexError."""
        c = _client()
        with pytest.raises(Exception, match="session_id is required"):
            c.record_session_end("", "/p")

    def test_record_user_prompt_empty_session_id_raises(self):
        """Empty session_id should raise CortexError."""
        c = _client()
        with pytest.raises(Exception, match="session_id is required"):
            c.record_user_prompt("", "hello")

    def test_record_user_prompt_empty_prompt_text_raises(self):
        """Empty prompt_text should raise CortexError."""
        c = _client()
        with pytest.raises(Exception, match="prompt_text is required"):
            c.record_user_prompt("s1", "")


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
            json={"observations": [{"id": "o1", "narrative": "test content", "content_session_id": "s1", "project": "/p"}], "strategy": "filter", "count": 1},
            status=200,
        )
        c = _client()
        result = c.search("/p", query="test", limit=5)
        assert isinstance(result, SearchResult)
        assert result.count == 1
        assert result.strategy == "filter"
        assert len(result.observations) == 1
        assert result.observations[0].content == "test content"
        assert result.observations[0].session_id == "s1"

    @responses.activate
    def test_list_observations(self):
        responses.add(
            responses.GET,
            f"{BASE}/api/observations",
            json={"items": [{"id": "o1", "narrative": "content", "content_session_id": "s1"}], "has_more": False, "offset": 0, "limit": 20},
            status=200,
        )
        c = _client()
        resp = c.list_observations("/p")
        assert isinstance(resp, ObservationsResponse)
        assert len(resp.items) == 1
        assert resp.items[0].content == "content"
        assert resp.items[0].session_id == "s1"

    @responses.activate
    def test_get_observations_by_ids(self):
        responses.add(
            responses.POST,
            f"{BASE}/api/observations/batch",
            json={"observations": [{"id": "o1", "narrative": "content", "content_session_id": "s1"}], "count": 1},
            status=200,
        )
        c = _client()
        resp = c.get_observations_by_ids(["o1"])
        assert resp.count == 1
        assert resp.observations[0].content == "content"


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
    def test_trigger_refinement_propagates_error(self):
        """Management operations must propagate API errors (NOT fire-and-forget)."""
        responses.add(responses.POST, f"{BASE}/api/memory/refine", json={"error": "busy"}, status=503)
        c = _client()
        with pytest.raises(ServerError):
            c.trigger_refinement("/p")

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
    def test_submit_feedback_propagates_error(self):
        """submit_feedback must propagate API errors."""
        responses.add(responses.POST, f"{BASE}/api/memory/feedback", json={"error": "not found"}, status=404)
        c = _client()
        with pytest.raises(NotFoundError):
            c.submit_feedback("bad-id", "useful")

    @responses.activate
    def test_update_observation(self):
        responses.add(responses.PATCH, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        c.update_observation("o1", title="New Title", extracted_data={"k": "v"})
        body = json.loads(responses.calls[0].request.body)
        assert body["title"] == "New Title"
        assert "extractedData" in body
        assert body["extractedData"] == {"k": "v"}

    @responses.activate
    def test_update_observation_propagates_conflict(self):
        """update_observation must propagate 409 Conflict."""
        responses.add(responses.PATCH, f"{BASE}/api/memory/observations/o1", json={"error": "conflict"}, status=409)
        c = _client()
        with pytest.raises(ConflictError):
            c.update_observation("o1", title="conflict")

    @responses.activate
    def test_delete_observation(self):
        responses.add(responses.DELETE, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        c.delete_observation("o1")

    @responses.activate
    def test_delete_observation_propagates_not_found(self):
        """delete_observation must propagate 404."""
        responses.add(responses.DELETE, f"{BASE}/api/memory/observations/o1", json={"error": "not found"}, status=404)
        c = _client()
        with pytest.raises(NotFoundError):
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

    @responses.activate
    def test_get_quality_distribution_propagates_error(self):
        """get_quality_distribution must propagate API errors."""
        responses.add(responses.GET, f"{BASE}/api/memory/quality-distribution", status=500)
        c = _client()
        with pytest.raises(ServerError):
            c.get_quality_distribution("/p")


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
            json={"id": "m1", "name": "default", "observation_types": ["feature"], "observation_concepts": ["test"]},
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
    def test_update_observation_narrative_field(self):
        """Verify narrative field is sent in wire format for cross-SDK consistency."""
        responses.add(responses.PATCH, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        update = ObservationUpdate(narrative="updated narrative")
        c.update_observation("o1", update)
        body = json.loads(responses.calls[0].request.body)
        assert body["narrative"] == "updated narrative"
        assert "content" not in body  # None fields omitted

    @responses.activate
    def test_update_observation_both_content_and_narrative(self):
        """Both content and narrative can be set (backend accepts either)."""
        responses.add(responses.PATCH, f"{BASE}/api/memory/observations/o1", status=204)
        c = _client()
        update = ObservationUpdate(content="the content", narrative="the narrative")
        c.update_observation("o1", update)
        body = json.loads(responses.calls[0].request.body)
        assert body["content"] == "the content"
        assert body["narrative"] == "the narrative"

    @responses.activate
    def test_update_observation_empty_id_raises(self):
        """Empty observation_id should raise."""
        c = _client()
        with pytest.raises(Exception):
            c.update_observation("", title="x")

    @responses.activate
    def test_update_observation_unknown_kwargs_raises(self):
        """Unknown kwargs should raise CortexError with clear message."""
        c = _client()
        with pytest.raises(Exception, match="unknown update fields.*typo_field"):
            c.update_observation("o1", typo_field="value")

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
        """Empty session_id or user_id should raise CortexError with clear message."""
        from cortex_mem import CortexError
        c = _client()
        with pytest.raises(CortexError, match="session_id is required"):
            c.update_session_user_id("", "u1")
        with pytest.raises(CortexError, match="user_id is required"):
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
        responses.add(responses.GET, f"{BASE}/api/observations", json={"items": [], "has_more": False, "offset": 50, "limit": 25}, status=200)
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
    def test_get_observations_by_ids_whitespace_raises(self):
        """Whitespace-only ID should raise CortexError."""
        c = _client()
        with pytest.raises(Exception, match="ids.*empty"):
            c.get_observations_by_ids(["valid-id", "   "])

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

    @responses.activate
    def test_start_session_non_json_response_graceful(self):
        """start_session should return default SessionStartResponse on non-JSON 200 response."""
        responses.add(
            responses.POST,
            f"{BASE}/api/session/start",
            body="not json",
            status=200,
            content_type="text/plain",
        )
        c = _client()
        resp = c.start_session("s1", "/p")
        # Should NOT crash — returns default values
        assert isinstance(resp, SessionStartResponse)
        assert resp.session_id == ""
        assert resp.session_db_id == ""

    @responses.activate
    def test_update_session_user_id_non_json_response_graceful(self):
        """update_session_user_id should return empty dict on non-JSON 200 response."""
        responses.add(
            responses.PATCH,
            f"{BASE}/api/session/s1/user",
            body="not json",
            status=200,
            content_type="text/plain",
        )
        c = _client()
        resp = c.update_session_user_id("s1", "u1")
        # Should NOT crash — returns empty dict
        assert resp == {}


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

    def test_observation_update_empty_to_wire(self):
        """Empty ObservationUpdate should produce empty dict."""
        update = ObservationUpdate()
        assert update.to_wire() == {}

    def test_observation_update_is_empty(self):
        """is_empty() returns True when no fields are set (Go SDK parity)."""
        assert ObservationUpdate().is_empty() is True

    def test_observation_update_is_empty_with_values(self):
        """is_empty() returns False when any field is set."""
        assert ObservationUpdate(title="x").is_empty() is False
        assert ObservationUpdate(source="manual").is_empty() is False
        assert ObservationUpdate(extracted_data={}).is_empty() is False
        assert ObservationUpdate(facts=[]).is_empty() is False  # empty list is still set

    def test_observation_update_is_empty_edge_cases(self):
        """is_empty() distinguishes None from empty string/list."""
        # Empty string is a set value (clears the field on backend)
        assert ObservationUpdate(title="").is_empty() is False
        # None means "not set" (field omitted from request)
        assert ObservationUpdate(title=None).is_empty() is True

    def test_observation_from_wire_field_mapping(self):
        """Observation.from_wire correctly maps backend field names."""
        from cortex_mem.dto import Observation
        obs = Observation.from_wire({
            "id": "o1",
            "content_session_id": "s1",
            "project": "/my/project",
            "type": "feature",
            "title": "Test",
            "narrative": "the content",
            "facts": ["f1"],
            "concepts": ["c1"],
            "quality_score": 0.85,
            "source": "manual",
            "extractedData": {"key": "val"},
            "prompt_number": 3,
            "created_at": "2026-01-01T00:00:00Z",
            "created_at_epoch": 1700000000,
        })
        assert obs.id == "o1"
        assert obs.session_id == "s1"       # content_session_id → session_id
        assert obs.project_path == "/my/project"  # project → project_path
        assert obs.content == "the content"  # narrative → content
        assert obs.source == "manual"
        assert obs.extracted_data == {"key": "val"}
        assert obs.prompt_number == 3
        assert obs.created_at_epoch == 1700000000


# ==================== Error Module ====================


class TestIsRetryable:
    """Tests for is_retryable() error classification."""

    def test_429_is_retryable(self):
        assert is_retryable(429) is True

    def test_502_is_retryable(self):
        assert is_retryable(502) is True

    def test_503_is_retryable(self):
        assert is_retryable(503) is True

    def test_504_is_retryable(self):
        assert is_retryable(504) is True

    def test_400_not_retryable(self):
        assert is_retryable(400) is False

    def test_404_not_retryable(self):
        assert is_retryable(404) is False

    def test_500_not_retryable(self):
        assert is_retryable(500) is False

    def test_200_not_retryable(self):
        assert is_retryable(200) is False


class TestRaiseForStatus:
    """Tests for raise_for_status() dispatching."""

    def test_2xx_does_not_raise(self):
        # Should not raise for 200-299
        raise_for_status(200, b'{"ok": true}')
        raise_for_status(201, b'')
        raise_for_status(204, b'')

    def test_401_raises_auth_error(self):
        with pytest.raises(AuthError):
            raise_for_status(401, b'{"error": "unauthorized"}')
        # Verify status code is preserved
        try:
            raise_for_status(401, b'bad token')
        except AuthError as e:
            assert e.status_code == 401

    def test_404_raises_not_found(self):
        with pytest.raises(NotFoundError):
            raise_for_status(404, b'{"error": "not found"}')

    def test_409_raises_conflict(self):
        with pytest.raises(ConflictError):
            raise_for_status(409, b'{"error": "conflict"}')

    def test_429_raises_rate_limit(self):
        with pytest.raises(RateLimitError):
            raise_for_status(429, b'{"error": "rate limited"}')

    def test_500_raises_server_error(self):
        with pytest.raises(ServerError):
            raise_for_status(500, b'{"error": "internal"}')

    def test_502_raises_server_error(self):
        with pytest.raises(ServerError):
            raise_for_status(502, b'bad gateway')

    def test_400_raises_api_error(self):
        with pytest.raises(APIError):
            raise_for_status(400, b'{"error": "bad request"}')

    def test_error_message_from_json(self):
        with pytest.raises(APIError, match="custom message"):
            raise_for_status(400, b'{"error": "custom message"}')

    def test_error_message_from_text(self):
        with pytest.raises(APIError, match="raw text"):
            raise_for_status(400, b'raw text error')

    def test_server_error_preserves_status_code(self):
        with pytest.raises(ServerError) as exc_info:
            raise_for_status(503, b'service unavailable')
        assert exc_info.value.status_code == 503


# ==================== Client Repr ====================


class TestClientRepr:
    def test_repr_open(self):
        c = CortexMemClient(base_url="http://localhost:37777")
        r = repr(c)
        assert "http://localhost:37777" in r
        assert "open" in r

    def test_repr_closed(self):
        c = CortexMemClient(base_url="http://localhost:37777")
        c.close()
        r = repr(c)
        assert "closed" in r


# ==================== User-Agent ====================


class TestUserAgent:
    @responses.activate
    def test_user_agent_header(self):
        """Verify User-Agent header includes SDK version."""
        responses.add(responses.GET, f"{BASE}/api/health", json={"status": "ok"}, status=200)
        c = _client()
        c.health_check()
        ua = responses.calls[0].request.headers.get("User-Agent", "")
        assert "cortex-mem-python/" in ua

    @responses.activate
    def test_accept_header(self):
        """Verify Accept header is set."""
        responses.add(responses.GET, f"{BASE}/api/health", json={"status": "ok"}, status=200)
        c = _client()
        c.health_check()
        accept = responses.calls[0].request.headers.get("Accept", "")
        assert "application/json" in accept


# ==================== API Key Auth ====================


class TestApiKeyAuth:
    @responses.activate
    def test_api_key_in_header(self):
        """Verify API key is sent as Bearer token."""
        responses.add(responses.GET, f"{BASE}/api/health", json={"status": "ok"}, status=200)
        c = CortexMemClient(base_url=BASE, api_key="test-key-123")
        c.health_check()
        auth = responses.calls[0].request.headers.get("Authorization", "")
        assert auth == "Bearer test-key-123"

    def test_no_auth_header_without_key(self):
        """Verify no Authorization header when api_key is not set."""
        c = CortexMemClient(base_url=BASE)
        headers = c._session.headers
        assert "Authorization" not in headers


# ==================== ValidationError ====================


class TestValidationError:
    """Tests for ValidationError type (Go SDK parity)."""

    def test_validation_error_is_cortex_error(self):
        """ValidationError should be a subclass of CortexError."""
        assert issubclass(ValidationError, CortexError)

    def test_validation_error_message(self):
        """ValidationError should carry a human-readable message."""
        err = ValidationError("session_id is required")
        assert "session_id is required" in str(err)
        assert "validation error" in str(err)

    def test_validation_error_message_attr(self):
        """ValidationError.message should be accessible."""
        err = ValidationError("batch size exceeded")
        assert err.message == "batch size exceeded"


# ==================== Custom Session ====================


class TestCustomSession:
    def test_custom_session_not_closed_on_client_close(self):
        """When a custom session is provided, closing the client should NOT close the session."""
        import requests
        s = requests.Session()
        c = CortexMemClient(base_url=BASE, session=s)
        c.close()
        # Session should still be usable (not closed) — verify it doesn't raise
        assert s.headers.get("Accept") == "application/json"

    def test_default_session_closed_on_client_close(self):
        """When using default session, closing the client SHOULD close the session."""
        c = CortexMemClient(base_url=BASE)
        c.close()
        # Subsequent requests should fail
        with pytest.raises(Exception):
            c.health_check()
