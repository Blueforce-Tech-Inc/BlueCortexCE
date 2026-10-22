/**
 * The query-parameter contract, pinned endpoint by endpoint.
 *
 * Why this file exists
 * --------------------
 * The request *body* contract of this SDK was pinned in round 399
 * (`wire-format.test.ts`). The **query string** was never pinned, and it has
 * the same failure mode with an extra edge: a body key the backend does not
 * know is ignored, and so is a query parameter the backend does not know.
 * Spring binds only the names it declares and drops the rest without a word.
 *
 * That was measured against a live backend on 2026-10-22: `GET /api/search`
 * with `orderBy=createdAtEpoch` and with `order_by=createdAtEpoch` and with
 * no sort key at all produced **the same 200 response**, byte for byte. A
 * renamed sort key is therefore not an error — it is a sort that quietly stops
 * sorting. (A renamed *required* parameter is louder: the extraction history
 * endpoint answers 400 when `projectPath` is missing.)
 *
 * A mutation scan put a number on the gap. For every query parameter this SDK
 * sends, the wire key was renamed — scoped to the one method that sends it —
 * and the whole suite was run:
 *
 *  - 19 parameter slots across 7 endpoints were probed;
 *  - 15 were pinned (renaming them turned the suite red);
 *  - **4 were pinned by nothing at all** (renaming them left the suite green):
 *
 *      GET /api/search                    orderBy
 *      GET /api/observations              offset
 *      GET /api/extraction/{t}/history    projectPath
 *      GET /api/extraction/{t}/history    userId
 *
 * The two extraction-history gaps are a pair: the sibling `/latest` endpoint
 * has both `projectPath` and `userId` pinned, the `/history` endpoint has
 * neither. And the `/api/observations` gap is a gap in the *positive* case
 * only — the suite asserts that `offset=0` is **not** sent, but never that
 * `offset=10` is.
 *
 * Nothing is wrong with the SDK today: all 19 names match the backend's
 * `@RequestParam` declarations, checked name by name against the live
 * OpenAPI document. This file records a **coverage** gap, not a defect — the
 * same distinction the body-side finding in round 399 made.
 *
 * The three families of check here are:
 *
 *  1. the complete key set of the query string, per endpoint;
 *  2. every key the SDK sends is a key the backend declares, read out of the
 *     backend's own controller sources (the direction the first family cannot
 *     see: the backend contract changing under a stale SDK);
 *  3. a self-test proving the comparison can fail — a checker that reports
 *     "no difference" for every input is the most expensive kind of failure.
 *
 * The expected key sets are written out **by hand** on purpose. Deriving them
 * from the SDK's own parameter-building code would make the assertion
 * tautological, and a tautological assertion is worse than no assertion.
 *
 * The SDK source is untouched by this file — it only pins what is already
 * there.
 */

import { describe, it, expect, vi } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { CortexMemClient } from '../index';

interface QueryCase {
  /** The method and path the request goes to. */
  verb: string;
  /** The concrete path this SDK requests. */
  path: string;
  /** The endpoint's identity in the backend's own controller tables. */
  backend: string;
  /** The complete set of query keys the request carries. */
  keys: string[];
  /** The response body the mock answers with. */
  response: unknown;
  /** A call that supplies every query parameter the endpoint takes. */
  call: (c: CortexMemClient) => Promise<unknown>;
}

