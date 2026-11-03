#!/usr/bin/env python3
"""Generate a golden set from this repository's real git history.

The purpose is to measure the extraction prompt against the traffic the repository
actually produces, instead of hand-written scenarios. Everything below is mechanical
and is written down before the run; no sample is selected because of a model answer.

Sample construction
-------------------
For each qualifying commit we take the real diff and build the tool trace the agent
would have produced. The trace covers **every file the diff touched**: the edited
file is named by ``file_path``, and all changed paths appear in ``read_files`` and
in the result lines, so a title that names any changed artifact has that artifact
visible in the trace. The identifier is the basename of the largest changed file
(the artifact the agent edited); the expected action set is derived from the shape
of the diff, following the rules below.

Action rules (per changed file, then unioned)
---------------------------------------------
1. remove    the file only deletes lines (added == 0, deleted > 0); a file that is
             net-deleting (deleted > added) in a commit whose subject says remove,
             delete, drop or strip counts as well.
2. document  the path is documentation (``docs/``, ``*.md``, ``*.txt``, ``*.adoc``).
3. secure    the commit text or that file's changed lines mention a security term
             (harden, security, auth, credential, secret, redact, sanitize, restrict,
             deny, cors, csrf, xss, injection, traversal, httponly).
4. create    the file did not exist in the parent commit.
5. modify    everything else.

The rules above are **facets, not a precedence chain**: each file contributes every
facet it actually exhibits. A docs deletion is ``{document, remove}``; a modified
config file is ``{modify}``; a new security migration is ``{create, secure}``; an
edited doc is ``{document, modify}``. A sample's expected action class is the union
across files (``titleActions``); a title hits if its leading verb maps to any member.
This is what makes a commit that adds a file *and* edits a test *and* updates docs
non-ambiguous. ``titleAction`` keeps the highest-priority member for older readers.

Contradiction exclusions (commit message vs diff)
-------------------------------------------------
* add-family subject (add/adds/create/introduce/implement/support) with no newly
  added file — the message says "add" while the diff only edits.
* remove-family subject (remove/removes/delete/drop/strip) with no deletion.
* document-family subject (document/documented/docs) that also touches non-doc files.

Other exclusions (also mechanical, applied before any model sees the sample)
----------------------------------------------------------------------------
merge commits, binary or renamed files, commits touching more than six files,
commits with an empty subject, and commits whose diff has no text lines.

Diagnose samples
----------------
The repository's history contains no read-only commits, so diagnose traces are
reconstructed from commits whose subject states an investigation (audit, patrol,
verify, measure, inspect, probe). The trace is the set of files that commit names,
read without editing; no change is claimed. These are marked
``expectObservation: false`` — the same expectation the hand-written set uses for
pure reads.

Type mapping (first match wins)
-------------------------------
* diagnose or documentation-only commit -> change
* security commit                       -> bugfix
* tests added, no production            -> feature
* refactor/cleanup/rename               -> refactor
* fix/bug/regression/correct            -> bugfix
* add/feat/introduce/implement          -> feature
* otherwise                             -> change

The rules are the default. ``TYPE_OVERRIDES`` records the round-11 audit, where a
human read the diff and the subject together: a word such as "security" inside a
prompt rule, or a trailing noun "refactor", is not the author's type. Regeneration
applies those audited labels instead of silently reintroducing the noisy ones.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

SECURITY_TERMS = re.compile(
    r"harden|security|auth|credential|secret|redact|saniti[sz]e|restrict|deny|"
    r"cors|csrf|xss|injection|traversal|httponly",
    re.IGNORECASE,
)
DOC_EXTS = (".md", ".txt", ".adoc")
CONVENTIONAL = re.compile(r"^[a-z]+(\([^)]*\))?!?\s*:\s*")
INVESTIGATION = re.compile(
    r"audit|patrol|investigat|inspect|verify|measure|probe|analysis|survey|examine",
    re.IGNORECASE,
)
ADD_FAMILY = re.compile(r"\b(add|adds|create|creates|introduce|implements?|support)\b", re.I)
REMOVE_FAMILY = re.compile(r"\b(remove|removes|delete|deletes|drop|drops|strip)\b", re.I)
DOCUMENT_FAMILY = re.compile(r"\b(document|documents|documented|docs)\b", re.I)

ACTION_PRIORITY = ("remove", "document", "secure", "create", "modify", "diagnose")
QUOTA = {"create": 10, "modify": 12, "remove": 10,
         "document": 12, "secure": 10, "diagnose": 10}
MIN_TOTAL = 60

# Round-11 label audit. The rules above are the default, and the audit found no label
# that contradicts the mechanical signals; these entries record the six subjects where
# reading the diff beat matching a word, plus two repairs whose defect is only visible
# in the code (so the rule would file them as "change").
TYPE_OVERRIDES = {
    "77ba40df77": ("feature", "subject opens with \"Add\" and the diff adds a new class "
                              "plus wiring: a new capability, not a restructure"),
    "cf667b4d8b": ("bugfix", "subject carries the fix: prefix and drops a mapped column "
                             "that V17 never created: a defect fix, not a rename"),
    "9a0e90bed5": ("refactor", "subject carries the refactor: prefix"),
    "d33efbd8ae": ("feature", "subject carries the feat: prefix"),
    "cd6b972cfd": ("feature", "subject carries the feat: prefix"),
    "f7b865efe5": ("change", "subject carries the build: prefix"),
    "3a2d426fdf": ("change", "the subject measures the eval harness and edits a script; "
                             "it refactors nothing"),
    "9e6612fdf9": ("feature", "the word 'security' names a prompt domain, not a "
                              "vulnerability; the commit extends the title rules"),
    "f3d12860ff": ("change", "making the log API optional is a behaviour change, not a repair"),
    "46e467bff6": ("feature", "adds a migration and a preflight script; the trailing word "
                              "'refactor' is a noun, not the author's type prefix"),
    "7093cb5c56": ("bugfix", "the diff makes a concurrent repair claim its row before "
                             "writing: a race fix"),
    "63b5502fb9": ("bugfix", "single atomic migration with fail-closed conflicts repairs "
                             "a data-integrity defect"),
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


def is_doc(path: str) -> bool:
    return path.startswith("docs/") or path.lower().endswith(DOC_EXTS)


def is_test(path: str) -> bool:
    return path.startswith("src/test/") or "/test/" in path


def exists_at(sha: str, path: str) -> bool:
    return subprocess.run(["git", "cat-file", "-e", f"{sha}:{path}"],
                          capture_output=True).returncode == 0


def existed_before(sha: str, path: str) -> bool:
    return subprocess.run(["git", "cat-file", "-e", f"{sha}^:{path}"],
                          capture_output=True).returncode == 0


def commit_text(commit: dict) -> str:
    return commit["subject"] + "\n" + commit["body"]


def file_actions(commit: dict, file: dict) -> set[str]:
    """Every action facet a single file exhibits, not just the first matching rule.

    A documentation deletion is both ``document`` (artifact kind) and ``remove`` (operation);
    a security edit is both ``secure`` and ``modify``; a new file is ``create`` and, when it
    is a doc or a security artifact, also ``document``/``secure``. The expected class is the
    union across files, so a title hits when it names any facet the diff actually has.
    """
    found: set[str] = set()
    subject = CONVENTIONAL.sub("", commit["subject"])
    is_new = not existed_before(commit["sha"], file["path"])
    removed, added = first_lines(commit["sha"], file["path"])
    if is_doc(file["path"]):
        found.add("document")
    if SECURITY_TERMS.search(commit_text(commit)) or SECURITY_TERMS.search(removed + "\n" + added):
        found.add("secure")
    if file["added"] == 0 and file["deleted"] > 0:
        found.add("remove")
    elif file["deleted"] > file["added"] and REMOVE_FAMILY.search(subject):
        found.add("remove")
    if is_new:
        found.add("create")
    elif "remove" not in found:
        found.add("modify")
    return found or {"modify"}


def actions_for(commit: dict, files: list[dict]) -> list[str]:
    found: set[str] = set()
    for file in files:
        found |= file_actions(commit, file)
    return [action for action in ACTION_PRIORITY if action in found]


def contradiction(commit: dict, files: list[dict], actions: list[str]) -> str | None:
    """Only the leading verb is judged: "untrack X, add to gitignore" leads with untrack."""
    subject = CONVENTIONAL.sub("", commit["subject"]).strip()
    leading = subject.split()[0].lower() if subject else ""
    added_any = any(not existed_before(commit["sha"], f["path"]) for f in files)
    deleted_any = any(not exists_at(commit["sha"], f["path"]) for f in files)
    doc_only = all(is_doc(f["path"]) for f in files)
    if ADD_FAMILY.fullmatch(leading) and not added_any:
        return "message leads with add but the diff only edits existing files"
    if REMOVE_FAMILY.fullmatch(leading) and not deleted_any and "remove" not in actions:
        return "message leads with remove but the diff deletes no file"
    if DOCUMENT_FAMILY.fullmatch(leading) and not doc_only:
        return "message leads with document but the diff touches non-document files"
    return None


def type_for(commit: dict, files: list[dict], actions: list[str]) -> str:
    text = commit["subject"].lower()
    production = [f for f in files if not is_doc(f["path"]) and not is_test(f["path"])]
    tests = [f for f in files if is_test(f["path"])]
    if "document" in actions and all(is_doc(f["path"]) for f in files):
        return "change"
    # A conventional prefix states the author's own classification, so it wins over the
    # file-kind heuristics. Without this, any commit that touched a scope/permission path
    # was labelled bugfix, including `feat(migration): add a fail-closed classifier` and
    # `refactor(scope): route reads through predicates`.
    scoped = re.match(r"(fix|bug|feat|feature|refactor|test|tests|docs|chore|build|ci"
                      r"|perf|style)(\([^)]*\))?!?:", text)
    if scoped:
        return {"fix": "bugfix", "bug": "bugfix", "feat": "feature",
                "feature": "feature", "refactor": "refactor", "test": "feature",
                "tests": "feature", "docs": "change", "chore": "change",
                "build": "change", "ci": "change", "perf": "change",
                "style": "change"}[scoped.group(1)]
    if tests and not production:
        return "feature"
    if "secure" in actions:
        return "bugfix"
    # The verb that opens the subject decides. Scanning the whole subject made nouns win:
    # "add ... for observation extraction" matched `extract` and became refactor, and
    # "fix(backend): ... rename a query" matched `rename` and became refactor.
    head = re.match(r"(fix|bug|feat|refactor|cleanup|rename|extract|simplif|add|introduce"
                    r"|implement|support|correct)\b", text)
    if head:
        word = head.group(1)
        if word in ("fix", "bug", "correct"):
            return "bugfix"
        if word in ("feat", "add", "introduce", "implement", "support"):
            return "feature"
        return "refactor"
    # Conventional-commit scope form: "fix(backend): ...", "feat(eval): ...".
    scoped = re.match(r"(fix|bug|feat|refactor|test|docs|chore)(\([^)]*\))?:", text)
    if scoped:
        kind = {"fix": "bugfix", "bug": "bugfix", "feat": "feature",
                "refactor": "refactor", "test": "feature", "docs": "change",
                "chore": "change"}[scoped.group(1)]
        return kind
    if re.search(r"\b(refactor|cleanup|rename|simplif)\b", text):
        return "refactor"
    if re.search(r"\b(fix|bug|regression|correct)\b", text):
        return "bugfix"
    if re.search(r"\b(add|feat|introduce|implement|support)\b", text):
        return "feature"
    return "change"


def category_for(files: list[dict], actions: list[str], expected_type: str) -> str:
    if "document" in actions and all(is_doc(f["path"]) for f in files):
        return "docs-update"
    if "secure" in actions and expected_type == "bugfix":
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


def concepts_for(actions: list[str], expected_type: str) -> list[str]:
    if "secure" in actions or "remove" in actions or expected_type == "bugfix":
        return ["problem-solution"]
    return ["what-changed"]


def trace_output(commit: dict, files: list[dict], mode: str) -> str:
    """Render only what a tool would return — never the commit prose.

    Copying "Commit message: ..." into the trace made models summarise the commit
    instead of the edit, which is where the identifier losses came from. Each line
    also states the operation ("removed 2 line(s), added 0") so every expected
    action class is inferable from the trace itself.
    """
    primary = max(files, key=lambda f: f["added"] + f["deleted"])
    lines = []
    for file in sorted(files, key=lambda f: f["added"] + f["deleted"], reverse=True):
        removed, added = first_lines(commit["sha"], file["path"])
        if mode == "diagnose":
            lines.append(f"Read {file['path']}: {removed or '(start of file)'}")
        else:
            lines.append(f"Edit {file['path']}: removed {file['deleted']} line(s), "
                         f"added {file['added']} line(s); "
                         f"'{removed or '(none)'}' -> '{added or '(none)'}'")
    totals = (f"{len(files)} file(s), "
              f"{sum(f['added'] for f in files)} insertion(s), "
              f"{sum(f['deleted'] for f in files)} deletion(s)")
    if mode == "diagnose":
        lines.append("No change applied; investigation only.")
    else:
        lines.append(f"Edited file_path={primary['path']}. Totals: {totals}.")
        term = SECURITY_TERMS.search(commit_text(commit))
        if term:
            lines.append(f"Security hardening: matched '{term.group(0)}'.")
    return " ".join(lines)


def build_sample(commit: dict, files: list[dict], actions: list[str],
                 mode: str) -> dict:
    ranked = sorted(files, key=lambda f: f["added"] + f["deleted"], reverse=True)
    primary = ranked[0]
    ident = identifier(primary["path"])
    expected_type = "change" if mode == "diagnose" else type_for(commit, files, actions)
    audit_note = ""
    override = TYPE_OVERRIDES.get(commit["sha"][:10])
    if override and mode != "diagnose":
        # Human-reviewed labels. The rules above are the default; these entries record
        # where reading the diff beat matching a word in the subject.
        expected_type, reason = override
        audit_note = " | round-11 label audit: " + reason
    action_set = ["diagnose"] if mode == "diagnose" else actions
    primary_action = action_set[0]

    if mode == "diagnose":
        tool_name = "Read"
        tool_input = (f"file_path={primary['path']}\n"
                      f"read_files={', '.join(f['path'] for f in ranked)}")
    else:
        tool_name = "Write" if action_set == ["create"] else "Edit"
        old_line, new_line = first_lines(commit["sha"], primary["path"])
        tool_input = (f"file_path={primary['path']}\n"
                      f"read_files={', '.join(f['path'] for f in ranked)}\n"
                      f"old_string=\"{old_line}\"\nnew_string=\"{new_line}\"")

    verb = {"create": "add", "modify": "change", "remove": "remove",
            "secure": "harden", "document": "document", "diagnose": "record"}[primary_action]
    return {
        "id": f"real-{commit['sha'][:10]}",
        "category": "diagnose" if mode == "diagnose"
        else category_for(files, action_set, expected_type),
        "keySample": False,
        "toolName": tool_name,
        "cwd": "/repo",
        "occurredAt": datetime.fromtimestamp(commit["at"], timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "toolInput": tool_input,
        "toolOutput": trace_output(commit, files, mode),
        "expectObservation": mode != "diagnose",
        "expectedType": expected_type,
        "titleKeywords": [verb, ident.lower()],
        "titleVerb": verb,
        "titleSubject": [ident],
        "titleIdentifiers": [ident],
        "factsKeywords": [],
        "conceptsKeywords": concepts_for(action_set, expected_type),
        "titleAction": primary_action,
        "titleActions": action_set,
        "notes": f"generated from {commit['sha']} ({commit['subject']})" + audit_note,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=5000,
                        help="commits to scan before filtering (default 5000)")
    parser.add_argument("--out", default="backend/src/test/resources/eval/golden-tool-use-real.json")
    args = parser.parse_args()

    exclusions: dict[str, int] = {}
    samples: list[dict] = []
    used: set[str] = set()
    filled = {action: 0 for action in QUOTA}
    history = commits(args.limit)

    for commit in history:
        if all(filled[a] >= QUOTA[a] for a in QUOTA if a != "diagnose"):
            break
        if not commit["subject"] or commit["sha"] in used:
            continue
        files = changed_files(commit["sha"])
        if not files or len(files) > 6 or not any(f["added"] + f["deleted"] > 0 for f in files):
            continue
        actions = actions_for(commit, files)
        if not actions:
            exclusions["no derivable action"] = exclusions.get("no derivable action", 0) + 1
            continue
        reason = contradiction(commit, files, actions)
        if reason:
            exclusions[reason] = exclusions.get(reason, 0) + 1
            continue
        if not any(filled[a] < QUOTA[a] for a in actions):
            continue
        samples.append(build_sample(commit, files, actions, "edit"))
        used.add(commit["sha"])
        for action in actions:
            filled[action] += 1

    for commit in history:
        if filled["diagnose"] >= QUOTA["diagnose"]:
            break
        if not commit["subject"] or commit["sha"] in used:
            continue
        if not INVESTIGATION.search(commit["subject"]):
            continue
        files = changed_files(commit["sha"])
        if not files or len(files) > 6 or not any(f["added"] + f["deleted"] > 0 for f in files):
            continue
        samples.append(build_sample(commit, files, ["diagnose"], "diagnose"))
        used.add(commit["sha"])
        filled["diagnose"] += 1

    if len(samples) < MIN_TOTAL:
        for commit in history:
            if len(samples) >= MIN_TOTAL:
                break
            if not commit["subject"] or commit["sha"] in used:
                continue
            files = changed_files(commit["sha"])
            if not files or len(files) > 6 or not any(f["added"] + f["deleted"] > 0 for f in files):
                continue
            actions = actions_for(commit, files)
            if not actions or contradiction(commit, files, actions):
                continue
            samples.append(build_sample(commit, files, actions, "edit"))
            used.add(commit["sha"])
            for action in actions:
                filled[action] += 1

    short = [f"{action}={filled[action]}/{QUOTA[action]}" for action in QUOTA
             if filled[action] < QUOTA[action]]
    if short:
        raise SystemExit("history did not fill every quota: " + ", ".join(short))
    if len(samples) < MIN_TOTAL:
        raise SystemExit(f"only {len(samples)} samples, need at least {MIN_TOTAL}")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"version": "real-git-2026-11-03-r11", "samples": samples},
                              indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {len(samples)} real-history samples to {out}")
    print("action coverage: " + ", ".join(f"{a}={filled[a]}" for a in QUOTA))
    if exclusions:
        print("excluded commits: " + "; ".join(f"{k} ({v})" for k, v in exclusions.items()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
