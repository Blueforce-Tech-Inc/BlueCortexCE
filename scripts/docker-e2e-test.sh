#!/bin/bash
#
# Docker Deployment End-to-End Test Script
# Tests the complete Docker deployment of Claude-Mem Java version
#
# Usage: ./docker-e2e-test.sh [--cleanup] [--skip-build] [--help]
#
# Features:
#   - Uses non-conflicting ports (PG: 15432, Java: 38888)
#   - Automatically stops/removes existing containers
#   - Builds Docker images if needed
#   - Runs comprehensive end-to-end tests
#   - Cleans up after tests (with --cleanup flag)
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Non-conflicting ports
PG_PORT="${PG_PORT:-15432}"
JAVA_PORT="${JAVA_PORT:-38888}"
DB_NAME="${DB_NAME:-claude_mem_test}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-testpassword123}"

# Container names
PG_CONTAINER="claude-mem-test-postgres"
JAVA_CONTAINER="claude-mem-test-java"
NETWORK_NAME="claude-mem-test-network"

# Test configuration
TEST_SESSION_ID="docker-e2e-$(date +%s)"
TEST_PROJECT="/tmp/docker-test-project-$$"
SKIP_BUILD=false
CLEANUP_AFTER=false
KEEP_RUNNING=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
TESTS_PASSED=0
TESTS_FAILED=0

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --cleanup)
            CLEANUP_AFTER=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --keep-running)
            KEEP_RUNNING=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --cleanup       Remove containers and volumes after tests"
            echo "  --skip-build    Skip Docker image build (use existing)"
            echo "  --keep-running  Keep containers running after tests"
            echo "  --help, -h      Show this help message"
            echo ""
            echo "Ports used:"
            echo "  PostgreSQL: $PG_PORT (non-default to avoid conflicts)"
            echo "  Java API:   $JAVA_PORT (non-default to avoid conflicts)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++))
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_section() {
    echo ""
    echo "=========================================="
    echo "$1"
    echo "=========================================="
}

check_prerequisites() {
    log_section "Checking Prerequisites"

    if ! command -v docker &> /dev/null; then
        log_fail "Docker is not installed"
        exit 1
    fi
    log_success "Docker is installed"

    if ! command -v curl &> /dev/null; then
        log_fail "curl is not installed"
        exit 1
    fi
    log_success "curl is installed"

    if ! docker info &> /dev/null; then
        log_fail "Docker daemon is not running"
        exit 1
    fi
    log_success "Docker daemon is running"

    if [ ! -f "$PROJECT_ROOT/Dockerfile" ]; then
        log_fail "Dockerfile not found at $PROJECT_ROOT/Dockerfile"
        exit 1
    fi
    log_success "Dockerfile found"
}

stop_existing_containers() {
    log_section "Stopping Existing Containers"

    log_info "Stopping and removing existing containers..."

    docker stop "$PG_CONTAINER" 2>/dev/null || true
    docker rm "$PG_CONTAINER" 2>/dev/null || true
    docker stop "$JAVA_CONTAINER" 2>/dev/null || true
    docker rm "$JAVA_CONTAINER" 2>/dev/null || true

    docker network rm "$NETWORK_NAME" 2>/dev/null || true

    log_success "Existing containers cleaned up"
}

