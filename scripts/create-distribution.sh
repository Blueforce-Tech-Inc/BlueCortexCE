#!/bin/bash

set -e

show_help() {
    cat << EOF
Usage: $(basename "$0") [options] <target_dir>

Create Cortex CE distribution package, packaging required files into specified directory.

Parameters:
  target_dir              Path to target distribution directory (required)

Options:
  -h, --help            Show this help message

Examples:
  $(basename "$0") ~/cortex-ce
  $(basename "$0") /path/to/dist
EOF
}

if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    show_help
    exit 0
fi

# Get absolute path of script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Get project root: parent of parent of script directory
# Assume script is located under claude-mem/java/scripts/
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"

# Target distribution directory
DIST_DIR="${1:-$DIST_DIR}"

if [[ -z "$DIST_DIR" ]]; then
    echo "Error: Target directory not specified"
    echo "Usage: $(basename "$0") <target_dir>"
    exit 1
fi

echo "Project root: ${PROJECT_ROOT}"
echo "Creating distribution to: ${DIST_DIR}"

# Clean old target directory if exists and create new directory
rm -rf "${DIST_DIR}"
mkdir -p "${DIST_DIR}"

# 1. Copy docker-compose.yml (includes Redis and Ollama service configuration)
cp "${PROJECT_ROOT}/java/docker-compose.yml" "${DIST_DIR}/"

# 2. Copy proxy directory (only necessary files, exclude node_modules, java test dirs and package-lock.json)
mkdir -p "${DIST_DIR}/proxy"

# Copy necessary files from proxy directory
for file in wrapper.js proxy.js tag-stripping.js package.json README.md CLAUDE-CODE-INTEGRATION.md CURSOR-INTEGRATION.md; do
    if [[ -f "${PROJECT_ROOT}/java/proxy/${file}" ]]; then
        cp "${PROJECT_ROOT}/java/proxy/${file}" "${DIST_DIR}/proxy/"
    fi
done

# 3. Copy README documentation
cp "${PROJECT_ROOT}/java/README.md" "${DIST_DIR}/README.md"

echo ""
echo "=== Done! ==="
echo ""
echo "Usage:"
echo "  1. cd ${DIST_DIR}"
echo "  2. cp .env.example .env"
echo "  3. Edit .env with your API keys"
echo "  4. docker compose up -d"
echo ""
