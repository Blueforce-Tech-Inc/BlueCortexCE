# coding: utf-8
"""Pin every client-side guard the Python SDK's README promises.

Why this file exists
--------------------
Round 398 swept the 35 ``raise ValidationError`` guards in ``cortex_mem/client.py``
one at a time: each guard's condition was made permanently false, the whole
suite was re-run, and the file was restored byte for byte. A guard whose
removal left the suite green was a guard that **no test pinned**.

**32** of the 35 were pinned. The three that were not:

- ``start_session`` rejected an empty ``session_id`` but nothing pinned its
  empty ``project_path``;
- ``get_latest_extraction`` pinned its ``template_name`` but not its
  ``project_path``;
- ``get_extraction_history`` pinned its ``template_name`` but not its
  ``project_path``.

The pattern is the same each time: **one guard per method was covered, its
sibling was not**. The README publishes an eighteen-row table promising that
every listed argument is rejected client-side and that no request is sent — so
those three promises were unbacked.

This file pins all 35 guards, binds the published table to the runtime, and
pins the two behaviours that the README does *not* promise but that callers
depend on:

- the **whitespace-only** difference between this SDK and the other three
  (Go, Java and JS strip before checking; this one does not), and
- the **two different messages** the content/narrative conflict produces
  depending on whether the dataclass was constructed with both fields or
  mutated afterwards.

Probe traps recorded here so the next reader does not repeat them
-----------------------------------------------------------------
1. ``responses`` is **not active** unless a mock is registered or the
   ``@responses.activate`` decorator is used. A case that asserts "no request
   was sent" must run inside an active mock, otherwise it really does reach
   the resident backend on 37777.
2. The "no request" assertion has to be able to fail — see
   ``test_the_request_counter_can_go_above_zero``, which is the positive
   control for the counter itself.
3. A script that lives outside the SDK directory imports the **installed**
   ``cortex_mem`` from site-packages, not the working tree. The copy installed
   on 2026-04-25 has no ``project_path`` / ``tool_name`` guards on the capture
   methods at all, so a probe run from ``/tmp`` silently measures a different
   client. ``pytest`` run from the SDK directory imports the working tree
   (``python -m pytest`` puts the cwd on ``sys.path``); this file therefore
   asserts nothing about paths, but the sweep that produced these numbers did
   and the difference is why the numbers are in this file at all.
"""

from __future__ import annotations

import inspect
import os
import re
from typing import Any, Callable

import pytest
import responses

from cortex_mem import CortexMemClient
from cortex_mem.dto import ObservationUpdate
from cortex_mem.error import ValidationError

SDK_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = "http://127.0.0.1:37777"

CONFLICT_MESSAGE = (
    "content and narrative cannot both be set — they are aliases for the same "
    "backend field. Use either content=... or narrative=..., but not both."
)

