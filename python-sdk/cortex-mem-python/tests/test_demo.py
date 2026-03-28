"""Unit tests for Python SDK HTTP Server Demo (Flask).

Tests validation logic, error handlers, and request routing
without requiring a real backend (mocked SDK client).
"""

import sys
from pathlib import Path

# Add the demo directory to sys.path so `from app import app` works
_demo_dir = str(Path(__file__).resolve().parent.parent / "examples" / "http-server")
if _demo_dir not in sys.path:
    sys.path.insert(0, _demo_dir)

import pytest


@pytest.fixture
def app():
    """Create a test Flask app with a mocked CortexMemClient."""
    # Patch the client before importing app
    from unittest.mock import MagicMock, patch

    mock_client = MagicMock()

    with patch("app.client", mock_client):
        from app import app as flask_app
        flask_app.config["TESTING"] = True
        flask_app._mock_client = mock_client
        yield flask_app


@pytest.fixture
def client(app):
    """Flask test client."""
    return app.test_client()


# ==================== Health ====================


class TestHealth:
    def test_health_ok(self, app, client):
        app._mock_client.health_check.return_value = None
        resp = client.get("/health")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["service"] == "python-sdk-http-server"
        assert "time" in data

    def test_health_unhealthy(self, app, client):
        from cortex_mem import CortexError
        app._mock_client.health_check.side_effect = CortexError("down")
        resp = client.get("/health")
        assert resp.status_code == 503
        assert "unhealthy" in resp.get_json()["error"]


# ==================== Validation Helpers ====================


class TestRequire:
    def test_missing_field(self, client):
        resp = client.post("/chat", json={})
        assert resp.status_code == 400
        assert "project is required" in resp.get_json()["error"]

    def test_empty_string_field(self, client):
        resp = client.post("/chat", json={"project": "", "message": "hi"})
        assert resp.status_code == 400
        assert "project is required" in resp.get_json()["error"]

    def test_whitespace_only_field(self, client):
        resp = client.post("/chat", json={"project": "  ", "message": "hi"})
        assert resp.status_code == 400
        assert "project is required" in resp.get_json()["error"]

    def test_none_field(self, client):
        resp = client.post("/chat", json={"project": None, "message": "hi"})
        assert resp.status_code == 400
        assert "project is required" in resp.get_json()["error"]

    def test_zero_is_valid(self, app, client):
        """0 should not be treated as missing."""
        from cortex_mem import ICLPromptResult
        app._mock_client.build_icl_prompt.return_value = ICLPromptResult(prompt="p", experience_count=0)
        resp = client.post("/chat", json={"project": "/p", "message": "hi", "maxChars": 0})
        assert resp.status_code == 200


# ==================== Search ====================


class TestSearch:
    def test_search_missing_project(self, client):
        resp = client.get("/search")
        assert resp.status_code == 400
        assert "project is required" in resp.get_json()["error"]

    def test_search_invalid_limit(self, client):
        resp = client.get("/search?project=/p&limit=abc")
        assert resp.status_code == 400
        assert "limit must be an integer" in resp.get_json()["error"]

    def test_search_limit_too_high(self, client):
        resp = client.get("/search?project=/p&limit=101")
        assert resp.status_code == 400
        assert "limit must be between 0 and 100" in resp.get_json()["error"]

    def test_search_negative_limit(self, client):
        resp = client.get("/search?project=/p&limit=-1")
        assert resp.status_code == 400

    def test_search_negative_offset(self, client):
        resp = client.get("/search?project=/p&offset=-1")
        assert resp.status_code == 400
        assert "offset must be non-negative" in resp.get_json()["error"]

    def test_search_invalid_offset(self, client):
        resp = client.get("/search?project=/p&offset=xyz")
        assert resp.status_code == 400
        assert "offset must be an integer" in resp.get_json()["error"]

    def test_search_ok(self, app, client):
        from cortex_mem import SearchResult
        app._mock_client.search.return_value = SearchResult(observations=[], strategy="hybrid", count=0)
        resp = client.get("/search?project=/p&query=test&limit=10")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["strategy"] == "hybrid"
        assert data["count"] == 0


