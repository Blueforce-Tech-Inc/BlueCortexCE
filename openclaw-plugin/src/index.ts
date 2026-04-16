/**
 * Claude-Mem OpenClaw Plugin for Java Backend
 *
 * This plugin integrates Claude-Mem memory system with OpenClaw Gateway,
 * connecting to the Java Spring Boot backend instead of the TypeScript version.
 *
 * Architecture (v2 - based on TS version investigation):
 * ```
 * OpenClaw Gateway
 * └── Claude-Mem Java Plugin (this)
 *     ├── HTTP Client → Java Backend (localhost:37777)
 *     ├── before_prompt_build → appendSystemContext (context injection)
 *     └── Observation Recording (tool_result_persist)
 * ```
 *
 * Key Improvement (v2):
 * - Uses `appendSystemContext` via `before_prompt_build` hook instead of MEMORY.md file sync
 * - This matches TS version behavior and eliminates file write race conditions
 * - MEMORY.md is now managed solely by the Agent (not by this plugin)
 */

// ============================================================================
// Type Definitions (aligned with OpenClaw Plugin SDK)
// ============================================================================

interface PluginLogger {
  debug?: (message: string) => void;
  info: (message: string) => void;
  warn: (message: string) => void;
  error: (message: string) => void;
}

interface PluginServiceContext {
  config: Record<string, unknown>;
  workspaceDir?: string;
  stateDir: string;
  logger: PluginLogger;
}

interface PluginCommandContext {
  senderId?: string;
  channel: string;
  isAuthorizedSender: boolean;
  args?: string;
  commandBody: string;
  config: Record<string, unknown>;
}

type PluginCommandResult = string | { text: string } | { text: string; format?: string };

// OpenClaw event types
interface BeforeAgentStartEvent {
  prompt?: string;
}

interface BeforePromptBuildEvent {
  prompt: string;
  messages: unknown[];
}

interface BeforePromptBuildResult {
  systemPrompt?: string;
  prependContext?: string;
  prependSystemContext?: string;
  appendSystemContext?: string;
}

interface ToolResultPersistEvent {
  toolName?: string;
  params?: Record<string, unknown>;
  message?: {
    content?: Array<{ type: string; text?: string }>;
  };
}

interface AgentEndEvent {
  messages?: Array<{
    role: string;
    content: string | Array<{ type: string; text?: string }>;
  }>;
}

interface SessionStartEvent {
  sessionId: string;
  resumedFrom?: string;
}

interface AfterCompactionEvent {
  messageCount: number;
  tokenCount?: number;
  compactedCount: number;
}

interface SessionEndEvent {
  sessionId: string;
  messageCount: number;
  durationMs?: number;
}

interface EventContext {
  sessionKey?: string;
  workspaceDir?: string;
  agentId?: string;
}

type EventCallback<T> = (event: T, ctx: EventContext) => void | Promise<void>;

interface OpenClawPluginApi {
  id: string;
  name: string;
  version?: string;
  source: string;
  config: Record<string, unknown>;
  pluginConfig?: Record<string, unknown>;
  logger: PluginLogger;
  registerService: (service: {
    id: string;
    start: (ctx: PluginServiceContext) => void | Promise<void>;
    stop?: (ctx: PluginServiceContext) => void | Promise<void>;
  }) => void;
  registerCommand: (command: {
    name: string;
    description: string;
    acceptsArgs?: boolean;
    requireAuth?: boolean;
    handler: (ctx: PluginCommandContext) => PluginCommandResult | Promise<PluginCommandResult>;
  }) => void;
  on: ((event: "before_agent_start", callback: EventCallback<BeforeAgentStartEvent>) => void) &
    ((event: "before_prompt_build", callback: (event: BeforePromptBuildEvent, ctx: EventContext) => BeforePromptBuildResult | Promise<BeforePromptBuildResult | void> | void) => void) &
    ((event: "tool_result_persist", callback: EventCallback<ToolResultPersistEvent>) => void) &
    ((event: "agent_end", callback: EventCallback<AgentEndEvent>) => void) &
    ((event: "session_start", callback: EventCallback<SessionStartEvent>) => void) &
    ((event: "session_end", callback: EventCallback<SessionEndEvent>) => void) &
    ((event: "after_compaction", callback: EventCallback<AfterCompactionEvent>) => void) &
    ((event: "gateway_start", callback: EventCallback<Record<string, never>>) => void);
  runtime: {
    channel: Record<string, Record<string, (...args: any[]) => Promise<any>>>;
  };
}