# The 35 guards, in the order they appear in cortex_mem/client.py.
#
# Each entry is (id, method, expected field, expected message, call). The call
# receives a client and must trip exactly one guard. The expected field and
# message are literals read off the running client and checked by hand - they
# are not derived from the expressions below, so a change in the client turns
# this test red instead of silently following along.
GUARDS: list[tuple[str, str, str, str, Callable[[CortexMemClient], Any]]] = [
    ("start_session.session_id", "start_session", "session_id", "session_id is required",
     lambda c: c.start_session("", "/p")),
    ("start_session.project_path", "start_session", "project_path", "project_path is required",
     lambda c: c.start_session("s1", "")),
    ("update_session_user_id.session_id", "update_session_user_id", "session_id", "session_id is required",
     lambda c: c.update_session_user_id("", "u1")),
    ("update_session_user_id.user_id", "update_session_user_id", "user_id", "user_id is required",
     lambda c: c.update_session_user_id("s1", "")),
    ("record_observation.session_id", "record_observation", "session_id", "session_id is required",
     lambda c: c.record_observation("", "/p", "Read")),
    ("record_observation.project_path", "record_observation", "project_path", "project_path is required",
     lambda c: c.record_observation("s1", "", "Read")),
    ("record_observation.tool_name", "record_observation", "tool_name", "tool_name is required",
     lambda c: c.record_observation("s1", "/p", "")),
    ("record_session_end.session_id", "record_session_end", "session_id", "session_id is required",
     lambda c: c.record_session_end("", "/p")),
    ("record_session_end.project_path", "record_session_end", "project_path", "project_path is required",
     lambda c: c.record_session_end("s1", "")),
    ("record_user_prompt.session_id", "record_user_prompt", "session_id", "session_id is required",
     lambda c: c.record_user_prompt("", "hi", "/p")),
    ("record_user_prompt.prompt_text", "record_user_prompt", "prompt_text", "prompt_text is required",
     lambda c: c.record_user_prompt("s1", "", "/p")),
    ("record_user_prompt.project_path", "record_user_prompt", "project_path", "project_path is required",
     lambda c: c.record_user_prompt("s1", "hi", "")),
    ("retrieve_experiences.task", "retrieve_experiences", "task", "task is required",
     lambda c: c.retrieve_experiences("")),
    ("build_icl_prompt.task", "build_icl_prompt", "task", "task is required",
     lambda c: c.build_icl_prompt("")),
    ("search.project", "search", "project", "project is required",
     lambda c: c.search("")),
    ("get_observation.observation_id", "get_observation", "observation_id", "observation_id is required",
     lambda c: c.get_observation("")),
    ("get_observations_by_ids.ids_empty", "get_observations_by_ids", "ids", "ids must not be empty",
     lambda c: c.get_observations_by_ids([])),
    ("get_observations_by_ids.ids_too_many", "get_observations_by_ids", "ids",
     "batch size exceeds maximum of 100 (got 101)",
     lambda c: c.get_observations_by_ids([f"obs-{i}" for i in range(101)])),
    ("get_observations_by_ids.ids_element", "get_observations_by_ids", "ids[1]", "ids[1] is empty",
     lambda c: c.get_observations_by_ids(["obs-1", ""])),
    ("trigger_refinement.project_path", "trigger_refinement", "project_path", "project_path is required",
     lambda c: c.trigger_refinement("")),
    ("submit_feedback.observation_id", "submit_feedback", "observation_id", "observation_id is required",
     lambda c: c.submit_feedback("", "useful")),
    ("submit_feedback.feedback_type", "submit_feedback", "feedback_type", "feedback_type is required",
     lambda c: c.submit_feedback("obs-1", "")),
    ("update_observation.observation_id", "update_observation", "observation_id", "observation_id is required",
     lambda c: c.update_observation("")),
    ("update_observation.unknown_field", "update_observation", "update", "unknown update fields: bogus_field",
     lambda c: c.update_observation("obs-1", bogus_field=1)),
    ("update_observation.content_and_narrative", "update_observation", "update", CONFLICT_MESSAGE,
     lambda c: c.update_observation("obs-1", content="c", narrative="n")),
    ("update_observation.empty_body", "update_observation", "update",
     "at least one field must be provided for update",
     lambda c: c.update_observation("obs-1")),
    ("delete_observation.observation_id", "delete_observation", "observation_id", "observation_id is required",
     lambda c: c.delete_observation("")),
    ("get_quality_distribution.project_path", "get_quality_distribution", "project_path",
     "project_path is required", lambda c: c.get_quality_distribution("")),
    ("trigger_extraction.project_path", "trigger_extraction", "project_path", "project_path is required",
     lambda c: c.trigger_extraction("")),
    ("get_latest_extraction.project_path", "get_latest_extraction", "project_path",
     "project_path is required", lambda c: c.get_latest_extraction("", "tpl")),
    ("get_latest_extraction.template_name", "get_latest_extraction", "template_name",
     "template_name is required", lambda c: c.get_latest_extraction("/p", "")),
    ("get_extraction_history.project_path", "get_extraction_history", "project_path",
     "project_path is required", lambda c: c.get_extraction_history("", "tpl")),
    ("get_extraction_history.template_name", "get_extraction_history", "template_name",
     "template_name is required", lambda c: c.get_extraction_history("/p", "")),
    ("get_extraction_history.negative_limit", "get_extraction_history", "limit",
     "limit must not be negative", lambda c: c.get_extraction_history("/p", "tpl", limit=-1)),
]

# The 35th guard is the one that wraps a ValueError coming out of
# ObservationUpdate.to_wire(). It is listed separately because the message it
# produces is a *re-wrapped* message - see
# test_update_conflict_has_two_different_messages for the exact strings.
TO_WIRE_GUARD = (
    "update_observation.to_wire_conflict",
    "update_observation",
    "update",
    f"cortex-ce: validation error on content|narrative: {CONFLICT_MESSAGE}",
)


def _client() -> CortexMemClient:
    return CortexMemClient(base_url=BASE)


