#!/usr/bin/env node
/**
 * Browser check: the viewer while the backend process is stopped and after it
 * comes back.
 *
 * The viewer is served by the same Spring Boot process, so a *cold* page load
 * while the backend is down is a browser-level network error page, not app UI.
 * What this checks is the case the app controls: a page that is already open
 * when the backend disappears, then a data request (pagination) is triggered.
 *
 *   1. stop the backend -> pagination fails -> a readable alert appears with a
 *      Retry button, and no spinner is left running
 *   2. start it again -> Retry succeeds, the missing page arrives, the alert
 *      disappears and the SSE connection logs a reconnect
 *
 * This script owns the application process for its phase; the caller passes the
 * jar, port and datasource through E2E_APP_* variables.
 */

import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import assert from 'node:assert/strict';

const base = (process.env.BASE_URL || 'http://127.0.0.1:37778').replace(/\/$/, '');
const jar = process.env.E2E_APP_JAR;
const port = process.env.E2E_APP_PORT;
const dbUrl = process.env.E2E_APP_DB_URL;
const dbUser = process.env.E2E_APP_DB_USER;
const dbPassword = process.env.E2E_APP_DB_PASSWORD;
const dataDir = process.env.E2E_APP_DATA_DIR;
const logFile = process.env.E2E_APP_LOG || '/tmp/cortexce-e2e-app.log';

if (!jar || !port || !dbUrl) {
  console.error('FAIL: SCRIPT ERROR: E2E_APP_JAR, E2E_APP_PORT and E2E_APP_DB_URL must be set');
  process.exit(1);
}

let app = null;
const pageErrors = [];

function launch() {
  const log = fs.openSync(logFile, 'a');
  const child = spawn('java', ['-jar', jar, `--server.port=${port}`], {
    env: {
      ...process.env,
      SPRING_DATASOURCE_URL: dbUrl,
      SPRING_DATASOURCE_USERNAME: dbUser || 'postgres',
      SPRING_DATASOURCE_PASSWORD: dbPassword || '',
      SERVER_PORT: String(port),
      CLAUDE_MEM_DATA_DIR: dataDir || '/tmp/cortexce-e2e-data',
      CORTEXCE_API_TOKEN: '',
    },
    stdio: ['ignore', log, log],
  });
  return child;
}

async function healthy() {
  try {
    const response = await fetch(`${base}/api/health`);
    return response.ok;
  } catch {
    return false;
  }
}

async function waitFor(condition, timeoutMs, what) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await condition()) return;
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`timed out after ${timeoutMs}ms waiting for ${what}`);
}

async function stopApp() {
  if (app === null) return;
  const child = app;
  app = null;
  const exited = new Promise(resolve => child.once('exit', resolve));
  child.kill('SIGTERM');
  await Promise.race([exited, new Promise(resolve => setTimeout(resolve, 20000))]);
  await waitFor(async () => !(await healthy()), 30000, 'the backend to stop answering');
}

async function startApp() {
  app = launch();
  await waitFor(healthy, 60000, 'the backend to become healthy');
}

let browser = null;
try {
  await startApp();
  console.log('PASS: backend started for the offline scenario');

  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('pageerror', error => pageErrors.push(String(error)));
  const consoleLines = [];
  page.on('console', message => consoleLines.push(message.text()));

  const sseConnected = page.waitForEvent('console', {
    predicate: message => message.text().includes('[SSE] Connected'),
    timeout: 30000,
  });
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForSelector('.card-title', { timeout: 20000 });
  await sseConnected;
  const cards = page.locator('.card-title');
  const before = await cards.count();
  assert.ok(before > 0, 'the first page should have rendered before the outage');
  console.log(`PASS: page open with ${before} cards before the outage`);

  // 1. Backend stops while the page stays open.
  await stopApp();
  console.log('PASS: backend stopped');

  // Ask for the next page. The sentinel is observed against the viewport, so
  // scroll both the feed and the window, like the main pagination check does.
  await page.evaluate(() => {
    const feed = document.querySelector('.feed');
    if (feed) feed.scrollTop = feed.scrollHeight;
    window.scrollTo(0, document.body.scrollHeight);
  });

  const alert = page.locator('.feed [role="alert"]');
  await alert.waitFor({ state: 'visible', timeout: 25000 });
  const alertText = (await alert.innerText()).trim();
  assert.ok(alertText.length > 0, 'the failure alert must carry a message');
  assert.match(alertText, /fetch|network|reach|load|connect/i,
    `the alert should describe a connection problem, saw "${alertText}"`);
  assert.equal(await page.locator('.feed .spinner').count(), 0,
    'the feed must not be stuck on the loading spinner');
  assert.ok((await page.locator('body').innerText()).includes('Timeline'),
    'the page must stay rendered instead of going blank');
  console.log(`PASS: outage shows a readable alert without a spinner ("${alertText}")`);

  // 2. Backend comes back: retry, then the SSE connection reconnects.
  const reconnected = page.waitForEvent('console', {
    predicate: message => message.text().includes('[SSE] Connected'),
    timeout: 30000,
  });
  await startApp();
  console.log('PASS: backend restarted');

  await page.locator('.feed [role="alert"] button').click();
  await waitFor(async () => (await cards.count()) > before, 20000,
    `pagination to resume past ${before} cards`);
  await reconnected;
  assert.equal(await page.locator('.feed [role="alert"]').count(), 0,
    'the alert must disappear after a successful retry');
  const after = await cards.count();
  console.log(`PASS: viewer recovered after restart (${before} -> ${after} cards, SSE reconnected)`);

  assert.equal(pageErrors.length, 0, `no page errors expected, saw ${pageErrors.join('; ')}`);
  console.log('RESULT: PASS');
} catch (error) {
  console.error(`FAIL: ${error.code === 'ERR_ASSERTION' ? 'ASSERTION FAILED' : 'SCRIPT ERROR'}: ${error.message}`);
  if (pageErrors.length > 0) console.error(`PAGE ERRORS: ${pageErrors.join('; ')}`);
  process.exitCode = 1;
} finally {
  if (browser !== null) await browser.close();
  await stopApp().catch(() => {});
}
