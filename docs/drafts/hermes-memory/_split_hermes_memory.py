#!/usr/bin/env python3
"""
Regenerate aspect-split docs from docs/drafts/hermes-memory-analysis.md

- Fence-aware: ## inside ``` fences are NOT section boundaries
- Max 48KiB per file (safety margin under 50KB limit)
- Concatenation of all parts must equal source (lossless)
"""
from __future__ import annotations

import hashlib
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]  # docs/drafts
SRC = ROOT / "hermes-memory-analysis.md"
OUT_DIR = ROOT / "hermes-memory"
MAX_BYTES = 48 * 1024

# English filenames only (order must match chunk order from fence_aware + packing).
# If chunk count changes, update this list and re-verify against monolith source.
CHUNK_ENGLISH_NAMES: list[tuple[str, str]] = [
    ("00-overview", "01-architecture-positioning-and-toc.md"),
    ("20-recommendations", "02-bluecortexce-recommendations.md"),
    ("40-context-compression", "03-memory-context-injection-and-prefetch-lifecycle.md"),
    ("50-honcho-holographic-deep", "04-honcho-four-tools-routing.md"),
    ("50-honcho-holographic-deep", "05-multimodal-memory-clarification.md"),
    ("60-evolution", "06-memory-provider-hooks-inventory.md"),
    ("60-evolution", "07-bluecortexce-contradiction-detection-plan.md"),
    ("60-evolution", "08-builtin-memory-tool-bounded-snapshot.md"),
    ("60-evolution", "09-supermemory-capture-lifecycle.md"),
    ("60-evolution", "10-holographic-hrr-implementation.md"),
]


def slugify(title: str, max_len: int = 44) -> str:
    title = re.sub(r"\s+", " ", title).strip()
    title = title.split("（")[0].split("(")[0].strip()
    s = re.sub(r"[^\w\u4e00-\u9fff\-]+", "-", title)
    s = re.sub(r"-+", "-", s).strip("-").lower()
    return (s[:max_len] or "section").rstrip("-")


def fence_aware_sections(text: str) -> list[str]:
    """Split into [preamble, sec2, sec3, ...] where each sec starts with '## ' outside fences."""
    lines = text.splitlines(keepends=True)
    in_fence = False
    parts: list[str] = []
    buf: list[str] = []

    def flush():
        nonlocal buf
        if buf:
            parts.append("".join(buf))
            buf = []

    for line in lines:
        stripped = line.strip()
        if stripped.startswith("```"):
            in_fence = not in_fence
            buf.append(line)
            continue
        if not in_fence and line.startswith("## ") and not line.startswith("###"):
            flush()
            buf.append(line)
        else:
            buf.append(line)
    flush()
    return parts


def split_oversized(section: str) -> list[str]:
    """If section > MAX_BYTES, split on ### / #### / line chunks (outside fences)."""
    if len(section.encode("utf-8")) <= MAX_BYTES:
        return [section]

    lines = section.splitlines(keepends=True)
    in_fence = False
    subs: list[str] = []
    buf: list[str] = []

    def flush():
        nonlocal buf
        if buf:
            subs.append("".join(buf))
            buf = []

    for line in lines:
        st = line.strip()
        if st.startswith("```"):
            in_fence = not in_fence
            buf.append(line)
            continue
        if not in_fence and line.startswith("### ") and not line.startswith("####"):
            flush()
            buf.append(line)
        else:
            buf.append(line)
    flush()

    if len(subs) <= 1:
        # try ####
        lines = section.splitlines(keepends=True)
        in_fence = False
        subs = []
        buf = []
        for line in lines:
            st = line.strip()
            if st.startswith("```"):
                in_fence = not in_fence
                buf.append(line)
                continue
            if not in_fence and line.startswith("#### "):
                flush()
                buf.append(line)
            else:
                buf.append(line)
        flush()

    out: list[str] = []
    for s in subs:
        if len(s.encode("utf-8")) <= MAX_BYTES:
            out.append(s)
            continue
        # hard line split
        lines = s.splitlines(keepends=True)
        chunk: list[str] = []
        cb = 0
        for line in lines:
            lb = len(line.encode("utf-8"))
            if chunk and cb + lb > MAX_BYTES:
                out.append("".join(chunk))
                chunk = [line]
                cb = lb
            else:
                chunk.append(line)
                cb += lb
        if chunk:
            out.append("".join(chunk))
    return out


