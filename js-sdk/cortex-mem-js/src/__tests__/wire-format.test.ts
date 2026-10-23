/**
 * The outgoing wire contract, pinned endpoint by endpoint.
 *
 * Why this file exists
 * --------------------
 * The README's "Wire Format" section publishes the field names the SDK puts on
 * the wire — `cwd`, `tool_name`, `extractedData`, `requiredConcepts`, `userId`
 * among them. That section is a contract with the backend, and the backend is
 * the authority: its request records spell the names out with `@JsonProperty`.
 *
 * Nothing pinned those names. Of the ten endpoints whose request carries a
 * body, **not one** had its body's key set asserted as a whole:
 *
 *  - `POST /api/ingest/tool-use` and `POST /api/ingest/session-end` asserted
 *    no body at all — only the URL and the method.
 *  - `POST /api/memory/experiences` asserted whether `count` was on the body
 *    and with which value; `POST /api/memory/icl-prompt` did the same for
 *    `maxChars`. Both checked one field's fate, never the set of fields.
 *  - The other six asserted individual field names (`startSession`,
 *    `updateSessionUserId`, `recordUserPrompt`, `submitFeedback`,
 *    `getObservationsByIds`, `updateObservation`); the last of them pinned a
 *    key set only for two *partial* update shapes — eight fields all null, and
 *    a lone `title` — never for the full-field shape.
 *
 * Every field name the README publishes for the capture and retrieval
 * endpoints — `session_id`, `cwd`, `tool_name`, `extractedData` (the capture
 * group), `requiredConcepts`, `userId` (the Experience and ICL group) — was
 * therefore free to drift **on those endpoints**: rename an interface
 * property and the suite would have said nothing while the SDK silently
 * stopped matching the backend.
 *
 * The README publishes no field name for the session-end endpoint at all, so
 * there is nothing to bind there. That is an omission rather than a false
 * statement, and this file pins what the README does publish rather than
 * trying to fill the gap.
 *
 * That gap has a sharp edge in this SDK. The TypeScript interfaces are the
 * *only* place the wire names exist: the client passes the request object
 * straight to `JSON.stringify`, so the names on the wire are whatever the
 * caller wrote, and the types that document them vanish at runtime. A property
 * rename is a silent wire change.
 *
 * How the expected key sets were obtained
 * ---------------------------------------
 * By measurement, not by reading the interfaces: a spy `fetch` recorded the
 * parsed JSON key set of every request this SDK sends. Those measurements were
 * then checked against the backend's `@JsonProperty` names, endpoint by
 * endpoint, and they agree:
 *
 *   POST /api/session/start            session_id, project_path, user_id, platform_source
 *   PATCH /api/session/user            user_id + opaque identity query
 *   POST /api/ingest/tool-use          session_id, cwd, tool_name, platform_source, tool_input,
 *                                      tool_response, prompt_number, source,
 *                                      extractedData
 *   POST /api/ingest/session-end       session_id, cwd, last_assistant_message, platform_source
 *   POST /api/ingest/user-prompt       session_id, prompt_text, cwd, prompt_number, platform_source
 *   POST /api/memory/experiences       task, project, count, source,
 *                                      requiredConcepts, userId
 *   POST /api/memory/icl-prompt        task, project, maxChars, userId
 *   POST /api/memory/feedback          observationId, feedbackType, comment
 *   PATCH /api/memory/observations/{id} title, subtitle, content, narrative,
 *                                      facts, concepts, source, extractedData
 *   POST /api/observations/batch       ids
 *
 * The SDK source is untouched by this file — it only pins what is already
 * there.
 */