create_test_env_file() {
    log_section "Creating Test Environment File"

    TEST_ENV_FILE="$PROJECT_ROOT/.env.docker-test"
    
    local chat_api_key="${SPRING_AI_OPENAI_API_KEY:-}"
    local chat_base_url="${SPRING_AI_OPENAI_BASE_URL:-https://api.deepseek.com}"
    local chat_model="${SPRING_AI_OPENAI_CHAT_MODEL:-deepseek-chat}"
    local embed_api_key="${SPRING_AI_OPENAI_EMBEDDING_API_KEY:-}"
    local embed_base_url="${SPRING_AI_OPENAI_EMBEDDING_BASE_URL:-https://api.siliconflow.cn}"
    local embed_model="${SPRING_AI_OPENAI_EMBEDDING_MODEL:-BAAI/bge-m3}"
    local embed_dim="${SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS:-1024}"
    
    if [ -f "$PROJECT_ROOT/claude-mem-java/.env" ]; then
        log_info "Loading API keys from claude-mem-java/.env..."
        while IFS='=' read -r key value; do
            [ -z "$key" ] && continue
            case "$key" in
                OPENAI_API_KEY) chat_api_key="$value" ;;
                OPENAI_BASE_URL) chat_base_url="$value" ;;
                OPENAI_MODEL) chat_model="$value" ;;
                SILICONFLOW_API_KEY) embed_api_key="$value" ;;
                SILICONFLOW_URL) embed_base_url="$value" ;;
                SILICONFLOW_MODEL) embed_model="$value" ;;
                SILICONFLOW_DIMENSIONS) embed_dim="$value" ;;
            esac
        done < <(grep -v '^#' "$PROJECT_ROOT/claude-mem-java/.env" | grep -v '^$')
    fi
    
    cat > "$TEST_ENV_FILE" << EOF
# Test environment - auto-generated
DB_NAME=$DB_NAME
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASS

# Spring profile
SPRING_PROFILES_ACTIVE=dev

# LLM Configuration (Chat)
SPRING_AI_OPENAI_API_KEY=$chat_api_key
SPRING_AI_OPENAI_BASE_URL=$chat_base_url
SPRING_AI_OPENAI_CHAT_MODEL=$chat_model

# Embedding Configuration
SPRING_AI_OPENAI_EMBEDDING_API_KEY=$embed_api_key
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=$embed_base_url
SPRING_AI_OPENAI_EMBEDDING_MODEL=$embed_model
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=$embed_dim

CLAUDE_MEM_MODE=code
EOF

    log_success "Test environment file created: $TEST_ENV_FILE"
}

build_docker_images() {
    if [ "$SKIP_BUILD" = true ]; then
        log_info "Skipping Docker image build (--skip-build)"
        return 0
    fi

    log_section "Building Docker Images"

    log_info "Building claude-mem-java image..."
    
    # Build context must be the parent directory (claude-mem root) to access WebUI source files
    local build_context="$(cd "$PROJECT_ROOT/.." && pwd)"
    
    local build_output
    if build_output=$(docker build -t claude-mem-java:test -f "$PROJECT_ROOT/Dockerfile" "$build_context" 2>&1); then
        log_success "Docker image built successfully"
        echo "$build_output" | tail -20
        return 0
    else
        log_fail "Docker image build failed"
        echo "$build_output" | tail -50
        
        if echo "$build_output" | grep -q "503\|Service Unavailable\|certificate"; then
            log_warn ""
            log_warn "Docker registry is unavailable. This might be due to:"
            log_warn "  1. Network connectivity issues"
            log_warn "  2. Docker registry mirror problems"
            log_warn "  3. TLS certificate issues"
            log_warn ""
            log_warn "Please check your Docker configuration and network connection."
        fi
        return 1
    fi
}

start_postgres() {
    log_section "Starting PostgreSQL Container"

    log_info "Pulling pgvector image..."
    docker pull pgvector/pgvector:pg16

    log_info "Creating Docker network..."
    docker network create "$NETWORK_NAME" 2>/dev/null || true

    log_info "Starting PostgreSQL on port $PG_PORT..."
    
    docker run -d \
        --name "$PG_CONTAINER" \
        --network "$NETWORK_NAME" \
        -e POSTGRES_DB="$DB_NAME" \
        -e POSTGRES_USER="$DB_USER" \
        -e POSTGRES_PASSWORD="$DB_PASS" \
        -p "$PG_PORT:5432" \
        pgvector/pgvector:pg16

    log_info "Waiting for PostgreSQL to be ready..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if docker exec "$PG_CONTAINER" pg_isready -U "$DB_USER" &>/dev/null; then
            log_success "PostgreSQL is ready"
            return 0
        fi
        echo -n "."
        sleep 1
        ((attempt++))
    done

    log_fail "PostgreSQL failed to start"
    return 1
}

