#!/usr/bin/env bash
# Check append-heavy project documents and report when compaction/archival is
# required. This script is intentionally read-only; the maintenance agent
# decides which resolved history can be safely archived.

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

readonly MAX_LINES="${MAX_LINES:-1500}"
readonly MAX_BYTES="${MAX_BYTES:-150000}"

DOCS=(
  docs/drafts/health-check-task.md
  docs/drafts/backend-review-findings.md
  docs/drafts/backend-fix-progress.md
  docs/drafts/patrol-task.md
  docs/drafts/doc-review-task.md
)

needs_action=0
for doc in "${DOCS[@]}"; do
  if [[ ! -f "$doc" ]]; then
    printf 'DOC_MISSING path=%s\n' "$doc"
    needs_action=1
    continue
  fi

  lines="$(wc -l <"$doc" | tr -d ' ')"
  bytes="$(wc -c <"$doc" | tr -d ' ')"
  status=OK
  if (( lines > MAX_LINES || bytes > MAX_BYTES )); then
    status=COMPACTION_REQUIRED
    needs_action=1
  fi
  printf 'DOC_STATUS path=%s lines=%s bytes=%s status=%s\n' "$doc" "$lines" "$bytes" "$status"
done

if (( needs_action )); then
  exit 2
fi
