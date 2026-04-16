// Codex Installer
// Manages installation/uninstallation of Codex CLI transcript watching.

import { readFile, writeFile, mkdir } from "fs/promises";
import { join } from "path";
import { homedir } from "os";
import { existsSync } from "fs";

const CONFIG_DIR = join(homedir(), ".claude-mem");
const CONFIG_FILE = join(CONFIG_DIR, "transcript-watch.json");
const STATE_FILE = join(CONFIG_DIR, "transcript-watch-state.json");

interface WatchEntry {
  name: string;
  path?: string;
  schema?: string;
  startAtEnd?: boolean;
  [key: string]: unknown;
}

interface TranscriptConfig {
  version: number;
  schemas?: Record<string, unknown>;
  watches?: WatchEntry[];
  stateFile?: string;
}

const CODEX_CONFIG_SECTION: TranscriptConfig = {
  version: 1,
  schemas: {
    codex: {
      name: "codex",
      version: "0.2",
      description: "Schema for Codex session JSONL files under ~/.codex/sessions.",
      events: [
        { name: "session-meta", match: { path: "type", equals: "session_meta" }, action: "session_context", fields: { sessionId: "payload.id", cwd: "payload.cwd" } },
        { name: "turn-context", match: { path: "type", equals: "turn_context" }, action: "session_context", fields: { cwd: "payload.cwd" } },
        { name: "user-message", match: { path: "payload.type", equals: "user_message" }, action: "session_init", fields: { prompt: "payload.message" } },
        { name: "assistant-message", match: { path: "payload.type", equals: "agent_message" }, action: "assistant_message", fields: { message: "payload.message" } },
        { name: "tool-use", match: { path: "payload.type", in: ["function_call", "custom_tool_call", "web_search_call"] }, action: "tool_use", fields: { toolId: "payload.call_id", toolName: { coalesce: ["payload.name", { value: "web_search" }] }, toolInput: { coalesce: ["payload.arguments", "payload.input", "payload.action"] } } },
        { name: "tool-result", match: { path: "payload.type", in: ["function_call_output", "custom_tool_call_output"] }, action: "tool_result", fields: { toolId: "payload.call_id", toolResponse: "payload.output" } },
        { name: "session-end", match: { path: "payload.type", equals: "turn_aborted" }, action: "session_end" }
      ]
    }
  },
  watches: [
    { name: "codex", path: "~/.codex/sessions/**/*.jsonl", schema: "codex", startAtEnd: true }
  ],
  stateFile: STATE_FILE
};

export class CodexInstaller {
  async isInstalled(): Promise<boolean> {
    if (!existsSync(CONFIG_FILE)) {
      return false;
    }
    try {
      const content = await readFile(CONFIG_FILE, "utf-8");
      const config: TranscriptConfig = JSON.parse(content);
      if (!config.watches) return false;
      return config.watches.some(w => w.name === "codex");
    } catch {
      return false;
    }
  }

  async install(): Promise<boolean> {
    try {
      if (!existsSync(CONFIG_DIR)) {
        await mkdir(CONFIG_DIR, { recursive: true });
      }

      let config: TranscriptConfig;
      if (existsSync(CONFIG_FILE)) {
        try {
          const content = await readFile(CONFIG_FILE, "utf-8");
          config = JSON.parse(content);
        } catch {
          const backupPath = CONFIG_FILE + ".backup." + Date.now();
          await writeFile(backupPath, await readFile(CONFIG_FILE));
          console.warn("[codex-watcher] Backed up corrupt config to " + backupPath);
          config = { version: 1, watches: [] };
        }
      } else {
        config = { version: 1, watches: [] };
      }

      if (!config.schemas) config.schemas = {};
      if (!config.watches) config.watches = [];

      if (config.schemas.codex) {
        console.log("[codex-watcher] Codex schema already exists, skipping");
      } else {
        config.schemas.codex = CODEX_CONFIG_SECTION.schemas!.codex;
      }

      const existingWatch = config.watches!.find(w => w.name === "codex");
      if (existingWatch) {
        console.log("[codex-watcher] Codex watch already configured, skipping");
      } else {
        config.watches.push(CODEX_CONFIG_SECTION.watches![0]);
      }

      if (!config.stateFile) {
        config.stateFile = STATE_FILE;
      }

      await writeFile(CONFIG_FILE, JSON.stringify(config, null, 2), "utf-8");
      console.log("[codex-watcher] Installation complete");
      console.log("[codex-watcher] Config: " + CONFIG_FILE);
      return true;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error("[codex-watcher] Installation failed: " + message);
      return false;
    }
  }

  async uninstall(): Promise<boolean> {
    try {
      if (!existsSync(CONFIG_FILE)) {
        console.log("[codex-watcher] No config file found, nothing to uninstall");
        return true;
      }

      const content = await readFile(CONFIG_FILE, "utf-8");
      const config: TranscriptConfig = JSON.parse(content);

      if (!config.watches || !Array.isArray(config.watches)) {
        console.log("[codex-watcher] No watches configured, nothing to uninstall");
        return true;
      }

      const initialLength = config.watches.length;
      config.watches = config.watches.filter(w => w.name !== "codex");

      if (config.watches.length === initialLength) {
        console.log("[codex-watcher] Codex watch not found in config");
        return true;
      }

      await writeFile(CONFIG_FILE, JSON.stringify(config, null, 2), "utf-8");
      console.log("[codex-watcher] Uninstallation complete");
      return true;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      console.error("[codex-watcher] Uninstallation failed: " + message);
      return false;
    }
  }

  async getStatus(): Promise<{ installed: boolean; configPath: string; watching: boolean }> {
    const installed = await this.isInstalled();
    return {
      installed,
      configPath: CONFIG_FILE,
      watching: installed
    };
  }
}
