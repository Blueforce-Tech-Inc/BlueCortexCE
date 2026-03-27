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
    QualityDistribution,
    VersionResponse,
    ProjectsResponse,
    StatsResponse,
    ModesResponse,
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
        responses.add(
            responses.POST,
            f"{BASE}/api/memory/experiences",
            json=[
                {"id": "e1", "task": "t1", "strategy": "s1", "outcome": "o1", "reuseCondition": "r1", "qualityScore": 0.9},
                {"id": "e2", "task": "t2", "strategy": "s2", "outcome": "o2", "reuseCondition": "r2", "qualityScore": 0.5},
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
