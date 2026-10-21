#!/usr/bin/env python3
"""Bind the published API reference to the server that is actually running.

Why this script exists
----------------------
`docs/API.md` and `docs/API-zh-CN.md` publish one HTTP method and path per
endpoint, and both files *assert* that the two languages describe the same
endpoints. That assertion used to be a promise with no mechanism behind it:
nothing in the build compared the published endpoints with the endpoints the
server answers, so renaming a route, adding one, or letting the two languages
drift apart left the reference silently stale.

What is compared
----------------
* the REST layer -- read from the running server's ``/v3/api-docs``;
* the actuator -- *discovered* from the running server's ``/actuator`` links
  page, so an actuator endpoint that gets exposed later is picked up without
  this file being edited;
* the MCP transport -- read from the backend's own ``application.yml`` and then
  probed live, so a configuration change moves the expectation with it
  instead of failing here;
* the two published references -- parsed out of the markdown.

The references must equal the union of the three server-side layers, and the
two languages must equal each other.

Exit codes
----------
0 -- the references match the running server.
1 -- they do not; every difference is printed.
2 -- the script could not run at all (unreachable server, unreadable file).

Usage
-----
    python3 scripts/check-api-doc-surface.py [--server-url URL] [--docs-dir DIR]
    python3 scripts/check-api-doc-surface.py --self-test
"""

import argparse
import json
import os
import re
import socket
import sys
import urllib.error
import urllib.parse
import urllib.request

# A request line written on its own line, as the English reference does inside
# its fenced request blocks: "GET /api/session/{sessionId}".
REQUEST_LINE = re.compile(
    r"^(GET|POST|PUT|DELETE|PATCH)\s+(/\S*?)\s*$", re.MULTILINE
)

# A path-style heading, as the Chinese reference uses throughout and the
# English reference uses inside its `## Context` section:
# "#### GET `/api/context/recent`".
PATH_HEADING = re.compile(
    r"^####\s+(GET|POST|PUT|DELETE|PATCH)\s+`(/[^`?]+)`\s*$", re.MULTILINE
)

HTTP_VERBS = ("get", "post", "put", "delete", "patch")

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


# --------------------------------------------------------------------------
# published references
# --------------------------------------------------------------------------
def parse_reference(markdown):
    """Every (method, path) the document publishes.

    Both published forms are collected: a request line on its own line, and a
    path-style heading. Neither form alone is complete -- the English file
    uses the first for most endpoints and the second inside `## Context`,
    while the Chinese file uses the second nearly everywhere -- and picking
    only one of them silently reports endpoints as missing.
    """
    ops = set()
    for match in REQUEST_LINE.finditer(markdown):
        path = match.group(2).split("?")[0].split("#")[0]
        ops.add("%s %s" % (match.group(1), path))
    for match in PATH_HEADING.finditer(markdown):
        ops.add("%s %s" % (match.group(1), match.group(2)))
    return ops


