"""Cortex CE Python SDK — Demo HTTP Server (Flask).

Exposes all 26 SDK methods as REST endpoints, mirroring the Go http-server demo.

Usage:
    export CORTEX_BASE_URL=http://127.0.0.1:37777  # optional
    export PORT=8080                                 # optional
    python app.py
"""

import logging
import os
from datetime import datetime, timezone

from flask import Flask, jsonify, request

from cortex_mem import CortexMemClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("demo")

app = Flask(__name__)

CORTEX_BASE_URL = os.environ.get("CORTEX_BASE_URL", "http://127.0.0.1:37777")
PORT = int(os.environ.get("PORT", "8080"))

client = CortexMemClient(base_url=CORTEX_BASE_URL)


# ==================== Helpers ====================


def _error(status: int, message: str):
    return jsonify({"error": message}), status


def _require(fields: dict):
    """Return first missing required field name, or None."""
    for name, value in fields.items():
        if not value:
            return name
    return None


# ==================== Health ====================


@app.get("/health")
def health():
    try:
        client.health_check()
        return jsonify(
            service="python-sdk-http-server",
            status="ok",
            time=datetime.now(timezone.utc).isoformat(),
        )
    except Exception as e:
        return _error(503, f"unhealthy: {e}")


# ==================== Chat ====================


