#!/usr/bin/env node
/**
 * Claude-Mem Hook Wrapper
 *
 * CLI wrapper for Claude Code hooks integration.
 * Reads hook events from stdin, calls Java API, processes responses.
 *
 * ============================================================================
 * FILE WRITING MATRIX (Aligned with TypeScript Version)
 * ============================================================================
 * | Hook            | Writes Files? | When?                              | TS Behavior |
 * |-----------------|---------------|-------------------------------------|-------------|
 * | SessionStart    | NO (DEPRECATED)| Previously: Java returns updateFiles| DEPRECATED  |
 * |                 |               | Now: Context injection only         |             |
 * | PostToolUse     | YES (if enabled)| If CLAUDE_MEM_FOLDER_CLAUDEMD_    | YES - folder |
 * |                 |               | ENABLED=true, updates folder        | CLAUDE.md   |
 * |                 |               | CLAUDE.md files (active exclusion)  |             |
 * | SessionEnd      | NO            | Only reads transcript, calls API    | NO - only    |
 * |                 |               | No file output                       | summary      |
 * | UserPromptSubmit| NO           | Only records prompt to database     | NO - only    |
 * |                 |               | No file output                       | logs prompt  |
 *
 * TS Alignment (2026-02-15):
 *   - Folder CLAUDE.md updates moved from SessionStart to PostToolUse
 *   - Active file exclusion (Issue #859): Skip folders where CLAUDE.md was read/modified
 *   - Controlled by CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED (default: false)
 *
 * Installation (manual):
 *   1. Place this script in your project or home directory
 *   2. Make executable: chmod +x wrapper.js
 *   3. Add to .claude/settings.json
 *
 * Configuration in ~/.claude/settings.json or .claude/settings.json:
 * {
 *   "hooks": {
 *     "SessionStart": [{
 *       "hooks": [{
 *         "type": "command",
 *         "command": "/path/to/wrapper.js session-start --url http://127.0.0.1:37777",
 *         "blocking": false
 *       }]
 *     }],
 *     "PostToolUse": [{
 *       "matcher": "Edit|Write|Read|Bash",
 *       "hooks": [{
 *         "type": "command",
 *         "command": "/path/to/wrapper.js tool-use --url http://127.0.0.1:37777",
 *         "blocking": false
 *       }]
 *     }],
 *     "SessionEnd": [{
 *       "hooks": [{
 *         "type": "command",
 *         "command": "/path/to/wrapper.js session-end --url http://127.0.0.1:37777",
 *         "blocking": false
 *       }]
 *     }],
 *     "UserPromptSubmit": [{
 *       "hooks": [{
 *         "type": "command",
 *         "command": "/path/to/wrapper.js user-prompt --url http://127.0.0.1:37777",
 *         "blocking": false
 *       }]
 *     }]
 *   }
 * }
 *
 * Environment:
 *   JAVA_API_URL - Java API URL (default: http://127.0.0.1:37777)
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync, statSync, unlinkSync, rmSync } from 'fs';
import { join, resolve, dirname, relative, basename } from 'path';
import { fileURLToPath } from 'url';
import axios from 'axios';

// Privacy Tags support (TS alignment)
import { stripMemoryTagsFromJson, stripMemoryTagsFromPrompt, isEntirelyPrivate } from './tag-stripping.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Configuration
const CONFIG = {
  javaUrl: process.env.JAVA_API_URL || 'http://127.0.0.1:37777',
  timeout: 10000, // 10s timeout for hooks
  tagPattern: /<claude-mem-context>([\s\S]*?)<\/claude-mem-context>/,
  debug: process.env.CLAUDE_MEM_DEBUG === 'true',
  folderClaudeMdEnabled: process.env.CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED === 'true',
  folderMdExcludePaths: (() => {
    try {
      const val = process.env.CLAUDE_MEM_FOLDER_MD_EXCLUDE;
      return val ? JSON.parse(val) : [];
    } catch {
      return [];
    }
  })(),
};

/**
 * ============================================================================
 * WORKTREE DETECTION (Aligned with TS src/utils/worktree.ts)
 * ============================================================================
 *
 * Detects if the current working directory is a git worktree.
 * Git worktrees have a `.git` file (not directory) containing:
 *   gitdir: /path/to/parent/.git/worktrees/<name>
 */

/**
 * Detect if a directory is a git worktree and extract parent info.
 *
 * @param {string} cwd - Current working directory (absolute path)
 * @returns {{isWorktree: boolean, worktreeName: string|null, parentRepoPath: string|null, parentProjectName: string|null}}
 */
function detectWorktree(cwd) {
  if (!cwd || cwd.trim() === '') {
    return { isWorktree: false, worktreeName: null, parentRepoPath: null, parentProjectName: null };
  }

  const gitPath = join(cwd, '.git');

  // Check if .git exists
  let stat;
  try {
    stat = statSync(gitPath);
  } catch {
    // No .git at all - not a git repo
    return { isWorktree: false, worktreeName: null, parentRepoPath: null, parentProjectName: null };
  }

  // Check if .git is a file (worktree) or directory (main repo)
  if (!stat.isFile()) {
    // .git is a directory = main repo, not a worktree
    return { isWorktree: false, worktreeName: null, parentRepoPath: null, parentProjectName: null };
  }

  // Parse .git file to find parent repo
  let content;
  try {
    content = readFileSync(gitPath, 'utf-8').trim();
  } catch {
    return { isWorktree: false, worktreeName: null, parentRepoPath: null, parentProjectName: null };
  }

  // Format: gitdir: /path/to/parent/.git/worktrees/<name>
  const match = content.match(/^gitdir:\s*(.+)$/);
  if (!match) {
    return { isWorktree: false, worktreeName: null, parentRepoPath: null, parentProjectName: null };
  }

  const gitdirPath = match[1];

  // Extract: /path/to/parent from /path/to/parent/.git/worktrees/name
  // Handle both Unix and Windows paths
  const worktreesMatch = gitdirPath.match(/^(.+)[/\\]\.git[/\\]worktrees[/\\]([^/\\]+)$/);
  if (!worktreesMatch) {
    return { isWorktree: false, worktreeName: null, parentRepoPath: null, parentProjectName: null };
  }

  const parentRepoPath = worktreesMatch[1];
  const worktreeName = basename(cwd);
  const parentProjectName = basename(parentRepoPath);

  return {
    isWorktree: true,
    worktreeName,
    parentRepoPath,
    parentProjectName
  };
}

/**
 * Extract project name from working directory path.
 *
 * @param {string|null|undefined} cwd - Current working directory
 * @returns {string} Project name or "unknown-project" if extraction fails
 */
