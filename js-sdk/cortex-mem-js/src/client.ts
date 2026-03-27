// ============================================================
// CortexMemClient — Main client implementation
// ============================================================

import { APIError, isRetryable } from './errors';
import {
  type CortexMemClientOptions,
  type ResolvedClientConfig,
  resolveConfig,
  SDK_VERSION,
} from './client-options';
import type {
  SessionStartRequest,
  SessionStartResponse,
  SessionEndRequest,
  UserPromptRequest,
  SessionUserUpdateResponse,
  ObservationRequest,
  ObservationUpdate,
  ExperienceRequest,
  Experience,
  ICLPromptRequest,
  ICLPromptResult,
  SearchRequest,
  SearchResult,
  ObservationsRequest,
  ObservationsResponse,
  BatchObservationsResponse,
  QualityDistribution,
  FeedbackRequest,
  ExtractionResult,
  VersionResponse,
  ProjectsResponse,
  StatsResponse,
  ModesResponse,
  HealthResponse,
} from './dto';

// ============================================================
// Logger interface
// ============================================================

export interface Logger {
  debug(msg: string, ...args: unknown[]): void;
  info(msg: string, ...args: unknown[]): void;
  warn(msg: string, ...args: unknown[]): void;
  error(msg: string, ...args: unknown[]): void;
}

// ============================================================
// CortexMemClient class
// ============================================================

export class CortexMemClient {
  private readonly config: ResolvedClientConfig;
  private closed = false;

  constructor(options?: CortexMemClientOptions) {
    this.config = resolveConfig(options);
  }

  // ==================== Session ====================

  /**
   * Start or resume a session.
   * POST /api/session/start
   */
  async startSession(req: SessionStartRequest): Promise<SessionStartResponse> {
    this.assertNotClosed();
    this.validateRequired('session_id', req.session_id);
    this.validateRequired('project_path', req.project_path);
    return this.requestJSON<SessionStartResponse>('POST', '/api/session/start', req);
  }

  /**
   * Update session userId.
   * PATCH /api/session/{sessionId}/user
   */
  async updateSessionUserId(sessionId: string, userId: string): Promise<SessionUserUpdateResponse> {
    this.assertNotClosed();
    this.validateRequired('sessionId', sessionId);
    return this.requestJSON<SessionUserUpdateResponse>(
      'PATCH',
      `/api/session/${encodeURIComponent(sessionId)}/user`,
      { user_id: userId },
    );
  }

  // ==================== Capture (fire-and-forget) ====================

  /**
   * Record a tool-use observation (fire-and-forget).
   * POST /api/ingest/tool-use
   */
  async recordObservation(req: ObservationRequest): Promise<void> {
    this.assertNotClosed();
    this.validateRequired('session_id', req.session_id);
    await this.doFireAndForget('RecordObservation', () =>
      this.requestNoContent('POST', '/api/ingest/tool-use', req),
    );
  }

  /**
   * Signal session end (fire-and-forget).
   * POST /api/ingest/session-end
   */
  async recordSessionEnd(req: SessionEndRequest): Promise<void> {
    this.assertNotClosed();
    this.validateRequired('session_id', req.session_id);
    await this.doFireAndForget('RecordSessionEnd', () =>
      this.requestNoContent('POST', '/api/ingest/session-end', req),
    );
  }

  /**
   * Record a user prompt (fire-and-forget).
   * POST /api/ingest/user-prompt
   */
  async recordUserPrompt(req: UserPromptRequest): Promise<void> {
    this.assertNotClosed();
    this.validateRequired('session_id', req.session_id);
    this.validateRequired('prompt_text', req.prompt_text);
    await this.doFireAndForget('RecordUserPrompt', () =>
      this.requestNoContent('POST', '/api/ingest/user-prompt', req),
    );
  }

  // ==================== Retrieval ====================

  /**
   * Retrieve relevant experiences.
   * POST /api/memory/experiences
   */
  async retrieveExperiences(req: ExperienceRequest): Promise<Experience[]> {
    this.assertNotClosed();
    this.validateRequired('task', req.task);
    return this.requestJSON<Experience[]>('POST', '/api/memory/experiences', req);
  }

  /**
   * Build an ICL prompt from historical experiences.
   * POST /api/memory/icl-prompt
   */
  async buildICLPrompt(req: ICLPromptRequest): Promise<ICLPromptResult> {
    this.assertNotClosed();
    this.validateRequired('task', req.task);
    return this.requestJSON<ICLPromptResult>('POST', '/api/memory/icl-prompt', req);
  }

