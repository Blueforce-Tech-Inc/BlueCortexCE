/**
 * Cortex CE JS SDK — Demo HTTP Server (Express).
 *
 * Exposes 26 REST endpoints, which together cover all 25 public SDK API methods:
 * buildICLPrompt is reachable from two of them (/chat and /iclprompt), and close()
 * is a lifecycle method with no route. /health is one of the 26 and is what maps to
 * healthCheck() — it is not in addition to them.
 * Mirrors the Go http-server and Python Flask demos.
 *
 * Usage:
 *   npm install express
 *   CORTEX_BASE_URL=http://127.0.0.1:37777 PORT=37781 npx tsx examples/http-server/app.ts
 */

import express, { Request, Response, NextFunction } from 'express';
import { pathToFileURL } from 'node:url';
import { CortexMemClient, APIError, ValidationError } from '../../src';
import type { ObservationUpdate } from '../../src';

const CORTEX_BASE_URL = process.env.CORTEX_BASE_URL ?? 'http://127.0.0.1:37777';
const PORT = parseInt(process.env.PORT ?? '37781', 10);

const client = new CortexMemClient({ baseURL: CORTEX_BASE_URL });
const app = express();

app.use(express.json({ limit: '1mb' }));

// ==================== Middleware ====================

// express.json() only parses a request whose Content-Type is application/json.
// For anything else — no body at all, or a form/text body — it leaves req.body
// as undefined, and every handler below dereferences it (req.body.project,
// 'title' in req.body, ...). That threw a TypeError inside the handler, which
// asyncHandler forwarded to the error middleware, which had no branch for it
// and answered 500 while echoing the internal message back to the caller:
//
//   PATCH /observations/xyz   (no body, no Content-Type)
//     -> 500 {"error":"Cannot use 'in' operator to search for 'extractedData' in undefined"}
//
// Go (readJSON) and Python (_parse_json) both reject the same request with 400
// before any handler runs, and the Java demo never reads a body it did not
// receive. A request that simply has no body is a client mistake, so report it
// as one: normalise the missing body to an empty object and let each handler's
// own validation produce its usual 400. That keeps the per-endpoint messages
// ("project is required", "ids is required", ...) rather than inventing one
// generic "body required" error here.
app.use((req: Request, _res: Response, next: NextFunction) => {
  if (req.body === undefined) req.body = {};
  next();
});

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

// Parse an optional integer query param.
// Returns { ok: false, message } for non-integer input so handlers can answer 400
// instead of silently falling back to the default (see the /search handler).
//
// The pattern allows a leading sign, and only a sign: "0x10" and "1e3" are
// rejected. parseInt() would happily accept "10abc" (returning 10) and
// Number("0x10") would return 16, so the check is done against the raw text
// rather than against the parsed value. A leading "+" is accepted because the
// backend, the Go demo, the Python demo and the Java demo all accept it, and
// the four demos are meant to demonstrate one contract, not four.
export function parseIntParam(
  raw: unknown,
  name: string,
  opts: { min: number; max: number; range?: string },
): { ok: true; value: number } | { ok: false; message: string } {
  if (raw === undefined || raw === null || raw === '') return { ok: true, value: 0 };
  const parsed = typeof raw === 'number' ? raw : parseInt(String(raw), 10);
  if (isNaN(parsed) || !/^[+-]?\d+$/.test(String(raw).trim())) {
    return { ok: false, message: `${name} must be an integer` };
  }
  if (parsed < opts.min || parsed > opts.max) {
    const range = opts.range ?? `between ${opts.min} and ${opts.max}`;
    return { ok: false, message: `${name} must be ${range}` };
  }
  return { ok: true, value: parsed };
}

// Wrap async handlers for compatibility (Express 5 catches async rejections natively, but this ensures safety across runtimes)
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
}));

// ==================== Chat ====================

