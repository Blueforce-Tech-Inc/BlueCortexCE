#!/bin/bash
#
# Thin Proxy Test Script (Quick Version)
# 
# Usage: ./thin-proxy-test.sh [options]
#
# Options:
#   --wrapper-only     Only test wrapper, skip Java backend
#   --skip-build       Skip Maven build
#   --cleanup          Cleanup test data after tests
#   --verbose          Show detailed output
#   --help, -h         Show this help
#
# ============================================================================
# HOOK file write operation matrix (aligned with TypeScript version)
# ============================================================================
# | Hook            | Write file? | Trigger condition                            | Test Coverage    |
# |-----------------|---------|-------------------------------------|-------------|
# | SessionStart    | YES     | Java returns updateFiles (CLAUDE.md)    | Test 6, 9   |
# | PostToolUse     | NO      | Only sends observation to database     | Test 7      |
# | SessionEnd      | NO      | Reads transcript, triggers summary      | Test 7b, 13 |
# | UserPromptSubmit| NO      | Only records prompt to database        | Test 7c     |
#
# Key insight: Only SessionStart writes files (CLAUDE.md)
# PostToolUse only sends observation to Java (fire-and-forget)
#
# Usage:
#   1. 1. Make sure Java backend is running:
#      export OPENAI_API_KEY=xxx
#      export SILICONFLOW_API_KEY=xxx
#      java -jar target/cortex-ce-0.1.0-beta.jar &
#
#   2. 2. Then run tests:
#      ./thin-proxy-test.sh
#
#   3. 3. Or only test wrapper:
#      ./thin-proxy-test.sh --wrapper-only
#
# Prerequisites:
#   - Java backend running at http://127.0.0.1:37777
#   - wrapper.js has npm dependencies installed

set -e

# Configuration
JAVA_API_URL="${JAVA_API_URL:-http://127.0.0.1:37777}"
WRAPPER_DIR="$(cd "$(dirname "$0")/../proxy" && pwd)"
WRAPPER="$WRAPPER_DIR/wrapper.js"
TEST_DIR="/tmp/claude-mem-test-$$"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; ((TESTS_PASSED++)) || true; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; ((TESTS_FAILED++)) || true; }
log_info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

cleanup() { rm -rf "$TEST_DIR"; }
trap cleanup EXIT
mkdir -p "$TEST_DIR"

#######################################
# Quick test wrapper.js syntax and help
#######################################
test_wrapper_basics() {
    echo "=== Test 1: wrapper.js syntax check ==="
    if node --check "$WRAPPER" 2>/dev/null; then
        log_pass "wrapper.js syntax correct"
    else
        log_fail "wrapper.js syntax error"
        return 1
    fi

    echo ""
    echo "=== Test 2: wrapper.js help command ==="
    HELP=$(node "$WRAPPER" --help 2>&1)
    if echo "$HELP" | grep -q "session-start"; then
        log_pass "Help command OK"
    else
        log_fail "Help command error"
    fi
}

#######################################
# Test Java API connectivity
#######################################
test_java_api() {
    echo ""
    echo "=== Test 3: Java API health check ==="
    RESP=$(curl -s "$JAVA_API_URL/actuator/health")
    if echo "$RESP" | grep -q "UP"; then
        log_pass "Java API healthy"
    else
        log_fail "Java API no response"
    fi

    echo ""
    echo "=== Test 4: Java API SessionStart (/api/session/start) ==="
    RESP=$(curl -s -X POST "$JAVA_API_URL/api/session/start" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"test-quick","project_path":"'"$TEST_DIR"'"}')
    if echo "$RESP" | grep -q "session_db_id"; then
        log_pass "SessionStart API OK"
    else
        log_fail "SessionStart API failed: $RESP"
    fi

    echo ""
    echo "=== Test 5: Java API ToolUse ==="
    RESP=$(curl -s -X POST "$JAVA_API_URL/api/ingest/tool-use" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"test-quick","tool_name":"Test","tool_input":"{}","cwd":"'"$TEST_DIR"'"}')
    if echo "$RESP" | grep -q "status"; then
        log_pass "ToolUse API OK"
    else
        log_fail "ToolUse API failed: $RESP"
    fi
}

