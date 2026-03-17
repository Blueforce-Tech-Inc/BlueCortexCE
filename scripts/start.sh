#!/bin/bash
# Claude-Mem Java Service Startup Script (dev profile)
#
# Usage: ./start.sh [--build]
#
# This script:
# 1. Loads environment variables from .env file
# 2. Builds the project (optional)
# 3. Starts the service with dev profile

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

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
for arg in "$@"; do
    case $arg in
        --build)
            BUILD=true
            shift
            ;;
    esac
done

# Kill any existing process on port 8080
kill_port() {
    local port=$1
    if lsof -ti:$port > /dev/null 2>&1; then
        echo "[start.sh] Killing existing process on port $port..."
        lsof -ti:$port | xargs -r kill -9 2>/dev/null || true
        sleep 1
    fi
    if lsof -ti:$port > /dev/null 2>&1; then
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
kill_port 8080

if [ "$BUILD" = true ]; then
    build_project
fi

# Load env vars again after build (in case .env changed)
load_env

echo "[start.sh] Starting Claude-Mem Java service with dev profile..."
java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