def _readme_lines(name: str) -> list[str]:
    path = os.path.join(SDK_ROOT, name)
    with open(path, encoding="utf-8") as handle:
        return handle.read().replace("\r\n", "\n").split("\n")


def _readme_section(name: str, heading: str) -> str:
    """Return the body of one section, from its heading to the next heading.

    Any heading ends the section, not just a ``###`` one: the required-argument
    section is the last ``###`` before a ``##``, and stopping only at ``###``
    would swallow the rest of the file.
    """
    lines = _readme_lines(name)
    start = -1
    for index, line in enumerate(lines):
        if line.strip() == heading:
            start = index
            break
    assert start >= 0, f"{name}: section {heading!r} not found"
    body: list[str] = []
    for line in lines[start + 1:]:
        if line.startswith("#"):
            break
        body.append(line)
    return "\n".join(body)


def _published_required_argument_table(name: str) -> dict[str, list[str]]:
    """Read the published required-argument table out of one README.

    Returns {method: [argument names]} with the argument names taken from the
    backticked tokens of the second cell. The table header differs per
    language, so it is passed in rather than guessed.
    """
    header = "| Method | Required arguments |"
    if name == "README-zh-CN.md":
        header = "| 方法 | 必填参数 |"
    rows: dict[str, list[str]] = {}
    in_table = False
    for line in _readme_lines(name):
        if not in_table:
            in_table = line.startswith(header)
            continue
        if not line.startswith("|"):
            break
        cells = line.split("|")
        if len(cells) < 3:
            continue
        method = cells[1].strip().strip("`")
        if not method or method.startswith("-"):
            continue
        args = re.findall(r"`([^`]+)`", cells[2])
        rows[method] = args
    return rows


# ==================== Every guard is pinned ====================


def _assert_guard(guard: tuple[str, str, str, str, Callable[[CortexMemClient], Any]]) -> None:
    guard_id, _method, field, message, call = guard
    with responses.RequestsMock(assert_all_requests_are_fired=False) as rsps:
        client = _client()
        try:
            try:
                call(client)
            except ValidationError as exc:
                assert exc.field == field, f"{guard_id}: field {exc.field!r} != {field!r}"
                assert exc.message == message, f"{guard_id}: message {exc.message!r} != {message!r}"
                assert str(exc) == f"cortex-ce: validation error on {field}: {message}", (
                    f"{guard_id}: rendered string {str(exc)!r} does not match the documented shape"
                )
            else:
                raise AssertionError(
                    f"{guard_id}: no ValidationError was raised - the guard did not fire"
                )
            assert len(rsps.calls) == 0, (
                f"{guard_id}: {len(rsps.calls)} request(s) went out; a client-side "
                f"rejection must send nothing"
            )
        finally:
            client.close()


def test_every_guard_is_pinned():
    """Each of the 35 guards rejects with the documented field and message.

    One test that walks the table rather than one test per guard: the ids are
    in the assertion messages, so a failure names the guard that broke.
    """
    for guard in GUARDS:
        _assert_guard(guard)


def test_the_to_wire_guard_is_pinned():
    """The 35th guard: a conflict detected inside to_wire(), not at construction."""
    guard_id, method, field, message = TO_WIRE_GUARD
    assert guard_id not in {g[0] for g in GUARDS}, "the to_wire guard is listed twice"
    assert method == "update_observation"

    update = ObservationUpdate(content="c")
    update.narrative = "n"  # assigned after construction, so __post_init__ already passed

    with responses.RequestsMock(assert_all_requests_are_fired=False) as rsps:
        client = _client()
        try:
            try:
                client.update_observation("obs-1", update)
            except ValidationError as exc:
                assert exc.field == field, f"{guard_id}: field {exc.field!r} != {field!r}"
                assert exc.message == message, f"{guard_id}: message {exc.message!r} != {message!r}"
            else:
                raise AssertionError(f"{guard_id}: no ValidationError was raised")
            assert len(rsps.calls) == 0, f"{guard_id}: {len(rsps.calls)} request(s) went out"
        finally:
            client.close()


def test_client_has_no_guard_the_table_missed():
    """The table has to keep up with the client.

    If somebody adds a guard to ``client.py`` without adding a case here, the
    new guard is unpinned - exactly the state this file was written to end. So
    the count of ``raise ValidationError`` sites in the client has to equal the
    number of cases in this file.
    """
    with open(os.path.join(SDK_ROOT, "cortex_mem", "client.py"), encoding="utf-8") as handle:
        source = handle.read()
    in_client = len(re.findall(r"raise ValidationError\(", source))
    in_table = len(GUARDS) + 1  # GUARDS plus the to_wire guard
    assert in_client == in_table, (
        f"client.py raises ValidationError in {in_client} place(s) but this file "
        f"pins {in_table}; a new guard needs a new case"
    )


