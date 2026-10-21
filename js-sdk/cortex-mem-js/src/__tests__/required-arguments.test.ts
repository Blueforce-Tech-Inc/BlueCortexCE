/**
 * The "Required arguments" contract, pinned end to end.
 *
 * The README publishes an 18-row table of required arguments and one sentence
 * that binds all of it: "Every argument below must be non-empty. The SDK
 * throws a `ValidationError` and sends no request."
 *
 * That table is a contract, but nothing tied it to the code. A per-guard
 * mutation sweep of `src/client.ts` (32 guard sites, each removed on its own)
 * showed that 16 of them were enforced by no test at all — for 8 of the 18
 * published rows, deleting the guard left all 273 tests green. A guard could
 * have been dropped from the SDK, or renamed, or moved, and the suite would
 * have said nothing.
 *
 * This file closes that gap in three directions:
 *
 *   1. every guard is asserted — error type, error field, the full message
 *      verbatim, and that no request goes out;
 *   2. every method gets a positive control — the same call with valid
 *      arguments *does* send a request, so "no request was sent" can never be
 *      satisfied vacuously by a method that never talks to the network;
 *   3. the README table itself is parsed and compared with the guards in both
 *      directions, in both languages, so the published table and the code
 *      cannot drift apart.
 *
 * SDK source is untouched by this file — it only pins what is already there.
 */