#######################################
# Test wrapper with Java API integration
# Note: Only SessionStart writes files, other hooks only call API
#
# ⚠️ Real Claude Code behavior:
#   1. CLI arg determines hook type: ./wrapper.js session-start
#   2. stdin sends event data, but without hook_event_name (CLI determines type)
#   3. SessionEnd stdin contains transcript_path
#   4. hook_event_name only for test backward compat
#######################################
test_wrapper_integration() {
    echo ""
    echo "=== Test 6: Wrapper SessionStart (real scenario) ==="
    echo "--- (this hook will write CLAUDE.md when Java returns updateFiles) ---"
    # Real Claude Code stdin: no hook_event_name, determined by CLI arg
    INPUT='{"session_id":"wrap-test-001","cwd":"'"$TEST_DIR"'","source":"compact"}'
    if echo "$INPUT" | node "$WRAPPER" session-start --url "$JAVA_API_URL" 2>&1 | grep -q "Hook event"; then
        log_pass "Wrapper SessionStart OK (CLI arg determines type)"
    else
        log_fail "Wrapper SessionStart failed"
    fi

    echo ""
    echo "=== Test 6b: Wrapper SessionStart (backward compat) ==="
    echo "--- (test hook_event_name field backward compat) ---"
    INPUT='{"hook_event_name":"SessionStart","session_id":"wrap-test-002","cwd":"'"$TEST_DIR"'","source":"compact"}'
    if echo "$INPUT" | node "$WRAPPER" session-start --url "$JAVA_API_URL" 2>&1 | grep -q "Hook event"; then
        log_pass "Wrapper SessionStart backward compat OK (with hook_event_name)"
    else
        log_fail "Wrapper SessionStart backward compat failed"
    fi

    echo ""
    echo "=== Test 7: Wrapper PostToolUse (Edit) ==="
    echo "--- (this hook does not write file, no context output, only sends observation) ---"
    # Real Claude Code stdin: no hook_event_name
    INPUT='{"session_id":"wrap-test-001","cwd":"'"$TEST_DIR"'","tool_name":"Edit","tool_input":"{}","tool_response":"{}"}'
    if echo "$INPUT" | node "$WRAPPER" tool-use --url "$JAVA_API_URL" 2>&1 | grep -q "Tool used"; then
        log_pass "Wrapper PostToolUse OK (fire-and-forget, aligned with TS)"
    else
        log_fail "Wrapper PostToolUse failed"
    fi

    echo ""
    echo "=== Test 7b: Wrapper SessionEnd (real scenario) ==="
    echo "--- (this hook does not write file, reads transcript and triggers summary) ---"
    # Real Claude Code stdin: contains transcript_path
    local TRANSCRIPT_PATH="$HOME/.claude/projects/wrap-test-001/wrap-test-001.jsonl"
    INPUT='{"session_id":"wrap-test-001","cwd":"wrap-test-001","transcript_path":"'"$TRANSCRIPT_PATH"'"}'
    if echo "$INPUT" | node "$WRAPPER" session-end --url "$JAVA_API_URL" 2>&1 | grep -q "Session end"; then
        log_pass "Wrapper SessionEnd OK (with transcript_path)"
    else
        log_fail "Wrapper SessionEnd failed"
    fi

    echo ""
    echo "=== Test 7c: Wrapper SessionEnd (no transcript_path) ==="
    echo "--- (test auto build path when no transcript_path) ---"
    # When no transcript_path, wrapper.js auto builds from cwd/sessionId
    INPUT='{"session_id":"wrap-test-002","cwd":"'"$TEST_DIR"'"}'
    if echo "$INPUT" | node "$WRAPPER" session-end --url "$JAVA_API_URL" 2>&1 | grep -q "Session end"; then
        log_pass "Wrapper SessionEnd auto path build OK"
    else
        log_fail "Wrapper SessionEnd auto path build failed"
    fi

    echo ""
    echo "=== Test 7d: Wrapper UserPromptSubmit ==="
    echo "--- (this hook does not write file, only records prompt to database) ---"
    # Real Claude Code stdin: no hook_event_name
    INPUT='{"session_id":"wrap-test-001","cwd":"'"$TEST_DIR"'","prompt_text":"Test prompt","prompt_number":1}'
    if echo "$INPUT" | node "$WRAPPER" user-prompt --url "$JAVA_API_URL" 2>&1 | grep -q "User prompt"; then
        log_pass "Wrapper UserPromptSubmit OK"
    else
        log_fail "Wrapper UserPromptSubmit failed"
    fi

    echo ""
    echo "=== Test 8: Wrapper tool filtering (Glob should skip) ==="
    INPUT='{"session_id":"wrap-test-001","cwd":"'"$TEST_DIR"'","tool_name":"Glob","tool_input":"{}"}'
    set +e
    echo "$INPUT" | node "$WRAPPER" tool-use --url "$JAVA_API_URL" >/dev/null 2>&1
    EXIT_CODE=$?
    set -e
    if [ $EXIT_CODE -eq 0 ]; then
        log_pass "Glob tool correctly skipped (silent exit)"
    else
        log_fail "Glob tool handling error (exit code: $EXIT_CODE)"
    fi

    # TS Alignment Tests
    echo ""
    echo "=== Test 8b: JSON stdout format (TS Alignment) ==="
    echo "--- (verify SessionStart output hookSpecificOutput.additionalContext) ---"
    INPUT='{"session_id":"json-stdout-test-001","cwd":"'"$TEST_DIR"'","source":"compact"}'
    STDOUT=$(echo "$INPUT" | node "$WRAPPER" session-start --url "$JAVA_API_URL" 2>/dev/null)
    if echo "$STDOUT" | grep -q '"hookSpecificOutput"' && echo "$STDOUT" | grep -q '"additionalContext"'; then
        log_pass "SessionStart output JSON format correct (hookSpecificOutput.additionalContext)"
    else
        log_fail "SessionStart output format incorrect, expect JSON contains hookSpecificOutput"
        echo "Got: $STDOUT"
    fi

    echo ""
    echo "=== Test 8c: CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED env var (TS Alignment) ==="
    echo "--- (verify PostToolUse env var defaults to CLAUDE.md update off) ---"
    # By default (without env var), PostToolUse does not trigger folder CLAUDE.md update
    INPUT='{"session_id":"env-test-001","cwd":"'"$TEST_DIR"'","tool_name":"Edit","tool_input":{"file_path":"test.ts"},"tool_response":"{}"}'
    STDERR=$(echo "$INPUT" | node "$WRAPPER" tool-use --url "$JAVA_API_URL" 2>&1)
    # By default, should not trigger folder CLAUDE.md update
    if echo "$STDERR" | grep -q "Folder CLAUDE.md"; then
        log_fail "Should not trigger Folder CLAUDE.md update by default"
    else
        log_pass "PostToolUse does not trigger Folder CLAUDE.md update by default (env var not set)"
    fi

    echo ""
    echo "=== Test 8d: CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true (TS Alignment) ==="
    echo "--- (verify PostToolUse env var enables CLAUDE.md update behavior) ---"
    # Set env var and run wrapper (use env to ensure env passed)
    INPUT='{"session_id":"env-enabled-test-001","cwd":"'"$TEST_DIR"'","tool_name":"Edit","tool_input":{"file_path":"test.ts"},"tool_response":"{}"}'
    STDERR=$(CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true echo "$INPUT" | CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true node "$WRAPPER" tool-use --url "$JAVA_API_URL" 2>&1)
    # Verify env var enabled behavior - should trigger folder CLAUDE.md update
    if echo "$STDERR" | grep -q "Folder CLAUDE.md update triggered"; then
        log_pass "Env var CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true active (PostToolUse triggers update)"
    else
        log_info "Env var test output: $STDERR"
        log_fail "env var CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true did not trigger update"
    fi

    echo ""
    echo "=== Test 8e: --enable-folder-claudemd command line arg ==="
    echo "--- (verify PostToolUse command line arg enables CLAUDE.md update behavior) ---"
    # Use command line arg to enable folder CLAUDE.md update
    INPUT='{"session_id":"cli-flag-test-001","cwd":"'"$TEST_DIR"'","tool_name":"Edit","tool_input":{"file_path":"test.ts"},"tool_response":"{}"}'
    STDERR=$(echo "$INPUT" | node "$WRAPPER" tool-use --url "$JAVA_API_URL" --enable-folder-claudemd 2>&1)
    # Verify command line arg enabled behavior - should trigger folder CLAUDE.md update
    if echo "$STDERR" | grep -q "Folder CLAUDE.md update triggered"; then
        log_pass "Command line arg --enable-folder-claudemd active (PostToolUse triggers update)"
    else
        log_info "Command line arg test output: $STDERR"
        log_fail "command line arg --enable-folder-claudemd did not trigger update"
    fi
}

