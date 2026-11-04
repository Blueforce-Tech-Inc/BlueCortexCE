#!/usr/bin/env node
/**
 * Browser-level end-to-end check of the bundled viewer.
 *
 * Not a unit test and never part of the build: it drives the real backend with a
 * real Chromium so that "the page renders" is proven in a browser instead of
 * inferred from an API response. Requirements checked:
 *
 *   1. the page renders the timeline with observation cards
 *   2. `files_modified` written through the ingest API is readable on the card
 *   3. pagination follows `hasMore` (50 -> all seeded rows, then "No more")
 *   4. a record created after load is visible after a plain refresh
 *   5. a settings toggle keeps its new value after a refresh
 *
 * Playwright is a test-only tool; install it once with
 * `(cd scripts/browser && npm install --no-save playwright && npx playwright install --with-deps chromium)`.
 * It is not a production dependency.
 */

import { chromium } from 'playwright';
import assert from 'node:assert/strict';

const base = (process.env.BASE_URL || 'http://127.0.0.1:37778').replace(/\/$/, '');
const project = process.env.PROJECT_PATH || '/tmp/cortexce-webui-browser-e2e';
const seedCount = Number(process.env.SEED_COUNT || 60);
const pageSize = Number(process.env.PAGE_SIZE || 50);
const screenshot = process.env.SCREENSHOT || '';

function fail(message, pageErrors = []) {
  console.error(`FAIL: ${message}`);
  if (pageErrors.length > 0) console.error(`PAGE ERRORS: ${pageErrors.join('; ')}`);
  process.exit(1);
}

let pageErrors = [];