import { describe, it, expect, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { CortexMemClient, ValidationError } from '../index';

interface Endpoint {
  /** The method and path the request goes to. */
  verb: string;
  path: string;
  /** The exact JSON keys the request body carries, in wire order. */
  keys: string[];
  /** A call that supplies every field of the request. */
  call: (c: CortexMemClient) => Promise<unknown>;
}

const ENDPOINTS: Endpoint[] = [
  {
    verb: 'POST',
    path: '/api/session/start',
    keys: ['session_id', 'project_path', 'user_id', 'platform_source'],
    call: (c) => c.startSession({ session_id: 's1', project_path: '/p', user_id: 'u1', platform_source: 'codex' }),
  },
  {
    verb: 'PATCH',
    path: '/api/session/user',
    keys: ['user_id'],
    call: (c) => c.updateSessionUserId('s1', 'u1'),
  },
  {
    verb: 'POST',
    path: '/api/ingest/tool-use',
    keys: [
      'session_id', 'cwd', 'tool_name', 'platform_source', 'tool_input',
      'tool_response', 'prompt_number', 'source', 'extractedData',
    ],
    call: (c) =>
      c.recordObservation({
        session_id: 's1',
        cwd: '/p',
        tool_name: 'Read',
        platform_source: 'codex',
        tool_input: { file: 'a.txt' },
        tool_response: 'ok',
        prompt_number: 3,
        source: 'cli',
        extractedData: { k: 'v' },
      }),
  },
  {
    verb: 'POST',
    path: '/api/ingest/session-end',
    keys: ['session_id', 'cwd', 'last_assistant_message', 'platform_source'],
    call: (c) => c.recordSessionEnd({ session_id: 's1', cwd: '/p', last_assistant_message: 'bye', platform_source: 'codex' }),
  },
  {
    verb: 'POST',
    path: '/api/ingest/user-prompt',
    keys: ['session_id', 'prompt_text', 'cwd', 'prompt_number', 'platform_source'],
    call: (c) => c.recordUserPrompt({ session_id: 's1', prompt_text: 'hi', cwd: '/p', prompt_number: 2, platform_source: 'codex' }),
  },
  {
    verb: 'POST',
    path: '/api/memory/experiences',
    keys: ['task', 'project', 'count', 'source', 'requiredConcepts', 'userId'],
    call: (c) =>
      c.retrieveExperiences({
        task: 't', project: '/p', count: 5, source: 's', requiredConcepts: ['c1'], userId: 'u1',
      }),
  },
  {
    verb: 'POST',
    path: '/api/memory/icl-prompt',
    keys: ['task', 'project', 'maxChars', 'userId'],
    call: (c) => c.buildICLPrompt({ task: 't', project: '/p', maxChars: 500, userId: 'u1' }),
  },
  {
    verb: 'POST',
    path: '/api/memory/feedback',
    keys: ['observationId', 'feedbackType', 'comment'],
    call: (c) => c.submitFeedback({ observationId: 'o1', feedbackType: 'useful', comment: 'c' }),
  },
  {
    verb: 'PATCH',
    path: '/api/memory/observations/o1',
    keys: [
      'title', 'subtitle', 'content', 'narrative',
      'facts', 'concepts', 'source', 'extractedData',
    ],
    call: (c) =>
      c.updateObservation('o1', {
        title: 'T',
        subtitle: 'S',
        content: 'C',
        narrative: 'N',
        facts: ['f'],
        concepts: ['c'],
        source: 'manual',
        extractedData: { k: 'v' },
      }),
  },
  {
    verb: 'POST',
    path: '/api/observations/batch',
    keys: ['ids'],
    call: (c) => c.getObservationsByIds(['o1', 'o2']),
  },
];

interface Captured {
  verb: string;
  url: string;
  keys: string[];
  body: unknown;
}

function capturingFetch(): { fetch: typeof globalThis.fetch; captured: Captured[] } {
  const captured: Captured[] = [];
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    let keys: string[] = [];
    let body: unknown = null;
    if (typeof init?.body === 'string') {
      body = JSON.parse(init.body);
      keys = Object.keys(body as Record<string, unknown>);
    }
    captured.push({ verb: init?.method ?? 'GET', url: String(url), keys, body });
    return {
      status: 200,
      headers: { get: () => null },
      body: {
        getReader() {
          const encoded = new TextEncoder().encode('{}');
          let consumed = false;
          return {
            read() {
              if (consumed) return Promise.resolve({ done: true });
              consumed = true;
              return Promise.resolve({ value: encoded, done: false });
            },
          };
        },
      },
      text: () => Promise.resolve('{}'),
    } as unknown as Response;
  });
  return { fetch: fetchMock as unknown as typeof globalThis.fetch, captured };
}

