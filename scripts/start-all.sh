#!/bin/bash
# Claude-Mem Full Stack Startup Script
#
# Starts both the Java backend and Thin Proxy
#
# Usage: ./start-all.sh [--build] [--no-proxy]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Load environment variables
load_env() {
    if [ -f "../.env" ]; then
        set -a
        while IFS= read -r line || [ -n "$line" ]; do
            [[ "$line" =~ ^[[:space:]]*# ]] && continue
            [[ -z "${line// /}" ]] && continue
            export "$line"
        done < "../.env"
        set +a
        echo "[start-all] Loaded environment variables"
    fi
}

# Kill existing processes
kill_port() {
    local port=$1
    local name=$2
    if lsof -ti:$port > /dev/null 2>&1; then
        echo "[start-all] Stopping existing $name on port $port..."
        lsof -ti:$port | xargs -r kill -9 2>/dev/null || true
        sleep 1
    fi
}

# Parse arguments
BUILD=false
NO_PROXY=false
for arg in "$@"; do
    case $arg in
        --build)
            BUILD=true
            shift
            ;;
        --no-proxy)
            NO_PROXY=true
            shift
            ;;
    esac
done

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Claude-Mem Full Stack Startup                      ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# Load env vars
load_env

# Kill existing processes
kill_port 8080 "Java backend"
kill_port 37778 "Thin Proxy"

# Build Java if requested
if [ "$BUILD" = true ]; then
    echo "[start-all] Building Java backend..."
    cd ../backend
    ./mvnw clean package -DskipTests -q
    echo "[start-all] Build complete"
    cd "$SCRIPT_DIR"
fi

# Install proxy dependencies if needed
if [ "$NO_PROXY" = false ]; then
    if [ ! -d "../proxy/node_modules" ]; then
        echo "[start-all] Installing proxy dependencies..."
        cd ../proxy
        npm install --silent 2>/dev/null || npm install
        cd "$SCRIPT_DIR"
        echo "[start-all] Proxy dependencies installed"
    fi
fi

# Load env vars again (in case changed)
load_env

# Start Java backend
echo "[start-all] Starting Java backend..."
cd ../backend
java -jar target/backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev > /tmp/claude-mem.log 2>&1 &
JAVA_PID=$!
cd "$SCRIPT_DIR"

echo "[start-all] Java backend started (PID: $JAVA_PID)"

# Wait for Java to be ready
echo "[start-all] Waiting for Java backend..."
for i in {1..30}; do
    if curl -sf http://127.0.0.1:37777/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}[OK]${NC} Java backend is ready"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}[FAIL]${NC} Java backend failed to start"
        kill $JAVA_PID 2>/dev/null || true
        exit 1
    fi
    sleep 1
done

# Start Thin Proxy
if [ "$NO_PROXY" = false ]; then
    echo "[start-all] Starting Thin Proxy..."
    cd ../proxy
    node proxy.js > /tmp/claude-proxy.log 2>&1 &
    PROXY_PID=$!
    cd "$SCRIPT_DIR"

    echo "[start-all] Thin Proxy started (PID: $PROXY_PID)"

    # Wait for proxy to be ready
    sleep 1
    if curl -sf http://127.0.0.1:37778/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}[OK]${NC} Thin Proxy is ready"
    else
        # Proxy doesn't have health endpoint, just check it's running
        if kill -0 $PROXY_PID 2>/dev/null; then
            echo -e "${GREEN}[OK]${NC} Thin Proxy is running"
        else
            echo -e "${YELLOW}[WARN]${NC} Thin Proxy may have failed to start"
        fi
    fi
fi

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Services Started                                   ║"
echo "╠══════════════════════════════════════════════════════╣"
echo "║  Java Backend:  http://127.0.0.1:37777             ║"
if [ "$NO_PROXY" = false ]; then
echo "║  Thin Proxy:    http://127.0.0.1:37778             ║"
else
echo "║  Thin Proxy:    (disabled with --no-proxy)         ║"
fi
echo "╚══════════════════════════════════════════════════════╝"
echo ""
echo "Logs:"
echo "  Java:   tail -f /tmp/claude-mem.log"
echo "  Proxy: tail -f /tmp/claude-proxy.log"
echo ""
