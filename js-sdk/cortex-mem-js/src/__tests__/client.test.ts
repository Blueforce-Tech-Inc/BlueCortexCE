/**
 * Unit tests for CortexMemClient.
 *
 * Uses vitest with a mock fetch to test client behavior
 * without a real backend.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CortexMemClient, APIError, isNotFound, isRateLimited, isRetryable } from '../index';

// Mock fetch
function mockFetch(status: number, body: unknown): typeof globalThis.fetch {
  return vi.fn().mockResolvedValue({
    status,
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
  });
}

describe('CortexMemClient', () => {
  let client: CortexMemClient;
  let fetchMock: ReturnType<typeof mockFetch>;

  beforeEach(() => {
    fetchMock = mockFetch(200, {});
    client = new CortexMemClient({
      baseURL: 'http://localhost:37777',
      fetch: fetchMock as unknown as typeof globalThis.fetch,
    });
  });

  describe('startSession', () => {
    it('should call POST /api/session/start', async () => {
      const response = {
        session_db_id: 'db-123',
        session_id: 'sess-1',
        prompt_number: 0,
      };
      fetchMock = mockFetch(200, response);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.startSession({
        session_id: 'sess-1',
        project_path: '/tmp/test',
      });

      expect(result.session_id).toBe('sess-1');
      expect(fetchMock).toHaveBeenCalledOnce();
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/session/start');
      expect(opts.method).toBe('POST');
      const body = JSON.parse(opts.body);
      expect(body.session_id).toBe('sess-1');
      expect(body.project_path).toBe('/tmp/test');
    });

    it('should throw on missing session_id', async () => {
      await expect(
        client.startSession({ session_id: '', project_path: '/tmp' }),
      ).rejects.toThrow('session_id is required');
    });
  });

  describe('retrieveExperiences', () => {
    it('should call POST /api/memory/experiences', async () => {
      const experiences = [
        { id: 'e1', task: 't1', strategy: 's1', outcome: 'o1', reuseCondition: '', qualityScore: 0.9 },
      ];
      fetchMock = mockFetch(200, experiences);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.retrieveExperiences({ task: 'test', project: '/tmp' });
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('e1');
    });
  });

  describe('search', () => {
    it('should call GET /api/search with query params', async () => {
      const searchResult = { observations: [], strategy: 'hybrid', fell_back: false, count: 0 };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.search({ project: '/tmp', query: 'test', limit: 5 });
      expect(result.strategy).toBe('hybrid');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/search');
      expect(url).toContain('project=%2Ftmp');
      expect(url).toContain('query=test');
      expect(url).toContain('limit=5');
    });
  });

  describe('error handling', () => {
    it('should throw APIError on 4xx', async () => {
      fetchMock = mockFetch(404, { error: 'not found' });
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      try {
        await client.healthCheck();
        expect.unreachable('should have thrown');
      } catch (err) {
        expect(err).toBeInstanceOf(APIError);
        expect((err as APIError).statusCode).toBe(404);
        expect(isNotFound(err)).toBe(true);
      }
    });

    it('should detect rate limiting', async () => {
      fetchMock = mockFetch(429, { error: 'rate limited' });
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      try {
        await client.healthCheck();
      } catch (err) {
        expect(isRateLimited(err)).toBe(true);
        expect(isRetryable(err)).toBe(true);
      }
    });
  });

  describe('fire-and-forget', () => {
    it('should not throw on recordObservation failure', async () => {
      fetchMock = mockFetch(500, { error: 'internal' });
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
        maxRetries: 1,
      });

      // Should not throw
      await client.recordObservation({
        session_id: 'sess-1',
        cwd: '/tmp',
        tool_name: 'Read',
      });
    });
  });

  describe('close', () => {
    it('should throw after close', async () => {
      client.close();
      await expect(client.healthCheck()).rejects.toThrow('client is closed');
    });
  });

  describe('getObservationsByIds', () => {
    it('should call POST /api/observations/batch', async () => {
      const resp = { observations: [{ id: 'obs-1', sessionId: 's1', projectPath: '/p', type: 'tool', content: 'c' }], count: 1 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.getObservationsByIds(['obs-1']);
      expect(result.count).toBe(1);
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/observations/batch');
      const body = JSON.parse(opts.body);
      expect(body.ids).toEqual(['obs-1']);
    });

    it('should reject empty ids', async () => {
      await expect(client.getObservationsByIds([])).rejects.toThrow('ids must not be empty');
    });

    it('should reject > 100 ids', async () => {
      const ids = Array.from({ length: 101 }, (_, i) => String(i));
      await expect(client.getObservationsByIds(ids)).rejects.toThrow('exceeds maximum of 100');
    });
  });

  describe('triggerRefinement', () => {
    it('should call POST /api/memory/refine with project query param', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      await client.triggerRefinement('/tmp/project');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/refine');
      expect(url).toContain('project=%2Ftmp%2Fproject');
    });
  });

  describe('getLatestExtraction', () => {
    it('should call GET /api/extraction/{template}/latest', async () => {
      const resp = { sessionId: 's1', extractedData: { key: 'val' }, createdAt: 0, observationId: 'o1' };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.getLatestExtraction('/tmp', 'user_preference', 'alice');
      expect(result.sessionId).toBe('s1');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/extraction/user_preference/latest');
      expect(url).toContain('projectPath=%2Ftmp');
      expect(url).toContain('userId=alice');
    });
  });
});
