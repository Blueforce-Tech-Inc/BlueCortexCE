/**
 * Unit tests for CortexMemClient.
 *
 * Uses vitest with a mock fetch to test client behavior
 * without a real backend.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CortexMemClient, APIError, ValidationError, isValidationError, isNotFound, isRateLimited, isRetryable, isForbidden, isUnprocessable, isConflict, isBadRequest, isUnauthorized, isClientError, isServerError, isBadGateway, isServiceUnavailable, isGatewayTimeout, parseObservation, parseExperience, parseExtractionResult, parseICLPromptResult, parseStatsResponse, parseWorkerStats, parseDatabaseStats, parseVersionResponse, parseQualityDistribution } from '../index';

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
      ).rejects.toThrow('session_id');
    });

    it('should reject whitespace-only session_id', async () => {
      await expect(
        client.startSession({ session_id: '   ', project_path: '/tmp' }),
      ).rejects.toThrow('session_id');
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

    it('should throw on missing userId', async () => {
      await expect(client.updateSessionUserId('sess-1', '')).rejects.toThrow('userId');
    });

    it('should throw on whitespace-only userId', async () => {
      await expect(client.updateSessionUserId('sess-1', '   ')).rejects.toThrow('userId');
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
      ).rejects.toThrow('session_id');
    });

    it('should reject whitespace-only session_id', async () => {
      await expect(
        client.recordObservation({ session_id: '   ', cwd: '/tmp', tool_name: 'Read' }),
      ).rejects.toThrow('session_id');
    });

    it('should throw on missing cwd', async () => {
      await expect(
        client.recordObservation({ session_id: 'sess-1', cwd: '', tool_name: 'Read' }),
      ).rejects.toThrow('cwd');
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

    it('should throw on missing session_id', async () => {
      await expect(
        client.recordSessionEnd({ session_id: '', cwd: '/tmp' }),
      ).rejects.toThrow('session_id');
    });

    it('should throw on missing cwd', async () => {
      await expect(
        client.recordSessionEnd({ session_id: 'sess-1', cwd: '' }),
      ).rejects.toThrow('cwd');
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
      ).rejects.toThrow('prompt_text');
    });
  });

  // ==================== Retrieval ====================

  describe('retrieveExperiences', () => {
    it('should call POST /api/memory/experiences', async () => {
      // Wire format uses SNAKE_CASE (backend Jackson naming strategy)
      const experiences = [
        { id: 'e1', task: 't1', strategy: 's1', outcome: 'o1', reuse_condition: 'when X', quality_score: 0.9, created_at: '2026-01-01' },
      ];
      fetchMock = mockFetch(200, experiences);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.retrieveExperiences({ task: 'test', project: '/tmp' });
      expect(result).toHaveLength(1);
      expect(result[0].id).toBe('e1');
      // Verify wire → canonical field mapping
      expect(result[0].qualityScore).toBe(0.9);
      expect(result[0].reuseCondition).toBe('when X');
      expect(result[0].createdAt).toBe('2026-01-01');
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
      await expect(client.buildICLPrompt({ task: '' })).rejects.toThrow('task');
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

    it('should pass type filter parameter', async () => {
      const searchResult = { observations: [], strategy: 'hybrid', fell_back: false, count: 0 };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      await client.search({ project: '/tmp', query: 'test', type: 'feature' });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('type=feature');
    });

    it('should pass offset parameter', async () => {
      const searchResult = { observations: [], strategy: 'hybrid', fell_back: false, count: 0 };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      await client.search({ project: '/tmp', query: 'test', offset: 10 });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('offset=10');
    });

    it('should skip zero offset', async () => {
      const searchResult = { observations: [], strategy: 'hybrid', fell_back: false, count: 0 };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      await client.search({ project: '/tmp', query: 'test', offset: 0 });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).not.toContain('offset=');
    });

    it('should throw on missing project', async () => {
      await expect(client.search({ project: '' })).rejects.toThrow('project');
    });

    it('should remap fell_back wire field to fellBack', async () => {
      const searchResult = { observations: [], strategy: 'keyword', fell_back: true, count: 0 };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.search({ project: '/tmp', query: 'test' });
      expect(result.fellBack).toBe(true);
    });

    it('should default fellBack to false when missing', async () => {
      const searchResult = { observations: [], strategy: 'hybrid', count: 0 };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.search({ project: '/tmp', query: 'test' });
      expect(result.fellBack).toBe(false);
    });

    it('should remap observation fields from wire format', async () => {
      // Wire format: content_session_id, project, narrative (NOT sessionId, projectPath, content)
      const searchResult = {
        observations: [{ id: 'o1', content_session_id: 's1', project: '/p', narrative: 'test content' }],
        strategy: 'hybrid', fell_back: false, count: 1,
      };
      fetchMock = mockFetch(200, searchResult);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.search({ project: '/tmp', query: 'test' });
      expect(result.observations[0].sessionId).toBe('s1');
      expect(result.observations[0].projectPath).toBe('/p');
      expect(result.observations[0].content).toBe('test content');
    });
  });

  describe('listObservations', () => {
    it('should call GET /api/observations with params', async () => {
      const resp = { items: [], has_more: false, offset: 0, limit: 20 };
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
      const resp = { items: [], has_more: false, offset: 0, limit: 20 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.listObservations({ offset: 0, limit: 10 });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).not.toContain('offset=');
    });

    it('should handle hasMore camelCase from real backend', async () => {
      // Real backend returns "hasMore" (camelCase) via Map.of()
      const resp = { items: [{ id: 'o1', narrative: 'test', content_session_id: 's1' }], hasMore: true, total: 50, offset: 0, limit: 20 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.listObservations({ project: '/tmp' });
      expect(result.hasMore).toBe(true);
      expect(result.total).toBe(50);
      expect(result.items).toHaveLength(1);
    });
  });

  describe('getObservationsByIds', () => {
    it('should call POST /api/observations/batch', async () => {
      // Wire format: content_session_id, project, narrative (NOT sessionId, projectPath, content)
      const resp = { observations: [{ id: 'obs-1', content_session_id: 's1', project: '/p', type: 'tool', narrative: 'c' }], count: 1 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.getObservationsByIds(['obs-1']);
      expect(result.count).toBe(1);
      // Verify field remapping from wire format to canonical types
      expect(result.observations[0].sessionId).toBe('s1');
      expect(result.observations[0].projectPath).toBe('/p');
      expect(result.observations[0].content).toBe('c');
      const [url, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/observations/batch');
      const body = JSON.parse(opts.body);
      expect(body.ids).toEqual(['obs-1']);
    });

    it('should reject empty ids', async () => {
      await expect(client.getObservationsByIds([])).rejects.toThrow('ids');
    });

    it('should reject > 100 ids', async () => {
      const ids = Array.from({ length: 101 }, (_, i) => String(i));
      await expect(client.getObservationsByIds(ids)).rejects.toThrow('exceeds maximum of 100');
    });

    it('should reject empty string in ids', async () => {
      await expect(client.getObservationsByIds(['valid', ''])).rejects.toThrow('ids[1]');
    });

    it('should reject whitespace-only string in ids', async () => {
      await expect(client.getObservationsByIds(['valid', '   '])).rejects.toThrow('ids[1]');
    });
  });

  describe('getObservation', () => {
    it('should return observation when found', async () => {
      const resp = { observations: [{ id: 'obs-1', content_session_id: 's1', project: '/p', type: 'tool', narrative: 'content' }], count: 1 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.getObservation('obs-1');
      expect(result).not.toBeNull();
      expect(result!.id).toBe('obs-1');
      expect(result!.content).toBe('content');
    });

    it('should return null when not found', async () => {
      const resp = { observations: [], count: 0 };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({
        fetch: fetchMock as unknown as typeof globalThis.fetch,
      });

      const result = await client.getObservation('nonexistent');
      expect(result).toBeNull();
    });

    it('should throw on missing id', async () => {
      await expect(client.getObservation('')).rejects.toThrow('id');
    });

    it('should throw on whitespace-only id', async () => {
      await expect(client.getObservation('   ')).rejects.toThrow('id');
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
      expect(body.content).toBe('New content');
    });

    it('should send narrative field (backend alias for content)', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.updateObservation('obs-1', { narrative: 'Updated narrative' });
      const [, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      const body = JSON.parse(opts.body);
      expect(body.narrative).toBe('Updated narrative');
    });

    it('should send extractedData in update', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.updateObservation('obs-1', { source: 'manual', extractedData: { key: 'val' } });
      const [, opts] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      const body = JSON.parse(opts.body);
      expect(body.source).toBe('manual');
      expect(body.extractedData).toEqual({ key: 'val' });
    });

    it('should URL-encode observationId', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.updateObservation('obs/1', { title: 't' });
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/observations/obs%2F1');
    });

    it('should reject empty update', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await expect(client.updateObservation('obs-1', {})).rejects.toThrow('at least one field');
      // Should NOT make HTTP call
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('should reject update with all-undefined fields', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await expect(
        client.updateObservation('obs-1', { title: undefined, content: undefined, source: undefined }),
      ).rejects.toThrow('at least one field');
      expect(fetchMock).not.toHaveBeenCalled();
    });

    it('should reject update with all-null fields', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await expect(
        client.updateObservation('obs-1', { title: null as unknown as string, content: null as unknown as string }),
      ).rejects.toThrow('at least one field');
      expect(fetchMock).not.toHaveBeenCalled();
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

    it('should URL-encode observationId', async () => {
      fetchMock = mockFetch(204, null);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      await client.deleteObservation('obs/with/slash');
      const [url] = (fetchMock as ReturnType<typeof vi.fn>).mock.calls[0];
      expect(url).toContain('/api/memory/observations/obs%2Fwith%2Fslash');
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

    it('should reject negative limit', async () => {
      await expect(client.getExtractionHistory('/tmp', 't', undefined, -1)).rejects.toThrow('limit');
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
      const resp = { id: 'full', name: 'Full', description: 'Full mode', version: '1.0', observation_types: [], observation_concepts: [] };
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

    it('should detect 400 Bad Request', async () => {
      fetchMock = mockFetch(400, { error: 'bad request' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });
      try { await client.healthCheck(); } catch (err) {
        expect(isBadRequest(err)).toBe(true);
        expect(isClientError(err)).toBe(true);
        expect(isServerError(err)).toBe(false);
      }
    });

    it('should detect 401 Unauthorized', async () => {
      fetchMock = mockFetch(401, { error: 'unauthorized' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });
      try { await client.healthCheck(); } catch (err) {
        expect(isUnauthorized(err)).toBe(true);
        expect(isRetryable(err)).toBe(false);
      }
    });

    it('should detect 403 Forbidden', async () => {
      fetchMock = mockFetch(403, { error: 'forbidden' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });
      try { await client.healthCheck(); } catch (err) {
        expect(isForbidden(err)).toBe(true);
      }
    });

    it('should detect 409 Conflict', async () => {
      fetchMock = mockFetch(409, { error: 'conflict' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });
      try { await client.healthCheck(); } catch (err) {
        expect(isConflict(err)).toBe(true);
      }
    });

    it('should detect 422 Unprocessable Entity', async () => {
      fetchMock = mockFetch(422, { error: 'unprocessable' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });
      try { await client.healthCheck(); } catch (err) {
        expect(isUnprocessable(err)).toBe(true);
      }
    });

    it('should extract error from detail field', async () => {
      fetchMock = mockFetch(500, { detail: 'db timeout' });
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });
      try { await client.healthCheck(); } catch (err) {
        expect((err as APIError).message).toContain('db timeout');
      }
    });

    it('should extract error from plain JSON string body', async () => {
      // Some backends return a plain JSON string: "not found" instead of {"error":"not found"}
      const stringFetch = vi.fn().mockResolvedValue({
        status: 404,
        body: null,
        text() { return Promise.resolve('"not found"'); },
      });
      const c = new CortexMemClient({ fetch: stringFetch as unknown as typeof globalThis.fetch });
      try { await c.healthCheck(); } catch (err) {
        expect(err).toBeInstanceOf(APIError);
        expect((err as APIError).statusCode).toBe(404);
        // Should contain the string directly, not JSON.stringify('"not found"')
        expect((err as APIError).message).toContain('not found');
        expect((err as APIError).message).not.toContain('"not found"');
      }
    });

    it('should handle non-string primitive JSON error body', async () => {
      // Edge case: backend returns a number or boolean as error
      const numFetch = vi.fn().mockResolvedValue({
        status: 500,
        body: null,
        text() { return Promise.resolve('42'); },
      });
      const c = new CortexMemClient({ fetch: numFetch as unknown as typeof globalThis.fetch });
      try { await c.healthCheck(); } catch (err) {
        expect(err).toBeInstanceOf(APIError);
        // Number is not a string, so falls through to JSON.stringify
        expect((err as APIError).message).toContain('42');
      }
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

    it('should show closed in toString', () => {
      expect(String(client)).toContain('open');
      client.close();
      expect(String(client)).toContain('closed');
    });

    it('should include base URL in toString', () => {
      expect(String(client)).toContain('http://localhost:37777');
    });
  });

  // ==================== URL building ====================

  describe('Observation DTO fields', () => {
    it('should parse wire format fields into canonical types', async () => {
      // Wire format from backend: content_session_id, project, narrative, quality_score, prompt_number, created_at_epoch
      const resp = {
        items: [{
          id: 'o1', content_session_id: 's1', project: '/p', type: 'tool',
          narrative: 'c', quality_score: 0.85, prompt_number: 42, created_at_epoch: 1711488000,
        }],
        has_more: false, offset: 0, limit: 20,
      };
      fetchMock = mockFetch(200, resp);
      client = new CortexMemClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });

      const result = await client.listObservations({ project: '/tmp', limit: 20 });
      const obs = result.items[0];
      // Verify all wire→canonical field mappings
      expect(obs.sessionId).toBe('s1');
      expect(obs.projectPath).toBe('/p');
      expect(obs.content).toBe('c');
      expect(obs.qualityScore).toBe(0.85);
      expect(obs.promptNumber).toBe(42);
      expect(obs.createdAtEpoch).toBe(1711488000);
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

// ==================== parseExperience ====================

describe('parseExperience', () => {
  it('should remap all wire format fields', () => {
    const raw = {
      id: 'e1',
      task: 'build auth',
      strategy: 'use JWT',
      outcome: 'success',
      reuse_condition: 'similar auth tasks',
      quality_score: 0.95,
      created_at: '2026-01-01T00:00:00Z',
    };

    const exp = parseExperience(raw);
    expect(exp.id).toBe('e1');
    expect(exp.reuseCondition).toBe('similar auth tasks');
    expect(exp.qualityScore).toBe(0.95);
    expect(exp.createdAt).toBe('2026-01-01T00:00:00Z');
  });

  it('should handle missing optional fields', () => {
    const exp = parseExperience({ id: 'e1', task: 't', strategy: 's', outcome: 'o' });
    expect(exp.reuseCondition).toBe('');
    expect(exp.qualityScore).toBe(0);
    expect(exp.createdAt).toBeUndefined();
  });

  it('should handle null wire fields gracefully', () => {
    const raw = {
      id: null,
      task: null,
      strategy: null,
      outcome: null,
      reuse_condition: null,
      quality_score: null,
      created_at: null,
    };

    const exp = parseExperience(raw);
    expect(exp.id).toBe('');
    expect(exp.task).toBe('');
    expect(exp.strategy).toBe('');
    expect(exp.outcome).toBe('');
    expect(exp.reuseCondition).toBe('');
    expect(exp.qualityScore).toBe(0);
    expect(exp.createdAt).toBeUndefined();
  });

  it('should handle type mismatches in wire fields', () => {
    const raw = {
      id: 42,
      quality_score: '0.75',
    };

    const exp = parseExperience(raw);
    expect(exp.id).toBe('42');
    expect(exp.qualityScore).toBe(0.75);
  });
});

// ==================== parseObservation ====================

describe('parseObservation', () => {
  it('should remap all wire format fields', () => {
    const raw = {
      id: 'o1',
      content_session_id: 'sess-1',
      project: '/my/project',
      type: 'feature',
      title: 'Title',
      subtitle: 'Sub',
      narrative: 'the content',
      facts: ['f1', 'f2'],
      concepts: ['c1'],
      quality_score: 0.95,
      feedback_type: 'SUCCESS',
      source: 'manual',
      extractedData: { key: 'val' },
      prompt_number: 7,
      created_at: '2026-01-01T00:00:00Z',
      created_at_epoch: 1700000000,
    };

    const obs = parseObservation(raw);
    expect(obs.id).toBe('o1');
    expect(obs.sessionId).toBe('sess-1');
    expect(obs.projectPath).toBe('/my/project');
    expect(obs.content).toBe('the content');
    expect(obs.qualityScore).toBe(0.95);
    expect(obs.promptNumber).toBe(7);
    expect(obs.createdAtEpoch).toBe(1700000000);
    expect(obs.extractedData).toEqual({ key: 'val' });
    expect(obs.feedbackType).toBe('SUCCESS');
    // Verify feedback_type wire → canonical field mapping
    expect(obs.feedbackType).toBe(raw.feedback_type);
  });

  it('should handle missing optional fields', () => {
    const obs = parseObservation({ id: 'o1' });
    expect(obs.id).toBe('o1');
    expect(obs.sessionId).toBe('');
    expect(obs.projectPath).toBe('');
    expect(obs.content).toBe('');
    expect(obs.qualityScore).toBeUndefined();
    expect(obs.feedbackType).toBeUndefined();
    expect(obs.promptNumber).toBeUndefined();
  });

  it('should handle null wire fields gracefully', () => {
    const raw = {
      id: null,
      content_session_id: null,
      project: null,
      type: null,
      title: null,
      subtitle: null,
      narrative: null,
      facts: null,
      concepts: null,
      quality_score: null,
      feedback_type: null,
      source: null,
      extractedData: null,
      prompt_number: null,
      created_at: null,
      created_at_epoch: null,
    };

    const obs = parseObservation(raw);
    expect(obs.id).toBe('');
    expect(obs.sessionId).toBe('');
    expect(obs.projectPath).toBe('');
    expect(obs.type).toBe('');
    expect(obs.title).toBeUndefined();
    expect(obs.subtitle).toBeUndefined();
    expect(obs.content).toBe('');
    expect(obs.facts).toBeUndefined();
    expect(obs.concepts).toBeUndefined();
    expect(obs.qualityScore).toBeUndefined();
    expect(obs.feedbackType).toBeUndefined();
    expect(obs.source).toBeUndefined();
    expect(obs.extractedData).toEqual({});
    expect(obs.promptNumber).toBeUndefined();
    expect(obs.createdAt).toBeUndefined();
    expect(obs.createdAtEpoch).toBeUndefined();
  });

  it('should handle type mismatches in wire fields', () => {
    const raw = {
      id: 123,           // number instead of string
      quality_score: '0.85', // string instead of number
      prompt_number: '42',   // string instead of number
      created_at_epoch: '1700000000', // string instead of number
      facts: 'not-an-array', // string instead of array
    };

    const obs = parseObservation(raw);
    expect(obs.id).toBe('123');
    expect(obs.qualityScore).toBe(0.85);
    expect(obs.promptNumber).toBe(42);
    expect(obs.createdAtEpoch).toBe(1700000000);
    expect(obs.facts).toBeUndefined(); // non-array → undefined
  });

  it('should handle NaN in numeric fields', () => {
    const raw = { quality_score: NaN, prompt_number: NaN, created_at_epoch: NaN };
    const obs = parseObservation(raw);
    expect(obs.qualityScore).toBeUndefined();
    expect(obs.promptNumber).toBeUndefined();
    expect(obs.createdAtEpoch).toBeUndefined();
  });

  it('should handle mixed null and valid items in facts array', () => {
    const raw = { facts: ['valid', null, 42, '', undefined] };
    const obs = parseObservation(raw);
    expect(obs.facts).toEqual(['valid', '42', '']);
  });

  it('should handle non-object extractedData', () => {
    const raw1 = { id: 'o1', extractedData: 'not-an-object' };
    expect(parseObservation(raw1).extractedData).toEqual({});

    const raw2 = { id: 'o2', extractedData: 42 };
    expect(parseObservation(raw2).extractedData).toEqual({});

    const raw3 = { id: 'o3', extractedData: ['array'] };
    expect(parseObservation(raw3).extractedData).toEqual({});
  });

  it('should default extractedData to empty object when missing', () => {
    const obs = parseObservation({ id: 'o1' });
    expect(obs.extractedData).toEqual({});
  });

  it('should prefer camelCase extractedData over snake_case', () => {
    const raw = { id: 'o1', extractedData: { key: 'camel' }, extracted_data: { key: 'snake' } };
    const obs = parseObservation(raw);
    expect(obs.extractedData).toEqual({ key: 'camel' });
  });

  it('should fall back to snake_case extracted_data when camelCase is absent', () => {
    const raw = { id: 'o1', extracted_data: { key: 'snake_val' } };
    const obs = parseObservation(raw);
    expect(obs.extractedData).toEqual({ key: 'snake_val' });
  });
});

// ==================== parseExtractionResult ====================

describe('parseExtractionResult', () => {
  it('should parse all wire format fields', () => {
    const raw = {
      status: 'ok',
      template: 'user-preferences',
      message: 'extracted successfully',
      sessionId: 'sess-1',
      extractedData: { preferences: ['dark-mode'] },
      createdAt: 1700000000,
      observationId: 'obs-123',
    };

    const result = parseExtractionResult(raw);
    expect(result.status).toBe('ok');
    expect(result.template).toBe('user-preferences');
    expect(result.message).toBe('extracted successfully');
    expect(result.sessionId).toBe('sess-1');
    expect(result.extractedData).toEqual({ preferences: ['dark-mode'] });
    expect(result.createdAt).toBe(1700000000);
    expect(result.observationId).toBe('obs-123');
  });

  it('should handle missing optional fields', () => {
    const raw = {
      sessionId: 's1',
      extractedData: {},
      createdAt: 0,
      observationId: 'o1',
    };

    const result = parseExtractionResult(raw);
    expect(result.status).toBeUndefined();
    expect(result.template).toBeUndefined();
    expect(result.message).toBeUndefined();
    expect(result.sessionId).toBe('s1');
  });

  it('should handle null wire fields gracefully', () => {
    const raw = {
      status: null,
      template: null,
      message: null,
      sessionId: null,
      extractedData: null,
      createdAt: null,
      observationId: null,
    };

    const result = parseExtractionResult(raw);
    expect(result.status).toBeUndefined();
    expect(result.template).toBeUndefined();
    expect(result.message).toBeUndefined();
    expect(result.sessionId).toBe('');
    expect(result.extractedData).toEqual({});
    expect(result.createdAt).toBe(0);
    expect(result.observationId).toBe('');
  });

  it('should handle type mismatches', () => {
    const raw = {
      sessionId: 42,
      createdAt: '1700000000',
      observationId: 123,
      extractedData: 'not-an-object',
    };

    const result = parseExtractionResult(raw);
    expect(result.sessionId).toBe('42');
    expect(result.createdAt).toBe(1700000000);
    expect(result.observationId).toBe('123');
    expect(result.extractedData).toEqual({}); // non-object → empty
  });
});

// ==================== APIError.toJSON ====================

describe('APIError', () => {
  it('should serialize to JSON with structured fields', () => {
    const err = new APIError(404, 'not found', '{"error":"missing"}');
    const json = err.toJSON();
    expect(json.name).toBe('APIError');
    expect(json.statusCode).toBe(404);
    expect(json.message).toContain('not found');
    expect(json.body).toBe('{"error":"missing"}');
  });

  it('should serialize without body when omitted', () => {
    const err = new APIError(500, 'internal');
    const json = err.toJSON();
    expect(json.body).toBeUndefined();
  });

  it('should produce meaningful message for empty response body', async () => {
    // Mock fetch that returns empty body (simulates 502 with no response body)
    const emptyBodyFetch = vi.fn().mockResolvedValue({
      status: 502,
      body: {
        getReader() {
          const encoded = new Uint8Array(0);
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
        return Promise.resolve('');
      },
    });
    const c = new CortexMemClient({ baseURL: 'http://127.0.0.1:37777', fetch: emptyBodyFetch });

    try {
      await c.getVersion();
      expect.unreachable('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(APIError);
      expect((err as APIError).statusCode).toBe(502);
      expect((err as APIError).message).toContain('empty response body');
    }
  });
});

// ==================== safeRecord edge cases ====================

describe('safeRecord edge cases', () => {
  it('should reject Date instances', () => {
    const obs = parseObservation({ id: 'o1', extractedData: new Date() });
    expect(obs.extractedData).toEqual({});
  });

  it('should accept regular plain objects', () => {
    const obs = parseObservation({ id: 'o1', extractedData: { key: 'val' } });
    expect(obs.extractedData).toEqual({ key: 'val' });
  });
});

// ==================== parseICLPromptResult ====================

describe('parseICLPromptResult', () => {
  it('should parse all wire format fields', () => {
    const raw = {
      prompt: 'test prompt content',
      experienceCount: 5,
      maxChars: 4000,
    };

    const result = parseICLPromptResult(raw);
    expect(result.prompt).toBe('test prompt content');
    expect(result.experienceCount).toBe(5);
    expect(result.maxChars).toBe(4000);
  });

  it('should handle missing optional fields', () => {
    const raw = { prompt: 'p', experienceCount: 3 };
    const result = parseICLPromptResult(raw);
    expect(result.prompt).toBe('p');
    expect(result.experienceCount).toBe(3);
    expect(result.maxChars).toBeUndefined();
  });

  it('should handle null wire fields gracefully', () => {
    const raw = { prompt: null, experienceCount: null, maxChars: null };
    const result = parseICLPromptResult(raw);
    expect(result.prompt).toBe('');
    expect(result.experienceCount).toBe(0);
    expect(result.maxChars).toBeUndefined();
  });

  it('should handle type mismatches', () => {
    const raw = { prompt: 42, experienceCount: '7', maxChars: '2000' };
    const result = parseICLPromptResult(raw);
    expect(result.prompt).toBe('42');
    expect(result.experienceCount).toBe(7);
    expect(result.maxChars).toBe(2000);
  });

  it('should handle NaN experienceCount', () => {
    const raw = { prompt: 'test', experienceCount: NaN };
    const result = parseICLPromptResult(raw);
    expect(result.experienceCount).toBe(0);
  });

  it('should prefer camelCase experienceCount over snake_case', () => {
    const raw = { prompt: 'test', experienceCount: 5, experience_count: 3 };
    const result = parseICLPromptResult(raw);
    expect(result.experienceCount).toBe(5);
  });

  it('should fall back to snake_case experience_count', () => {
    const raw = { prompt: 'test', experience_count: 7 };
    const result = parseICLPromptResult(raw);
    expect(result.experienceCount).toBe(7);
  });

  it('should prefer camelCase maxChars over snake_case', () => {
    const raw = { prompt: 'test', maxChars: 2000, max_chars: 1000 };
    const result = parseICLPromptResult(raw);
    expect(result.maxChars).toBe(2000);
  });

  it('should fall back to snake_case max_chars', () => {
    const raw = { prompt: 'test', max_chars: 3000 };
    const result = parseICLPromptResult(raw);
    expect(result.maxChars).toBe(3000);
  });
});

// ==================== parseStatsResponse ====================

describe('parseStatsResponse', () => {
  it('should parse all wire format fields', () => {
    const raw = {
      worker: { isProcessing: false, queueDepth: 0 },
      database: { totalObservations: 100, totalSummaries: 5, totalSessions: 10, totalProjects: 2 },
    };

    const result = parseStatsResponse(raw);
    expect(result.worker.isProcessing).toBe(false);
    expect(result.worker.queueDepth).toBe(0);
    expect(result.database.totalObservations).toBe(100);
    expect(result.database.totalSummaries).toBe(5);
    expect(result.database.totalSessions).toBe(10);
    expect(result.database.totalProjects).toBe(2);
  });

  it('should handle null nested objects', () => {
    const raw = { worker: null, database: null };
    const result = parseStatsResponse(raw);
    expect(result.worker.isProcessing).toBe(false);
    expect(result.worker.queueDepth).toBe(0);
    expect(result.database.totalObservations).toBe(0);
    expect(result.database.totalSummaries).toBe(0);
  });

  it('should handle missing nested objects', () => {
    const result = parseStatsResponse({});
    expect(result.worker.isProcessing).toBe(false);
    expect(result.worker.queueDepth).toBe(0);
    expect(result.database.totalObservations).toBe(0);
  });

  it('should handle type mismatches in nested fields', () => {
    const raw = {
      worker: { isProcessing: 'true', queueDepth: '5' },
      database: { totalObservations: '100', totalSummaries: '5', totalSessions: '10', totalProjects: '2' },
    };

    const result = parseStatsResponse(raw);
    expect(result.worker.isProcessing).toBe(true);
    expect(result.worker.queueDepth).toBe(5);
    expect(result.database.totalObservations).toBe(100);
  });

  it('should handle snake_case wire fields', () => {
    const raw = {
      worker: { is_processing: true, queue_depth: 3 },
      database: { total_observations: 50, total_summaries: 2, total_sessions: 5, total_projects: 1 },
    };

    const result = parseStatsResponse(raw);
    expect(result.worker.isProcessing).toBe(true);
    expect(result.worker.queueDepth).toBe(3);
    expect(result.database.totalObservations).toBe(50);
  });

  it('should reject non-object worker/database', () => {
    const raw = { worker: 'invalid', database: 42 };
    const result = parseStatsResponse(raw);
    expect(result.worker.isProcessing).toBe(false);
    expect(result.database.totalObservations).toBe(0);
  });
});

// ==================== parseVersionResponse ====================

describe('parseVersionResponse', () => {
  it('should parse all wire format fields', () => {
    const raw = {
      version: '0.1.0-beta',
      service: 'claude-mem-java',
      java: '21.0.1',
      springBoot: '3.3.0',
    };

    const result = parseVersionResponse(raw);
    expect(result.version).toBe('0.1.0-beta');
    expect(result.service).toBe('claude-mem-java');
    expect(result.java).toBe('21.0.1');
    expect(result.springBoot).toBe('3.3.0');
  });

  it('should handle null wire fields gracefully', () => {
    const raw = {
      version: null,
      service: null,
      java: null,
      springBoot: null,
    };

    const result = parseVersionResponse(raw);
    expect(result.version).toBe('');
    expect(result.service).toBe('');
    expect(result.java).toBe('');
    expect(result.springBoot).toBe('');
  });

  it('should handle missing fields', () => {
    const result = parseVersionResponse({});
    expect(result.version).toBe('');
    expect(result.service).toBe('');
    expect(result.java).toBe('');
    expect(result.springBoot).toBe('');
  });

  it('should handle type mismatches', () => {
    const raw = {
      version: 42,
      service: true,
      java: 21,
    };

    const result = parseVersionResponse(raw);
    expect(result.version).toBe('42');
    expect(result.service).toBe('true');
    expect(result.java).toBe('21');
    expect(result.springBoot).toBe('');
  });

  it('should prefer camelCase springBoot over snake_case', () => {
    const raw = { version: '1.0', service: 's', java: '21', springBoot: '3.3', spring_boot: '3.2' };
    const result = parseVersionResponse(raw);
    expect(result.springBoot).toBe('3.3');
  });

  it('should fall back to snake_case spring_boot', () => {
    const raw = { version: '1.0', service: 's', java: '21', spring_boot: '3.1' };
    const result = parseVersionResponse(raw);
    expect(result.springBoot).toBe('3.1');
  });
});

describe('getVersion defensive parsing', () => {
  it('should use parseVersionResponse for wire format safety', async () => {
    const localFetch = mockFetch(200, { version: '1.0', service: null, java: null, springBoot: '3.3' });
    const localClient = new CortexMemClient({ fetch: localFetch as unknown as typeof globalThis.fetch });

    const result = await localClient.getVersion();
    expect(result.version).toBe('1.0');
    expect(result.service).toBe(''); // defensive: null → ''
    expect(result.java).toBe('');
    expect(result.springBoot).toBe('3.3');
  });
});

// ==================== Defensive ICL parsing in client ====================

describe('buildICLPrompt defensive parsing', () => {
  it('should use parseICLPromptResult for wire format safety', async () => {
    // Simulate backend returning null experienceCount
    const localFetch = mockFetch(200, { prompt: 'test', experienceCount: null });
    const localClient = new CortexMemClient({ fetch: localFetch as unknown as typeof globalThis.fetch });

    const result = await localClient.buildICLPrompt({ task: 'test' });
    expect(result.experienceCount).toBe(0); // defensive: null → 0
    expect(result.prompt).toBe('test');
  });
});

describe('getStats defensive parsing', () => {
  it('should use parseStatsResponse for wire format safety', async () => {
    const localFetch = mockFetch(200, {
      worker: null,
      database: { totalObservations: 42, totalSummaries: 0, totalSessions: 1, totalProjects: 1 },
    });
    const localClient = new CortexMemClient({ fetch: localFetch as unknown as typeof globalThis.fetch });

    const result = await localClient.getStats();
    expect(result.worker.isProcessing).toBe(false); // defensive: null → defaults
    expect(result.database.totalObservations).toBe(42);
  });
});

// ==================== camelCase wire format fallback ====================

describe('camelCase wire format fallback', () => {
  it('parseObservation should prefer snake_case over camelCase', () => {
    // When both snake_case and camelCase are present, snake_case wins
    const raw = {
      id: 'o1',
      content_session_id: 'sess-snake',
      sessionId: 'sess-camel',
      quality_score: 0.9,
      qualityScore: 0.1,
      feedback_type: 'SUCCESS',
      feedbackType: 'FAILURE',
      created_at: '2026-01-01',
      createdAt: '2025-01-01',
    };
    const obs = parseObservation(raw);
    expect(obs.sessionId).toBe('sess-snake');
    expect(obs.qualityScore).toBe(0.9);
    expect(obs.feedbackType).toBe('SUCCESS');
    expect(obs.createdAt).toBe('2026-01-01');
  });

  it('parseObservation should fall back to camelCase when snake_case is absent', () => {
    const raw = {
      id: 'o1',
      sessionId: 'sess-camel',
      qualityScore: 0.75,
      feedbackType: 'PARTIAL',
      feedbackUpdatedAt: '2026-02-01',
      promptNumber: 5,
      createdAt: '2026-01-15',
      createdAtEpoch: 1700000000,
      lastAccessedAt: '2026-03-01',
      filesRead: ['a.ts', 'b.ts'],
      filesModified: ['c.ts'],
    };
    const obs = parseObservation(raw);
    expect(obs.sessionId).toBe('sess-camel');
    expect(obs.qualityScore).toBe(0.75);
    expect(obs.feedbackType).toBe('PARTIAL');
    expect(obs.feedbackUpdatedAt).toBe('2026-02-01');
    expect(obs.promptNumber).toBe(5);
    expect(obs.createdAt).toBe('2026-01-15');
    expect(obs.createdAtEpoch).toBe(1700000000);
    expect(obs.lastAccessedAt).toBe('2026-03-01');
    expect(obs.filesRead).toEqual(['a.ts', 'b.ts']);
    expect(obs.filesModified).toEqual(['c.ts']);
  });

  it('parseExperience should prefer snake_case over camelCase', () => {
    const raw = {
      id: 'e1', task: 't', strategy: 's', outcome: 'o',
      reuse_condition: 'snake',
      reuseCondition: 'camel',
      quality_score: 0.9,
      qualityScore: 0.1,
      created_at: '2026-01-01',
      createdAt: '2025-01-01',
    };
    const exp = parseExperience(raw);
    expect(exp.reuseCondition).toBe('snake');
    expect(exp.qualityScore).toBe(0.9);
    expect(exp.createdAt).toBe('2026-01-01');
  });

  it('parseExperience should fall back to camelCase when snake_case is absent', () => {
    const raw = {
      id: 'e1', task: 't', strategy: 's', outcome: 'o',
      reuseCondition: 'camel condition',
      qualityScore: 0.8,
      createdAt: '2026-06-15',
    };
    const exp = parseExperience(raw);
    expect(exp.reuseCondition).toBe('camel condition');
    expect(exp.qualityScore).toBe(0.8);
    expect(exp.createdAt).toBe('2026-06-15');
  });
});

// ==================== ValidationError ====================

describe('ValidationError', () => {
  it('should be thrown for missing required fields', async () => {
    const c = new CortexMemClient();
    try {
      await c.startSession({ session_id: '', project_path: '/tmp' });
      expect.unreachable('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(ValidationError);
      expect(isValidationError(err)).toBe(true);
      expect((err as ValidationError).field).toBe('session_id');
    }
  });

  it('should serialize to JSON with structured fields', () => {
    const err = new ValidationError('observationId', 'is required');
    const json = err.toJSON();
    expect(json.name).toBe('ValidationError');
    expect(json.field).toBe('observationId');
    expect(json.message).toContain('is required');
  });

  it('should not be an APIError', () => {
    const err = new ValidationError('field', 'msg');
    expect(err).not.toBeInstanceOf(APIError);
  });

  it('isValidationError should return false for APIError', () => {
    const apiErr = new APIError(400, 'bad request');
    expect(isValidationError(apiErr)).toBe(false);
  });
});

// ==================== Specific 5xx predicates ====================

describe('5xx error predicates', () => {
  it('should detect 502 Bad Gateway', async () => {
    const f = mockFetch(502, { error: 'bad gateway' });
    const c = new CortexMemClient({ fetch: f as unknown as typeof globalThis.fetch });
    try { await c.healthCheck(); } catch (err) {
      expect(isBadGateway(err)).toBe(true);
      expect(isServerError(err)).toBe(true);
      expect(isRetryable(err)).toBe(true);
    }
  });

  it('should detect 503 Service Unavailable', async () => {
    const f = mockFetch(503, { error: 'unavailable' });
    const c = new CortexMemClient({ fetch: f as unknown as typeof globalThis.fetch });
    try { await c.healthCheck(); } catch (err) {
      expect(isServiceUnavailable(err)).toBe(true);
      expect(isServerError(err)).toBe(true);
      expect(isRetryable(err)).toBe(true);
    }
  });

  it('should detect 504 Gateway Timeout', async () => {
    const f = mockFetch(504, { error: 'timeout' });
    const c = new CortexMemClient({ fetch: f as unknown as typeof globalThis.fetch });
    try { await c.healthCheck(); } catch (err) {
      expect(isGatewayTimeout(err)).toBe(true);
      expect(isServerError(err)).toBe(true);
      expect(isRetryable(err)).toBe(true);
    }
  });

  it('isBadGateway should return false for non-APIError', () => {
    expect(isBadGateway(new Error('network'))).toBe(false);
  });

  it('isServiceUnavailable should return false for non-APIError', () => {
    expect(isServiceUnavailable(new Error('network'))).toBe(false);
  });

  it('isGatewayTimeout should return false for non-APIError', () => {
    expect(isGatewayTimeout(new Error('network'))).toBe(false);
  });
});

// ==================== parseQualityDistribution ====================

describe('parseQualityDistribution', () => {
  it('should parse all wire format fields', () => {
    const raw = { project: '/tmp', high: 10, medium: 5, low: 2, unknown: 1 };
    const result = parseQualityDistribution(raw);
    expect(result.project).toBe('/tmp');
    expect(result.high).toBe(10);
    expect(result.medium).toBe(5);
    expect(result.low).toBe(2);
    expect(result.unknown).toBe(1);
  });

  it('should handle null wire fields gracefully', () => {
    const raw = { project: null, high: null, medium: null, low: null, unknown: null };
    const result = parseQualityDistribution(raw);
    expect(result.project).toBe('');
    expect(result.high).toBe(0);
    expect(result.medium).toBe(0);
    expect(result.low).toBe(0);
    expect(result.unknown).toBe(0);
  });

  it('should handle missing fields', () => {
    const result = parseQualityDistribution({});
    expect(result.project).toBe('');
    expect(result.high).toBe(0);
    expect(result.medium).toBe(0);
    expect(result.low).toBe(0);
    expect(result.unknown).toBe(0);
  });

  it('should handle type mismatches', () => {
    const raw = { project: 42, high: '10', medium: '5', low: true, unknown: NaN };
    const result = parseQualityDistribution(raw);
    expect(result.project).toBe('42');
    expect(result.high).toBe(10);
    expect(result.medium).toBe(5);
    expect(result.low).toBe(0); // boolean is not a valid number → 0
    expect(result.unknown).toBe(0); // NaN → 0
  });
});

// ==================== getModes defensive parsing ====================

describe('getModes defensive parsing', () => {
  it('should handle null observation_types gracefully', async () => {
    const localFetch = mockFetch(200, {
      id: 'full', name: 'Full', description: 'Full mode', version: '1.0',
      observation_types: null, observation_concepts: null,
    });
    const localClient = new CortexMemClient({ fetch: localFetch as unknown as typeof globalThis.fetch });

    const result = await localClient.getModes();
    expect(result.id).toBe('full');
    expect(result.observationTypes).toEqual([]);
    expect(result.observationConcepts).toEqual([]);
  });

  it('should handle non-array observation_types gracefully', async () => {
    const localFetch = mockFetch(200, {
      id: 'test', name: 'Test', description: 'Test mode', version: '1.0',
      observation_types: 'not-an-array', observation_concepts: 42,
    });
    const localClient = new CortexMemClient({ fetch: localFetch as unknown as typeof globalThis.fetch });

    const result = await localClient.getModes();
    expect(result.observationTypes).toEqual([]);
    expect(result.observationConcepts).toEqual([]);
  });

  it('should handle null string fields gracefully', async () => {
    const localFetch = mockFetch(200, {
      id: null, name: null, description: null, version: null,
      observation_types: ['type1'], observation_concepts: ['concept1'],
    });
    const localClient = new CortexMemClient({ fetch: localFetch as unknown as typeof globalThis.fetch });

    const result = await localClient.getModes();
    expect(result.id).toBe('');
    expect(result.name).toBe('');
    expect(result.description).toBe('');
    expect(result.version).toBe('');
    expect(result.observationTypes).toEqual(['type1']);
  });
});

// ==================== getQualityDistribution defensive parsing ====================

describe('getQualityDistribution defensive parsing', () => {
  it('should handle null fields gracefully', async () => {
    const localFetch = mockFetch(200, { project: null, high: null, medium: null, low: null, unknown: null });
    const localClient = new CortexMemClient({ fetch: localFetch as unknown as typeof globalThis.fetch });

    const result = await localClient.getQualityDistribution('/tmp');
    expect(result.project).toBe('');
    expect(result.high).toBe(0);
    expect(result.medium).toBe(0);
    expect(result.low).toBe(0);
    expect(result.unknown).toBe(0);
  });
});