async function capture(endpoint: Endpoint): Promise<Captured> {
  const { fetch, captured } = capturingFetch();
  const c = new CortexMemClient({ baseURL: 'http://127.0.0.1:37777', fetch });
  // The spy answers `{}` for every endpoint; some parsers may complain about
  // that. What matters here is the request that was sent.
  await endpoint.call(c).catch(() => undefined);
  expect(captured, `${endpoint.verb} ${endpoint.path} sent no request at all`).toHaveLength(1);
  return captured[0];
}

describe('outgoing wire format — the exact key set of every request body', () => {
  it.each(ENDPOINTS.map((e) => [e.verb, e.path, e] as const))(
    '%s %s carries exactly the published keys',
    async (_verb, _path, endpoint) => {
      const sent = await capture(endpoint);
      expect(sent.keys).toEqual(endpoint.keys);
    },
  );

  it('records the measurement the expectations were taken from', () => {
    // If this ever fails, the catalogue above drifted from the code and the
    // per-endpoint expectations have to be re-measured, not edited by hand.
    expect(ENDPOINTS.map((e) => `${e.verb} ${e.path}`)).toEqual([
      'POST /api/session/start',
      'PATCH /api/session/user',
      'POST /api/ingest/tool-use',
      'POST /api/ingest/session-end',
      'POST /api/ingest/user-prompt',
      'POST /api/memory/experiences',
      'POST /api/memory/icl-prompt',
      'POST /api/memory/feedback',
      'PATCH /api/memory/observations/o1',
      'POST /api/observations/batch',
    ]);
  });
});

// ---------------------------------------------------------------------------
// README "Wire Format" section ⇄ the keys that actually go out
// ---------------------------------------------------------------------------

const README_EN = readFileSync(new URL('../../README.md', import.meta.url), 'utf-8');
const README_ZH = readFileSync(new URL('../../README-zh-CN.md', import.meta.url), 'utf-8');

/**
 * Parse the bullet lists of the "Wire Format" section into groups.
 *
 * Two details matter:
 *
 *  - Only the `**Group:**` bullets are collected. The section also has prose
 *    paragraphs about the *response* shape (`facts`, `concepts`, `filesRead`,
 *    …), and those are not request fields. The response group is prose-only,
 *    so it contributes no bullets and is left out.
 *  - Only the part of a bullet **before the em dash** is read. A bullet reads
 *    like "`session_id`, `project_path` (snake_case) — `SessionStartRequest`":
 *    the names before the dash are wire field names, the ones after it are
 *    TypeScript type names, and the type names are not sent on the wire.
 */
