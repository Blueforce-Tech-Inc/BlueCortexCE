#!/bin/bash
# Claude-Mem Java Service Startup Script (dev profile)
#
# Usage: ./start.sh [--build] [--background]
#
# This script:
# 1. Loads environment variables from .env file
# 2. Builds the project (optional)
# 3. Starts the service with dev profile

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
cd "$BACKEND_DIR"

# Load environment variables from .env file
load_env() {
    if [ -f ".env" ]; then
        # Read all non-comment lines and export them
        set -a
        while IFS= read -r line || [ -n "$line" ]; do
            # Skip empty lines and comments
            [[ "$line" =~ ^[[:space:]]*# ]] && continue
            [[ -z "${line// /}" ]] && continue
            export "$line"
        done < .env
        set +a
        echo "[start.sh] Loaded environment variables from .env"
    else
        echo "[start.sh] Warning: .env file not found, using system environment variables only"
    fi
}

# Parse arguments
BUILD=false
BACKGROUND=false
for arg in "$@"; do
    case $arg in
        --build)
            BUILD=true
            shift
            ;;
        --background)
            BACKGROUND=true
            shift
            ;;
    esac
done

# Scheduled maintenance always uses the dedicated backend port. Ignore a
# stale SERVER_PORT from a local shell instead of accidentally using 8080.
SERVER_PORT=37777
JAR_PATH="$BACKEND_DIR/target/cortex-ce-0.1.0-beta.jar"

# Kill an existing CortexCE process on the dedicated backend port. The
# backend intentionally uses 37777 rather than a common development port.
kill_port() {
    local port=$1
    if lsof -nP -tiTCP:"$port" -sTCP:LISTEN > /dev/null 2>&1; then
        echo "[start.sh] Stopping existing process on port $port..."
        while IFS= read -r pid; do
            [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
        done < <(lsof -nP -tiTCP:"$port" -sTCP:LISTEN)
        sleep 1
    fi
    if lsof -nP -tiTCP:"$port" -sTCP:LISTEN > /dev/null 2>&1; then
        echo "[start.sh] Warning: Port $port still in use"
        return 1
    fi
    echo "[start.sh] Port $port is free"
    return 0
}

# Build the project
build_project() {
    echo "[start.sh] Building project with Maven..."
    ./mvnw clean package -DskipTests -q
    echo "[start.sh] Build completed"
}

# Main
load_env
SERVER_PORT=37777
kill_port "$SERVER_PORT"

if [ "$BUILD" = true ]; then
    build_project
fi

# Load env vars again after build (in case .env changed)
load_env
SERVER_PORT=37777

echo "[start.sh] Starting Claude-Mem Java service with dev profile..."
if [[ ! -f "$JAR_PATH" ]]; then
    echo "[start.sh] Missing $JAR_PATH; rerun with --build" >&2
    exit 1
fi

JAVA_ARGS=(-jar "$JAR_PATH" --spring.profiles.active=dev --server.port="$SERVER_PORT")
if [ "$BACKGROUND" = true ]; then
    LOG_PATH="$BACKEND_DIR/target/cortex-ce.log"
    nohup java "${JAVA_ARGS[@]}" >"$LOG_PATH" 2>&1 &
    JAVA_PID=$!
    echo "$JAVA_PID" > "$BACKEND_DIR/target/cortex-ce.pid"
    echo "[start.sh] Started in background (pid $JAVA_PID); log: $LOG_PATH"
    echo "[start.sh] Waiting for http://127.0.0.1:$SERVER_PORT/api/health..."
    for attempt in $(seq 1 60); do
        if curl -sf --max-time 2 "http://127.0.0.1:$SERVER_PORT/api/health" > /dev/null 2>&1; then
            echo "[start.sh] Service is ready on port $SERVER_PORT"
            exit 0
        fi
        if ! kill -0 "$JAVA_PID" 2>/dev/null; then
            echo "[start.sh] Service exited before becoming ready; see $LOG_PATH" >&2
            tail -40 "$LOG_PATH" >&2 || true
            exit 1
        fi
        sleep 1
    done
    echo "[start.sh] Service did not become ready within 60 seconds; see $LOG_PATH" >&2
    exit 1
else
    exec java "${JAVA_ARGS[@]}"
fi
