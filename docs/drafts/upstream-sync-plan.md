# BlueCortexCE Upstream Sync Plan

**Date**: 2026-10-06
**Status**: Draft v8 — implementation and runtime verification complete; one concurrency gap remains
**Positioning**: Experimental reference; inspiration and feature-gap analysis only
**Backend source**: `https://github.com/wubuku/claude-mem-fork.git`
**WebUI source**: `https://github.com/Blueforce-Tech-Inc/claude-mem.git`
**Backend reference commit**: `ed37a1b227b6067befe5c9ada331989d02cbfad0`
**Paired WebUI commit**: `72e7804b13f89b03177f746867e0dd341fb12c8a`

The local checkout used for comparison is represented here as
`<local-upstream-path>` and is intentionally not recorded as a machine-specific
path in tracked documentation.

## Executive Summary

The latest reference changes describe a coordinated backend/WebUI capability
set: UUID-based feed records, platform-aware session identity, a session
catalog, deletion flows, and live deletion events. BlueCortexCE selectively
adapts the parts that fit its PostgreSQL/JPA architecture and preserves its
existing `content_session_id` schema and API contracts.

“Sync upstream” in this repository means **study, compare, review, and adapt**.
The Java port is experimental: it has no required implementation-quality level
and provides no guarantee of production readiness, operational safety,
security, compatibility, or correctness. Its code, migrations, tests, and
designs are research material, not a production authority or an automatic
merge source.

The `webui/` submodule is deliberately paired with the backend behavior under
review. It must not be advanced to an unrelated “latest” WebUI commit.

## Current State

| Component | Current observation | Decision |
|-----------|--------------------|----------|
| Backend reference | `ed37a1b227b6067befe5c9ada331989d02cbfad0` | Study as experimental reference only |
| WebUI reference | `72e7804b13f89b03177f746867e0dd341fb12c8a` | Pair with the adapted backend contract |
| Local migration baseline | `V18__add_platform_source.sql` | Implemented `V19__viewer_session_indexes.sql` |
| Current WebUI worktree | Checked out at `72e7804b13f89b03177f746867e0dd341fb12c8a` | Parent gitlink change is intentional and must be retained |
| Existing platform model | `platform_source` plus canonical `content_session_id` | Preserve existing V18/V13 behavior; do not duplicate migrations |

The parent repository currently records the previous WebUI gitlink in `HEAD`
while the working tree has the paired commit checked out. The gitlink change is
intentional; the user-requested commit step must record it in the parent
repository so the checked-out WebUI and the parent pointer agree.

## Feature Gap Analysis

### Adapted

| Reference idea | BlueCortexCE result | Review status |
|----------------|---------------------|---------------|
| UUID feed/session records and stable ordering | Observation, summary, and prompt pagination now accept `contentSessionId`, use UUID tie-break ordering, and preserve `items`/`hasMore` | Implemented |
| Platform-aware feed filtering | Viewer feeds and context preview accept `platformSource`; legacy null values are treated as `claude` | Implemented |
| Session catalog | Added `GET /api/sessions` with project/source filters, offset pagination, and item counts | Implemented |
| Session and item deletion | Added paired WebUI deletion routes for sessions, observations, and summaries | Implemented |
| Deletion safety | Protects active/queued/processing/summarizing sessions, pending work, and in-flight summary generation | Implemented; runtime concurrency test remains pending |
| Post-commit live updates | Emits `item_deleted` and `session_deleted` SSE payloads after successful transactions | Implemented |
| Platform propagation | New sessions, prompts, observations, and summaries inherit the session source | Implemented |
| Viewer settings compatibility | Exposes `CLAUDE_MEM_BACKEND=java` without changing existing `CLAUDE_MEM_*` keys | Implemented |
| Query performance | Adds V19 indexes for session ordering and per-session feed reads | Implemented |

### Intentionally not copied

- The experimental repository is not merged wholesale, and its quality is not
  treated as evidence of production readiness.
- V17 and V18 are already present locally; no duplicate migrations are added.
- Prompt deletion remains read-only in the paired WebUI because deleting one
  prompt can invalidate the session's derived prompt numbering.
- Cloud-sync tombstones and remote-device deletion rules are not claimed by
  this local PostgreSQL backend; the implementation deletes local rows only.
- The existing schema keeps `content_session_id` globally unique. Full support
  for the same content ID appearing under multiple platform sources would
  require a separate identity/migration decision and is not silently implied by
  adding source filters.

### Known verification gaps

- The deletion guard needs an integration test that races a queued or running
  worker against a viewer deletion request.
- The targeted WebUI tests use local route fixtures and browser fixtures; they
  validate the WebUI contract and deletion lifecycle, while the live Java
  service checks below validate the backend HTTP/SSE surface separately.

## Adaptation Plan

1. Inspect the experimental backend reference and paired WebUI routes without
   modifying either reference checkout.
2. Confirm the local migration maximum before adding schema changes.
3. Implement the backend contract in dependency order: migration, repositories,
   services, controllers, async guards, and events.
4. Verify WebUI consumers for response names, UUID IDs, query parameters, and
   SSE payloads before changing the parent gitlink.