function getProjectName(cwd) {
  if (!cwd || cwd.trim() === '') {
    return 'unknown-project';
  }

  const name = basename(cwd);

  // Edge case: Root directory (basename returns empty)
  if (name === '') {
    return 'unknown-project';
  }

  return name;
}

/**
 * Get project context with worktree detection.
 *
 * When in a worktree, returns both the worktree project name and parent project name
 * for unified timeline queries.
 *
 * @param {string|null|undefined} cwd - Current working directory
 * @returns {{primary: string, parent: string|null, isWorktree: boolean, allProjects: string[]}}
 */
function getProjectContext(cwd) {
  const primary = getProjectName(cwd);

  if (!cwd) {
    return { primary, parent: null, isWorktree: false, allProjects: [primary] };
  }

  const worktreeInfo = detectWorktree(cwd);

  if (worktreeInfo.isWorktree && worktreeInfo.parentProjectName) {
    // In a worktree: include parent first for chronological ordering
    return {
      primary,
      parent: worktreeInfo.parentProjectName,
      isWorktree: true,
      allProjects: [worktreeInfo.parentProjectName, primary]
    };
  }

  return { primary, parent: null, isWorktree: false, allProjects: [primary] };
}

/**
 * Cursor input normalization function
 * Based on TS original: src/cli/adapters/cursor.ts
 * Cursor uses workspace_roots[0], conversation_id/generation_id, etc.
 *
 * @param {Object} event - Raw hook event from Cursor
 * @returns {Object} - Normalized input
 */
function normalizeCursorInput(event) {
  const r = event || {};
  const isShellCommand = !!r.command && !r.tool_name;

  return {
    cwd: r.workspace_roots?.[0] || r.workspace_root || r.cwd || process.cwd(),
    session_id: r.conversation_id || r.generation_id || r.session_id || `cursor-${Date.now()}`,
    prompt: r.prompt,  // TS对齐: beforeSubmitPrompt 会传递 prompt 字段
    toolName: isShellCommand ? 'Bash' : (r.tool_name || 'unknown'),
    toolInput: isShellCommand ? { command: r.command } : r.tool_input,
    toolResponse: isShellCommand ? { output: r.output } : r.result_json,
    transcriptPath: r.transcript_path,
    filePath: r.file_path,
    edits: r.edits,
  };
}

/**
 * Cursor output formatting function
 * Based on TS original: src/cli/adapters/cursor.ts formatOutput
 * Cursor expects { decision: "allow" } format (NOT { continue: true })
 *
 * @param {Object} result - Handler result
 * @returns {Object} - Formatted output
 */
function formatCursorOutput(result) {
  return { decision: "allow" };
}

/**
 * Read hook event from stdin
 */
function readHookEvent() {
  const chunks = [];
  const stdin = process.stdin;

  return new Promise((resolve, reject) => {
    stdin.on('data', chunk => chunks.push(chunk));
    stdin.on('end', () => {
      try {
        const input = Buffer.concat(chunks).toString();
        if (!input.trim()) {
          resolve(null);
        } else {
          resolve(JSON.parse(input));
        }
      } catch (e) {
        reject(e);
      }
    });
    stdin.on('error', reject);
  });
}

/**
 * Find CLAUDE.md starting from startDir
 */
function findClaudeMd(startDir) {
  let current = resolve(startDir);

  while (true) {
    const claudeMdPath = join(current, 'CLAUDE.md');
    const gitPath = join(current, '.git');

    if (existsSync(claudeMdPath) && statSync(claudeMdPath).isFile()) {
      return claudeMdPath;
    }

    if (existsSync(gitPath)) {
      return null; // Reached project root
    }

    const parent = dirname(current);
    if (parent === current) break;
    current = parent;
  }

  return null;
}

/**
 * Update CLAUDE.md with incremental tag replacement
 */
function updateClaudeMd(filePath, newContent) {
  try {
    if (!existsSync(filePath)) {
      // Create new CLAUDE.md with context wrapped in tag
      console.error(`[claude-mem] Creating ${filePath}`);
      writeFileSync(filePath, `<claude-mem-context>\n${newContent}\n</claude-mem-context>\n`, 'utf8');
      return true;
    }

    const existing = readFileSync(filePath, 'utf8');

    if (CONFIG.tagPattern.test(existing)) {
      const updated = existing.replace(
        CONFIG.tagPattern,
        `<claude-mem-context>\n${newContent}\n</claude-mem-context>`
      );

      if (updated !== existing) {
        console.error(`[claude-mem] Updated ${filePath}`);
        writeFileSync(filePath, updated, 'utf8');
        return true;
      }
    } else {
      const separator = existing.endsWith('\n') ? '' : '\n';
      const updated = `${separator}<claude-mem-context>\n${newContent}\n</claude-mem-context>\n`;
      console.error(`[claude-mem] Appended to ${filePath}`);
      writeFileSync(filePath, existing + updated, 'utf8');
      return true;
    }

    return false;
  } catch (error) {
    console.error(`[claude-mem] Error updating ${filePath}:`, error.message);
    return false;
  }
}

/**
 * Process updateFiles from Java response
 */
function processUpdateFiles(cwd, updateFiles) {
  if (!updateFiles || !Array.isArray(updateFiles)) {
    return { processed: [], errors: [] };
  }

  const processed = [];
  const errors = [];

  for (const file of updateFiles) {
    try {
      const targetPath = file.path.startsWith('/')
        ? file.path
        : join(cwd, file.path);

      const dir = dirname(targetPath);
      if (!existsSync(dir)) {
        mkdirSync(dir, { recursive: true });
      }

      if (file.path === 'CLAUDE.md' || file.path.endsWith('/CLAUDE.md')) {
        const claudeMdPath = findClaudeMd(cwd);
        if (claudeMdPath) {
          updateClaudeMd(claudeMdPath, file.content);
          processed.push({ path: relative(cwd, claudeMdPath), success: true });
        } else {
          const targetFilePath = join(cwd, 'CLAUDE.md');
          updateClaudeMd(targetFilePath, file.content);
          processed.push({ path: 'CLAUDE.md', success: true });
        }
      } else {
        writeFileSync(targetPath, file.content, 'utf8');
        processed.push({ path: file.path, success: true });
        console.error(`[claude-mem] Wrote ${file.path}`);
      }
    } catch (error) {
      errors.push({ path: file.path, error: error.message });
    }
  }

  return { processed, errors };
}

/**
 * Call Java API (POST)
 */
async function callJavaApi(endpoint, data) {
  try {
    const response = await axios({
      method: 'POST',
      url: `${CONFIG.javaUrl}${endpoint}`,
      data,
      timeout: CONFIG.timeout,
    });

    return response.data;
  } catch (error) {
    if (error.code === 'ECONNREFUSED') {
      console.error(`[claude-mem] Cannot connect to Java API at ${CONFIG.javaUrl}`);
      return null;
    }
    // Debug: log response details
    if (error.response) {
      console.error(`[DEBUG] API error ${error.response.status}: ${JSON.stringify(error.response.data)}`);
    } else {
      console.error(`[claude-mem] API error:`, error.message);
    }
    return null;
  }
}

