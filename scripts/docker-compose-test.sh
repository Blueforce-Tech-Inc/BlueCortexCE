#!/bin/bash
#
# Docker Compose Test Script
# Tests docker-compose.yml deployment for production readiness
#
# Usage: ./docker-compose-test.sh [--cleanup] [--help]
#
# Features:
#   - Uses non-conflicting ports (PG: 15433, Java: 38889)
#   - Tests docker-compose.yml configuration
#   - Validates production deployment setup
#   - Cleans up after tests (with --cleanup flag)
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Non-conflicting ports (different from docker-e2e-test.sh)
PG_PORT="${PG_PORT:-15433}"
JAVA_PORT="${JAVA_PORT:-38889}"
DB_NAME="${DB_NAME:-claude_mem_compose_test}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASS:-testpassword123}"

# Test configuration
TEST_SESSION_ID="compose-test-$(date +%s)"
TEST_PROJECT="/tmp/compose-test-project-$$"
SKIP_BUILD=false
CLEANUP_AFTER=false
USE_PREBUILT_IMAGE=true

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
        --build)
            SKIP_BUILD=false
            USE_PREBUILT_IMAGE=false
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            USE_PREBUILT_IMAGE=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --cleanup       Remove containers and volumes after tests"
            echo "  --build        Build Docker image locally instead of using pre-built"
            echo "  --skip-build   Skip Docker image build (use existing pre-built)"
            echo "  --help, -h     Show this help message"
            echo ""
            echo "Ports used:"
            echo "  PostgreSQL: $PG_PORT (non-default to avoid conflicts)"
            echo "  Java API:   $JAVA_PORT (non-default to avoid conflicts)"
            echo ""
            echo "Default behavior: Uses pre-built image from ghcr.io"
            echo "Use --build to build locally for development testing"
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

    if ! docker info &> /dev/null; then
        log_fail "Docker daemon is not running"
        exit 1
    fi
    log_success "Docker daemon is running"

    if [ ! -f "$PROJECT_ROOT/docker-compose.yml" ]; then
        log_fail "docker-compose.yml not found at $PROJECT_ROOT/docker-compose.yml"
        exit 1
    fi
    log_success "docker-compose.yml found"

    if [ "$USE_PREBUILT_IMAGE" = false ]; then
        if [ ! -f "$PROJECT_ROOT/Dockerfile" ]; then
            log_fail "Dockerfile not found at $PROJECT_ROOT/Dockerfile (required for local build)"
            exit 1
        fi
        log_success "Dockerfile found (for local build)"
    else
        log_info "Using pre-built image (Dockerfile not required)"
    fi
}

create_test_env_file() {
    log_section "Creating Test Environment File"

    TEST_ENV_FILE="$PROJECT_ROOT/.env.compose-test"
    
    local chat_api_key="${SPRING_AI_OPENAI_API_KEY:-}"
    local chat_base_url="${SPRING_AI_OPENAI_BASE_URL:-https://api.deepseek.com}"
    local chat_model="${SPRING_AI_OPENAI_CHAT_MODEL:-deepseek-chat}"
    local embed_api_key="${SPRING_AI_OPENAI_EMBEDDING_API_KEY:-}"
    local embed_base_url="${SPRING_AI_OPENAI_EMBEDDING_BASE_URL:-https://api.siliconflow.cn}"
    local embed_model="${SPRING_AI_OPENAI_EMBEDDING_MODEL:-BAAI/bge-m3}"
    local embed_dim="${SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS:-1024}"
    
    if [ -f "$PROJECT_ROOT/backend/.env" ]; then
        log_info "Loading API keys from backend/.env..."
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
        done < <(grep -v '^#' "$PROJECT_ROOT/backend/.env" | grep -v '^$')
    fi
    
    cat > "$TEST_ENV_FILE" << EOF
# Test environment - auto-generated
DB_NAME=$DB_NAME
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASS
POSTGRES_PORT=$PG_PORT
SERVER_PORT=$JAVA_PORT
SPRING_PROFILES_ACTIVE=dev
SPRING_AI_OPENAI_API_KEY=$chat_api_key
SPRING_AI_OPENAI_BASE_URL=$chat_base_url
SPRING_AI_OPENAI_CHAT_MODEL=$chat_model
SPRING_AI_OPENAI_EMBEDDING_API_KEY=$embed_api_key
SPRING_AI_OPENAI_EMBEDDING_BASE_URL=$embed_base_url
SPRING_AI_OPENAI_EMBEDDING_MODEL=$embed_model
SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=$embed_dim
CLAUDE_MEM_MODE=code
EOF

    log_success "Test environment file created: $TEST_ENV_FILE"
}

