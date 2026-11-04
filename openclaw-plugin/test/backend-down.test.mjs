import assert from "node:assert/strict";
import test from "node:test";
import pluginModule from "../dist/index.js";

const plugin = pluginModule.default;
const PORT = 37779;

function load(hooks, commands, logger) {
  plugin({
    id: "down", name: "down", source: "down", config: {},
    pluginConfig: { workerPort: PORT, project: "/project" },
    logger,
    on(name, callback) { hooks.set(name, callback); },
    registerCommand(definition) { commands.set(definition.name, definition); },
    registerService() {},
    runtime: { channel: {} },
  });
  return { hooks, commands };
}

function withDeadBackend(t) {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });
  globalThis.fetch = async () => {
    throw new TypeError("fetch failed");
  };
}

test("status and search report the dead backend as readable text", async (t) => {
  withDeadBackend(t);
  const logs = { info: [], warn: [], error: [], debug: [] };
  const logger = {
    info: (m) => logs.info.push(String(m)),
    warn: (m) => logs.warn.push(String(m)),
    error: (m) => logs.error.push(String(m)),
    debug: (m) => logs.debug.push(String(m)),
  };
  const { commands } = load(new Map(), new Map(), logger);

  const status = await commands.get("claude-mem-status").handler({});
  assert.match(status, /unreachable/);

  const search = await commands.get("claude-mem-search").handler({ args: "anything" });
  assert.match(search, /^Search failed/);
  assert.ok(logs.warn.length >= 2, "both failed GETs should log a warning");
});

test("context injection stays silent instead of throwing when the backend is down", async (t) => {
  withDeadBackend(t);
  const logs = { info: [], warn: [], error: [], debug: [] };
  const logger = {
    info: (m) => logs.info.push(String(m)),
    warn: (m) => logs.warn.push(String(m)),
    error: (m) => logs.error.push(String(m)),
    debug: (m) => logs.debug.push(String(m)),
  };
  const { hooks } = load(new Map(), new Map(), logger);

  const injected = await hooks.get("before_prompt_build")({}, {
    sessionKey: "down-key", workspaceDir: "/project-down",
  });

  assert.equal(injected, undefined);
  assert.ok(logs.warn.length > 0, "the host should see a warning");
});