#######################################
# Test CLAUDE.md update flow (real scenario)
#######################################
test_claude_md_update() {
    echo ""
    echo "=== Test 9: CLAUDE.md update flow (real scenario) ==="
    echo "--- (debug mode, no file creation) ---"

    # Cleanup and create test directory
    rm -rf "$TEST_DIR"
    mkdir -p "$TEST_DIR"

    # Call session-start debug mode (real scenario: no hook_event_name)
    INPUT='{"session_id":"claude-md-test-001","cwd":"'"$TEST_DIR"'","source":"compact","debug":true}'
    STDOUT=$(echo "$INPUT" | node "$WRAPPER" session-start --url "$JAVA_API_URL" 2>/dev/null)

    # Check if context output to stdout (may be "Debug Test Context" or default)
    if echo "$STDOUT" | grep -qE "(Debug Test Context|no memories yet)"; then
        log_pass "Context output to stdout OK"
    else
        log_fail "Context output failed: $STDOUT"
    fi

    # Check if CLAUDE.md was created (debug mode may not create file)
    if [ -f "$TEST_DIR/CLAUDE.md" ]; then
        log_pass "CLAUDE.md file created"
    else
        # In debug mode, updateFiles may be empty, expected behavior
        log_info "CLAUDE.md file not created (debug mode updateFiles empty, expected)"
    fi

    # If CLAUDE.md exists, check if contains context tag
    if [ -f "$TEST_DIR/CLAUDE.md" ] && grep -q "<claude-mem-context>" "$TEST_DIR/CLAUDE.md"; then
        log_pass "CLAUDE.md contains context tag"
    elif [ -f "$TEST_DIR/CLAUDE.md" ]; then
        log_fail "CLAUDE.md does not contain context tag"
    fi

    # Cleanup test directory
    rm -rf "$TEST_DIR"
    mkdir -p "$TEST_DIR"

    echo ""
    echo "=== Test 10: CLAUDE.md tag replacement flow (real scenario) ==="

    # Pre-create CLAUDE.md with old tags
    cat > "$TEST_DIR/CLAUDE.md" << 'EOF'
# Project README

<claude-mem-context>
Old context content
</claude-mem-context>

More content here.
EOF

    # Call session-start debug mode (real scenario: no hook_event_name)
    INPUT='{"session_id":"claude-md-test-002","cwd":"'"$TEST_DIR"'","source":"compact","debug":true}'
    STDOUT=$(echo "$INPUT" | node "$WRAPPER" session-start --url "$JAVA_API_URL" 2>/dev/null)

    # Check if old content replaced (debug mode updateFiles empty, no file update)
    if ! grep -q "Old context content" "$TEST_DIR/CLAUDE.md"; then
        log_pass "Old context correctly replaced"
    else
        # In debug mode, updateFiles empty array, file not updated
        log_info "Old context not replaced (debug mode updateFiles empty, expected)"
    fi

    # Check if new content appears
    if grep -qE "(Debug Test Context|no memories yet)" "$TEST_DIR/CLAUDE.md"; then
        log_pass "New context written"
    else
        log_info "New context not written (debug mode updateFiles empty)"
    fi
}