app.post('/chat', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['project', 'message']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  // Validate optional maxChars is a valid number if provided. Without this the
  // raw body value goes straight to the SDK, a non-numeric value reaches the
  // backend as-is, the backend answers 400, and the catch below turns that into
  // a 200 with no memoryContext at all — the caller cannot tell the difference
  // between "no memories" and "your input was rejected". Same helper and bounds
  // as the /search handler below.
  const maxChars = parseIntParam(req.body.maxChars, 'maxChars', { min: 0, max: 100000 });
  if (!maxChars.ok) return errorJson(res, 400, maxChars.message);

  let iclResult = null;
  try {
    iclResult = await client.buildICLPrompt({
      task: req.body.message,
      project: req.body.project,
      // 0 means "let the backend choose"; the SDK drops non-positive values
      // rather than putting a 0 on the wire, which the backend would clamp to
      // 100 characters.
      maxChars: maxChars.value,
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
}));

// ==================== Search ====================

app.get('/search', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  if (!project) return errorJson(res, 400, 'project is required');

  const query = req.query.query as string | undefined;
  const type = req.query.type as string | undefined;
  const concept = req.query.concept as string | undefined;
  const source = req.query.source as string | undefined;
  // Use the same strict helper as the other handlers: a bare parseInt accepts
  // trailing garbage ("10abc" -> 10), so this used to be the only endpoint in
  // the file that answered 200 where /observations, /extraction/history and
  // /iclprompt all answered 400 for the same input.
  const limit = parseIntParam(req.query.limit, 'limit', { min: 0, max: 100 });
  if (!limit.ok) return errorJson(res, 400, limit.message);
  const offset = parseIntParam(req.query.offset, 'offset', {
    min: 0,
    max: Number.MAX_SAFE_INTEGER,
    range: 'non-negative',
  });
  if (!offset.ok) return errorJson(res, 400, offset.message);

  const orderBy = (req.query.orderBy as string) || undefined;

  const result = await client.search({
    project,
    ...(query && { query }),
    ...(type && { type }),
    ...(concept && { concept }),
    ...(source ? { source } : {}),
    ...(limit.value > 0 && { limit: limit.value }),
    ...(offset.value > 0 && { offset: offset.value }),
    ...(orderBy && { orderBy }),
  });
  res.json(result);
}));

// ==================== Version ====================

app.get('/version', asyncHandler(async (_req: Request, res: Response) => {
  const v = await client.getVersion();
  res.json(v);
}));

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

  // Use the same strict helper as every other numeric param in this file. A bare
  // parseInt turns "10abc" into 10, so this used to be the only endpoint that
  // answered 200 where /search, /observations, /iclprompt and
  // /extraction/history all answered 400 for the same trailing garbage.
  // One deliberate alignment: count= (empty) is now treated as unset, like
  // limit= and maxChars=, where the bare parseInt used to reject it with 400.
  const rawCount = parseIntParam(req.query.count ?? '4', 'count', { min: 0, max: 100 });
  if (!rawCount.ok) return errorJson(res, 400, rawCount.message);
  // count=0 means "use SDK default" (consistent with Java demo)
  const count = rawCount.value > 0 ? rawCount.value : 4;

  const experiences = await client.retrieveExperiences({
    task,
    project,
    count,
    source: ((req.query.source as string) || undefined),
    requiredConcepts,
    userId: ((req.query.userId as string) || undefined),
  });
  res.json({ experiences, count: experiences.length });
}));

// ==================== ICL Prompt ====================

app.get('/iclprompt', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields({ project: req.query.project, task: req.query.task }, ['project', 'task']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  const project = req.query.project as string;
  const task = req.query.task as string;

  const maxChars = parseIntParam(req.query.maxChars, 'maxChars', { min: 0, max: 100000 });
  if (!maxChars.ok) return errorJson(res, 400, maxChars.message);

  const result = await client.buildICLPrompt({
    task,
    project,
    maxChars: maxChars.value,
    userId: (req.query.userId as string) ?? undefined,
  });
  res.json(result);
}));

// ==================== Observations ====================

app.get('/observations', asyncHandler(async (req: Request, res: Response) => {
  // project is optional — empty/missing means all projects (consistent with Go/Java demos)
  const project = (req.query.project as string) || '';

  const limit = parseIntParam(req.query.limit, 'limit', { min: 0, max: 100 });
  if (!limit.ok) return errorJson(res, 400, limit.message);
  const offset = parseIntParam(req.query.offset, 'offset', {
    min: 0,
    max: Number.MAX_SAFE_INTEGER,
    range: 'non-negative',
  });
  if (!offset.ok) return errorJson(res, 400, offset.message);

  const result = await client.listObservations({
    project,
    limit: limit.value,
    offset: offset.value,
  });
  res.json(result);
}));

app.get('/observations/:id', asyncHandler(async (req: Request, res: Response) => {
  const id = req.params.id as string;
  const result = await client.getObservation(id);
  if (result === null) return errorJson(res, 404, `observation ${id} not found`);
  res.json(result);
}));

