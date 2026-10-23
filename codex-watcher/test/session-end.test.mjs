import assert from "node:assert/strict";
import test from "node:test";
import { ApiClient } from "../dist/api.js";
import { parseCodexEvent } from "../dist/events.js";

test("turn_aborted is sent once as a Codex SessionEnd event", async (t) => {
  const originalFetch = globalThis.fetch;
  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  const requests = [];
  globalThis.fetch = async (input, init) => {
    requests.push({
      url: new URL(String(input)),
      body: JSON.parse(String(init?.body || "{}")),
    });
    return new Response(null, { status: 204 });
  };

  const event = parseCodexEvent('{"type":"turn_aborted","payload":{}}');
  assert.equal(event?.action, "session_end");

  const api = new ApiClient({ baseUrl: "http://localhost:37777", projectName: "/project" });
  await api.processEvent(event, "codex-session-1");

  assert.equal(requests.length, 1);
  assert.equal(requests[0].url.pathname, "/api/ingest/session-end");
  assert.equal(requests[0].body.session_id, "codex-session-1");
  assert.equal(requests[0].body.platform_source, "codex");
});