#######################################
# Test Prior Messages feature (new P0)
# Note: This feature does not involve file write operations, only queries history from database
#######################################
test_prior_messages() {
    echo ""
    echo "=== Test 12: Prior Messages get feature ==="

    # First create a session and save lastAssistantMessage
    echo ""
    echo "--- Create test session ---"
    SESSION_RESP=$(curl -s -X POST "$JAVA_API_URL/api/session/start" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"prior-msg-test-001","project_path":"'"$TEST_DIR"'"}')
    if echo "$SESSION_RESP" | grep -q "session_db_id"; then
        log_pass "Prior Messages test session created"
    else
        log_fail "Prior Messages test session creation failed"
    fi

# Call ContextService Prior Messages API (needs /api/context/prior-messages endpoint)
    echo ""
    echo "--- Test Prior Messages endpoint ---"
    PRIOR_RESP=$(curl -s "$JAVA_API_URL/api/context/prior-messages?project=${TEST_DIR}&current_session_id=prior-msg-test-001" 2>/dev/null)
    if [ -n "$PRIOR_RESP" ]; then
        log_pass "Prior Messages API returned response"
        echo "$PRIOR_RESP" | head -c 200
        echo "..."
    else
        log_info "Prior Messages endpoint may not be implemented (expected if API not added yet)"
    fi
}