app.post('/observations/batch', asyncHandler(async (req: Request, res: Response) => {
  const ids: string[] = req.body.ids ?? [];
  if (!ids.length) return errorJson(res, 400, 'ids is required');
  if (ids.length > 100) return errorJson(res, 400, 'batch size exceeds maximum of 100');
  for (let i = 0; i < ids.length; i++) {
    if (!ids[i] || !ids[i].trim()) return errorJson(res, 400, `ids[${i}] is empty`);
  }

  const result = await client.getObservationsByIds(ids);
  res.json(result);
}));

app.post('/observations/create', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['project', 'session_id', 'tool_name']);
  if (missing) return errorJson(res, 400, `${missing} is required`);

  // Validate extractedData type if provided (must be object, not string or array)
  if ('extractedData' in req.body && (typeof req.body.extractedData !== 'object' || Array.isArray(req.body.extractedData) || req.body.extractedData === null)) {
    return errorJson(res, 400, 'extractedData must be a JSON object');
  }

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
}));

app.patch('/observations/:id', asyncHandler(async (req: Request, res: Response) => {
  // Validate extractedData type if provided (must be object, not string or array)
  if ('extractedData' in req.body && (typeof req.body.extractedData !== 'object' || Array.isArray(req.body.extractedData) || req.body.extractedData === null)) {
    return errorJson(res, 400, 'extractedData must be a JSON object');
  }

  const update: ObservationUpdate = {};
  if ('title' in req.body) update.title = req.body.title;
  if ('subtitle' in req.body) update.subtitle = req.body.subtitle;
  if ('content' in req.body) update.content = req.body.content;
  if ('narrative' in req.body) update.narrative = req.body.narrative;
  if ('facts' in req.body) update.facts = req.body.facts;
  if ('concepts' in req.body) update.concepts = req.body.concepts;
  if ('source' in req.body) update.source = req.body.source;
  if ('extractedData' in req.body) update.extractedData = req.body.extractedData;

  const id = req.params.id as string;
  await client.updateObservation(id, update);
  res.json({ status: 'updated' });
}));

app.delete('/observations/:id', asyncHandler(async (req: Request, res: Response) => {
  await client.deleteObservation(req.params.id as string);
  res.status(204).end();
}));

// ==================== Projects / Stats / Modes / Settings ====================

app.get('/projects', asyncHandler(async (_req: Request, res: Response) => {
  const result = await client.getProjects();
  res.json(result);
}));

app.get('/stats', asyncHandler(async (req: Request, res: Response) => {
  const result = await client.getStats((req.query.project as string) ?? undefined);
  res.json(result);
}));

app.get('/modes', asyncHandler(async (_req: Request, res: Response) => {
  const result = await client.getModes();
  res.json(result);
}));

app.get('/settings', asyncHandler(async (_req: Request, res: Response) => {
  const result = await client.getSettings();
  res.json(result);
}));

// ==================== Quality ====================

app.get('/quality', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  if (!project) return errorJson(res, 400, 'project is required');
  const result = await client.getQualityDistribution(project);
  res.json(result);
}));

// ==================== Extraction ====================

app.get('/extraction/latest', asyncHandler(async (req: Request, res: Response) => {
  const template = req.query.template as string;
  const project = req.query.project as string;
  if (!template) return errorJson(res, 400, 'template is required');
  if (!project) return errorJson(res, 400, 'project is required');

  const userId = (req.query.userId as string) || undefined;
  const result = await client.getLatestExtraction(project, template, userId);
  res.json(result);
}));

app.get('/extraction/history', asyncHandler(async (req: Request, res: Response) => {
  const template = req.query.template as string;
  const project = req.query.project as string;
  if (!template) return errorJson(res, 400, 'template is required');
  if (!project) return errorJson(res, 400, 'project is required');

  const userId = (req.query.userId as string) || undefined;
  const limit = parseIntParam(req.query.limit, 'limit', { min: 0, max: 100 });
  if (!limit.ok) return errorJson(res, 400, limit.message);
  const results = await client.getExtractionHistory(
    project,
    template,
    userId,
    limit.value > 0 ? limit.value : undefined,
  );
  res.json(results);
}));

app.post('/extraction/run', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  if (!project) return errorJson(res, 400, 'project is required');
  await client.triggerExtraction(project);
  res.json({ status: 'extraction triggered' });
}));

// ==================== Refine / Feedback ====================

app.post('/refine', asyncHandler(async (req: Request, res: Response) => {
  const project = req.query.project as string;
  if (!project) return errorJson(res, 400, 'project is required');
  await client.triggerRefinement(project);
  res.json({ status: 'refined' });
}));

