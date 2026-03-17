#!/bin/bash
#
# Sync resources from TypeScript to Java version
#
# This script synchronizes:
# 1. Mode files: plugin/modes/*.json -> java/.../resources/modes/
# 2. Prompt files: Extracted from mode files -> java/.../resources/prompts/
#
# Usage:
#   ./sync-resources.sh              # Sync both modes and prompts
#   ./sync-resources.sh --modes      # Sync only mode files
#   ./sync-resources.sh --prompts    # Sync only prompt files
#   ./sync-resources.sh --force      # Skip confirmation
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_DIR="$(dirname "$SCRIPT_DIR")"
WEBUI_DIR="$JAVA_DIR/webui"

# Source directories (from WebUI submodule)
TS_MODES_DIR="$WEBUI_DIR/plugin/modes"

# Target directories (Java)
JAVA_MODES_DIR="$JAVA_DIR/backend/src/main/resources/modes"
JAVA_PROMPTS_DIR="$JAVA_DIR/backend/src/main/resources/prompts"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

show_help() {
    cat << EOF
Sync resources from TypeScript to Java version

Usage: $0 [OPTIONS]

Options:
    --modes         Sync only mode files
    --prompts       Sync only prompt files (extracted from modes)
    --all           Sync both modes and prompts (default)
    --force         Skip confirmation prompts
    --help          Show this help message

Source (TS):
    Modes:   $TS_MODES_DIR

Target (Java):
    Modes:   $JAVA_MODES_DIR
    Prompts: $JAVA_PROMPTS_DIR

Examples:
    $0                    # Sync all resources
    $0 --modes --force    # Sync modes without confirmation
    $0 --prompts          # Sync only prompts

Mode files are copied directly to Java resources.
Prompt files are extracted from mode JSON and generated as .txt files.
EOF
}

# Check jq is available (for JSON parsing)
check_jq() {
    if ! command -v jq &> /dev/null; then
        log_error "jq is required but not installed"
        log_info "Install with: brew install jq"
        exit 1
    fi
}

# Extract string value from JSON (handles multiline)
jq_extract() {
    local file="$1"
    local key="$2"
    jq -r ".$key // empty" "$file"
}

# ========================================
# MODE SYNC FUNCTIONS
# ========================================