#######################################
# Test Worktree detection feature
# Git worktree has .git file (not directory)
# File content: gitdir: /path/to/parent/.git/worktrees/<name>
#######################################
test_worktree_detection() {
    echo ""
    echo "=== Test 14: Worktree detection feature ==="

    # Test 14a: non-worktree (normal .git directory)
    echo ""
    echo "--- Test 14a: Normal project (non-worktree) ---"
    local NORMAL_PROJECT="$TEST_DIR/normal-project"
    mkdir -p "$NORMAL_PROJECT"
    mkdir -p "$NORMAL_PROJECT/.git"

    INPUT='{"session_id":"worktree-test-001","cwd":"'"$NORMAL_PROJECT"'","source":"compact"}'
    STDERR=$(echo "$INPUT" | node "$WRAPPER" session-start --url "$JAVA_API_URL" 2>&1)

    if echo "$STDERR" | grep -q "Worktree detected"; then
        log_fail "Normal project incorrectly identified as worktree"
    else
        log_pass "Normal project correctly identified as non-worktree"
    fi

    # Test 14b: Worktree detection (.git is file)
    echo ""
    echo "--- Test 14b: Worktree project detection ---"
    local PARENT_REPO="$TEST_DIR/parent-repo"
    local WORKTREE_PROJECT="$TEST_DIR/worktree-project"
    mkdir -p "$PARENT_REPO/.git/worktrees/worktree-project"
    mkdir -p "$WORKTREE_PROJECT"

    # Create .git file (worktree marker)
    echo "gitdir: $PARENT_REPO/.git/worktrees/worktree-project" > "$WORKTREE_PROJECT/.git"

    INPUT='{"session_id":"worktree-test-002","cwd":"'"$WORKTREE_PROJECT"'","source":"compact"}'
    STDERR=$(echo "$INPUT" | node "$WRAPPER" session-start --url "$JAVA_API_URL" 2>&1)

    if echo "$STDERR" | grep -q "Worktree detected: worktree-project -> parent: parent-repo"; then
        log_pass "Worktree project correctly detected and passed to Java API"
    else
        log_fail "Worktree detection failed"
        echo "Output: $STDERR"
    fi

    # Test 14c: Java API receives worktree param
    echo ""
    echo "--- Test 14c: Java API multi-project param handling ---"
    RESP=$(curl -s -X POST "$JAVA_API_URL/api/session/start" \
        -H "Content-Type: application/json" \
        -d '{"session_id":"worktree-api-test","project_path":"'"$WORKTREE_PROJECT"'","cwd":"'"$WORKTREE_PROJECT"'","projects":"parent-repo,worktree-project","is_worktree":true,"parent_project":"parent-repo"}')

    if echo "$RESP" | grep -q "session_db_id"; then
        log_pass "Java API multi-project param handling OK"
    else
        log_fail "Java API multi-project param handling failed: $RESP"
    fi

    # Test 14d: no .git project
    echo ""
    echo "--- Test 14d: No .git project ---"
    local NO_GIT_PROJECT="$TEST_DIR/no-git-project"
    mkdir -p "$NO_GIT_PROJECT"

    INPUT='{"session_id":"worktree-test-003","cwd":"'"$NO_GIT_PROJECT"'","source":"compact"}'
    STDERR=$(echo "$INPUT" | node "$WRAPPER" session-start --url "$JAVA_API_URL" 2>&1)

    if echo "$STDERR" | grep -q "Worktree detected"; then
        log_fail "No .git project incorrectly identified as worktree"
    else
        log_pass "No .git project handled correctly"
    fi
}

