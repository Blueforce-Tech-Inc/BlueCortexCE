import assert from "node:assert/strict";
import test from "node:test";
import pluginModule from "../dist/index.js";

const plugin = pluginModule.default;

function load(hooks, logger) {
  plugin({
    id: "ctx", name: "ctx", source: "ctx", config: {},
    pluginConfig: { workerPort: 37779 },
    logger,
    on(name, callback) { hooks.set(name, callback); },
    registerCommand() {},
    registerService() {},
    runtime: { channel: {} },
  });
}

function withFetch(t, body) {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });
  globalThis.fetch = async (input) => {
    assert.match(String(input), /\/api\/context\/inject/);
    return new Response(body, { status: 200, headers: { "Content-Type": "application/json" } });
  };
}

test("backend error placeholder is never injected into the prompt", async (t) => {
  withFetch(t, JSON.stringify({
    context: "[Error generating context for /project]",
    updateFiles: [],
  }));
  const hooks = new Map();
  load(hooks, { info() {}, warn() {}, error() {}, debug() {} });

  const injected = await hooks.get("before_prompt_build")({}, {
    sessionKey: "k", workspaceDir: "/project",
  });

  assert.equal(injected, undefined);
});

test("raw non-JSON error placeholder is also dropped", async (t) => {
  withFetch(t, "[Error generating context for /project]");
  const hooks = new Map();
  load(hooks, { info() {}, warn() {}, error() {}, debug() {} });

  const injected = await hooks.get("before_prompt_build")({}, {
    sessionKey: "k", workspaceDir: "/project",
  });

  assert.equal(injected, undefined);
});

test("real context is still injected and cached", async (t) => {
  let calls = 0;
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });
  globalThis.fetch = async () => {
    calls += 1;
    return new Response(JSON.stringify({ context: "# project context\n50 observations" }),
      { status: 200, headers: { "Content-Type": "application/json" } });
  };
  const hooks = new Map();
  load(hooks, { info() {}, warn() {}, error() {}, debug() {} });

  const first = await hooks.get("before_prompt_build")({}, {
    sessionKey: "k", workspaceDir: "/project",
  });
  assert.equal(first.appendSystemContext, "# project context\n50 observations");

  const second = await hooks.get("before_prompt_build")({}, {
    sessionKey: "k", workspaceDir: "/project",
  });
  assert.equal(second.appendSystemContext, "# project context\n50 observations");
  assert.equal(calls, 1, "second call should hit the 60s cache");
});
