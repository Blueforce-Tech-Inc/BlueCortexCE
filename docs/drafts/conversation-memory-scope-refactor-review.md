# Conversation / Memory Scope Refactor Review

> **Purpose**: Track the substantive review findings and one-shot remediation for the conversation/memory-scope refactor.
> **Status**: Local review and remediation complete through R46. The paired WebUI fix is published and this parent change records its gitlink.
> **Scope**: Parent repository `main` after adopting `codex/conversation-memory-scope-refactor` at `3409dda`.
> **Constraints**: V20 remains one atomic Flyway migration. The deployment accepts downtime and manual intervention after a failed migration; rollback, checkpointing, shadow reads, and phased migration are intentionally out of scope and are not review defects.

## Review Baseline

- Parent working tree: `main`, implementation fast-forwarded through `3409dda`.
- Backend change set: conversation identity `(platform_source, content_session_id)` plus internal UUID and memory scopes.
- WebUI invariant: the parent gitlink and staged static Viewer must be the paired version that understands the backend wire contract. A checked-out submodule SHA different from the parent gitlink is not acceptable.
- Review method: source inspection, cross-component search, migration-path analysis, focused tests, and real Flyway/database verification where the local environment permits it.

## Remediation Status

| Findings | Current status | Evidence |
|---|---|---|
| R1–R46 | Remediated in the current local worktree | Backend, database, Viewer, SDK, proxy, agent-client, MCP, and migration checks are recorded below. |
| R25 | Resolved | WebUI fix `2a3b7463` is published to child `origin/main`; this parent change points `webui` at that reachable commit, and the local Viewer bundle matches the backend static artifact. |

R33–R46 are the latest local review fixes: standalone export/import now round-trips with local Scope reconstruction; multi-project and derived Observation queries use authoritative Scope ownership; OpenClaw resumes retain the prior identity; the legacy `manual-memories` selector includes both its literal project Scope and `manual_default`; structured extraction excludes standalone manual memories by default; concurrent standalone imports serialize around duplicate checks; native Observation queries hydrate the `memory_scope_type` field; Viewer deletion tests exercise and assert the current query-based DELETE endpoint; all V20 rehearsal modes refuse existing or unverifiable databases and only recreate a database created by the current run; the proxy E2E invokes the actual wrapper CLI; MCP `get_observations` filters by authoritative Scope ownership; and native Enter/Space tests no longer depend on a headless-renderer animation-frame wait that can hang after menu dismissal. The `includeStandaloneManual` opt-in remains explicit and defaults to false.

## Confirmed Findings

### R1 — V20 is not executable by the application migration runner

- **Evidence**: `backend/src/main/resources/db/migration/V20__conversation_memory_scope_expand.sql` ends with the psql client command `\\echo`. Flyway executes SQL through JDBC, not the psql client, so the migration can fail after `COMMIT` or be reported inconsistently even though the rehearsal script succeeds.
- **Impact**: The script used as migration evidence is not the same execution path used at application startup; deployment can fail or leave Flyway state inconsistent.
- **Required fix**: Remove psql-only syntax. Make the rehearsal assert the Flyway/JDBC path separately from its raw-psql fixture path.

### R2 — Durable SessionEnd summary jobs are implemented but not used

- **Evidence**: `SummaryJobService` owns the durable `mem_pending_messages` state machine, but `IngestionController` still calls `SummaryGenerationService.completeSessionAsync`. The latter uses the in-process `summarizingSessions` set and does not create or advance a durable job.
- **Impact**: A restart can lose an in-flight summary and deletion protection; repeated SessionEnd delivery can regenerate work. The implementation does not provide the persistence guarantees claimed by the refactor.
- **Required fix**: Route SessionEnd through one durable job orchestration path. Preserve the one-shot migration rule; this is a runtime wiring correction, not a migration rollback design.

### R3 — Prompt listing still joins sessions by the obsolete bare external ID

- **Evidence**: `UserPromptRepository.findAllPaged` joins `SessionEntity` on `s.contentSessionId = p.contentSessionId` and applies platform filtering after that join.
- **Failure scenario**: two conversations `(claude, same-id)` and `(codex, same-id)` exist; a prompt belonging to one is joined to both parent rows, producing duplicate or incorrectly attributed prompt results.
- **Required fix**: make prompt listing use `conversation_id` as the relationship key and resolve an optional external identity once at the controller/service boundary.

### R4 — Session-start prior-message exclusion compares a bare external ID

- **Evidence**: `ContextService.getPriorSessionMessages` skips a candidate when `session.contentSessionId` equals the current external ID.
- **Failure scenario**: a Claude and Codex conversation share an external ID; starting the Codex conversation suppresses the Claude conversation's prior message, or vice versa.
- **Required fix**: pass the resolved internal conversation UUID and compare `session.id`; keep project-path selection semantics unchanged.

### R5 — Manual sentinel SQL has a NULL three-valued-logic hole

- **Evidence**: the V20 manual-bucket guard uses `b.status <> 'completed'` and `b.project_path <> 'manual-memories'`. A NULL status or project path makes those predicates UNKNOWN. A row can therefore evade the guard and then fail to receive a standalone classification, or be re-attributed as a conversation row.
- **Failure scenario**: a `manual-memories` parent with blank content, manual-marked children, and `status = NULL` does not satisfy the SQL `OR` guard when all other shape checks pass; the later `kind = 'manual'` update also does not classify it, and the observation backfill can attach it to the parent conversation.
- **Required fix**: use NULL-safe `IS DISTINCT FROM` predicates and add a regression fixture/assertion for NULL status and project values. SQL classification must remain stricter than Java classification, never looser.

### R6 — Opaque external IDs still have an incomplete HTTP transport

