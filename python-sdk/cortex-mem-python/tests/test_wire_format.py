# coding: utf-8
"""Pins the COMPLETE key set of every outbound request body the Python SDK sends.

Why this file exists
--------------------

Every other wire-format test in this package asserts that *some* key is present
with a given value (``assert "userId" in body``), or that a handful of specific
wrong keys are absent. None of them asserts **which keys are all there**. A
measurement on 2026-10-22 put a number on that: adding one extra
always-present field to each of the nine body-carrying endpoints in this SDK
left the whole suite GREEN in 9 cases out of 9.

The gap this closes is the "new field" direction. The backend accepts unknown
JSON properties and ignores them, so a field added to a request body changes
the wire silently: the backend would answer 200 and the SDK would carry a
payload the server never reads.

The expectation sets below are written out **by hand** on purpose. Deriving
them from the SDK's own body-building code would make the assertion
tautological, and a tautological assertion is worse than no assertion at all.

The three families of check here are:

1. the complete key set of a fully populated request (what the backend receives
   when the caller supplies everything),
2. the complete key set of a minimal request (what the backend receives when
   the caller supplies only the required fields — this SDK drops empty
   optional fields, so the key set is payload-dependent and both ends need
   pinning),
3. every key the SDK sends is a key the backend actually declares (read from
   the backend's own request records), which is the one direction the first two
   families cannot see: the backend contract changing under a stale SDK.

This is the fourth SDK in the family to have this gap — the JS SDK in round
399, the Java SDK in round 402 and the Go SDK in round 403. The Go SDK's fix
lives in ``wire_format_test.go``; the Java SDK's in ``CortexMemWireFormatTest``.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from urllib.parse import urlsplit

import responses

from cortex_mem import CortexMemClient, ObservationUpdate

BASE = "http://localhost:37777"
SESS_ID = "sess-1"
OBS_ID = "11111111-1111-1111-1111-111111111111"


class WireCase:
    """One body-carrying endpoint of this SDK."""

    def __init__(self, name, method, path, full, minimal, full_keys, minimal_keys,
                 response_json=None, status=200, contract=None):
        self.name = name
        self.method = method
        self.path = path
        self.full = full
        self.minimal = minimal
        self.full_keys = sorted(full_keys)
        self.minimal_keys = sorted(minimal_keys)
        self.response_json = {} if response_json is None else response_json
        self.status = status
        # The endpoint's identity in the backend's own contract tables. It
        # differs from `path` where the SDK's request path carries a concrete
        # id while the contract names it as a variable.
        self.contract = contract

    def __repr__(self) -> str:  # pragma: no cover - debugging aid
        return f"<WireCase {self.name}>"


CASES = [
    WireCase(
        name="POST /api/session/start",
        method=responses.POST,
        path="/api/session/start",
        full=lambda c: c.start_session(SESS_ID, "/proj", user_id="user-1", platform_source="codex"),
        minimal=lambda c: c.start_session(SESS_ID, "/proj"),
        full_keys=["session_id", "project_path", "user_id", "platform_source"],
        minimal_keys=["session_id", "project_path"],
        response_json={"session_db_id": "db-1", "session_id": SESS_ID, "updateFiles": [],
                       "prompt_number": 0},
        contract="SessionStartRequest",
    ),
    WireCase(
        name="POST /api/ingest/tool-use",
        method=responses.POST,
        path="/api/ingest/tool-use",
        full=lambda c: c.record_observation(
            SESS_ID, "/proj", "Edit",
            tool_input={"file": "main.py"},
            tool_response={"ok": True},
            prompt_number=7,
            source="manual",
            extracted_data={"k": "v"},
            platform_source="codex",
        ),
        minimal=lambda c: c.record_observation(SESS_ID, "/proj", "Edit"),
        full_keys=["session_id", "cwd", "tool_name", "tool_input", "tool_response",
                   "prompt_number", "source", "extractedData", "platform_source"],
        minimal_keys=["session_id", "cwd", "tool_name"],
        status=204,
        contract="ToolUseRequest",
    ),
    WireCase(
        name="POST /api/ingest/session-end",
        method=responses.POST,
        path="/api/ingest/session-end",
        full=lambda c: c.record_session_end(SESS_ID, "/proj", last_assistant_message="done", platform_source="codex"),
        minimal=lambda c: c.record_session_end(SESS_ID, "/proj"),
        full_keys=["session_id", "cwd", "last_assistant_message", "platform_source"],
        minimal_keys=["session_id", "cwd"],
        status=204,
        contract="SessionEndRequest",
    ),
    WireCase(
        name="POST /api/ingest/user-prompt",
        method=responses.POST,
        path="/api/ingest/user-prompt",
        full=lambda c: c.record_user_prompt(SESS_ID, "hello", "/proj", prompt_number=3, platform_source="codex"),
        minimal=lambda c: c.record_user_prompt(SESS_ID, "hello", "/proj"),
        full_keys=["session_id", "prompt_text", "cwd", "prompt_number", "platform_source"],
        minimal_keys=["session_id", "prompt_text", "cwd"],
        status=204,
        contract="UserPromptRequest",
    ),
    WireCase(
        name="POST /api/memory/experiences",
        method=responses.POST,
        path="/api/memory/experiences",
        full=lambda c: c.retrieve_experiences(
            "task", "/proj", count=4, source="manual",
            required_concepts=["a"], user_id="user-1",
        ),
        minimal=lambda c: c.retrieve_experiences("task"),
        full_keys=["task", "project", "count", "source", "requiredConcepts", "userId"],
        minimal_keys=["task"],
        response_json=[],
        contract="ExperienceRequest",
    ),
    WireCase(
        name="POST /api/memory/icl-prompt",
        method=responses.POST,
        path="/api/memory/icl-prompt",
        full=lambda c: c.build_icl_prompt("task", "/proj", max_chars=4000, user_id="user-1"),
        minimal=lambda c: c.build_icl_prompt("task"),
        full_keys=["task", "project", "maxChars", "userId"],
        minimal_keys=["task"],
        contract="ICLPromptRequest",
    ),
    WireCase(
        name="POST /api/observations/batch",
        method=responses.POST,
        path="/api/observations/batch",
        full=lambda c: c.get_observations_by_ids([OBS_ID]),
        minimal=lambda c: c.get_observations_by_ids([OBS_ID]),
        full_keys=["ids"],
        minimal_keys=["ids"],
        response_json={"observations": [], "count": 0},
        contract="BatchObservationsRequest",
    ),
    WireCase(
        name="POST /api/memory/feedback",
        method=responses.POST,
        path="/api/memory/feedback",
        full=lambda c: c.submit_feedback("obs-1", "SUCCESS", "good"),
        minimal=lambda c: c.submit_feedback("obs-1", "SUCCESS"),
        full_keys=["observationId", "feedbackType", "comment"],
        minimal_keys=["observationId", "feedbackType"],
        status=204,
        contract="FeedbackRequest",
    ),
    WireCase(
        # Built inline in the client, not from a dataclass.
        name="PATCH /api/session/user",
        method=responses.PATCH,
        path="/api/session/user",
        full=lambda c: c.update_session_user_id(SESS_ID, "user-1"),
        minimal=lambda c: c.update_session_user_id(SESS_ID, "user-1"),
        full_keys=["user_id"],
        minimal_keys=["user_id"],
        response_json={"status": "ok", "sessionId": SESS_ID, "userId": "user-1"},
        contract="SessionUserUpdateRequest",
    ),
    WireCase(
        # This SDK has no `content` wire key: `content` and `narrative` are
        # aliases for the same backend field and both map to "narrative"
        # (see ObservationUpdate._WIRE_FIELDS). The Go SDK sends "content" in
        # one of its two variants; the backend accepts either.
        name="PATCH /api/memory/observations/{id}",
        method=responses.PATCH,
        path=f"/api/memory/observations/{OBS_ID}",
        full=lambda c: c.update_observation(
            OBS_ID,
            ObservationUpdate(
                title="t",
                subtitle="s",
                content="c",
                facts=["f"],
                concepts=["c1"],
                source="manual",
                extracted_data={"k": "v"},
            ),
        ),
        minimal=lambda c: c.update_observation(OBS_ID, title="t"),
        full_keys=["title", "subtitle", "narrative", "facts", "concepts", "source",
                   "extractedData"],
        minimal_keys=["title"],
        status=204,
        contract="PATCH /api/memory/observations/{id}",
    ),
]


# ------------------------------------------------------------------
# Family 1 + 2: the complete key set, fully populated and minimal.
# ------------------------------------------------------------------


def capture_body(case: WireCase, call) -> dict:
    """Run one client call against a mock server and return the recorded body.

    The mock is registered for the case's own method and path, and the number
    of recorded calls is asserted to be exactly one: the capture methods are
    fire-and-forget and **swallow every error**, so a request that never left
    the client would otherwise look like a request that carried no body.
    """
    with responses.RequestsMock() as rsps:
        if case.status == 204:
            # A 204 carries no body — `responses` would otherwise answer with
            # a JSON payload and the client would fail while reading it.
            rsps.add(case.method, f"{BASE}{case.path}", body="", status=204)
        else:
            rsps.add(case.method, f"{BASE}{case.path}", json=case.response_json,
                     status=case.status)
        client = CortexMemClient(base_url=BASE)
        try:
            call(client)
        finally:
            client.close()

        if len(rsps.calls) != 1:
            raise AssertionError(
                f"{case.name}: expected exactly 1 request, got {len(rsps.calls)}"
            )
        request = rsps.calls[0].request
        assert request.method == case.method, (
            f"{case.name}: method = {request.method}, want {case.method}"
        )
        assert urlsplit(request.url).path == case.path, (
            f"{case.name}: path = {urlsplit(request.url).path}, want {case.path}"
        )
        body = request.body
        if isinstance(body, (bytes, bytearray)):
            body = body.decode("utf-8")
        return json.loads(body)


def key_set_diff(expected: list[str], actual: list[str]) -> str:
    """Render the difference between the expected and the actual key set."""
    expected_set = set(expected)
    actual_set = set(actual)
    missing = sorted(expected_set - actual_set)
    unexpected = sorted(actual_set - expected_set)
    return f"missing={missing} unexpected={unexpected}"


def assert_key_set(case_name: str, expected: list[str], body: dict) -> str | None:
    """Check that the recorded body carries exactly the expected key set.

    Returns None when the key sets match, and a one-line description of the
    difference when they do not. The caller collects the differences instead
    of failing on the first one: a systematic break (every endpoint gained a
    key) would otherwise be reported one endpoint at a time.
    """
    got = sorted(body.keys())
    if got == sorted(expected):
        return None
    return (
        f"{case_name}: complete key set mismatch\n"
        f"  want: {sorted(expected)}\n"
        f"  got:  {got}\n"
        f"  {key_set_diff(expected, got)}"
    )


def test_wire_format_complete_key_set():
    """Every body-carrying endpoint sends exactly the expected keys, fully populated."""
    problems = []
    for case in CASES:
        body = capture_body(case, case.full)
        diff = assert_key_set(case.name, case.full_keys, body)
        if diff:
            problems.append(diff)
    assert not problems, "\n\n".join(problems)


def test_wire_format_minimal_key_set():
    """Every body-carrying endpoint sends exactly the expected keys, minimally populated.

    This SDK drops empty optional fields (an empty ``source``, a zero
    ``prompt_number``, a non-positive ``count``), so the key set depends on the
    payload. The backend ignores unknown properties, so a field that starts
    being omitted — or stops being omitted — changes the wire with nothing
    turning red.
    """
    problems = []
    for case in CASES:
        body = capture_body(case, case.minimal)
        diff = assert_key_set(case.name, case.minimal_keys, body)
        if diff:
            problems.append(diff)
    assert not problems, "\n\n".join(problems)


# ------------------------------------------------------------------
# Family 3: the backend contract, read from the backend's own source.
# ------------------------------------------------------------------


def backend_root() -> Path:
    """Locate the backend sources from this test file's directory.

    The file lives at <repo>/python-sdk/cortex-mem-python/tests/, so the repo
    root is three levels up.
    """
    root = Path(__file__).resolve().parents[3] / "backend"
    if not root.is_dir():
        raise AssertionError(f"cannot find the backend directory at {root}")
    return root


def record_keys(source: str, record: str) -> list[str]:
    """Extract the @JsonProperty names of one request record."""
    idx = source.find(f"record {record}(")
    if idx < 0:
        return []
    end = source.find(") {}", idx)
    if end < 0:
        return []
    body = source[idx:end]
    seen: set[str] = set()
    keys: list[str] = []
    for name in re.findall(r'@JsonProperty\("([^"]+)"\)', body):
        if name not in seen:
            seen.add(name)
            keys.append(name)
    return sorted(keys)


def backend_update_observation_keys(source: str) -> list[str]:
    """Read the keys the PATCH-observation handler acts on.

    The handler takes a raw Map, so the set of keys it honours is the set of
    names it asks for.
    """
    start = source.find("public ResponseEntity<Map<String, Object>> updateObservation(")
    if start < 0:
        return []
    end = source.find("\n    }", start)
    if end < 0:
        end = len(source)
    block = source[start:end]
    seen: set[str] = set()
    keys: list[str] = []
    for name in re.findall(r'body\.containsKey\("([^"]+)"\)', block):
        if name not in seen:
            seen.add(name)
            keys.append(name)
    return sorted(keys)


def test_wire_format_sent_keys_are_declared_by_the_backend():
    """Every key this SDK sends is a key the backend declares.

    The two families above can only see changes made in THIS repository's SDK
    code. They cannot see the backend's contract changing underneath a stale
    SDK: the backend would accept the old key set, ignore the new field, and
    the SDK would keep shipping a payload the server no longer reads.

    The expectation is a subset relation, not equality: the backend accepts
    more keys than the SDK sends (``platform_source``, for instance, which no
    SDK sends). What must never happen is the SDK sending a key the backend
    does not declare — the backend ignores unknown properties silently, so such
    a key is a field that is written and never read.
    """
    root = backend_root()
    api_requests_path = (root / "src" / "main" / "java" / "com" / "ablueforce"
                         / "cortexce" / "dto" / "ApiRequests.java")
    api_requests = api_requests_path.read_text(encoding="utf-8")

    memory_controller_path = (root / "src" / "main" / "java" / "com" / "ablueforce"
                              / "cortexce" / "controller" / "MemoryController.java")
    memory_controller = memory_controller_path.read_text(encoding="utf-8")

    for case in CASES:
        if case.contract == "PATCH /api/memory/observations/{id}":
            declared = backend_update_observation_keys(memory_controller)
        else:
            declared = record_keys(api_requests, case.contract)
        if not declared:
            raise AssertionError(
                f"{case.name}: cannot read the backend's declared keys for "
                f"{case.contract}"
            )
        declared_set = set(declared)
        for key in case.full_keys:
            assert key in declared_set, (
                f"{case.name}: the SDK sends {key!r} but the backend does not "
                f"declare it\n  backend declares: {declared}"
            )


# ------------------------------------------------------------------
# Self-test: the key-set assertion has to be able to fail.
# ------------------------------------------------------------------


def test_wire_format_assertion_is_falsifiable():
    """Prove the key-set comparison can fail, on real captured data.

    A checker that reports "no difference" for every input is the most
    expensive kind of failure: it exits clean and looks like a pass. This test
    takes a body that the suite really captured, perturbs it the two ways the
    SDK could break (an extra key, a renamed key), and asserts that the
    comparison reports each one.
    """
    case = next(c for c in CASES if c.name == "POST /api/memory/feedback")
    body = capture_body(case, case.full)

    # The unmodified body matches its expectation.
    assert assert_key_set(case.name, case.full_keys, body) is None

    # An extra key is detected.
    perturbed = dict(body)
    perturbed["surpriseField"] = 1
    diff = assert_key_set(case.name, case.full_keys, perturbed)
    assert diff is not None, "the key-set comparison did not report an extra key"
    assert "unexpected=['surpriseField']" in diff, diff

    # A renamed key is detected.
    perturbed = dict(body)
    perturbed["observationid"] = perturbed.pop("observationId")
    diff = assert_key_set(case.name, case.full_keys, perturbed)
    assert diff is not None, "the key-set comparison did not report a renamed key"
    assert "missing=['observationId']" in diff, diff
