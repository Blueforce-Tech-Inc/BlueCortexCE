/**
 * Cortex CE JS SDK — Demo HTTP Server (Express).
 *
 * Exposes all 26 SDK methods as REST endpoints,
 * mirroring the Go http-server and Python Flask demos.
 *
 * Usage:
 *   npm install express
 *   CORTEX_BASE_URL=http://127.0.0.1:37777 PORT=8080 npx tsx examples/http-server/app.ts
 */

import express, { Request, Response, NextFunction } from 'express';
import { CortexMemClient, APIError } from '../../src';

const CORTEX_BASE_URL = process.env.CORTEX_BASE_URL ?? 'http://127.0.0.1:37777';
const PORT = parseInt(process.env.PORT ?? '8080', 10);

const client = new CortexMemClient({ baseURL: CORTEX_BASE_URL });
const app = express();

app.use(express.json({ limit: '1mb' }));

// ==================== Middleware ====================

function requireFields(data: Record<string, unknown>, fields: string[]): string | null {
  for (const f of fields) {
    const v = data[f];
    if (v === undefined || v === null || (typeof v === 'string' && !v.trim())) return f;
  }
  return null;
}

function errorJson(res: Response, status: number, message: string) {
  res.status(status).json({ error: message });
}

// Express 4 does not catch async rejections — wrap async handlers
function asyncHandler(fn: (req: Request, res: Response, next: NextFunction) => Promise<void>) {
  return (req: Request, res: Response, next: NextFunction) => {
    fn(req, res, next).catch(next);
  };
}

// Request logger
app.use((req: Request, _res: Response, next: NextFunction) => {
  console.log(`${new Date().toISOString()} ${req.method} ${req.path}`);
  next();
});

// ==================== Health ====================

app.get('/health', asyncHandler(async (_req: Request, res: Response) => {
  try {
    await client.healthCheck();
    res.json({
      service: 'js-sdk-http-server',
      status: 'ok',
      time: new Date().toISOString(),
    });
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e);
    errorJson(res, 503, `unhealthy: ${msg}`);
  }
});

// ==================== Chat ====================

app.post('/chat', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['project', 'message']);
  if (missing) return errorJson(res, 400, `${missing} is required`);

  let iclResult = null;
  try {
    iclResult = await client.buildICLPrompt({
      task: req.body.message,
      project: req.body.project,
      maxChars: req.body.maxChars ?? 0,
      userId: req.body.userId,
    });
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e);
    console.warn('ICL prompt failed:', msg);
  }

  const resp: Record<string, unknown> = {
    response: `Received: ${req.body.message}`,
    project: req.body.project,
    timestamp: new Date().toISOString(),
  };
  if (iclResult?.prompt) {
    resp.memoryContext = iclResult.prompt;
    resp.experienceCount = iclResult.experienceCount;
  }
  res.json(resp);
});

// ==================== Search ====================

app.get('/search', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  if (!project) return errorJson(res, 400, 'project is required');

  const query = req.query.query as string | undefined;
  const type = req.query.type as string | undefined;
  const concept = req.query.concept as string | undefined;
  const source = req.query.source as string | undefined;
  const limit = parseInt(req.query.limit as string ?? '0', 10) || undefined;
  const offset = parseInt(req.query.offset as string ?? '0', 10) || undefined;

  const result = await client.search({
    project,
    ...(query && { query }),
    ...(type && { type }),
    ...(concept && { concept }),
    ...(source && { source }),
    ...(limit && { limit }),
    ...(offset && { offset }),
  });
  res.json(result);
});

// ==================== Version ====================

app.get('/version', asyncHandler(async (_req: Request, res: Response) => {
  const v = await client.getVersion();
  res.json(v);
});

// ==================== Experiences ====================

app.get('/experiences', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  const task = req.query.task as string;
  if (!project) return errorJson(res, 400, 'project is required');
  if (!task) return errorJson(res, 400, 'task is required');

  const conceptsStr = (req.query.requiredConcepts as string) ?? '';
  const requiredConcepts = conceptsStr
    ? conceptsStr.split(',').map(c => c.trim()).filter(Boolean)
    : undefined;

  const experiences = await client.retrieveExperiences({
    task,
    project,
    count: parseInt(req.query.count as string ?? '4', 10) || 4,
    source: (req.query.source as string) ?? undefined,
    requiredConcepts,
    userId: (req.query.userId as string) ?? undefined,
  });
  res.json({ experiences, count: experiences.length });
});