// ============================================================================
// Plugin Configuration
// ============================================================================

interface ClaudeMemJavaPluginConfig {
  /** Project name for memory tracking (default: "openclaw") */
  project?: string;
  /** Java backend port (default: 37777) */
  workerPort?: number;
}

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_WORKER_PORT = 37777;
const TOOL_RESULT_MAX_LENGTH = 1000;

// ============================================================================
// Circuit Breaker — prevents CPU-spinning when worker is unreachable
// After CIRCUIT_BREAKER_THRESHOLD consecutive network errors, the circuit
// opens and all worker calls are silently dropped for CIRCUIT_BREAKER_COOLDOWN_MS.
// After the cooldown, one probe attempt is allowed to check if the worker recovered.
// ============================================================================

const CIRCUIT_BREAKER_THRESHOLD = 3;
const CIRCUIT_BREAKER_COOLDOWN_MS = 30_000;

type CircuitState = "CLOSED" | "OPEN" | "HALF_OPEN";

let _circuitState: CircuitState = "CLOSED";
let _circuitFailures = 0;
let _circuitOpenedAt = 0;
let _halfOpenProbeInFlight = false;

function circuitAllow(logger: PluginLogger): boolean {
  if (_circuitState === "CLOSED") return true;
  if (_circuitState === "OPEN") {
    if (Date.now() - _circuitOpenedAt >= CIRCUIT_BREAKER_COOLDOWN_MS) {
      _circuitState = "HALF_OPEN";
      logger.info("[claude-mem] Circuit breaker: probing worker connection");
      if (_halfOpenProbeInFlight) return false;
      _halfOpenProbeInFlight = true;
      return true;
    }
    return false;
  }
  // HALF_OPEN: allow one probe through
  if (_halfOpenProbeInFlight) return false;
  return true;
}

function circuitRecordFailure(): void {
  if (_circuitState === "HALF_OPEN") {
    _circuitState = "OPEN";
    _circuitOpenedAt = Date.now();
    _halfOpenProbeInFlight = false;
    return;
  }
  _circuitFailures++;
  if (_circuitState === "CLOSED" && _circuitFailures >= CIRCUIT_BREAKER_THRESHOLD) {
    _circuitState = "OPEN";
    _circuitOpenedAt = Date.now();
  }
}

function circuitRecordSuccess(): void {
  if (_circuitState === "HALF_OPEN") {
    _halfOpenProbeInFlight = false;
  }
  _circuitState = "CLOSED";
  _circuitFailures = 0;
  _circuitOpenedAt = 0;
}

function circuitReset(): void {
  _circuitState = "CLOSED";
  _circuitFailures = 0;
  _circuitOpenedAt = 0;
  _halfOpenProbeInFlight = false;
}

// ============================================================================
// Context Cache — 60s TTL cache for before_prompt_build context injection
// ============================================================================

const CONTEXT_CACHE_TTL_MS = 60_000;
const contextCache = new Map<string, { text: string; fetchedAt: number }>();

function getCachedContext(cacheKey: string): string | null {
  const cached = contextCache.get(cacheKey);
  if (cached && Date.now() - cached.fetchedAt < CONTEXT_CACHE_TTL_MS) {
    return cached.text;
  }
  return null;
}

function setCachedContext(cacheKey: string, text: string): void {
  contextCache.set(cacheKey, { text, fetchedAt: Date.now() });
}

// ============================================================================
// HTTP Client (Java Backend API)
// ============================================================================

function workerBaseUrl(port: number): string {
  return `http://127.0.0.1:${port}`;
}

/**
 * POST to Java backend API
 * Adapted for Java endpoints:
 * - /api/session/start (instead of /api/sessions/init)
 * - /api/ingest/tool-use (instead of /api/sessions/observations)
 * - /api/ingest/session-end (instead of /api/sessions/complete)
 */
async function workerPost(
  port: number,
  path: string,
  body: Record<string, unknown>,
  logger: PluginLogger
): Promise<Record<string, unknown> | null> {
  try {
    const response = await fetch(`${workerBaseUrl(port)}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      logger.warn(`[claude-mem] Worker POST ${path} returned ${response.status}`);
      return null;
    }
    return (await response.json()) as Record<string, unknown>;
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    logger.warn(`[claude-mem] Worker POST ${path} failed: ${message}`);
    return null;
  }
}

/**
 * Fire-and-forget POST (no waiting for response)
 */
function workerPostFireAndForget(
  port: number,
  path: string,
  body: Record<string, unknown>,
  logger: PluginLogger
): void {
  fetch(`${workerBaseUrl(port)}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }).catch((error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    logger.warn(`[claude-mem] Worker POST ${path} failed: ${message}`);
  });
}