function parseWireFormatGroups(section: string): Map<string, string[]> {
  const groups = new Map<string, string[]>();
  let current: string[] | null = null;
  for (const line of section.split('\n')) {
    // The colon is full-width in the Chinese README and half-width in the
    // English one, so both are accepted.
    const heading = /^\*\*(.+?)[:：]\*\*$/.exec(line);
    if (heading) {
      current = [];
      groups.set(heading[1], current);
      continue;
    }
    if (current && line.startsWith('- ')) {
      const fieldPart = line.split('—')[0];
      for (const m of fieldPart.matchAll(/`([^`]+)`/g)) current.push(m[1]);
    }
  }
  // Groups without bullets document response fields, not request fields.
  for (const [name, fields] of [...groups]) {
    if (fields.length === 0) groups.delete(name);
  }
  return groups;
}

/**
 * The request groups each README publishes, the endpoints each one covers, and
 * the field names the group is supposed to publish.
 *
 * The group *labels* differ per language, so the mapping is per language; the
 * endpoint list and the published names are the measured ones. Pinning the
 * published names makes the binding two-directional: a name the SDK does not
 * send is caught, and so is a name the README stopped publishing.
 */
const WIRE_FORMAT_SECTIONS: Array<
  [string, string, Record<string, { paths: string[]; published: string[] }>]
> = [
  [
    'README.md',
    '## Wire Format',
    {
      Session: {
        paths: ['POST /api/session/start'],
        published: ['session_id', 'project_path', 'user_id', 'platform_source'],
      },
      'Observation (capture)': {
        paths: ['POST /api/ingest/tool-use'],
        published: ['session_id', 'cwd', 'tool_name', 'platform_source', 'extractedData'],
      },
      'Experience & ICL': {
        paths: ['POST /api/memory/experiences', 'POST /api/memory/icl-prompt'],
        published: ['requiredConcepts', 'userId'],
      },
      Feedback: {
        paths: ['POST /api/memory/feedback'],
        published: ['observationId', 'feedbackType'],
      },
    },
  ],
  [
    'README-zh-CN.md',
    '## Wire 格式',
    {
      Session: {
        paths: ['POST /api/session/start'],
        published: ['session_id', 'project_path', 'user_id', 'platform_source'],
      },
      'Observation（采集）': {
        paths: ['POST /api/ingest/tool-use'],
        published: ['session_id', 'cwd', 'tool_name', 'platform_source', 'extractedData'],
      },
      'Experience 和 ICL': {
        paths: ['POST /api/memory/experiences', 'POST /api/memory/icl-prompt'],
        published: ['requiredConcepts', 'userId'],
      },
      Feedback: {
        paths: ['POST /api/memory/feedback'],
        published: ['observationId', 'feedbackType'],
      },
    },
  ],
];

describe('README wire-format section matches the keys that go out', () => {
  it.each(WIRE_FORMAT_SECTIONS)(
    '%s publishes only field names the SDK actually sends',
    async (_name, heading, groups) => {
      const readme = _name === 'README.md' ? README_EN : README_ZH;
      const start = readme.indexOf(heading);
      expect(start, `"${heading}" section not found`).toBeGreaterThan(-1);
      const end = readme.indexOf('\n## ', start + 5);
      const section = readme.slice(start, end === -1 ? undefined : end);

      const parsed = parseWireFormatGroups(section);
      expect([...parsed.keys()].sort()).toEqual(Object.keys(groups).sort());

      for (const [group, published] of parsed) {
        const spec = groups[group];
        expect(spec, `the wire-format group "${group}" has no endpoint mapping`).toBeDefined();

        const sent = new Set<string>();
        for (const path of spec.paths) {
          const endpoint = ENDPOINTS.find((e) => `${e.verb} ${e.path}` === path);
          expect(endpoint, `no measured endpoint for ${path}`).toBeDefined();
          for (const key of (endpoint as Endpoint).keys) sent.add(key);
        }
        for (const field of published) {
          expect(
            [...sent],
            `${group}: the README publishes "${field}" but the SDK sends ${JSON.stringify([...sent])}`,
          ).toContain(field);
        }
        // The other direction: a published name the section dropped would
        // otherwise go unnoticed.
        expect([...published].sort()).toEqual([...spec.published].sort());
      }
    },
  );
});

// ---------------------------------------------------------------------------
// content / narrative: two names for one backend field
// ---------------------------------------------------------------------------

describe('content and narrative are aliases, not two fields', () => {
  it('sends both without complaining — the backend keeps content and drops narrative', async () => {
    const { fetch, captured } = capturingFetch();
    const c = new CortexMemClient({ baseURL: 'http://127.0.0.1:37777', fetch });

    // The other three SDKs reject this combination; this one does not, and the
    // backend then keeps "content" and discards "narrative" silently.
    await expect(
      c.updateObservation('o1', { content: 'C', narrative: 'N' }),
    ).resolves.toBeUndefined();

    expect(captured).toHaveLength(1);
    const body = captured[0].body as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(['content', 'narrative']);
    expect(body.content).toBe('C');
    expect(body.narrative).toBe('N');
  });

  it('the README tells the reader that one of the two is silently discarded', () => {
    const en = README_EN;
    const zh = README_ZH;
    expect(en).toContain('`narrative`');
    expect(en).toMatch(/silently/i);
    expect(zh).toContain('`narrative`');
    expect(zh).toMatch(/静默|悄悄/);
  });
});
