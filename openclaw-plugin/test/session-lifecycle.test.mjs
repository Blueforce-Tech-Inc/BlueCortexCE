import assert from "node:assert/strict";
import test from "node:test";
import pluginModule from "../dist/index.js";

const claudeMemJavaPlugin = pluginModule.default;

test("tool observations reach the backend before SessionEnd and agent_end is non-terminal", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  const hooks = new Map();
  const requests = [];
  let releaseToolRequest;
  let signalToolRequestStarted;
  const toolRequestStarted = new Promise((resolve) => {
    signalToolRequestStarted = resolve;
  });

  globalThis.fetch = async (input, init) => {
    const url = new URL(String(input));
    const body = JSON.parse(String(init?.body || "{}"));
    requests.push({ path: url.pathname, body });
    if (url.pathname === "/api/ingest/tool-use") {
      signalToolRequestStarted();
      await new Promise((resolve) => {
        releaseToolRequest = resolve;
      });
    }
    return new Response(JSON.stringify({ status: "ok" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  const logger = { info() {}, warn() {}, error() {}, debug() {} };
  claudeMemJavaPlugin({
    id: "test",
    name: "test",
    source: "test",
    config: {},
    pluginConfig: { workerPort: 37779 },
    logger,
    on(name, callback) {
      hooks.set(name, callback);
    },
    registerCommand() {},
    registerService() {},
    runtime: { channel: {} },
  });

  const context = { sessionKey: "key-1", workspaceDir: "/project" };
  await hooks.get("session_start")({ sessionId: "host-session-1" }, context);
  await hooks.get("agent_end")({
    messages: [{ role: "assistant", content: "final answer" }],
  }, context);
  assert.equal(requests.some(({ path }) => path === "/api/ingest/session-end"), false);

  const toolWrite = hooks.get("tool_result_persist")({
    toolName: "Read",
    params: { file: "README.md" },
    message: { content: [{ type: "tool_result", text: "read" }] },
  }, context);
  await toolRequestStarted;

  const sessionEnd = hooks.get("session_end")({ sessionId: "host-session-1", messageCount: 2 }, context);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(requests.some(({ path }) => path === "/api/ingest/session-end"), false);

  releaseToolRequest();
  await Promise.all([toolWrite, sessionEnd]);

  const toolIndex = requests.findIndex(({ path }) => path === "/api/ingest/tool-use");
  const endIndex = requests.findIndex(({ path }) => path === "/api/ingest/session-end");
  assert.ok(toolIndex >= 0 && endIndex > toolIndex);
  assert.equal(requests[toolIndex].body.cwd, "/project");
  assert.equal(requests[toolIndex].body.platform_source, "openclaw");
  assert.equal(requests[endIndex].body.session_id, "openclaw-host-session-1");
  assert.equal(requests[endIndex].body.last_assistant_message, "final answer");
});

test("resumed OpenClaw sessions keep their prior identity across gateway restart", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  const hooks = new Map();
  const requests = [];
  globalThis.fetch = async (input, init) => {
    const url = new URL(String(input));
    requests.push({ path: url.pathname, body: JSON.parse(String(init?.body || "{}")) });
    return new Response(JSON.stringify({ status: "ok" }), { status: 200 });
  };

  claudeMemJavaPlugin({
    id: "test",
    name: "test",
    source: "test",
    config: {},
    pluginConfig: { workerPort: 37779 },
    logger: { info() {}, warn() {}, error() {}, debug() {} },
    on(name, callback) { hooks.set(name, callback); },
    registerCommand() {},
    registerService() {},
    runtime: { channel: {} },
  });

  const resumedEvent = { sessionId: "host-session-resumed", resumedFrom: "host-session-original" };
  const context = { sessionKey: "key-resumed", workspaceDir: "/project" };
  await hooks.get("session_start")(resumedEvent, context);
  await hooks.get("after_compaction")({ messageCount: 5, compactedCount: 2 }, context);
  await hooks.get("gateway_start")({});
  await hooks.get("session_start")(resumedEvent, context);
  await hooks.get("session_end")({ sessionId: "host-session-resumed", messageCount: 6 }, context);

  const lifecycleIds = requests
    .filter(({ path }) => path === "/api/session/start" || path === "/api/ingest/session-end")
    .map(({ body }) => body.session_id);
  assert.deepEqual(lifecycleIds, [
    "openclaw-host-session-original",
    "openclaw-host-session-original",
    "openclaw-host-session-original",
    "openclaw-host-session-original",
  ]);

  await hooks.get("session_start")({ sessionId: "host-session-new" }, {
    sessionKey: "key-new",
    workspaceDir: "/project",
  });
  assert.equal(requests.at(-1).body.session_id, "openclaw-host-session-new");
});