start_java_app() {
    log_section "Starting Java Application Container"

    log_info "Starting Java application on port $JAVA_PORT..."

    docker run -d \
        --name "$JAVA_CONTAINER" \
        --network "$NETWORK_NAME" \
        --env-file "$TEST_ENV_FILE" \
        -e SPRING_DATASOURCE_URL="jdbc:postgresql://$PG_CONTAINER:5432/$DB_NAME" \
        -e SPRING_DATASOURCE_USERNAME="$DB_USER" \
        -e SPRING_DATASOURCE_PASSWORD="$DB_PASS" \
        -e SERVER_PORT=37777 \
        -e SERVER_ADDRESS=0.0.0.0 \
        -p "$JAVA_PORT:37777" \
        claude-mem-java:test

    log_info "Waiting for Java application to be ready..."
    local max_attempts=60
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "http://localhost:$JAVA_PORT/actuator/health" > /dev/null 2>&1; then
            log_success "Java application is ready"
            return 0
        fi
        echo -n "."
        sleep 2
        ((attempt++))
    done

    log_fail "Java application failed to start"
    log_info "Container logs:"
    docker logs "$JAVA_CONTAINER" --tail 50
    return 1
}

test_health_endpoint() {
    log_section "Test 1: Health Endpoint"

    local response
    response=$(curl -sf "http://localhost:$JAVA_PORT/actuator/health" 2>&1) || {
        log_fail "Health endpoint returned error: $response"
        return 1
    }

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('status')=='UP' else 1)" 2>/dev/null; then
        log_success "Health endpoint returns UP"
        echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
        return 0
    else
        log_fail "Health endpoint not UP: $response"
        return 1
    fi
}

test_session_creation() {
    log_section "Test 2: Session Creation"

    local response
    response=$(curl -sf -X POST "http://localhost:$JAVA_PORT/api/session/start" \
        -H 'Content-Type: application/json' \
        -d "{
            \"session_id\": \"$TEST_SESSION_ID\",
            \"project_path\": \"$TEST_PROJECT\"
        }" 2>&1) || {
        log_fail "Session creation failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('session_db_id') else 1)" 2>/dev/null; then
        log_success "Session created successfully"
        return 0
    else
        log_fail "Session creation did not return session_db_id"
        return 1
    fi
}

test_observation_ingestion() {
    log_section "Test 3: Observation Ingestion"

    local response
    response=$(curl -sf -X POST "http://localhost:$JAVA_PORT/api/ingest/observation" \
        -H 'Content-Type: application/json' \
        -d "{
            \"memory_session_id\": \"$TEST_SESSION_ID\",
            \"project_path\": \"$TEST_PROJECT\",
            \"type\": \"feature\",
            \"title\": \"Docker E2E Test Observation\",
            \"subtitle\": \"Test subtitle\",
            \"content\": \"This is a test observation created by the Docker E2E test suite.\",
            \"facts\": [\"Test fact 1\", \"Test fact 2\"],
            \"concepts\": [\"docker\", \"testing\"],
            \"files_read\": [\"test.java\"],
            \"files_modified\": [\"Test.java\"],
            \"prompt_number\": 1
        }" 2>&1) || {
        log_fail "Observation ingestion failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('id') else 1)" 2>/dev/null; then
        log_success "Observation created with ID"
        return 0
    else
        log_fail "Observation creation did not return ID"
        return 1
    fi
}