app.post('/feedback', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['observationId', 'feedbackType']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  await client.submitFeedback({
    observationId: req.body.observationId,
    feedbackType: req.body.feedbackType,
    comment: req.body.comment,
  });
  res.json({ status: 'submitted' });
}));

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
}));

app.patch('/session/user', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['session_id', 'user_id']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  const result = await client.updateSessionUserId(req.body.session_id, req.body.user_id);
  res.json(result);
}));

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
}));

app.post('/ingest/session-end', asyncHandler(async (req: Request, res: Response) => {
  const missing = requireFields(req.body, ['project', 'session_id']);
  if (missing) return errorJson(res, 400, `${missing} is required`);
  await client.recordSessionEnd({
    session_id: req.body.session_id,
    cwd: req.body.project,
    last_assistant_message: req.body.last_assistant_message,
  });
  res.json({ status: 'ended' });
}));

// ==================== Async error handler ====================

// Global error handler (asyncHandler catches async rejections, this catches sync errors)
app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
  // body-parser signals a body it could not accept by throwing rather than by
  // calling next() with a value. Those are client errors and the other three
  // demos answer 400/413 — falling through to the generic branch reported them
  // as 500 and echoed the raw parser message back.
  //
  // The two rejections are different types, so matching on the class alone is
  // not enough: unparsable JSON is a SyntaxError (status 400), while a body
  // over the 1mb limit is a PayloadTooLargeError (status 413). An earlier fix
  // here tested `err instanceof SyntaxError && (status === 400 || 413)`, which
  // reads as if it covers both but can only ever match the first — the 413 arm
  // was dead, and a 2mb POST answered 500 {"error":"request entity too large"}.
  // Python answers 413 for the same request and Go 400. Key off the status the
  // parser attached, which both types carry, and keep the class check as a guard
  // so an unrelated 4xx thrown from a handler is not relabelled as a body error.
  const bodyStatus = (err as { status?: unknown } | null | undefined)?.status;
  const isBodyParserError = err instanceof SyntaxError || (err as { type?: unknown })?.type === 'entity.too.large';
  if (isBodyParserError && (bodyStatus === 400 || bodyStatus === 413)) {
    errorJson(res, bodyStatus, err.message);
    return;
  }
  console.error('Unhandled error:', err);
  if (err instanceof ValidationError) {
    errorJson(res, 400, err.message);
  } else if (err instanceof APIError) {
    errorJson(res, err.statusCode, err.message);
  } else {
    const message = err instanceof Error ? err.message : 'Internal server error';
    errorJson(res, 500, message);
  }
});

// ==================== Start ====================

// Only bind a port when this file is the entry point. parseIntParam is exported
// so its rule can be unit-tested, and importing the module for a test must not
// start a server or leave vitest hanging on an open handle.
const isEntryPoint = process.argv[1]
  ? import.meta.url === pathToFileURL(process.argv[1]).href
  : false;

const server = isEntryPoint ? app.listen(PORT, () => {
  console.log(`🚀 JS SDK HTTP server starting on :${PORT}`);
  console.log(`   Backend: ${CORTEX_BASE_URL}`);
  console.log();
  console.log('Endpoints:');
  console.log('  GET    /health              - Health check');
  console.log('  POST   /chat                - Chat with memory');
  console.log('  GET    /search              - Search observations (supports orderBy)');
  console.log('  GET    /version             - Backend version');
  console.log('  GET    /experiences         - Retrieve experiences');
  console.log('  GET    /iclprompt           - Build ICL prompt');
  console.log('  GET    /observations        - List observations');
  console.log('  GET    /observations/:id    - Get observation by ID');
  console.log('  POST   /observations/batch  - Batch get observations by IDs');
  console.log('  POST   /observations/create - Record observation');
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
  console.log('  POST   /ingest/prompt       - Ingest user prompt');
  console.log('  POST   /ingest/session-end  - Ingest session end');
})
  : null;

// ==================== Graceful shutdown ====================

function shutdown(signal: string) {
  console.log(`\n${signal} received, shutting down gracefully...`);
  // server is null when this module was imported rather than run (see isEntryPoint).
  server?.close(() => {
    client.close();
    console.log('Server closed.');
    process.exit(0);
  });
  // Force exit after 5s if connections persist
  setTimeout(() => {
    console.error('Forced shutdown after timeout');
    process.exit(1);
  }, 5_000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