- **Evidence**: external IDs are deliberately allowed to contain `/`, `%`, `?`, `#`, Unicode, and boundary spaces, but session deletion is exposed only as a path-variable route and the Viewer helper places the ID in a URL path.
- **Failure scenario**: a valid ID containing `/` is split into path segments; encoded delimiters may be rejected or decoded by a proxy/container before identity resolution; deletion can target no row or the wrong route.
- **Required fix**: provide and use a query-parameter/body deletion contract with standard URL encoding. Keep any safe-character path alias only as a compatibility route and never as the sole opaque-ID API.

### R7 — The checked-out WebUI SHA differs from the parent gitlink

- **Evidence**: parent `HEAD` records gitlink `8d0d2e71...`, while the local submodule checkout was `72e7804b...` and appeared as modified.
- **Impact**: a clean parent checkout would build a different Viewer from the one used during validation. This violates the backend/WebUI pairing invariant and makes the current static bundle provenance ambiguous.
- **Required fix**: select the explicitly paired WebUI commit, rebuild/stage the Viewer from that commit, update the parent gitlink, and leave both parent and submodule clean. Do not push the submodule's unrelated repository history.

### R8 — Queue recovery measures processing age from row creation

- **Evidence**: `PendingMessageRepository.countStale` and `updateStaleMessages` compare `createdAtEpoch`, although durable summary jobs record the worker claim time in `startedProcessingAtEpoch`.
- **Failure scenario**: a pending row created long ago is claimed by a healthy worker; the scheduled recovery task sees the old creation time while the LLM call is still running and resets the row to `pending`, allowing duplicate summary work.
- **Required fix**: use `startedProcessingAtEpoch` for stale detection, treat a missing claim timestamp as recoverable, and clear it when resetting a row to `pending`. Add a repository/state-machine regression assertion.

### R9 — Session-limited context groups by the bare external ID

- **Evidence**: both `findByTypeAndConceptsWithSessionLimit` queries group and filter on `content_session_id`; the platform-aware variant only filters platform on the child rows.
- **Failure scenario**: `(claude, shared-id)` and `(codex, shared-id)` are counted as one recent session in the unfiltered query, so one platform can consume the other platform's session slot and return the wrong result set. Standalone rows can also consume a session slot despite having no Conversation.
- **Required fix**: group and filter by `conversation_id`, exclude `NULL` conversation IDs from the session window, and retain the explicit platform predicate where requested.

### R10 — `MemoryScopeKey` treats a digest as identity

- **Evidence**: `equals`/`hashCode` compare `scopeType + sha256`, even though the service and schema document the digest as an index candidate that must be followed by an exact key comparison.
- **Failure scenario**: two distinct project paths supplied through a colliding test digester compare equal in Java and can be collapsed by an in-memory collection before the database collision guard runs.
- **Required fix**: compare `scopeType + scopeKey`; keep the digest only for lookup/indexing and add a forced-collision equality regression test.

### R11 — The Viewer manual sentinel still admits Conversation rows and misses `manual_default`

- **Evidence**: `contentSessionId=manual-memories` reaches the observation query without forcing `standaloneKind=manual`; the project-only scope predicate cannot select manual memories saved without a project.
- **Failure scenario**: a real Conversation whose external ID is `manual-memories` is shown with manual rows, while no-project manual memories are absent from the same legacy Viewer filter.
- **Required fix**: force the sentinel to the manual standalone branch and make that branch include the fixed `manual_default` scope (while preserving explicit project-scoped manual rows). Add source-level and API regression coverage.

### R12 — Viewer source and deployed bundle disagree about standalone rows

- **Evidence**: `webui/src/ui/viewer/utils/sessions.ts` currently derives a `SessionRef` from any row with `content_session_id`/`session_id`, while standalone observations carry the compatibility ID but no Conversation.
- **Failure scenario**: deleting or filtering a manual/extraction/DLQ row is interpreted as a Conversation operation for the synthetic ID, potentially removing unrelated feed rows from the UI or attributing the item to a catalog session.
- **Required fix**: return `null` for rows with `standalone_kind`, cover it in Viewer tests, commit the source change to the paired WebUI remote, and rebuild the parent static artifact from that reachable commit.

### R13 — Summary result and job pointer are not atomically persisted

- **Evidence**: `persistSummaryResult` is called internally from `SummaryGenerationService`, so its `@Transactional` annotation is bypassed; the Summary repository save and the job-service pointer update can therefore commit in separate transactions.
- **Failure scenario**: a process failure after the Summary commit but before `result_summary_id` commits leaves a job with no durable result pointer; recovery can call the LLM again and create a duplicate or fail to finalize the existing Summary.
- **Required fix**: introduce an explicit short transaction boundary around Summary insert/upsert plus `markResultPersisted`; keep the LLM call outside that boundary and retain the unique `(conversation_id, summary_job_key)` guard.

### R14 — Replayed terminal SessionEnd reopens a completed Conversation

- **Evidence**: `enqueueSessionSummaryJob` sets a parent to `summarizing` whenever its status is not `completed`, even when the existing canonical job is already `processed`, `skipped`, or terminal `failed`.
- **Failure scenario**: a replayed SessionEnd after a terminal job changes the parent status back to `summarizing`, creating a misleading lifecycle state and potentially changing deletion behavior without new work.
- **Required fix**: terminal job replay must remain idempotent; only a new or in-flight job may set the parent to `summarizing`, while terminal jobs leave it completed.

### R15 — SessionEnd can summarize before admitted Observation work drains

