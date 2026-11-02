#!/usr/bin/env bash
# Manual-only model-output quality evaluation.
#
# Calls a real OpenAI-compatible chat provider (LongCat by default) against the
# golden tool-use set, scores the answers with the production XmlParser, and
# writes a Markdown report. Never runs in CI: it refuses when CI is set, and the
# Java entry point enforces the same rule plus a hard call cap.
#
# Usage:
#   env -u CI -u GITHUB_ACTIONS ./scripts/eval-model-quality.sh
#   EVAL_MAX_CALLS=10 EVAL_LIMIT=10 ./scripts/eval-model-quality.sh
#   EVAL_REPORT=/tmp/eval.md ./scripts/eval-model-quality.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -n "${CI:-}" || -n "${GITHUB_ACTIONS:-}" ]]; then
  echo "Refusing to call a real model inside CI. Unset CI/GITHUB_ACTIONS for a manual run:" >&2
  echo "  env -u CI -u GITHUB_ACTIONS ./scripts/eval-model-quality.sh" >&2
  exit 2
fi

if [[ ! -f "$ROOT/backend/.env" ]]; then
  echo "backend/.env is required (OPENAI_API_KEY, OPENAI_BASE_URL, OPENAI_MODEL)." >&2
  exit 2
fi

set -a
# shellcheck disable=SC1091
. "$ROOT/backend/.env"
set +a

for name in OPENAI_API_KEY OPENAI_BASE_URL OPENAI_MODEL; do
  if [[ -z "${!name:-}" ]]; then
    echo "$name is empty; refusing to run the real evaluation." >&2
    exit 2
  fi
done

export REAL_LLM_EVAL=1
export EVAL_MAX_CALLS="${EVAL_MAX_CALLS:-20}"
export EVAL_REPORT="${EVAL_REPORT:-$ROOT/docs/archive/$(date +%F)_model-output-quality-eval.md}"

echo "model=$OPENAI_MODEL base_url=$OPENAI_BASE_URL max_calls=$EVAL_MAX_CALLS report=$EVAL_REPORT"

cd "$ROOT/backend"
mvn -q -DskipTests test-compile \
  dependency:build-classpath \
  -Dmdep.outputFile="$ROOT/backend/target/eval-classpath.txt" \
  -Dmdep.includeScope=test

cd "$ROOT"
exec java -cp "$ROOT/backend/target/test-classes:$ROOT/backend/target/classes:$(cat "$ROOT/backend/target/eval-classpath.txt")" \
  com.ablueforce.cortexce.eval.GoldenSetEvaluator
