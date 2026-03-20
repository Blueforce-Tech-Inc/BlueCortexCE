#!/bin/bash
# Seed diverse test data for WebUI testing
# Creates observations and summaries with various types, concepts, and content

set -e

API_BASE="http://localhost:37777/api"
PROJECT="/tmp/diverse-test-project"

echo "=== Seeding Diverse Test Data ==="

# Function to create an observation via API
# NOTE: Requires session to exist first (FK constraint)
create_observation() {
    local type=$1
    local title=$2
    local subtitle=$3
    local narrative=$4
    local facts=$5
    local concepts=$6
    local files_read=$7
    local files_modified=$8

    local session_id="diverse-session-$(date +%s%N)"

    # First create a session (required for FK constraint)
    # NOTE: session-start expects "session_id"; observations use content_session_id
    curl -s -X POST "http://localhost:37777/api/session/start" \
        -H "Content-Type: application/json" \
        -d "{
            \"session_id\": \"$session_id\",
            \"project_path\": \"$PROJECT\",
            \"cwd\": \"$PROJECT\"
        }" > /dev/null

    # Then create the observation
    curl -s -X POST "$API_BASE/ingest/observation" \
        -H "Content-Type: application/json" \
        -d "{
            \"content_session_id\": \"$session_id\",
            \"project_path\": \"$PROJECT\",
            \"type\": \"$type\",
            \"title\": \"$title\",
            \"subtitle\": \"$subtitle\",
            \"narrative\": \"$narrative\",
            \"facts\": $facts,
            \"concepts\": $concepts,
            \"files_read\": $files_read,
            \"files_modified\": $files_modified,
            \"prompt_number\": 1
        }" > /dev/null

    echo "  Created: $type - $title"
}

# Function to create a summary via direct DB insert (using session-end endpoint)
create_summary() {
    local request=$1
    local completed=$2

    local session_id="summary-session-$(date +%s%N)"

    # First create a session (session_id in /api/session/start)
    curl -s -X POST "http://localhost:37777/api/session/start" \
        -H "Content-Type: application/json" \
        -d "{
            \"session_id\": \"$session_id\",
            \"project_path\": \"$PROJECT\",
            \"cwd\": \"$PROJECT\"
        }" > /dev/null

    # Then end it with summary
    curl -s -X POST "$API_BASE/ingest/session-end" \
        -H "Content-Type: application/json" \
        -d "{
            \"content_session_id\": \"$session_id\",
            \"project_path\": \"$PROJECT\",
            \"request\": \"$request\",
            \"completed\": \"$completed\"
        }" > /dev/null

    echo "  Created summary: ${request:0:50}..."
}

echo ""
echo "=== Creating Diverse Observations ==="

# Bugfix observations
create_observation "bugfix" \
    "Fix SSE connection dropping on reconnect" \
    "Resolved race condition in EventSource handling" \
    "The SSE connection was dropping when clients reconnected due to a race condition between the old connection cleanup and new connection setup. Added proper cleanup sequencing." \
    '["SSE connections now properly clean up before reconnecting", "Added connection state tracking to prevent race conditions", "Tested with 100 reconnection cycles"]' \
    '["gotcha", "problem-solution"]' \
    '["src/services/SSEBroadcaster.ts", "src/ui/viewer/hooks/useSSE.ts"]' \
    '["src/services/SSEBroadcaster.ts"]'

create_observation "bugfix" \
    "Fix null pointer in embedding service" \
    "Handle missing API key gracefully" \
    "The embedding service was throwing NPE when API key was not configured. Added proper null check and fallback behavior." \
    '["Added null check for embedding API key", "Service now logs warning instead of crashing", "Fallback to text-only search when embeddings unavailable"]' \
    '["gotcha", "how-it-works"]' \
    '["src/services/EmbeddingService.ts"]' \
    '["src/services/EmbeddingService.ts"]'