- **Evidence**: `handleToolUse` delegates enqueueing to `AgentService.processToolUseAsync`, so the endpoint may return before an Observation pending row exists. `handleSessionEnd` can then close the Conversation and publish a summary event; `processSummaryJob` snapshots current Observations without checking outstanding `message_type='observation'` jobs. Neither Observation admission nor final Observation persistence uses the Conversation parent lock/status protocol required by the plan.
- **Failure scenario**: a PostToolUse request is accepted and its async worker is delayed; SessionEnd commits and the summary worker sees no observations, marks the job `skipped`, then the delayed worker inserts an Observation into the completed Conversation. The final result is an un-summarized Observation and a terminal summary job that will never retry. If a pending row already exists, the summary may instead snapshot a partial set and miss the Observation produced later.
- **Required fix**: make Observation queue admission synchronous and durable under the Conversation UUID row lock, reject new admissions after SessionEnd closes the Conversation, let only pre-admitted jobs finish, and defer summary snapshot/finalization until all admitted Observation work is terminal. Ensure completion wakes the durable summary job promptly; scheduled recovery remains the crash fallback. Add race/state tests proving both orderings.

### R16 — Observation LLM response bodies are written to debug logs

- **Evidence**: `AgentService.callLlmAndSaveObservation` logged the full parsed response, and `LlmService.chatCompletionStructured` logged a raw response preview plus the exception message. These values can contain extracted user conversation content and facts; debug logging does not make them less sensitive, and deployments commonly retain/debug-forward logs.
- **Impact**: enabling debug logs duplicates private memory content into a less protected retention path, contrary to the plan's rule that prompts, observations, and summary snapshots never enter logs.
- **Required fix**: log only non-content metadata such as token count, response length, and exception type; exception messages and causes may contain provider response fragments and must not be logged on these paths.

### R17 — Several Conversation-owned child mutations bypass lifecycle serialization

- **Evidence**: several Viewer and import mutations were initially direct child-row writes. The review pass also found `SummaryGenerationService.triggerQualityScoringAndRefinement` writing scored Observation entities directly, and `MemoryRefineService` performing asynchronous updates/deletes after its LLM call without a parent lock.
- **Failure scenario**: a child deletion, import, quality update, or refinement races SessionEnd snapshot construction or whole-Conversation deletion; a stale refinement can overwrite an intervening edit or delete a freshly updated row.
- **Required fix**: route session-owned Viewer/import/refinement mutations through the UUID parent lock, re-read rows after acquiring it, and reject/defer ordinary writes while the parent is `summarizing` or has in-flight summary work. Summary-derived quality metadata may be persisted under the parent lock after the immutable snapshot is fixed, but must not alter snapshot content. Keep LLM calls outside transactions; use short, conditional write transactions and lock multi-Conversation imports in deterministic UUID order.

### R18 — Import writes do not satisfy the V20 child identity contract

- **Evidence**: `ImportService.importObservation` creates an Observation without `memory_scope_id`; bulk Observation import omits `conversation_id`, `platform_source`, and `memory_scope_id`; bulk Prompt import omits `conversation_id` and `platform_source`. V20 makes Observation `memory_scope_id` non-null and both Conversation-owned Observation/Prompt rows require their internal conversation relation. The bulk duplicate sets also use Conversation UUID keys for existing rows but external session ID keys for incoming rows, so existing duplicates never match.
- **Failure scenario**: a valid single Observation import reaches the database with NULL `memory_scope_id` and fails; a valid bulk Observation or Prompt reaches the database without its owning Conversation UUID and fails its schema constraint/FK. Replaying a previously imported record in the bulk path is counted as new because the key domains differ.
- **Required fix**: resolve each external ref to its local Conversation, lock all affected UUID parents in deterministic order, assign the Conversation projection and exact project Scope to Observation rows, assign Conversation identity to Prompt rows, and use one UUID-based duplicate key consistently. Add single/bulk tests including same external ID on two platforms.

### R19 — Refinement still treats the bare external ID as a grouping identity

- **Evidence**: `MemoryRefineService.refineObservations` groups candidates by `ObservationEntity.contentSessionId`; the new model explicitly allows the same external ID on different platforms. The current candidate repository queries exclude `standalone_kind IS NOT NULL`, so standalone rows are not part of this particular reachable path.
- **Failure scenario**: Claude and Codex Conversations sharing `content_session_id = "shared"` are grouped together; the merge can persist one platform's content into the other platform's primary Observation and delete rows from both Conversations.
- **Required fix**: group Conversation-owned candidates by `conversation_id`; never merge rows across distinct Conversations. Serialize child mutations with the parent lifecycle lock and re-read candidate rows after acquiring it.

### R20 — Conversation creation retries inside an aborted PostgreSQL transaction

- **Evidence**: `SessionManagementService.getOrCreate` catches a unique-key `DataIntegrityViolationException` from `saveAndFlush` and then queries for the winner in the same `@Transactional` method. PostgreSQL marks the current transaction failed after a constraint error; subsequent SQL on that transaction is rejected until rollback. `createImported` also has no same-identity race recovery.
- **Failure scenario**: two first-writers concurrently create the same `(platform_source, content_session_id)`; the loser catches the unique violation, but its winner lookup runs on the failed transaction and cannot return the winning UUID, so a valid concurrent SessionStart/observation may fail.
- **Required fix**: make identity creation an atomic insert-on-conflict operation that returns/loads the canonical local UUID without relying on SQL after a constraint exception in the same transaction. Cover concurrent same-ref creation against real PostgreSQL, including import creation.

### R21 — External identity lookup does not state the database comparison contract

- **Evidence**: V20 creates the composite identity index with `COLLATE "C"`, and `ExternalSessionRef` promises exact, case-sensitive opaque IDs. `SessionRepository.findByPlatformSourceAndContentSessionId` uses JPQL equality without an explicit collation.
- **Risk**: on a PostgreSQL database using a nondeterministic default collation, lookup equality may not mean byte-exact equality even though uniqueness is defined under `C`; the same API value could resolve differently from the database identity index.
- **Required fix**: make core `(platform_source, content_session_id)` lookups use explicit `C` collation matching V20's unique index. Include a real-PostgreSQL check with distinct case/Unicode spellings where the test database supports a nondeterministic collation.