  /**
   * Perform semantic search.
   * GET /api/search
   */
  async search(req: SearchRequest): Promise<SearchResult> {
    this.assertNotClosed();
    this.validateRequired('project', req.project);
    const params = this.buildSearchParams(req);
    return this.requestJSON<SearchResult>('GET', '/api/search', undefined, params);
  }

  /**
   * List observations with pagination.
   * GET /api/observations
   */
  async listObservations(req: ObservationsRequest): Promise<ObservationsResponse> {
    this.assertNotClosed();
    const params: Record<string, string> = {};
    if (req.project) params.project = req.project;
    if (req.offset !== undefined && req.offset > 0) params.offset = String(req.offset);
    if (req.limit !== undefined && req.limit > 0) params.limit = String(req.limit);
    return this.requestJSON<ObservationsResponse>('GET', '/api/observations', undefined, params);
  }

  /**
   * Get observations by IDs.
   * POST /api/observations/batch
   */
  async getObservationsByIds(ids: string[]): Promise<BatchObservationsResponse> {
    this.assertNotClosed();
    if (!ids || ids.length === 0) {
      throw new Error('cortex-ce: ids must not be empty');
    }
    if (ids.length > 100) {
      throw new Error(`cortex-ce: batch size exceeds maximum of 100 (got ${ids.length})`);
    }
    return this.requestJSON<BatchObservationsResponse>(
      'POST',
      '/api/observations/batch',
      { ids },
    );
  }

  // ==================== Management ====================

  /**
   * Trigger memory refinement.
   * POST /api/memory/refine?project=...
   */
  async triggerRefinement(projectPath: string): Promise<void> {
    this.assertNotClosed();
    this.validateRequired('projectPath', projectPath);
    await this.requestNoContent('POST', '/api/memory/refine', undefined, {
      project: projectPath,
    });
  }

  /**
   * Submit feedback for an observation.
   * POST /api/memory/feedback
   */
  async submitFeedback(req: FeedbackRequest): Promise<void> {
    this.assertNotClosed();
    this.validateRequired('observationId', req.observationId);
    this.validateRequired('feedbackType', req.feedbackType);
    const body: Record<string, string> = {
      observationId: req.observationId,
      feedbackType: req.feedbackType,
    };
    if (req.comment) body.comment = req.comment;
    await this.requestNoContent('POST', '/api/memory/feedback', body);
  }

  /**
   * Update an existing observation.
   * PATCH /api/memory/observations/{id}
   */
  async updateObservation(observationId: string, update: ObservationUpdate): Promise<void> {
    this.assertNotClosed();
    this.validateRequired('observationId', observationId);
    await this.requestNoContent(
      'PATCH',
      `/api/memory/observations/${encodeURIComponent(observationId)}`,
      update,
    );
  }

  /**
   * Delete an observation.
   * DELETE /api/memory/observations/{id}
   */
  async deleteObservation(observationId: string): Promise<void> {
    this.assertNotClosed();
    this.validateRequired('observationId', observationId);
    await this.requestNoContent(
      'DELETE',
      `/api/memory/observations/${encodeURIComponent(observationId)}`,
    );
  }

  /**
   * Get quality distribution for a project.
   * GET /api/memory/quality-distribution?project=...
   */
  async getQualityDistribution(projectPath: string): Promise<QualityDistribution> {
    this.assertNotClosed();
    this.validateRequired('projectPath', projectPath);
    return this.requestJSON<QualityDistribution>(
      'GET',
      '/api/memory/quality-distribution',
      undefined,
      { project: projectPath },
    );
  }

  // ==================== Health ====================

  /**
   * Check backend health.
   * GET /api/health
   */
  async healthCheck(): Promise<HealthResponse> {
    this.assertNotClosed();
    const resp = await this.requestJSON<HealthResponse>('GET', '/api/health');
    if (resp.status !== 'ok') {
      throw new APIError(503, `Unhealthy: ${JSON.stringify(resp)}`);
    }
    return resp;
  }

  // ==================== Extraction ====================

  /**
   * Manually trigger extraction for a project.
   * POST /api/extraction/run?projectPath=...
   */
  async triggerExtraction(projectPath: string): Promise<void> {
    this.assertNotClosed();
    this.validateRequired('projectPath', projectPath);
    await this.requestNoContent('POST', '/api/extraction/run', undefined, {
      projectPath,
    });
  }