/**
 * Extract the last assistant message from transcript file.
 * This is used for Prior Messages feature to maintain context continuity.
 *
 * @param cwd Working directory (project root)
 * @param sessionId Current session ID
 * @param customTranscriptPath Optional custom path for testing (or from Claude Code)
 * @returns The last assistant message content, or null if not found
 */
function extractLastAssistantMessage(cwd, sessionId, customTranscriptPath = null) {
  try {
    // Prefer custom path if provided (from Claude Code or test), otherwise use standard path
    // Claude Code stores transcripts in ~/.claude/projects/{project_hash}/{session_id}.jsonl
    const homeDir = process.env.HOME || process.env.USERPROFILE || (process.env.HOME === '' ? '/Users/yangjiefeng' : '.claude');
    const transcriptPath = customTranscriptPath || join(homeDir, '.claude', 'projects', cwd, `${sessionId}.jsonl`);

    if (!existsSync(transcriptPath)) {
      console.error(`[claude-mem] Transcript not found: ${transcriptPath}`);
      return null;
    }

    const content = readFileSync(transcriptPath, 'utf8').trim();
    if (!content) {
      return null;
    }

    const lines = content.split('\n').filter(line => line.trim());
    let lastAssistantMessage = '';

    // Find the last assistant message (reverse order)
    for (let i = lines.length - 1; i >= 0; i--) {
      try {
        const line = lines[i];
        if (!line.includes('"type":"assistant"')) {
          continue;
        }

        const entry = JSON.parse(line);
        if (entry.type === 'assistant' && entry.message?.content && Array.isArray(entry.message.content)) {
          let text = '';
          for (const block of entry.message.content) {
            if (block.type === 'text') {
              text += block.text;
            }
          }
          // Strip system reminders
          text = text.replace(/<system-reminder>[\s\S]*?<\/system-reminder>/g, '').trim();
          if (text) {
            lastAssistantMessage = text;
            break;
          }
        }
      } catch (parseError) {
        // Skip malformed lines
        continue;
      }
    }

    if (lastAssistantMessage) {
      console.error(`[claude-mem] Extracted lastAssistantMessage (${lastAssistantMessage.length} chars)`);
    }

    return lastAssistantMessage || null;
  } catch (error) {
    console.error(`[claude-mem] Error extracting lastAssistantMessage:`, error.message);
    return null;
  }
}

/**
 * Handle session-start event (after compaction).
 *
 * Flow:
 *   1. Detect worktree and get project context (parent + worktree if applicable)
 *   2. Call /api/session/start (session init + context generation)
 *   3. Process updateFiles → write CLAUDE.md to disk ← WRITES FILES!
 *   4. Output context to stdout (injected into Claude's context)
 *
 * Worktree Support (TS Alignment):
 *   - If in a worktree, pass both parent and worktree projects to backend
 *   - Backend queries unified timeline from both projects
 *
 * Debug mode (debug=true):
 *   - Still calls Java API (E2E test)
 *   - Still outputs context to stdout
 *   - Does NOT write files (updateFiles may be empty in debug mode)
 *
 * @param event Hook event from Claude Code
 */
async function handleSessionStart(event) {
  const { cwd, session_id, source, debug, ...extra } = event;

  // TS Alignment: Process on ALL session-start events (fresh start + compact)
  // Note: TypeScript version's contextHandler doesn't check source, so we don't either
  console.error(`[claude-mem] Session start${source !== 'compact' ? ' (fresh start)' : ' (compact)'}, initializing session and getting context...`);

  // TS Alignment: Get project context with worktree detection
  // If in a worktree, allProjects = [parentProjectName, primary]
  const projectContext = getProjectContext(cwd);

  if (projectContext.isWorktree) {
    console.error(`[claude-mem] Worktree detected: ${projectContext.primary} -> parent: ${projectContext.parent}`);
  }

  // Use unified /api/session/start endpoint
  // Combines session initialization + context generation in one call
  // Pass projects parameter for unified timeline (worktree support)
  const data = await callJavaApi('/api/session/start', {
    session_id: session_id,
    project_path: cwd,
    cwd: cwd,
    // TS Alignment: Pass all projects for unified timeline query
    projects: projectContext.allProjects.join(','),
    is_worktree: projectContext.isWorktree,
    parent_project: projectContext.parent
  });

  if (!data) {
    process.exit(0); // Continue without context
  }

  // Log session info
  if (data.session_db_id) {
    console.error(`[claude-mem] Session initialized: ${data.session_db_id}`);
  }

  // DEPRECATED (2026-02-15): Folder CLAUDE.md updates moved to PostToolUse
  // The updateFiles field from session-start is no longer processed here.
  // Folder CLAUDE.md updates now happen in PostToolUse via updateFolderClaudeMdFiles()
  // This aligns with TypeScript version behavior in ResponseProcessor.ts
  if (data.updateFiles && Array.isArray(data.updateFiles) && data.updateFiles.length > 0) {
    console.error(`[claude-mem] NOTE: updateFiles from session-start is deprecated. Folder CLAUDE.md updates now happen in PostToolUse.`);
  }

  // TS Alignment: Output context in JSON format (hookSpecificOutput.additionalContext)
  // This matches the TypeScript version's context.ts output format
  if (data.context) {
    console.log(JSON.stringify({
      hookSpecificOutput: {
        hookEventName: 'SessionStart',
        additionalContext: data.context
      }
    }));
  }
}

/**
 * Handle PostToolUse event (capture observations).
 *
 * Flow:
 *   1. Call /api/ingest/tool-use (async observation extraction)
 *   2. If CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true, update folder CLAUDE.md files
 *   3. Exit (NO stdout context)
 *
 * TS Alignment: Mirrors TypeScript ResponseProcessor.ts behavior.
 * After storing observations, updates folder-level CLAUDE.md files (fire-and-forget).
 *
 * Active File Exclusion (Issue #859):
 *   Skips folders where CLAUDE.md was read/modified in the current tool use.
 *   This prevents "file modified since read" errors in Claude Code.
 *
 * @param event Hook event from Claude Code
 */