### R22 — Summary-triggered refinement is published before the summary job becomes terminal

- **Evidence**: `SummaryGenerationService` publishes the refinement event from `triggerQualityScoringAndRefinement` before calling `SummaryJobService.finalizeResult`. Refinement now takes the Conversation lock and deliberately rejects writes while the summary job is in flight.
- **Failure scenario**: the async refinement listener starts before `finalizeResult` commits, sees `result_persisted`/`processing`, and refuses every candidate mutation; the one-shot refinement event is then lost until a later unrelated scheduled pass.
- **Required fix**: separate quality metadata persistence from refinement-event publication; finalize the durable summary job first, then publish the refinement wake-up after the terminal transition commits. Add an ordering test.

### R23 — Generic stale recovery can reset an actively running summary job

- **Evidence**: `StaleMessageRecoveryTask` calls `PendingMessageRepository.updateStaleMessages` for every row with `status='processing'`; the query does not restrict `message_type`. The durable summary processor independently implements a stale threshold and parent-locked claim, but this generic bulk update bypasses both.
- **Failure scenario**: a summary LLM call runs longer than the generic 10-minute threshold; the scheduled recovery changes its `summarize` row to `pending` while the original worker is still active. A second event claims the pending job and invokes the LLM again, racing result persistence/failure transitions.
- **Required fix**: generic stale recovery must be limited to `message_type='observation'`; summary rows are reclaimed only through `SummaryJobService.claimForProcessing` under the Conversation lock and its summary-specific stale threshold. Add repository query coverage proving summarize rows remain processing.

### R24 — Sensitive search and provider failure details cross logging and response boundaries

- **Evidence**: MCP search and timeline logged raw query text; Viewer search logged a query prefix when embedding failed. MCP `save_memory`, timeline, and extraction handlers logged or returned raw exception messages. Extraction failures passed `Exception.getMessage()` into DLQ `extracted_data`. Context generation, semantic search, and search/timeline fallback paths also logged provider or database exception messages.
- **Failure scenario**: a user searches for a secret or private fact, or an embedding/provider exception contains request or response fragments; the value is copied to retained application logs, an MCP/HTTP caller response, or a searchable DLQ record.
- **Required fix**: keep query values and exception messages out of logs and external error responses; persist only a bounded error type/code in DLQ metadata. Preserve operational diagnostics through non-content metadata such as exception type and result counts. Add canary-string tests for MCP errors and DLQ persistence, and audit the touched context/search/timeline/extraction paths.

### R25 — The shipped Viewer bundle does not contain the opaque-safe delete fix

- **Evidence**: at review start, the parent gitlink targeted unavailable commit `8d0d2e71...`. The remediation uses query-based opaque deletion and excludes `standalone_kind` rows from Conversation attribution. Child commit `2a3b7463` is now published to WebUI `origin/main`; the parent pointer in this change targets it. The child bundle and Spring Boot static bundle have identical SHA-256.
- **Failure scenario**: a fresh parent checkout cannot resolve its recorded WebUI gitlink, and the current local WebUI source change is not a reachable published commit. Local runtime verification therefore does not prove a clean clone can reproduce the paired Viewer.
- **Disposition**: Resolved. WebUI commit `2a3b7463` is published, the parent gitlink now targets it, and the paired source/static bundle passed Viewer tests and TypeScript checking.

### R26 — Java and Go SDK wire tests do not pin the new platform identity field

- **Evidence**: the Java and Go SDK request DTOs support `platform_source`, but their complete-body wire tests exercise only the legacy field sets. The tests can pass while a future refactor drops the platform identity from SessionStart, Observation, UserPrompt, or SessionEnd requests.
- **Failure scenario**: a Codex/OpenClaw client sends an otherwise valid request after a DTO regression omits `platform_source`; the backend defaults it to Claude and stores the event under the wrong Conversation identity.
- **Required fix**: include `platform_source` in fully populated Java and Go wire fixtures and expected key sets, while proving minimal requests still omit optional fields. Add explicit response parsing coverage for the identity fields in JS and Java.

### R27 — SDK documentation still advertises the path-based session-user route

- **Evidence**: the Java, JS, and Go SDK documentation/comments still name `PATCH /api/session/{sessionId}/user`, although the clients now use `/api/session/user` with `contentSessionId` and `platformSource` query parameters.
- **Failure scenario**: a caller copies the published route or assumes the session ID is path-safe; IDs containing `/`, `?`, `#`, or `%` are routed incorrectly or cannot be updated.
- **Required fix**: update the affected SDK README and DTO/API comments to document the query identity route and optional Claude default; retain old route only where the backend explicitly supports it as a compatibility alias.

### R28 — Codex's documented terminal event never reaches SessionEnd

- **Evidence**: both Codex integration guides map a root-level `turn_aborted` event to `session_end`, but `parseCodexEvent` does not map that type. The watcher therefore drops it before `ApiClient.processEvent`, so the SessionEnd request and durable summary job are never created for that lifecycle event.
- **Failure scenario**: Codex appends `{"type":"turn_aborted",...}` to a watched transcript; parsing returns `null`, leaving the Conversation active and unsummarized despite the documented mapping.
- **Required fix**: map `turn_aborted` to `session_end`, keep exactly one SessionEnd emission path, and add a parser-to-API regression test that asserts the outgoing request carries `platform_source: "codex"`.

### R29 — Backend endpoint-surface sentinels still assert the old operation and path counts

