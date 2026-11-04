#!/bin/bash
#
# Export memories from Java backend
# Usage: ./export-memories.sh [--query QUERY] [--project PROJECT] [--output FILE] [--limit N]
#
# Options:
#   --query QUERY    Search query (default: *)
#   --project PROJECT Filter by project path
#   --output FILE    Output file (default: memories-export.json)
#   --limit N        Max results per table (default: 1000)
#   --help          Show this help message
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_URL="${CLAUDE_MEM_API_URL:-http://127.0.0.1:37777}"
OUTPUT_FILE=""
QUERY="*"
PROJECT=""
LIMIT=1000

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --query)
            QUERY="$2"
            shift 2
            ;;
        --project)
            PROJECT="$2"
            shift 2
            ;;
        --output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        --limit)
            LIMIT="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --query QUERY    Search query (default: *)"
            echo "  --project PROJECT Filter by project path"
            echo "  --output FILE    Output file (default: memories-export.json)"
            echo "  --limit N        Max results per table (default: 1000)"
            echo ""
            echo "Examples:"
            echo "  $0 --query 'feature implementation'"
            echo "  $0 --project /path/to/project --output backup.json"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

OUTPUT_FILE="${OUTPUT_FILE:-memories-export-$(date +%Y%m%d-%H%M%S).json}"

echo "Exporting memories from Java backend..."
echo "API URL: $API_URL"
echo "Query: $QUERY"
echo "Project: ${PROJECT:-all}"
echo "Output: $OUTPUT_FILE"
echo ""

TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/cortexce-export.XXXXXX")
trap 'rm -rf "$TMP_DIR"' EXIT

# Fetch every page of a paged viewer endpoint until `max_total` rows are
# collected. The controller clamps `limit` to 100, so a single request cannot
# return a large database; without this loop the export silently stopped at
# the first page (100 rows) and a backup of 100k memories lost 99.9% of it.
#
# Pages are appended to a JSONL file instead of being accumulated in a shell
# variable: a 100k-row export does not fit in argv or a single `jq --argjson`.
#
# $1 base URL, $2 response item key, $3 max rows, $4 project filter (optional),
# $5 query (optional; only /api/search uses it), $6 output JSONL file.
fetch_paged_items() {
    local base_url="$1" item_key="$2" max_total="$3" project_filter="$4" query_filter="$5" out_file="$6"
    local page_size=100
    local offset=0
    local total=0
    : > "$out_file"
    while [ "$total" -lt "$max_total" ]; do
        local remaining=$((max_total - total))
        local page=$page_size
        if [ "$remaining" -lt "$page_size" ]; then page=$remaining; fi
        local curl_args=()
        if [ -n "$project_filter" ]; then curl_args+=(--data-urlencode "project=$project_filter"); fi
        if [ -n "$query_filter" ]; then curl_args+=(--data-urlencode "query=$query_filter"); fi
        local response
        response=$(curl -s -G "$base_url" "${curl_args[@]}" \
            --data-urlencode "offset=$offset" --data-urlencode "limit=$page")
        local count
        count=$(printf '%s' "$response" | jq -r --arg key "$item_key" '.[$key] // [] | length' 2>/dev/null || echo 0)
        if [ "$count" -le 0 ]; then
            break
        fi
        printf '%s' "$response" | jq -c --arg key "$item_key" '.[$key][]?' >> "$out_file"
        total=$((total + count))
        if [ "$count" -lt "$page" ]; then
            break
        fi
        offset=$((offset + count))
    done
}

count_lines() {
    if [ -f "$1" ]; then wc -l < "$1" | tr -d ' '; else echo 0; fi
}

# Step 1: Search for observations
echo "Step 1: Searching observations..."
OBS_FILE="$TMP_DIR/observations.jsonl"
if [ -n "$PROJECT" ] && [ "$QUERY" != "*" ]; then
    fetch_paged_items "$API_URL/api/search" observations "$LIMIT" "$PROJECT" "$QUERY" "$OBS_FILE"
else
    # Listing endpoint. `*` means "every observation", not a text query.
    fetch_paged_items "$API_URL/api/observations" items "$LIMIT" "$PROJECT" "" "$OBS_FILE"
fi
OBS_COUNT=$(count_lines "$OBS_FILE")
echo "Found $OBS_COUNT observations"

# Step 2: Get summaries
echo "Step 2: Fetching summaries..."
SUMMARIES_FILE="$TMP_DIR/summaries.jsonl"
fetch_paged_items "$API_URL/api/summaries" items "$LIMIT" "$PROJECT" "" "$SUMMARIES_FILE"
SUMMARIES_COUNT=$(count_lines "$SUMMARIES_FILE")
echo "Found $SUMMARIES_COUNT summaries"