# --------------------------------------------------------------------------
# the running server
# --------------------------------------------------------------------------
def fetch(url, method="GET", body=None, headers=None, timeout=20):
    """(status, body) for one request. Never raises for an HTTP error status."""
    request = urllib.request.Request(
        url, method=method, data=body, headers=headers or {}
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read(1 << 20)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read(1 << 20)
    except Exception as exc:  # refused, reset, timed out, ...
        return 0, str(exc).encode("utf-8", "replace")


def stream_status(url, timeout=20):
    """The status line of a streaming response, without reading its body.

    The MCP transport answers with a Server-Sent Events stream that never
    ends, so the body must not be read -- only the first line of the response
    is of interest.
    """
    parts = urllib.parse.urlsplit(url)
    host = parts.hostname
    port = parts.port or (443 if parts.scheme == "https" else 80)
    target = parts.path or "/"
    if parts.query:
        target += "?" + parts.query
    request = (
        "GET %s HTTP/1.1\r\nHost: %s\r\nAccept: text/event-stream\r\n"
        "Connection: close\r\n\r\n" % (target, parts.netloc)
    )
    try:
        with socket.create_connection((host, port), timeout=timeout) as sock:
            sock.sendall(request.encode("ascii"))
            head = sock.recv(128)
    except OSError as exc:
        return 0, str(exc).encode("utf-8", "replace")
    match = re.match(rb"HTTP/1\.\d (\d{3})", head)
    return (int(match.group(1)) if match else 0), head


def rest_operations(base):
    """The (method, path) pairs springdoc publishes."""
    status, body = fetch(base + "/v3/api-docs")
    if status != 200:
        raise RuntimeError(
            "cannot read the OpenAPI document from %s/v3/api-docs (status %s)"
            % (base, status)
        )
    document = json.loads(body)
    operations = set()
    for path, item in document.get("paths", {}).items():
        for verb in HTTP_VERBS:
            if verb in item:
                operations.add("%s %s" % (verb.upper(), path))
    return operations


def actuator_operations(base):
    """The actuator endpoints the running server exposes, discovered live.

    The links page names every exposed endpoint, so an endpoint that is
    exposed later appears here without this file being touched.
    """
    status, body = fetch(base + "/actuator")
    if status != 200:
        return set()
    links = json.loads(body).get("_links", {})
    operations = {"GET /actuator"}
    for name, info in links.items():
        if name == "self" or info.get("templated"):
            continue
        # The path comes from the link's own href, not from its name: the name
        # is a label and need not be the last path segment.
        href = info.get("href", "")
        path = urllib.parse.urlsplit(href).path or ""
        if not path:
            continue
        operations.add("GET %s" % path)
    return operations


# --------------------------------------------------------------------------
# the backend's own configuration
# --------------------------------------------------------------------------
def _yaml_tree(text):
    """The nested-mapping subset of YAML that application.yml uses.

    A tiny reader keeps this script free of a third-party YAML dependency.
    Anything it cannot read is simply absent, and the live probe below turns
    a mis-read into a visible failure rather than a silent pass.
    """
    root = {}
    stack = [(-1, root)]
    for raw in text.splitlines():
        stripped = raw.strip()
        if not stripped or stripped.startswith("#") or ":" not in stripped:
            continue
        indent = len(raw) - len(raw.lstrip())
        key, _, value = stripped.partition(":")
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        while stack and stack[-1][0] >= indent:
            stack.pop()
        parent = stack[-1][1]
        if value == "":
            node = {}
            parent[key] = node
            stack.append((indent, node))
        else:
            parent[key] = value
    return root


def _yaml_path(tree, dotted):
    node = tree
    for part in dotted.split("."):
        if not isinstance(node, dict) or part not in node:
            return None
        node = node[part]
    return node


def mcp_operations(base, backend_dir):
    """The MCP transport endpoints, derived from the backend's configuration.

    Returns (operations, problems). The configured endpoints are probed live:
    a transport the configuration announces but the server does not serve is a
    problem of its own, and is not silently dropped from the comparison.
    """
    problems = []
    config_path = os.path.join(
        backend_dir, "src", "main", "resources", "application.yml"
    )
    with open(config_path, encoding="utf-8") as handle:
        tree = _yaml_tree(handle.read())

    protocol = _yaml_path(tree, "spring.ai.mcp.server.protocol")
    sse_endpoint = _yaml_path(tree, "spring.ai.mcp.server.sse-endpoint")
    sse_message = _yaml_path(tree, "spring.ai.mcp.server.sse-message-endpoint")
    http_endpoint = _yaml_path(tree, "spring.ai.mcp.server.streamable-http.mcp-endpoint")

    if not protocol:
        problems.append(
            "the MCP protocol could not be read from %s -- the comparison "
            "would be vacuous" % config_path
        )
        return set(), problems

    operations = set()
    if protocol.upper() == "SSE":
        if not sse_endpoint or not sse_message:
            problems.append(
                "the SSE transport is configured but its endpoints could not "
                "be read from %s" % config_path
            )
            return set(), problems
        operations.add("GET %s" % sse_endpoint)
        operations.add("POST %s" % sse_message)
    else:
        if not http_endpoint:
            problems.append(
                "the %s transport is configured but its endpoint could not be "
                "read from %s" % (protocol, config_path)
            )
            return set(), problems
        operations.add("POST %s" % http_endpoint)

    for operation in sorted(operations):
        verb, path = operation.split(" ", 1)
        if verb == "GET":
            status, _ = stream_status(base + path)
        else:
            status, _ = fetch(
                base + path,
                method="POST",
                body=b"{}",
                headers={"Content-Type": "application/json"},
            )
        if status == 404:
            problems.append(
                "the MCP transport endpoint %s is configured in application.yml "
                "but the running server does not serve it (404)" % operation
            )
        elif status == 0:
            problems.append(
                "the MCP transport endpoint %s could not be reached (%s)"
                % (operation, "no response")
            )
    return operations, problems


# --------------------------------------------------------------------------
# the comparison
# --------------------------------------------------------------------------
def compare(reference_en, reference_zh, served):
    """Every way in which the published references differ from the server."""
    problems = []
    if reference_en != reference_zh:
        only_en = sorted(reference_en - reference_zh)
        only_zh = sorted(reference_zh - reference_en)
        problems.append(
            "the two published references do not describe the same endpoints"
        )
        if only_en:
            problems.append("  only in the English reference: %s" % ", ".join(only_en))
        if only_zh:
            problems.append("  only in the Chinese reference: %s" % ", ".join(only_zh))
    for label, reference in (("English", reference_en), ("Chinese", reference_zh)):
        missing = sorted(served - reference)
        extra = sorted(reference - served)
        if missing:
            problems.append(
                "  the %s reference does not document: %s" % (label, ", ".join(missing))
            )
        if extra:
            problems.append(
                "  the %s reference documents endpoints the server does not "
                "serve: %s" % (label, ", ".join(extra))
            )
    return problems


# --------------------------------------------------------------------------
# self-test
# --------------------------------------------------------------------------
def self_test():
    """The checker has to be able to fail, or a green run means nothing.

    Every case below feeds the comparison a difference that *must* be
    reported. If the checker stopped reporting any of them, this exits 1.
    """
    served = {"GET /a", "GET /b", "POST /c"}
    cases = [
        (
            "a reference missing an endpoint",
            {"GET /a", "POST /c"},
            {"GET /a", "GET /b", "POST /c"},
            True,
        ),
        (
            "a reference documenting an endpoint the server does not serve",
            {"GET /a", "GET /b", "POST /c", "GET /d"},
            {"GET /a", "GET /b", "POST /c", "GET /d"},
            True,
        ),
        (
            "the two languages drifting apart",
            {"GET /a", "GET /b", "POST /c"},
            {"GET /a", "POST /c"},
            True,
        ),
        (
            "a reference that matches the server",
            set(served),
            set(served),
            False,
        ),
    ]
    failures = []
    for name, en, zh, expect_problem in cases:
        problems = compare(en, zh, served)
        reported = bool(problems)
        if reported != expect_problem:
            failures.append(
                "%s: expected %s, the checker reported %s"
                % (name, "a problem" if expect_problem else "no problem", problems)
            )

    # The parser has to read both published forms, and neither alone.
    sample = (
        "### Get Session\n\n```\nGET /api/session/{sessionId}\n```\n\n"
        "#### GET `/api/context/recent`\n"
    )
    parsed = parse_reference(sample)
    if parsed != {"GET /api/session/{sessionId}", "GET /api/context/recent"}:
        failures.append("the parser did not read both published forms: %s" % sorted(parsed))

    # A request line carrying a query string is still that endpoint.
    query = parse_reference("```\nGET /api/observations?project=/p&limit=20\n```\n")
    if query != {"GET /api/observations"}:
        failures.append("the parser kept a query string: %s" % sorted(query))

    if failures:
        print("SELF-TEST FAILED")
        for failure in failures:
            print("  - %s" % failure)
        return 1
    print("SELF-TEST PASSED (%d cases)" % (len(cases) + 2))
    return 0


# --------------------------------------------------------------------------
def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--server-url",
        default=os.environ.get("SERVER_URL", "http://127.0.0.1:37777"),
        help="the running backend to compare against (default: $SERVER_URL)",
    )
    parser.add_argument(
        "--docs-dir",
        default=os.path.join(REPO_ROOT, "docs"),
        help="the directory holding API.md and API-zh-CN.md",
    )
    parser.add_argument(
        "--backend-dir",
        default=os.path.join(REPO_ROOT, "backend"),
        help="the backend module holding application.yml",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="prove the checker can fail, then exit",
    )
    args = parser.parse_args(argv)

    if args.self_test:
        return self_test()

    base = args.server_url.rstrip("/")
    documents = {}
    for name in ("API.md", "API-zh-CN.md"):
        path = os.path.join(args.docs_dir, name)
        try:
            with open(path, encoding="utf-8") as handle:
                documents[name] = handle.read()
        except OSError as exc:
            print("ERROR: cannot read %s (%s)" % (path, exc), file=sys.stderr)
            return 2

    try:
        served = rest_operations(base)
    except (RuntimeError, ValueError) as exc:
        print("ERROR: %s" % exc, file=sys.stderr)
        return 2

    actuator = actuator_operations(base)
    mcp, problems = mcp_operations(base, args.backend_dir)
    served |= actuator
    served |= mcp

    reference_en = parse_reference(documents["API.md"])
    reference_zh = parse_reference(documents["API-zh-CN.md"])

    problems += compare(reference_en, reference_zh, served)

    print("server            : %s" % base)
    print("served operations : %d (%d REST + %d transport)"
          % (len(served), len(served) - len(actuator) - len(mcp),
             len(actuator) + len(mcp)))
    print("English reference : %d endpoints" % len(reference_en))
    print("Chinese reference : %d endpoints" % len(reference_zh))

    if problems:
        print("")
        print("FAIL: the published references do not match the running server")
        for problem in problems:
            print("  %s" % problem)
        return 1

    print("")
    print("PASS: both published references match the running server exactly")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
