#!/usr/bin/env python3
"""Generate a golden set from this repository's real git history.

The purpose is to measure the extraction prompt against the traffic the repository
actually produces, instead of hand-written scenarios. Everything below is mechanical
and is written down before the run; no sample is selected because of a model answer.

Sample construction
-------------------
For each qualifying commit we take the real diff and build the tool trace the agent
would have produced: the path it touched, the first removed line, the first added line,
and a result line that repeats the commit subject. The identifier is the changed
artifact named in the trace's ``file_path`` (the file the agent edited); the expected
action class is derived from the shape of the diff and the commit message, following
the rules below.

Action rules (first match wins)
-------------------------------
1. document  every changed file is documentation (``docs/``, ``*.md``, ``*.txt``,
             ``*.adoc``) and nothing was deleted.
2. remove    every changed file only deleted lines.
3. secure    the subject or body mentions a security term
             (harden, security, auth, credential, secret, redact, sanitize, restrict,
             deny, cors, csrf, xss, injection, traversal, httponly).
4. create    every changed file is newly added.
5. modify    everything else.

Type mapping (first match wins)
-------------------------------
* documentation-only commit  -> change
* security commit             -> bugfix
* tests added, no production -> feature
* refactor/cleanup/rename     -> refactor
* fix/bug/regression/correct  -> bugfix
* add/feat/introduce/implement -> feature
* otherwise                   -> change

Exclusions (also mechanical, applied before any model sees the sample)
----------------------------------------------------------------------
merge commits, commits with binary or renamed files, commits touching more than six
files, commits with an empty subject, and commits whose diff has no text lines.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path

SECURITY_TERMS = re.compile(
    r"harden|security|auth|credential|secret|redact|saniti[sz]e|restrict|deny|"
    r"cors|csrf|xss|injection|traversal|httponly",
    re.IGNORECASE,
)
DOC_EXTS = (".md", ".txt", ".adoc")
CONVENTIONAL = re.compile(r"^[a-z]+(\([^)]*\))?!?\s*:\s*")
STOPWORDS = {
    "the", "a", "an", "and", "or", "for", "to", "of", "in", "on", "with", "from",
    "make", "keep", "add", "adds", "fix", "fixes", "change", "changes", "update",
    "updates", "refactor", "refactors", "record", "records", "archive", "archives",
    "score", "scores", "gate", "gates", "show", "shows", "use", "uses", "move",
    "moves", "replace", "replaces", "require", "requires", "exercise", "exercises",
}


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True,
                          text=True).stdout


def commits(limit: int) -> list[dict]:
    raw = git("log", "--first-parent", "--no-merges", "-n", str(limit),
              "--format=%H%x00%at%x00%s%x00%b%x01")
    out = []
    for record in raw.split("\x01"):
        record = record.strip("\n")
        if not record:
            continue
        sha, at, subject, body = record.split("\x00", 3)
        out.append({"sha": sha, "at": int(at), "subject": subject.strip(),
                    "body": body.strip()})
    return out


def changed_files(sha: str) -> list[dict]:
    numstat = git("show", "--format=", "--numstat", sha).splitlines()
    files = []
    for line in numstat:
        parts = line.split("\t")
        if len(parts) != 3:
            continue
        added, deleted, path = parts
        if added == "-" or deleted == "-" or "=>" in path:
            return []
        files.append({"path": path, "added": int(added), "deleted": int(deleted)})
    return files


def first_lines(sha: str, path: str) -> tuple[str, str]:
    diff = git("diff", "--unified=0", f"{sha}^", sha, "--", path)
    removed, added = "", ""
    for line in diff.splitlines():
        if line.startswith("---") or line.startswith("+++"):
            continue
        if line.startswith("-") and not removed:
            removed = line[1:].strip()
        elif line.startswith("+") and not added:
            added = line[1:].strip()
    return removed[:160], added[:160]


def basename(path: str) -> str:
    return Path(path).name


def identifier(path: str) -> str:
    name = basename(path)
    if name.endswith(".java"):
        return name[:-5]
    return name


def subject_words(subject: str) -> list[str]:
    cleaned = CONVENTIONAL.sub("", subject)
    return re.findall(r"[A-Za-z][A-Za-z0-9_./-]*", cleaned)


def is_doc(path: str) -> bool:
    return path.startswith("docs/") or path.lower().endswith(DOC_EXTS)


def action_for(commit: dict, files: list[dict]) -> str:
    if all(is_doc(f["path"]) for f in files) and all(f["deleted"] == 0 for f in files):
        return "document"
    if all(f["added"] == 0 for f in files):
        return "remove"
    text = commit["subject"] + "\n" + commit["body"]
    if SECURITY_TERMS.search(text):
        return "secure"
    if all(was_added(commit["sha"], f["path"]) for f in files):
        return "create"
    return "modify"


def was_added(sha: str, path: str) -> bool:
    result = subprocess.run(["git", "cat-file", "-e", f"{sha}^:{path}"],
                            capture_output=True)
    return result.returncode != 0


def type_for(commit: dict, files: list[dict], action: str) -> str:
    text = commit["subject"].lower()
    production = [f for f in files if not is_doc(f["path"]) and not is_test(f["path"])]
    tests = [f for f in files if is_test(f["path"])]
    if action == "document":
        return "change"
    if action == "secure":
        return "bugfix"
    if tests and not production:
        return "feature"
    if re.search(r"refactor|cleanup|rename|extract|simplif", text):
        return "refactor"
    if re.search(r"fix|bug|regression|correct", text):
        return "bugfix"
    if re.search(r"add|feat|introduce|implement|support", text):
        return "feature"
    return "change"


def is_test(path: str) -> bool:
    return path.startswith("src/test/") or "/test/" in path


def category_for(files: list[dict], action: str, expected_type: str) -> str:
    if action == "document":
        return "docs-update"
    if action == "secure":
        return "security-fix"
    if expected_type == "refactor":
        return "refactor"
    if expected_type == "bugfix":
        return "bug-fix"
    if any(f["path"].lower().endswith((".yml", ".yaml", ".properties", ".xml")) for f in files):
        return "config-change"
    if expected_type == "feature" and all(is_test(f["path"]) for f in files):
        return "test-add"
    return "feature"


def concepts_for(action: str, expected_type: str) -> list[str]:
    if action in ("secure", "remove") or expected_type == "bugfix":
        return ["problem-solution"]
    return ["what-changed"]


def build_sample(commit: dict, files: list[dict], index: int) -> dict | None:
    action = action_for(commit, files)
    added = sum(f["added"] for f in files)
    deleted = sum(f["deleted"] for f in files)
    ranked = sorted(files, key=lambda f: f["added"] + f["deleted"], reverse=True)
    primary = ranked[0]
    if action == "create":
        tool_name = "Write"
    else:
        tool_name = "Edit"

    old_line, new_line = first_lines(commit["sha"], primary["path"])
    identifiers = [identifier(primary["path"])]
    subject = identifiers[0]

    if tool_name == "Write":
        tool_input = f"file_path={primary['path']}\ncontent=<{len(new_line)} chars shown>"
    else:
        tool_input = (f"file_path={primary['path']}\n"
                      f"old_string=\"{old_line}\"\nnew_string=\"{new_line}\"")

    other = ", ".join(f["path"] for f in ranked[1:3])
    tool_output = (f"Edit applied. Commit message: {commit['subject']}. "
                   f"Totals: {len(files)} file(s), {added} insertion(s), {deleted} deletion(s). "
                   f"Changed paths: {', '.join(f['path'] for f in ranked[:3])}"
                   + (f". Other touched: {other}" if other else "")
                   + ". Verification: not recorded in this commit.")

    expected_type = type_for(commit, files, action)
    verb = {"create": "add", "modify": "change", "remove": "remove",
            "secure": "harden", "document": "document"}[action]
    keywords = [verb, subject.lower()] + [i.lower() for i in identifiers[1:]]
    return {
        "id": f"real-{commit['sha'][:10]}",
        "category": category_for(files, action, expected_type),
        "keySample": False,
        "toolName": tool_name,
        "cwd": "/repo",
        "occurredAt": subprocess.run(
            ["date", "-u", "-d", f"@{commit['at']}", "+%Y-%m-%dT%H:%M:%SZ"],
            capture_output=True, text=True).stdout.strip(),
        "toolInput": tool_input,
        "toolOutput": tool_output,
        "expectObservation": True,
        "expectedType": expected_type,
        "titleKeywords": keywords,
        "titleVerb": verb,
        "titleSubject": [subject],
        "titleIdentifiers": identifiers,
        "factsKeywords": [],
        "conceptsKeywords": concepts_for(action, expected_type),
        "titleAction": action,
        "notes": f"generated from {commit['sha']} ({commit['subject']})",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=120,
                        help="commits to scan before filtering (default 120)")
    parser.add_argument("--target", type=int, default=36,
                        help="maximum samples to emit (default 36, the floor is 30)")
    parser.add_argument("--out", default="backend/src/test/resources/eval/golden-tool-use-real.json")
    args = parser.parse_args()

    samples = []
    for commit in commits(args.limit):
        if not commit["subject"]:
            continue
        files = changed_files(commit["sha"])
        if not files or len(files) > 6:
            continue
        if not any(f["added"] + f["deleted"] > 0 for f in files):
            continue
        sample = build_sample(commit, files, len(samples))
        if sample is not None:
            samples.append(sample)
        if len(samples) >= args.target:
            break

    if len(samples) < 30:
        raise SystemExit(f"only {len(samples)} samples met the mechanical rules; need at least 30")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"version": "real-git-2026-11-03", "samples": samples},
                              indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {len(samples)} real-history samples to {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