/**
 * GET text from Java backend (with Circuit Breaker)
 */
async function workerGetText(
  port: number,
  path: string,
  logger: PluginLogger
): Promise<string | null> {
  // Circuit breaker: silently drop if open
  if (!circuitAllow(logger)) {
    logger.debug?.(`[claude-mem] Circuit breaker: dropping GET ${path}`);
    return null;
  }

  try {
    const response = await fetch(`${workerBaseUrl(port)}${path}`);
    if (!response.ok) {
      logger.warn(`[claude-mem] Worker GET ${path} returned ${response.status}`);
      circuitRecordFailure();
      return null;
    }
    circuitRecordSuccess();
    return await response.text();
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    logger.warn(`[claude-mem] Worker GET ${path} failed: ${message}`);
    circuitRecordFailure();
    return null;
  }
}

// ============================================================================
// Plugin Entry Point
// ============================================================================

export default function claudeMemJavaPlugin(api: OpenClawPluginApi): void {
  const userConfig = (api.pluginConfig || {}) as ClaudeMemJavaPluginConfig;
  const workerPort = userConfig.workerPort || DEFAULT_WORKER_PORT;
  const projectName = userConfig.project || "openclaw";

  // ------------------------------------------------------------------
  // Session tracking
  // ------------------------------------------------------------------
  const sessionIds = new Map<string, string>();
  const workspaceDirsBySessionKey = new Map<string, string>();

  /**
   * Get or create content session ID for OpenClaw session
   */
  function getContentSessionId(sessionKey?: string): string {
    const key = sessionKey || "default";
    if (!sessionIds.has(key)) {
      sessionIds.set(key, `openclaw-${key}-${Date.now()}`);
    }
    return sessionIds.get(key)!;
  }

  // ------------------------------------------------------------------
  // Event: session_start — init claude-mem session
  // Java API: POST /api/session/start
  // ------------------------------------------------------------------
  api.on("session_start", async (_event, ctx) => {
    const contentSessionId = getContentSessionId(ctx.sessionKey);

    // Java backend uses /api/session/start (not /api/sessions/init)
    await workerPost(workerPort, "/api/session/start", {
      session_id: contentSessionId,
      project_path: projectName,
      cwd: ctx.workspaceDir || "",
    }, api.logger);

    api.logger.info(`[claude-mem] Session initialized: ${contentSessionId}`);
  });

  // ------------------------------------------------------------------
  // Event: after_compaction — re-init session after context compaction
  // Java API: POST /api/session/start
  // ------------------------------------------------------------------
  api.on("after_compaction", async (_event, ctx) => {
    const contentSessionId = getContentSessionId(ctx.sessionKey);

    // Java backend uses /api/session/start
    await workerPost(workerPort, "/api/session/start", {
      session_id: contentSessionId,
      project_path: projectName,
      cwd: ctx.workspaceDir || "",
    }, api.logger);

    api.logger.info(`[claude-mem] Session re-initialized after compaction: ${contentSessionId}`);
  });

  // ------------------------------------------------------------------
  // Event: before_prompt_build — inject memory context via appendSystemContext
  // Java API: GET /api/context/inject
  // Uses 60s TTL context cache to reduce redundant API calls
  // ------------------------------------------------------------------
  api.on("before_prompt_build", async (_event, ctx) => {
    const projectPath = ctx.workspaceDir || projectName;
    const cacheKey = projectPath;

    // Check context cache first (60s TTL)
    const cached = getCachedContext(cacheKey);
    if (cached !== null) {
      api.logger.debug?.(`[claude-mem] Context cache hit for ${projectPath}`);
      if (cached.trim().length > 0) {
        return { appendSystemContext: cached };
      }
      return;
    }

    const contextText = await workerGetText(
      workerPort,
      `/api/context/inject?projects=${encodeURIComponent(projectPath)}`,
      api.logger
    );

    if (contextText && contextText.trim().length > 0) {
      try {
        // Java backend returns JSON: {context: "...", updateFiles: [...]}
        const data = JSON.parse(contextText);
        const context = data.context || "";

        if (context.trim().length > 0) {
          // Cache the context for 60s
          setCachedContext(cacheKey, context);
          api.logger.info(`[claude-mem] Context injected (${context.length} chars) for ${projectPath}`);
          return { appendSystemContext: context };
        }
      } catch {
        // If JSON parsing fails, return raw text and cache it
        setCachedContext(cacheKey, contextText);
        api.logger.info(`[claude-mem] Context injected (raw, ${contextText.length} chars) for ${projectPath}`);
        return { appendSystemContext: contextText };
      }
    }
    return;
  });

  // ------------------------------------------------------------------
  // Event: before_agent_start — track workspace dir for session
  // ------------------------------------------------------------------
  api.on("before_agent_start", async (_event, ctx) => {
    // Track workspace dir for session
    if (ctx.workspaceDir) {
      workspaceDirsBySessionKey.set(ctx.sessionKey || "default", ctx.workspaceDir);
    }
  });

  // ------------------------------------------------------------------
  // Event: tool_result_persist — record tool observations
  // Java API: POST /api/ingest/tool-use
  // Note: No longer syncing MEMORY.md — context is injected via before_prompt_build
  // ------------------------------------------------------------------
  api.on("tool_result_persist", (event, ctx) => {
    const toolName = event.toolName;
    if (!toolName || toolName.startsWith("memory_")) return;

    const contentSessionId = getContentSessionId(ctx.sessionKey);

    // Extract result text from message content
    let toolResponseText = "";
    const content = event.message?.content;
    if (Array.isArray(content)) {
      const textBlock = content.find(
        (block) => block.type === "tool_result" || block.type === "text"
      );
      if (textBlock && "text" in textBlock) {
        toolResponseText = String(textBlock.text).slice(0, TOOL_RESULT_MAX_LENGTH);
      }
    }

    // Java backend uses /api/ingest/tool-use (not /api/sessions/observations)
    // tool_input must be JSON string (not object) for Java backend
    workerPostFireAndForget(workerPort, "/api/ingest/tool-use", {
      session_id: contentSessionId,
      tool_name: toolName,
      tool_input: JSON.stringify(event.params || {}),
      tool_response: toolResponseText,
      cwd: ctx.workspaceDir || "",
    }, api.logger);
  });

  // ------------------------------------------------------------------
  // Event: agent_end — summarize and complete session
  // Java API: POST /api/ingest/session-end (combines summarize + complete)
  // ------------------------------------------------------------------
  api.on("agent_end", async (event, ctx) => {
    const contentSessionId = getContentSessionId(ctx.sessionKey);

    // Extract last assistant message for summarization
    let lastAssistantMessage = "";
    if (Array.isArray(event.messages)) {
      for (let i = event.messages.length - 1; i >= 0; i--) {
        const message = event.messages[i];
        if (message?.role === "assistant") {
          if (typeof message.content === "string") {
            lastAssistantMessage = message.content;
          } else if (Array.isArray(message.content)) {
            lastAssistantMessage = message.content
              .filter((block) => block.type === "text")
              .map((block) => block.text || "")
              .join("\n");
          }
          break;
        }
      }
    }

    // Java backend uses /api/ingest/session-end (combines summarize + complete)
    workerPostFireAndForget(workerPort, "/api/ingest/session-end", {
      session_id: contentSessionId,
      last_assistant_message: lastAssistantMessage,
    }, api.logger);
  });

  // ------------------------------------------------------------------
  // Event: session_end — clean up session tracking
  // ------------------------------------------------------------------
  api.on("session_end", async (_event, ctx) => {
    const key = ctx.sessionKey || "default";
    sessionIds.delete(key);
    workspaceDirsBySessionKey.delete(key);
  });

  // ------------------------------------------------------------------
  // Event: gateway_start — clear session tracking for fresh start
  // Also reset circuit breaker and context cache
  // ------------------------------------------------------------------
  api.on("gateway_start", async () => {
    workspaceDirsBySessionKey.clear();
    sessionIds.clear();
    circuitReset();
    contextCache.clear();
    api.logger.info("[claude-mem] Gateway started — session tracking, circuit breaker, and context cache reset");
  });

  // ------------------------------------------------------------------
  // Note: No SSE service registration
  // Java Thin Proxy architecture doesn't support SSE (maintains CLI simplicity)
  // Users can still view observations via WebUI or MCP tools
  // ------------------------------------------------------------------

  // ------------------------------------------------------------------
  // Command: /claude-mem-status — worker health check
  // ------------------------------------------------------------------
  api.registerCommand({
    name: "claude-mem-status",
    description: "Check Claude-Mem Java backend health and session status",
    handler: async () => {
      const healthText = await workerGetText(workerPort, "/actuator/health", api.logger);
      if (!healthText) {
        return `Claude-Mem Java backend unreachable at port ${workerPort}`;
      }

      try {
        const health = JSON.parse(healthText);
        return [
          "Claude-Mem Java Backend Status",
          `Status: ${health.status || "unknown"}`,
          `Port: ${workerPort}`,
          `Active sessions: ${sessionIds.size}`,
        ].join("\n");
      } catch {
        return `Claude-Mem Java backend responded but returned unexpected data`;
      }
    },
  });

  // ------------------------------------------------------------------
  // Command: /claude-mem-projects — list tracked projects
  // ------------------------------------------------------------------
  api.registerCommand({
    name: "claude-mem-projects",
    description: "List all projects tracked by Claude-Mem",
    handler: async () => {
      const projectsText = await workerGetText(workerPort, "/api/projects", api.logger);
      if (!projectsText) {
        return `Failed to fetch projects from Claude-Mem Java backend`;
      }

      try {
        const data = JSON.parse(projectsText);
        if (data.projects && Array.isArray(data.projects)) {
          return [
            "Claude-Mem Projects",
            ...data.projects.map((p: string) => `  - ${p}`),
          ].join("\n");
        }
        return `Projects: ${projectsText}`;
      } catch {
        return `Projects: ${projectsText}`;
      }
    },
  });

  // ------------------------------------------------------------------
  // Helper: parse limit parameter with bounds
  // ------------------------------------------------------------------
  function parseLimit(value: string, defaultValue: number): number {
    const parsed = parseInt(value, 10);
    if (isNaN(parsed) || parsed < 1) return defaultValue;
    return Math.min(parsed, 50); // Cap at 50 to prevent abuse
  }

  // ------------------------------------------------------------------
  // Command: /claude-mem-search — query worker search API
  // Usage: /claude-mem-search <query> [limit]
  // Note: limit is only recognized if it's a separate trailing argument (e.g., "test query 10")
  // ------------------------------------------------------------------
  api.registerCommand({
    name: "claude-mem-search",
    description: "Search Claude-Mem observations by query",
    acceptsArgs: true,
    handler: async (ctx) => {
      const raw = ctx.args?.trim() || "";
      if (!raw) {
        return "Usage: /claude-mem-search <query> [limit]";
      }

      // Parse: split by whitespace, but only treat last arg as limit if it's a single digit
      // and there are other arguments before it (to avoid "test123" being parsed as limit=123)
      const pieces = raw.split(/\s+/).filter(p => p.length > 0);
      let limit = 10;
      let query = raw;

      if (pieces.length >= 2) {
        const lastPiece = pieces[pieces.length - 1];
        // Only recognize as limit if it's a single or double digit number (1-50 range)
        if (/^\d{1,2}$/.test(lastPiece)) {
          limit = parseLimit(lastPiece, 10);
          query = pieces.slice(0, -1).join(" ");
        }
      }

      // Java backend uses /api/search with project and query params
      const searchText = await workerGetText(
        workerPort,
        `/api/search?project=${encodeURIComponent(projectName)}&query=${encodeURIComponent(query)}&limit=${limit}`,
        api.logger
      );

      if (!searchText) {
        return `Search failed or no results for: ${query}`;
      }

      try {
        const data = JSON.parse(searchText);
        if (data.observations && Array.isArray(data.observations)) {
          if (data.observations.length === 0) {
            return `No observations found for: ${query}`;
          }
          const lines = [`Search results for: ${query}`, `Found ${data.observations.length} observation(s):`];
          data.observations.forEach((obs: any, i: number) => {
            const preview = obs.content?.slice(0, 100) || "(no content)";
            lines.push(`${i + 1}. ${preview}${obs.content?.length > 100 ? "..." : ""}`);
          });
          return lines.join("\n");
        }
        return `Unexpected response format: ${searchText}`;
      } catch {
        return `Failed to parse search results for: ${query}`;
      }
    },
  });

  // ------------------------------------------------------------------
  // Command: /claude-mem-recent — recent context snapshot
  // Usage: /claude-mem-recent [project] [limit]
  // Note: limit is only recognized if it's a separate trailing argument
  // ------------------------------------------------------------------
  api.registerCommand({
    name: "claude-mem-recent",
    description: "Show recent Claude-Mem context for a project",
    acceptsArgs: true,
    handler: async (ctx) => {
      const raw = ctx.args?.trim() || "";
      const parts = raw.split(/\s+/).filter(p => p.length > 0);
      let limit = 3;
      let project = projectName;

      if (parts.length === 1) {
        // Single argument - could be project or limit
        if (/^\d{1,2}$/.test(parts[0])) {
          limit = parseLimit(parts[0], 3);
        } else {
          project = parts[0];
        }
      } else if (parts.length >= 2) {
        // Two+ arguments - last one is limit (if numeric), rest is project
        const lastPiece = parts[parts.length - 1];
        if (/^\d{1,2}$/.test(lastPiece)) {
          limit = parseLimit(lastPiece, 3);
          project = parts.slice(0, -1).join(" ");
        } else {
          project = parts.join(" ");
        }
      }

      // Java backend uses /api/context/recent with project and limit params
      const recentText = await workerGetText(
        workerPort,
        `/api/context/recent?project=${encodeURIComponent(project)}&limit=${limit}`,
        api.logger
      );

      if (!recentText) {
        return `Failed to fetch recent context for project: ${project}`;
      }

      try {
        // Java /api/context/recent returns text directly
        if (recentText.trim().startsWith("#") || recentText.trim().startsWith("##")) {
          return recentText;
        }
        // Try parsing as JSON
        const data = JSON.parse(recentText);
        if (data.context) return data.context;
        return recentText;
      } catch {
        return recentText;
      }
    },
  });

  // ------------------------------------------------------------------
  // Command: /claude-mem-timeline — search and timeline around best match
  // Usage: /claude-mem-timeline <query> [depthBefore] [depthAfter]
  //
  // Note: Java Timeline API only supports anchor (UUID) parameter,
  // not query parameter like TS version. So we first search for the
  // best match, then use that ID as the anchor.
  // ------------------------------------------------------------------
  api.registerCommand({
    name: "claude-mem-timeline",
    description: "Find best memory match and show nearby timeline events",
    acceptsArgs: true,
    handler: async (ctx) => {
      const raw = ctx.args?.trim() || "";
      if (!raw) {
        return "Usage: /claude-mem-timeline <query> [depthBefore] [depthAfter]";
      }

      const parts = raw.split(/\s+/).filter(p => p.length > 0);
      let depthAfter = 5;
      let depthBefore = 5;

      // Parse depthAfter from the last numeric argument (1-2 digits only)
      if (parts.length >= 2 && /^\d{1,2}$/.test(parts[parts.length - 1])) {
        depthAfter = parseLimit(parts.pop()!, 5);
      }
      // Parse depthBefore from what is now the last numeric argument (1-2 digits only)
      if (parts.length >= 2 && /^\d{1,2}$/.test(parts[parts.length - 1])) {
        depthBefore = parseLimit(parts.pop()!, 5);
      }

      const query = parts.join(" ");

      // Step 1: Search for best match to get anchor ID
      const searchText = await workerGetText(
        workerPort,
        `/api/search?project=${encodeURIComponent(projectName)}&query=${encodeURIComponent(query)}&limit=1`,
        api.logger
      );

      if (!searchText) {
        return `No observations found for query: ${query}`;
      }

      let anchorId: string | null = null;
      try {
        const searchResult = JSON.parse(searchText);
        anchorId = searchResult.observations?.[0]?.id ?? null;
      } catch {
        return `Failed to parse search result for: ${query}`;
      }

      if (!anchorId) {
        return `No observations found for query: ${query}`;
      }

      // Step 2: Get timeline around anchor ID
      const timelineText = await workerGetText(
        workerPort,
        `/api/context/timeline?project=${encodeURIComponent(projectName)}&anchor=${encodeURIComponent(anchorId)}&depth_before=${depthBefore}&depth_after=${depthAfter}`,
        api.logger
      );

      if (!timelineText) {
        return `Failed to fetch timeline for anchor: ${anchorId}`;
      }

      try {
        // Java /api/context/timeline may return JSON or text
        const data = JSON.parse(timelineText);
        if (data.context) return data.context;
        if (data.timeline) return data.timeline;
        return timelineText;
      } catch {
        return timelineText;
      }
    },
  });

  api.logger.info(`[claude-mem] OpenClaw Java Plugin loaded — v2.0.0 (backend: 127.0.0.1:${workerPort})`);
}