build_docker_images() {
    if [ "$USE_PREBUILT_IMAGE" = true ]; then
        log_info "Using pre-built image from ghcr.io (skip local build)"
        log_info "To build locally, run with --build flag"
        return 0
    fi

    log_section "Building Docker Images"

    log_info "Building backend image..."
    
    # Build context must be the parent directory (claude-mem root) to access WebUI source files
    local build_context="$(cd "$PROJECT_ROOT/.." && pwd)"
    
    local build_output
    if build_output=$(docker build -t backend:test -f "$PROJECT_ROOT/Dockerfile" "$build_context" 2>&1); then
        log_success "Docker image built successfully"
        echo "$build_output" | tail -20
        return 0
    else
        log_fail "Docker image build failed"
        echo "$build_output" | tail -50
        return 1
    fi
}

start_with_compose() {
    log_section "Starting Services with Docker Compose"

    cd "$PROJECT_ROOT"
    
    # Copy test env to .env first
    cp "$TEST_ENV_FILE" "$PROJECT_ROOT/.env"
    
    # Determine which image to use
    local image_name
    if [ "$USE_PREBUILT_IMAGE" = false ]; then
        image_name="backend:test"
    else
        image_name="ghcr.io/wubuku/backend:latest"
    fi
    
    # Append test-specific overrides to .env (will override defaults)
    cat >> "$PROJECT_ROOT/.env" << EOF

# Test-specific overrides
IMAGE_NAME=$image_name
POSTGRES_PORT=$PG_PORT
SERVER_PORT=$JAVA_PORT
EOF
    
    log_info "Starting services with docker compose using docker-compose.yml..."
    
    if docker compose -f docker-compose.yml up -d 2>&1; then
        log_success "Services started with docker compose"
        
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
        docker compose -f docker-compose.yml logs --tail 50
        return 1
    else
        log_fail "Failed to start services with docker compose"
        return 1
    fi
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
            \"content_session_id\": \"$TEST_SESSION_ID\",
            \"project_path\": \"$TEST_PROJECT\",
            \"type\": \"feature\",
            \"title\": \"Compose Test Observation\",
            \"content\": \"This is a test observation from docker compose test.\",
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
    response=$(curl -sf "http://localhost:$JAVA_PORT/api/search?project=${TEST_PROJECT}&query=test&limit=5" 2>&1) || {
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

cleanup() {
    log_section "Cleanup"

    cd "$PROJECT_ROOT"

    if [ "$CLEANUP_AFTER" = true ]; then
        log_info "Removing containers and volumes..."
        
        docker compose -f docker-compose.yml down -v 2>/dev/null || true
        
        rm -f "$TEST_ENV_FILE" "$PROJECT_ROOT/.env"
        
        log_success "Cleanup complete"
    else
        log_info "Containers are still running. Use --cleanup to remove them."
        log_info "To stop: docker compose -f docker-compose.yml down"
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
    
    cd "$PROJECT_ROOT"
    
    docker compose -f docker-compose.yml logs --tail 30
}

main() {
    echo ""
    echo "=============================================="
    echo "  Claude-Mem Docker Compose Test Suite"
    echo "=============================================="
    echo ""
    echo "PostgreSQL Port: $PG_PORT"
    echo "Java API Port:   $JAVA_PORT"
    echo "Test Session:    $TEST_SESSION_ID"
    echo "Test Project:    $TEST_PROJECT"
    echo ""

    check_prerequisites || exit 1

    create_test_env_file

    build_docker_images || exit 1

    start_with_compose || {
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