- **Evidence**: `BackendEndpointSurfaceTest` has the correct explicit 74-operation expected set across 68 distinct paths, and its discovered-surface equality passes, but the non-vacuous assertions still expect 72 operations and 66 paths.
- **Impact**: the backend test suite fails despite the endpoint set matching the actual controller mappings, obscuring substantive regressions behind a stale guard.
- **Required fix**: update the count guards to 74 operations and 68 paths while retaining exact operation-set comparison.

### R30 — Real-database Scope test cleanup deletes the migration-owned singleton

- **Evidence**: `MemoryScopeServiceDatabaseTest` tracks `resolveManualDefault()` results alongside test-created project scopes, then deletes every tracked ID in `@AfterEach`. The fixed `manual_default/manual-default-v1` row is shared schema state, not disposable test data.
- **Failure scenario**: the integration test passes against a migrated database, then removes its singleton. A later Flyway assertion or Viewer request sees no manual-default scope; reusing the test database can also make results order-dependent.
- **Required fix**: limit cleanup to `project` scope rows or use a database dedicated to one test method; the shared manual-default row must survive all test cleanup.

### R31 — JS example health-route test depends on a live backend

- **Evidence**: `examples/http-server/error-contract.test.ts` calls the demo `/health` route, which calls the real SDK against the default `127.0.0.1:37777` backend.
- **Failure scenario**: running the SDK suite without a healthy local worker returns the legitimate `503 unhealthy` response, so the route-ordering test fails despite the terminal 404/405 middleware being correct.
- **Required fix**: isolate the SDK health dependency in this test so it verifies the matched route without contacting a local service.

### R32 — Session lifecycle errors still expose exception messages

- **Evidence**: `SessionController.startSession` and `getSession` log `Exception.getMessage()` and return it in the public `error` field. Provider, database, and filesystem exceptions can contain sensitive request or storage details.
- **Failure scenario**: a SessionStart failure includes a provider response fragment or SQL detail; the raw value is retained in logs and returned to the client even though the error response contract only needs a stable failure message.
- **Required fix**: log only non-content metadata such as exception type and return a generic stable message; preserve the existing HTTP status and JSON field shape.

### R33 — Exported observations cannot be imported as standalone records

- **Evidence**: `scripts/export-memories.sh` preserves standalone Observation rows in its `observations` array, but `ImportService.ObservationImportData` has no `standalone_kind` or Scope-kind field. Both single and bulk import resolve every observation through a real `(platform_source, content_session_id)` Conversation; manual, extraction, and DLQ correlation keys intentionally have no such parent after V20. The export entity's wire names (`content_session_id`, `project`, `narrative`, `facts`, `files_read`, and `files_modified`) also do not all match the import record's default Jackson names.
- **Failure scenario**: export a manual Observation with `conversation_id:null`; post the export's observations to `/api/import`; the record cannot bind its identity/content fields consistently and, even if adapted to the current DTO, parent resolution rejects its legacy correlation key. A round-trip therefore loses or errors on standalone memory.
- **Required fix**: accept the existing exported wire aliases and explicit standalone kind, route standalone rows without resolving/creating a Conversation, and rebuild their target Scope locally. Preserve the distinction between `manual_default` and a project literally named `manual-memories` without exporting a Scope UUID. Add a fixture that serializes the exported wire shape, imports all three standalone kinds, and verifies their local Scope/Conversation relations.
- **Disposition**: Implemented. `ImportServiceIdentityTest` deserializes exported wire rows and verifies manual, extraction, and DLQ imports rebuild local Scope ownership without creating Conversation rows.

### R34 — Two multi-project Observation repository queries bypass Memory Scope

- **Evidence**: `ObservationRepository.findByProjectsLimited` and `findByProjectsTypesAndConcepts` still filter `mem_observations.project_path IN (:projects)`, while the plan's §4.3 and §10.1 require every project-scoped Observation predicate to resolve through `memory_scope_id`. These repository methods are currently not called by production code, so this is an incomplete query-surface migration and latent regression path, not evidence that the active `ContextService.generateContextMultiProject` path currently leaks or omits data.
- **Failure scenario**: a caller uses either multi-project query for a row whose authoritative Scope is one of the requested exact paths but whose retained/display `project_path` differs; the row is omitted despite belonging to the requested Scope. Conversely, a stale/mismatched path can include a row whose Scope is outside the requested set.
- **Required fix**: resolve both native queries by exact `project` Scope keys using `COLLATE "C"`; retain input path set, filtering, sort, and limit semantics. Add a real database regression test demonstrating that Scope ownership, not the display path, controls inclusion, and update the residual-site inventory.
- **Disposition**: Implemented. Both multi-project queries use exact Scope membership, and the PostgreSQL regression test proves display-path mismatches do not change ownership results.

### R35 — OpenClaw resume metadata is ignored when selecting Conversation identity

- **Evidence**: the plugin's `session_start` event type includes optional `resumedFrom`, but the handler keys identity only from `event.sessionId`; `gateway_start` also clears the only session-ID map. The implementation and tests therefore do not cover the plan's explicit restart/resume identity requirement.
- **Failure scenario**: OpenClaw resumes a Conversation under a new `sessionId` and supplies the previous durable ID in `resumedFrom`; the plugin starts/continues under `openclaw-<new id>` rather than the prior Conversation, splitting the Conversation and its observations/summary across IDs. This depends on the host's resume event semantics and should be verified against its documented event contract.
- **Required fix**: when `resumedFrom` is present, preserve that predecessor identity for the resumed Conversation; otherwise use the current host session ID. Add lifecycle tests covering resume, gateway restart, compaction, and terminal SessionEnd, while ensuring a genuinely new session with no `resumedFrom` gets a distinct ID.
- **Disposition**: Implemented for the host-provided `resumedFrom` identity and gateway restart. OpenClaw build/tests pass. Per user direction, verification against the actual OpenClaw host runtime is intentionally out of scope and is not a release blocker.