async function handlePostToolUse(event) {
  const { cwd, session_id, tool_name, tool_input, tool_response } = event;

  console.error(`[claude-mem] Tool used: ${tool_name}`);

  // Only process for Edit/Write/Read tools
  if (!['Edit', 'Write', 'Read', 'Bash'].includes(tool_name)) {
    process.exit(0);
  }

  // TS Alignment: Strip privacy tags from tool_input and tool_response
  // This prevents sensitive content (marked with <private>) from being stored
  const strippedToolInput = tool_input ? stripMemoryTagsFromJson(JSON.stringify(tool_input)) : '{}';
  const strippedToolResponse = tool_response ? stripMemoryTagsFromJson(JSON.stringify(tool_response)) : '{}';

  const data = await callJavaApi('/api/ingest/tool-use', {
    session_id: session_id,
    tool_name: tool_name,
    tool_input: strippedToolInput,
    tool_response: strippedToolResponse,
    cwd: cwd,
  });

  if (!data) {
    if (CONFIG.debug) console.error('[DEBUG] API call failed, exiting without folder update');
    process.exit(0);
  }

  if (CONFIG.debug) {
    console.error('[DEBUG] API call succeeded, folderClaudeMdEnabled:', CONFIG.folderClaudeMdEnabled);
  }

  // TS Alignment: Update folder CLAUDE.md files after observation is stored
  // Only runs if CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true (default: false)
  // Mirrors TS ResponseProcessor.ts syncAndBroadcastObservations() behavior
  //
  // TIMING FIX (2026-02-15): Java version uses async processing (@Async),
  // so observation may not be written when API returns. We poll the API
  // until observations are found or timeout (30 seconds max).
  // See docs/drafts/context-injection-hooks-analysis.md section 15 for details.
  if (CONFIG.folderClaudeMdEnabled) {
    const filePaths = extractFilePathsFromToolInput(tool_name, tool_input);

    if (CONFIG.debug) {
      console.error('[DEBUG] filePaths extracted:', JSON.stringify(filePaths));
      console.error('[DEBUG] tool_name:', tool_name, 'tool_input:', JSON.stringify(tool_input));
    }
    
    if (filePaths.length > 0) {
      if (CONFIG.debug) console.error('[DEBUG] Calling updateFolderClaudeMdFiles');
      await updateFolderClaudeMdFiles(filePaths, cwd);
      if (CONFIG.debug) console.error('[DEBUG] updateFolderClaudeMdFiles completed');
      process.exit(0);
      return;
    }
  }

  process.exit(0);
}

/**
 * Extract file paths from tool_input based on tool type.
 * Used for folder CLAUDE.md updates.
 *
 * @param tool_name - The tool name (Edit, Write, Read, Bash)
 * @param tool_input - The tool input object
 * @returns Array of file paths (may include relative and absolute paths)
 */
function extractFilePathsFromToolInput(tool_name, tool_input) {
  const paths = [];

  if (!tool_input || typeof tool_input !== 'object') {
    return paths;
  }

  switch (tool_name) {
    case 'Edit':
      if (tool_input.file_path) paths.push(tool_input.file_path);
      break;
    case 'Write':
      if (tool_input.file_path) paths.push(tool_input.file_path);
      break;
    case 'Read':
      if (tool_input.file_path) paths.push(tool_input.file_path);
      break;
    case 'Bash':
      // Bash commands may reference files, but parsing is complex
      // For now, skip Bash - it's less common for file operations
      break;
  }

  return paths;
}

/**
 * Update CLAUDE.md files for folders containing the given files.
 * TS Alignment: Mirrors src/utils/claude-md-utils.ts updateFolderClaudeMdFiles()
 *
 * Key behaviors:
 *   - Skips project root folders (contains .git)
 *   - Skips folders where CLAUDE.md was actively read/modified (Issue #859)
 *   - Skips unsafe directories (node_modules, .git, build, etc.)
 *   - Fetches timeline via /api/search/by-file
 *   - Writes to folder's CLAUDE.md file
 *
 * @param filePaths - Array of file paths (may be relative or absolute)
 * @param cwd - Current working directory (project root)
 */
/**
 * Wait for observations to be written to database.
 * Polls the search API until observations are found or timeout.
 * 
 * Note: Java backend uses @Async, so observations may take several seconds to be written.
 * This polling mechanism ensures we wait long enough for the async processing to complete.
 * Only enabled when CONFIG.debug is true.
 */
async function waitForObservations(project, folderPath, getRelativeFolderPath, maxAttempts = 30, intervalMs = 2000) {
  const log = (...args) => CONFIG.debug && console.error(...args);
  
  log('[DEBUG] >>> waitForObservations called');
  log('[DEBUG] project:', project, 'folderPath:', folderPath);
  
  try {
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      log(`[DEBUG] Polling attempt ${attempt}/${maxAttempts}`);
      
      const relativeFolderPath = getRelativeFolderPath(folderPath);
      log('[DEBUG] relativeFolderPath:', relativeFolderPath);
      
      try {
        log('[DEBUG] About to call API with params:', JSON.stringify({
          project: project,
          filePath: relativeFolderPath,
          isFolder: 'true',
          limit: 50
        }));
        
        const response = await axios({
          method: 'GET',
          url: `${CONFIG.javaUrl}/api/search/by-file`,
          params: {
            project: project,
            filePath: relativeFolderPath,
            isFolder: 'true',
            limit: 50
          },
          timeout: CONFIG.timeout,
        });
        log('[DEBUG] API response received, status:', response.status);
        
        const result = response.data;
        log('[DEBUG] result:', result);
        log('[DEBUG] API result observations count:', result.observations?.length);
        if (result.observations && result.observations.length > 0) {
          log('[DEBUG] Found observations, returning');
          return result.observations;
        }
      } catch (error) {
        log('[DEBUG] Axios error:', error.message);
        log('[DEBUG] Error code:', error.code);
        log('[DEBUG] Error stack:', error.stack);
      }
      
      log('[DEBUG] No observations found, will retry');
      if (attempt < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, intervalMs));
      }
    }
    
    log('[DEBUG] Timeout reached');
    return null;
  } catch (err) {
    log('[DEBUG] waitForObservations exception:', err);
    return null;
  }
}

