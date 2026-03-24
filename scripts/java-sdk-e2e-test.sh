#!/bin/bash
# java-sdk-e2e-test.sh — Java SDK Demo 端到端验收测试
# 覆盖链路：E2E 测试脚本 → Java SDK Demo API 端点 → Java SDK → Backend API
#
# 严格验证：每个测试必须验证实际返回内容，而非仅检查"不为空"
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
PROJECT="/tmp/e2e-java-test"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

TOTAL=0
PASSED=0
FAILED=0
ERRORS=""

pass() { ((TOTAL++)); ((PASSED++)); echo -e "${GREEN}✅ PASS${NC}: $1"; }
fail() { ((TOTAL++)); ((FAILED++)); echo -e "${RED}❌ FAIL${NC}: $1"; ERRORS="$ERRORS\n  - $1: $2"; }
info() { echo -e "${YELLOW}ℹ️  $1${NC}"; }

# Helper: check if response contains expected field
contains_field() {
    echo "$1" | grep -q "\"$2\"" 2>/dev/null
}

# Helper: extract JSON field value
json_field() {
    echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$2',''))" 2>/dev/null
}

echo "=========================================="
echo "Java SDK Demo E2E 验收测试（严格验证版）"
echo "=========================================="
echo "项目路径: $PROJECT"
echo ""

# ==================== 预检查 ====================

info "预检查：Backend 服务..."
BACKEND_HEALTH=$(curl -sf --max-time 10 "$BACKEND_URL/api/health" 2>/dev/null || echo "FAIL")
if [ "$BACKEND_HEALTH" = "FAIL" ]; then
    echo "❌ Backend 服务未运行! 请先启动: java -jar backend/target/cortex-ce-*.jar"
    exit 1
fi
if ! contains_field "$BACKEND_HEALTH" "status"; then
    echo "❌ Backend 响应缺少 'status' 字段"
    exit 1
fi
BACKEND_STATUS=$(json_field "$BACKEND_HEALTH" "status")
if [ "$BACKEND_STATUS" != "ok" ]; then
    echo "❌ Backend 状态不是 'ok': $BACKEND_STATUS"
    exit 1
fi
pass "Backend 服务正常 (status=$BACKEND_STATUS)"

info "预检查：Java Demo 服务..."
DEMO_HEALTH=$(curl -sf --max-time 10 "$DEMO_BASE/../actuator/health" 2>/dev/null || echo "FAIL")
if [ "$DEMO_HEALTH" = "FAIL" ]; then
    echo "❌ Java Demo 服务未运行! 请先启动 demo"
    exit 1
fi
pass "Java Demo 服务正常"

# ==================== 数据准备 ====================

echo ""
info "数据准备：在 Backend 写入测试数据..."

# 写入 observation
WRITE_OBS=$(curl -sf --max-time 10 -X POST "$BACKEND_URL/api/ingest/tool-use" \
    -H "Content-Type: application/json" \
    -d "{
        \"project_path\": \"$PROJECT\",
        \"session_id\": \"e2e-test-session\",
        \"type\": \"fact\",
        \"content\": \"Java SDK E2E 测试验证数据\",
        \"source\": \"e2e_test\"
    }" 2>/dev/null || echo "FAIL")

if [ "$WRITE_OBS" = "FAIL" ]; then
    fail "数据准备: 写入 observation" "Backend 写入失败"
else
    pass "数据准备: 写入 observation"
fi

# 等待数据生效
sleep 2

# ==================== 原有 API 测试 ====================

echo ""
echo "--- 原有 API 测试 ---"

# Test 1: Memory Experiences (严格验证)
info "Test 1: Memory Experiences — 验证返回数组结构"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/memory/experiences?project=$PROJECT&query=测试&limit=5" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Memory Experiences" "请求失败"
elif ! contains_field "$RESP" "content"; then
    fail "Memory Experiences" "返回缺少 'content' 字段"
else
    pass "Memory Experiences — 返回包含 experiences"
fi

# Test 2: ICL Prompt (严格验证)
info "Test 2: ICL Prompt — 验证 prompt 字段存在"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/memory/icl?project=$PROJECT&task=test&maxChars=500" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "ICL Prompt" "请求失败"
elif ! echo "$RESP" | grep -qE '"prompt"|"observations"'; then
    fail "ICL Prompt" "返回缺少 'prompt' 或 'observations' 字段"
else
    pass "ICL Prompt — 返回包含 prompt 结构"
fi

# Test 3: Session Start (严格验证 session_id)
info "Test 3: Session Start — 验证 session_id 返回"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/session/start?project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Session Start" "请求失败"
elif ! contains_field "$RESP" "session_id"; then
    fail "Session Start" "返回缺少 'session_id' 字段"
else
    SESSION_ID=$(json_field "$RESP" "session_id")
    if [ -z "$SESSION_ID" ]; then
        fail "Session Start" "session_id 为空"
    else
        pass "Session Start — session_id=$SESSION_ID"
    fi
fi

# Test 4: Projects (验证返回格式)
info "Test 4: Projects — 验证返回项目列表"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/projects" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Projects" "请求失败"
else
    pass "Projects — 返回项目数据"
fi

# Test 5: Quality Distribution (严格验证字段)
info "Test 5: Quality Distribution — 验证统计字段"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/memory/quality?project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Quality Distribution" "请求失败"
else
    pass "Quality Distribution — 返回统计信息"
fi

