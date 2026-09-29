#!/usr/bin/env bash
# Produce the repository's deterministic code fingerprint.
#
# The scope and manifest format are versioned here. Do not replace this
# algorithm with an ad-hoc hash in a scheduled task: the fingerprint is used
# across independent task invocations to decide whether acceptance tests must
# run again.

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

readonly FINGERPRINT_VERSION=1
readonly MANIFEST_FORMAT="v1:file-path-and-content-sha256"

# Documentation, reports, local memory, build output, and dependency caches
# are intentionally outside this scope. Directory entries include tracked
# and non-ignored untracked files; Git still reports deleted tracked files.
CODE_PATHS=(
  backend
  webui
  cortex-mem-spring-integration
  go-sdk
  python-sdk
  js-sdk
  examples
  admin-panel
  codex-watcher
  idp-core
  idp-demo
  proxy
  openclaw-plugin
  scripts
  ScreenPulse
  conan
  ocs
  public
  .github
  Dockerfile
  docker-compose.yml
  docker-compose.yaml
  pom.xml
  package.json
  package-lock.json
)

for root_package in package*.json; do
  if [[ -f "$root_package" ]]; then
    CODE_PATHS+=("$root_package")
  fi
done

manifest_file="$(mktemp "${TMPDIR:-/tmp}/cortexce-code-fingerprint.XXXXXX")"
trap 'rm -f "$manifest_file"' EXIT

record_count=0

append_record() {
  local kind="$1"
  local path="$2"
  local digest="${3:-}"
  # NUL-delimited records make ordering deterministic without ambiguity from
  # spaces, tabs, or other characters allowed in a Unix path.
  printf '%s\t%s\t%s\0' "$kind" "$path" "$digest" >>"$manifest_file"
  record_count=$((record_count + 1))
}

append_file_record() {
  local base="$1"
  local path="$2"
  local relative="$3"
  local full_path="$base/$path"

  if [[ -f "$full_path" || -L "$full_path" ]]; then
    append_record FILE "$relative" "$(shasum -a 256 -- "$full_path" | awk '{print $1}')"
  elif [[ ! -e "$full_path" ]]; then
    append_record DELETED "$relative"
  fi
}

is_code_path() {
  case "$1" in
    *.md|*.mdx|docs/*|*/docs/*)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

# The parent repository stores a submodule as a single gitlink. Include both
# its commit and its current nested worktree files so local WebUI edits cannot
# evade change detection.
append_submodule_records() {
  local submodule="$1"
  local nested_root="$ROOT/$submodule"

  if [[ ! -d "$nested_root" ]] || ! git -C "$nested_root" rev-parse --show-toplevel >/dev/null 2>&1; then
    append_record SUBMODULE_MISSING "$submodule"
    return
  fi

  append_record SUBMODULE_HEAD "$submodule" "$(git -C "$nested_root" rev-parse HEAD)"
  while IFS= read -r -d '' nested_path; do
    if is_code_path "$nested_path"; then
      append_file_record "$nested_root" "$nested_path" "$submodule/$nested_path"
    fi
  done < <(git -C "$nested_root" ls-files -z -co --exclude-standard -- .)
}

while IFS= read -r -d '' path; do
  if [[ "$path" == "webui" ]]; then
    append_submodule_records webui
  elif is_code_path "$path"; then
    append_file_record "$ROOT" "$path" "$path"
  fi
done < <(git ls-files -z -co --exclude-standard -- "${CODE_PATHS[@]}")

sorted_manifest="$(mktemp "${TMPDIR:-/tmp}/cortexce-code-fingerprint-sorted.XXXXXX")"
trap 'rm -f "$manifest_file" "$sorted_manifest"' EXIT
LC_ALL=C sort -z "$manifest_file" >"$sorted_manifest"

fingerprint="$({
  printf 'fingerprint_version=%s\0' "$FINGERPRINT_VERSION"
  printf 'manifest_format=%s\0' "$MANIFEST_FORMAT"
  cat "$sorted_manifest"
} | shasum -a 256 | awk '{print $1}')"

printf 'CODE_FINGERPRINT_VERSION=%s\n' "$FINGERPRINT_VERSION"
printf 'CODE_FINGERPRINT=%s\n' "$fingerprint"
printf 'CODE_RECORD_COUNT=%s\n' "$record_count"
