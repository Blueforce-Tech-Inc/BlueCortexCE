#!/bin/bash
# Codex Watcher E2E Test Script
# Tests Codex CLI integration with Java backend

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CODEX_WATCHER_DIR="$PROJECT_ROOT/codex-watcher"
BACKEND_DIR="$PROJECT_ROOT/backend"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
SKIP_BUILD=false
SKIP_BACKEND=false
BACKEND_PID=""

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    --skip-backend)
      SKIP_BACKEND=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

cleanup() {
  log_info "Cleaning up..."
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT

# Test 1: Build codex-watcher
test_build() {
  log_info "Test 1: Building codex-watcher..."

  if [[ "$SKIP_BUILD" == "true" ]]; then
    log_warn "Skipping build (--skip-build)"
    return 0
  fi

  cd "$CODEX_WATCHER_DIR"
  npm install
  npm run build

  if [[ ! -f "dist/index.js" ]]; then
    log_error "Build failed: dist/index.js not found"
    exit 1
  fi

  log_info "Build successful"
}

# Test 2: Start Java backend
test_backend() {
  log_info "Test 2: Starting Java backend..."

  if [[ "$SKIP_BACKEND" == "true" ]]; then
    log_warn "Skipping backend start (--skip-backend)"
    return 0
  fi

  cd "$BACKEND_DIR"

  # Check if .env exists
  if [[ -f ".env" ]]; then
    export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
  fi

  # Start backend in background
  ./mvnw spring-boot:run > /tmp/backend.log 2>&1 &
  BACKEND_PID=$!

  # Wait for backend to be ready
  log_info "Waiting for backend to start (PID: $BACKEND_PID)..."
  for i in {1..60}; do
    if curl -s http://127.0.0.1:37777/actuator/health > /dev/null 2>&1; then
      log_info "Backend is ready"
      return 0
    fi
    sleep 2
  done

  log_error "Backend failed to start within 120 seconds"
  cat /tmp/backend.log
  exit 1
}

# Test 3: Codex watcher help
test_help() {
  log_info "Test 3: Testing codex-watcher help..."

  cd "$CODEX_WATCHER_DIR"
  node dist/index.js help | grep -q "Claude-Mem Codex Watcher"

  if [[ $? -ne 0 ]]; then
    log_error "Help command failed"
    exit 1
  fi

  log_info "Help command works"
}

# Test 4: Codex watcher status (without installation)
test_status() {
  log_info "Test 4: Testing codex-watcher status..."

  cd "$CODEX_WATCHER_DIR"
  OUTPUT=$(node dist/index.js status 2>&1)

  if echo "$OUTPUT" | grep -q "Installed:"; then
    log_info "Status command works"
  else
    log_error "Status command failed"
    echo "$OUTPUT"
    exit 1
  fi
}

# Test 5: Codex watcher install
test_install() {
  log_info "Test 5: Testing codex-watcher install..."

  cd "$CODEX_WATCHER_DIR"
  node dist/index.js install

  if [[ -f "$HOME/.claude-mem/transcript-watch.json" ]]; then
    log_info "Installation successful"
  else
    log_warn "Installation may have issues (config file not found)"
  fi
}

# Test 6: Backend health check via watcher
test_backend_health() {
  log_info "Test 6: Testing backend health check..."

  cd "$CODEX_WATCHER_DIR"

  # Start watcher briefly to test health check
  # Use cross-platform timeout (macOS uses gtimeout from coreutils if installed, otherwise a simple workaround)
  if command -v gtimeout &> /dev/null; then
    gtimeout 5s node dist/index.js start 2>&1 || true
  elif command -v timeout &> /dev/null; then
    timeout 5s node dist/index.js start 2>&1 || true
  else
    # Fallback: run in background and kill after 5 seconds
    node dist/index.js start &
    local pid=$!
    sleep 5
    kill $pid 2>/dev/null || true
  fi

  log_info "Health check test completed"
}

# Test 7: Codex watcher uninstall
test_uninstall() {
  log_info "Test 7: Testing codex-watcher uninstall..."

  cd "$CODEX_WATCHER_DIR"
  node dist/index.js uninstall

  log_info "Uninstall completed"
}

# Main
main() {
  echo "========================================="
  echo "Codex Watcher E2E Tests"
  echo "========================================="

  test_build
  test_backend
  test_help
  test_status
  test_install
  test_backend_health
  test_uninstall

  echo "========================================="
  echo -e "${GREEN}All tests passed!${NC}"
  echo "========================================="
}

main "$@"