// ==================== ICL Prompt ====================

app.get('/iclprompt', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  const task = req.query.task as string;
  if (!project) return errorJson(res, 400, 'project is required');
  if (!task) return errorJson(res, 400, 'task is required');

  const result = await client.buildICLPrompt({
    task,
    project,
    maxChars: parseInt(req.query.maxChars as string ?? '0', 10) || 0,
    userId: (req.query.userId as string) ?? undefined,
  });
  res.json(result);
});

// ==================== Observations ====================

app.get('/observations', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  if (!project) return errorJson(res, 400, 'project is required');

  const result = await client.listObservations({
    project,
    limit: parseInt(req.query.limit as string ?? '0', 10) || 0,
    offset: parseInt(req.query.offset as string ?? '0', 10) || 0,
  });
  res.json(result);
});

app.post('/observations/batch', asyncHandler(async (req: Request, res: Response) => {
  const ids: string[] = req.body.ids ?? [];
  if (!ids.length) return errorJson(res, 400, 'ids is required');
  if (ids.length > 100) return errorJson(res, 400, 'batch size exceeds maximum of 100');

  const result = await client.getObservationsByIds(ids);
  res.json(result);
});

app.post('/observations/create', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['project', 'session_id', 'tool_name']);
  if (missing) return errorJson(res, 400, `${missing} is required`);

  await client.recordObservation({
    session_id: req.body.session_id,
    cwd: req.body.project,
    tool_name: req.body.tool_name,
    tool_input: req.body.tool_input,
    tool_response: req.body.tool_response,
    prompt_number: req.body.prompt_number,
    source: req.body.source,
    extractedData: req.body.extractedData,
  });
  res.json({ status: 'recorded' });
});

