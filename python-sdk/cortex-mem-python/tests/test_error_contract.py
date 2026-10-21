"""The Python SDK's client-side error contract, pinned exactly.

Why this file exists
--------------------
The empty-update rejection is published as a full message in **both** READMEs,
and the other three SDKs publish their own version of the same rule. Until now
the Python side pinned almost none of it:

* ``test_update_with_no_fields_raises_validation_error`` asserted
  ``"at least one field" in exc_info.value.message`` — a **substring** of the
  body. The ``cortex-ce: validation error on update: `` prefix was therefore
  never checked, and it could change without a single test turning red.
* ``test_update_observation_extracted_data_empty_raises`` used
  ``pytest.raises(..., match="at least one field")``, which is a **regex
  search** over the rendered message, not an equality check — the same
  weakness in a different shape.
* Nothing bound the string the READMEs publish to the string the SDK returns,
  so the document and the code could drift apart silently.

What this file pins:

* the exact full message of an empty update, in all three call shapes;
* the exact error type and the ``field`` attribute;
* that no request is sent — and that the "no request" assertion can fail;
* that both READMEs publish exactly the string the SDK returns.

Nothing here touches the SDK's behaviour; it is tests and documentation only.
"""

from __future__ import annotations

import pathlib
import re
import socket

import pytest
import requests
import responses

from cortex_mem import CortexMemClient, ObservationUpdate, ValidationError
from cortex_mem.error import is_validation_error

BASE = "http://localhost:37777"

SDK_DIR = pathlib.Path(__file__).resolve().parent.parent
README_EN = SDK_DIR / "README.md"
README_ZH = SDK_DIR / "README-zh-CN.md"

# The message **body** is the only part the four SDKs share.
EMPTY_UPDATE_BODY = "at least one field must be provided for update"

# The **stem** of the body. A document that quotes only part of it is still
# quoting something the caller cannot match on, so the checks below look for
# the stem rather than the full body — that way a truncated quote is caught
# instead of slipping through.
EMPTY_UPDATE_STEM = "at least one field must be provided"

# The prefix Python and JS use (both wrap a ``ValidationError`` that carries a
# field name).
PY_JS_PREFIX = "cortex-ce: validation error on update: "

# The prefix Go uses (its error type is specific to ``ObservationUpdate``).
GO_PREFIX = "cortex-ce: ObservationUpdate validation error: "

# The exact string the Python SDK returns for an empty update.
EMPTY_UPDATE_MESSAGE = PY_JS_PREFIX + EMPTY_UPDATE_BODY

# The exact string the Python SDK returns when content and narrative are both
# set. The em dash is part of the message.
CONFLICT_MESSAGE = (
    "cortex-ce: validation error on content|narrative: content and narrative "
    "cannot both be set — they are aliases for the same backend field. "
    "Use either content=... or narrative=..., but not both."
)


def _client() -> CortexMemClient:
    return CortexMemClient(base_url=BASE, max_retries=1)


def _runtime_empty_update_message() -> str:
    """The message the SDK returns **right now**, taken from a live call.

    Comparing the READMEs against a constant would only prove that the
    document and this file agree with each other. Taking the value from the
    SDK is what makes the check notice that the runtime moved.
    """
    c = _client()
    try:
        c.update_observation("o1")
    except ValidationError as exc:
        return str(exc)
    finally:
        c.close()
    raise AssertionError("update_observation accepted an empty update")


# ==================== The empty-update contract ====================


class TestEmptyUpdateContract:
    """The empty update must produce one exact error, in every call shape."""

    @responses.activate
    def test_pure_empty_update_message_is_exact(self):
        """update_observation() with nothing set — the message, exactly."""
        c = _client()
        with pytest.raises(ValidationError) as exc_info:
            c.update_observation("o1")
        assert str(exc_info.value) == EMPTY_UPDATE_MESSAGE
        assert exc_info.value.field == "update"
        assert exc_info.value.message == EMPTY_UPDATE_BODY
        assert is_validation_error(exc_info.value) is True
        assert len(responses.calls) == 0, "no request should be made for an empty update"

    @responses.activate
    def test_dataclass_update_with_empty_extracted_data_is_the_same_error(self):
        """The dataclass form that assembles to an empty body — same error."""
        c = _client()
        with pytest.raises(ValidationError) as exc_info:
            c.update_observation("o1", ObservationUpdate(extracted_data={}))
        assert str(exc_info.value) == EMPTY_UPDATE_MESSAGE
        assert exc_info.value.field == "update"
        assert len(responses.calls) == 0, "no request should be made for an empty update"

    @responses.activate
    def test_dataclass_update_with_none_content_is_the_same_error(self):
        """content=None is "unset" in the dataclass form — so it is empty too."""
        c = _client()
        with pytest.raises(ValidationError) as exc_info:
            c.update_observation("o1", ObservationUpdate(content=None))
        assert str(exc_info.value) == EMPTY_UPDATE_MESSAGE
        assert exc_info.value.field == "update"
        assert len(responses.calls) == 0, "no request should be made for an empty update"

    @responses.activate
    def test_required_argument_error_is_a_different_message(self):
        """A blank observation id is a different error, and it is not the update one.

        The two must not be confused: the required-argument check names the
        argument, the update check names the update. Both use ValidationError,
        so the message is the only thing that tells them apart.
        """
        c = _client()
        with pytest.raises(ValidationError) as exc_info:
            c.update_observation("", ObservationUpdate(title="t"))
        assert str(exc_info.value) == "cortex-ce: validation error on observation_id: observation_id is required"
        assert exc_info.value.field == "observation_id"
        assert str(exc_info.value) != EMPTY_UPDATE_MESSAGE

    @responses.activate
    def test_content_narrative_conflict_message_is_exact(self):
        """The second update-level error: same type, different field and message."""
        c = _client()
        with pytest.raises(ValidationError) as exc_info:
            c.update_observation("o1", ObservationUpdate(content="a", narrative="b"))
        assert str(exc_info.value) == CONFLICT_MESSAGE
        assert exc_info.value.field == "content|narrative"
        assert len(responses.calls) == 0, "no request should be made for a conflicting update"


