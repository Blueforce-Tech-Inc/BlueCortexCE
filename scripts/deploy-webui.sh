#!/bin/bash
# Deploy WebUI to Java Spring Boot static resources
# Usage: ./deploy-webui.sh [--build | --rebuild]
#
# This script deploys WebUI from the submodule (java/webui) to Java static resources.
#
# Options:
#   --build, --rebuild  Build WebUI before deploying
#   --help             Show this help message

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_DIR="$(dirname "$SCRIPT_DIR")"
WEBUI_DIR="$JAVA_DIR/webui"
PLUGIN_UI="$WEBUI_DIR/plugin/ui"
JAVA_STATIC="$JAVA_DIR/backend/src/main/resources/static"

# Show help
show_help() {
    head -7 "$0" | tail -5
    echo ""
    echo "Deploy WebUI bundle files from plugin/ui to Java Spring Boot static resources."
    echo ""
    echo "Options:"
    echo "  --build, --rebuild   Build WebUI before deploying (recommended)"
    echo "  --help               Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./deploy-webui.sh                 # Deploy existing files (may be outdated)"
    echo "  ./deploy-webui.sh --build         # Build then deploy"
    echo "  ./deploy-webui.sh --rebuild       # Force rebuild then deploy"
    exit 0
}

# Parse arguments
BUILD=false
for arg in "$@"; do
    case "$arg" in
        --help|-h)
            show_help
            ;;
        --build|--rebuild)
            BUILD=true
            ;;
        *)
            ;;
    esac
done

echo "=== WebUI Deployment Script ==="
echo "Source: $PLUGIN_UI"
echo "Target: $JAVA_STATIC"
echo ""

# Warn if not building
if [ "$BUILD" = false ]; then
    echo "WARNING: No --build or --rebuild flag specified."
    echo "         Deploying existing WebUI files (may be outdated)."
    echo "         Run with --build to rebuild first."
    echo ""
fi

# Check if source exists
if [ ! -d "$PLUGIN_UI" ]; then
    echo "ERROR: Plugin UI directory not found at $PLUGIN_UI"
    echo "Please run 'node scripts/build-viewer.js' first to build the viewer,"
    echo "or use '--rebuild' / '--build' option."
    exit 1
fi

# Check if viewer.html exists
if [ ! -f "$PLUGIN_UI/viewer.html" ]; then
    echo "ERROR: viewer.html not found in $PLUGIN_UI"
    echo "Please run 'node scripts/build-viewer.js' first to build the viewer,"
    echo "or use '--rebuild' / '--build' option."
    exit 1
fi

# Rebuild if requested
if [ "$BUILD" = true ]; then
    echo "Rebuilding WebUI..."
    cd "$WEBUI_DIR"
    node scripts/build-viewer.js
    echo ""
fi

# Create target directory if needed
mkdir -p "$JAVA_STATIC"
mkdir -p "$JAVA_STATIC/assets"

# Copy viewer files
echo "Copying viewer files..."
cp "$PLUGIN_UI/viewer.html" "$JAVA_STATIC/index.html"
cp "$PLUGIN_UI/viewer-bundle.js" "$JAVA_STATIC/viewer-bundle.js"

# Copy assets (fonts, etc.)
if [ -d "$PLUGIN_UI/assets" ]; then
    cp -r "$PLUGIN_UI/assets/"* "$JAVA_STATIC/assets/" 2>/dev/null || true
fi

# Copy icons
for icon in "$PLUGIN_UI"/icon-*.svg; do
    if [ -f "$icon" ]; then
        cp "$icon" "$JAVA_STATIC/"
    fi
done

# Copy logos
for logo in "$PLUGIN_UI"/*.webp; do
    if [ -f "$logo" ]; then
        cp "$logo" "$JAVA_STATIC/"
    fi
done

echo ""
echo "=== Deployment Complete ==="
echo "Files deployed to: $JAVA_STATIC"
ls -la "$JAVA_STATIC"
echo ""
echo "Restart Java service to pick up changes:"
echo "  lsof -ti:37777 | xargs -r kill -9"
echo "  cd java/backend && java -jar target/backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev"