# Step 3: Get prompts
echo "Step 3: Fetching prompts..."
PROMPTS_FILE="$TMP_DIR/prompts.jsonl"
fetch_paged_items "$API_URL/api/prompts" items "$LIMIT" "$PROJECT" "" "$PROMPTS_FILE"
PROMPTS_COUNT=$(count_lines "$PROMPTS_FILE")
echo "Found $PROMPTS_COUNT prompts"

# Step 4: Extract real Conversation references. Standalone rows (manual /
# extraction / dlq) are exported as observations but never produce a session
# ref. Summaries and prompts also reference their conversation, so they are
# folded in — a conversation with a summary but no observation must still
# round-trip. The session catalog is also folded in so a conversation with no
# child rows is not dropped from the backup.
echo "Step 4: Extracting Conversation references..."
CATALOG_FILE="$TMP_DIR/session-catalog.jsonl"
fetch_paged_items "$API_URL/api/sessions" sessions "$LIMIT" "$PROJECT" "" "$CATALOG_FILE"
SESSION_REFS_FILE="$TMP_DIR/session-refs.json"
jq -n \
    --slurpfile obs "$OBS_FILE" \
    --slurpfile sums "$SUMMARIES_FILE" \
    --slurpfile prompts "$PROMPTS_FILE" \
    --slurpfile catalog "$CATALOG_FILE" '
    [
      ($obs[] | select((has("conversation_id") | not) or .conversation_id != null)
              | select(.content_session_id != null and .content_session_id != "")
              | {contentSessionId: .content_session_id, platformSource: (.platform_source // "claude")}),
      ($sums[] | select(.session_id != null and .session_id != "")
               | {contentSessionId: .session_id, platformSource: (.platform_source // "claude")}),
      ($prompts[] | select(.content_session_id != null and .content_session_id != "")
                 | {contentSessionId: .content_session_id, platformSource: (.platform_source // "claude")}),
      ($catalog[] | select(.content_session_id != null and .content_session_id != "")
                  | {contentSessionId: .content_session_id, platformSource: (.platform_source // "claude")})
    ] | unique_by([.platformSource, .contentSessionId])' > "$SESSION_REFS_FILE" 2>/dev/null \
    || echo "[]" > "$SESSION_REFS_FILE"

SESSIONS_FILE="$TMP_DIR/sessions.jsonl"
: > "$SESSIONS_FILE"
REFS_COUNT=$(jq 'length' "$SESSION_REFS_FILE" 2>/dev/null || echo 0)
if [ "$REFS_COUNT" -eq 0 ]; then
    echo "No sessions found"
    SESSIONS_COUNT=0
else
    # Step 5: Batch query sessions
    echo "Step 5: Fetching session metadata..."
    jq -n --slurpfile refs "$SESSION_REFS_FILE" '{sessionRefs: ($refs[0] // [])}' > "$TMP_DIR/sessions-batch.json"
    SESSIONS_RESPONSE=$(curl -s -X POST "$API_URL/api/sdk-sessions/batch" \
        -H "Content-Type: application/json" \
        -d @"$TMP_DIR/sessions-batch.json")
    printf '%s' "$SESSIONS_RESPONSE" | jq -c 'if type == "array" then .[] else empty end' > "$SESSIONS_FILE" 2>/dev/null || true
    SESSIONS_COUNT=$(count_lines "$SESSIONS_FILE")
    echo "Found $SESSIONS_COUNT sessions"
fi

# Step 6: Build export JSON
echo "Step 6: Building export file..."
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)
EPOCH=$(date +%s)000

jq -cn \
    --arg exportedAt "$TIMESTAMP" \
    --argjson exportedAtEpoch "$EPOCH" \
    --arg query "$QUERY" \
    --arg project "$PROJECT" \
    --argjson totalObservations "$OBS_COUNT" \
    --argjson totalSessions "$SESSIONS_COUNT" \
    --argjson totalSummaries "$SUMMARIES_COUNT" \
    --argjson totalPrompts "$PROMPTS_COUNT" \
    --slurpfile observations "$OBS_FILE" \
    --slurpfile sessions "$SESSIONS_FILE" \
    --slurpfile summaries "$SUMMARIES_FILE" \
    --slurpfile prompts "$PROMPTS_FILE" \
    '{
        exportedAt: $exportedAt,
        exportedAtEpoch: $exportedAtEpoch,
        query: $query,
        project: $project,
        totalObservations: $totalObservations,
        totalSessions: $totalSessions,
        totalSummaries: $totalSummaries,
        totalPrompts: $totalPrompts,
        observations: $observations,
        sessions: $sessions,
        summaries: $summaries,
        prompts: $prompts
    }' > "$OUTPUT_FILE"

echo ""
echo "Export completed successfully!"
echo "Output file: $OUTPUT_FILE"
echo "File size: $(du -h "$OUTPUT_FILE" | cut -f1)"