# ==================== Experiences ====================


class TestExperiences:
    def test_experiences_missing_project(self, client):
        resp = client.get("/experiences?task=build")
        assert resp.status_code == 400
        assert "project is required" in resp.get_json()["error"]

    def test_experiences_missing_task(self, client):
        resp = client.get("/experiences?project=/p")
        assert resp.status_code == 400
        assert "task is required" in resp.get_json()["error"]

    def test_experiences_invalid_count(self, client):
        resp = client.get("/experiences?project=/p&task=build&count=abc")
        assert resp.status_code == 400

    def test_experiences_count_out_of_range(self, client):
        resp = client.get("/experiences?project=/p&task=build&count=101")
        assert resp.status_code == 400
        assert "count must be between 1 and 100" in resp.get_json()["error"]

    def test_experiences_count_zero(self, client):
        resp = client.get("/experiences?project=/p&task=build&count=0")
        assert resp.status_code == 400

    def test_experiences_ok(self, app, client):
        from cortex_mem import Experience
        app._mock_client.retrieve_experiences.return_value = [
            Experience(id="e1", task="build API", strategy="use REST", quality_score=0.9)
        ]
        resp = client.get("/experiences?project=/p&task=build+API")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["count"] == 1
        assert data["experiences"][0]["id"] == "e1"


# ==================== ICL Prompt ====================


class TestICLPrompt:
    def test_iclprompt_missing_project(self, client):
        resp = client.get("/iclprompt?task=test")
        assert resp.status_code == 400

    def test_iclprompt_missing_task(self, client):
        resp = client.get("/iclprompt?project=/p")
        assert resp.status_code == 400

    def test_iclprompt_ok(self, app, client):
        from cortex_mem import ICLPromptResult
        app._mock_client.build_icl_prompt.return_value = ICLPromptResult(
            prompt="context here", experience_count=3, max_chars=1000
        )
        resp = client.get("/iclprompt?project=/p&task=test&maxChars=1000")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["prompt"] == "context here"
        assert data["experience_count"] == 3


# ==================== Observations ====================


class TestObservations:
    def test_list_missing_project(self, client):
        resp = client.get("/observations")
        assert resp.status_code == 400

    def test_list_invalid_limit(self, client):
        resp = client.get("/observations?project=/p&limit=abc")
        assert resp.status_code == 400

    def test_list_limit_too_high(self, client):
        resp = client.get("/observations?project=/p&limit=101")
        assert resp.status_code == 400

    def test_list_ok(self, app, client):
        from cortex_mem import ObservationsResponse, Observation
        app._mock_client.list_observations.return_value = ObservationsResponse(
            items=[Observation(id="o1", content="test")], has_more=False, total=1
        )
        resp = client.get("/observations?project=/p&limit=10")
        assert resp.status_code == 200
        data = resp.get_json()
        assert len(data["items"]) == 1
        assert data["items"][0]["id"] == "o1"

    def test_batch_missing_ids(self, client):
        resp = client.post("/observations/batch", json={})
        assert resp.status_code == 400
        assert "ids is required" in resp.get_json()["error"]

    def test_batch_empty_ids(self, client):
        resp = client.post("/observations/batch", json={"ids": []})
        assert resp.status_code == 400

    def test_batch_too_many(self, client):
        resp = client.post("/observations/batch", json={"ids": [str(i) for i in range(101)]})
        assert resp.status_code == 400
        assert "batch size exceeds maximum of 100" in resp.get_json()["error"]

    def test_batch_empty_id_in_list(self, client):
        resp = client.post("/observations/batch", json={"ids": ["o1", "", "o3"]})
        assert resp.status_code == 400
        assert "ids[1] is empty" in resp.get_json()["error"]

    def test_create_missing_fields(self, client):
        resp = client.post("/observations/create", json={})
        assert resp.status_code == 400

    def test_create_ok(self, app, client):
        app._mock_client.record_observation.return_value = None
        resp = client.post("/observations/create", json={
            "project": "/p", "session_id": "s1", "tool_name": "Read"
        })
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "recorded"

    def test_update_empty_id(self, client):
        resp = client.patch("/observations/  ", json={"title": "new"})
        assert resp.status_code == 400
        assert "observation id is required" in resp.get_json()["error"]

    def test_update_no_fields(self, client):
        resp = client.patch("/observations/o1", json={})
        assert resp.status_code == 400
        assert "at least one field must be provided" in resp.get_json()["error"]

    def test_update_ok(self, app, client):
        app._mock_client.update_observation.return_value = None
        resp = client.patch("/observations/o1", json={"title": "New Title", "source": "manual"})
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "updated"
        # Verify correct kwargs passed to SDK
        app._mock_client.update_observation.assert_called_once()
        call_kwargs = app._mock_client.update_observation.call_args
        assert call_kwargs[0][0] == "o1"
        assert call_kwargs[1]["title"] == "New Title"
        assert call_kwargs[1]["source"] == "manual"

    def test_update_with_extracted_data(self, app, client):
        app._mock_client.update_observation.return_value = None
        resp = client.patch("/observations/o1", json={"extractedData": {"key": "val"}})
        assert resp.status_code == 200
        call_kwargs = app._mock_client.update_observation.call_args
        assert call_kwargs[1]["extracted_data"] == {"key": "val"}

    def test_delete_empty_id(self, client):
        resp = client.delete("/observations/  ")
        assert resp.status_code == 400

    def test_delete_ok(self, app, client):
        app._mock_client.delete_observation.return_value = None
        resp = client.delete("/observations/o1")
        assert resp.status_code == 204