async function updateFolderClaudeMdFiles(filePaths, cwd) {
  const log = (...args) => CONFIG.debug && console.error(...args);
  
  log('[DEBUG] >>> updateFolderClaudeMdFiles called');
  log('[DEBUG] filePaths:', JSON.stringify(filePaths));
  log('[DEBUG] cwd:', cwd);
  console.error(`[claude-mem] Folder CLAUDE.md update triggered (CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true)`);
  
  const project = cwd;
  log('[DEBUG] project (full path):', project);

  // Convert absolute paths to relative paths for API queries
  // (Database stores relative paths like "src/utils/", not absolute paths)
  const toRelativePath = (absPath) => {
    if (!absPath || !cwd) return absPath;
    if (absPath.startsWith(cwd + '/')) {
      return absPath.substring(cwd.length + 1);
    }
    return absPath;
  };
  
  // Get relative folder paths for API queries
  const getRelativeFolderPath = (absFolderPath) => {
    const relPath = toRelativePath(absFolderPath);
    // API requires trailing slash for folder queries
    return relPath.endsWith('/') ? relPath : relPath + '/';
  };

  // Track folders with actively-used CLAUDE.md files (Issue #859)
  const foldersWithActiveClaudeMd = new Set();

  // First pass: identify folders with actively-used CLAUDE.md files
  for (const filePath of filePaths) {
    if (!filePath) continue;
    const baseName = basename(filePath);
    if (baseName === 'CLAUDE.md') {
      const absolutePath = filePath.startsWith('/') ? filePath : join(cwd, filePath);
      const folderPath = dirname(absolutePath);
      foldersWithActiveClaudeMd.add(folderPath);
      console.error(`[claude-mem] Skipping active CLAUDE.md folder: ${folderPath}`);
    }
  }

  // Extract unique folder paths
  const folderPaths = new Set();
  for (const filePath of filePaths) {
    if (!filePath || filePath.trim() === '') continue;

    // Skip invalid paths (tilde, URLs, spaces, etc.)
    if (!isValidPathForClaudeMd(filePath, cwd)) {
      continue;
    }

    const absolutePath = filePath.startsWith('/') ? filePath : join(cwd, filePath);
    const folderPath = dirname(absolutePath);

    if (folderPath && folderPath !== '.' && folderPath !== '/') {
      if (CONFIG.debug) console.error(`[DEBUG] Processing folderPath: ${folderPath}`);
      
      // Skip project root (contains .git)
      if (isProjectRoot(folderPath)) {
        if (CONFIG.debug) console.error(`[DEBUG] Skipping project root: ${folderPath}`);
        continue;
      }
      // Skip unsafe directories
      if (isExcludedUnsafeDirectory(folderPath)) {
        continue;
      }
      // Skip folders in user-configured exclude list
      if (CONFIG.folderMdExcludePaths.length > 0 && isExcludedFolder(folderPath, CONFIG.folderMdExcludePaths)) {
        if (CONFIG.debug) console.error(`[DEBUG] Skipping excluded folder: ${folderPath}`);
        continue;
      }
      // Skip folders with active CLAUDE.md (Issue #859)
      if (foldersWithActiveClaudeMd.has(folderPath)) {
        continue;
      }
      folderPaths.add(folderPath);
      if (CONFIG.debug) console.error(`[DEBUG] Added folder to update list: ${folderPath}`);
    }
  }

  if (folderPaths.size === 0) {
    if (CONFIG.debug) console.error(`[DEBUG] No folder paths to update (empty after filtering)`);
    return;
  }

  console.error(`[claude-mem] Updating ${folderPaths.size} folder CLAUDE.md files...`);

  for (const folderPath of folderPaths) {
    log('[DEBUG] Processing folder:', folderPath);
    try {
      log('[DEBUG] Calling waitForObservations for:', folderPath);
      const observations = await waitForObservations(project, folderPath, getRelativeFolderPath);
      log('[DEBUG] waitForObservations returned:', observations?.length, 'observations');
      
      if (!observations || observations.length === 0) {
        log('[DEBUG] No observations found for folder:', folderPath);
        continue;
      }

      log('[DEBUG] Got observations, calling formatObservationsForClaudeMd');

      const formatted = formatObservationsForClaudeMd(observations);
      log('[DEBUG] formatObservationsForClaudeMd returned:', formatted?.substring(0, 50));

      const claudeMdPath = join(folderPath, 'CLAUDE.md');
      log('[DEBUG] claudeMdPath:', claudeMdPath, 'exists:', existsSync(claudeMdPath));

      if (!existsSync(claudeMdPath) && formatted.includes('No recent activity')) {
        log('[DEBUG] Skipping - no file and no activity');
        continue;
      }

      log('[DEBUG] Calling updateClaudeMd');
      updateClaudeMd(claudeMdPath, formatted);
      console.error(`[claude-mem] Updated folder CLAUDE.md: ${folderPath}`);

    } catch (error) {
      // Non-critical error, continue with other folders
      console.error(`[claude-mem] Failed to update folder CLAUDE.md: ${folderPath}`, error.message);
    }
  }
}

/**
 * Format observations for CLAUDE.md content.
 * TS Alignment: Mirrors src/utils/claude-md-utils.ts formatTimelineForClaudeMd()
 *
 * @param observations - Array of observation objects from API
 * @returns Formatted markdown string
 */
function formatObservationsForClaudeMd(observations) {
  const log = (...args) => CONFIG.debug && console.error(...args);
  
  log('[DEBUG] >>> formatObservationsForClaudeMd called');
  log('[DEBUG] observations length:', observations?.length);
  
  if (!observations || observations.length === 0) {
    log('[DEBUG] observations is empty, returning empty string');
    return '';
  }

  const getEpoch = (obs) => obs.created_at_epoch || obs.createdAtEpoch;
  log('[DEBUG] first observation epoch:', getEpoch(observations[0]));

  const lines = ['# Recent Activity', ''];

  // Group by date
  const byDate = new Map();
  for (const obs of observations) {
    const epoch = getEpoch(obs);
    if (!epoch) continue;

    const date = new Date(epoch);
    const dateStr = date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });

    if (!byDate.has(dateStr)) {
      byDate.set(dateStr, []);
    }
    byDate.get(dateStr).push(obs);
  }

  // Render each date group
  for (const [dateStr, dayObs] of byDate) {
    lines.push(`### ${dateStr}`);
    lines.push('');
    lines.push('| ID | Time | T | Title | Read |');
    lines.push('|----|------|---|-------|------|');

    let lastTime = '';
    for (const obs of dayObs) {
      const epoch = getEpoch(obs);
      const time = new Date(epoch).toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true
      });

      const timeDisplay = time === lastTime ? '"' : time;
      lastTime = time;

      const id = obs.id ? `#${obs.id.toString().slice(0, 6)}` : '#?';
      const typeEmoji = getTypeEmoji(obs.type);
      const title = (obs.title || '(untitled)').slice(0, 50);
      const tokens = obs.tokens ? `~${obs.tokens}` : '~?';

      lines.push(`| ${id} | ${timeDisplay} | ${typeEmoji} | ${title} | ${tokens} |`);
    }

    lines.push('');
  }

  return lines.join('\n').trim();
}

/**
 * Get emoji for observation type.
 */
function getTypeEmoji(type) {
  const typeEmojis = {
    'decision': '🔧',
    'progress': '✅',
    'finding': '🔍',
    'issue': '🔴',
    'learning': '🟣',
    'refactor': '🔄',
    'default': '🔵'
  };
  return typeEmojis[type] || typeEmojis['default'];
}

