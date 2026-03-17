"use strict";
/**
 * Claude-Mem OpenClaw Plugin for Java Backend
 *
 * This plugin integrates Claude-Mem memory system with OpenClaw Gateway,
 * connecting to the Java Spring Boot backend instead of the TypeScript version.
 *
 * Key Differences from TypeScript Version:
 * - Uses Java backend API endpoints (adapted)
 * - No SSE support (Java Thin Proxy architecture)
 * - Simpler fire-and-forget pattern
 *
 * Architecture:
 * ```
 * OpenClaw Gateway
 * └── Claude-Mem Java Plugin (this)
 *     ├── HTTP Client → Java Backend (localhost:37777)
 *     ├── MEMORY.md Sync
 *     └── Observation Recording
 * ```
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = claudeMemJavaPlugin;
const promises_1 = require("fs/promises");
const path_1 = require("path");
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
    const syncMemoryFile = userConfig.syncMemoryFile !== false; // default true
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
    /**
     * Sync MEMORY.md to workspace directory
     * Uses Java backend /api/context/inject endpoint
     *
     * P0 Fix: Java backend expects absolute path, not project name!
     * Must pass workspaceDir (absolute path) instead of projectName
     */
    async function syncMemoryToWorkspace(workspaceDir) {
        // P0 Fix: Use workspaceDir as the project path (Java backend requires absolute path)
        const projectPath = workspaceDir || projectName;
        const contextText = await workerGetText(workerPort, `/api/context/inject?projects=${encodeURIComponent(projectPath)}`, api.logger);
        if (contextText && contextText.trim().length > 0) {
            try {
                // Java backend returns JSON: {context: "...", updateFiles: [...]}
                // Extract the context field only
                const data = JSON.parse(contextText);
                const memoryContent = data.context || "";
                if (memoryContent.trim().length > 0) {
                    await (0, promises_1.writeFile)((0, path_1.join)(workspaceDir, "MEMORY.md"), memoryContent, "utf-8");
                    api.logger.info(`[claude-mem] MEMORY.md synced to ${workspaceDir}`);
                }
            }
            catch (parseError) {
                // If JSON parsing fails, try writing as-is (backwards compatibility)
                const msg = parseError instanceof Error ? parseError.message : String(parseError);
                api.logger.warn(`[claude-mem] Failed to parse context JSON, writing raw: ${msg}`);
                try {
                    await (0, promises_1.writeFile)((0, path_1.join)(workspaceDir, "MEMORY.md"), contextText, "utf-8");
                    api.logger.info(`[claude-mem] MEMORY.md synced (raw) to ${workspaceDir}`);
                }
                catch (writeError) {
                    const wmsg = writeError instanceof Error ? writeError.message : String(writeError);
                    api.logger.warn(`[claude-mem] Failed to write MEMORY.md: ${wmsg}`);
                }
            }
        }
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
    // Event: before_agent_start — sync MEMORY.md + track workspace
    // ------------------------------------------------------------------
    api.on("before_agent_start", async (_event, ctx) => {
        // Track workspace dir so tool_result_persist can sync MEMORY.md later
        if (ctx.workspaceDir) {
            workspaceDirsBySessionKey.set(ctx.sessionKey || "default", ctx.workspaceDir);
        }
        // Sync MEMORY.md before agent runs
        if (syncMemoryFile && ctx.workspaceDir) {
            await syncMemoryToWorkspace(ctx.workspaceDir);
        }
    });
    // ------------------------------------------------------------------
    // Event: tool_result_persist — record tool observations + sync MEMORY.md
    // Java API: POST /api/ingest/tool-use
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
        // Sync MEMORY.md after tool use (fire-and-forget, don't block tool response)
        const workspaceDir = ctx.workspaceDir || workspaceDirsBySessionKey.get(ctx.sessionKey || "default");
        if (syncMemoryFile && workspaceDir) {
            syncMemoryToWorkspace(workspaceDir).catch((err) => {
                api.logger.warn(`[claude-mem] MEMORY.md sync failed: ${err}`);
            });
        }
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