# Feature observations
create_observation "feature" \
    "Add multi-project filtering to WebUI" \
    "Users can now filter observations by project" \
    "Implemented a dropdown selector in the WebUI header that allows users to filter all observations, summaries, and prompts by project path. The filter state persists across page refreshes." \
    '["Added project dropdown to header component", "Filter applies to all list views", "State persists in localStorage", "SSE updates respect active filter"]' \
    '["how-it-works", "pattern"]' \
    '["src/ui/viewer/components/Header.tsx", "src/ui/viewer/App.tsx"]' \
    '["src/ui/viewer/App.tsx"]'

create_observation "feature" \
    "Implement semantic search with pgvector" \
    "Vector similarity search for observations" \
    "Integrated PostgreSQL pgvector extension for semantic search. Observations are now embedded using bge-m3 model and stored as 1024-dimensional vectors. Search falls back to text search when embeddings unavailable." \
    '["Uses pgvector extension for vector operations", "bge-m3 model produces 1024-dim embeddings", "Cosine similarity for semantic matching", "Automatic fallback to ILIKE text search"]' \
    '["how-it-works", "why-it-exists", "trade-off"]' \
    '["docs/drafts/webui-java-integration-feasibility-report.md"]' \
    '["src/services/SearchService.ts", "src/repositories/ObservationRepository.ts"]'

# Refactor observations
create_observation "refactor" \
    "Extract AgentService from monolithic worker" \
    "Improved separation of concerns" \
    "Split the large WorkerService class into dedicated AgentService for LLM orchestration. This improves testability and follows single responsibility principle." \
    '["AgentService now handles all LLM interactions", "WorkerService focuses on HTTP and routing", "Improved unit test coverage by 40%"]' \
    '["pattern", "what-changed"]' \
    '["src/services/WorkerService.ts"]' \
    '["src/services/AgentService.ts", "src/services/WorkerService.ts"]'

create_observation "refactor" \
    "Migrate from SQLite to PostgreSQL" \
    "Java port uses PostgreSQL for better concurrency" \
    "The Java implementation uses PostgreSQL instead of SQLite to handle concurrent connections better and leverage pgvector for semantic search." \
    '["PostgreSQL handles concurrent writes better", "pgvector provides native vector search", "Flyway manages schema migrations", "Connection pooling via HikariCP"]' \
    '["why-it-exists", "trade-off", "what-changed"]' \
    '["src/services/sqlite/*.ts"]' \
    '["java/claude-mem-java/src/main/resources/db/migration/*.sql"]'

# Discovery observations
create_observation "discovery" \
    "SSE requires unnamed events for EventSource.onmessage" \
    "Named events need addEventListener instead" \
    "Discovered that the WebUI uses EventSource.onmessage which only receives unnamed SSE events. Java Spring Boot SseEmitter.event().name() sends named events that require addEventListener(). Fix: remove .name() calls." \
    '["EventSource.onmessage catches unnamed events only", "Named events need eventSource.addEventListener()", "Spring Boot SseEmitter sends event:name header", "TS version uses res.write(\"data:...\") format"]' \
    '["gotcha", "how-it-works"]' \
    '["src/ui/viewer/hooks/useSSE.ts"]' \
    '["java/claude-mem-java/src/main/java/com/claudemem/server/controller/StreamController.java"]'

create_observation "discovery" \
    "pgvector HNSW index limit is 2000 dimensions" \
    "Cannot index 3072-dim embeddings" \
    "PostgreSQL pgvector extension has a limit of 2000 dimensions for HNSW and IVFFlat indexes. The 3072-dimensional embedding column must remain unindexed." \
    '["HNSW max dimensions: 2000", "IVFFlat max dimensions: 2000", "3072-dim column has no index", "Exact search still works on unindexed columns"]' \
    '["gotcha", "trade-off"]' \
    '["java/CLAUDE.md"]' \
    '[]'

