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
)


class TestDTOFromWire:
    def test_experience_from_wire(self):
        data = {
            "id": "e1",
            "task": "t1",
            "strategy": "s1",
            "outcome": "o1",
            "reuseCondition": "r1",
            "qualityScore": 0.85,
            "createdAt": "2026-01-01",
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

    def test_observation_from_wire(self):
        data = {
            "id": "o1",
            "sessionId": "s1",
            "projectPath": "/p",
            "type": "feature",
            "title": "Test",
            "content": "Content",
            "facts": ["f1"],
            "concepts": ["c1"],
            "qualityScore": 0.9,
            "source": "manual",
            "extractedData": {"key": "val"},
            "prompt_number": 5,
            "createdAt": "2026-01-01",
            "created_at_epoch": 1710000000,
        }
        obs = Observation.from_wire(data)
        assert obs.id == "o1"
        assert obs.session_id == "s1"
        assert obs.project_path == "/p"
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
            "observations": [{"id": "o1", "content": "test"}],
            "strategy": "hybrid",
            "fell_back": False,
            "count": 1,
        }
        sr = SearchResult.from_wire(data)
        assert sr.count == 1
        assert sr.strategy == "hybrid"
        assert len(sr.observations) == 1

    def test_observations_response_from_wire(self):
        data = {
            "items": [{"id": "o1", "content": "test"}],
            "hasMore": True,
            "total": 100,
            "offset": 0,
            "limit": 20,
        }
        resp = ObservationsResponse.from_wire(data)
        assert resp.has_more is True
        assert resp.total == 100

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
            "observationTypes": ["feature", "bug"],
            "observationConcepts": ["testing"],
        }
        mr = ModesResponse.from_wire(data)
        assert mr.name == "default"
        assert len(mr.observation_types) == 2

    def test_session_start_response(self):
        data = {"session_db_id": "db-1", "session_id": "s-1", "prompt_number": 0}
        sr = SessionStartResponse(**data)
        assert sr.session_id == "s-1"