app.patch('/observations/:id', asyncHandler(async (req: Request, res: Response) => {
  const update: Record<string, unknown> = {};
  for (const key of ['title', 'subtitle', 'content', 'narrative', 'facts', 'concepts', 'source']) {
    if (key in req.body) update[key] = req.body[key];
  }
  if ('extractedData' in req.body) update.extractedData = req.body.extractedData;

  await client.updateObservation(req.params.id, update);
  res.json({ status: 'updated' });
});

app.delete('/observations/:id', asyncHandler(async (req: Request, res: Response) => {
  await client.deleteObservation(req.params.id);
  res.status(204).end();
});

// ==================== Projects / Stats / Modes / Settings ====================

app.get('/projects', asyncHandler(async (_req: Request, res: Response) => {
  const result = await client.getProjects();
  res.json(result);
});

app.get('/stats', asyncHandler(async (req: Request, res: Response) => {
  const result = await client.getStats((req.query.project as string) ?? undefined);
  res.json(result);
});

app.get('/modes', asyncHandler(async (_req: Request, res: Response) => {
  const result = await client.getModes();
  res.json(result);
});

app.get('/settings', asyncHandler(async (_req: Request, res: Response) => {
  const result = await client.getSettings();
  res.json(result);
});

// ==================== Quality ====================

app.get('/quality', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  if (!project) return errorJson(res, 400, 'project is required');
  const result = await client.getQualityDistribution(project);
  res.json(result);
});

// ==================== Extraction ====================

app.get('/extraction/latest', asyncHandler(async (req: Request, res: Response) => {
  const template = req.query.template as string;
  const project = req.query.project as string;
  if (!template) return errorJson(res, 400, 'template is required');
  if (!project) return errorJson(res, 400, 'project is required');

  const userId = (req.query.userId as string) || undefined;
  const result = await client.getLatestExtraction(project, template, userId);
  res.json(result);
});

app.get('/extraction/history', asyncHandler(async (req: Request, res: Response) => {
  const template = req.query.template as string;
  const project = req.query.project as string;
  if (!template) return errorJson(res, 400, 'template is required');
  if (!project) return errorJson(res, 400, 'project is required');

  const userId = (req.query.userId as string) || undefined;
  const results = await client.getExtractionHistory(
    project,
    template,
    userId,
    parseInt(req.query.limit as string ?? '0', 10) || undefined,
  );
  res.json(results);
});

app.post('/extraction/run', asyncHandler(async (req: Request, res: Response) => {
  const projectPath = req.query.projectPath as string;
  if (!projectPath) return errorJson(res, 400, 'projectPath is required');
  await client.triggerExtraction(projectPath);
  res.json({ status: 'extraction triggered' });
});

// ==================== Refine / Feedback ====================

app.post('/refine', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  if (!project) return errorJson(res, 400, 'project is required');
  await client.triggerRefinement(project);
  res.json({ status: 'refined' });
});

app.post('/feedback', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['observation_id', 'feedback_type']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  await client.submitFeedback({
    observationId: req.body.observation_id,
    feedbackType: req.body.feedback_type,
    comment: req.body.comment,
  });
  res.json({ status: 'submitted' });
});

// ==================== Session ====================

app.post('/session/start', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['session_id', 'project']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  const result = await client.startSession({
    session_id: req.body.session_id,
    project_path: req.body.project,
    user_id: req.body.user_id,
  });
  res.json(result);
});

app.patch('/session/user', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['session_id', 'user_id']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  const result = await client.updateSessionUserId(req.body.session_id, req.body.user_id);
  res.json(result);
});

// ==================== Ingest ====================

app.post('/ingest/prompt', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['project', 'session_id', 'prompt']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  await client.recordUserPrompt({
    session_id: req.body.session_id,
    prompt_text: req.body.prompt,
    cwd: req.body.project,
    prompt_number: req.body.prompt_number ?? 0,
  });
  res.json({ status: 'recorded' });
});

app.post('/ingest/session-end', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['project', 'session_id']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  await client.recordSessionEnd({
    session_id: req.body.session_id,
    cwd: req.body.project,
  });
  res.json({ status: 'ended' });
});

// ==================== Async error handler ====================

// Global error handler (asyncHandler catches async rejections, this catches sync errors)
app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
  console.error('Unhandled error:', err);
  if (err instanceof APIError) {
    errorJson(res, err.statusCode, err.message);
  } else {
    const message = err instanceof Error ? err.message : 'Internal server error';
    errorJson(res, 500, message);
  }
});

// ==================== Start ====================

app.listen(PORT, () => {
  console.log(`🚀 JS SDK HTTP server starting on :${PORT}`);
  console.log(`   Backend: ${CORTEX_BASE_URL}`);
  console.log();
  console.log('Endpoints:');
  console.log('  GET    /health              - Health check');
  console.log('  POST   /chat                - Chat with memory');
  console.log('  GET    /search              - Search observations');
  console.log('  GET    /version             - Backend version');
  console.log('  GET    /experiences         - Retrieve experiences');
  console.log('  GET    /iclprompt           - Build ICL prompt');
  console.log('  GET    /observations        - List observations');
  console.log('  POST   /observations/batch  - Batch get observations by IDs');
  console.log('  GET    /projects            - Get projects');
  console.log('  GET    /stats               - Get stats');
  console.log('  GET    /modes               - Get modes');
  console.log('  GET    /settings            - Get settings');
  console.log('  GET    /quality             - Quality distribution');
  console.log('  GET    /extraction/latest   - Latest extraction result');
  console.log('  GET    /extraction/history  - Extraction history');
  console.log('  POST   /extraction/run      - Trigger extraction');
  console.log('  POST   /refine              - Trigger memory refinement');
  console.log('  POST   /feedback            - Submit observation feedback');
  console.log('  POST   /session/start       - Start/resume session');
  console.log('  PATCH  /session/user        - Update session user ID');
  console.log('  PATCH  /observations/:id    - Update observation');
  console.log('  DELETE /observations/:id    - Delete observation');
  console.log('  POST   /observations/create - Record observation');
  console.log('  POST   /ingest/prompt       - Ingest user prompt');
  console.log('  POST   /ingest/session-end  - Ingest session end');
});