def first_heading(chunk: str) -> str:
    for line in chunk.splitlines():
        if line.startswith("## ") and not line.startswith("###"):
            return line[3:].strip()
    if chunk.lstrip().startswith("# "):
        return chunk.lstrip()[2:].split("\n", 1)[0].strip()
    return "fragment"


def aspect_prefix(title: str) -> str:
    """Map section title to stable aspect folder prefix for AI navigation."""
    if "附录" in title:
        return "90-appendix"
    if "待进一步" in title or "下轮计划" in title:
        return "95-meta"
    t = title.lower()
    if "critical context" in t:
        return "40-context-compression"
    if "目录" in title or "架构定位" in title:
        return "00-overview"
    m = re.match(r"^(\d+)\.", title.strip())
    if m:
        num = int(m.group(1))
        if num <= 6:
            return "10-core-memory"
        if num <= 10:
            return "20-recommendations"
        if num <= 14:
            return "30-providers-and-routing"
        if num <= 19:
            return "40-context-compression"
        if num <= 30:
            return "50-honcho-holographic-deep"
        return "60-evolution"
    return "99-misc"


def main() -> None:
    text = SRC.read_text(encoding="utf-8")
    sections = fence_aware_sections(text)
    atomic: list[str] = []
    for sec in sections:
        atomic.extend(split_oversized(sec))

    chunks: list[str] = []
    buf = ""
    for sec in atomic:
        cand = buf + sec if buf else sec
        if len(cand.encode("utf-8")) > MAX_BYTES and buf:
            chunks.append(buf)
            buf = sec
        else:
            buf = cand
    if buf:
        chunks.append(buf)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    keep = {"index.md", "misc.md", "staging.md", "README.md", "AGENT.md"}
    for p in OUT_DIR.rglob("*.md"):
        if p.name in keep:
            continue
        p.unlink()
    for p in OUT_DIR.rglob("*"):
        if p.is_dir() and not any(p.iterdir()):
            p.rmdir()

    if len(chunks) != len(CHUNK_ENGLISH_NAMES):
        raise RuntimeError(
            f"Chunk count {len(chunks)} != {len(CHUNK_ENGLISH_NAMES)} — update CHUNK_ENGLISH_NAMES"
        )

    manifest: list[tuple[str, str, int]] = []
    for i, chunk in enumerate(chunks, 1):
        title = first_heading(chunk)
        asp, basename = CHUNK_ENGLISH_NAMES[i - 1]
        fname = f"{asp}/{basename}"
        p = OUT_DIR / fname
        p.parent.mkdir(parents=True, exist_ok=True)
        header = f"<!-- split {i}/{len(chunks)} | aspect:{asp} | ≤50KB -->\n\n"
        p.write_text(header + chunk, encoding="utf-8")
        manifest.append((fname, title, p.stat().st_size))

    joined = "".join(chunks)
    assert joined == text, "lossless join failed"

    # Write manifest for self-check
    (OUT_DIR / "_manifest.txt").write_text(
        "\n".join(f"{a}\t{b}\t{c}" for a, b, c in manifest) + "\n",
        encoding="utf-8",
    )
    print("OK parts:", len(chunks), "max_bytes:", max(c for _, _, c in manifest))
    print("sha256:", hashlib.sha256(text.encode("utf-8")).hexdigest())


if __name__ == "__main__":
    main()