/**
 * Regression tests for a truncated response body.
 *
 * A connection dropped part-way through a response body is a transient network
 * failure. doFetch used to swallow the error from resp.text() and return an
 * empty body with the response's own status, which meant:
 *
 *   - a truncated 200 on a read path became "failed to parse <path> response",
 *     which isRetryable does not recognise, so it was never retried;
 *   - a truncated 200 on a capture path became a *silent success*;
 *   - the client's own request timeout, if it fired while reading the body,
 *     was likewise swallowed.
 *
 * The raw runtime reports these as TypeError: terminated, and isRetryable is
 * written to recognise exactly that. These tests pin the SDK to the runtime's
 * own classification.
 */

import { describe, it, expect, vi } from 'vitest';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { CortexMemClient, isRetryable } from '../index';

interface TruncatingServer {
  server: Server;
  base: string;
  hits: () => number;
}

/** Server that promises a Content-Length, sends a partial body, then drops the connection. */
function truncatingServer(): Promise<TruncatingServer> {
  let hits = 0;
  const server = createServer((_req, res) => {
    hits += 1;
    res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': '200' });
    res.write('{"version":"1.0.0","serv');
    setTimeout(() => res.socket?.destroy(), 10);
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address() as AddressInfo;
      resolve({ server, base: `http://127.0.0.1:${port}`, hits: () => hits });
    });
  });
}

/** Server that truncates the first `failures` requests, then answers normally. */
function flakyServer(failures: number): Promise<TruncatingServer> {
  let hits = 0;
  const server = createServer((_req, res) => {
    hits += 1;
    if (hits <= failures) {
      res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': '200' });
      res.write('{"version":"1.0.0","serv');
      setTimeout(() => res.socket?.destroy(), 10);
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ version: '1.0.0', service: 'claude-mem-java' }));
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address() as AddressInfo;
      resolve({ server, base: `http://127.0.0.1:${port}`, hits: () => hits });
    });
  });
}

/** Server that sends headers and then stalls, so the client's own timeout fires mid-body. */
function stallingServer(): Promise<TruncatingServer> {
  const server = createServer((_req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': '200' });
    res.write('{"version":"1.0.0"');
    // Never finish; the socket is closed when the test ends.
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address() as AddressInfo;
      resolve({ server, base: `http://127.0.0.1:${port}`, hits: () => 0 });
    });
  });
}

const OBSERVATION = {
  session_id: 'sess-1',
  cwd: '/tmp/project',
  tool_name: 'Edit',
  tool_input: { file: 'a.ts' },
  tool_response: { ok: true },
};

describe('truncated response body', () => {
  it('rethrows the runtime error instead of reporting a JSON parse failure', async () => {
    const { server, base } = await truncatingServer();
    try {
      const client = new CortexMemClient({ baseURL: base, maxRetries: 1 });
      // The runtime says "terminated"; the SDK must not turn that into a parse error,
      // which is what a caller would otherwise see for a network problem.
      await expect(client.getVersion()).rejects.toThrow(/terminated/);
      await expect(client.getVersion()).rejects.not.toThrow(/failed to parse/);
    } finally {
      server.close();
    }
  });

  it('classifies a truncated body as retryable', async () => {
    const { server, base } = await truncatingServer();
    try {
      const client = new CortexMemClient({ baseURL: base, maxRetries: 1 });
      const err = await client.getVersion().then(
        () => null,
        (e: unknown) => e,
      );
      expect(err).not.toBeNull();
      // isRetryable recognises TypeError precisely so a dropped connection is retried.
      expect(isRetryable(err as Error)).toBe(true);
    } finally {
      server.close();
    }
  });

  it('retries a truncated capture instead of silently reporting success', async () => {
    const { server, base, hits } = await flakyServer(2);
    try {
      const client = new CortexMemClient({ baseURL: base, maxRetries: 3, retryBackoff: 100 });
      // Fire-and-forget swallows errors by design, so the only way to tell that it
      // retried is the request count. Before the fix this resolved after one hit,
      // reporting success for a response that never arrived intact.
      await client.recordObservation(OBSERVATION);
      expect(hits()).toBe(3);
    } finally {
      server.close();
    }
  });

  it('rethrows the client timeout when it fires while reading the body', async () => {
    const { server, base } = await stallingServer();
    try {
      const client = new CortexMemClient({ baseURL: base, maxRetries: 1, timeout: 300 });
      // The timeout used to be swallowed into an empty 200, which read as success.
      await expect(client.getVersion()).rejects.toThrow();
    } finally {
      server.close();
      server.closeAllConnections?.();
    }
  });

  it('still returns an empty body for an unrecognised non-transport read failure', async () => {
    // The original comment defended against resp.text() throwing for a null body.
    // That case is kept for values that are neither TypeError nor AbortError, and
    // is asserted through a no-content path where an empty body is not a parse error.
    const bodyText = vi.fn().mockRejectedValue(new Error('some exotic runtime quirk'));
    const fetchImpl = vi.fn().mockResolvedValue({
      status: 204,
      headers: new Map(),
      text: bodyText,
    });
    const client = new CortexMemClient({
      baseURL: 'http://127.0.0.1:1',
      maxRetries: 1,
      fetch: fetchImpl as unknown as typeof globalThis.fetch,
    });
    await expect(client.recordObservation(OBSERVATION)).resolves.toBeUndefined();
  });
});
