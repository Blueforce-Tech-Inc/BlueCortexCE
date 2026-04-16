"""Cortex CE Python SDK — Demo HTTP Server (Flask).

Exposes all 25 SDK API methods as REST endpoints, mirroring the Go http-server demo.

Usage:
    export CORTEX_BASE_URL=http://127.0.0.1:37777  # optional
    export PORT=8080                                 # optional
    python app.py
"""

import atexit
import logging
import os
from datetime import datetime, timezone

from flask import Flask, jsonify, request

from cortex_mem import APIError, CortexError, CortexMemClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("demo")

app = Flask(__name__)

CORTEX_BASE_URL = os.environ.get("CORTEX_BASE_URL", "http://127.0.0.1:37777")
PORT = int(os.environ.get("PORT", "8080"))

# Request body size limit: 1 MB (matches Go http-server demo)
MAX_CONTENT_LENGTH = int(os.environ.get("MAX_CONTENT_LENGTH", str(1 << 20)))
app.config["MAX_CONTENT_LENGTH"] = MAX_CONTENT_LENGTH

client = CortexMemClient(base_url=CORTEX_BASE_URL)

# Clean up HTTP session on shutdown (prevents resource leak in long-running server)
atexit.register(client.close)


# ==================== Error Handlers ====================


@app.errorhandler(APIError)
def handle_api_error(exc: APIError):
    """Return structured JSON for SDK API errors."""
    status = exc.status_code if 400 <= exc.status_code < 600 else 502
    logger.warning("API error %d: %s", exc.status_code, exc.message)
    return jsonify(error=exc.message), status


@app.errorhandler(CortexError)
def handle_cortex_error(exc: CortexError):
    """Return structured JSON for SDK logic errors (validation, closed client, etc.)."""
    logger.warning("SDK error: %s", exc)
    return jsonify(error=str(exc)), 400


@app.errorhandler(Exception)
def handle_generic_error(exc: Exception):
    """Catch-all: return JSON instead of Flask's default HTML 500 page."""
    logger.error("Unhandled exception: %s", exc, exc_info=True)
    return jsonify(error="internal server error"), 500


@app.errorhandler(413)
def handle_payload_too_large(exc):
    """Return JSON for 413 Request Entity Too Large."""
    return jsonify(error=f"request body too large (max {MAX_CONTENT_LENGTH} bytes)"), 413


# ==================== Helpers ====================


def _error(status: int, message: str):
    return jsonify({"error": message}), status


def _require(fields: dict):
    """Return first missing required field name, or None.

    Checks for None and empty strings only. Does NOT reject falsy values
    like 0, False, or empty lists — callers must handle those separately.
    """
    for name, value in fields.items():
        if value is None or (isinstance(value, str) and not value.strip()):
            return name
    return None


def _parse_json():
    """Parse JSON request body with Content-Type validation.

    Returns parsed data dict on success, or error response tuple on failure.
    Can be used directly in return: data = _parse_json(); if isinstance(data, tuple): return data
    """
    if not request.is_json:
        return _error(400, "Content-Type must be application/json")
    data = request.get_json(silent=True)
    if data is None:
        return _error(400, "invalid or empty JSON body")
    return data