# ==================== The published table is bound to the runtime ====================


def test_published_required_argument_table_matches_runtime():
    """The README's eighteen-row table has to match what the client enforces."""
    guarded = {guard[1] for guard in GUARDS} | {TO_WIRE_GUARD[1]}
    assert guarded, "no guarded method found - the comparison below would hold vacuously"

    for name in ("README.md", "README-zh-CN.md"):
        published = _published_required_argument_table(name)
        assert published, f"{name}: the published table is empty - the comparison would hold vacuously"
        assert set(published) == guarded, (
            f"{name}: the published table does not match the runtime\n"
            f"  published: {sorted(published)}\n"
            f"  runtime:   {sorted(guarded)}"
        )


def test_published_required_arguments_all_have_a_guard():
    """Every argument the README names must actually be enforced somewhere.

    The published rows name arguments in prose-free backticks; each of them has
    to correspond to a guard on that method, or the table promises something
    the client does not check.
    """
    fields_by_method: dict[str, set[str]] = {}
    for _id, method, field, _message, _call in GUARDS:
        fields_by_method.setdefault(method, set()).add(field.split("[")[0])
    fields_by_method.setdefault(TO_WIRE_GUARD[1], set()).add(TO_WIRE_GUARD[2].split("[")[0])

    for name in ("README.md", "README-zh-CN.md"):
        published = _published_required_argument_table(name)
        for method, args in published.items():
            enforced = fields_by_method.get(method, set())
            for arg in args:
                assert arg in enforced, (
                    f"{name}: the table promises {method}({arg}) is checked, but the "
                    f"client enforces only {sorted(enforced)} on that method"
                )


def test_exposed_api_method_count_is_pinned():
    """Pin the API surface: close() is lifecycle, not an API method."""
    members = [
        name
        for name, _ in inspect.getmembers(CortexMemClient, predicate=callable)
        if not name.startswith("_") and name != "close"
    ]
    assert len(members) == 25, f"expected 25 API methods, got {len(members)}: {sorted(members)}"


def _published_default_base_url(name: str) -> str:
    """Read the default base URL out of one README's configuration table."""
    for line in _readme_lines(name):
        if not line.startswith("| `base_url` |"):
            continue
        cells = line.split("|")
        if len(cells) >= 3:
            return cells[2].strip().strip("`")
    return ""


def test_published_default_base_url_matches_runtime():
    """The base URL each README publishes must be the one the client uses.

    Every README is checked on its own. A single loop that reads both files and
    keeps the last value would let a wrong value in one language be masked by a
    correct value in the other - which is exactly what mutation M8 did when
    this test was first written.
    """
    with CortexMemClient() as client:
        actual = client._base_url
    for name in ("README.md", "README-zh-CN.md"):
        published = _published_default_base_url(name)
        assert published, (
            f"{name}: no default base URL is published - the comparison would hold vacuously"
        )
        assert published == actual, (
            f"{name}: the README publishes {published!r}, the client uses {actual!r}"
        )


# ==================== The whitespace difference ====================


def test_whitespace_only_arguments_are_accepted_and_sent():
    """This SDK checks for *empty*, not for *blank*.

    Go (``strings.TrimSpace``), Java (``String.isBlank``) and JS
    (``value.trim()``) all reject a whitespace-only required argument. This SDK
    does not: ``"   "`` is a non-empty string, so it passes every guard and is
    sent on the wire. That is a real difference between the four SDKs, so it is
    pinned here rather than left to be discovered.

    The behaviour itself is **not** changed here - narrowing the accepted set
    is a contract change for existing callers, so it is recorded as a finding
    instead. What this file pins is that the behaviour is what the README says
    it is.
    """
    with responses.RequestsMock(assert_all_requests_are_fired=False) as rsps:
        rsps.add(
            responses.POST,
            f"{BASE}/api/session/start",
            json={"session_db_id": "db-1", "session_id": "s1", "updateFiles": [], "prompt_number": 0},
            status=200,
        )
        client = _client()
        try:
            client.start_session("s1", "   ")
        finally:
            client.close()
        assert len(rsps.calls) == 1, (
            "a whitespace-only project_path is expected to be accepted and sent; "
            f"{len(rsps.calls)} request(s) went out"
        )


