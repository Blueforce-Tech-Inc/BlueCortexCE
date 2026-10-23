import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest';
import type { AddressInfo } from 'node:net';
import type { Server } from 'node:http';
import { app } from './app';

vi.mock('../../src', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../src')>();
  return {
    ...actual,
    CortexMemClient: class extends actual.CortexMemClient {
      override async healthCheck() {
        return { status: 'ok', service: 'test' };
      }
    },
  };
});

/**
 * Pins the demo's 404 / 405 contract.
 *
 * Express's built-in 404 handler answers an HTML page ("Cannot GET /x") and
 * Express does not answer 405 at all — a wrong method simply falls through to
 * that same HTML 404. Every other error path in this demo answers JSON, so a
 * typo'd path or a wrong verb produced text/html where a JSON client expected
 * {"error": "..."}. Measured live before the terminal handler existed:
 *
 *   GET  /health            -> 200 JSON
 *   POST /health            -> 404 text/html  "Cannot POST /health"
 *   GET  /no-such-endpoint  -> 404 text/html  "Cannot GET /no-such-endpoint"
 *
 * The other three demos answer 405 with JSON for a wrong method and 404 with
 * JSON for an unknown path, and the Go demo README states that contract.
 */
let server: Server;
let base: string;

beforeAll(async () => {
  await new Promise<void>((resolve) => {
    server = app.listen(0, () => resolve());
  });
  const addr = server.address() as AddressInfo;
  base = `http://127.0.0.1:${addr.port}`;
});

afterAll(async () => {
  await new Promise<void>((resolve, reject) => {
    server.close((err) => (err ? reject(err) : resolve()));
  });
});

describe('terminal 404 / 405 handler', () => {
  it('answers a wrong method with 405 and JSON, not an HTML 404', async () => {
    const res = await fetch(`${base}/health`, { method: 'POST' });
    const text = await res.text();
    expect(res.status).toBe(405);
    expect(res.headers.get('content-type')).toContain('application/json');
    expect(text).not.toContain('<!DOCTYPE html>');
    expect(JSON.parse(text)).toEqual({ error: 'method not allowed: expected GET' });
  });

  it('lists the methods that would have worked in the Allow header', async () => {
    const res = await fetch(`${base}/observations/abc`, { method: 'PUT' });
    expect(res.status).toBe(405);
    const allow = res.headers.get('allow') ?? '';
    expect(allow.split(', ').sort()).toEqual(['DELETE', 'GET', 'PATCH']);
    expect(await res.json()).toEqual({
      error: 'method not allowed: expected DELETE, GET, PATCH',
    });
  });

  it('answers an unknown path with 404 and JSON, not an HTML page', async () => {
    const res = await fetch(`${base}/no-such-endpoint-xyz`);
    const text = await res.text();
    expect(res.status).toBe(404);
    expect(res.headers.get('content-type')).toContain('application/json');
    expect(text).not.toContain('<!DOCTYPE html>');
    expect(JSON.parse(text)).toEqual({ error: 'not found: /no-such-endpoint-xyz' });
  });

  it('leaves a working route alone', async () => {
    // The handler is terminal: it must not run for a path a route matched.
    const res = await fetch(`${base}/health`);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.status).toBe('ok');
  });

  it('answers a path that exists only for another method with 405, not 404', async () => {
    // /extraction/run is POST-only. A GET on it must be 405, because the path
    // exists — only the verb is wrong.
    const res = await fetch(`${base}/extraction/run`);
    expect(res.status).toBe(405);
    expect(res.headers.get('allow')).toBe('POST');
  });
});