# ==================== Session ====================


class TestSession:
    def test_start_missing_fields(self, client):
        resp = client.post("/session/start", json={})
        assert resp.status_code == 400

    def test_start_ok(self, app, client):
        from cortex_mem import SessionStartResponse
        app._mock_client.start_session.return_value = SessionStartResponse(
            session_db_id="db-1", session_id="s1", context="ctx", prompt_number=5
        )
        resp = client.post("/session/start", json={"session_id": "s1", "project": "/p"})
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["session_db_id"] == "db-1"
        assert data["prompt_number"] == 5

    def test_user_missing_fields(self, client):
        resp = client.patch("/session/user", json={})
        assert resp.status_code == 400

    def test_user_ok(self, app, client):
        app._mock_client.update_session_user_id.return_value = {"status": "ok"}
        resp = client.patch("/session/user", json={"session_id": "s1", "user_id": "u1"})
        assert resp.status_code == 200


# ==================== Management Endpoints ====================


class TestManagement:
    def test_projects_ok(self, app, client):
        from cortex_mem import ProjectsResponse
        app._mock_client.get_projects.return_value = ProjectsResponse(projects=["p1", "p2"])
        resp = client.get("/projects")
        assert resp.status_code == 200
        assert resp.get_json()["projects"] == ["p1", "p2"]

    def test_stats_ok(self, app, client):
        from cortex_mem import StatsResponse, WorkerStats, DatabaseStats
        app._mock_client.get_stats.return_value = StatsResponse(
            worker=WorkerStats(is_processing=True, queue_depth=3),
            database=DatabaseStats(total_observations=100)
        )
        resp = client.get("/stats")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["worker"]["is_processing"] is True
        assert data["database"]["total_observations"] == 100

    def test_modes_ok(self, app, client):
        from cortex_mem import ModesResponse
        app._mock_client.get_modes.return_value = ModesResponse(
            id="m1", name="default", observation_types=["type1"]
        )
        resp = client.get("/modes")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["name"] == "default"
        assert data["observation_types"] == ["type1"]

    def test_settings_ok(self, app, client):
        app._mock_client.get_settings.return_value = {"key": "val"}
        resp = client.get("/settings")
        assert resp.status_code == 200
        assert resp.get_json()["key"] == "val"

    def test_quality_missing_project(self, client):
        resp = client.get("/quality")
        assert resp.status_code == 400

    def test_quality_ok(self, app, client):
        from cortex_mem import QualityDistribution
        app._mock_client.get_quality_distribution.return_value = QualityDistribution(
            project="/p", high=5, medium=3, low=1, unknown=0
        )
        resp = client.get("/quality?project=/p")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["high"] == 5
        assert data["total"] == 9


