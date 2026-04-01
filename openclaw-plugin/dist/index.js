"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = claudeMemJavaPlugin;
// ============================================================================
// Constants
// ============================================================================
const DEFAULT_WORKER_PORT = 37777;
const TOOL_RESULT_MAX_LENGTH = 1000;
// ============================================================================
// HTTP Client (Java Backend API)
// ============================================================================
function workerBaseUrl(port) {
    return `http://127.0.0.1:${port}`;
}
/**
 * POST to Java backend API
 * Adapted for Java endpoints:
 * - /api/session/start (instead of /api/sessions/init)
 * - /api/ingest/tool-use (instead of /api/sessions/observations)
 * - /api/ingest/session-end (instead of /api/sessions/complete)
 */
async function workerPost(port, path, body, logger) {
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
        return (await response.json());
    }
    catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        logger.warn(`[claude-mem] Worker POST ${path} failed: ${message}`);
        return null;
    }
}
/**
 * Fire-and-forget POST (no waiting for response)
 */
function workerPostFireAndForget(port, path, body, logger) {
    fetch(`${workerBaseUrl(port)}${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
    }).catch((error) => {
        const message = error instanceof Error ? error.message : String(error);
        logger.warn(`[claude-mem] Worker POST ${path} failed: ${message}`);
    });
}
/**
 * GET text from Java backend
 */
async function workerGetText(port, path, logger) {
    try {
        const response = await fetch(`${workerBaseUrl(port)}${path}`);
        if (!response.ok) {
            logger.warn(`[claude-mem] Worker GET ${path} returned ${response.status}`);
            return null;
        }
        return await response.text();
    }
    catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        logger.warn(`[claude-mem] Worker GET ${path} failed: ${message}`);
        return null;
    }
}
// ============================================================================
// Plugin Entry Point
// ============================================================================
function claudeMemJavaPlugin(api) {
    const userConfig = (api.pluginConfig || {});
    const workerPort = userConfig.workerPort || DEFAULT_WORKER_PORT;
    const projectName = userConfig.project || "openclaw";
    // ------------------------------------------------------------------
    // Session tracking
    // ------------------------------------------------------------------
    const sessionIds = new Map();
    const workspaceDirsBySessionKey = new Map();
    /**
     * Get or create content session ID for OpenClaw session
     */
    function getContentSessionId(sessionKey) {
        const key = sessionKey || "default";
        if (!sessionIds.has(key)) {
            sessionIds.set(key, `openclaw-${key}-${Date.now()}`);
        }
        return sessionIds.get(key);
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
    // This replaces the old MEMORY.md file sync approach
    // ------------------------------------------------------------------
    api.on("before_prompt_build", async (_event, ctx) => {
        const projectPath = ctx.workspaceDir || projectName;
        const contextText = await workerGetText(workerPort, `/api/context/inject?projects=${encodeURIComponent(projectPath)}`, api.logger);
        if (contextText && contextText.trim().length > 0) {
            try {
                // Java backend returns JSON: {context: "...", updateFiles: [...]}
                const data = JSON.parse(contextText);
                const context = data.context || "";
                if (context.trim().length > 0) {
                    api.logger.info(`[claude-mem] Context injected (${context.length} chars) for ${projectPath}`);
                    return { appendSystemContext: context };
                }
            }
            catch {
                // If JSON parsing fails, return raw text
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
        if (!toolName || toolName.startsWith("memory_"))
            return;
        const contentSessionId = getContentSessionId(ctx.sessionKey);
        // Extract result text from message content
        let toolResponseText = "";
        const content = event.message?.content;
        if (Array.isArray(content)) {
            const textBlock = content.find((block) => block.type === "tool_result" || block.type === "text");
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
                    }
                    else if (Array.isArray(message.content)) {
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
    // ------------------------------------------------------------------
    api.on("gateway_start", async () => {
        workspaceDirsBySessionKey.clear();
        sessionIds.clear();
        api.logger.info("[claude-mem] Gateway started — session tracking reset");
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
            }
            catch {
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
                        ...data.projects.map((p) => `  - ${p}`),
                    ].join("\n");
                }
                return `Projects: ${projectsText}`;
            }
            catch {
                return `Projects: ${projectsText}`;
            }
        },
    });
    api.logger.info(`[claude-mem] OpenClaw Java Plugin loaded — v1.0.0 (backend: 127.0.0.1:${workerPort})`);
}
//# sourceMappingURL=index.js.map