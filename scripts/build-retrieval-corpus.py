#!/usr/bin/env python3
"""Build a retrieval-quality corpus from real Claude Code session transcripts.

The extraction-quality track already uses real history for model evaluation; this
script covers the other half of the pipeline, retrieval. It reads the session
transcripts on this machine (`~/.claude/projects/*/*.jsonl`) and turns each
substantive user request into a query whose expected observations are the memory
records the assistant produced while answering it.

Ground truth is temporal attribution, not lexical overlap:

  * a query is a real user prompt (verbatim, whitespace-collapsed);
  * its expected observations are the Edit/Write/MultiEdit tool calls made by the
    assistant between that prompt and the next user prompt in the same session;
  * the rationale is written into the corpus so the labeling rule is auditable.

That rule answers the question retrieval is actually asked: "which memory records
did this request produce?" It is not circular with the text index, because the
prompt and the observation are different text, often in different languages
(Chinese request, English code change).

Usage:
    python3 scripts/build-retrieval-corpus.py --output \
        backend/src/test/resources/eval/retrieval-corpus-real.json
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
from pathlib import Path

TRANSCRIPT_ROOT = Path(os.path.expanduser("~/.claude/projects"))
EDIT_TOOLS = {"Edit", "Write", "MultiEdit", "NotebookEdit"}
MAX_NARRATIVE = 600
MIN_PROMPT_CHARS = 30
MIN_NARRATIVE_CHARS = 20
MAX_RELEVANT = 4
SKIP_PREFIXES = (
    "<command-name>", "<local-command", "<bash-input>", "Caveat:", "This session is",
    "[Request interrupted", "<system-reminder>", "<task-notification>",
    "A session-scoped Stop hook", "<summary>", "[SYSTEM NOTIFICATION",
)


CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
LATIN_WORD = re.compile(r"[A-Za-z]{2,}")


def query_language(text: str) -> str:
    """Label the query so the evaluation can report Chinese vs English/mixed.

    A request is `zh` when it is written in Chinese, `en` when it has no CJK
    characters, and `mixed` when it carries both a Chinese sentence and Latin
    identifiers (the common shape in this history: Chinese instruction, English
    file or class names).
    """
    cjk = len(CJK.findall(text))
    latin = LATIN_WORD.findall(text)
    if cjk == 0:
        return "en" if len(latin) >= 3 else "unknown"
    return "mixed" if len(latin) >= 3 else "zh"


def parse_epoch(timestamp: str | None) -> int | None:
    if not timestamp:
        return None
    try:
        return int(dt.datetime.fromisoformat(timestamp.replace("Z", "+00:00")).timestamp() * 1000)
    except ValueError:
        return None


def clean(text: str, limit: int) -> str:
    text = re.sub(r"\x1b\[[0-9;]*m", "", text)
    # Keep the inner instruction but drop transcript wrappers that would
    # otherwise dominate the query embedding.
    text = re.sub(r"</?pasted_content[^>]*>", "", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = text.strip()
    return text[:limit]


def is_user_prompt(entry: dict) -> str | None:
    message = entry.get("message")
    if not isinstance(message, dict) or message.get("role") != "user":
        return None
    content = message.get("content")
    if not isinstance(content, str):
        return None  # tool results and list content are not requests
    text = content.strip()
    if len(text) < MIN_PROMPT_CHARS:
        return None
    if text.startswith(SKIP_PREFIXES):
        return None
    if "tool_use_id" in text:
        return None
    return clean(text, 300)


def observation_from_tool(tool: dict, session: str, turn: int, index: int, project: str,
                          observed_at: int | None) -> dict | None:
    payload = tool.get("input") or {}
    path = payload.get("file_path") or payload.get("notebook_path") or payload.get("path")
    if not path:
        return None
    tool_name = tool.get("name", "Edit")
    snippet = payload.get("new_string")
    if not snippet and isinstance(payload.get("edits"), list):
        snippet = "\n".join(
            str(edit.get("new_string", "")) for edit in payload["edits"] if isinstance(edit, dict))
    if not snippet:
        snippet = payload.get("content")
    narrative = clean(str(snippet or ""), MAX_NARRATIVE)
    if len(narrative) < MIN_NARRATIVE_CHARS:
        return None
    basename = os.path.basename(str(path))
    verb = "Created" if tool_name == "Write" else "Changed"
    return {
        "id": f"obs-{session[:8]}-{turn}-{index}",
        "title": f"{verb} {basename}",
        "narrative": narrative,
        "facts": [
            f"{tool_name} {path}",
            f"project {project}",
        ],
        "session": session,
        "project": project,
        "file": str(path),
        # Real transcript time, so recency reranking is measured on real ages
        # instead of row order.
        "observed_at": observed_at,
    }


def read_session(path: Path) -> tuple[list[dict], list[dict]]:
    """Return (observations, queries) for one transcript file."""
    session = path.stem.split("-")[0]
    observations: list[dict] = []
    queries: list[dict] = []
    turn = 0
    pending_prompt: str | None = None
    pending_prompt_at: int | None = None
    last_timestamp: int | None = None
    seen_observation = 0

    def close_turn() -> None:
        nonlocal pending_prompt, pending_prompt_at, seen_observation
        if not pending_prompt:
            return
        turn_observations = [obs for obs in observations if obs["turn_start"] == turn]
        if 1 <= len(turn_observations) <= MAX_RELEVANT:
            queries.append({
                "id": f"q-{session[:8]}-{turn}",
                "text": pending_prompt,
                "language": query_language(pending_prompt),
                # When the request was made. The evaluation uses it as its clock:
                # observations created after the answer did not exist yet, and
                # recency is measured relative to this moment.
                "asked_at": pending_prompt_at,
                "relevant": [obs["id"] for obs in turn_observations],
                "rationale": (
                    "Temporal attribution: these observations were produced by the "
                    f"assistant while answering this prompt in session {session}, "
                    f"turn {turn} (transcript {path.name})."
                ),
                "session": session,
                "turn": turn,
            })
        pending_prompt = None
        pending_prompt_at = None

    for line in path.open(errors="replace"):
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            continue
        timestamp = parse_epoch(entry.get("timestamp")) or last_timestamp
        if timestamp:
            last_timestamp = timestamp
        prompt = is_user_prompt(entry)
        if prompt:
            close_turn()
            turn += 1
            pending_prompt = prompt
            pending_prompt_at = timestamp
            continue
        if not pending_prompt:
            continue
        message = entry.get("message")
        if not isinstance(message, dict) or message.get("role") != "assistant":
            continue
        content = message.get("content")
        if not isinstance(content, list):
            continue
        for block in content:
            if not isinstance(block, dict) or block.get("type") != "tool_use":
                continue
            if block.get("name") not in EDIT_TOOLS:
                continue
            project = entry.get("cwd") or ""
            observation = observation_from_tool(
                block, session, turn, seen_observation, project, timestamp)
            if observation:
                seen_observation += 1
                observation["turn_start"] = turn
                observations.append(observation)
    close_turn()

    for observation in observations:
        observation.pop("turn_start", None)
    return observations, queries


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        default="backend/src/test/resources/eval/retrieval-corpus-real.json",
    )
    parser.add_argument("--max-queries", type=int, default=60)
    parser.add_argument(
        "--english-queries", type=int, default=30,
        help="Extra queries labelled en/mixed, spread across projects (round 16 contrast set)")
    args = parser.parse_args()

    if not TRANSCRIPT_ROOT.exists():
        raise SystemExit(f"no transcripts at {TRANSCRIPT_ROOT}")

    transcripts = sorted(
        (path for path in TRANSCRIPT_ROOT.glob("*/**/*.jsonl")
         if "subagents" not in path.parts and path.stat().st_size > 20_000),
        key=lambda p: p.stat().st_size,
        reverse=True,
    )

    all_observations: dict[str, dict] = {}
    all_queries: list[dict] = []
    for path in transcripts:
        observations, queries = read_session(path)
        for observation in observations:
            all_observations.setdefault(observation["id"], observation)
        for query in queries:
            if query["text"].lower() in {existing["text"].lower() for existing in all_queries}:
                continue
            all_queries.append(query)

    # Keep only queries whose expected set survived deduplication, then take a
    # spread across projects instead of 60 queries from one repository.
    valid = [query for query in all_queries if all(obs in all_observations for obs in query["relevant"])]
    by_project: dict[str, list[dict]] = {}
    for query in valid:
        session_project = next(
            (all_observations[obs]["project"] for obs in query["relevant"]), "unknown")
        by_project.setdefault(session_project, []).append(query)
    selected: list[dict] = []
    while len(selected) < args.max_queries and any(by_project.values()):
        for project in sorted(by_project):
            bucket = by_project[project]
            if bucket and len(selected) < args.max_queries:
                selected.append(bucket.pop(0))
        by_project = {key: value for key, value in by_project.items() if value}

    # Contrast set: requests written in English or in Chinese with English
    # identifiers. Round 15's corpus was almost all Chinese/mixed, so the two
    # halves of the fused path were never compared on the same query shape.
    selected_ids = {query["id"] for query in selected}
    extras: list[dict] = []
    if args.english_queries > 0:
        extras_by_project: dict[str, list[dict]] = {}
        for query in valid:
            if query["id"] in selected_ids or query["language"] == "zh":
                continue
            project = next(
                (all_observations[obs]["project"] for obs in query["relevant"]), "unknown")
            extras_by_project.setdefault(project, []).append(query)
        while len(extras) < args.english_queries and any(extras_by_project.values()):
            for project in sorted(extras_by_project):
                bucket = extras_by_project[project]
                if bucket and len(extras) < args.english_queries:
                    extras.append(bucket.pop(0))
            extras_by_project = {key: value for key, value in extras_by_project.items() if value}
        if len(extras) < args.english_queries:
            print(f"warning: only {len(extras)} en/mixed queries available, "
                  f"asked for {args.english_queries}")
        selected.extend(extras)

    selected_ids = {obs for query in selected for obs in query["relevant"]}
    languages: dict[str, int] = {}
    for query in selected:
        languages[query["language"]] = languages.get(query["language"], 0) + 1
    corpus = {
        "version": "retrieval-real-2026-11-04",
        "note": (
            "Queries and expected observations are taken from real Claude Code session "
            "transcripts. Relevance is temporal: the observations were produced while the "
            "assistant answered the query. A review pass dropped system-generated turns "
            "(task notifications, hook banners) and transcript wrappers; every query keeps "
            "its rationale so the attribution can be re-checked. Each query carries a "
            "language label (zh / en / mixed) and each observation its real transcript "
            "timestamp, so quality and recency reranking can be reported per dimension."
        ),
        "source": str(TRANSCRIPT_ROOT),
        "observations": [all_observations[obs] for obs in sorted(selected_ids)],
        "queries": selected,
    }
    print(f"query languages: {dict(sorted(languages.items()))}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(corpus, ensure_ascii=False, indent=2) + "\n")
    print(f"observations={len(corpus['observations'])} queries={len(corpus['queries'])}")
    print(f"corpus written to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