/**
 * Validate that a file path is safe for CLAUDE.md generation.
 * TS Alignment: Mirrors src/utils/claude-md-utils.ts isValidPathForClaudeMd()
 */
function isValidPathForClaudeMd(filePath, projectRoot) {
  if (!filePath || !filePath.trim()) return false;
  if (filePath.startsWith('~')) return false;
  if (filePath.startsWith('http://') || filePath.startsWith('https://')) return false;
  if (filePath.includes(' ')) return false;
  if (filePath.includes('#')) return false;

  return true;
}

/**
 * Check if a folder is a project root (contains .git directory).
 */
function isProjectRoot(folderPath) {
  const gitPath = join(folderPath, '.git');
  return existsSync(gitPath);
}

/**
 * Check if a folder path contains any excluded segment.
 */
function isExcludedUnsafeDirectory(folderPath) {
  const excludedDirs = ['res', '.git', 'build', 'node_modules', '__pycache__'];
  const segments = folderPath.split(/[/\\]/);
  return segments.some(segment => excludedDirs.includes(segment));
}

/**
 * Check if a folder path is excluded from CLAUDE.md generation.
 * TS Alignment: Mirrors src/utils/claude-md-utils.ts isExcludedFolder()
 *
 * @param folderPath - Absolute path to check
 * @param excludePaths - Array of paths to exclude
 * @returns true if folder should be excluded
 */
function isExcludedFolder(folderPath, excludePaths) {
  const normalizedFolder = resolve(folderPath);
  for (const excludePath of excludePaths) {
    const normalizedExclude = resolve(excludePath);
    if (normalizedFolder === normalizedExclude ||
        normalizedFolder.startsWith(normalizedExclude + sep)) {
      return true;
    }
  }
  return false;
}

/**
 * Handle SessionEnd event (session ended).
 *
 * Flow:
 *   1. Extract lastAssistantMessage from transcript (read from ~/.claude/projects/)
 *   2. Call /api/ingest/session-end (marks session completed, triggers async summary)
 *
 * NO FILES WRITTEN - This hook only reads transcript and calls API.
 *
 * Debug mode (debug=true):
 *   - Still reads transcript and extracts message
 *   - Still calls Java API (E2E test)
 *   - Outputs [DEBUG_LAST_ASSISTANT_MESSAGE:...] to stdout for test verification
 *
 * @param event Hook event from Claude Code
 */
async function handleSessionEnd(event) {
  const { cwd, session_id, transcript_path, debug } = event;

  console.error(`[claude-mem] Session end: ${session_id}`);

  // Extract lastAssistantMessage from transcript for Prior Messages feature
  // Prefer transcript_path from Claude Code if provided, otherwise construct from cwd/sessionId
  const lastAssistantMessage = extractLastAssistantMessage(cwd, session_id, transcript_path);

  // Debug mode: output parsed message for testing verification
  if (debug === true) {
    console.error(`[claude-mem] Debug: Extracted lastAssistantMessage (${lastAssistantMessage?.length || 0} chars)`);
    if (lastAssistantMessage) {
      // Output to stdout for test verification
      console.log(`[DEBUG_LAST_ASSISTANT_MESSAGE:${lastAssistantMessage}]`);
    }
    // Continue to call Java API - this is still E2E test!
  }

  const data = await callJavaApi('/api/ingest/session-end', {
    session_id: session_id,
    cwd: cwd,
    last_assistant_message: lastAssistantMessage,
  });

  if (data) {
    console.error(`[claude-mem] Session ended and summary generation started`);
  }
}

/**
 * Handle UserPromptSubmit event (user submitted a prompt).
 *
 * Flow:
 *   1. Call /api/ingest/user-prompt (records prompt to database)
 *
 * NO FILES WRITTEN - This hook only records the prompt.
 *
 * @param event Hook event from Claude Code
 */
async function handleUserPrompt(event) {
  const { cwd, session_id, prompt_text, prompt_number } = event;

  console.error(`[claude-mem] User prompt submitted: ${session_id}`);

  // TS Alignment: Strip privacy tags from prompt_text
  // If prompt is entirely private (strips to empty), skip recording
  const strippedPromptText = prompt_text ? stripMemoryTagsFromPrompt(prompt_text) : '';

  // Skip processing if prompt is entirely private
  if (isEntirelyPrivate(prompt_text)) {
    console.error(`[claude-mem] User prompt is entirely private, skipping recording`);
    process.exit(0);
    return;
  }

  const data = await callJavaApi('/api/ingest/user-prompt', {
    session_id: session_id,
    prompt_text: strippedPromptText,
    prompt_number: prompt_number || 1,
    cwd: cwd,
  });

  if (data) {
    console.error(`[claude-mem] User prompt recorded`);
  }
}

// ============================================================================
// CURSOR IDE HOOK HANDLERS
// ============================================================================

/**
 * Handle Cursor beforeSubmitPrompt hook.
 * Maps to session init + context generation.
 *
 * @param event Hook event from Cursor IDE
 */
async function handleCursorBeforeSubmitPrompt(event) {
  // Use unified Cursor input normalization
  if (CONFIG.debug) {
    console.error(`[DEBUG] Cursor beforeSubmitPrompt event: ${JSON.stringify(event).slice(0, 500)}`);
  }
  
  const input = normalizeCursorInput(event);
  const cwd = input.cwd;
  const session_id = input.session_id;
  const prompt_text = input.prompt || '';  // 从 normalizeCursorInput 获取 prompt
  const prompt_number = event?.prompt_number || 1;

  if (CONFIG.debug) {
    console.error(`[claude-mem] Cursor beforeSubmitPrompt: cwd=${cwd}, session_id=${session_id}, prompt_text="${prompt_text.slice(0, 50)}..."`);
  }

  // Get project context (worktree detection)
  const projectContext = getProjectContext(cwd);

  // P0: Record user prompt FIRST (triggers SSE broadcast to Web UI)
  // This is the same as Claude Code's UserPromptSubmit handler
  await callJavaApi('/api/ingest/user-prompt', {
    session_id: session_id,
    cwd: cwd,  // Java expects "cwd" not "project_path"
    prompt_text: prompt_text,
    prompt_number: prompt_number
  });

  // Then call session-start API (handles init + context generation)
  const data = await callJavaApi('/api/session/start', {
    session_id: session_id,
    project_path: cwd,
    projects: projectContext.allProjects.join(','),
    is_worktree: projectContext.isWorktree,
    parent_project: projectContext.parent,
    prompt_text: prompt_text,
    prompt_number: prompt_number
  });

  if (!data) {
    console.error(`[claude-mem] Failed to get context from API`);
    // Cursor expects JSON output
    console.log(JSON.stringify(formatCursorOutput({ continue: true })));
    return;
  }

  if (data.context) {
    // Update Cursor context file (main context injection method for Cursor)
    // Note: Cursor does NOT use CLAUDE.md - context is injected via .cursor/context.md
    const projectName = projectContext.isWorktree
      ? projectContext.parent
      : projectContext.primary;
    console.error(`[DEBUG] Project name: ${projectName}, cwd: ${cwd}`);

    // Try to update Cursor context file
    await updateCursorContextFile(projectName, cwd, data.context);
  }

  // Cursor adapter formatOutput: only outputs { continue: true }
  // Context is injected via file, not stdout
  const output = JSON.stringify(formatCursorOutput({ continue: true }));
  console.error(`[DEBUG] stdout output: ${output}`);
  console.log(output);
}

