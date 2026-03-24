#!/bin/bash
# java-sdk-e2e-test.sh — Java SDK Demo 端到端验收测试
# 覆盖链路：E2E 测试脚本 → Java SDK Demo API 端点 → Java SDK → Backend API
#
# 前提条件：
# 1. Backend 服务运行中 (port 37777)
# 2. Java Demo 运行中 (port 8080)
#
# 运行方式：
#   bash scripts/java-sdk-e2e-test.sh

set -e

DEMO_BASE="http://localhost:8080/demo"
BACKEND_URL="http://127.0.0.1:37777"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

TOTAL=0
PASSED=0
FAILED=0

pass() { ((TOTAL++)); ((PASSED++)); echo -e "${GREEN}✅ PASS${NC}: $1"; }
fail() { ((TOTAL++)); ((FAILED++)); echo -e "${RED}❌ FAIL${NC}: $1"; }
info() { echo -e "${YELLOW}ℹ️  $1${NC}"; }

echo "=========================================="
echo "Java SDK Demo E2E 验收测试"
echo "=========================================="
echo ""

# Check backend is running
info "检查 Backend 服务..."
if ! curl -sf "$BACKEND_URL/api/health" > /dev/null 2>&1; then
    echo "❌ Backend 服务未运行! 请先启动: java -jar backend/target/cortex-ce-*.jar"
    exit 1
fi
pass "Backend 服务正常"

# Check demo is running
info "检查 Java Demo 服务..."
if ! curl -sf "$DEMO_BASE/../actuator/health" > /dev/null 2>&1; then
    echo "❌ Java Demo 服务未运行! 请先启动 demo"
    exit 1
fi
pass "Java Demo 服务正常"

echo ""
echo "--- 原有 API 测试 ---"

# Test 1: Memory Experiences
info "Test 1: /demo/memory/experiences"
RESP=$(curl -sf "$DEMO_BASE/memory/experiences?project=/tmp/e2e-test&query=demo&limit=5" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "Memory Experiences"
else
    fail "Memory Experiences"
fi

# Test 2: ICL Prompt
info "Test 2: /demo/memory/icl"
RESP=$(curl -sf "$DEMO_BASE/memory/icl?project=/tmp/e2e-test&task=test&maxChars=500" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "ICL Prompt"
else
    fail "ICL Prompt"
fi

# Test 3: Session Lifecycle
info "Test 3: /demo/session"
RESP=$(curl -sf "$DEMO_BASE/session/start?project=/tmp/e2e-test" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "Session Start"
else
    fail "Session Start"
fi

# Test 4: Projects
info "Test 4: /demo/projects"
RESP=$(curl -sf "$DEMO_BASE/projects" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "Projects"
else
    fail "Projects"
fi

# Test 5: Quality Distribution
info "Test 5: /demo/memory/quality"
RESP=$(curl -sf "$DEMO_BASE/memory/quality?project=/tmp/e2e-test" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "Quality Distribution"
else
    fail "Quality Distribution"
fi

echo ""
echo "--- 新增 P0/P1 API 测试 ---"

# Test 6: Search API (P0)
info "Test 6: /demo/search"
RESP=$(curl -sf "$DEMO_BASE/search?project=/tmp/e2e-test&query=demo&limit=10" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ] && echo "$RESP" | grep -q '"observations"'; then
    pass "Search API (P0)"
else
    fail "Search API (P0)"
fi

# Test 7: List Observations (P0)
info "Test 7: /demo/observations"
RESP=$(curl -sf "$DEMO_BASE/observations?project=/tmp/e2e-test&limit=10&offset=0" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ] && echo "$RESP" | grep -q '"observations"'; then
    pass "List Observations (P0)"
else
    fail "List Observations (P0)"
fi

# Test 8: Batch Observations (P0)
info "Test 8: /demo/observations/batch"
RESP=$(curl -sf -X POST "$DEMO_BASE/observations/batch" \
    -H "Content-Type: application/json" \
    -d '{"ids": ["test-id-1"]}' 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "Batch Observations (P0)"
else
    fail "Batch Observations (P0)"
fi

# Test 9: Version API (P1)
info "Test 9: /demo/manage/version"
RESP=$(curl -sf "$DEMO_BASE/manage/version" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ] && echo "$RESP" | grep -q '"version"'; then
    pass "Version API (P1)"
else
    fail "Version API (P1)"
fi

# Test 10: Stats API (P1)
info "Test 10: /demo/manage/stats"
RESP=$(curl -sf "$DEMO_BASE/manage/stats?project=/tmp/e2e-test" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "Stats API (P1)"
else
    fail "Stats API (P1)"
fi

# Test 11: Modes API (P1)
info "Test 11: /demo/manage/modes"
RESP=$(curl -sf "$DEMO_BASE/manage/modes" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "Modes API (P1)"
else
    fail "Modes API (P1)"
fi

# Test 12: Settings API (P1)
info "Test 12: /demo/manage/settings"
RESP=$(curl -sf "$DEMO_BASE/manage/settings" 2>/dev/null || echo "FAIL")
if [ "$RESP" != "FAIL" ]; then
    pass "Settings API (P1)"
else
    fail "Settings API (P1)"
fi

echo ""
echo "=========================================="
echo "测试结果: $PASSED/$TOTAL passed, $FAILED failed"
echo "=========================================="

if [ $FAILED -gt 0 ]; then
    exit 1
fi

echo "🎉 Java SDK Demo E2E 测试全部通过!"
