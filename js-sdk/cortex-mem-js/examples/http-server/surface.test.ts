import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { app } from './app';

/**
 * Pins the demo's endpoint surface — every (verb, path) it serves — and binds
 * it to the README's endpoint table.
 *
 * Why this exists: the Java demo pins its 44 endpoints (DemoEndpointSurfaceTest)
 * and the Python demo pins its 26 (TestDemoSurface, which also keeps that
 * demo's README honest). The JS demo pinned neither, so the 26-row table in
 * its README was a claim nothing could check: a renamed path, a flipped verb
 * or a dropped route would have left the table quietly wrong, and a table is
 * the first thing a reader copies from.
 *
 * The surface is read from the running Express router — not from a source
 * scan — so it is what the server really serves. The set is compared for
 * **equality**, not containment: an added or a removed endpoint fails, which
 * is the point.
 *
 * The set is also compared against a declared list written down by hand, so
 * that the runtime, the README and this file cannot all drift together.
 */
interface ExpressRouteLayer {
  route?: {
    path?: unknown;
    methods?: Record<string, boolean | undefined>;
  };
}

const README = readFileSync(new URL('./README.md', import.meta.url), 'utf-8');

/** The (verb, path) pairs the running server serves. */
function servedSurface(): Set<string> {
  const router = (app as unknown as { router?: { stack?: ExpressRouteLayer[] } }).router;
  const stack = router?.stack;
  if (!Array.isArray(stack)) {
    throw new Error('the Express router stack is not reachable — the surface cannot be read');
  }
  const out = new Set<string>();
  for (const layer of stack) {
    const route = layer?.route;
    if (!route || typeof route.path !== 'string') continue;
    for (const [method, enabled] of Object.entries(route.methods ?? {})) {
      if (enabled) out.add(`${method.toUpperCase()} ${route.path}`);
    }
  }
  if (out.size === 0) {
    throw new Error('no routes were read from the Express router — the reader is broken, not the demo');
  }
  return out;
}

/** The (verb, path) pairs the README's endpoint table publishes. */
function documentedSurface(): Set<string> {
  const out = new Set<string>();
  const row = /^\|\s*(GET|POST|PUT|PATCH|DELETE)\s*\|\s*`([^`]+)`\s*\|/gm;
  for (const m of README.matchAll(row)) {
    out.add(`${m[1].toUpperCase()} ${m[2]}`);
  }
  if (out.size === 0) {
    throw new Error('no rows were read from the README endpoint table — the reader is broken, not the README');
  }
  return out;
}

function difference(a: Set<string>, b: Set<string>): string[] {
  return [...a].filter((x) => !b.has(x)).sort();
}

describe('demo endpoint surface', () => {
  it('serves exactly the endpoints the README table lists', () => {
    const served = servedSurface();
    const documented = documentedSurface();

    const missing = difference(served, documented);
    const extra = difference(documented, served);
    expect(missing, 'endpoints served but missing from the README table').toEqual([]);
    expect(extra, 'README rows with no endpoint behind them').toEqual([]);
  });

  it('serves exactly the declared set', () => {
    const declared = new Set([
      'GET /health',
      'POST /chat',
      'GET /search',
      'GET /version',
      'GET /experiences',
      'GET /iclprompt',
      'GET /observations',
      'GET /observations/:id',
      'PATCH /observations/:id',
      'DELETE /observations/:id',
      'POST /observations/batch',
      'POST /observations/create',
      'POST /session/start',
      'PATCH /session/user',
      'GET /projects',
      'GET /stats',
      'GET /modes',
      'GET /settings',
      'GET /quality',
      'GET /extraction/latest',
      'GET /extraction/history',
      'POST /extraction/run',
      'POST /refine',
      'POST /feedback',
      'POST /ingest/prompt',
      'POST /ingest/session-end',
    ]);
    const served = servedSurface();

    expect(difference(served, declared), 'endpoints served but not in the declared set').toEqual([]);
    expect(difference(declared, served), 'declared endpoints the demo does not serve').toEqual([]);
  });

  it('matches the endpoint count the README headline states', () => {
    const m = README.match(/exposing (\d+) REST\s+endpoints/);
    expect(m, 'the README no longer states an endpoint count').not.toBeNull();
    expect(Number(m?.[1])).toBe(servedSurface().size);
  });
});