def _parse_int_param(key: str, default: int = 0) -> int:
    """Parse an optional integer query param.

    Returns default if param is missing.
    Raises ValueError if param is present but not a valid integer.
    """
    raw = request.args.get(key)
    if raw is None:
        return default
    try:
        return int(raw)
    except (ValueError, TypeError):
        raise ValueError(f"{key} must be an integer")


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
    data = _parse_json()
    if isinstance(data, tuple):
        return data
    missing = _require({"project": data.get("project"), "message": data.get("message")})
    if missing:
        return _error(400, f"{missing} is required")

    icl_result = None
    user_id = data.get("userId")
    if user_id is not None and user_id.strip() == "":
        user_id = None  # treat empty string as "not provided" (matches Java/Go behavior)
    try:
        icl_result = client.build_icl_prompt(
            task=data["message"],
            project=data["project"],
            max_chars=data.get("maxChars", 0),
            user_id=user_id,
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
        limit = _parse_int_param("limit")
        offset = _parse_int_param("offset")
    except ValueError as e:
        return _error(400, str(e))
    if limit < 0 or limit > 100:
        return _error(400, "limit must be between 0 and 100")
    if offset < 0:
        return _error(400, "offset must be non-negative")
    # Note: limit=0 and offset=0 are accepted — SDK omits them from the request,
    # letting the backend apply its defaults (consistent with Python SDK semantics).

    result = client.search(
        project=project,
        query=request.args.get("query", ""),
        type_=request.args.get("type", ""),
        concept=request.args.get("concept") or "",
        source=request.args.get("source") or None,
        limit=limit,
        offset=offset,
        order_by=request.args.get("orderBy") or "",
    )
    return jsonify(
        observations=[o.to_dict() for o in result.observations],
        strategy=result.strategy,
        fell_back=result.fell_back,
        count=result.count,
    )


# ==================== Version ====================


@app.get("/version")
def version():
    v = client.get_version()
    resp = {"version": v.version, "service": v.service}
    if v.java:
        resp["java"] = v.java
    if v.spring_boot:
        resp["spring_boot"] = v.spring_boot
    return jsonify(resp)


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
        count = _parse_int_param("count", default=4)
    except ValueError as e:
        return _error(400, str(e))
    if count < 0 or count > 100:
        return _error(400, "count must be between 0 and 100")
    # count=0 means "use SDK default" (consistent with Java demo)
    if count == 0:
        count = 4

    concepts_str = request.args.get("requiredConcepts", "")
    required_concepts = [c.strip() for c in concepts_str.split(",") if c.strip()] if concepts_str else None

    exps = client.retrieve_experiences(
        task=task,
        project=project,
        count=count,
        source=request.args.get("source") or None,
        required_concepts=required_concepts,
        user_id=request.args.get("userId") or None,
    )
    return jsonify(experiences=[e.to_dict() for e in exps], count=len(exps))


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
        max_chars = _parse_int_param("maxChars")
    except ValueError as e:
        return _error(400, str(e))

    result = client.build_icl_prompt(
        task=task,
        project=project,
        max_chars=max_chars,
        user_id=request.args.get("userId", ""),
    )
    # Use camelCase to match Go/JS demo response format
    return jsonify(prompt=result.prompt, experienceCount=result.experience_count, maxChars=result.max_chars)


# ==================== Observations ====================


@app.get("/observations")
def observations_list():
    # project is optional — empty/missing means all projects (consistent with Go/Java demos)
    project = request.args.get("project") or ""
    try:
        limit = _parse_int_param("limit")
        offset = _parse_int_param("offset")
    except ValueError as e:
        return _error(400, str(e))
    if limit < 0 or limit > 100:
        return _error(400, "limit must be between 0 and 100")
    if offset < 0:
        return _error(400, "offset must be non-negative")
    # Note: limit=0 and offset=0 are accepted — SDK omits them from the request,
    # letting the backend apply its defaults (consistent with Python SDK semantics).

    result = client.list_observations(project=project, limit=limit, offset=offset)
    return jsonify(
        items=[o.to_dict() for o in result.items],
        has_more=result.has_more,
        total=result.total,
        offset=result.offset,
        limit=result.limit,
    )


@app.get("/observations/<observation_id>")
def get_observation(observation_id: str):
    obs = client.get_observation(observation_id)
    if obs is None:
        return _error(404, f"observation {observation_id} not found")
    return jsonify(obs.to_dict())


@app.post("/observations/batch")
def observations_batch():
    data = _parse_json()
    if isinstance(data, tuple):
        return data
    ids = data.get("ids", [])
    if not ids:
        return _error(400, "ids is required")
    if len(ids) > 100:
        return _error(400, "batch size exceeds maximum of 100")
    for i, id_ in enumerate(ids):
        if not id_ or not str(id_).strip():
            return _error(400, f"ids[{i}] is empty")

    result = client.get_observations_by_ids(ids)
    return jsonify(observations=[o.to_dict() for o in result.observations], count=result.count)


@app.post("/observations/create")
def observations_create():
    data = _parse_json()
    if isinstance(data, tuple):
        return data
    missing = _require({
        "project": data.get("project"),
        "session_id": data.get("session_id"),
        "tool_name": data.get("tool_name"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    # Validate extractedData type if provided (must be dict, not string or list)
    if "extractedData" in data and not isinstance(data["extractedData"], dict):
        return _error(400, "extractedData must be a JSON object")

    client.record_observation(
        session_id=data["session_id"],
        project_path=data["project"],
        tool_name=data["tool_name"],
        tool_input=data.get("tool_input"),
        tool_response=data.get("tool_response"),
        prompt_number=data.get("prompt_number", 0),
        source=data.get("source", ""),
        extracted_data=data.get("extractedData"),
    )
    return jsonify(status="recorded")


@app.patch("/observations/<obs_id>")
def observations_update(obs_id: str):
    if not obs_id or not obs_id.strip():
        return _error(400, "observation id is required")
    data = _parse_json()
    if isinstance(data, tuple):
        return data
    # Validate extractedData type if provided (must be dict, not string or list)
    if "extractedData" in data and not isinstance(data["extractedData"], dict):
        return _error(400, "extractedData must be a JSON object")
    kwargs = {}
    for key in ("title", "subtitle", "content", "narrative", "facts", "concepts", "source"):
        if key in data:
            kwargs[key] = data[key]
    if "extractedData" in data:
        kwargs["extracted_data"] = data["extractedData"]

    if not kwargs:
        return _error(400, "at least one field must be provided for update")

    client.update_observation(obs_id, **kwargs)
    return jsonify(status="updated")


@app.delete("/observations/<obs_id>")
def observations_delete(obs_id: str):
    if not obs_id or not obs_id.strip():
        return _error(400, "observation id is required")
    client.delete_observation(obs_id)
    return "", 204


# ==================== Projects / Stats / Modes / Settings ====================


@app.get("/projects")
def projects():
    result = client.get_projects()
    return jsonify(projects=result.projects)


@app.get("/stats")
def stats():
    result = client.get_stats(project_path=request.args.get("project", ""))
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
        observation_types=[t.to_dict() for t in result.observation_types],
        observation_concepts=[c.to_dict() for c in result.observation_concepts],
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
    return jsonify(project=result.project, high=result.high, medium=result.medium,
                   low=result.low, unknown=result.unknown, total=result.total)


# ==================== Extraction ====================


@app.get("/extraction/latest")
def extraction_latest():
    """Get latest extraction result.

    Note: Backend endpoint is /api/extraction/{templateName}/latest (path param).
    This demo uses /extraction/latest?template=... (query param) for simplicity.
    """
    template = request.args.get("template")
    project = request.args.get("project")
    if not template:
        return _error(400, "template is required")
    if not project:
        return _error(400, "project is required")
    result = client.get_latest_extraction(project, template, user_id=request.args.get("userId", ""))
    return jsonify(result.to_dict())


@app.get("/extraction/history")
def extraction_history():
    template = request.args.get("template")
    project = request.args.get("project")
    if not template:
        return _error(400, "template is required")
    if not project:
        return _error(400, "project is required")
    try:
        limit = _parse_int_param("limit")
    except ValueError as e:
        return _error(400, str(e))

    results = client.get_extraction_history(project, template, user_id=request.args.get("userId", ""), limit=limit)
    return jsonify([r.to_dict() for r in results])


@app.post("/extraction/run")
def extraction_run():
    project = request.args.get("project")
    if not project:
        return _error(400, "project is required")
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
    data = _parse_json()
    if isinstance(data, tuple):
        return data
    missing = _require({
        "observationId": data.get("observationId"),
        "feedbackType": data.get("feedbackType"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    client.submit_feedback(data["observationId"], data["feedbackType"], data.get("comment", ""))
    return jsonify(status="submitted")


# ==================== Session ====================


@app.post("/session/start")
def session_start():
    data = _parse_json()
    if isinstance(data, tuple):
        return data
    missing = _require({
        "session_id": data.get("session_id"),
        "project": data.get("project"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    result = client.start_session(
        session_id=data["session_id"],
        project_path=data["project"],
        user_id=data.get("user_id"),
    )
    return jsonify(
        session_db_id=result.session_db_id,
        session_id=result.session_id,
        context=result.context,
        updateFiles=result.update_files,
        prompt_number=result.prompt_number,
    )


@app.patch("/session/user")
def session_user():
    data = _parse_json()
    if isinstance(data, tuple):
        return data
    missing = _require({
        "session_id": data.get("session_id"),
        "user_id": data.get("user_id"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    result = client.update_session_user_id(data["session_id"], data["user_id"])
    return jsonify(status=result.status, session_id=result.session_id, user_id=result.user_id)


# ==================== Ingest ====================


@app.post("/ingest/prompt")
def ingest_prompt():
    data = _parse_json()
    if isinstance(data, tuple):
        return data
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
        prompt_number=data.get("prompt_number", 0),
    )
    return jsonify(status="recorded")


@app.post("/ingest/session-end")
def ingest_session_end():
    data = _parse_json()
    if isinstance(data, tuple):
        return data
    missing = _require({
        "project": data.get("project"),
        "session_id": data.get("session_id"),
    })
    if missing:
        return _error(400, f"{missing} is required")
    client.record_session_end(
        session_id=data["session_id"],
        project_path=data["project"],
        last_assistant_message=data.get("last_assistant_message"),
    )
    return jsonify(status="ended")


# ==================== Main ====================

if __name__ == "__main__":
    print(f"🚀 Python SDK HTTP server starting on :{PORT}")
    print(f"   Backend: {CORTEX_BASE_URL}")
    print(f"   Max request body: {MAX_CONTENT_LENGTH} bytes")
    print()
    print("Endpoints:")
    print("  GET    /health              - Health check")
    print("  POST   /chat                - Chat with memory")
    print("  GET    /search              - Search observations")
    print("  GET    /version             - Backend version")
    print("  GET    /experiences         - Retrieve experiences")
    print("  GET    /iclprompt           - Build ICL prompt")
    print("  GET    /observations        - List observations")
    print("  GET    /observations/{id}   - Get observation by ID")
    print("  POST   /observations/batch  - Batch get observations by IDs")
    print("  POST   /observations/create - Record observation")
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
    print("  POST   /session/start       - Start/resume session")
    print("  PATCH  /session/user        - Update session user ID")
    print("  PATCH  /observations/<id>   - Update observation")
    print("  DELETE /observations/<id>   - Delete observation")
    print("  POST   /ingest/prompt       - Ingest user prompt")
    print("  POST   /ingest/session-end  - Ingest session end")

    app.run(host="0.0.0.0", port=PORT, debug=False)
