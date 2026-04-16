/**
 * Codex Watcher - Claude-Mem Java Backend
 *
 * Monitors Codex CLI session JSONL files and records observations
 * to the Java backend via HTTP API.
 *
 * Usage:
 *   node dist/index.js          Start watching
 *   node dist/index.js install  Install Codex watcher config
 *   node dist/index.js uninstall - Remove Codex watcher config
 *   node dist/index.js status   Check installation status
 */

import { ApiClient } from "./api.js";
import { CodexWatcher } from "./watcher.js";
import { CodexInstaller } from "./installer.js";

// ============================================================================
// Configuration
// ============================================================================

const DEFAULT_BACKEND_URL = "http://127.0.0.1:37777";
const DEFAULT_PROJECT_NAME = "codex";

// ============================================================================
// Help
// ============================================================================

function showHelp(): void {
  console.log(`
Claude-Mem Codex Watcher

Monitors Codex CLI session files and records observations to the Java backend.

Usage:
  node dist/index.js          Start watching Codex sessions
  node dist/index.js install  Install Codex watcher configuration
  node dist/index.js uninstall Remove Codex watcher configuration
  node dist/index.js status   Check installation status
  node dist/index.js help     Show this help message

Environment Variables:
  CLAUDE_MEM_BACKEND_URL  Backend URL (default: ${DEFAULT_BACKEND_URL})
  CLAUDE_MEM_PROJECT      Project name (default: ${DEFAULT_PROJECT_NAME})

Examples:
  # Start watching with default settings
  node dist/index.js

  # Start watching with custom backend
  CLAUDE_MEM_BACKEND_URL=http://localhost:37779 node dist/index.js

  # Install then start
  node dist/index.js install && node dist/index.js
`);
}

// ============================================================================
// Main
// ============================================================================

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const command = args[0] || "start";

  // Get configuration from environment
  const backendUrl = process.env.CLAUDE_MEM_BACKEND_URL || DEFAULT_BACKEND_URL;
  const projectName = process.env.CLAUDE_MEM_PROJECT || DEFAULT_PROJECT_NAME;

  // Create API client
  const api = new ApiClient({
    baseUrl: backendUrl,
    projectName: projectName,
  });

  // Create installer for status/install/uninstall commands
  const installer = new CodexInstaller();

  switch (command) {
    case "help":
      showHelp();
      break;

    case "install":
      console.log("[codex-watcher] Installing Codex watcher...");
      const installSuccess = await installer.install();
      process.exit(installSuccess ? 0 : 1);
      break;

    case "uninstall":
      console.log("[codex-watcher] Uninstalling Codex watcher...");
      const uninstallSuccess = await installer.uninstall();
      process.exit(uninstallSuccess ? 0 : 1);
      break;

    case "status":
      const status = await installer.getStatus();
      console.log(`
Codex Watcher Status
====================
Installed: ${status.installed ? "Yes" : "No"}
Config: ${status.configPath}
Watching: ${status.watching ? "Yes" : "No"}
Backend: ${backendUrl}
Project: ${projectName}
`);
      break;

    case "start":
    default:
      await runWatcher(api);
      break;
  }
}

/**
 * Run the watcher
 */
async function runWatcher(api: ApiClient): Promise<void> {
  console.log("[codex-watcher] Starting Codex watcher...");
  console.log("[codex-watcher] Backend:", api instanceof ApiClient ? "configured" : "error");

  // Check backend health
  const healthy = await api.healthCheck();
  if (!healthy) {
    console.warn("[codex-watcher] Warning: Backend health check failed");
    console.warn("[codex-watcher] Make sure Java backend is running on the configured port");
  } else {
    console.log("[codex-watcher] Backend health check passed");
  }

  // Create and start watcher
  const watcher = new CodexWatcher(api);

  // Handle graceful shutdown
  const shutdown = async (): Promise<void> => {
    console.log("[codex-watcher] Shutting down...");
    await watcher.stop();
    process.exit(0);
  };

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);

  await watcher.start();

  console.log("[codex-watcher] Watching for Codex sessions...");
  console.log("[codex-watcher] Press Ctrl+C to stop");

  // Keep process running
  await new Promise(() => {});
}

// Run main
main().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`[codex-watcher] Fatal error: ${message}`);
  process.exit(1);
});