class TestNoRequestAssertionIsFalsifiable:
    """A "no request was sent" assertion that cannot fail is worth nothing.

    The client-side guard cases in ``test_client.py`` are only safe because an
    active ``responses`` mock swallows the request instead of letting it reach
    the resident backend on port 37777. These two cases prove that property
    holds, so the guard cannot silently rot into a live write.
    """

    def test_unmatched_request_never_reaches_the_socket(self):
        """With the mock active and nothing registered, the request must not escape."""
        escaped: list = []
        real_connect = socket.socket.connect

        def spy(self, address):  # pragma: no cover - only runs on failure
            escaped.append(address)
            raise OSError("request escaped to %r" % (address,))

        socket.socket.connect = spy
        try:
            with responses.RequestsMock():
                c = _client()
                try:
                    with pytest.raises(requests.RequestException):
                        c.search("/p")
                finally:
                    c.close()
        finally:
            socket.socket.connect = real_connect

        assert escaped == [], "a request escaped the mock and could have reached a live server"

    def test_the_mock_does_record_a_request_that_was_made(self):
        """And the counter the guard cases rely on must be able to go above zero."""
        with responses.RequestsMock() as rsps:
            rsps.add(responses.POST, f"{BASE}/api/ingest/tool-use", status=200)
            c = _client()
            try:
                c.record_observation("s1", "/p", "Read")
            finally:
                c.close()
            assert len(rsps.calls) == 1


# ==================== The READMEs must publish what the code returns ====================


def _read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


class TestReadmePublishesTheRuntimeMessage:
    """The READMEs publish the message; the code must be the one that is published."""

    @responses.activate
    def test_readmes_publish_the_exact_runtime_message(self):
        """The document must publish the string the SDK actually returns.

        The comparison is against a **live call**, not against the constant
        above: if the runtime message ever changes, the READMEs have to change
        with it, and this is the test that says so.
        """
        runtime = _runtime_empty_update_message()
        assert runtime == EMPTY_UPDATE_MESSAGE, (
            "the runtime message is %r but the pinned contract is %r — the READMEs "
            "below are compared against the runtime value" % (runtime, EMPTY_UPDATE_MESSAGE)
        )
        for path in (README_EN, README_ZH):
            content = _read(path)
            assert runtime in content, f"{path.name} does not publish {runtime!r}"

    @responses.activate
    def test_every_published_body_carries_a_registered_prefix(self):
        """A body printed without its prefix is not the string any SDK returns.

        The four-SDK comparison table legitimately prints all four forms, so
        the rule is per-SDK: Python's and Go's prefixes are always acceptable,
        and the bare body is acceptable only on a line that names Java — Java
        is the one SDK that prepends nothing.

        The scan looks for the **stem** of the message, so a document that
        quotes only part of the body is caught too: a truncated quote is still
        something a caller cannot match on.
        """
        for path in (README_EN, README_ZH):
            content = _read(path)
            occurrences = 0
            for match in re.finditer(re.escape(EMPTY_UPDATE_STEM), content):
                occurrences += 1
                start = match.start()
                if content[:start].endswith(PY_JS_PREFIX):
                    continue
                if content[:start].endswith(GO_PREFIX):
                    continue
                line = content[content.rfind("\n", 0, start) + 1 : content.find("\n", start)]
                if "Java" in line:
                    continue
                raise AssertionError(
                    f"{path.name}: the empty-update message is published without a registered "
                    f"prefix on this line:\n  {line.strip()}"
                )
            assert occurrences > 0, f"{path.name}: no occurrence of the message body found"

    @responses.activate
    def test_readmes_name_the_per_sdk_error_types(self):
        """Both languages must name what each SDK actually raises."""
        for path in (README_EN, README_ZH):
            content = _read(path)
            for expected in (
                "`ValidationError`",
                "dto.ObservationUpdateValidationError",
                "`IllegalArgumentException`",
                GO_PREFIX,
            ):
                assert expected in content, f"{path.name} must name {expected}"

    @responses.activate
    def test_readmes_do_not_claim_cross_sdk_sameness(self):
        """The false claims must not come back.

        The rule is the same in all four SDKs, but the **message** is not: Go
        uses its own prefix and Java prepends nothing at all.
        """
        for path in (README_EN, README_ZH):
            content = _read(path)
            for banned in (
                "The same rule and message apply in the Go, Java and JS SDKs",
                "规则与消息完全相同",
            ):
                assert banned not in content, f"{path.name} still contains the false claim {banned!r}"
