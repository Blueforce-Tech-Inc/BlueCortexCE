#!/bin/bash

set -e

BASE_URL="http://127.0.0.1:37777"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${BLUE}=========================================="
    echo -e "LLM Provider Test: $1"
    echo -e "==========================================${NC}"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

print_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

test_llm_endpoint() {
    print_header "LLM Chat Completion Test"
    
    print_info "Sending request to: $BASE_URL/api/test/llm"
    print_info "Request time: $(date '+%Y-%m-%d %H:%M:%S')"
    
    echo ""
    print_info "Response:"
    
    HTTP_STATUS=$(curl -s -o /tmp/llm_response.txt -w "%{http_code}" "$BASE_URL/api/test/llm" 2>&1)
    BODY=$(cat /tmp/llm_response.txt)
    
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    
    if [ "$HTTP_STATUS" = "200" ]; then
        STATUS=$(echo "$BODY" | jq -r '.status' 2>/dev/null)
        if [ "$STATUS" = "success" ]; then
            print_success "LLM test passed"
            echo ""
            print_info "Response content:"
            echo "$BODY" | jq -r '.response' 2>/dev/null
            return 0
        else
            print_error "LLM test failed with status: $STATUS"
            print_info "Error message: $(echo "$BODY" | jq -r '.message' 2>/dev/null)"
            return 1
        fi
    else
        print_error "HTTP request failed with status: $HTTP_STATUS"
        return 1
    fi
}

test_embedding_endpoint() {
    print_header "Embedding Test"
    
    print_info "Sending request to: $BASE_URL/api/test/embedding"
    print_info "Request time: $(date '+%Y-%m-%d %H:%M:%S')"
    
    echo ""
    print_info "Response:"
    
    HTTP_STATUS=$(curl -s -o /tmp/embedding_response.txt -w "%{http_code}" "$BASE_URL/api/test/embedding" 2>&1)
    BODY=$(cat /tmp/embedding_response.txt)
    
    echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
    
    if [ "$HTTP_STATUS" = "200" ]; then
        STATUS=$(echo "$BODY" | jq -r '.status' 2>/dev/null)
        if [ "$STATUS" = "success" ]; then
            print_success "Embedding test passed"
            print_info "Dimensions: $(echo "$BODY" | jq -r '.dimensions' 2>/dev/null)"
            return 0
        elif [ "$STATUS" = "disabled" ]; then
            print_info "Embedding is disabled (no API key configured)"
            return 0
        else
            print_error "Embedding test failed with status: $STATUS"
            print_info "Error message: $(echo "$BODY" | jq -r '.message' 2>/dev/null)"
            return 1
        fi
    else
        print_error "HTTP request failed with status: $HTTP_STATUS"
        return 1
    fi
}

check_server_health() {
    print_header "Server Health Check"
    
    print_info "Checking server at: $BASE_URL/api/health"
    
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/health" 2>&1)
    
    if [ "$HTTP_STATUS" = "200" ]; then
        print_success "Server is running"
        return 0
    else
        print_error "Server is not responding (HTTP $HTTP_STATUS)"
        print_info "Please start the server first:"
        echo "  cd $PROJECT_ROOT/java/backend"
        echo "  java -jar target/backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev"
        return 1
    fi
}

show_current_provider() {
    print_header "Current LLM Provider Configuration"
    
    ENV_FILE="$PROJECT_ROOT/java/backend/.env"
    
    if [ -f "$ENV_FILE" ]; then
        print_info "Reading from: $ENV_FILE"
        echo ""
        while IFS= read -r line; do
            if [[ "$line" == *"API_KEY"* ]] || [[ "$line" == *"api-key"* ]]; then
                KEY=$(echo "$line" | cut -d'=' -f1)
                echo "  $KEY=***"
            else
                echo "  $line"
            fi
        done < <(grep -E "^(CLAUDEMEM_LLM_PROVIDER|OPENAI_|ANTHROPIC_|SILICONFLOW_)" "$ENV_FILE" 2>/dev/null || true)
    else
        print_info "No .env file found at $ENV_FILE"
    fi
}

main() {
    echo ""
    echo "=========================================="
    echo "  LLM Provider Test Script"
    echo "=========================================="
    echo ""
    
    print_info "Project root: $PROJECT_ROOT"
    
    show_current_provider
    echo ""
    
    if ! check_server_health; then
        exit 1
    fi
    
    echo ""
    FAILED=0
    
    if ! test_llm_endpoint; then
        FAILED=1
    fi
    
    echo ""
    
    if ! test_embedding_endpoint; then
        FAILED=1
    fi
    
    echo ""
    
    print_header "Test Summary"
    
    if [ $FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        exit 0
    else
        echo -e "${RED}Some tests failed!${NC}"
        echo ""
        print_info "Check server logs for details:"
        echo "  tail -f ~/.claude-mem/logs/claude-mem.log"
        exit 1
    fi
}

if [ "$1" = "--llm" ]; then
    test_llm_endpoint
elif [ "$1" = "--embedding" ]; then
    test_embedding_endpoint
elif [ "$1" = "--health" ]; then
    check_server_health
elif [ "$1" = "--config" ]; then
    show_current_provider
else
    main
fi