import { describe, it, expect, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { CortexMemClient, ValidationError } from '../index';

type Call = (c: CortexMemClient) => Promise<unknown>;

interface GuardCase {
  /** Which published field this rule belongs to. */
  docField: string;
  /** The field name the SDK reports in `ValidationError.field`. */
  field: string;
  /** The full message, pinned verbatim. */
  message: string;
  /** A call that supplies every other required argument correctly. */
  call: Call;
  /**
   * True when the rule is documented in prose rather than as a backticked
   * field name (the "at least one field to change" clause on `updateObservation`).
   */
  proseOnly?: boolean;
}

interface MethodSpec {
  method: string;
  guards: GuardCase[];
  /** A call with every required argument present — the positive control. */
  valid: Call;
}

const required = (field: string): string => `cortex-ce: validation error on ${field}: is required`;

/**
 * The catalogue, written from the README's table and checked against the SDK
 * by the mutation sweep that motivated this file.
 */
const CATALOGUE: MethodSpec[] = [
  {
    method: 'startSession',
    guards: [
      { docField: 'session_id', field: 'session_id', message: required('session_id'),
        call: (c) => c.startSession({ session_id: '', project_path: '/p' }) },
      { docField: 'project_path', field: 'project_path', message: required('project_path'),
        call: (c) => c.startSession({ session_id: 's', project_path: '   ' }) },
    ],
    valid: (c) => c.startSession({ session_id: 's', project_path: '/p' }),
  },
  {
    method: 'updateSessionUserId',
    guards: [
      { docField: 'sessionId', field: 'sessionId', message: required('sessionId'),
        call: (c) => c.updateSessionUserId('', 'u') },
      { docField: 'userId', field: 'userId', message: required('userId'),
        call: (c) => c.updateSessionUserId('sid', '') },
    ],
    valid: (c) => c.updateSessionUserId('sid', 'u'),
  },
  {
    method: 'recordObservation',
    guards: [
      { docField: 'session_id', field: 'session_id', message: required('session_id'),
        call: (c) => c.recordObservation({ session_id: '', cwd: '/p', tool_name: 'Read' }) },
      { docField: 'cwd', field: 'cwd', message: required('cwd'),
        call: (c) => c.recordObservation({ session_id: 's', cwd: '', tool_name: 'Read' }) },
      { docField: 'tool_name', field: 'tool_name', message: required('tool_name'),
        call: (c) => c.recordObservation({ session_id: 's', cwd: '/p', tool_name: '' }) },
    ],
    valid: (c) => c.recordObservation({ session_id: 's', cwd: '/p', tool_name: 'Read' }),
  },
  {
    method: 'recordSessionEnd',
    guards: [
      { docField: 'session_id', field: 'session_id', message: required('session_id'),
        call: (c) => c.recordSessionEnd({ session_id: '', cwd: '/p' }) },
      { docField: 'cwd', field: 'cwd', message: required('cwd'),
        call: (c) => c.recordSessionEnd({ session_id: 's', cwd: '' }) },
    ],
    valid: (c) => c.recordSessionEnd({ session_id: 's', cwd: '/p' }),
  },
  {
    method: 'recordUserPrompt',
    guards: [
      { docField: 'session_id', field: 'session_id', message: required('session_id'),
        call: (c) => c.recordUserPrompt({ session_id: '', prompt_text: 'p', cwd: '/p' }) },
      { docField: 'prompt_text', field: 'prompt_text', message: required('prompt_text'),
        call: (c) => c.recordUserPrompt({ session_id: 's', prompt_text: '', cwd: '/p' }) },
      { docField: 'cwd', field: 'cwd', message: required('cwd'),
        call: (c) => c.recordUserPrompt({ session_id: 's', prompt_text: 'p', cwd: '  ' }) },
    ],
    valid: (c) => c.recordUserPrompt({ session_id: 's', prompt_text: 'p', cwd: '/p' }),
  },
  {
    method: 'retrieveExperiences',
    guards: [
      { docField: 'task', field: 'task', message: required('task'),
        call: (c) => c.retrieveExperiences({ task: '' }) },
    ],
    valid: (c) => c.retrieveExperiences({ task: 't' }),
  },
  {
    method: 'buildICLPrompt',
    guards: [
      { docField: 'task', field: 'task', message: required('task'),
        call: (c) => c.buildICLPrompt({ task: '' }) },
    ],
    valid: (c) => c.buildICLPrompt({ task: 't' }),
  },
  {
    method: 'search',
    guards: [
      { docField: 'project', field: 'project', message: required('project'),
        call: (c) => c.search({ project: '' }) },
    ],
    valid: (c) => c.search({ project: '/p' }),
  },
  {
    method: 'getObservation',
    guards: [
      { docField: 'id', field: 'id', message: required('id'),
        call: (c) => c.getObservation('') },
    ],
    valid: (c) => c.getObservation('o1'),
  },
  {
    method: 'getObservationsByIds',
    guards: [
      { docField: 'ids', field: 'ids', message: 'cortex-ce: validation error on ids: must not be empty',
        call: (c) => c.getObservationsByIds([]) },
      { docField: 'ids', field: 'ids',
        message: 'cortex-ce: validation error on ids: batch size exceeds maximum of 100 (got 101)',
        call: (c) => c.getObservationsByIds(Array.from({ length: 101 }, (_, i) => `o${i}`)) },
      { docField: 'ids', field: 'ids[0]', message: 'cortex-ce: validation error on ids[0]: is empty',
        call: (c) => c.getObservationsByIds(['  ']) },
    ],
    valid: (c) => c.getObservationsByIds(['o1']),
  },
  {
    method: 'triggerRefinement',
    guards: [
      { docField: 'projectPath', field: 'projectPath', message: required('projectPath'),
        call: (c) => c.triggerRefinement('') },
    ],
    valid: (c) => c.triggerRefinement('/p'),
  },
  {
    method: 'submitFeedback',
    guards: [
      { docField: 'observationId', field: 'observationId', message: required('observationId'),
        call: (c) => c.submitFeedback({ observationId: '', feedbackType: 'helpful' }) },
      { docField: 'feedbackType', field: 'feedbackType', message: required('feedbackType'),
        call: (c) => c.submitFeedback({ observationId: 'o1', feedbackType: '' }) },
    ],
    valid: (c) => c.submitFeedback({ observationId: 'o1', feedbackType: 'helpful' }),
  },
  {
    method: 'updateObservation',
    guards: [
      { docField: 'observationId', field: 'observationId', message: required('observationId'),
        call: (c) => c.updateObservation('', { title: 't' }) },
      { docField: 'update', field: 'update',
        message: 'cortex-ce: validation error on update: at least one field must be provided for update',
        proseOnly: true,
        call: (c) => c.updateObservation('o1', {}) },
    ],
    valid: (c) => c.updateObservation('o1', { title: 't' }),
  },
  {
    method: 'deleteObservation',
    guards: [
      { docField: 'observationId', field: 'observationId', message: required('observationId'),
        call: (c) => c.deleteObservation('') },
    ],
    valid: (c) => c.deleteObservation('o1'),
  },
  {
    method: 'getQualityDistribution',
    guards: [
      { docField: 'projectPath', field: 'projectPath', message: required('projectPath'),
        call: (c) => c.getQualityDistribution('') },
    ],
    valid: (c) => c.getQualityDistribution('/p'),
  },
  {
    method: 'triggerExtraction',
    guards: [
      { docField: 'projectPath', field: 'projectPath', message: required('projectPath'),
        call: (c) => c.triggerExtraction('') },
    ],
    valid: (c) => c.triggerExtraction('/p'),
  },
  {
    method: 'getLatestExtraction',
    guards: [
      { docField: 'projectPath', field: 'projectPath', message: required('projectPath'),
        call: (c) => c.getLatestExtraction('', 'tpl') },
      { docField: 'templateName', field: 'templateName', message: required('templateName'),
        call: (c) => c.getLatestExtraction('/p', '') },
    ],
    valid: (c) => c.getLatestExtraction('/p', 'tpl'),
  },
  {
    method: 'getExtractionHistory',
    guards: [
      { docField: 'projectPath', field: 'projectPath', message: required('projectPath'),
        call: (c) => c.getExtractionHistory('', 'tpl') },
      { docField: 'templateName', field: 'templateName', message: required('templateName'),
        call: (c) => c.getExtractionHistory('/p', '') },
      { docField: 'limit', field: 'limit', message: 'cortex-ce: validation error on limit: must not be negative',
        call: (c) => c.getExtractionHistory('/p', 'tpl', undefined, -1) },
    ],
    valid: (c) => c.getExtractionHistory('/p', 'tpl'),
  },
];

/**
 * The methods the README's Methods section lists that take no required
 * argument at all. Together with the 18 table rows they are the 25 API
 * methods the README's feature list claims.
 */
const NO_REQUIRED_ARGUMENTS = [
  'listObservations',
  'healthCheck',
  'getVersion',
  'getProjects',
  'getStats',
  'getModes',
  'getSettings',
];

/** `close()` and `toString()` are lifecycle/debug members, not API methods. */
const NOT_API_METHODS = ['close', 'toString'];

function spyFetch(): ReturnType<typeof vi.fn> {
  return vi.fn().mockResolvedValue({
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
  });
}

function clientWith(fetchMock: ReturnType<typeof vi.fn>): CortexMemClient {
  return new CortexMemClient({
    baseURL: 'http://127.0.0.1:37777',
    fetch: fetchMock as unknown as typeof globalThis.fetch,
  });
}

const ALL_GUARDS = CATALOGUE.flatMap((spec) =>
  spec.guards.map((guard) => ({ ...guard, method: spec.method })),
);

describe('required arguments — every guard is enforced', () => {
  it.each(ALL_GUARDS.map((g) => [g.method, g.docField, g] as const))(
    '%s / %s throws the documented ValidationError and sends no request',
    async (_method, _docField, guard) => {
      const fetchMock = spyFetch();
      const c = clientWith(fetchMock);

      const err = await guard
        .call(c)
        .then(() => null)
        .catch((e: unknown) => e);

      expect(err).toBeInstanceOf(ValidationError);
      expect((err as ValidationError).field).toBe(guard.field);
      expect((err as ValidationError).message).toBe(guard.message);
      // The published rule is "throws ... and sends no request".
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );
});

describe('required arguments — positive control', () => {
  it.each(CATALOGUE.map((spec) => [spec.method, spec] as const))(
    '%s does send a request when every required argument is present',
    async (_method, spec) => {
      const fetchMock = spyFetch();
      const c = clientWith(fetchMock);

      // The response body is not shaped like any DTO, so the call may reject —
      // what matters is that the request was attempted, which is what makes
      // the "no request was sent" assertion above meaningful.
      await spec.valid(c).catch(() => undefined);

      expect(fetchMock).toHaveBeenCalledTimes(1);
    },
  );
});

// ---------------------------------------------------------------------------
// README table ⇄ SDK guards
// ---------------------------------------------------------------------------

const README_EN = readFileSync(new URL('../../README.md', import.meta.url), 'utf-8');
const README_ZH = readFileSync(new URL('../../README-zh-CN.md', import.meta.url), 'utf-8');

/** Extract the backticked field names from one cell of the required-args table. */
function docTokens(cell: string): string[] {
  return [...cell.matchAll(/`([^`]+)`/g)].map((m) => m[1].replace(/^req\./, ''));
}

/**
 * Parse the "Required arguments" table: method name → the field names the
 * table publishes for it.
 */
function parseRequiredArgsTable(readme: string, headerPrefix: string): Map<string, string[]> {
  const lines = readme.split('\n');
  const headerIndex = lines.findIndex(
    (l) => l.startsWith('|') && l.includes(headerPrefix) && !l.includes('---'),
  );
  expect(headerIndex, `header row starting with ${headerPrefix} not found`).toBeGreaterThan(-1);

  const out = new Map<string, string[]>();
  for (let i = headerIndex + 1; i < lines.length; i++) {
    const line = lines[i];
    if (!line.startsWith('|')) break;
    if (line.includes('---')) continue;
    const cells = line.split('|').slice(1, -1).map((s) => s.trim());
    const method = cells[0].replace(/`/g, '');
    out.set(method, docTokens(cells[1]));
  }
  return out;
}