5. Keep the parent `webui` gitlink at the known paired commit, not merely at a
   newer remote tip.
6. Run narrow and broad checks, then document passed, failed, skipped, and
   unavailable verification explicitly.

## Implementation Summary (2026-10-06)

### Completed

| Adapted idea | Status | Main files |
|--------------|--------|------------|
| Viewer feed filters and deterministic pagination | Done | `ViewerController.java`, `ObservationRepository.java`, `SummaryRepository.java`, `UserPromptRepository.java` |
| Session catalog | Done | `ViewerSessionController.java`, `ViewerSessionService.java` |
| Viewer deletion routes and transaction guards | Done | `ViewerSessionService.java`, `SessionRepository.java`, `PendingMessageRepository.java` |
| Post-commit deletion SSE events | Done | `ViewerSessionService.java` |
| Source propagation and settings compatibility | Done | `IngestionController.java`, `SessionController.java`, `AgentService.java`, `SummaryGenerationService.java`, `AppSettings.java` |
| Platform-aware context preview | Done | `ContextController.java`, `ContextService.java`, `ObservationRepository.java`, `SummaryRepository.java` |
| Viewer indexes | Done | `V19__viewer_session_indexes.sql` |
| Paired WebUI pointer | Checked out | `webui` → `72e7804b13f89b03177f746867e0dd341fb12c8a` |

### Independent Review

- **Production-readiness assessment**: The reference Java port remains
  experimental and is not a production guarantee. Adapted behavior is reviewed
  against BlueCortexCE's own schema and contracts.
- **API and WebUI compatibility**: WebUI routes, viewer types, deletion helpers,
  pagination, and SSE event payloads were inspected. Existing `hasMore`,
  `updateFiles`, and `CLAUDE_MEM_*` contracts remain intact.
- **Migration safety**: The local maximum was V18; V19 is additive and does not
  recreate V17 or V18.
- **Concurrency and failure handling**: Session row locking, pending-work
  checks, in-memory summarization protection, transaction-scoped deletion, and
  after-commit SSE publication were reviewed. Live database race coverage is
  still a gap.
- **Security and privacy**: No new authentication boundary is introduced;
  deletion behavior remains within the existing service trust boundary. The
  reference checkout path is excluded from tracked documentation.
- **Submodule pairing**: The backend reference commit and WebUI commit are
  recorded together. The parent gitlink must be committed at the paired WebUI
  commit before this work is considered clean.

### Build and Test Status

- Compile: passed — `backend/./mvnw clean compile -DskipTests`
- Maven unit tests: passed — 167 tests, 0 failures, 0 errors, 0 skipped
- Formatting/diff whitespace: passed — `git diff --check`
- PostgreSQL/Flyway runtime verification: passed — an isolated rebuilt service
  on port `37778` applied V19 successfully; `flyway_schema_history` is at V19
  and all four viewer indexes exist. The existing service on `37777` was left
  untouched.
- Live Java HTTP/API verification: passed — health, Java backend settings,
  session catalog, project/source metadata, filtered observations/summaries/
  prompts, and safe 404 deletion responses all passed assertions.
- Live Java SSE verification: passed — `/stream` emitted both `initial_load`
  and `processing_status` events.
- WebUI targeted contract tests: passed — installed the missing dependencies
  through the configured proxy and ran 16 tests with 0 failures, including the
  session catalog, deletion routes, and six browser deletion-lifecycle cases.
- Backend reference / paired WebUI commit: `ed37a1b227b6067befe5c9ada331989d02cbfad0` / `72e7804b13f89b03177f746867e0dd341fb12c8a`

### Pending

- [ ] Add or run an integration test for deletion versus queued/async work.
- [ ] Stage and commit the intentional `webui` gitlink together with the
      backend/documentation changes when explicitly requested.

## Testing

The minimum verification set for a future sync is:

```bash
cd backend && ./mvnw clean compile
cd backend && ./mvnw test
cd .. && git diff --check
```

When PostgreSQL and the service are available, additionally exercise:

```bash
curl "http://localhost:37777/api/sessions?platformSource=claude&limit=20&offset=0"
curl "http://localhost:37777/api/observations?platformSource=claude&contentSessionId=<id>"
curl "http://localhost:37777/api/summaries?platformSource=claude&contentSessionId=<id>"
curl "http://localhost:37777/api/prompts?platformSource=claude&contentSessionId=<id>"
```

The WebUI pointer should be checked from the parent repository as well as from
inside the submodule:

```bash
git -C webui rev-parse HEAD
git ls-tree HEAD webui
git diff --submodule=log -- webui
```

## Rollback

V19 only adds indexes, so application rollback does not require destructive
data changes. If the adapted code must be reverted, deploy the previous
application version and restore the parent WebUI gitlink to its previous
committed value. Do not use an unreviewed Flyway undo operation against
production data.

## Document History

| Version | Date | Changes |
|---------|------|---------|
| v7 | 2026-10-06 | Replaced the stale V17/V18 proposal with the paired backend/WebUI implementation plan and verification record |
| v6 | 2026-04-16 | Superseded draft that proposed V17/V18 as new work |