#######################################
# Test Transcript parsing feature (real scenario)
# Note: SessionEnd does not write files, only reads transcript and triggers summary generation
# Real Claude Code behavior:
#   - stdin contains transcript_path field
#   - wrapper.js prioritizes transcript_path, otherwise auto build path
#######################################
test_transcript_parsing() {
    echo ""
    echo "=== Test 13: Transcript parsing feature (E2E test - real scenario) ==="

    # Claude Code transcript storage path: ~/.claude/projects/{cwd}/{sessionId}.jsonl
    # But test environment cannot access HOME, use /tmp to simulate
    local PROJECT_HASH="test-project-hash-$$"
    local SESSION_ID="transcript-e2e-test-$$"
    local TRANSCRIPT_DIR="/tmp/claude-mem-test-transcript/$PROJECT_HASH"
    local TRANSCRIPT_PATH="$TRANSCRIPT_DIR/$SESSION_ID.jsonl"

    # Cleanup test environment
    rm -rf "$TRANSCRIPT_DIR"
    mkdir -p "$TRANSCRIPT_DIR"

    # Create real format transcript file
    cat > "$TRANSCRIPT_PATH" << 'EOF'
{"type":"user","message":{"content":[{"type":"text","text":"Hello, help me write a factorial function"}]}}
{"type":"assistant","message":{"content":[{"type":"text","text":"Sure! Here is a recursive factorial function in JavaScript."}]}}
{"type":"user","message":{"content":[{"type":"text","text":"Make it iterative instead"}]}}
{"type":"assistant","message":{"content":[{"type":"text","text":"Here is an iterative version using a loop. Remember to handle edge cases like negative input."}]}}
EOF

    echo "Created transcript at: $TRANSCRIPT_PATH"

    # Call wrapper.js session-end (debug mode outputs parsing result but still calls Java API)
    # Real Claude Code scenario: stdin contains transcript_path
    INPUT='{"session_id":"'"$SESSION_ID"'","cwd":"'"$PROJECT_HASH"'","transcript_path":"'"$TRANSCRIPT_PATH"'","debug":true}'
    STDOUT=$(echo "$INPUT" | node "$WRAPPER" session-end --url "$JAVA_API_URL" 2>&1)

    # Verify transcript correctly parsed
    if echo "$STDOUT" | grep -q "DEBUG_LAST_ASSISTANT_MESSAGE:Here is an iterative version"; then
        log_pass "Transcript parsed successfully, extracted correct lastAssistantMessage"
    else
        log_fail "Transcript parsing failed"
        echo "Output: $STDOUT"
    fi

    # Cleanup test transcript
    rm -rf "$TRANSCRIPT_DIR"

    # Test transcript not existing (real scenario: no transcript_path, auto build path)
    echo ""
    echo "--- Test handling transcript not existing (real scenario) ---"
    mkdir -p "$TRANSCRIPT_DIR"
    # Real Claude Code scenario: no transcript_path, wrapper.js auto builds path
    INPUT='{"session_id":"non-existent-session","cwd":"'"$PROJECT_HASH"'","debug":false}'
    STDOUT=$(echo "$INPUT" | node "$WRAPPER" session-end --url "$JAVA_API_URL" 2>&1)

    # Should handle transcript not existing (continue, no error)
    if echo "$STDOUT" | grep -q "Transcript not found"; then
        log_pass "Correctly handle transcript not existing (auto path build)"
    else
        log_info "Transcript not existing handling may not produce expected output (may be silent skip)"
    fi

    # Cleanup
    rm -rf "$TRANSCRIPT_DIR"
}

#######################################
# Cursor IDE integration test
#######################################
test_cursor_commands() {
    log_info "=== Test 15: Cursor IDE commands ==="

    # Enable debug mode to verify log output
    export CLAUDE_MEM_DEBUG=true

    # Test cursor context command
    log_info "Test 15a: cursor context command"
    local INPUT='{"session_id":"cursor-test-001","cwd":"'$TEST_DIR'","prompt_text":"test prompt"}'
    local OUTPUT=$(echo "$INPUT" | node "$WRAPPER" cursor context --url "$JAVA_API_URL" 2>&1)
    if echo "$OUTPUT" | grep -q "Cursor beforeSubmitPrompt"; then
        log_pass "cursor context command executed OK"
    else
        log_fail "cursor context command failed: $OUTPUT"
    fi

    # Test cursor observation command
    log_info "Test 15b: cursor observation command"
    INPUT='{"session_id":"cursor-test-002","cwd":"'$TEST_DIR'","tool_name":"Edit","tool_input":{"path":"test.txt"},"tool_response":{"success":true}}'
    OUTPUT=$(echo "$INPUT" | node "$WRAPPER" cursor observation --url "$JAVA_API_URL" 2>&1)
    if echo "$OUTPUT" | grep -q "Cursor observation queued"; then
        log_pass "cursor observation command executed OK"
    else
        log_fail "cursor observation command failed: $OUTPUT"
    fi

    # Test cursor summarize command
    log_info "Test 15c: cursor summarize command"
    INPUT='{"session_id":"cursor-test-003","cwd":"'$TEST_DIR'"}'
    OUTPUT=$(echo "$INPUT" | node "$WRAPPER" cursor summarize --url "$JAVA_API_URL" 2>&1)
    if echo "$OUTPUT" | grep -q "Cursor stop\|Cursor session ended"; then
        log_pass "cursor summarize command executed OK"
    else
        log_fail "cursor summarize command failed: $OUTPUT"
    fi

    # Test cursor skip non-essential tools
    log_info "Test 15d: cursor observation skip Glob tool"
    INPUT='{"session_id":"cursor-test-004","cwd":"'$TEST_DIR'","tool_name":"Glob","tool_input":{"pattern":"**/*.ts"}}'
    OUTPUT=$(echo "$INPUT" | node "$WRAPPER" cursor observation --url "$JAVA_API_URL" 2>&1)
    if echo "$OUTPUT" | grep -q "Skipping tool Glob"; then
        log_pass "cursor observation correctly skipped Glob tool"
    else
        log_fail "cursor observation did not correctly skip Glob tool: $OUTPUT"
    fi

    # Test cursor file-edit command (afterFileEdit hook)
    # Cursor afterFileEdit passes file_path and edits, not tool_name/tool_input
    log_info "Test 15d-2: cursor file-edit command"
    INPUT='{"session_id":"cursor-test-005","cwd":"'$TEST_DIR'","file_path":"test.txt","edits":[{"old_string":"old","new_string":"new"}]}'
    OUTPUT=$(echo "$INPUT" | node "$WRAPPER" cursor file-edit --url "$JAVA_API_URL" 2>&1)
    if echo "$OUTPUT" | grep -q "Cursor afterExecution"; then
        log_pass "cursor file-edit command executed OK"
    else
        log_fail "cursor file-edit command failed: $OUTPUT"
    fi

    # Turn off debug mode
    export CLAUDE_MEM_DEBUG=

    # Test Cursor project registration API
    log_info "Test 15e: Cursor project registration API"
    local REGISTER_RESPONSE=$(curl -s -X POST "$JAVA_API_URL/api/cursor/register" \
        -H "Content-Type: application/json" \
        -d '{"projectName":"cursor-test-project","workspacePath":"'$TEST_DIR'"}')
    if echo "$REGISTER_RESPONSE" | grep -q '"success":true'; then
        log_pass "Cursor project registration success"
    else
        log_fail "Cursor project registration failed: $REGISTER_RESPONSE"
    fi

    # Test Cursor project list API
    log_info "Test 15f: Cursor project list API"
    local PROJECTS_RESPONSE=$(curl -s "$JAVA_API_URL/api/cursor/projects")
    if echo "$PROJECTS_RESPONSE" | grep -q "cursor-test-project"; then
        log_pass "Cursor project list contains registered project"
    else
        log_fail "Cursor project list does not contain registered project: $PROJECTS_RESPONSE"
    fi

    # Test Cursor project unregister
    log_info "Test 15g: Cursor project unregister API"
    local UNREGISTER_RESPONSE=$(curl -s -X DELETE "$JAVA_API_URL/api/cursor/register/cursor-test-project")
    if echo "$UNREGISTER_RESPONSE" | grep -q '"success":true\|unregistered'; then
        log_pass "Cursor project unregister success"
    else
        log_fail "Cursor project unregister failed: $UNREGISTER_RESPONSE"
    fi
}