/** The method names the README's Methods section lists. */
function parseMethodsSection(readme: string): string[] {
  const start = readme.indexOf('### Methods');
  expect(start, '"### Methods" section not found').toBeGreaterThan(-1);
  const end = readme.indexOf('### Error Handling', start);
  const section = readme.slice(start, end === -1 ? undefined : end);
  const names = new Set<string>();
  for (const m of section.matchAll(/^\|\s*`([A-Za-z][A-Za-z0-9_]*)\(/gm)) {
    names.add(m[1]);
  }
  return [...names];
}

describe('README required-arguments table matches the SDK', () => {
  const enTable = parseRequiredArgsTable(README_EN, 'Required arguments');
  const zhTable = parseRequiredArgsTable(README_ZH, '必填参数');

  it('publishes the same 18 methods in both languages', () => {
    expect([...enTable.keys()].sort()).toEqual([...zhTable.keys()].sort());
    expect(enTable.size).toBe(18);
  });

  it.each(CATALOGUE.map((spec) => [spec.method, spec] as const))(
    '%s — the English table publishes exactly the guarded fields',
    (_method, spec) => {
      const published = enTable.get(spec.method);
      expect(published, `${spec.method} is missing from the English table`).toBeDefined();
      // Several rules can share one published field name — `ids` carries three.
      const guarded = [...new Set(spec.guards.filter((g) => !g.proseOnly).map((g) => g.docField))].sort();
      expect([...(published as string[])].sort()).toEqual(guarded);
    },
  );

  it.each(CATALOGUE.map((spec) => [spec.method, spec] as const))(
    '%s — the Chinese table publishes exactly the guarded fields',
    (_method, spec) => {
      const published = zhTable.get(spec.method);
      expect(published, `${spec.method} is missing from the Chinese table`).toBeDefined();
      const guarded = [...new Set(spec.guards.filter((g) => !g.proseOnly).map((g) => g.docField))].sort();
      expect([...(published as string[])].sort()).toEqual(guarded);
    },
  );

  it('every published field is a field the SDK actually guards', () => {
    for (const [method, fields] of enTable) {
      const spec = CATALOGUE.find((s) => s.method === method);
      expect(spec, `${method} has a published row but no guard`).toBeDefined();
      const guardFields = (spec as MethodSpec).guards.map((g) => g.field);
      for (const field of fields) {
        expect(
          guardFields,
          `${method}: the README publishes "${field}" but the SDK guards ${JSON.stringify(guardFields)}`,
        ).toContain(field);
      }
    }
  });

  it('the prose-only rule on updateObservation is actually written down', () => {
    const proseRule = CATALOGUE.find((s) => s.method === 'updateObservation');
    const proseGuards = (proseRule as MethodSpec).guards.filter((g) => g.proseOnly);
    expect(proseGuards).toHaveLength(1);
    expect(README_EN).toContain('plus at least one field to change');
    expect(README_ZH).toContain('外加至少一个待修改字段');
  });

  it('the 18 tabled methods plus the 7 argument-free ones are the 25 API methods', () => {
    const enMethods = parseMethodsSection(README_EN).filter((m) => !NOT_API_METHODS.includes(m));
    expect(enMethods).toHaveLength(25);
    const tabled = [...enTable.keys()];
    const argumentFree = NO_REQUIRED_ARGUMENTS;
    expect([...tabled, ...argumentFree].sort()).toEqual([...enMethods].sort());
  });
});

describe('README published error strings match the running client', () => {
  it('the published empty-update message is the message the client throws', async () => {
    const fetchMock = spyFetch();
    const c = clientWith(fetchMock);

    const err = await c
      .updateObservation('o1', {})
      .then(() => null)
      .catch((e: unknown) => e);

    const live = (err as ValidationError).message;

    // The README publishes the message inside a fenced code block.
    const published = README_EN.match(
      /```\n(cortex-ce: validation error on update: [^\n]+)\n```/,
    );
    expect(published, 'the empty-update message block was not found in README.md').not.toBeNull();
    expect(live).toBe((published as RegExpMatchArray)[1]);

    // And the Chinese README publishes the same string.
    expect(README_ZH).toContain(live);
  });
});

// ---------------------------------------------------------------------------
// The `limit` rule applies to one method only
// ---------------------------------------------------------------------------

describe('limit handling differs between the three paginated methods', () => {
  it('search drops a non-positive limit instead of sending it', async () => {
    const fetchMock = spyFetch();
    const c = clientWith(fetchMock);

    await c.search({ project: '/p', limit: -5 }).catch(() => undefined);

    const url = (fetchMock.mock.calls[0] as unknown as [string])[0];
    expect(url).not.toContain('limit');
  });

  it('listObservations drops a non-positive limit instead of sending it', async () => {
    const fetchMock = spyFetch();
    const c = clientWith(fetchMock);

    await c.listObservations({ limit: -5 }).catch(() => undefined);

    const url = (fetchMock.mock.calls[0] as unknown as [string])[0];
    expect(url).not.toContain('limit');
  });

  it('search does send a positive limit', async () => {
    const fetchMock = spyFetch();
    const c = clientWith(fetchMock);

    await c.search({ project: '/p', limit: 5 }).catch(() => undefined);

    const url = (fetchMock.mock.calls[0] as unknown as [string])[0];
    expect(url).toContain('limit=5');
  });

  it('getExtractionHistory rejects a negative limit instead of dropping it', async () => {
    const fetchMock = spyFetch();
    const c = clientWith(fetchMock);

    const err = await c
      .getExtractionHistory('/p', 'tpl', undefined, -1)
      .then(() => null)
      .catch((e: unknown) => e);

    expect(err).toBeInstanceOf(ValidationError);
    expect((err as ValidationError).field).toBe('limit');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('the published limit comparison covers the same four SDKs in both languages', () => {
  it('both READMEs publish a row for each of the four SDKs', () => {
    for (const sdk of ['Java', 'Go', 'JS', 'Python']) {
      expect(README_EN, `README.md has no row for ${sdk}`).toContain(`| ${sdk} |`);
      expect(README_ZH, `README-zh-CN.md has no row for ${sdk}`).toContain(`| ${sdk} |`);
    }
  });

  it('both READMEs state the same verdict for this SDK', () => {
    expect(README_EN).toMatch(/\|\s*JS\s*\|[^\n]*silently dropped/);
    expect(README_ZH).toMatch(/\|\s*JS\s*\|[^\n]*静默丢弃/);
  });
});
