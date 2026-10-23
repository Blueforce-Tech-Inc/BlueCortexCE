import { strict as assert } from 'node:assert';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import axios from 'axios';

const PROXY_WRAPPER_PATH = resolve(dirname(fileURLToPath(import.meta.url)), '../../wrapper.js');

const CONFIG = {
  timeout: 10000,
  tagPattern: /<claude-mem-context>([\s\S]*?)<\/claude-mem-context>/,
};

function readConfig() {
  const rawServerUrl = process.env.PROXY_E2E_SERVER_URL;
  if (!rawServerUrl) {
    throw new Error('Set PROXY_E2E_SERVER_URL to the backend under test.');
  }
  const expectedVersion = process.env.PROXY_E2E_EXPECTED_VERSION;
  if (!expectedVersion) {
    throw new Error('Set PROXY_E2E_EXPECTED_VERSION to the exact /api/version value expected.');
  }

  const parsedUrl = new URL(rawServerUrl);
  if (!['http:', 'https:'].includes(parsedUrl.protocol)
      || parsedUrl.username || parsedUrl.password || parsedUrl.search || parsedUrl.hash
      || (parsedUrl.pathname !== '/' && parsedUrl.pathname !== '')) {
    throw new Error('PROXY_E2E_SERVER_URL must be an HTTP(S) origin without credentials, path, query, or fragment.');
  }

  return {
    javaUrl: parsedUrl.toString().replace(/\/+$/, ''),
    expectedService: process.env.PROXY_E2E_EXPECTED_SERVICE || 'claude-mem-java',
    expectedVersion,
    tempRoot: resolve(process.env.PROXY_E2E_TMPDIR || tmpdir()),
  };
}

async function verifyBackendIdentity(config) {
  const health = await axios.get(`${config.javaUrl}/api/health`, { timeout: CONFIG.timeout });
  assert.equal(health.data?.service, config.expectedService,
    `Unexpected backend service: ${health.data?.service ?? 'missing'}`);
  assert.equal(health.data?.status, 'ok', 'Backend health status is not ok.');

  const version = await axios.get(`${config.javaUrl}/api/version`, { timeout: CONFIG.timeout });
  assert.equal(version.data?.service, config.expectedService,
    `Unexpected version endpoint service: ${version.data?.service ?? 'missing'}`);
  assert.equal(version.data?.version, config.expectedVersion,
    `Unexpected backend version: ${version.data?.version ?? 'missing'}`);

  console.log(`Target verified: ${config.javaUrl}`);
  console.log(`Service: ${health.data.service}; version: ${version.data.version}`);
}

function runProxyHook(config, cwd, command, event) {
  const result = spawnSync(process.execPath, [PROXY_WRAPPER_PATH, command, '--url', config.javaUrl], {
    cwd,
    input: JSON.stringify(event),
    encoding: 'utf8',
    timeout: 20000,
    maxBuffer: 1024 * 1024,
  });

  if (result.error) throw result.error;
  assert.equal(result.status, 0,
    `${command} wrapper failed (status ${result.status}): ${result.stderr}`);
  assert.doesNotMatch(result.stderr, /Cannot connect to Java API|\bAPI error\b/,
    `${command} wrapper reported an API error: ${result.stderr}`);
  return result;
}

function updateClaudeMd(filePath, newContent) {
  const existing = readFileSync(filePath, 'utf8');
  if (CONFIG.tagPattern.test(existing)) {
    const updated = existing.replace(
      CONFIG.tagPattern,
      `<claude-mem-context>\n${newContent}\n</claude-mem-context>`
    );
    writeFileSync(filePath, updated, 'utf8');
  } else {
    const separator = existing.endsWith('\n') ? '' : '\n';
    writeFileSync(
      filePath,
      existing + separator + `<claude-mem-context>\n${newContent}\n</claude-mem-context>\n`,
      'utf8'
    );
  }
}

function resolveWorkspaceFile(cwd, filePath) {
  assert.equal(typeof filePath, 'string', 'updateFiles entry must contain a path.');
  const targetPath = resolve(filePath);
  const relativePath = relative(cwd, targetPath);
  assert.ok(relativePath && relativePath !== '..' && !relativePath.startsWith(`..${sep}`)
    && !isAbsolute(relativePath), `Refusing updateFiles path outside the temporary workspace: ${filePath}`);
  return targetPath;
}