# ==================== Extraction ====================


class TestExtraction:
    def test_latest_missing_template(self, client):
        resp = client.get("/extraction/latest?project=/p")
        assert resp.status_code == 400
        assert "template is required" in resp.get_json()["error"]

    def test_latest_missing_project(self, client):
        resp = client.get("/extraction/latest?template=user_prefs")
        assert resp.status_code == 400

    def test_latest_ok(self, app, client):
        from cortex_mem import ExtractionResult
        app._mock_client.get_latest_extraction.return_value = ExtractionResult(
            status="ok", template="user_prefs", extracted_data={"pref": "dark"}
        )
        resp = client.get("/extraction/latest?template=user_prefs&project=/p")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["extractedData"]["pref"] == "dark"

    def test_history_missing_params(self, client):
        resp = client.get("/extraction/history")
        assert resp.status_code == 400

    def test_history_invalid_limit(self, client):
        resp = client.get("/extraction/history?template=t&project=/p&limit=abc")
        assert resp.status_code == 400

    def test_history_ok(self, app, client):
        from cortex_mem import ExtractionResult
        app._mock_client.get_extraction_history.return_value = [
            ExtractionResult(status="ok", template="t", created_at=1000)
        ]
        resp = client.get("/extraction/history?template=t&project=/p")
        assert resp.status_code == 200
        data = resp.get_json()
        assert len(data) == 1

    def test_run_missing_project(self, client):
        resp = client.post("/extraction/run")
        assert resp.status_code == 400

    def test_run_ok(self, app, client):
        app._mock_client.trigger_extraction.return_value = None
        resp = client.post("/extraction/run?project=/p")
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "extraction triggered"


# ==================== Refine / Feedback ====================


class TestRefineFeedback:
    def test_refine_missing_project(self, client):
        resp = client.post("/refine")
        assert resp.status_code == 400

    def test_refine_ok(self, app, client):
        app._mock_client.trigger_refinement.return_value = None
        resp = client.post("/refine?project=/p")
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "refined"

    def test_feedback_missing_fields(self, client):
        resp = client.post("/feedback", json={})
        assert resp.status_code == 400

    def test_feedback_ok(self, app, client):
        app._mock_client.submit_feedback.return_value = None
        resp = client.post("/feedback", json={
            "observationId": "o1", "feedbackType": "SUCCESS"
        })
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "submitted"


# ==================== Ingest ====================


class TestIngest:
    def test_ingest_prompt_missing_fields(self, client):
        resp = client.post("/ingest/prompt", json={})
        assert resp.status_code == 400

    def test_ingest_prompt_ok(self, app, client):
        app._mock_client.record_user_prompt.return_value = None
        resp = client.post("/ingest/prompt", json={
            "project": "/p", "session_id": "s1", "prompt": "hello"
        })
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "recorded"

    def test_session_end_missing_fields(self, client):
        resp = client.post("/ingest/session-end", json={})
        assert resp.status_code == 400

    def test_session_end_ok(self, app, client):
        app._mock_client.record_session_end.return_value = None
        resp = client.post("/ingest/session-end", json={
            "project": "/p", "session_id": "s1"
        })
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "ended"


# ==================== Error Handling ====================


class TestErrorHandling:
    def test_api_error_handler(self, app, client):
        from cortex_mem import APIError
        app._mock_client.get_version.side_effect = APIError(503, "backend down")
        resp = client.get("/version")
        assert resp.status_code == 503
        assert "backend down" in resp.get_json()["error"]

    def test_validation_error_handler(self, app, client):
        from cortex_mem import ValidationError
        app._mock_client.search.side_effect = ValidationError("bad input", field="query")
        resp = client.get("/search?project=/p&query=test")
        assert resp.status_code == 400
        assert "bad input" in resp.get_json()["error"]

    def test_generic_error_handler(self, app, client):
        app._mock_client.get_version.side_effect = RuntimeError("unexpected")
        resp = client.get("/version")
        assert resp.status_code == 500
        assert "internal server error" in resp.get_json()["error"]