### R36 — Derived Observation queries still treat display paths as Scope ownership

- **Evidence**: `ObservationRepository.countByProjectPath`, both `findByProjectPathOrderByCreatedAtEpochDesc` overloads, and `findDistinctProjects` still operate on `mem_observations.project_path`. Their callers include Viewer project statistics, CLAUDE.md/project memory summaries, context preview, Timeline windows, and scheduled refinement. The plan requires Observation ownership and project enumeration to follow `memory_scope_id`/`mem_memory_scopes`; only Summary/Session metadata paths intentionally remain on `project_path`.
- **Failure scenario**: an Observation's retained/display `project_path` differs from its authoritative Scope key. The project that owns the Scope omits the row from previews/timelines and undercounts it, while the stale display path can include it in another project's results. Scheduled refinement enumerates the display path instead of the owning Scope key.
- **Required fix**: replace these derived predicates with exact `project` Scope predicates using `COLLATE "C"`; preserve the legacy `manual-memories` selector by including both the exact same-named project Scope and `manual_default` Scope. Enumerate projects from Scopes with observations. Add a real PostgreSQL regression test with deliberately mismatched display paths and assert list, page, count, and enumeration behavior.
- **Disposition**: Implemented. PostgreSQL coverage verifies authoritative Scope ownership for list, page, count, and project enumeration with deliberately mismatched display paths.

### R37 — The legacy `manual-memories` selector omits `manual_default` in most Scope queries

- **Evidence**: most `ObservationRepository` project predicates select only `scope_type='project'`; only Viewer `findAllPaged` has an explicit `manual_default` branch. V20 stores no-project manual memories in `manual_default` while retaining the wire/display path `manual-memories`. Plan §4.3.5 requires that exact compatibility selector to include both the same-named project Scope and the fixed manual-default Scope.
- **Failure scenario**: a client calls a project-filtered search/context/timeline/refinement-adjacent query with `project=manual-memories`; it sees only records in a literal project Scope and silently omits old no-project manual memories that the pre-refactor `project_path` predicate returned.
- **Required fix**: update project-filtered Observation repository queries to resolve the exact project Scope plus `manual_default` only when the requested path is exactly `manual-memories`; preserve project-only behavior for every other path. Keep conversation-only queries restricted to conversation-owned rows. Add real-database coverage for representative search/context/list/count queries and the multi-project sentinel form.
- **Disposition**: Implemented. Single- and multi-project predicates include `manual_default` only for the exact legacy sentinel; PostgreSQL coverage checks the sentinel alongside a literal project Scope.

### R38 — Backfilled manual memories enter structured extraction by default

- **Evidence**: `StructuredExtractionService.runTemplateExtraction` defaults `sourceFilter` to `user_statement` and `manual`, then calls `findBySourceIn` without filtering `standalone_kind`. V20 repairs qualifying legacy manual rows to `source='manual'`; therefore rows that were not extraction candidates before migration become eligible by default. The frozen plan §1 and §4.3.8 explicitly require manual standalone rows to remain excluded unless a template opts in with `include-standalone-manual: true`.
- **Failure scenario**: after V20, scheduled extraction runs with the default template configuration; a user's `save_memory` content has `standalone_kind='manual'` and `source='manual'`, so it is sent to the LLM, potentially changing extracted state and incurring cost without user opt-in.
- **Required fix**: add a default-false template property `includeStandaloneManual`; make the repository candidate query exclude `standalone_kind='manual'` unless that flag is true, without changing source/order/limit semantics; cover default exclusion and explicit opt-in on the V20 database.
- **Disposition**: Implemented. The candidate SQL excludes manual standalone rows before applying the limit unless the default-false template option opts in; tests cover both modes against V20.

### R39 — Concurrent standalone imports can create duplicate observations

- **Evidence**: conversation-owned imports serialize on the parent Session row, but standalone rows have no parent row to lock. Their duplicate check and insert were separate operations, and V20 intentionally has no unique constraint for this logical import key.
- **Failure scenario**: two instances import the same standalone row at once; both see no existing row and both insert it. The duplicate key is `(memory_scope_id, standalone_kind, platform_source, content_session_id, title, created_at_epoch)`.
- **Required fix**: serialize single and bulk standalone imports on a transaction-scoped lock derived from that full logical key. Acquire multiple batch locks in stable order; keep the existing duplicate semantics and schema unchanged.
- **Disposition**: Implemented with PostgreSQL transaction-level advisory locks. Lock acquisition requires an existing transaction and is held through duplicate lookup and insert. Eight concurrent imports against V20 produce one row, one `imported`, seven `duplicates`, and zero errors. Additional real-database tests cover cross-platform same-ID conversations and standalone `manual_default` re-import through single and bulk APIs.

### R40 — Native Observation queries fail when the Scope formula is hydrated

- **Evidence**: `ObservationEntity.memoryScopeType` is a Hibernate `@Formula`, but multiple native repository queries return `SELECT * FROM mem_observations`. PostgreSQL has no physical `memoryScopeType` column, and those result sets omit the formula alias Hibernate requires.
- **Failure scenario**: `SessionStart` requests context for a project with existing observations; `ObservationRepository.findByTypeAndConcepts` returns a native entity query and Hibernate fails with SQLState `42703` / missing result column `memoryScopeType`, producing HTTP 500. The same mapping risk exists in the other native queries returning `ObservationEntity`.
- **Required fix**: preserve the `memory_scope_type` wire field and include the formula alias in every native query that hydrates `ObservationEntity`; leave scalar, aggregate, and count queries unchanged. Add V20-backed tests exercising populated native results and the SessionStart path.
- **Disposition**: Fixed. Every native query returning `ObservationEntity` now selects the formula alias; aggregate/count queries remain unchanged. A real V20 repository test returns populated context rows with `memory_scope_type=project`, and packaged SessionStart succeeds against a database containing existing observations.