const CASES: QueryCase[] = [
  {
    verb: 'GET',
    path: '/api/search',
    backend: '/api/search',
    keys: ['concept', 'limit', 'offset', 'orderBy', 'project', 'query', 'source', 'type'],
    response: { observations: [], strategy: 'hybrid', fell_back: false, count: 0 },
    call: (c) =>
      c.search({
        project: '/p',
        query: 'q',
        type: 'bugfix',
        concept: 'gotcha',
        source: 'manual',
        limit: 5,
        offset: 10,
        orderBy: 'createdAtEpoch',
      }),
  },
  {
    verb: 'GET',
    path: '/api/observations',
    backend: '/api/observations',
    keys: ['limit', 'offset', 'project'],
    response: { items: [], hasMore: false },
    call: (c) => c.listObservations({ project: '/p', offset: 10, limit: 5 }),
  },
  {
    verb: 'GET',
    path: '/api/extraction/user_pref/latest',
    backend: '/api/extraction/{templateName}/latest',
    keys: ['projectPath', 'userId'],
    response: { sessionId: 's1', extractedData: {}, createdAt: 0, observationId: 'o1' },
    call: (c) => c.getLatestExtraction('/p', 'user_pref', 'alice'),
  },
  {
    verb: 'GET',
    path: '/api/extraction/user_pref/history',
    backend: '/api/extraction/{templateName}/history',
    keys: ['limit', 'projectPath', 'userId'],
    response: [],
    call: (c) => c.getExtractionHistory('/p', 'user_pref', 'alice', 5),
  },
  {
    verb: 'GET',
    path: '/api/stats',
    backend: '/api/stats',
    keys: ['project'],
    response: {
      worker: { isProcessing: false, queueDepth: 0 },
      database: { totalObservations: 0, totalSummaries: 0, totalSessions: 0, totalProjects: 0 },
    },
    call: (c) => c.getStats('/p'),
  },
  {
    verb: 'POST',
    path: '/api/memory/refine',
    backend: '/api/memory/refine',
    keys: ['project'],
    response: {},
    call: (c) => c.triggerRefinement('/p'),
  },
  {
    verb: 'GET',
    path: '/api/memory/quality-distribution',
    backend: '/api/memory/quality-distribution',
    keys: ['project'],
    response: { project: '/p', high: 0, medium: 0, low: 0, unknown: 0 },
    call: (c) => c.getQualityDistribution('/p'),
  },
];

// ---------------------------------------------------------------------------
// Capturing the request
// ---------------------------------------------------------------------------