# Decision observations
create_observation "decision" \
    "Use thin proxy architecture for hooks" \
    "Hooks forward to Java service asynchronously" \
    "Decided to use a thin Node.js proxy that forwards hook events to the Java service. This prevents hook timeouts while keeping the hooks lightweight." \
    '["Hooks must complete in < 5 seconds", "Node.js proxy exits immediately after forwarding", "Java service processes asynchronously", "Exit code 0 prevents terminal tab accumulation"]' \
    '["why-it-exists", "pattern", "trade-off"]' \
    '["docs/drafts/java-rewrite-plan.md"]' \
    '["java/proxy/wrapper.js"]'

create_observation "decision" \
    "Use camelCase in API but snake_case in JSON" \
    "Java entities serialize to snake_case" \
    "Decided to use snake_case for JSON field names to match TypeScript version API. Java code uses camelCase internally but Jackson serializes to snake_case via @JsonProperty." \
    '["WebUI expects snake_case field names", "Java uses @JsonProperty for serialization", "Maintains API compatibility with TS version"]' \
    '["pattern", "why-it-exists"]' \
    '["docs/drafts/webui-java-integration-feasibility-report.md"]' \
    '["java/claude-mem-java/src/main/java/com/claudemem/server/entity/*.java"]'

# Change observations
create_observation "change" \
    "Update embedding model to bge-m3" \
    "Better multilingual support" \
    "Changed embedding model from text-embedding-3-small to bge-m3 for better multilingual support and lower cost via SiliconFlow API." \
    '["bge-m3 supports 100+ languages", "1024 dimensions vs 1536", "Lower cost via SiliconFlow", "Better performance on code documentation"]' \
    '["what-changed", "why-it-exists"]' \
    '["src/services/EmbeddingService.ts"]' \
    '["java/claude-mem-java/src/main/resources/application.yml"]'

create_observation "change" \
    "Add processing status indicator to WebUI" \
    "Shows spinner when LLM is processing" \
    "Added a spinning logo and queue depth indicator to the WebUI header that activates when the LLM is processing observations or summaries." \
    '["Logo spins during processing", "Queue depth shown in bubble", "SSE pushes processing_status events", "Icon returns to static when complete"]' \
    '["what-changed", "how-it-works"]' \
    '["src/ui/viewer/App.tsx"]' \
    '["src/ui/viewer/components/Header.tsx", "src/ui/viewer/hooks/useSpinningFavicon.ts"]'

echo ""
echo "=== Creating Diverse Summaries ==="

create_summary \
    "Implement complete WebUI integration with Java backend" \
    "Successfully integrated React WebUI with Spring Boot backend. Fixed SSE event format compatibility, deployed static files, and verified all API endpoints work correctly."

create_summary \
    "Debug and fix empty project dropdown in WebUI" \
    "Identified that SSE named events were not being received by EventSource.onmessage. Fixed by removing .name() from SSE events, matching TypeScript version behavior."

create_summary \
    "Migrate embedding pipeline from OpenAI to SiliconFlow" \
    "Configured bge-m3 model via SiliconFlow API. Updated all embedding dimensions to 1024 and verified vector search works correctly."

create_summary \
    "Set up PostgreSQL with pgvector for semantic search" \
    "Installed pgvector extension, created migration scripts, and implemented hybrid search with fallback to text search when embeddings unavailable."

create_summary \
    "Deploy WebUI static files to Spring Boot resources" \
    "Created deployment script that copies built viewer files to Java static directory. Verified all assets load correctly including fonts and icons."

echo ""
echo "=== Seeding Complete ==="
echo "Project: $PROJECT"
echo ""
echo "Verify with:"
echo "  curl -s 'http://localhost:37777/api/observations?project=$PROJECT&limit=20' | jq '.items[] | {type, title}'"
echo "  curl -s 'http://localhost:37777/api/summaries?project=$PROJECT&limit=10' | jq '.items[] | {request, completed}'"
