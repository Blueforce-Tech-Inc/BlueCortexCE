// File Watcher for Codex Session JSONL Files
// Monitors ~/.codex/sessions/**/*.jsonl files for new events.

import { stat } from "fs/promises";
import { basename } from "path";
import { homedir } from "os";
import chokidar, { type FSWatcher } from "chokidar";
import type { ParsedCodexEvent } from "./events.js";
import { parseCodexEvent } from "./events.js";
import type { ApiClient } from "./api.js";

const CODEX_SESSIONS_GLOB = "~/.codex/sessions/**/*.jsonl";

interface WatchState {
  watcher: FSWatcher | null;
  filePositions: Map<string, number>;
  sessionIds: Map<string, string>;
  pendingToolCalls: Map<string, { toolName: string; toolInput: string; cwd: string }>;
}

export class CodexWatcher {
  private state: WatchState;
  private api: ApiClient;
  private pollInterval: ReturnType<typeof setInterval> | null = null;

  constructor(api: ApiClient) {
    this.api = api;
    this.state = {
      watcher: null,
      filePositions: new Map(),
      sessionIds: new Map(),
      pendingToolCalls: new Map(),
    };
  }

  async start(): Promise<void> {
    const globPath = CODEX_SESSIONS_GLOB.replace("~", homedir());
    console.log("[codex-watcher] Starting watcher on: " + globPath);

    this.state.watcher = chokidar.watch(globPath, {
      persistent: true,
      ignoreInitial: false,
      awaitWriteFinish: {
        stabilityThreshold: 500,
        pollInterval: 100,
      },
    });

    this.state.watcher.on("add", (filePath) => this.handleFileAdd(filePath));
    this.state.watcher.on("change", (filePath) => this.handleFileChange(filePath));
    this.state.watcher.on("error", (error) => {
      console.error("[codex-watcher] Watcher error: " + error.message);
    });

    this.pollInterval = setInterval(() => this.pollAllFiles(), 2000);

    console.log("[codex-watcher] Watcher started");
  }

  async stop(): Promise<void> {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }

    if (this.state.watcher) {
      await this.state.watcher.close();
      this.state.watcher = null;
    }

    this.state.filePositions.clear();
    this.state.sessionIds.clear();
    this.state.pendingToolCalls.clear();

    console.log("[codex-watcher] Watcher stopped");
  }

  private async handleFileAdd(filePath: string): Promise<void> {
    console.log("[codex-watcher] New file discovered: " + basename(filePath));
    await this.processFile(filePath, true);
  }

  private async handleFileChange(filePath: string): Promise<void> {
    await this.processFile(filePath, false);
  }

  private async pollAllFiles(): Promise<void> {
    for (const filePath of this.state.filePositions.keys()) {
      try {
        await this.processFile(filePath, false);
      } catch {
        this.state.filePositions.delete(filePath);
        this.state.sessionIds.delete(filePath);
      }
    }
  }

  private async processFile(filePath: string, startAtEnd: boolean): Promise<void> {
    try {
      const stats = await stat(filePath);
      const fileSize = stats.size;

      let startPos = 0;
      if (!startAtEnd && this.state.filePositions.has(filePath)) {
        startPos = this.state.filePositions.get(filePath)!;
        if (startPos >= fileSize) {
          return;
        }
      } else if (startAtEnd) {
        startPos = fileSize;
      }

      const newContent = await this.readFileRange(filePath, startPos, fileSize);
      if (!newContent) return;

      const lines = newContent.split("\n").filter((line) => line.trim());
      for (const line of lines) {
        await this.processLine(filePath, line);
      }

      this.state.filePositions.set(filePath, fileSize);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.warn("[codex-watcher] Error processing file " + filePath + ": " + message);
    }
  }

  private async readFileRange(filePath: string, start: number, end: number): Promise<string> {
    const { createReadStream } = await import("fs");

    return new Promise((resolve, reject) => {
      const chunks: Buffer[] = [];
      const stream = createReadStream(filePath, {
        start,
        end: end - 1,
        encoding: "utf-8",
      });

      stream.on("data", (chunk: string | Buffer) => {
        if (Buffer.isBuffer(chunk)) {
          chunks.push(chunk);
        } else {
          chunks.push(Buffer.from(chunk));
        }
      });
      stream.on("end", () => resolve(chunks.join("")));
      stream.on("error", reject);
    });
  }

  private async processLine(filePath: string, line: string): Promise<void> {
    const parsed = parseCodexEvent(line);
    if (!parsed) return;

    let sessionId = this.state.sessionIds.get(filePath);
    if (!sessionId) {
      sessionId = "codex-" + basename(filePath, ".jsonl") + "-" + Date.now();
      this.state.sessionIds.set(filePath, sessionId);
    }

    if (parsed.action === "session_context" || parsed.action === "session_init") {
      if (parsed.sessionId) {
        this.state.sessionIds.set(filePath, parsed.sessionId);
        sessionId = parsed.sessionId;
      }
    }

    if (parsed.action === "tool_use" && parsed.toolId) {
      this.state.pendingToolCalls.set(parsed.toolId, {
        toolName: parsed.toolName || "unknown",
        toolInput: parsed.toolInput || "",
        cwd: parsed.cwd || "",
      });
    }

    if (parsed.action === "tool_result" && parsed.toolId) {
      const pendingCall = this.state.pendingToolCalls.get(parsed.toolId);
      if (pendingCall) {
        await this.api.recordToolUse(
          sessionId,
          pendingCall.toolName,
          pendingCall.toolInput,
          parsed.toolResponse || "",
          pendingCall.cwd
        );
        this.state.pendingToolCalls.delete(parsed.toolId);
        // Don't fall through to processEvent - tool_result is handled here
        return;
      }
      // No matching pending call - fall through to processEvent for logging
    }

    await this.api.processEvent(parsed, sessionId);

    if (parsed.action === "session_end") {
      await this.api.sessionEnd(sessionId);
    }
  }
}