function mockFetch(body: unknown): typeof globalThis.fetch {
  return vi.fn().mockResolvedValue({
    status: 200,
    body: {
      getReader() {
        const encoded = new TextEncoder().encode(JSON.stringify(body));
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
    text() {
      return Promise.resolve(JSON.stringify(body));
    },
  }) as unknown as typeof globalThis.fetch;
}

/**
 * Run one call against a spy fetch and return the URL it requested.
 */
async function capture(kase: QueryCase): Promise<URL> {
  const fetchMock = mockFetch(kase.response);
  const client = new CortexMemClient({ fetch: fetchMock });
  await kase.call(client);
  const calls = (fetchMock as unknown as ReturnType<typeof vi.fn>).mock.calls;
  expect(calls.length, `${kase.verb} ${kase.path}: expected exactly one request`).toBe(1);
  return new URL(calls[0][0] as string);
}

function queryKeys(url: URL): string[] {
  return [...url.searchParams.keys()].sort();
}

/**
 * Compare the query key set against the expectation.
 *
 * Returns null when they match and a description of the difference when they
 * do not, so the caller can report every endpoint at once instead of stopping
 * at the first one.
 */
function keySetDiff(expected: string[], actual: string[]): string | null {
  const want = [...expected].sort();
  const got = [...actual].sort();
  if (want.length === got.length && want.every((k, i) => k === got[i])) return null;
  const missing = want.filter((k) => !got.includes(k));
  const unexpected = got.filter((k) => !want.includes(k));
  return `missing=${JSON.stringify(missing)} unexpected=${JSON.stringify(unexpected)}`;
}

// ---------------------------------------------------------------------------
// Family 1: the complete key set, per endpoint
// ---------------------------------------------------------------------------

describe('query parameter key sets', () => {
  it('sends exactly the expected keys — every endpoint', async () => {
    const problems: string[] = [];
    for (const kase of CASES) {
      const url = await capture(kase);
      expect(url.pathname, `${kase.verb} ${kase.path}: wrong path`).toBe(kase.path);
      const diff = keySetDiff(kase.keys, queryKeys(url));
      if (diff) {
        problems.push(`${kase.verb} ${kase.path}: complete key set mismatch ${diff}`);
      }
    }
    expect(problems.join('\n')).toBe('');
  });

  it('sorts by the key the backend declares, not by a near miss', async () => {
    // The specific gap the scan found: `orderBy` had no assertion at all.
    const kase = CASES.find((k) => k.path === '/api/search')!;
    const url = await capture(kase);
    expect(url.searchParams.get('orderBy')).toBe('createdAtEpoch');
    expect(url.searchParams.has('order_by')).toBe(false);
  });

  it('sends the extraction history parameters the sibling endpoint also sends', async () => {
    // The other half of the gap: `/latest` pinned `projectPath` and `userId`,
    // `/history` pinned neither.
    const kase = CASES.find((k) => k.path === '/api/extraction/user_pref/history')!;
    const url = await capture(kase);
    expect(url.searchParams.get('projectPath')).toBe('/p');
    expect(url.searchParams.get('userId')).toBe('alice');
  });

  it('sends a positive offset for the observations endpoint', async () => {
    // The suite asserted that offset=0 is NOT sent; it never asserted that a
    // real offset IS.
    const kase = CASES.find((k) => k.path === '/api/observations')!;
    const url = await capture(kase);
    expect(url.searchParams.get('offset')).toBe('10');
  });
});

// ---------------------------------------------------------------------------
// Family 2: the backend contract, read from the backend's own source
// ---------------------------------------------------------------------------

/**
 * Index just past the ')' that matches the '(' at src[open].
 */
function closeParen(src: string, open: number): number {
  let depth = 0;
  let inStr = false;
  for (let j = open; j < src.length; j++) {
    const c = src[j];
    if (inStr) {
      if (c === '\\') { j++; continue; }
      if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') { inStr = true; continue; }
    if (c === '(') depth++;
    else if (c === ')') {
      depth--;
      if (depth === 0) return j + 1;
    }
  }
  return -1;
}

/**
 * The names of the @RequestParam parameters in one method signature.
 *
 * `@RequestParam` may carry an argument list or none at all
 * (`@RequestParam String project`), and the declaration that follows it may
 * hold a generic type with a space in it (`Map<String, Object> x`), so the
 * name is read as the last identifier before the parameter ends.
 */
function requestParamNames(sig: string): string[] {
  const out: string[] = [];
  const re = /@RequestParam\b/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(sig)) !== null) {
    let k = m.index + m[0].length;
    while (k < sig.length && (sig[k] === ' ' || sig[k] === '\t')) k++;
    let end: number;
    if (sig[k] === '(') {
      end = closeParen(sig, k);
      if (end < 0) continue;
    } else {
      end = k;
    }
    let depth = 0;
    let j = end;
    for (; j < sig.length; j++) {
      const c = sig[j];
      if (c === '(') depth++;
      else if (c === ')') { if (depth === 0) break; depth--; }
      else if (c === ',' && depth === 0) break;
    }
    const ids = sig.slice(end, j).trim().match(/([A-Za-z_]\w*)\s*$/);
    if (ids) out.push(ids[1]);
  }
  return out;
}

/**
 * The source of the method that follows a mapping annotation.
 */
function methodSignature(src: string, after: number): string {
  const m = /\n    public /.exec(src.slice(after));
  if (!m) return '';
  const start = after + m.index + m[0].length;
  let depth = 0;
  let inStr = false;
  for (let j = start; j < src.length; j++) {
    const c = src[j];
    if (inStr) {
      if (c === '\\') { j++; continue; }
      if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') { inStr = true; continue; }
    if (c === '(' || c === '<' || c === '[') depth++;
    else if (c === ')' || c === '>' || c === ']') depth--;
    else if (c === '{' && depth === 0) return src.slice(start, j);
  }
  return '';
}

/**
 * The backend's controller sources, located from this test file's directory.
 *
 * The check that follows is only meaningful against the real backend sources,
 * so a missing backend directory has to say so plainly: a bare ENOENT would
 * read like a broken path constant, and a test that quietly skipped the
 * comparison would be the most expensive kind of failure.
 */
function backendControllerDir(): URL {
  const dir = new URL(
    '../../../../backend/src/main/java/com/ablueforce/cortexce/controller/',
    import.meta.url,
  );
  if (!existsSync(dir)) {
    throw new Error(
      'cannot find the backend controller sources at ' +
        fileURLToPath(dir) +
        ' — this test binds the SDK query keys to the backend declarations, ' +
        'so it needs the backend sources of the repository it lives in.',
    );
  }
  return dir;
}

/**
 * The query parameter names each backend endpoint declares.
 */
function backendParams(): Map<string, string[]> {
  const wanted = new Set(CASES.map((k) => `${k.verb} ${k.backend}`));
  const out = new Map<string, string[]>();
  const controllerDir = backendControllerDir();
  for (const file of ['ViewerController.java', 'ExtractionController.java', 'MemoryController.java']) {
    const src = readFileSync(new URL(file, controllerDir), 'utf-8');
    const prefix = /@RequestMapping\("([^"]*)"\)/.exec(src)?.[1] ?? '';
    const re = /@(Get|Post|Put|Delete|Patch)Mapping\("([^"]*)"\)/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(src)) !== null) {
      const key = `${m[1].toUpperCase()} ${prefix}${m[2]}`;
      if (!wanted.has(key)) continue;
      out.set(key, requestParamNames(methodSignature(src, m.index + m[0].length)));
    }
  }
  return out;
}

