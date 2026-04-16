/**
 * Java Backend API Client
 *
 * Communicates with the Java Spring Boot backend via HTTP API.
 * Uses the same endpoints as the OpenClaw plugin.
 */

import type { ParsedCodexEvent } from "./events.js";

// ============================================================================
// Configuration
// ============================================================================

interface ApiConfig {
  baseUrl: string;
  projectName: string;
}

// ============================================================================
// API Client
// ============================================================================

export class ApiClient {
  private baseUrl: string;
  private projectName: string;

  constructor(config: ApiConfig) {
    this.baseUrl = config.baseUrl.replace(/\/$/, ""); // Remove trailing slash
    this.projectName = config.projectName;
  }

  /**
   * POST to Java backend API
   */
  async post<T = unknown>(
    path: string,
    body: Record<string, unknown>
  ): Promise<T | null> {
    try {
      const response = await fetch(`${this.baseUrl}${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        console.warn(`[codex-watcher] API POST ${path} returned ${response.status}`);
        return null;
      }
      const text = await response.text();
      if (!text) return null;
      try {
        return JSON.parse(text) as T;
      } catch {
        return null;
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.warn(`[codex-watcher] API POST ${path} failed: ${message}`);
      return null;
    }
  }

  /**
   * Initialize a Codex session
   */
  async sessionStart(sessionId: string, cwd: string): Promise<boolean> {
    const result = await this.post<{ success?: boolean }>("/api/session/start", {
      session_id: sessionId,
      project_path: this.projectName,
      cwd: cwd || "",
    });
    return result?.success !== false;
  }

  /**
   * Record a tool use observation
   */
  async recordToolUse(
    sessionId: string,
    toolName: string,
    toolInput: string,
    toolResponse: string,
    cwd: string
  ): Promise<void> {
    await this.post("/api/ingest/tool-use", {
      session_id: sessionId,
      tool_name: toolName,
      tool_input: toolInput,
      tool_response: toolResponse,
      cwd: cwd || "",
    });
  }

  /**
   * Complete a session
   */
  async sessionEnd(sessionId: string, lastAssistantMessage?: string): Promise<void> {
    await this.post("/api/ingest/session-end", {
      session_id: sessionId,
      last_assistant_message: lastAssistantMessage || "",
    });
  }

  /**
   * Check if the backend is healthy
   */
  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(`${this.baseUrl}/actuator/health`);
      if (!response.ok) return false;
      const data = (await response.json()) as { status?: string };
      return data.status === "UP";
    } catch {
      return false;
    }
  }

  /**
   * Process a parsed Codex event
   */
  async processEvent(
    event: ParsedCodexEvent,
    sessionId: string
  ): Promise<void> {
    switch (event.action) {
      case "session_context":
        // Initialize session with context
        if (event.cwd) {
          await this.sessionStart(sessionId, event.cwd);
        }
        break;

      case "session_init":
        // User message received - initialize session
        await this.sessionStart(sessionId, event.cwd || "");
        break;

      case "tool_use":
        // Tool call - record observation
        await this.recordToolUse(
          sessionId,
          event.toolName || "unknown",
          event.toolInput || "",
          event.toolResponse || "",
          event.cwd || ""
        );
        break;

      case "tool_result":
        // Tool result - combine with previous tool_use if we have matching toolId
        // For simplicity, we don't do correlation here; the Java backend handles it
        break;

      case "session_end":
        // Session ended
        await this.sessionEnd(sessionId);
        break;

      case "assistant_message":
        // Log only, don't record as observation
        break;
    }
  }
}
