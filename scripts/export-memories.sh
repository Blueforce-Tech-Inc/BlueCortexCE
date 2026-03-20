#!/bin/bash
#
# Export memories from Java backend
# Usage: ./export-memories.sh [--query QUERY] [--project PROJECT] [--output FILE] [--limit N]
#
# Options:
#   --query QUERY    Search query (default: *)
#   --project PROJECT Filter by project path
#   --output FILE    Output file (default: memories-export.json)
#   --limit N        Max results (default: 1000)
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
            echo "  --limit N        Max results (default: 1000)"
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

# Step 1: Search for observations
echo "Step 1: Searching observations..."

if [ -n "$PROJECT" ]; then
    # Use search API with project filter
    SEARCH_URL="$API_URL/api/search?query=$(echo "$QUERY" | jq -r . | sed 's/ /%20/g')&project=$(echo "$PROJECT" | jq -r . | sed 's/ /%20/g')&limit=$LIMIT"
    SEARCH_RESPONSE=$(curl -s "$SEARCH_URL")

    # Check if search returned results
    if ! echo "$SEARCH_RESPONSE" | jq -e '.observations' > /dev/null 2>&1; then
        echo "No observations found for query: $QUERY"
        exit 0
    fi
else
    # No project specified - get observations from all projects (limited to LIMIT)
    echo "No project specified - fetching observations (limit: $LIMIT)..."
    OBS_RESPONSE=$(curl -s "$API_URL/api/observations?offset=0&limit=$LIMIT")
    OBSERVATIONS=$(echo "$OBS_RESPONSE" | jq '.items // []')
    SEARCH_RESPONSE="{\"observations\": $OBSERVATIONS}"
fi

OBSERVATIONS=$(echo "$SEARCH_RESPONSE" | jq '.observations')
OBS_COUNT=$(echo "$OBSERVATIONS" | jq 'length')

echo "Found $OBS_COUNT observations"

# Step 2: Extract session IDs
echo "Step 2: Extracting session IDs..."
# Extract unique content session IDs and build JSON array
CONTENT_SESSION_IDS=$(echo "$OBSERVATIONS" | jq '[.[].content_session_id] | unique' 2>/dev/null || echo "[]")

if [ "$CONTENT_SESSION_IDS" = "[]" ] || [ "$CONTENT_SESSION_IDS" = "null" ]; then
    echo "No sessions found"
    SESSIONS="[]"
    SESSIONS_COUNT=0
else
    # Step 3: Batch query sessions
    echo "Step 3: Fetching session metadata..."
    # Create JSON payload properly
    JSON_PAYLOAD=$(jq -n --argjson ids "$CONTENT_SESSION_IDS" '{contentSessionIds: $ids}')
    SESSIONS_RESPONSE=$(curl -s -X POST "$API_URL/api/sdk-sessions/batch" \
        -H "Content-Type: application/json" \
        -d "$JSON_PAYLOAD")

    SESSIONS=$(echo "$SESSIONS_RESPONSE" | jq '. ' 2>/dev/null || echo "[]")
    SESSIONS_COUNT=$(echo "$SESSIONS" | jq 'length' 2>/dev/null || echo "0")
    echo "Found $SESSIONS_COUNT sessions"
fi

# Step 4: Get summaries
echo "Step 4: Fetching summaries..."
if [ -n "$PROJECT" ]; then
    SUMMARIES_RESPONSE=$(curl -s "$API_URL/api/summaries?project=$(echo "$PROJECT" | jq -r . | sed 's/ /%20/g')&limit=$LIMIT")
else
    SUMMARIES_RESPONSE=$(curl -s "$API_URL/api/summaries?limit=$LIMIT")
fi
if echo "$SUMMARIES_RESPONSE" | jq -e '.items' > /dev/null 2>&1; then
    SUMMARIES=$(echo "$SUMMARIES_RESPONSE" | jq '.items')
    SUMMARIES_COUNT=$(echo "$SUMMARIES" | jq 'length')
else
    SUMMARIES="[]"
    SUMMARIES_COUNT=0
fi
echo "Found $SUMMARIES_COUNT summaries"

# Step 5: Get prompts
echo "Step 5: Fetching prompts..."
if [ -n "$PROJECT" ]; then
    PROMPTS_RESPONSE=$(curl -s "$API_URL/api/prompts?project=$(echo "$PROJECT" | jq -r . | sed 's/ /%20/g')&limit=$LIMIT")
else
    PROMPTS_RESPONSE=$(curl -s "$API_URL/api/prompts?limit=$LIMIT")
fi
if echo "$PROMPTS_RESPONSE" | jq -e '.items' > /dev/null 2>&1; then
    PROMPTS=$(echo "$PROMPTS_RESPONSE" | jq '.items')
    PROMPTS_COUNT=$(echo "$PROMPTS" | jq 'length')
else
    PROMPTS="[]"
    PROMPTS_COUNT=0
fi
echo "Found $PROMPTS_COUNT prompts"

# Step 6: Build export JSON
echo "Step 6: Building export file..."

# Debug: Check each variable for validity
echo "$OBSERVATIONS" | jq '.' > /dev/null 2>&1 || { echo "ERROR: OBSERVATIONS is invalid JSON"; OBSERVATIONS="[]"; }
echo "$SESSIONS" | jq '.' > /dev/null 2>&1 || { echo "ERROR: SESSIONS is invalid JSON"; SESSIONS="[]"; }
echo "$SUMMARIES" | jq '.' > /dev/null 2>&1 || { echo "ERROR: SUMMARIES is invalid JSON"; SUMMARIES="[]"; }
echo "$PROMPTS" | jq '.' > /dev/null 2>&1 || { echo "ERROR: PROMPTS is invalid JSON"; PROMPTS="[]"; }

# Use printf to create JSON to avoid jq --argjson issues with special chars
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)
EPOCH=$(date +%s)000

# Escape strings for JSON
QUERY_ESCAPED=$(printf '%s' "$QUERY" | jq -Rs .)
PROJECT_ESCAPED=$(printf '%s' "$PROJECT" | jq -Rs .)

EXPORT_JSON=$(jq -n \
    --arg exportedAt "$TIMESTAMP" \
    --argjson exportedAtEpoch "$EPOCH" \
    --argjson query "$QUERY_ESCAPED" \
    --argjson project "$PROJECT_ESCAPED" \
    --argjson totalObservations "$OBS_COUNT" \
    --argjson totalSessions "$SESSIONS_COUNT" \
    --argjson totalSummaries "$SUMMARIES_COUNT" \
    --argjson totalPrompts "$PROMPTS_COUNT" \
    --argjson observations "$OBSERVATIONS" \
    --argjson sessions "$SESSIONS" \
    --argjson summaries "$SUMMARIES" \
    --argjson prompts "$PROMPTS" \
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
    }')

# Write to file
echo "$EXPORT_JSON" > "$OUTPUT_FILE"

echo ""
echo "========================================"
echo "Export complete!"
echo "========================================"
echo "Output file: $OUTPUT_FILE"
echo "Observations: $OBS_COUNT"
echo "Sessions: $SESSIONS_COUNT"
echo "Summaries: $SUMMARIES_COUNT"
echo "Prompts: $PROMPTS_COUNT"