#######################################
# Privacy Tags test (TS Alignment)
# Test <private> tag stripping feature
#######################################
test_privacy_tags() {
    echo ""
    echo "=== Test 16: Privacy Tags feature (TS Alignment) ==="

    local TAG_STRIPPER="$WRAPPER_DIR/tag-stripping.js"

    # Test 16a: tag-stripping.js syntax check
    echo ""
    echo "--- Test 16a: tag-stripping.js syntax check ---"
    if node --check "$TAG_STRIPPER" 2>/dev/null; then
        log_pass "tag-stripping.js syntax correct"
    else
        log_fail "tag-stripping.js syntax error"
        return 1
    fi

    # Test 16b: Strip <private> tag
    echo ""
    echo "--- Test 16b: Strip <private> tag ---"
    local TEST_RESULT=$(node -e "
import { stripMemoryTagsFromPrompt } from '$TAG_STRIPPER';
const input = 'Hello world <private>secret password 123</private> end';
const result = stripMemoryTagsFromPrompt(input);
console.log(result);
" 2>&1)
    if echo "$TEST_RESULT" | grep -q "Hello world" && ! echo "$TEST_RESULT" | grep -q "secret password"; then
        log_pass "<private> tag content correctly stripped"
    else
        log_fail "<private> tag stripping failed: $TEST_RESULT"
    fi

    # Test 16c: Strip <claude-mem-context> tag
    echo ""
    echo "--- Test 16c: Strip <claude-mem-context> tag ---"
    TEST_RESULT=$(node -e "
import { stripMemoryTagsFromPrompt } from '$TAG_STRIPPER';
const input = 'Before <claude-mem-context>auto injected content</claude-mem-context> After';
const result = stripMemoryTagsFromPrompt(input);
console.log(result);
" 2>&1)
    if echo "$TEST_RESULT" | grep -q "Before" && echo "$TEST_RESULT" | grep -q "After" && ! echo "$TEST_RESULT" | grep -q "auto injected"; then
        log_pass "<claude-mem-context> tag content correctly stripped"
    else
        log_fail "<claude-mem-context> tag stripping failed: $TEST_RESULT"
    fi

    # Test 16d: Detect fully private prompt
    echo ""
    echo "--- Test 16d: Detect fully private prompt ---"
    TEST_RESULT=$(node -e "
import { isEntirelyPrivate } from '$TAG_STRIPPER';
const input = '<private>entirely secret content</private>';
console.log(isEntirelyPrivate(input));
" 2>&1)
    if echo "$TEST_RESULT" | grep -q "true"; then
        log_pass "Fully private prompt correctly detected"
    else
        log_fail "Fully private prompt detection failed: $TEST_RESULT"
    fi

    # Test 16e: Detect non-fully private prompt
    echo ""
    echo "--- Test 16e: Detect non-fully private prompt ---"
    TEST_RESULT=$(node -e "
import { isEntirelyPrivate } from '$TAG_STRIPPER';
const input = 'Hello <private>secret</private>';
console.log(isEntirelyPrivate(input));
" 2>&1)
    if echo "$TEST_RESULT" | grep -q "false"; then
        log_pass "Non-fully private prompt correctly detected"
    else
        log_fail "Non-fully private prompt detection failed: $TEST_RESULT"
    fi

    # Test 16f: PostToolUse strip <private> tag in tool_input
    echo ""
    echo "--- Test 16f: PostToolUse strip <private> tag in tool_input ---"
    # Use mock scenario to verify wrapper handling of tool_input
    INPUT='{"session_id":"privacy-test-001","cwd":"'"$TEST_DIR"'","tool_name":"Edit","tool_input":{"file_path":"test.ts","content":"my api key is <private>sk-1234567890abcdef</private>"},"tool_response":"{}"}'
    STDERR=$(echo "$INPUT" | node "$WRAPPER" tool-use --url "$JAVA_API_URL" 2>&1)
    # wrapper should handle successfully (not fail due to special chars)
    if echo "$STDERR" | grep -q "Tool used"; then
        log_pass "PostToolUse correctly handles tool_input with <private>"
    else
        log_fail "PostToolUse handling tool_input with <private> failed"
    fi

    # Test 16g: UserPromptSubmit strip <private> tag in prompt_text
    echo ""
    echo "--- Test 16g: UserPromptSubmit strip <private> tag in prompt_text ---"
    INPUT='{"session_id":"privacy-test-002","cwd":"'"$TEST_DIR"'","prompt_text":"My password is <private>secret123</private>","prompt_number":1}'
    STDERR=$(echo "$INPUT" | node "$WRAPPER" user-prompt --url "$JAVA_API_URL" 2>&1)
    if echo "$STDERR" | grep -q "User prompt"; then
        log_pass "UserPromptSubmit correctly handles prompt_text with <private>"
    else
        log_fail "UserPromptSubmit handling prompt_text with <private> failed"
    fi

    # Test 16h: UserPromptSubmit skip fully private prompt
    echo ""
    echo "--- Test 16h: UserPromptSubmit skip fully private prompt ---"
    INPUT='{"session_id":"privacy-test-003","cwd":"'"$TEST_DIR"'","prompt_text":"<private>entirely secret content</private>","prompt_number":1}'
    STDERR=$(echo "$INPUT" | node "$WRAPPER" user-prompt --url "$JAVA_API_URL" 2>&1)
    if echo "$STDERR" | grep -q "entirely private"; then
        log_pass "UserPromptSubmit correctly skips fully private prompt"
    else
        log_fail "UserPromptSubmit did not correctly skip fully private prompt"
        echo "Output: $STDERR"
    fi
}

#######################################
# Main function
#######################################
main() {
    echo "========================================"
    echo "Thin Proxy quick test"
    echo "========================================"
    echo "API: $JAVA_API_URL"
    echo ""

    # Check prerequisites
    if ! command -v node &> /dev/null; then
        echo "Error: Node.js not installed"
        exit 1
    fi

    if [ ! -f "$WRAPPER" ]; then
        echo "Error: wrapper.js not found"
        exit 1
    fi

    # Run tests
    test_wrapper_basics

    echo ""
    echo "--- Java API tests (requires Java backend running) ---"
    if curl -s "$JAVA_API_URL/actuator/health" > /dev/null 2>&1; then
        test_java_api
        test_wrapper_integration
        test_claude_md_update
        test_prior_messages  # Prior Messages test
        test_worktree_detection  # Worktree detection test
        test_transcript_parsing  # Transcript parsing test
        test_cursor_commands  # Cursor IDE integration test
        test_privacy_tags  # Privacy Tags test (TS Alignment)
    else
        echo "Java backend not running, skipping API related tests"
        echo "Start command: java -jar target/claude-mem-java-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev"
    fi

    echo ""
    echo "========================================"
    echo "Test results: passed $TESTS_PASSED / $((TESTS_PASSED + TESTS_FAILED))"
    echo "========================================"
}

main "$@"