### R41 — Viewer deletion lifecycle tests mock a retired endpoint

- **Evidence**: the browser fixture matched `DELETE /api/sessions/claude/owned-session`, but `deleteSession` sends `DELETE /api/sessions?platformSource=claude&contentSessionId=owned-session`. The fixture therefore returned its generic success response without blocking or observing the real request in the `late-delete-response` and `live-before-delete` scenarios.
- **Failure scenario**: a regression changes the Viewer DELETE URL or its pending-request behavior; the fixture does not intercept it, so those lifecycle tests no longer exercise the API race they claim to cover and can report misleading results.
- **Required fix**: intercept the actual query-based URL and assert that the two API-driven scenarios reached that handler.
- **Disposition**: Fixed in the child test fixture. All six deletion lifecycle browser scenarios pass, including an explicit assertion that the asynchronous-delete cases reached the DELETE handler.

### R42 — Scale rehearsal could proceed after its database safety probe failed

- **Evidence**: the new scale-mode guard used a psql variable inside `psql -c`; psql does not interpolate variables in `-c`. The query returned a syntax error, and the script did not treat the failed existence check as fatal before calling `recreate`, which drops the named database.
- **Failure scenario**: a scale run targets an existing `_rehearsal` database; if the existence probe errors, the script can mistake the empty result for “not present” and drop the target before recreating it.
- **Required fix**: restrict scale database identifiers, use a safely constructed existence query, and abort if the probe itself fails or finds an existing database.
- **Disposition**: Fixed. Re-running against the populated scale database exits before any drop; post-check confirmed its 100,013 observations and database size were unchanged.

### R43 — Proxy full-flow test bypasses the proxy wrapper

- **Evidence**: `proxy/java/proxy/test-full-flow.mjs` previously sent SessionStart directly with Axios and manually applied the returned `updateFiles`; it never launched `proxy/wrapper.js`. The script therefore verified backend behavior but could not detect a broken wrapper command, hook-event mapping, request body, or response handling.
- **Failure scenario**: `wrapper.js` sends SessionStart to the wrong endpoint or drops required event fields while the direct Axios test continues to pass, leaving the Claude Code client path unverified.
- **Required fix**: run the actual wrapper CLI against the version-verified backend, then read the resulting Conversation and submitted prompt back through the API. Keep the direct SessionStart check for its camelCase `updateFiles` compatibility contract, and keep all filesystem activity inside the generated temporary workspace.
- **Disposition**: Implemented. Targeted end-to-end verification is recorded below.

### R44 — MCP `get_observations` filters project by display path

- **Evidence**: `ClaudeMemMcpTools.getObservations` fetched rows by ID, then applied `project.equals(o.getProjectPath())` in Java. The refactor defines `memory_scope_id` as the authoritative project owner and explicitly permits a retained/display `project_path` to differ from the Scope key.
- **Failure scenario**: an Observation belongs to project Scope `/repo/current` but has display path `/repo/old`; `get_observations(ids, project=/repo/current)` incorrectly drops it. Conversely, a row with a stale display path matching the requested project can appear while its Scope belongs elsewhere.
- **Required fix**: resolve the exact project Scope without creating one and query the requested Observation IDs by `memory_scope_id`. Preserve the legacy `manual-memories` selector by including both its project Scope and `manual_default` Scope.
- **Disposition**: Implemented. MCP unit tests cover scope filtering, sentinel behavior, and an all-invalid ID list; a PostgreSQL V20 test changes the display path independently and verifies owner-scope inclusion and foreign-scope exclusion.

### R45 — Standard migration rehearsal can drop a pre-existing database

- **Evidence**: the standard (non-scale) path accepted names such as `v20` and immediately called `DROP DATABASE IF EXISTS $DATABASE`; only scale mode checked whether the database existed. The database name was also interpolated into SQL without identifier quoting.
- **Failure scenario**: an operator points `--container` at a PostgreSQL instance that already has a database named `v20` or `*_copy`; the rehearsal drops that database before replaying migrations. A specially formed allowed name could also alter the interpolated SQL.
- **Required fix**: validate database names in every mode, probe the server and refuse any existing target before DDL, create the database on the first cycle, and only drop/recreate that database after this process has created it. Quote database identifiers through psql variable substitution and use a unique default target name.
- **Disposition**: Implemented and verified. The script refuses the existing `v20` database before DDL, rejects a SQL-shaped name before connecting, and passes the complete 100,000-observation rehearsal on a newly created target (26/26).

### R46 — Native keyboard browser tests can hang on animation-frame settling

- **Evidence**: `session-keyboard-actions.browser.test.ts` awaited four `requestAnimationFrame` callbacks through `Runtime.evaluate` after each native key. On the local headless Chrome run, Enter and Space both dispatched successfully and opened the menu, but the animation-frame promise never completed after Escape dismissed it; the suite ended at its 30-second timeout without reaching the assertions.
- **Failure scenario**: a correct native keyboard interaction closes the menu, then the test waits forever for renderer animation frames. CI reports a timeout rather than verifying the Enter/Space behavior.
- **Required fix**: wait from the Bun test process for observable UI state instead of awaiting renderer frames, and bound DevTools HTTP/WebSocket/CDP waits so infrastructure stalls fail with a specific error.
- **Disposition**: Implemented. Both native key cases pass, and the complete Viewer suite passes 76/76.

## Verification Results

Verification run on 2026-10-23 against a dedicated PostgreSQL 16.12 review container; no user database was migrated.

