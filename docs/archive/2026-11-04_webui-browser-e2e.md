# Round 20: real-browser end-to-end for the bundled viewer

- Date: 2026-11-04
- Gap covered: every previous WebUI check was API-level. Nothing proved that the
  bundled viewer actually renders in a browser, that its pagination consumes
  `hasMore`, that written file lists reach a card, or that new rows appear on
  refresh.
- Tooling: headless Chromium via Playwright, run locally only. No production
  dependency was added (`scripts/browser/webui-viewer-e2e.mjs` imports
  `playwright`, which each developer installs once).

## How it runs

```bash
./scripts/webui-browser-e2e.sh
# optional: WEBUI_E2E_PORT=37800 SCREENSHOT=/tmp/viewer.png ./scripts/webui-browser-e2e.sh
```

The script creates a disposable `cortexce_browser_<timestamp>_flyway_rehearsal`
database, boots the packaged jar on a free port, starts a session (asserting the
`updateFiles` array is present), seeds 60 observations through
`POST /api/ingest/observation`, drives the browser, then drops the database and
stops the app. `KEEP_DB=1` keeps the database for inspection.

## Assertions and result

| # | Assertion | Result |
|---|---|---|
| 1 | The page renders: view tabs plus observation cards | PASS (50 cards on first page) |
| 2 | `files_modified` written through ingest is readable on the card | PASS (`modified: src/seed-60.ts`) |
| 3 | Pagination follows `hasMore` from 50 to all 60 rows, then "No more items to load" | PASS |
| 4 | `GET /api/observations` keeps the `{items,hasMore}` envelope | PASS |
| 5 | A record created after load is visible after a plain refresh | PASS |
| 6 | No browser `pageerror` during the whole flow | PASS (after the fix below) |

Run log evidence: six `PASS` lines followed by `ALL CHECKS PASSED`.

## Bug found and fixed

The first run failed assertion 6 with:

```
TypeError: e.CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES?.trim is not a function
```

`AppSettings.toMap()` returned a JSON array for
`CLAUDE_MEM_CONTEXT_OBSERVATION_TYPES` / `_CONCEPTS` and numbers for
`CLAUDE_MEM_CONTEXT_OBSERVATIONS` / `_FULL_COUNT` / `_SESSION_COUNT`, while the
fork's viewer treats those settings as env-style strings and calls `.trim()`
(`webui/src/ui/viewer/hooks/useContextPreview.ts`). The API-level checks could
never see this: only a browser executes that code path.

`AppSettings.toMap()` now returns the raw string values for those five keys, and
`WebUiWireContractTest` asserts their Java type is `String` so the regression
cannot come back silently. `docs/API.md` and `docs/API-zh-CN.md` examples were
updated to match.

## Not covered

- Drag/scroll beyond the feed, settings editing round-trips, SSE live updates in
  the browser, and WebUI `npm run build` drift are still untested.
- The viewer's boolean display toggles are left unchanged: they do not throw, and
  converting them without a failing case would be a guess.