/**
 * Handle Cursor afterMCPExecution / afterShellExecution hook.
 * Maps to observation recording.
 *
 * @param event Hook event from Cursor IDE
 */
async function handleCursorAfterExecution(event) {
  // Use unified Cursor input normalization
  if (CONFIG.debug) {
    console.error(`[DEBUG] Cursor afterExecution event: ${JSON.stringify(event).slice(0, 500)}`);
  }
  
  const input = normalizeCursorInput(event);
  const cwd = input.cwd;
  const session_id = input.session_id;
  const tool_name = input.toolName;
  const tool_input = input.toolInput;
  const tool_response = input.toolResponse;

  // Skip non-essential tools
  const skipTools = ['Glob', 'LS', 'ListFiles', 'GetFileDetails', 'CodebaseSearch'];
  if (skipTools.includes(tool_name)) {
    if (CONFIG.debug) {
      console.error(`[claude-mem] Cursor: Skipping tool ${tool_name}`);
    }
    console.log(JSON.stringify(formatCursorOutput({ continue: true })));
    return;
  }

  if (CONFIG.debug) {
    console.error(`[claude-mem] Cursor afterExecution: ${tool_name}`);
  }

  // Get project context for worktree support
  const projectContext = getProjectContext(cwd);

  // TS Alignment: Strip privacy tags from tool_input and tool_response
  const strippedToolInput = tool_input
    ? stripMemoryTagsFromJson(typeof tool_input === 'string' ? tool_input : JSON.stringify(tool_input))
    : '{}';
  const strippedToolResponse = tool_response
    ? stripMemoryTagsFromJson(typeof tool_response === 'string' ? tool_response : JSON.stringify(tool_response))
    : '{}';

  const data = await callJavaApi('/api/ingest/tool-use', {
    session_id: session_id,
    tool_name: tool_name,
    tool_input: strippedToolInput,
    tool_response: strippedToolResponse,
    project_path: cwd,
    projects: projectContext.allProjects.join(','),
    is_worktree: projectContext.isWorktree
  });

  if (data && data.status === 'accepted') {
    if (CONFIG.debug) {
      console.error(`[claude-mem] Cursor observation queued`);
    }
  }

  // Cursor expects JSON output
  console.log(JSON.stringify(formatCursorOutput({ continue: true })));
}

/**
 * Handle Cursor stop hook.
 * Maps to summary generation.
 *
 * @param event Hook event from Cursor IDE
 */
async function handleCursorStop(event) {
  // Use unified Cursor input normalization
  const input = normalizeCursorInput(event);
  const cwd = input.cwd;
  const session_id = input.session_id;
  const transcript_path = input.transcriptPath;

  if (CONFIG.debug) {
    console.error(`[claude-mem] Cursor stop: ${session_id}`);
  }

  // Get project context
  const projectContext = getProjectContext(cwd);

  // Read last assistant message from transcript if available
  let lastAssistantMessage = '';
  if (transcript_path && existsSync(transcript_path)) {
    lastAssistantMessage = extractLastAssistantMessage(transcript_path);
  } else if (CONFIG.debug) {
    console.error(`[claude-mem] Transcript not found: ${transcript_path}`);
  }

  const data = await callJavaApi('/api/ingest/session-end', {
    session_id: session_id,
    project_path: cwd,
    projects: projectContext.allProjects.join(','),
    is_worktree: projectContext.isWorktree,
    last_assistant_message: lastAssistantMessage
  });

  if (data) {
    if (CONFIG.debug) {
      console.error(`[claude-mem] Cursor session ended, summary scheduled`);
    }

    // Update Cursor context file
    const projectName = projectContext.isWorktree
      ? projectContext.parent
      : projectContext.primary;

    await updateCursorContextFile(projectName, cwd, null);
  }

  // Cursor expects JSON output
  console.log(JSON.stringify(formatCursorOutput({ continue: true })));
}

/**
 * Update Cursor context file via API.
 *
 * @param projectName Project name
 * @param cwd Working directory
 * @param context Context content (null to fetch from API)
 */
async function updateCursorContextFile(projectName, cwd, context) {
  try {
    // First register the project
    await callJavaApi('/api/cursor/register', {
      projectName: projectName,
      workspacePath: cwd
    });

    // Then update context (API will fetch fresh context if null)
    if (context) {
      await callJavaApi(`/api/cursor/context/${encodeURIComponent(projectName)}/custom`, {
        context: context
      });
    } else {
      await callJavaApi(`/api/cursor/context/${encodeURIComponent(projectName)}`, {});
    }

    if (CONFIG.debug) {
      console.error(`[claude-mem] Cursor context file updated for ${projectName}`);
    }
  } catch (error) {
    if (CONFIG.debug) {
      console.error(`[claude-mem] Failed to update Cursor context: ${error.message}`);
    }
  }
}

/**
 * Handle cursor subcommands.
 *
 * @param subCommand Cursor subcommand (context-init, context, observation, summarize)
 */
async function handleCursorCommand(subCommand) {
  // Read hook event from stdin
  const event = await readHookEvent();

  if (!event) {
    if (CONFIG.debug) {
      console.error(`[claude-mem] Cursor: No stdin event for command ${subCommand}`);
    }
    process.exit(0);
  }

  switch (subCommand) {
    case 'session-init':
    case 'context-init':
      // Initialize session (called before context)
      if (CONFIG.debug) {
        console.error(`[claude-mem] Cursor session-init`);
      }
      // Just log, the context command does the real work
      break;

    case 'context':
      // Context injection (beforeSubmitPrompt)
      await handleCursorBeforeSubmitPrompt(event);
      break;

    case 'observation':
      // Observation recording (afterMCPExecution, afterShellExecution)
      await handleCursorAfterExecution(event);
      break;

    case 'file-edit':
      // File edit observation (afterFileEdit hook from Cursor)
      // Cursor sends file_path and edits, not tool_input/tool_response
      event.tool_name = 'write_file';
      event.tool_input = {
        filePath: event.file_path,
        edits: event.edits
      };
      event.tool_response = { success: true };
      await handleCursorAfterExecution(event);
      break;

    case 'summarize':
      // Summary generation (stop)
      await handleCursorStop(event);
      break;

    default:
      console.error(`[claude-mem] Unknown cursor command: ${subCommand}`);
      printCursorHelp();
      break;
  }

  process.exit(0);
}