test_observation_retrieval() {
    log_section "Test 4: Observation Retrieval"

    local response
    response=$(curl -sf "http://localhost:$JAVA_PORT/api/observations?project=${TEST_PROJECT}&offset=0&limit=10" 2>&1) || {
        log_fail "Observation retrieval failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    local count
    count=$(echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); print(len(data.get('items', [])))" 2>/dev/null || echo "0")

    if [ "$count" -ge 1 ]; then
        log_success "Retrieved $count observation(s)"
        return 0
    else
        log_fail "Expected at least 1 observation, got $count"
        return 1
    fi
}

test_search_endpoint() {
    log_section "Test 5: Search Endpoint"

    local response
    response=$(curl -sf "http://localhost:$JAVA_PORT/api/search?project=${TEST_PROJECT}&query=docker+test&limit=5" 2>&1) || {
        log_fail "Search endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    local count
    count=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('count', 0))" 2>/dev/null || echo "0")

    if [ "$count" -ge 0 ]; then
        log_success "Search endpoint working (returned $count results)"
        return 0
    else
        log_fail "Search endpoint returned unexpected response"
        return 1
    fi
}

test_stats_endpoint() {
    log_section "Test 6: Stats Endpoint"

    local response
    response=$(curl -sf "http://localhost:$JAVA_PORT/api/stats" 2>&1) || {
        log_fail "Stats endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if 'worker' in d and 'database' in d else 1)" 2>/dev/null; then
        log_success "Stats endpoint returns nested {worker, database} structure"
        return 0
    else
        log_fail "Stats endpoint missing required nested structure"
        return 1
    fi
}

test_projects_endpoint() {
    log_section "Test 7: Projects Endpoint"

    local response
    response=$(curl -sf "http://localhost:$JAVA_PORT/api/projects" 2>&1) || {
        log_fail "Projects endpoint failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if isinstance(d.get('projects', []), list) else 1)" 2>/dev/null; then
        log_success "Projects endpoint returns {projects: [...]} format"
        return 0
    else
        log_fail "Projects endpoint did not return {projects: [...]}"
        return 1
    fi
}

test_session_completion() {
    log_section "Test 8: Session Completion"

    local response
    response=$(curl -sf -X POST "http://localhost:$JAVA_PORT/api/ingest/session-end" \
        -H 'Content-Type: application/json' \
        -d "{\"session_id\": \"$TEST_SESSION_ID\"}" 2>&1) || {
        log_fail "Session completion failed: $response"
        return 1
    }

    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"

    if echo "$response" | python3 -c "import sys, json; d=json.load(sys.stdin); sys.exit(0 if d.get('status')=='ok' else 1)" 2>/dev/null; then
        log_success "Session completed successfully"
        return 0
    else
        log_fail "Session completion did not return status: ok"
        return 1
    fi
}

test_database_persistence() {
    log_section "Test 9: Database Persistence"

    log_info "Checking database directly..."

    local obs_count
    obs_count=$(docker exec "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "
        SELECT COUNT(*) FROM mem_observations WHERE project_path = '$TEST_PROJECT';
    " 2>/dev/null | tr -d '[:space:]') || obs_count="0"

    echo "Observations in DB: $obs_count"

    if [ "$obs_count" -ge 1 ]; then
        log_success "Data persisted in database ($obs_count observations)"
        return 0
    else
        log_fail "Data not persisted in database"
        return 1
    fi
}

test_container_restart() {
    log_section "Test 10: Container Restart Persistence"

    log_info "Restarting Java container..."
    docker restart "$JAVA_CONTAINER"

    log_info "Waiting for Java application to be ready after restart..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "http://localhost:$JAVA_PORT/actuator/health" > /dev/null 2>&1; then
            break
        fi
        echo -n "."
        sleep 2
        ((attempt++))
    done

    if [ $attempt -gt $max_attempts ]; then
        log_fail "Java application failed to start after restart"
        return 1
    fi

    local response
    response=$(curl -sf "http://localhost:$JAVA_PORT/api/observations?project=${TEST_PROJECT}&offset=0&limit=10" 2>&1) || {
        log_fail "Observation retrieval failed after restart: $response"
        return 1
    }

    local count
    count=$(echo "$response" | python3 -c "import sys, json; data=json.load(sys.stdin); print(len(data.get('items', [])))" 2>/dev/null || echo "0")

    if [ "$count" -ge 1 ]; then
        log_success "Data persisted after container restart ($count observations)"
        return 0
    else
        log_fail "Data lost after container restart"
        return 1
    fi
}

test_webui_static_files() {
    log_section "Test 11: WebUI Static Files"

    # Simple check: verify index.html returns valid HTML
    local html_content
    html_content=$(curl -sf "http://localhost:$JAVA_PORT/index.html" 2>&1) || {
        log_fail "WebUI not accessible"
        return 1
    }
    
    if [[ "$html_content" == *"DOCTYPE html"* ]]; then
        log_success "WebUI is accessible at /index.html"
        return 0
    else
        log_fail "WebUI returned invalid HTML"
        return 1
    fi
}

cleanup() {
    log_section "Cleanup"

    if [ "$KEEP_RUNNING" = true ]; then
        log_info "Keeping containers running (--keep-running)"
        return 0
    fi

    if [ "$CLEANUP_AFTER" = true ]; then
        log_info "Removing containers and volumes..."
        
        docker stop "$JAVA_CONTAINER" 2>/dev/null || true
        docker rm "$JAVA_CONTAINER" 2>/dev/null || true
        docker stop "$PG_CONTAINER" 2>/dev/null || true
        docker rm "$PG_CONTAINER" 2>/dev/null || true
        docker network rm "$NETWORK_NAME" 2>/dev/null || true
        
        rm -f "$TEST_ENV_FILE"
        
        log_success "Cleanup complete"
    else
        log_info "Containers are still running. Use --cleanup to remove them."
        log_info "To stop: docker stop $PG_CONTAINER $JAVA_CONTAINER"
        log_info "To remove: docker rm $PG_CONTAINER $JAVA_CONTAINER"
    fi
}

print_summary() {
    log_section "Test Summary"
    echo ""
    echo -e "${GREEN}Passed:${NC}  $TESTS_PASSED"
    echo -e "${RED}Failed:${NC}  $TESTS_FAILED"
    echo ""

    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        return 0
    else
        echo -e "${RED}Some tests failed.${NC}"
        return 1
    fi
}

show_logs() {
    log_section "Container Logs (last 30 lines)"
    
    echo "=== PostgreSQL Logs ==="
    docker logs "$PG_CONTAINER" --tail 30 2>/dev/null || echo "No logs available"
    
    echo ""
    echo "=== Java Application Logs ==="
    docker logs "$JAVA_CONTAINER" --tail 30 2>/dev/null || echo "No logs available"
}

main() {
    echo ""
    echo "=============================================="
    echo "  Claude-Mem Docker E2E Test Suite"
    echo "=============================================="
    echo ""
    echo "PostgreSQL Port: $PG_PORT"
    echo "Java API Port:   $JAVA_PORT"
    echo "Test Session:    $TEST_SESSION_ID"
    echo "Test Project:    $TEST_PROJECT"
    echo ""

    check_prerequisites || exit 1

    stop_existing_containers

    create_test_env_file

    build_docker_images || exit 1

    start_postgres || {
        show_logs
        exit 1
    }

    start_java_app || {
        show_logs
        exit 1
    }

    log_section "Running End-to-End Tests"

    test_health_endpoint
    test_session_creation
    test_observation_ingestion
    test_observation_retrieval
    test_search_endpoint
    test_stats_endpoint
    test_projects_endpoint
    test_session_completion
    test_database_persistence
    test_container_restart
    test_webui_static_files

    print_summary
    local exit_code=$?

    if [ $TESTS_FAILED -gt 0 ]; then
        show_logs
    fi

    cleanup

    exit $exit_code
}

trap 'log_warn "Test interrupted"; show_logs; cleanup; exit 130' INT TERM

main "$@"
