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

  // ==================== Session ====================

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

  describe('updateSessionUserId', () => {
    it('should call PATCH /api/session/{id}/user', async () => {
      fetchMock = mockFetch(200, { status: 'ok', sessionId: 'sess-1', userId: 'alice' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.updateSessionUserId('sess-1', 'alice');
      expect(result.userId).toBe('alice');
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/session/sess-1/user');
      expect(opts.method).toBe('PATCH');
      const body = JSON.parse(opts.body);
      expect(body.user_id).toBe('alice');
    });

    it('should URL-encode sessionId', async () => {
      fetchMock = mockFetch(200, { status: 'ok', sessionId: 's/1', userId: 'alice' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.updateSessionUserId('s/1', 'alice');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/session/s%2F1/user');
    });
  });

  // ==================== Capture (fire-and-forget) ====================

  describe('recordObservation', () => {
    it('should call POST /api/ingest/tool-use', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.recordObservation({ session_id: 'sess-1', cwd: '/tmp', tool_name: 'Read' });
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/ingest/tool-use');
      expect(opts.method).toBe('POST');
    });

    it('should throw on missing session_id', async () => {
      await expect(
        client.recordObservation({ session_id: '', cwd: '/tmp', tool_name: 'Read' }),
      ).rejects.toThrow('session_id is required');
    });
  });

  describe('recordSessionEnd', () => {
    it('should call POST /api/ingest/session-end', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.recordSessionEnd({ session_id: 'sess-1', cwd: '/tmp' });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/ingest/session-end');
    });
  });

  describe('recordUserPrompt', () => {
    it('should call POST /api/ingest/user-prompt', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.recordUserPrompt({ session_id: 'sess-1', prompt_text: 'hello', cwd: '/tmp' });
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/ingest/user-prompt');
      const body = JSON.parse(opts.body);
      expect(body.prompt_text).toBe('hello');
    });

    it('should throw on missing prompt_text', async () => {
      await expect(
        client.recordUserPrompt({ session_id: 's1', prompt_text: '', cwd: '/tmp' }),
      ).rejects.toThrow('prompt_text is required');
    });
  });

  // ==================== Retrieval ====================

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

  describe('buildICLPrompt', () => {
    it('should call POST /api/memory/icl-prompt', async () => {
      const resp = { prompt: 'test prompt', experienceCount: 3, maxChars: 2000 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.buildICLPrompt({ task: 'test task', project: '/tmp' });
      expect(result.prompt).toBe('test prompt');
      expect(result.experienceCount).toBe(3);
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/icl-prompt');
      expect(opts.method).toBe('POST');
    });

    it('should throw on missing task', async () => {
      await expect(client.buildICLPrompt({ task: '' })).rejects.toThrow('task is required');
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

    it('should pass source filter parameter', async () => {
      const searchResult = { observations: [], strategy: 'hybrid', fell_back: false, count: 0 };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      await client.search({ project: '/tmp', query: 'test', source: 'manual' });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('source=manual');
    });

    it('should pass concept filter parameter', async () => {
      const searchResult = { observations: [], strategy: 'hybrid', fell_back: false, count: 0 };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      await client.search({ project: '/tmp', query: 'test', concept: 'error-handling' });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('concept=error-handling');
    });

    it('should throw on missing project', async () => {
      await expect(client.search({ project: '' })).rejects.toThrow('project is required');
    });
  });

  describe('listObservations', () => {
    it('should call GET /api/observations with params', async () => {
      const resp = { items: [], hasMore: false, offset: 0, limit: 20 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.listObservations({ project: '/tmp', limit: 20 });
      expect(result.hasMore).toBe(false);
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/observations');
      expect(url).toContain('project=%2Ftmp');
      expect(url).toContain('limit=20');
    });

    it('should skip zero offset', async () => {
      const resp = { items: [], hasMore: false, offset: 0, limit: 20 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.listObservations({ offset: 0, limit: 10 });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).not.toContain('offset=');
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

    it('should reject empty string in ids', async () => {
      await expect(client.getObservationsByIds(['valid', ''])).rejects.toThrow('ids[1] is empty');
    });
  });

  // ==================== Management ====================

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

  describe('submitFeedback', () => {
    it('should call POST /api/memory/feedback', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.submitFeedback({ observationId: 'obs-1', feedbackType: 'positive', comment: 'good' });
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/feedback');
      const body = JSON.parse(opts.body);
      expect(body.observationId).toBe('obs-1');
      expect(body.feedbackType).toBe('positive');
      expect(body.comment).toBe('good');
    });

    it('should omit comment when not provided', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.submitFeedback({ observationId: 'obs-1', feedbackType: 'positive' });
      const [, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      const body = JSON.parse(opts.body);
      expect(body.comment).toBeUndefined();
    });
  });

  describe('updateObservation', () => {
    it('should call PATCH /api/memory/observations/{id}', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.updateObservation('obs-1', { title: 'Updated', content: 'New content' });
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/observations/obs-1');
      expect(opts.method).toBe('PATCH');
      const body = JSON.parse(opts.body);
      expect(body.title).toBe('Updated');
    });

    it('should URL-encode observationId', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.updateObservation('obs/1', { title: 't' });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/observations/obs%2F1');
    });
  });

  describe('deleteObservation', () => {
    it('should call DELETE /api/memory/observations/{id}', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.deleteObservation('obs-1');
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/observations/obs-1');
      expect(opts.method).toBe('DELETE');
    });
  });

  describe('getQualityDistribution', () => {
    it('should call GET /api/memory/quality-distribution', async () => {
      const resp = { project: '/tmp', high: 10, medium: 5, low: 2, unknown: 1 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.getQualityDistribution('/tmp');
      expect(result.high).toBe(10);
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/quality-distribution');
      expect(url).toContain('project=%2Ftmp');
    });
  });

  // ==================== Health ====================

  describe('healthCheck', () => {
    it('should return health on 200 ok', async () => {
      fetchMock = mockFetch(200, { status: 'ok', service: 'cortex-mem' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.healthCheck();
      expect(result.status).toBe('ok');
    });

    it('should throw on unhealthy response', async () => {
      fetchMock = mockFetch(200, { status: 'degraded' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await expect(client.healthCheck()).rejects.toThrow('Unhealthy');
    });
  });

  // ==================== Extraction ====================

  describe('triggerExtraction', () => {
    it('should call POST /api/extraction/run', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.triggerExtraction('/tmp');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/extraction/run');
      expect(url).toContain('projectPath=%2Ftmp');
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

    it('should work without userId', async () => {
      const resp = { sessionId: 's1', extractedData: {}, createdAt: 0, observationId: 'o1' };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.getLatestExtraction('/tmp', 'user_preference');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).not.toContain('userId=');
    });
  });

  describe('getExtractionHistory', () => {
    it('should call GET /api/extraction/{template}/history', async () => {
      const resp = [{ sessionId: 's1', extractedData: {}, createdAt: 0, observationId: 'o1' }];
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.getExtractionHistory('/tmp', 'user_pref', 'alice', 5);
      expect(result).toHaveLength(1);
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/extraction/user_pref/history');
      expect(url).toContain('limit=5');
    });
  });

  // ==================== Version ====================

  describe('getVersion', () => {
    it('should call GET /api/version', async () => {
      const resp = { version: '1.0.0', service: 'cortex-mem', java: '21', springBoot: '3.3.0' };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.getVersion();
      expect(result.version).toBe('1.0.0');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/version');
    });
  });

  // ==================== P1 Management ====================

  describe('getProjects', () => {
    it('should call GET /api/projects', async () => {
      const resp = { projects: ['/tmp/proj1', '/tmp/proj2'] };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.getProjects();
      expect(result.projects).toHaveLength(2);
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/projects');
    });
  });

  describe('getStats', () => {
    it('should call GET /api/stats with optional project', async () => {
      const resp = {
        worker: { isProcessing: false, queueDepth: 0 },
        database: { totalObservations: 100, totalSummaries: 5, totalSessions: 10, totalProjects: 2 },
      };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.getStats('/tmp');
      expect(result.database.totalObservations).toBe(100);
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/stats');
      expect(url).toContain('project=%2Ftmp');
    });

    it('should work without project', async () => {
      const resp = {
        worker: { isProcessing: false, queueDepth: 0 },
        database: { totalObservations: 0, totalSummaries: 0, totalSessions: 0, totalProjects: 0 },
      };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.getStats();
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).not.toContain('project=');
    });
  });

  describe('getModes', () => {
    it('should call GET /api/modes', async () => {
      const resp = { id: 'full', name: 'Full', description: 'Full mode', version: '1.0', observationTypes: [], observationConcepts: [] };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.getModes();
      expect(result.id).toBe('full');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/modes');
    });
  });

  describe('getSettings', () => {
    it('should call GET /api/settings', async () => {
      const resp = { debug: false, logLevel: 'info' };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.getSettings();
      expect(result.debug).toBe(false);
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/settings');
    });
  });

  // ==================== Error handling ====================

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

    it('should extract error from message field', async () => {
      fetchMock = mockFetch(500, { message: 'db error' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      try {
        await client.healthCheck();
        expect.unreachable('should have thrown');
      } catch (err) {
        expect((err as APIError).message).toContain('db error');
      }
    });

    it('should detect 5xx as server error', async () => {
      fetchMock = mockFetch(503, { error: 'down' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      try {
        await client.healthCheck();
      } catch (err) {
        expect(err).toBeInstanceOf(APIError);
        expect(isRetryable(err)).toBe(true);
      }
    });

    it('should treat timeout AbortError (DOMException) as retryable', () => {
      const abortErr = new DOMException('The operation was aborted.', 'AbortError');
      expect(isRetryable(abortErr)).toBe(true);
    });

    it('should treat AbortError by name as retryable (Node.js compat)', () => {
      const abortErr = new Error('The operation was aborted.');
      abortErr.name = 'AbortError';
      expect(isRetryable(abortErr)).toBe(true);
    });

    it('should treat TypeError (network error) as retryable', () => {
      const netErr = new TypeError('Failed to fetch');
      expect(isRetryable(netErr)).toBe(true);
    });

    it('should not treat generic Error as retryable', () => {
      const genericErr = new Error('something went wrong');
      expect(isRetryable(genericErr)).toBe(false);
    });
  });

  // ==================== Fire-and-forget ====================

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

    it('should swallow non-retryable errors immediately', async () => {
      fetchMock = mockFetch(400, { error: 'bad request' });
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
        maxRetries: 3,
      });

      // Should not throw and should not retry
      await client.recordObservation({
        session_id: 'sess-1',
        cwd: '/tmp',
        tool_name: 'Read',
      });
      // Only 1 call (no retries for 4xx)
      expect(fetchMock).toHaveBeenCalledOnce();
    });

    it('should retry on timeout AbortError', async () => {
      let callCount = 0;
      const abortFetch = vi.fn().mockImplementation(() => {
        callCount++;
        if (callCount < 3) {
          return Promise.reject(new DOMException('The operation was aborted.', 'AbortError'));
        }
        return Promise.resolve({
          status: 204,
          body: null,
          text() { return Promise.resolve(''); },
        });
      });

      client = new CortexMemClient({
        fetch: abortFetch as unknown as typeof globalThis.fetch,
        maxRetries: 3,
        retryBackoff: 1, // minimal backoff for fast test
      });

      await client.recordObservation({
        session_id: 'sess-1',
        cwd: '/tmp',
        tool_name: 'Read',
      });
      expect(callCount).toBe(3);
    });
  });

  // ==================== Lifecycle ====================

  describe('close', () => {
    it('should throw after close', async () => {
      client.close();
      await expect(client.healthCheck()).rejects.toThrow('client is closed');
    });

    it('should throw on all methods after close', async () => {
      client.close();
      await expect(client.getVersion()).rejects.toThrow('client is closed');
      await expect(client.getProjects()).rejects.toThrow('client is closed');
      await expect(client.getStats()).rejects.toThrow('client is closed');
      await expect(client.getModes()).rejects.toThrow('client is closed');
      await expect(client.getSettings()).rejects.toThrow('client is closed');
    });
  });

  // ==================== URL building ====================

  describe('Observation DTO fields', () => {
    it('should parse promptNumber and createdAtEpoch from response', async () => {
      const resp = {
        items: [{
          id: 'o1', sessionId: 's1', projectPath: '/p', type: 'tool',
          content: 'c', promptNumber: 42, createdAtEpoch: 1711488000,
        }],
        hasMore: false, offset: 0, limit: 20,
      };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.listObservations({ project: '/tmp', limit: 20 });
      expect(result.items[0].promptNumber).toBe(42);
      expect(result.items[0].createdAtEpoch).toBe(1711488000);
    });
  });

  describe('URL building', () => {
    it('should strip trailing slash from baseURL', async () => {
      fetchMock = mockFetch(200, { status: 'ok', service: 'test' });
      client = new CortexMemClient({
        baseURL: 'http://localhost:37777///',
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      await client.healthCheck();
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toBe('http://localhost:37777/api/health');
    });
  });
});
