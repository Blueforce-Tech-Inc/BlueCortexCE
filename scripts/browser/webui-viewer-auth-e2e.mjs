#!/usr/bin/env node
/**
 * Browser check: the viewer when the backend requires an API token but the
 * browser carries none.
 *
 * Reads stay open by design (the timeline and /stream must work without
 * custom headers), so the gated surfaces are exercised here:
 *   GET /api/logs        -> 401 envelope, shown as a readable banner
 *   POST /api/settings   -> 401 envelope, shown as "✗ Error: Unauthorized"
 *
 * The point is that a rejected request never renders as an empty feed:
 * the viewer must show an error and keep working (no white screen, no JS
 * crash). Run against a backend started with CORTEXCE_API_TOKEN=<anything>.
 */

import { chromium } from 'playwright';
import assert from 'node:assert/strict';

const base = (process.env.BASE_URL || 'http://127.0.0.1:37778').replace(/\/$/, '');

const pageErrors = [];

const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('pageerror', error => pageErrors.push(String(error)));

  // The gated read must answer 401 with the error envelope, not 200 with an
  // empty list: "unauthorized" and "no logs" must stay distinguishable.
  const gated = await page.request.get(`${base}/api/logs`);
  assert.equal(gated.status(), 401, `GET /api/logs should be 401, saw ${gated.status()}`);
  const gatedBody = await gated.json();
  // The envelope is {error: {code, message}, message} for a missing token.
  const code = typeof gatedBody.error === 'string'
    ? gatedBody.error
    : (gatedBody.error && typeof gatedBody.error.code === 'string' ? gatedBody.error.code : '');
  assert.equal(code, 'unauthorized',
    `401 must carry the unauthorized code, saw ${JSON.stringify(gatedBody)}`);
  assert.ok(!('logs' in gatedBody), 'a rejected request must not return an empty logs payload');
  assert.match(gatedBody.message, /bearer token/i,
    `the envelope must name the auth problem, saw ${JSON.stringify(gatedBody)}`);
  console.log(`PASS: gated GET /api/logs answers 401 (${code}: ${gatedBody.message})`);

  // Reads stay open so the timeline itself renders; this is the documented
  // trade-off that keeps /stream usable without custom headers.
  const feed = await page.request.get(`${base}/api/observations?offset=0&limit=5`);
  assert.equal(feed.status(), 200, `timeline reads stay open, saw ${feed.status()}`);
  console.log('PASS: timeline reads stay open without a token (by design)');

  const sseConnected = page.waitForEvent('console', {
    predicate: message => message.text().includes('[SSE] Connected'),
    timeout: 30000,
  });
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForSelector('.card-title', { timeout: 20000 });
  await sseConnected;
  console.log('PASS: viewer renders observations with a token-protected backend');

  // Gated read through the UI: the logs drawer must show the rejection.
  await page.locator('.console-toggle-btn').click();
  await page.waitForSelector('.console-error', { timeout: 15000 });
  const banner = await page.locator('.console-error').innerText();
  // Servlet 6 dropped `setStatus(int, String)`, so Tomcat sends "HTTP/1.1 401 "
  // without a reason phrase and the drawer's statusText suffix is empty. The
  // banner must still appear and say that the fetch failed.
  assert.match(banner, /Failed to fetch logs/i,
    `logs drawer should explain the rejection, saw "${banner}"`);
  console.log(`PASS: logs drawer shows a visible rejection (${banner.trim()})`);
  // The drawer overlays the header (it covers even its own toggle), so close it
  // with its ✕ control before reaching for the settings button.
  await page.locator('.console-control-btn[title="Close console"]').click();
  await page.locator('.console-error').waitFor({ state: 'hidden', timeout: 10000 });

  // Gated write through the UI: saving settings must report the rejection
  // instead of pretending the save succeeded.
  await page.locator('.settings-btn').click();
  await page.waitForSelector('.context-settings-modal', { timeout: 10000 });
  await page.locator('.save-btn').click();
  await page.waitForSelector('.save-status .error', { timeout: 15000 });
  const saveStatus = await page.locator('.save-status .error').innerText();
  assert.match(saveStatus, /Error/,
    `settings save should report the failure, saw "${saveStatus}"`);
  assert.match(saveStatus, /Unauthorized|unauthorized|401|token/i,
    `settings save should name the auth problem, saw "${saveStatus}"`);
  console.log(`PASS: settings save reports the rejection (${saveStatus.trim()})`);

  assert.equal(pageErrors.length, 0, `no page errors expected, saw ${pageErrors.join('; ')}`);
  console.log('PASS: no JavaScript errors on the error paths');
  console.log('RESULT: PASS');
} catch (error) {
  console.error(`FAIL: ${error.code === 'ERR_ASSERTION' ? 'ASSERTION FAILED' : 'SCRIPT ERROR'}: ${error.message}`);
  if (pageErrors.length > 0) console.error(`PAGE ERRORS: ${pageErrors.join('; ')}`);
  process.exitCode = 1;
} finally {
  await browser.close();
}