describe('query parameters are declared by the backend', () => {
  it('reads the backend controllers and finds every endpoint', () => {
    const declared = backendParams();
    for (const kase of CASES) {
      const key = `${kase.verb} ${kase.backend}`;
      expect(declared.has(key), `${key} not found in the backend controllers`).toBe(true);
      expect(declared.get(key)!.length, `${key} has no @RequestParam`).toBeGreaterThan(0);
    }
  });

  it('sends no parameter the backend does not declare', () => {
    const declared = backendParams();
    const problems: string[] = [];
    for (const kase of CASES) {
      const names = declared.get(`${kase.verb} ${kase.backend}`)!;
      for (const key of kase.keys) {
        if (!names.includes(key)) {
          problems.push(
            `${kase.verb} ${kase.backend}: the SDK sends "${key}" but the backend does not declare it` +
            ` (backend declares: ${JSON.stringify(names)})`,
          );
        }
      }
    }
    expect(problems.join('\n')).toBe('');
  });
});

// ---------------------------------------------------------------------------
// Family 3: the comparison has to be able to fail
// ---------------------------------------------------------------------------

describe('the key-set comparison is falsifiable', () => {
  it('reports an extra key and a missing key', async () => {
    const kase = CASES.find((k) => k.path === '/api/stats')!;
    const url = await capture(kase);
    const actual = queryKeys(url);

    // The unmodified request matches its expectation.
    expect(keySetDiff(kase.keys, actual)).toBeNull();

    // An extra key is reported.
    expect(keySetDiff(kase.keys, [...actual, 'surprise'])).not.toBeNull();
    expect(keySetDiff(kase.keys, [...actual, 'surprise'])).toContain('unexpected');

    // A renamed key is reported.
    const renamed = actual.map((k) => (k === 'project' ? 'project_probe' : k));
    expect(keySetDiff(kase.keys, renamed)).not.toBeNull();
    expect(keySetDiff(kase.keys, renamed)).toContain('missing');
  });
});