/**
 * Print Cursor help.
 */
function printCursorHelp() {
  console.error(`
Cursor IDE Commands:
  cursor session-init  - Initialize session (beforeSubmitPrompt)
  cursor context       - Inject context (beforeSubmitPrompt)
  cursor observation   - Record observation (afterMCPExecution, afterShellExecution)
  cursor file-edit     - Record file edit observation
  cursor summarize     - Generate summary (stop)
`);
}

/**
 * Main handler
 */
async function main() {
  const command = process.argv[2];
  const subCommand = process.argv[3];
  const urlOverride = process.argv.indexOf('--url');

  if (urlOverride !== -1 && urlOverride + 1 < process.argv.length) {
    CONFIG.javaUrl = process.argv[urlOverride + 1];
  }

  // Parse --enable-folder-claudemd flag (alternative to CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED env var)
  if (process.argv.includes('--enable-folder-claudemd')) {
    CONFIG.folderClaudeMdEnabled = true;
  }

  // Handle --help or -h
  if (command === '--help' || command === '-h') {
    printHelp();
    process.exit(0);
  }

  // Handle cursor subcommands
  if (command === 'cursor') {
    await handleCursorCommand(subCommand);
    return;
  }

  // Read hook event from stdin
  const event = await readHookEvent();

  if (!event) {
    // No event data - handle based on command argument (for CLI testing)
    console.error(`[claude-mem] No stdin event, using command: ${command}`);

    switch (command) {
      case 'session-start':
        console.error(`[claude-mem] Error: session-start requires stdin with hook event`);
        break;
      case 'session-end':
        console.error(`[claude-mem] Error: session-end requires stdin with hook event`);
        break;
      case 'user-prompt':
        console.error(`[claude-mem] Error: user-prompt requires stdin with hook event`);
        break;
      case 'tool-use':
        console.error(`[claude-mem] Error: tool-use requires stdin with hook event`);
        break;
      default:
        if (command) {
          console.error(`[claude-mem] Unknown command: ${command}`);
        }
        break;
    }
    process.exit(0);
  }

  // Claude Code passes hook type via CLI argument, NOT in stdin
  // Prefer CLI command (process.argv[2]) over stdin hook_event_name for real Claude Code usage
  // Keep backward compatibility: if stdin has hook_event_name, use it (for testing legacy format)
  const commandToHookMap = {
    'session-start': 'SessionStart',
    'tool-use': 'PostToolUse',
    'session-end': 'SessionEnd',
    'user-prompt': 'UserPromptSubmit'
  };

  let hookEventName;

  // If CLI command is provided, use it to determine hook type
  if (command && commandToHookMap[command]) {
    hookEventName = commandToHookMap[command];
    console.error(`[claude-mem] Hook event (from CLI): ${hookEventName}`);
  } else if (event.hook_event_name) {
    // Backward compatibility: use hook_event_name from stdin
    hookEventName = event.hook_event_name;
    console.error(`[claude-mem] Hook event (from stdin, backward compat): ${hookEventName}`);
  } else {
    console.error(`[claude-mem] Error: Unknown hook type (CLI: ${command}, stdin no hook_event_name)`);
    process.exit(0);
  }

  switch (hookEventName) {
    case 'SessionStart':
      await handleSessionStart(event);
      break;

    case 'PostToolUse':
      await handlePostToolUse(event);
      break;

    case 'SessionEnd':
      await handleSessionEnd(event);
      break;

    case 'UserPromptSubmit':
      await handleUserPrompt(event);
      break;

    default:
      // Ignore other events
      console.error(`[claude-mem] Ignored event: ${hookEventName}`);
      break;
  }

  process.exit(0);
}

function printHelp() {
  console.log(`
Claude-Mem Hook Wrapper

Usage:
  cat event.json | ./wrapper.js <command> [options]

Claude Code Commands:
  session-start   Handle SessionStart event (session init + context)
  session-end     Handle SessionEnd event (trigger summary generation)
  user-prompt     Handle UserPromptSubmit event (record user prompt)
  tool-use        Handle PostToolUse event (capture observations)

Cursor IDE Commands:
  cursor session-init  Initialize session (beforeSubmitPrompt)
  cursor context       Inject context (beforeSubmitPrompt)
  cursor observation   Record observation (afterMCPExecution, afterShellExecution)
  cursor file-edit     Record file edit observation
  cursor summarize     Generate summary (stop)

Options:
  --url <url>           Java API URL (default: http://127.0.0.1:37777)
  --enable-folder-claudemd  Enable folder CLAUDE.md auto-update on file edits
  --help, -h            Show this help

Environment:
  JAVA_API_URL    Java API URL (default: http://127.0.0.1:37777)

Example Claude Code configuration in .claude/settings.json:
{
  "hooks": {
    "SessionStart": [{
      "hooks": [{
        "type": "command",
        "command": "/path/to/wrapper.js session-start --url http://127.0.0.1:37777",
        "blocking": false
      }]
    }],
    "PostToolUse": [{
      "matcher": "Edit|Write|Read|Bash",
      "hooks": [{
        "type": "command",
        "command": "/path/to/wrapper.js tool-use --url http://127.0.0.1:37777",
        "blocking": false
      }]
    }],
    "SessionEnd": [{
      "hooks": [{
        "type": "command",
        "command": "/path/to/wrapper.js session-end --url http://127.0.0.1:37777",
        "blocking": false
      }]
    }],
    "UserPromptSubmit": [{
      "hooks": [{
        "type": "command",
        "command": "/path/to/wrapper.js user-prompt --url http://127.0.0.1:37777",
        "blocking": false
      }]
    }]
  }
}
`);
}

// Export for testing
export {
  findClaudeMd,
  updateClaudeMd,
  processUpdateFiles,
  CONFIG,
  extractLastAssistantMessage,
  // Worktree detection
  detectWorktree,
  getProjectName,
  getProjectContext,
  // Folder CLAUDE.md updates (PostToolUse)
  extractFilePathsFromToolInput,
  updateFolderClaudeMdFiles,
  formatObservationsForClaudeMd,
  isValidPathForClaudeMd,
  isProjectRoot,
  isExcludedUnsafeDirectory,
  // Privacy Tags (TS alignment)
  stripMemoryTagsFromJson,
  stripMemoryTagsFromPrompt,
  isEntirelyPrivate,
};

// Run if executed directly
main();