- Backend: the final database-enabled `./mvnw test` passed 313 tests with 0 failures, 0 errors, and 0 skips, including the Flyway/JDBC smoke test. Both the PostgreSQL integration URL and Flyway smoke-test URL pointed at isolated V20 rehearsal databases.
- Post-R39 targeted verification: `ImportServiceIdentityTest` and `ImportServiceDatabaseTest` passed 7/7 against the isolated V20 rehearsal database; the database class asserts V20 and cleans its fixture rows/scopes/conversations.
- Post-R40 targeted verification: `ImportServiceDatabaseTest` and `ObservationRepositoryScopeQueryTest` passed 5/5; the populated native context query now hydrates `memory_scope_type` correctly.
- Post-R44 targeted verification: `ClaudeMemMcpToolsTest` and `ImportServiceDatabaseTest` passed 14/14 against V20, including Scope-vs-display-path behavior, the legacy sentinel, and the invalid-ID empty-list case.
- Migration: Flyway/JDBC replayed all 18 versioned migrations from an empty disposable database through V20. The standard psql rehearsal passed its 21 migration checks, including atomic rollback on conflicting fixtures. The updated scale mode passed all 26 checks with 100,000 synthetic observations, 10,000 sessions, 10,000 summaries, 10,000 prompts, and 10,000 pending rows in addition to the base fixture; 100,013 observations remained and all received Scopes. V20 took 9.731 seconds in this latest local run; database size was 98.2 MiB before and 198.6 MiB after migration. The safety checks refused the pre-existing `v20` target and a SQL-shaped database name without issuing DDL.
- Runtime API: the packaged service started on port 37819 against the isolated V20 database. Health returned 200; SessionStart returned the existing camelCase `updateFiles` field; the same opaque ID containing `/`, `?`, `#`, `%`, and Unicode created distinct Claude/Codex UUIDs; query identity lookup, UserPrompt, SessionEnd, catalog isolation, and query-based deletion worked. A delete while the summary job was pending correctly returned 409; retry after the job became terminal returned 204.
- Proxy E2E: `test-full-flow.mjs` verified `claude-mem-java` and exact version `0.1.0-beta` on port 37819, invoked the actual wrapper CLI for SessionStart and UserPromptSubmit, verified the Conversation and prompt through the API, checked the direct SessionStart `updateFiles` field, and applied any returned file update only inside an automatically removed temporary workspace.
- Identity race: eight concurrent HTTP SessionStart requests for the same `(platform_source, content_session_id)` all returned 200 and the same UUID.
- Clients: JS SDK passed 396 tests plus TypeScript lint; Python SDK passed 489 tests; Go SDK `go test ./...` passed; Java Spring integration `mvn -o test` passed. OpenClaw `npm test` passed 2/2 and Codex watcher `npm test` passed 1/1 in the final local pass.
- Viewer: `bun test tests/ui/viewer/deletion-lifecycle.browser.test.ts` passed 6/6 after the fixture correction. The focused native Enter/Space cases passed 2/2, and the complete `bun test tests/ui/viewer` run passed 76/76 with 208 assertions. Viewer TypeScript checking passed in the final local pass. Parent static bundle and local child bundle are byte-identical and contain both standalone-row exclusion and query-based opaque deletion.
- Additional checks: `node --check proxy/wrapper.js` and `git diff --check` passed.

## Remaining Limits

- Clean-clone Viewer reproducibility is restored by published WebUI commit `2a3b7463` and the parent gitlink update included with this review remediation.
- Bugbot was requested, but this runtime exposes no Bugbot subagent invocation tool. The review therefore used local source/diff inspection and tests; it does not claim a Bugbot result.
- The keyboard-test repair is currently local to the WebUI submodule worktree and is not committed or published yet. The deletion lifecycle fixture correction also remains a local submodule change.
- The 9.731-second V20 timing is one synthetic local run (an earlier equivalent run measured 15.226 seconds), not a production-database estimate; actual disk speed, WAL policy, concurrent workload, and data distribution can change lock duration.
- Actual OpenClaw host-runtime event semantics remain unverified by explicit user direction; plugin unit tests and build checks do not claim host-runtime coverage.

## Repair Disposition

- R1-R24 and R26-R29 are repaired in the local implementation and covered by the focused backend, database, client, plugin, or wire-contract checks above.
- R25 is fully repaired and published; the parent gitlink is updated to the reachable paired Viewer commit.
- R30 cleanup now deletes only project scopes, preserving the migration-owned manual-default singleton. R31 mocks the JS SDK health check so the example test does not call a live worker. R32 keeps SessionController failure responses and logs free of exception messages while preserving status codes and JSON `error` fields; dedicated canary tests cover both SessionStart and legacy session lookup failures.
- R33-R46 are repaired locally: export-shaped standalone imports rebuild local Scope links; all Observation project analytics and multi-project queries use Scope ownership; OpenClaw resume identity uses `resumedFrom`; the legacy manual selector includes both matching Scopes; extraction excludes standalone manual rows unless explicitly opted in; concurrent standalone imports serialize around duplicate checks; native Observation queries hydrate `memory_scope_type`; deletion lifecycle tests intercept the current API; all migration rehearsal modes fail closed on existing or unverifiable targets and only recreate their own database; the proxy E2E invokes the actual wrapper CLI; MCP project filters use Scope ownership; and the native keyboard browser test avoids renderer-dependent animation-frame settling.

## Non-Findings (Explicitly Accepted)

The one-shot V20 transaction, manual intervention after migration failure, lack of rollback/checkpoints, and the accepted downtime are user-frozen deployment decisions. They must be tested for atomic failure and diagnosability, but must not be “fixed” by reintroducing phased migration machinery.