const browser = await chromium.launch({ headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  page.on('pageerror', error => pageErrors.push(String(error)));

  // The viewer keeps a long-lived SSE connection to /stream, so `networkidle`
  // never settles; wait for DOM plus the first rendered card instead.
  const sseConnected = page.waitForEvent('console', {
    predicate: message => message.text().includes('[SSE] Connected'),
    timeout: 30000,
  });
  await page.goto(`${base}/`, { waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForSelector('.card-title', { timeout: 20000 });
  await sseConnected;
  assert.ok((await page.locator('body').innerText()).includes('Timeline'), 'view tabs render');
  console.log('PASS: page rendered with observation cards and an SSE connection');

  const cards = page.locator('.card-title');
  const firstPage = await cards.count();
  assert.equal(firstPage, pageSize, `first page should hold ${pageSize} cards, saw ${firstPage}`);
  console.log(`PASS: first page holds ${firstPage} cards`);

  await page.locator('.view-mode-toggle').first().click();
  await page.waitForSelector('.meta-files', { timeout: 10000 });
  const modified = await page.locator('.meta-files').first().innerText();
  assert.match(modified, /modified:/, `expected a modified file list, saw "${modified}"`);
  // The card strips the project root, so the seeded file shows up as src/seed-*.ts.
  assert.match(modified, /src\/seed-/, `expected a seeded file path, saw "${modified}"`);
  console.log(`PASS: files_modified readable in the viewer (${modified.trim()})`);

  // Scroll the feed itself: wheel events depend on the mouse position, which is
  // flaky in CI, while setting scrollTop is deterministic.
  for (let round = 0; round < 20; round += 1) {
    if (await page.locator('text=No more items to load').count() > 0) break;
    await page.evaluate(() => {
      const feed = document.querySelector('.feed');
      if (feed) feed.scrollTop = feed.scrollHeight;
      window.scrollTo(0, document.body.scrollHeight);
    });
    await page.waitForTimeout(500);
  }
  await page.waitForSelector('text=No more items to load', { timeout: 30000 });
  const lastPage = await cards.count();
  assert.equal(lastPage, seedCount, `pagination should reach all ${seedCount} rows, saw ${lastPage}`);
  console.log(`PASS: hasMore pagination reached all ${lastPage} rows`);

  const paged = await (await page.request.get(`${base}/api/observations?offset=0&limit=5`)).json();
  assert.equal(paged.items.length, 5, 'limit honoured');
  assert.equal(paged.hasMore, true, 'hasMore must stay true while rows remain');
  console.log('PASS: /api/observations keeps the {items,hasMore} envelope');

  // SSE: write through the API while the page stays open. The viewer must merge
  // the broadcast row without a reload (the only /stream path browsers exercise).
  const liveTitle = `Browser e2e live ${Date.now()}`;
  const liveCreated = await page.request.post(`${base}/api/ingest/observation`, {
    data: {
      content_session_id: 'browser-e2e-session',
      project_path: project,
      type: 'change',
      title: liveTitle,
      subtitle: 'Created after the page was already open',
      facts: ['The SSE broadcast should surface this title without a refresh'],
      concepts: ['what-changed', 'pattern'],
      files_modified: [`${project}/src/browser-live.ts`],
    },
  });
  assert.equal(liveCreated.status(), 200, `live observation create returned ${liveCreated.status()}`);
  await page.waitForSelector(`text=${liveTitle}`, { timeout: 20000 });
  const afterLive = await cards.count();
  assert.ok(afterLive >= lastPage + 1,
    `SSE should append a live card without a reload, saw ${afterLive} after ${lastPage}`);
  console.log('PASS: a new observation appears over SSE without a refresh');

  const title = `Browser e2e record ${new Date().toISOString()}`;
  const created = await page.request.post(`${base}/api/ingest/observation`, {
    data: {
      content_session_id: 'browser-e2e-session',
      project_path: project,
      type: 'change',
      title,
      subtitle: 'Created after the first page loaded',
      facts: ['Browser e2e refresh check recorded the new title'],
      concepts: ['what-changed', 'pattern'],
      files_modified: [`${project}/src/browser-refresh.ts`],
    },
  });
  assert.equal(created.status(), 200, `observation create returned ${created.status()}`);
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForSelector(`text=${title}`, { timeout: 20000 });
  const afterReload = await cards.count();
  assert.equal(afterReload, pageSize, 'refresh shows the same first-page size');
  console.log('PASS: a record created after load is visible after refresh');

  // Settings round trip: the toggles compare strings with `=== 'true'`, so a
  // boolean in the API response renders an enabled switch as off.
  await page.locator('.settings-btn').click();
  await page.waitForSelector('.context-settings-modal', { timeout: 10000 });
  const toggle = page.locator('#show-read-tokens');
  const initial = await toggle.getAttribute('aria-checked');
  assert.equal(initial, 'true',
    `an enabled "Read cost" toggle must render on; aria-checked=${initial}`);
  await toggle.click();
  assert.equal(await toggle.getAttribute('aria-checked'), 'false', 'toggle must turn off');
  await page.locator('.save-btn').click();
  await page.waitForSelector('text=✓ Saved', { timeout: 10000 });
  await page.reload({ waitUntil: 'domcontentloaded', timeout: 45000 });
  await page.waitForSelector('.card-title', { timeout: 20000 });
  await page.locator('.settings-btn').click();
  await page.waitForSelector('.context-settings-modal', { timeout: 10000 });
  const persisted = await page.locator('#show-read-tokens').getAttribute('aria-checked');
  assert.equal(persisted, 'false', `saved toggle must survive a refresh; aria-checked=${persisted}`);
  console.log('PASS: a settings toggle survives change -> save -> refresh');

  if (pageErrors.length > 0) fail(`PAGE ERROR: ${pageErrors.join('; ')}`, pageErrors);
  if (screenshot) {
    await page.screenshot({ path: screenshot, fullPage: false });
    console.log(`screenshot: ${screenshot}`);
  }
  console.log('RESULT: PASS');
} catch (error) {
  const kind = error.code === 'ERR_ASSERTION' ? 'ASSERTION FAILED' : 'SCRIPT ERROR';
  fail(`${kind}: ${error.message}`, pageErrors);
} finally {
  await browser.close();
}
