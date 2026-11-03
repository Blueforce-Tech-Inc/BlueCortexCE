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
#   EVAL_PROVIDER=anthropic ./scripts/eval-model-quality.sh
#   EVAL_LIMIT=20 EVAL_MAX_CALLS=20 ./scripts/eval-model-quality.sh
#   EVAL_PHASE=after EVAL_VALIDATE=1 EVAL_FIXTURE_OUT=/tmp/longcat-after.json ./scripts/eval-model-quality.sh
#   EVAL_PHASE=before EVAL_VALIDATE=0 \
#     EVAL_PROMPT_DIR=backend/src/test/resources/eval/prompts-baseline ./scripts/eval-model-quality.sh
#   EVAL_FIXTURE_IN=/tmp/longcat-after.json ./scripts/eval-model-quality.sh   # offline replay
#
# EVAL_MAX_CALLS defaults to 0 (unlimited). Failed calls are never retried.
# Key samples are repeated EVAL_KEY_REPEAT times (default 3).
# EVAL_REPEAT_ALL=1 repeats every sample EVAL_KEY_REPEAT times to measure run-to-run
# variance and list samples whose type/action changed between runs.
# EVAL_GOLDEN=real scores the mechanically generated real-history set
# (backend/src/test/resources/eval/golden-tool-use-real.json) instead of the
# hand-written set. Keep the two reports separate; they are different distributions.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -n "${CI:-}" || -n "${GITHUB_ACTIONS:-}" ]]; then
  echo "Refusing to call a real model inside CI. Unset CI/GITHUB_ACTIONS for a manual run:" >&2
  echo "  env -u CI -u GITHUB_ACTIONS ./scripts/eval-model-quality.sh" >&2
  exit 2
fi

if [[ ! -f "$ROOT/backend/.env" ]]; then
  echo "backend/.env is required (OPENAI_* and/or ANTHROPIC_* provider variables)." >&2
  exit 2
fi

set -a
# shellcheck disable=SC1091
. "$ROOT/backend/.env"
set +a

EVAL_PROVIDER="${EVAL_PROVIDER:-openai}"
if [[ "$EVAL_PROVIDER" == "anthropic" ]]; then
  required=(ANTHROPIC_API_KEY ANTHROPIC_BASE_URL ANTHROPIC_MODEL)
else
  required=(OPENAI_API_KEY OPENAI_BASE_URL OPENAI_MODEL)
fi
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "$name is empty; refusing to run the real evaluation." >&2
    exit 2
  fi
done

export REAL_LLM_EVAL=1
export EVAL_PROVIDER
export EVAL_MAX_CALLS="${EVAL_MAX_CALLS:-0}"
export EVAL_REPORT="${EVAL_REPORT:-$ROOT/docs/archive/$(date +%F)_model-output-quality-eval.md}"

if [[ "$EVAL_PROVIDER" == "anthropic" ]]; then
  echo "provider=anthropic model=$ANTHROPIC_MODEL base_url=$ANTHROPIC_BASE_URL max_calls=$EVAL_MAX_CALLS phase=${EVAL_PHASE:-current} report=$EVAL_REPORT"
else
  echo "provider=openai model=$OPENAI_MODEL base_url=$OPENAI_BASE_URL max_calls=$EVAL_MAX_CALLS phase=${EVAL_PHASE:-current} report=$EVAL_REPORT"
fi

cd "$ROOT/backend"
mvn -q -DskipTests test-compile \
  dependency:build-classpath \
  -Dmdep.outputFile="$ROOT/backend/target/eval-classpath.txt" \
  -Dmdep.includeScope=test

cd "$ROOT"
exec java -cp "$ROOT/backend/target/test-classes:$ROOT/backend/target/classes:$(cat "$ROOT/backend/target/eval-classpath.txt")" \
  com.ablueforce.cortexce.eval.GoldenSetEvaluator
