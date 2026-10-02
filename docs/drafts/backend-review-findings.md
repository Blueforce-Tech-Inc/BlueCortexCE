# Backend Review Findings

> **Purpose**: 记录当前仍需处理的 Backend 代码审查发现。
> **Updated by**: 定时项目维护任务。
> **Update rule**: 新发现必须记录文件、行号、问题、严重级别和处理状态；已解决的详细历史归档，当前未解决项保留在本文件。

## Current Status

| Severity | Open | Rule |
|----------|------|------|
| P0 | 0 | 立即修复并复测 |
| P1 | 0 | 优先修复并复测 |
| P2 | 0 | 本轮完整验收阶段处理或明确标记为已跳过 |

## Open Findings

### P2-1: Java Demo `/refine` response differs from other SDK demos

- **Scope**: `examples/cortex-mem-demo/ManagementController.java` and the Go/Python/JS demo equivalents
- **Problem**: Java returns `{"status":"refinement triggered","project":"..."}`, while the other demos return `{"status":"refined"}`. The Java response is more informative, but the cross-SDK demo contract is inconsistent.
- **Status**: ✅已跳过（2026-09-30）。这是各 Demo 自己的管理接口响应文案差异，不是共享后端 API 契约；Java Demo 的响应包含更多上下文，保持现状可读性更好。本轮不改代码，也不新增跨 Demo 契约测试。

### P2-2: Rate-limit fallback key is unique per call, disabling the limit

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/service/RateLimitService.java:264` (`generateFallbackKey`)
- **Problem**: When `tryAcquire` receives a null/empty key it falls back to
  `"fallback:" + hash + ":" + UUID.randomUUID().substring(0, 8)`. The random suffix makes every
  call produce a distinct key, so each request lands in a fresh `SlidingWindow` with count=1 and
  is always allowed. The rate limit is silently a no-op on that path, and the 300-second
  cleanup then has to evict these one-shot entries.
- **Current impact**: none on live traffic — the only caller (`IngestionController:132`) builds
  `"tool-use:" + contentSessionId` *after* validating `session_id` is non-blank
  (`IngestionController:122-127`), so the fallback is never reached today. It is a latent defect
  for any future caller that passes null/empty.
- **Proposed fix**: drop the random suffix and keep `"fallback:" + hash(ip-or-thread + minute
  bucket)`. The hash is already privacy-preserving and stable within a minute, which is the
  granularity the comment claims to use.
- **Status**: ✅已修复（2026-10-02）。`generateFallbackKey()` 去掉 `UUID.randomUUID()` 后缀，只保留
  `"fallback:" + hex(hash(identifier + minuteBucket))`。键在同一分钟内稳定，滑动窗口重新生效；
  隐私性不变（仍只暴露哈希），同时不再产生一次性窗口条目。同步移除随之失效的 `UUID` import。
  编译验证：本轮 `mvn clean package -DskipTests` 通过，回归 + EXTRACTION 验收全部通过。

### P2-3: `RateLimitService` javadoc shows a method overload that does not exist

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/service/RateLimitService.java:21`
- **Problem**: The class-level usage example reads
  `rateLimitService.tryAcquire("user:123", 10, 60)`, but the class only exposes
  `tryAcquire(String key)`; the limits come from `claudemem.rate-limit.*` configuration.
  Anyone copying the example gets a compile error and may conclude per-call limits are supported.
- **Proposed fix**: correct the example to `tryAcquire("user:123")` and state that the threshold
  and window are configured via `claudemem.rate-limit.max-requests` / `window-seconds`.
- **Status**: ✅已修复（2026-10-02）。示例改为 `tryAcquire("user:123")`，并写明阈值与窗口来自
  `claudemem.rate-limit.max-requests` / `window-seconds` 配置。

### P2-4: `ProjectFilterService` is dead code kept alive only by its own unit tests

- **Scope**: `backend/src/main/java/com/ablueforce/cortexce/service/ProjectFilterService.java`
- **Problem**: The class is not annotated `@Service`/`@Component` and has no production caller —
  `grep` over `backend/src/main` finds references only inside the class itself. Its ~40 unit
  tests in `ProjectFilterServiceTest` pass and give the impression of covered behaviour, but no
  request path uses `shouldInclude` or `isUnsafeDirectory`. The class comment already admits it
  "is not currently wired into any processing pipeline".
- **Options**: (a) wire it into the pipeline that should filter project paths (e.g. CLAUDE.md
  generation or observation file capture) — a design change needing its own acceptance; or
  (b) delete the class and its test until a consumer exists, so the suite does not imply coverage
  that no runtime path provides.
- **Status**: ✅已跳过（2026-10-02），保留为工具类。核查依据：`backend/src/main` 内除自身外无任何
  引用，且全仓 `main` 源码没有任何目录遍历（`Files.walk` / `walkFileTree` / `Files.list`）——
  当前设计里根本不存在“项目文件扫描”这条链路，接入等于凭空新增一条管线。删除则要连带删掉 40 个
  正确的工具契约测试，收益为负。类注释已明确声明未接入流水线，故保持原样。
  **复审触发条件**：一旦后端出现目录遍历/CLAUDE.md 写入路径过滤的需求，改为接入本类而非另写一套。

## Processing Rules

- SDK/Demo findings are fixed in place with focused compile/test verification.
- Backend findings are fixed in place when small and safe; otherwise they remain here until the complete acceptance stage.
- Every finding must end as a code fix, a documented design decision, or an explicit skipped status. Reporting alone is not a valid resolution.
- After resolution, append the verification result and commit identifier here before moving the detailed entry to an archive.

## Archived History

The complete historical review log through 2026-05-07 is preserved in [`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md). Do not modify that archive; future resolved history should use a new dated archive when this file reaches the growth threshold again.