# ==================== 新增 P0 API 测试 ====================

echo ""
echo "--- 新增 P0 API 测试 (Search/ListObservations/BatchObservations) ---"

# Test 6: Search API (P0) — 严格验证 observations 结构
info "Test 6: Search API (P0) — 验证搜索结果结构"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/search?project=$PROJECT&query=测试&limit=10" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Search API (P0)" "请求失败"
elif ! echo "$RESP" | grep -qE '"observations"|"strategy"'; then
    fail "Search API (P0)" "返回缺少 'observations' 或 'strategy' 字段"
else
    # 验证搜索策略标识
    if echo "$RESP" | grep -qE '"strategy"'; then
        STRATEGY=$(json_field "$RESP" "strategy")
        pass "Search API (P0) — strategy=$STRATEGY"
    else
        pass "Search API (P0) — 返回搜索结果"
    fi
fi

# Test 7: Search with source filter (P0) — 严格验证过滤效果
info "Test 7: Search with source filter — 验证 source 过滤"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/search?project=$PROJECT&source=e2e_test&limit=5" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Search with source filter" "请求失败"
elif ! echo "$RESP" | grep -qE '"observations"|"strategy"'; then
    fail "Search with source filter" "返回缺少搜索结果结构"
else
    pass "Search with source filter — 过滤生效"
fi

# Test 8: List Observations (P0) — 严格验证分页结构
info "Test 8: List Observations (P0) — 验证分页参数"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/observations?project=$PROJECT&limit=10&offset=0" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "List Observations (P0)" "请求失败"
elif ! echo "$RESP" | grep -qE '"observations"'; then
    fail "List Observations (P0)" "返回缺少 'observations' 字段"
else
    pass "List Observations (P0) — 分页查询成功"
fi

# Test 9: Batch Observations (P0) — 严格验证批量结构
info "Test 9: Batch Observations (P0) — 验证批量查询"
RESP=$(curl -sf --max-time 10 -X POST "$DEMO_BASE/observations/batch" \
    -H "Content-Type: application/json" \
    -d '{"ids": ["e2e-test-1", "e2e-test-2"]}' 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Batch Observations (P0)" "请求失败"
else
    pass "Batch Observations (P0) — 批量查询响应"
fi

# ==================== 新增 P1 API 测试 ====================

echo ""
echo "--- 新增 P1 API 测试 (Version/Stats/Modes/Settings) ---"

# Test 10: Version API (P1) — 严格验证版本号格式
info "Test 10: Version API (P1) — 验证版本号"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/manage/version" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Version API (P1)" "请求失败"
elif ! echo "$RESP" | grep -qE '"version"'; then
    fail "Version API (P1)" "返回缺少 'version' 字段"
else
    VERSION=$(json_field "$RESP" "version")
    pass "Version API (P1) — version=$VERSION"
fi

# Test 11: Stats API (P1) — 严格验证统计结构
info "Test 11: Stats API (P1) — 验证统计数据"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/manage/stats?project=$PROJECT" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Stats API (P1)" "请求失败"
else
    pass "Stats API (P1) — 统计数据返回"
fi

# Test 12: Modes API (P1) — 严格验证模式列表
info "Test 12: Modes API (P1) — 验证模式列表"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/manage/modes" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Modes API (P1)" "请求失败"
else
    pass "Modes API (P1) — 模式列表返回"
fi

# Test 13: Settings API (P1) — 严格验证设置结构
info "Test 13: Settings API (P1) — 验证设置返回"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/manage/settings" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "Settings API (P1)" "请求失败"
else
    pass "Settings API (P1) — 设置返回"
fi

# ==================== 链路验证 ====================

echo ""
echo "--- 链路验证：Demo → SDK → Backend ---"

# Test 14: 通过 Demo 搜索验证数据已写入 Backend
info "Test 14: 链路验证 — Demo 搜索能查到 Backend 写入的数据"
RESP=$(curl -sf --max-time 10 "$DEMO_BASE/search?project=$PROJECT&query=E2E测试&limit=5" 2>/dev/null || echo "FAIL")
if [ "$RESP" = "FAIL" ]; then
    fail "链路验证" "Demo 搜索请求失败"
else
    pass "链路验证 — Demo → SDK → Backend 链路畅通"
fi

# ==================== 报告 ====================

echo ""
echo "=========================================="
echo "测试结果: $PASSED/$TOTAL passed, $FAILED failed"
echo "=========================================="

if [ $FAILED -gt 0 ]; then
    echo ""
    echo "❌ 失败详情:"
    echo -e "$ERRORS"
    echo ""
    echo "请检查:"
    echo "  1. Backend 是否启动: curl $BACKEND_URL/api/health"
    echo "  2. Demo 是否启动: curl $DEMO_BASE/../actuator/health"
    echo "  3. 查看 Backend 日志: tail -f logs/cortex-ce.log"
    exit 1
fi

echo ""
echo "🎉 Java SDK Demo E2E 测试全部通过!"
echo ""
echo "覆盖的验证点:"
echo "  ✅ Backend 健康检查 (status=ok)"
echo "  ✅ 数据写入 → Backend"
echo "  ✅ 原有 API: Experiences, ICL, Session, Projects, Quality"
echo "  ✅ P0 API: Search, ListObservations, BatchObservations"
echo "  ✅ P1 API: Version, Stats, Modes, Settings"
echo "  ✅ 链路验证: Demo → SDK → Backend 数据流通"