async function main() {
  const config = readConfig();
  let cwd;
  try {
    await verifyBackendIdentity(config);

    mkdirSync(config.tempRoot, { recursive: true });
    cwd = mkdtempSync(join(config.tempRoot, 'claude-mem-proxy-e2e-'));
    writeFileSync(join(cwd, 'CLAUDE.md'), '# Isolated proxy E2E fixture\n', 'utf8');
    const sessionId = `proxy-e2e-${randomUUID()}`;

    console.log('=== Testing Full Flow ===');
    console.log('Session ID:', sessionId);
    console.log('Temporary working directory:', cwd);

    const sessionStart = runProxyHook(config, cwd, 'session-start', {
      session_id: sessionId,
      cwd,
    });
    const storedSession = await axios.get(`${config.javaUrl}/api/session`, {
      params: { contentSessionId: sessionId, platformSource: 'claude' },
      timeout: CONFIG.timeout,
    });
    assert.equal(storedSession.data?.content_session_id, sessionId,
      'SessionStart wrapper did not create the expected Conversation.');
    assert.match(storedSession.data?.session_db_id ?? '',
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
      'SessionStart wrapper did not create a Conversation UUID.');
    assert.ok(sessionStart.stderr.includes(`Session initialized: ${storedSession.data.session_db_id}`),
      'SessionStart wrapper did not report the backend-created Conversation.');

    const promptText = `Proxy E2E prompt ${randomUUID()}`;
    const userPrompt = runProxyHook(config, cwd, 'user-prompt', {
      session_id: sessionId,
      cwd,
      prompt: promptText,
      prompt_number: 1,
    });
    assert.match(userPrompt.stderr, /User prompt recorded/,
      'UserPromptSubmit wrapper did not report a successful API response.');
    const prompts = await axios.get(`${config.javaUrl}/api/prompts`, {
      params: {
        contentSessionId: sessionId,
        platformSource: 'claude',
        limit: 20,
      },
      timeout: CONFIG.timeout,
    });
    assert.ok(prompts.data?.items?.some((item) => item.prompt_text === promptText),
      'UserPromptSubmit wrapper did not persist the submitted prompt.');

    const response = await axios.post(`${config.javaUrl}/api/session/start`, {
      session_id: sessionId,
      project_path: cwd,
      debug: true,
    }, { timeout: CONFIG.timeout });
    const data = response.data;

    assert.equal(data?.session_id, sessionId, 'SessionStart response did not preserve session_id.');
    assert.equal(typeof data?.context, 'string', 'SessionStart response is missing context.');
    assert.ok(Array.isArray(data?.updateFiles), 'SessionStart response is missing camelCase updateFiles.');
    assert.match(data?.session_db_id ?? '', /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
      'SessionStart response is missing a valid session_db_id.');
    assert.equal(data.session_db_id, storedSession.data.session_db_id,
      'Direct and proxy SessionStart calls resolved different Conversation identities.');

    console.log('\nAPI response verified');
    console.log('Has context:', data.context.length > 0);
    console.log('updateFiles:', data.updateFiles.length);
    if (data.context) {
      console.log('\n=== CONTEXT OUTPUT (stdout for Claude) ===');
      console.log(data.context);
    }

    for (const file of data.updateFiles) {
      const targetPath = resolveWorkspaceFile(cwd, file.path);
      assert.equal(typeof file.content, 'string', 'updateFiles entry must contain string content.');
      updateClaudeMd(targetPath, file.content);
      console.log('Updated isolated file:', relative(cwd, targetPath));
    }

    console.log('\n=== Test Complete ===');
  } finally {
    if (cwd && existsSync(cwd)) {
      rmSync(cwd, { recursive: true, force: true });
    }
  }
}

main().catch((error) => {
  const status = error.response?.status;
  console.error(`Proxy full-flow E2E failed${status ? ` (HTTP ${status})` : ''}: ${error.message}`);
  process.exitCode = 1;
});