def test_ids_elements_are_the_one_place_that_rejects_whitespace():
    """``get_observations_by_ids`` strips its elements, so it rejects blanks.

    This is the internal inconsistency: 30+ guards use ``if not x`` and accept
    ``"   "``, while the id elements use ``if not id_ or not id_.strip()`` and
    reject it.
    """
    with responses.RequestsMock(assert_all_requests_are_fired=False) as rsps:
        client = _client()
        try:
            with pytest.raises(ValidationError) as caught:
                client.get_observations_by_ids(["obs-1", "   "])
            assert caught.value.field == "ids[1]"
            assert caught.value.message == "ids[1] is empty"
            assert len(rsps.calls) == 0, "a rejected id element must send nothing"
        finally:
            client.close()


def test_the_readmes_document_the_whitespace_difference():
    """Both READMEs have to name the whitespace difference.

    All four SDK READMEs claim the four SDKs enforce "the same set of
    arguments" with one exception (Java's absolute-path rule). The whitespace
    rule is a **second** difference, so the section has to say so - otherwise
    the sentence is a false statement about this SDK.
    """
    en = _readme_section("README.md", "### Required Arguments Are Checked Client-Side")
    zh = _readme_section("README-zh-CN.md", "### 必填参数在客户端校验")
    assert "whitespace-only" in en, "the English section does not mention the whitespace difference"
    assert "纯空白" in zh, "the Chinese section does not mention the whitespace difference"
    assert "two" in en.lower() and "两处" in zh, (
        "the section still claims a single difference; the whitespace rule is a second one"
    )


# ==================== The two content/narrative conflict messages ====================


def test_update_conflict_has_two_different_messages():
    """The same conflict produces two different strings, depending on the path.

    Constructed with both fields: the dataclass's ``__post_init__`` raises
    before the client is reached, so the message names the
    ``content|narrative`` field.

    Constructed with one field and then mutated: ``__post_init__`` has already
    passed, ``to_wire()`` catches the conflict inside the client, and the
    client re-wraps it - which produces the prefix **twice** and changes the
    field to ``update``.

    The duplicated prefix is a message-shape oddity, not a behaviour change:
    the strings are published nowhere, so this file pins them as they are
    rather than "fixing" them.
    """
    with responses.RequestsMock(assert_all_requests_are_fired=False) as rsps:
        client = _client()
        try:
            # Path A: constructed with both fields.
            try:
                client.update_observation("obs-1", ObservationUpdate(content="c", narrative="n"))
            except ValidationError as exc:
                assert exc.field == "content|narrative"
                assert exc.message == CONFLICT_MESSAGE
                assert str(exc) == f"cortex-ce: validation error on content|narrative: {CONFLICT_MESSAGE}"
            else:
                raise AssertionError("the conflict was not detected at construction time")

            # Path B: constructed with one field, mutated afterwards.
            update = ObservationUpdate(content="c")
            update.narrative = "n"
            try:
                client.update_observation("obs-1", update)
            except ValidationError as exc:
                assert exc.field == "update"
                assert exc.message == f"cortex-ce: validation error on content|narrative: {CONFLICT_MESSAGE}"
                assert str(exc) == (
                    "cortex-ce: validation error on update: "
                    f"cortex-ce: validation error on content|narrative: {CONFLICT_MESSAGE}"
                )
            else:
                raise AssertionError("the conflict was not detected at to_wire() time")

            assert len(rsps.calls) == 0, "a rejected update must send nothing"
        finally:
            client.close()


# ==================== Positive control ====================


def test_the_request_counter_can_go_above_zero():
    """Prove the "no request was sent" assertions can actually fail.

    Every other test in this file leans on ``len(rsps.calls) == 0``. A counter
    that was stuck at zero would make all of them pass for the wrong reason, so
    here a request is made on purpose and the counter has to report it.
    """
    with responses.RequestsMock(assert_all_requests_are_fired=False) as rsps:
        rsps.add(
            responses.POST,
            f"{BASE}/api/session/start",
            json={"session_db_id": "db-1", "session_id": "s1", "updateFiles": [], "prompt_number": 0},
            status=200,
        )
        client = _client()
        try:
            client.start_session("s1", "/p")
        finally:
            client.close()
        assert len(rsps.calls) == 1, (
            f"the counter reported {len(rsps.calls)} request(s); it has to be able "
            f"to go above zero for the other assertions to mean anything"
        )
