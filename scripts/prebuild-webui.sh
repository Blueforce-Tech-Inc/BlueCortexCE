#!/usr/bin/env bash
#
# Prebuild Script: Prepare WebUI resources for Docker build
#
# Usage:
#   ./prebuild-webui.sh           # Copy WebUI to correct location
#   ./prebuild-webui.sh --clean   # Cleanup copied files
#
# This script solves the issue where git submodules cannot be automatically resolved during Docker build
# After running this script, you can run docker build directly
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_DIR="$(dirname "$SCRIPT_DIR")"
WEBUI_DIR="$JAVA_DIR/webui"
STATIC_DIR="$JAVA_DIR/backend/src/main/resources/static"

green() { printf "\033[32m%s\033[0m\n" "$*"; }
yellow() { printf "\033[33m%s\033[0m\n" "$*"; }
red() { printf "\033[31m%s\033[0m\n" "$*"; }
bold() { printf "\033[1m%s\033[0m\n" "$*"; }

# Parse arguments
CLEAN=false
if [ "$1" = "--clean" ]; then
    CLEAN=true
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
bold "WebUI Prebuild Script"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check if submodule exists
if [ ! -d "$WEBUI_DIR" ]; then
    red "Error: WebUI submodule does not exist"
    echo ""
    echo "Please run the following command to initialize submodule:"
    echo "  git submodule update --init --recursive"
    echo ""
    exit 1
fi

# Check WebUI source files
if [ ! -d "$WEBUI_DIR/plugin/ui" ]; then
    red "Error: WebUI plugin/ui directory does not exist"
    exit 1
fi

# Cleanup mode
if [ "$CLEAN" = true ]; then
    bold "Cleaning up copied files..."
    rm -f "$STATIC_DIR"/*.html
    rm -f "$STATIC_DIR"/*.js
    rm -rf "$STATIC_DIR"/assets
    rm -f "$STATIC_DIR"/*.svg
    rm -f "$STATIC_DIR"/*.webp
    green "✓ Cleanup complete"
    exit 0
fi

# Create target directory
mkdir -p "$STATIC_DIR"

# Copy WebUI static files
bold "Copying WebUI static files to Spring Boot resources directory..."
echo ""

# HTML
if [ -f "$WEBUI_DIR/plugin/ui/viewer.html" ]; then
    cp "$WEBUI_DIR/plugin/ui/viewer.html" "$STATIC_DIR/index.html"
    green "✓ viewer.html -> index.html"
fi

# JS Bundle
if [ -f "$WEBUI_DIR/plugin/ui/viewer-bundle.js" ]; then
    cp "$WEBUI_DIR/plugin/ui/viewer-bundle.js" "$STATIC_DIR/"
    green "✓ viewer-bundle.js"
fi

# Assets
if [ -d "$WEBUI_DIR/plugin/ui/assets" ]; then
    cp -r "$WEBUI_DIR/plugin/ui/assets" "$STATIC_DIR/"
    green "✓ assets/"
fi

# SVG icons
if [ -d "$WEBUI_DIR/plugin/ui" ]; then
    cp "$WEBUI_DIR/plugin/ui"/icon-thick-*.svg "$STATIC_DIR/" 2>/dev/null || true
    cp "$WEBUI_DIR/plugin/ui"/icon-thin-*.svg "$STATIC_DIR/" 2>/dev/null || true
    green "✓ icon-*.svg"
fi

# WebP logos
cp "$WEBUI_DIR/plugin/ui"/*.webp "$STATIC_DIR/" 2>/dev/null || true
green "✓ *.webp"

echo ""
bold "WebUI resources ready!"
echo ""
echo "Now you can run:"
echo "  docker build -t cortex-ce:latest -f java/Dockerfile ."
echo ""
echo "Or use docker-compose:"
echo "  docker-compose -f java/docker-compose.yml up --build"
echo ""