sync_modes() {
    log_step "Syncing mode files..."
    
    # Check source directory
    if [[ ! -d "$TS_MODES_DIR" ]]; then
        log_error "Source directory not found: $TS_MODES_DIR"
        return 1
    fi
    
    # Ensure target directory exists
    mkdir -p "$JAVA_MODES_DIR"
    
    # Count files
    local total_files=$(ls -1 "$TS_MODES_DIR"/*.json 2>/dev/null | wc -l | tr -d ' ')
    local existing_files=$(ls -1 "$JAVA_MODES_DIR"/*.json 2>/dev/null | wc -l | tr -d ' ')
    
    log_info "Source: $TS_MODES_DIR ($total_files files)"
    log_info "Target: $JAVA_MODES_DIR ($existing_files files existing)"
    
    # Copy all mode files
    local copied=0
    local skipped=0
    
    for mode_file in "$TS_MODES_DIR"/*.json; do
        if [[ -f "$mode_file" ]]; then
            local filename=$(basename "$mode_file")
            local target_file="$JAVA_MODES_DIR/$filename"
            
            cp "$mode_file" "$target_file"
            ((copied++)) || true
        fi
    done
    
    log_info "Copied $copied mode files"
    
    # List synced modes
    echo ""
    log_info "Synced modes:"
    ls -1 "$JAVA_MODES_DIR"/*.json 2>/dev/null | xargs -n1 basename | sed 's/^/  - /'
    
    return 0
}

# ========================================
# PROMPT SYNC FUNCTIONS
# ========================================

generate_init_prompt() {
    local mode_file="$1"
    local mode_name=$(jq_extract "$mode_file" "name")
    log_info "Generating init.txt for mode: $mode_name"

    cat > "$JAVA_PROMPTS_DIR/init.txt" << 'INIT_PROMPT'
You are a Claude-Mem, a specialized observer tool for creating searchable memory FOR FUTURE SESSIONS.

CRITICAL: Record what was LEARNED/BUILT/FIXED/DEPLOYED/CONFIGURED, not what you (the observer) are doing.

You do not have access to tools. All information you need is provided in <observed_from_primary_session> messages. Create observations from what you observe - no investigation needed.

INIT_PROMPT

    jq -r '.prompts.spatial_awareness // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/init.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/init.txt"

    jq -r '.prompts.observer_role // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/init.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/init.txt"

    jq -r '.prompts.recording_focus // ""' "$mode_file" | sed 's/\\n/\n/g' >> "$JAVA_PROMPTS_DIR/init.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/init.txt"

    jq -r '.prompts.skip_guidance // ""' "$mode_file" | sed 's/\\n/\n/g' >> "$JAVA_PROMPTS_DIR/init.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/init.txt"

    jq -r '.prompts.output_format_header // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/init.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/init.txt"

    cat >> "$JAVA_PROMPTS_DIR/init.txt" << 'EOF'
```xml
<observation>
  <type>[ bugfix | feature | refactor | change | discovery | decision ]</type>
  <title>[**title**: Short title capturing the core action or topic]</title>
  <subtitle>[**subtitle**: One sentence explanation (max 24 words)]
  <facts>
    <fact>[Concise, self-contained statement]</fact>
    <fact>[Concise, self-contained statement]</fact>
    <fact>[Concise, self-contained statement]</fact>
  </facts>
  <narrative>[**narrative**: Full context: What was done, how it works, why it matters]
  <concepts>
    <concept>[knowledge-type-category]</concept>
    <concept>[knowledge-type-category]</concept>
  </concepts>
  <files_read>
    <file>[path/to/file]</file>
  </files_read>
  <files_modified>
    <file>[path/to/file]</file>
  </files_modified>
</observation>
```

EOF

    jq -r '.prompts.format_examples // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/init.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/init.txt"

    jq -r '.prompts.footer // ""' "$mode_file" | sed 's/\\n/\n/g' >> "$JAVA_PROMPTS_DIR/init.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/init.txt"

    jq -r '.prompts.header_memory_start // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/init.txt"
}

generate_observation_prompt() {
    local mode_file="$1"
    local mode_name=$(jq_extract "$mode_file" "name")
    log_info "Generating observation.txt for mode: $mode_name"

    cat > "$JAVA_PROMPTS_DIR/observation.txt" << 'OBSERVATION_PROMPT'
<observed_from_primary_session>
  <what_happened>{{toolName}}</what_happened>
  <occurred_at>{{occurredAt}}</occurred_at>
  <working_directory>{{cwd}}</working_directory>
  <parameters>{{toolInput}}</parameters>
  <outcome>{{toolOutput}}</outcome>
</observed_from_primary_session>
OBSERVATION_PROMPT
}

generate_summary_prompt() {
    local mode_file="$1"
    local mode_name=$(jq_extract "$mode_file" "name")
    log_info "Generating summary.txt for mode: $mode_name"

    jq -r '.prompts.header_summary_checkpoint // ""' "$mode_file" > "$JAVA_PROMPTS_DIR/summary.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/summary.txt"

    jq -r '.prompts.summary_instruction // ""' "$mode_file" | sed 's/\\n/\n/g' >> "$JAVA_PROMPTS_DIR/summary.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/summary.txt"

    jq -r '.prompts.summary_context_label // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/summary.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/summary.txt"

    cat >> "$JAVA_PROMPTS_DIR/summary.txt" << 'EOF'
<summary>
  <request>[Short title capturing the user's request AND the substance of what was discussed/done]</request>
  <investigated>[What has been explored so far? What was examined?]</investigated>
  <learned>[What have you learned about how things work?]</learned>
  <completed>[What work has been completed so far? What has shipped or changed?]</completed>
  <next_steps>[What are you actively working on or planning to work on next in this session?]</next_steps>
  <notes>[Additional insights or observations about the current progress]</notes>
</summary>
EOF

    echo "" >> "$JAVA_PROMPTS_DIR/summary.txt"

    jq -r '.prompts.summary_format_instruction // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/summary.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/summary.txt"

    jq -r '.prompts.summary_footer // ""' "$mode_file" | sed 's/\\n/\n/g' >> "$JAVA_PROMPTS_DIR/summary.txt"
}

generate_continuation_prompt() {
    local mode_file="$1"
    local mode_name=$(jq_extract "$mode_file" "name")
    log_info "Generating continuation.txt for mode: $mode_name"

    jq -r '.prompts.continuation_greeting // ""' "$mode_file" > "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    cat >> "$JAVA_PROMPTS_DIR/continuation.txt" << 'CONTINUATION_HEADER'
<observed_from_primary_session>
  <user_request>{{userPrompt}}</user_request>
  <requested_at>{{date}}</requested_at>
</observed_from_primary_session>

CONTINUATION_HEADER

    jq -r '.prompts.system_identity // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    jq -r '.prompts.observer_role // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    jq -r '.prompts.spatial_awareness // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    jq -r '.prompts.recording_focus // ""' "$mode_file" | sed 's/\\n/\n/g' >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    jq -r '.prompts.skip_guidance // ""' "$mode_file" | sed 's/\\n/\n/g' >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    jq -r '.prompts.continuation_instruction // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    jq -r '.prompts.output_format_header // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    cat >> "$JAVA_PROMPTS_DIR/continuation.txt" << 'EOF'
```xml
<observation>
  <type>[ bugfix | feature | refactor | change | discovery | decision ]</type>
  <title>[**title**: Short title capturing the core action or topic]</title>
  <subtitle>[**subtitle**: One sentence explanation (max 24 words)]
  <facts>
    <fact>[Concise, self-contained statement]</fact>
    <fact>[Concise, self-contained statement]</fact>
    <fact>[Concise, self-contained statement]</fact>
  </facts>
  <narrative>[**narrative**: Full context: What was done, how it works, why it matters]
  <concepts>
    <concept>[knowledge-type-category]</concept>
    <concept>[knowledge-type-category]</concept>
  </concepts>
  <files_read>
    <file>[path/to/file]</file>
  </files_read>
  <files_modified>
    <file>[path/to/file]</file>
  </files_modified>
</observation>
```

EOF

    jq -r '.prompts.format_examples // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    jq -r '.prompts.footer // ""' "$mode_file" | sed 's/\\n/\n/g' >> "$JAVA_PROMPTS_DIR/continuation.txt"
    echo "" >> "$JAVA_PROMPTS_DIR/continuation.txt"

    jq -r '.prompts.header_memory_continued // ""' "$mode_file" >> "$JAVA_PROMPTS_DIR/continuation.txt"
}

sync_prompts() {
    log_step "Syncing prompt files..."
    
    check_jq
    
    # Ensure target directory exists
    mkdir -p "$JAVA_PROMPTS_DIR"
    
    # Use code.json as default mode source
    local mode_file="$TS_MODES_DIR/code.json"
    
    if [[ ! -f "$mode_file" ]]; then
        log_error "Mode file not found: $mode_file"
        return 1
    fi
    
    local mode_name=$(jq_extract "$mode_file" "name")
    log_info "Using mode: $mode_name"
    log_info "Source: $mode_file"
    log_info "Target: $JAVA_PROMPTS_DIR"
    
    # Generate all prompts
    generate_init_prompt "$mode_file"
    generate_observation_prompt "$mode_file"
    generate_summary_prompt "$mode_file"
    generate_continuation_prompt "$mode_file"
    
    log_info "Generated prompt files:"
    ls -la "$JAVA_PROMPTS_DIR"
    
    return 0
}

# ========================================
# MAIN
# ========================================

SYNC_MODES=false
SYNC_PROMPTS=false
SKIP_CONFIRM=false

# Parse args
while [[ $# -gt 0 ]]; do
    case $1 in
        --modes) SYNC_MODES=true; shift ;;
        --prompts) SYNC_PROMPTS=true; shift ;;
        --all) SYNC_MODES=true; SYNC_PROMPTS=true; shift ;;
        --force) SKIP_CONFIRM=true; shift ;;
        --help|-h) show_help; exit 0 ;;
        *) shift ;;
    esac
done

# Default: sync both
if [[ "$SYNC_MODES" == false && "$SYNC_PROMPTS" == false ]]; then
    SYNC_MODES=true
    SYNC_PROMPTS=true
fi

# Show summary
echo ""
log_info "=== TS -> Java Resource Sync ==="
echo ""
log_info "Operations:"
[[ "$SYNC_MODES" == true ]] && log_info "  - Sync mode files (32 files)"
[[ "$SYNC_PROMPTS" == true ]] && log_info "  - Generate prompt files (4 files)"
echo ""

# Confirm
if [[ "$SKIP_CONFIRM" == false ]]; then
    read -p "Proceed? [y/N] " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Sync cancelled"
        exit 0
    fi
fi

# Execute
echo ""
if [[ "$SYNC_MODES" == true ]]; then
    sync_modes
    echo ""
fi

if [[ "$SYNC_PROMPTS" == true ]]; then
    sync_prompts
    echo ""
fi

log_info "=== Sync Complete ==="
echo ""
log_info "Next steps:"
log_info "  1. Rebuild Java project: cd java/backend && mvn clean package"
log_info "  2. Restart Java server to pick up new resources"
log_info "  3. Test mode loading with different modes (e.g., code--zh)"