  /**
   * Get latest extraction result.
   * GET /api/extraction/{template}/latest?projectPath=...&userId=...
   */
  async getLatestExtraction(
    projectPath: string,
    templateName: string,
    userId?: string,
  ): Promise<ExtractionResult> {
    this.assertNotClosed();
    this.validateRequired('projectPath', projectPath);
    this.validateRequired('templateName', templateName);
    const params: Record<string, string> = { projectPath };
    if (userId) params.userId = userId;
    return this.requestJSON<ExtractionResult>(
      'GET',
      `/api/extraction/${encodeURIComponent(templateName)}/latest`,
      undefined,
      params,
    );
  }

  /**
   * Get extraction history.
   * GET /api/extraction/{template}/history?projectPath=...&userId=...&limit=...
   */
  async getExtractionHistory(
    projectPath: string,
    templateName: string,
    userId?: string,
    limit?: number,
  ): Promise<ExtractionResult[]> {
    this.assertNotClosed();
    this.validateRequired('projectPath', projectPath);
    this.validateRequired('templateName', templateName);
    const params: Record<string, string> = { projectPath };
    if (userId) params.userId = userId;
    if (limit !== undefined && limit > 0) params.limit = String(limit);
    return this.requestJSON<ExtractionResult[]>(
      'GET',
      `/api/extraction/${encodeURIComponent(templateName)}/history`,
      undefined,
      params,
    );
  }

  // ==================== Version ====================

  /**
   * Get backend version info.
   * GET /api/version
   */
  async getVersion(): Promise<VersionResponse> {
    this.assertNotClosed();
    return this.requestJSON<VersionResponse>('GET', '/api/version');
  }

  // ==================== P1 Management ====================

  /**
   * Get all projects.
   * GET /api/projects
   */
  async getProjects(): Promise<ProjectsResponse> {
    this.assertNotClosed();
    return this.requestJSON<ProjectsResponse>('GET', '/api/projects');
  }

  /**
   * Get project statistics.
   * GET /api/stats
   */
  async getStats(projectPath?: string): Promise<StatsResponse> {
    this.assertNotClosed();
    const params: Record<string, string> = {};
    if (projectPath) params.project = projectPath;
    return this.requestJSON<StatsResponse>('GET', '/api/stats', undefined, params);
  }

  /**
   * Get memory mode settings.
   * GET /api/modes
   */
  async getModes(): Promise<ModesResponse> {
    this.assertNotClosed();
    return this.requestJSON<ModesResponse>('GET', '/api/modes');
  }

  /**
   * Get current settings.
   * GET /api/settings
   */
  async getSettings(): Promise<Record<string, unknown>> {
    this.assertNotClosed();
    return this.requestJSON<Record<string, unknown>>('GET', '/api/settings');
  }

  // ==================== Lifecycle ====================

  /**
   * Close the client. Subsequent calls will throw.
   */
  close(): void {
    this.closed = true;
  }

  // ============================================================
  // Private HTTP layer
  // ============================================================

  private assertNotClosed(): void {
    if (this.closed) {
      throw new Error('cortex-ce: client is closed');
    }
  }

  private validateRequired(field: string, value: string | undefined): void {
    if (!value) {
      throw new Error(`cortex-ce: ${field} is required`);
    }
  }

  private buildURL(
    path: string,
    queryParams?: Record<string, string>,
  ): string {
    const url = new URL(path, this.config.baseURL);
    if (queryParams) {
      for (const [k, v] of Object.entries(queryParams)) {
        if (v !== undefined && v !== '') {
          url.searchParams.set(k, v);
        }
      }
    }
    return url.toString();
  }

  private buildHeaders(body?: unknown): Record<string, string> {
    const headers: Record<string, string> = {
      Accept: 'application/json',
      'User-Agent': `cortex-mem-js/${SDK_VERSION}`,
      ...this.config.headers,
    };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (this.config.apiKey) {
      headers['Authorization'] = `Bearer ${this.config.apiKey}`;
    }
    return headers;
  }

  private async doFetch(
    method: string,
    path: string,
    body?: unknown,
    queryParams?: Record<string, string>,
  ): Promise<{ data: Uint8Array; status: number }> {
    const url = this.buildURL(path, queryParams);
    const headers = this.buildHeaders(body);

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.config.timeout);