@app.post("/chat")
def chat():
    data = request.get_json(force=True)
    missing = _require({"project": data.get("project"), "message": data.get("message")})
    if missing:
        return _error(400, f"{missing} is required")

    icl_result = None
    try:
        icl_result = client.build_icl_prompt(
            task=data["message"],
            project=data["project"],
            max_chars=data.get("maxChars", 0),
            user_id=data.get("userId", ""),
        )
    except Exception as e:
        logger.warning("ICL prompt failed: %s", e)

    resp = {
        "response": f"Received: {data['message']}",
        "project": data["project"],
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    if icl_result and icl_result.prompt:
        resp["memoryContext"] = icl_result.prompt
        resp["experienceCount"] = icl_result.experience_count
    return jsonify(resp)


# ==================== Search ====================


@app.get("/search")
def search():
    project = request.args.get("project")
    if not project:
        return _error(400, "project is required")
    try:
        limit = int(request.args.get("limit", "0") or "0")
    except ValueError:
        return _error(400, "limit must be an integer")
    try:
        offset = int(request.args.get("offset", "0") or "0")
    except ValueError:
        return _error(400, "offset must be an integer")

    result = client.search(
        project=project,
        query=request.args.get("query", ""),
        type=request.args.get("type", ""),
        concept=request.args.get("concept", ""),
        source=request.args.get("source", ""),
        limit=limit,
        offset=offset,
    )
    return jsonify(
        observations=[vars(o) for o in result.observations],
        strategy=result.strategy,
        fell_back=result.fell_back,
        count=result.count,
    )


# ==================== Version ====================


@app.get("/version")
def version():
    v = client.get_version()
    return jsonify(version=v.version, service=v.service)


# ==================== Experiences ====================


@app.get("/experiences")
def experiences():
    project = request.args.get("project")
    task = request.args.get("task")
    if not project:
        return _error(400, "project is required")
    if not task:
        return _error(400, "task is required")
    try:
        count = int(request.args.get("count", "4") or "4")
    except ValueError:
        return _error(400, "count must be an integer")

    concepts_str = request.args.get("requiredConcepts", "")
    required_concepts = [c.strip() for c in concepts_str.split(",") if c.strip()] if concepts_str else None

    exps = client.retrieve_experiences(
        task=task,
        project=project,
        count=count,
        source=request.args.get("source", ""),
        required_concepts=required_concepts,
        user_id=request.args.get("userId", ""),
    )
    return jsonify(experiences=[vars(e) for e in exps], count=len(exps))


# ==================== ICL Prompt ====================


@app.get("/iclprompt")
def iclprompt():
    project = request.args.get("project")
    task = request.args.get("task")
    if not project:
        return _error(400, "project is required")
    if not task:
        return _error(400, "task is required")
    try:
        max_chars = int(request.args.get("maxChars", "0") or "0")
    except ValueError:
        return _error(400, "maxChars must be an integer")

    result = client.build_icl_prompt(
        task=task,
        project=project,
        max_chars=max_chars,
        user_id=request.args.get("userId", ""),
    )
    return jsonify(prompt=result.prompt, experience_count=result.experience_count, max_chars=result.max_chars)


# ==================== Observations ====================


@app.get("/observations")
def observations_list():
    project = request.args.get("project")
    if not project:
        return _error(400, "project is required")
    try:
        limit = int(request.args.get("limit", "0") or "0")
    except ValueError:
        return _error(400, "limit must be an integer")
    try:
        offset = int(request.args.get("offset", "0") or "0")
    except ValueError:
        return _error(400, "offset must be an integer")

    result = client.list_observations(project=project, limit=limit, offset=offset)
    return jsonify(
        items=[vars(o) for o in result.items],
        has_more=result.has_more,
        total=result.total,
        offset=result.offset,
        limit=result.limit,
    )


@app.post("/observations/batch")
def observations_batch():
    data = request.get_json(force=True)
    ids = data.get("ids", [])
    if not ids:
        return _error(400, "ids is required")
    if len(ids) > 100:
        return _error(400, "batch size exceeds maximum of 100")

    result = client.get_observations_by_ids(ids)
    return jsonify(observations=[vars(o) for o in result.observations], count=result.count)


@app.post("/observations/create")
def observations_create():
    data = request.get_json(force=True)
    missing = _require({
        "project": data.get("project"),
        "session_id": data.get("session_id"),
        "tool_name": data.get("tool_name"),
    })
    if missing:
        return _error(400, f"{missing} is required")

    client.record_observation(
        session_id=data["session_id"],
        project_path=data["project"],
        tool_name=data["tool_name"],
        tool_input=data.get("tool_input"),
        tool_response=data.get("tool_response"),
        source=data.get("source", ""),
        extracted_data=data.get("extractedData"),
    )
    return jsonify(status="recorded")


@app.patch("/observations/<obs_id>")
def observations_update(obs_id: str):
    data = request.get_json(force=True)
    kwargs = {}
    if "title" in data:
        kwargs["title"] = data["title"]
    if "subtitle" in data:
        kwargs["subtitle"] = data["subtitle"]
    if "content" in data:
        kwargs["content"] = data["content"]
    if "facts" in data:
        kwargs["facts"] = data["facts"]
    if "concepts" in data:
        kwargs["concepts"] = data["concepts"]
    if "source" in data:
        kwargs["source"] = data["source"]
    if "extractedData" in data:
        kwargs["extracted_data"] = data["extractedData"]

    client.update_observation(obs_id, **kwargs)
    return jsonify(status="updated")


@app.delete("/observations/<obs_id>")
def observations_delete(obs_id: str):
    client.delete_observation(obs_id)
    return "", 204


# ==================== Projects / Stats / Modes / Settings ====================


@app.get("/projects")
def projects():
    result = client.get_projects()
    return jsonify(projects=result.projects)


@app.get("/stats")
def stats():
    result = client.get_stats(project=request.args.get("project", ""))
    return jsonify(
        worker={"is_processing": result.worker.is_processing, "queue_depth": result.worker.queue_depth},
        database={
            "total_observations": result.database.total_observations,
            "total_summaries": result.database.total_summaries,
            "total_sessions": result.database.total_sessions,
            "total_projects": result.database.total_projects,
        },
    )


@app.get("/modes")
def modes():
    result = client.get_modes()
    return jsonify(
        id=result.id,
        name=result.name,
        description=result.description,
        version=result.version,
        observation_types=result.observation_types,
        observation_concepts=result.observation_concepts,
    )


@app.get("/settings")
def settings():
    return jsonify(client.get_settings())


# ==================== Quality ====================


@app.get("/quality")
def quality():
    project = request.args.get("project")
    if not project:
        return _error(400, "project is required")
    result = client.get_quality_distribution(project)
    return jsonify(project=result.project, high=result.high, medium=result.medium, low=result.low, unknown=result.unknown)


# ==================== Extraction ====================


@app.get("/extraction/latest")
def extraction_latest():
    template = request.args.get("template")
    project = request.args.get("project")
    if not template:
        return _error(400, "template is required")
    if not project:
        return _error(400, "project is required")
    result = client.get_latest_extraction(project, template, user_id=request.args.get("userId", ""))
    return jsonify(
        status=result.status,
        template=result.template,
        message=result.message,
        extractedData=result.extracted_data,
        sessionId=result.session_id,
        createdAt=result.created_at,
        observationId=result.observation_id,
    )


@app.get("/extraction/history")
def extraction_history():
    template = request.args.get("template")
    project = request.args.get("project")
    if not template:
        return _error(400, "template is required")
    if not project:
        return _error(400, "project is required")
    try:
        limit = int(request.args.get("limit", "0") or "0")
    except ValueError:
        return _error(400, "limit must be an integer")

    results = client.get_extraction_history(project, template, user_id=request.args.get("userId", ""), limit=limit)
    return jsonify([
        {
            "status": r.status,
            "template": r.template,
            "message": r.message,
            "extractedData": r.extracted_data,
            "sessionId": r.session_id,
            "createdAt": r.created_at,
            "observationId": r.observation_id,
        }
        for r in results
    ])


@app.post("/extraction/run")
def extraction_run():
    project = request.args.get("projectPath")
    if not project:
        return _error(400, "projectPath is required")
    client.trigger_extraction(project)
    return jsonify(status="extraction triggered")


# ==================== Refine / Feedback ====================


@app.post("/refine")
def refine():
    project = request.args.get("project")
    if not project:
        return _error(400, "project is required")
    client.trigger_refinement(project)
    return jsonify(status="refined")


@app.post("/feedback")
def feedback():
    data = request.get_json(force=True)
    missing = _require({
        "observation_id": data.get("observation_id"),
        "feedback_type": data.get("feedback_type"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    client.submit_feedback(data["observation_id"], data["feedback_type"], data.get("comment", ""))
    return jsonify(status="submitted")


# ==================== Session ====================


@app.patch("/session/user")
def session_user():
    data = request.get_json(force=True)
    missing = _require({
        "session_id": data.get("session_id"),
        "user_id": data.get("user_id"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    result = client.update_session_user_id(data["session_id"], data["user_id"])
    return jsonify(result)


# ==================== Ingest ====================


@app.post("/ingest/prompt")
def ingest_prompt():
    data = request.get_json(force=True)
    missing = _require({
        "project": data.get("project"),
        "session_id": data.get("session_id"),
        "prompt": data.get("prompt"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    client.record_user_prompt(
        session_id=data["session_id"],
        prompt_text=data["prompt"],
        project_path=data["project"],
    )
    return jsonify(status="recorded")


@app.post("/ingest/session-end")
def ingest_session_end():
    data = request.get_json(force=True)
    missing = _require({
        "project": data.get("project"),
        "session_id": data.get("session_id"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    client.record_session_end(session_id=data["session_id"], project_path=data["project"])
    return jsonify(status="ended")


# ==================== Main ====================

if __name__ == "__main__":
    print(f"🚀 Python SDK HTTP server starting on :{PORT}")
    print(f"   Backend: {CORTEX_BASE_URL}")
    print()
    print("Endpoints:")
    print("  GET    /health              - Health check")
    print("  POST   /chat                - Chat with memory")
    print("  GET    /search              - Search observations")
    print("  GET    /version             - Backend version")
    print("  GET    /experiences         - Retrieve experiences")
    print("  GET    /iclprompt           - Build ICL prompt")
    print("  GET    /observations        - List observations")
    print("  POST   /observations/batch  - Batch get observations by IDs")
    print("  GET    /projects            - Get projects")
    print("  GET    /stats               - Get stats")
    print("  GET    /modes               - Get modes")
    print("  GET    /settings            - Get settings")
    print("  GET    /quality             - Quality distribution")
    print("  GET    /extraction/latest   - Latest extraction result")
    print("  GET    /extraction/history  - Extraction history")
    print("  POST   /extraction/run      - Trigger extraction")
    print("  POST   /refine              - Trigger memory refinement")
    print("  POST   /feedback            - Submit observation feedback")
    print("  PATCH  /session/user        - Update session user ID")
    print("  PATCH  /observations/<id>   - Update observation")
    print("  DELETE /observations/<id>   - Delete observation")
    print("  POST   /observations/create - Record observation")
    print("  POST   /ingest/prompt       - Ingest user prompt")
    print("  POST   /ingest/session-end  - Ingest session end")

    app.run(host="0.0.0.0", port=PORT, debug=False)
