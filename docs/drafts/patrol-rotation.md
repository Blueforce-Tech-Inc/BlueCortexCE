# Patrol Rotation Tracker

> **Purpose**: 持久化代码审查方向的轮换状态。
> **Updated by**: 定时项目维护任务。
> **Update rule**: `patrol-state.json` 是机器可读的唯一状态源；每轮完成后同步更新本文件的当前位置和历史摘要。

## Rotation Order (6 directions)
1. Java SDK
2. Go SDK
3. Python SDK
4. JS/TS SDK
5. Demo
6. Backend

## Current Position
**Last completed**: Python SDK (2026-10-01 03:31, eleventh cycle)
**Next up**: JS/TS SDK

## History
| DateTime | Direction | Findings |
|----------|-----------|----------|
| 2026-04-26 09:52 | Java SDK | ✅ No issues — clean compilation, 120 tests pass, DTO design solid, retry logic correct, error handling robust |
| 2026-09-30 01:26 | Python SDK | ✅ No new issues — implementation and 374-test SDK suite reviewed; current code fingerprint recorded for full acceptance |
| 2026-09-30 03:45 | JS/TS SDK | ✅ README and HTTP Demo contract reviewed; strict E2E checks pass; next direction is Demo |
| 2026-09-30 04:23 | Demo | ✅ Fixed missing Go wrapper-module requirements in eino/genkit/langchaingo examples; Java/Go/Python/JS checks pass; Go HTTP E2E 39/39 |
| 2026-09-30 04:32 | Backend | ✅ ViewerController and StructuredExtractionService spot-check found no code issues; Backend findings remain P0/P1/P2 = 0; next direction is Java SDK |
| 2026-09-30 05:03 | Java SDK | ✅ All 3 modules pass (client 121 + spring-ai 46 + starter 7); wire format spot-checks match backend `@JsonProperty` definitions; baseline doc corrected 120→121 tests; next direction is Go SDK |
| 2026-09-30 05:27 | Go SDK | ✅ Fixed gofmt alignment in 4 files (dto×3 + genkit retriever); 288 tests pass across 5 modules; Go demo E2E 39/39; full acceptance passed and new baseline set (b04ccf8); next direction is Python SDK |
| 2026-09-30 05:32 | Python SDK | ✅ 374/374 tests pass; 25 API methods and dual-mode ObservationUpdate verified; no issues, no changes; next direction is JS/TS SDK |
| 2026-09-30 05:37 | JS/TS SDK | ✅ 212/212 tests, lint clean, CJS+ESM+DTS build complete; no issues, no changes; next direction is Demo |
| 2026-09-30 09:26 | Demo | ✅ Java Demo verified (no unit tests by design, 12 @RestControllers counted, baseline corrected 10→12); Go 5 examples vet OK; no code changes; loop paused by user after this round; next direction is Backend |
| 2026-09-30 23:10 | Backend | ✅ Fixed SSEBroadcaster.broadcast() to catch IllegalStateException from completed emitters (was aborting the broadcast loop); TimelineService/StreamController clean; full acceptance passed, new baseline 24faf55; second rotation cycle begins, next direction is Java SDK |
| 2026-09-30 23:18 | Java SDK | ✅ Second cycle: spring-ai/starter modules reviewed in depth (advisor fail-open, aspect truncation matches backend constant, autoconfiguration conditional beans correct); no issues, no changes; next direction is Go SDK |
| 2026-09-30 23:22 | Go SDK | ✅ Second cycle: client_impl internals reviewed (context fast-fail, 10MB response cap, generic error mapping); no issues, no changes; next direction is Python SDK |
| 2026-09-30 23:27 | Python SDK | ✅ Second cycle: error.py exception hierarchy and retry semantics verified (cross-SDK parity), Flask demo structure clean; 26.md DoD note added pointing to the script as the acceptance definition; next direction is JS/TS SDK |
| 2026-09-30 23:30 | JS/TS SDK | ✅ Second cycle: client.ts private HTTP layer reviewed (closed-guard, AbortController timeout, 10MB limit); no issues, no changes; next direction is Demo |
| 2026-09-30 23:32 | Demo | ✅ Second cycle: 3 named Java Demo controllers reviewed (validation aligned with SDK limits); no issues, no changes; next direction is Backend (third cycle) |
| 2026-09-30 23:34 | Backend | ✅ Third cycle: WorktreeDetector (edge cases documented) and QualityScorer (clamped scoring, LLM fallback) clean; API-doc session-start examples verified snake_case in EN/ZH; no changes; next direction is Go SDK |
| 2026-09-30 23:39 | Go SDK | ✅ Third cycle: StringList dual-format decoding and error.go parity verified; JS README session.response comment corrected (EN/ZH); no code changes; next direction is Python SDK |
| 2026-09-30 23:42 | Python SDK | ✅ Third cycle: client.py request layer reviewed (jittered backoff, graceful degradation, retryable-only); design docs: index size table accurate within rounding, 8.md 10/10 claim verified; no changes; next direction is JS/TS SDK |
| 2026-09-30 23:48 | JS/TS SDK | ✅ Third cycle: errors.ts retry semantics match Python/Go/Java (429/502/503/504 + network, 500 excluded); architecture docs: 16 migration files verified (V9/V10 absent, max V18), 13 controllers match; no changes; next direction is Demo |
| 2026-09-30 23:53 | Demo | ✅ Third cycle: SessionLifecycle/Ingest controllers validated; user guide: DOCKER_README env-var defaults (6 rows) match docker-compose.yml exactly; no changes; next direction is Backend (fourth cycle) |
| 2026-10-01 00:00 | Backend | ✅ Fourth cycle: ModeService (cache/inheritance merge) and SettingsService (dual-prefix WebUI keys) clean; API-doc context/generate example verified against DTO `@JsonProperty`; no changes; next direction is Go SDK |
| 2026-10-01 00:02 | Go SDK | ✅ Fourth cycle: NewClient config resolution hardened (normalization, minimums, TLS verify); langchaingo/genkit adapters follow functional-options; Go/Java README example signatures verified; no changes; next direction is Python SDK |
| 2026-10-01 00:11 | Python SDK | ✅ Fourth cycle: search param building matches backend contract (0-sentinel omission); design docs: 22.md findings verified implemented (ExpRagService userId isolation); no changes; next direction is JS/TS SDK |
| 2026-10-01 00:16 | JS/TS SDK | ✅ Fourth cycle: wire-helpers.ts defensive parsing verified; architecture docs: stale "Express" claims corrected to Node.js/axios in EN/ZH (proxy uses built-in http + axios only); no code changes; next direction is Demo |
| 2026-10-01 00:19 | Demo | ✅ Fourth cycle: Experiences/Feedback controllers validated; user guide: TESTING.md has no hardcoded test counts (complies with output-based discipline); no changes; next direction is Backend (fifth cycle) |
| 2026-10-01 00:33 | Backend | ✅ Fifth cycle: fixed PendingMessageProcessor init log reading @Value before injection (always said enabled=false; now logged in @PostConstruct, verified enabled=true); LlmService clean; full acceptance passed, new baseline 32d5dfb; next direction is Go SDK |
| 2026-10-01 00:39 | Go SDK | ✅ Fifth cycle: dto/session.go wire fields match backend; Python README Wire Format section verified claim-by-claim against @JsonProperty; no changes; next direction is Python SDK |
| 2026-10-01 00:42 | Python SDK | ✅ Fifth cycle: record_observation wire building matches backend contract field-by-field; design docs: v30 lock-sharing claim verified in code, index 24.6.md description updated through v30; no code changes; next direction is JS/TS SDK |
| 2026-10-01 00:45 | JS/TS SDK | ✅ Fifth cycle: observation.ts extended-field parsing (safe conversion + dual key-variant fallback) matches live wire format; architecture docs: localhost binding claim matches application.yml default; no changes; next direction is Demo |
| 2026-10-01 00:47 | Demo | ✅ Fifth cycle: Projects/Tools controllers validated (session context cleanup in finally); user guide: DEVELOPMENT.md structure claims verified; no changes; next direction is Backend (sixth cycle) |
| 2026-10-01 00:50 | Backend | ✅ Sixth cycle: TemplateService (placeholder fail-fast) and CursorService (registry IO) clean; API-doc memory/refine section verified incl. live 400 test; no changes; next direction is Go SDK |
| 2026-10-01 00:53 | Go SDK | ✅ Sixth cycle: experience/ICL dto camelCase wire fields match backend; @EnableCortexMem annotation verified as real @Import of the auto-configuration; no changes; next direction is Python SDK |
| 2026-10-01 01:06 | Python SDK | ✅ Sixth cycle: list/get param building and batch validation match cross-SDK contract; design docs: 19.md 8-prerequisites claim verified method-by-method; no changes; next direction is JS/TS SDK |
| 2026-10-01 01:09 | JS/TS SDK | ✅ Sixth cycle: client-options defaults match Go SDK; architecture docs: Viewer "15 methods" verified against 15 active mappings (16th grep hit is a commented-out route); no changes; next direction is Demo |
| 2026-10-01 01:36 | Demo | ✅ Sixth cycle: final 3 controllers (Memory/Extraction/Chat) validated — all 12 demo controllers now reviewed across cycles; user guide: DEVELOPMENT.md port flag example and actuator health response verified live; no changes; next direction is Backend (seventh cycle) |
| 2026-10-01 01:38 | Backend | ✅ Seventh cycle: ExpRagService deep review (userId isolation, fallback chains) and SummaryGenerationService clean; API-doc quality-distribution verified live (shape + 400); no changes; next direction is Go SDK |
| 2026-10-01 01:42 | Go SDK | ✅ Seventh cycle: extraction dto camelCase fields match ApiResponses; JS README Wire Format claims (5 groups) verified against @JsonProperty incl. FeedbackRequest; no changes; next direction is Python SDK |
| 2026-10-01 01:47 | Python SDK | ✅ Seventh cycle: session wire and URL-encoded PATCH path verified; design docs: 20.md all-resolved claim holds, formatExtractedData "not implemented" note still accurate; no changes; next direction is JS/TS SDK |
| 2026-10-01 01:52 | JS/TS SDK | ✅ Seventh cycle: index.ts export surface complete; architecture docs: privacy-stripping claim corrected in EN/ZH (proxy strips tags entirely, not [REDACTED] replace; stripping happens in proxy/tag-stripping.js); no code changes; next direction is Demo |
| 2026-10-01 01:57 | Demo | ✅ Seventh cycle: JS demo app.ts reviewed (Express structure clean — all 4 demo stacks covered); user guide: DEVELOPMENT.md ObservationService references verified as intentional convention examples; no changes; next direction is Backend (eighth cycle) |
| 2026-10-01 02:12 | Backend | ✅ Eighth cycle: AgentService processToolUseAsync core flow reviewed (age guard, dedup, enqueue-first, exception taxonomy) — clean; API-doc search response shape verified live; no changes; next direction is Go SDK |
| 2026-10-01 02:17 | Go SDK | ✅ Eighth cycle: examples/basic idiomatic (error handling, V14 features); JS README error-handling example imports verified against exports; no changes; next direction is Python SDK |
| 2026-10-01 02:22 | Python SDK | ✅ Eighth cycle: experiences/ICL wire mapping verified; design docs: 21.md 10-prerequisite table consistent with quick-ref, lock-leak finding has reasoned decision; no changes; next direction is JS/TS SDK |
| 2026-10-01 02:11 | JS/TS SDK | ✅ Eighth cycle: session.ts request types match backend DTOs field-by-field; architecture docs: llm.provider config claim matches SpringAiConfig and application.yml; no changes; next direction is Demo |
| 2026-10-01 02:19 | Demo | ✅ Eighth cycle: helper classes (FileReadTool AOP note, DemoProperties) clean; user guide: TESTING.md drift-prone Lines column removed in EN/ZH (4 tables, 5 drifted values) and duplicate table header fixed; "15 test functions" verified accurate; no code changes; next direction is Backend (ninth cycle) |
| 2026-10-01 02:26 | Backend | ✅ Ninth cycle: fixed started_at never populated on all 5 session creation paths (JPA explicit insert bypassed column DEFAULT); verified live ISO timestamp; MemoryRefineService lock-sharing clean; full acceptance passed, new baseline 33dc573; next direction is Go SDK |
| 2026-10-01 02:31 | Go SDK | ✅ Ninth cycle: dto package fully covered (management/misc/search verified against live responses); Java README properties table and method endpoints verified; no changes; next direction is Python SDK |
| 2026-10-01 02:44 | Python SDK | ✅ Ninth cycle: management methods (dual-mode update with conflict detection, encoded paths) clean; design docs: 0.1.md v7 fixes verified in code (List param, BeanOutputConverter/templateClass); no changes; next direction is JS/TS SDK |
| 2026-10-01 02:52 | JS/TS SDK | ✅ Ninth cycle: experience.ts dual-format parsing verified; architecture docs: MCP transport table verified against application.yml config + live /sse 200 (POST /mcp 404 expected under SSE protocol); no changes; next direction is Demo |
| 2026-10-01 02:57 | Demo | ✅ Ninth cycle: Go eino example clean (post-fix module refs); user guide: TESTING.md sections 4-8 verified item-by-item (flags, workflows, SERVER_URL, MCP auto-detect matches live test); no changes; next direction is Backend (tenth cycle) |
| 2026-10-01 03:02 | Backend | ✅ Tenth cycle: LlmQualityScorer and ExperienceTemplate clean; API-doc feedback error paths verified live (400 missing fields, 404 absent UUID); no changes; next direction is Go SDK |
| 2026-10-01 03:07 | Go SDK | ✅ Tenth cycle: retry internals (jittered backoff, context fast-fail, sentinel mapping) verified; Go README option table and error section match implementation; no changes; next direction is Python SDK |
| 2026-10-01 03:12 | Python SDK | ✅ Tenth cycle: SessionStartResponse from_wire/to_dict carry inline updateFiles contract knowledge; design docs: 3.md honestly marked SUPERSEDED; no changes; next direction is JS/TS SDK |
| 2026-10-01 03:17 | JS/TS SDK | ✅ Tenth cycle: dto/search.ts contracts verified (orderBy dual-format, fell_back, hasMore); architecture docs: virtual threads claim matches application.yml; no changes; next direction is Demo |
| 2026-10-01 03:13 | Demo | ✅ Tenth cycle: langchaingo example clean; user guide: DEPLOYMENT.md 9 endpoint references verified (actuator/info+metrics 200, prometheus 404 correct as optional-dependency absence, section clearly marked Optional); no changes; next direction is Backend (eleventh cycle) |
| 2026-10-01 03:21 | Backend | ✅ Eleventh cycle: MemoryRefineService.refineObservations (survivor tracking, cooldown filter) and LogsController clean; API-doc import endpoints verified bidirectionally (5/5); no changes; next direction is Go SDK |
| 2026-10-01 03:26 | Go SDK | ✅ Eleventh cycle: integration submodule go.mod hygiene verified (module/require/replace intact); Python README error-handling imports match error.py; no changes; next direction is Python SDK |
| 2026-10-01 03:31 | Python SDK | ✅ Eleventh cycle: system methods clean — Python SDK now fully covered across cycles; design docs: 0.2.md array-handling solution matches implementation; no changes; next direction is JS/TS SDK |