    try {
      const resp = await this.config.fetch(url, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });

      // Read response with size limit (10MB)
      const maxSize = 10 * 1024 * 1024;
      const reader = resp.body?.getReader();
      if (!reader) {
        // Fallback: resp.text() (for environments without ReadableStream)
        const text = await resp.text();
        if (text.length > maxSize) {
          throw new Error('cortex-ce: response body exceeds 10MB limit');
        }
        return { data: new TextEncoder().encode(text), status: resp.status };
      }

      const chunks: Uint8Array[] = [];
      let totalSize = 0;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        totalSize += value.length;
        if (totalSize > maxSize) {
          reader.cancel();
          throw new Error('cortex-ce: response body exceeds 10MB limit');
        }
        chunks.push(value);
      }

      // Concatenate chunks
      const data = new Uint8Array(totalSize);
      let offset = 0;
      for (const chunk of chunks) {
        data.set(chunk, offset);
        offset += chunk.length;
      }

      return { data, status: resp.status };
    } finally {
      clearTimeout(timer);
    }
  }

  private async requestJSON<T>(
    method: string,
    path: string,
    body?: unknown,
    queryParams?: Record<string, string>,
  ): Promise<T> {
    const { data, status } = await this.doFetch(method, path, body, queryParams);
    const text = new TextDecoder().decode(data);
    if (status >= 400) {
      throw new APIError(status, this.extractErrorMessage(text), text);
    }
    try {
      return JSON.parse(text) as T;
    } catch {
      throw new Error(`cortex-ce: failed to parse ${path} response`);
    }
  }

  private async requestNoContent(
    method: string,
    path: string,
    body?: unknown,
    queryParams?: Record<string, string>,
  ): Promise<void> {
    const { data, status } = await this.doFetch(method, path, body, queryParams);
    if (status >= 400) {
      const text = new TextDecoder().decode(data);
      throw new APIError(status, this.extractErrorMessage(text), text);
    }
  }

  private extractErrorMessage(text: string): string {
    try {
      const parsed = JSON.parse(text) as Record<string, unknown>;
      for (const key of ['error', 'message', 'detail']) {
        const val = parsed[key];
        if (typeof val === 'string' && val) return val;
      }
      return JSON.stringify(parsed);
    } catch {
      return text.length > 200 ? text.slice(0, 200) + '...' : text;
    }
  }

  /**
   * Fire-and-forget with retry. Matches Go SDK behavior.
   * Internal retry with linear backoff + jitter. Failures are swallowed.
   */
  private async doFireAndForget(
    name: string,
    fn: () => Promise<void>,
  ): Promise<void> {
    let lastError: unknown;
    for (let attempt = 1; attempt <= this.config.maxRetries; attempt++) {
      try {
        await fn();
        return;
      } catch (err) {
        lastError = err;
        // Don't retry non-retryable errors
        if (!isRetryable(err)) {
          this.config.logger.warn(
            `cortex-ce: ${name} failed with non-retryable error, giving up`,
            { error: err, attempt },
          );
          return; // Swallow error
        }
        if (attempt < this.config.maxRetries) {
          this.config.logger.warn(
            `cortex-ce: ${name} failed, retrying`,
            { error: err, attempt, maxRetries: this.config.maxRetries },
          );
          // Linear backoff with ±25% jitter
          const baseDelay = this.config.retryBackoff * attempt;
          const jitter = baseDelay * 0.25 * (Math.random() * 2 - 1);
          const delay = Math.max(0, baseDelay + jitter);
          await this.sleep(delay);
        }
      }
    }
    this.config.logger.warn(
      `cortex-ce: ${name} failed after retries`,
      { error: lastError, attempts: this.config.maxRetries },
    );
    // Swallow error — fire-and-forget
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  // ============================================================
  // Search param builder
  // ============================================================

  private buildSearchParams(req: SearchRequest): Record<string, string> {
    const params: Record<string, string> = { project: req.project };
    if (req.query) params.query = req.query;
    if (req.type) params.type = req.type;
    if (req.concept) params.concept = req.concept;
    if (req.source) params.source = req.source;
    if (req.limit !== undefined && req.limit > 0) params.limit = String(req.limit);
    if (req.offset !== undefined && req.offset > 0) params.offset = String(req.offset);
    return params;
  }
}
