> **用途**: 记录 backend-review-findings.md 问题的集中修复进度
> **维护者**: PM Agent
> **来源**: backend-review-findings.md (2026-03-31 集中修复批次)

# Backend Review 集中修复记录

## 2026-03-31 | 集中修复批次

**修复范围**: backend-review-findings.md 中 12 个未修复问题

| # | 文件 | 问题 | 级别 | 修复方式 | 状态 |
|---|------|------|------|----------|------|
| 1 | WorktreeDetector.java L83 | WORKTREES_PATTERN 依赖 gitdir 路径中 `.git/worktrees/` 段，core.worktree 非标准位置会误判 | P2(低) | 在 detectWorktree Javadoc 中明确说明此限制 | ✅已修复 |
| 2 | WorktreeDetector.java L91 | getProjectContext 中 primary 值可能不一致 | P2(低) | 改用 worktreeInfo.worktreeName() 作为 worktree 场景下的 primary | ✅已修复 |
| 3 | ExtractionController.java L104-117 `/run` | Swagger 描述为 "synchronously" 但无超时说明 | P2(低) | 在 @Operation description 中加超时提示 | ✅已修复 |
| 4 | PendingMessageEventListener.java L46 | catch 块仅 log.error，消息状态不变，存在无限重试风险 | P2(低) | catch 中将消息标记为 "failed" | ✅已修复 |
| 5 | ExperienceTemplate.java L88 | buildSimpleExperience() action/outcome 为 null 时输出大量 N/A | P2 | content 前 200 字符作为 fallback 替代 N/A | ✅已修复 |
| 6 | CortexMemClientImpl.java executeWithRetrySilent | interrupt during retry sleep 需加 WARN 日志 | P2 | catch 中已有 interrupt() 恢复，加 WARN 日志 | ✅已修复 |
| 7 | CortexMemClientImpl.java isRetryable | 500 排除在 retry 外，需说明设计原因 | P2(低) | Javadoc 中加注说明（500 是 code bug，非 transient） | ✅已修复 |
| 8 | client_impl.go doFireAndForget | 内联 jitter 计算逻辑可提取 | P2(低) | 提取 jitteredBackoff(baseDelay, attempt) 辅助函数 | ✅已修复 |
| 9 | client-options.ts L31 | SDK_VERSION = '1.0.0' 与 package.json 重复定义 | P2(低) | Javadoc 说明重复原因（bundler 兼容性）及发布时同步要求 | ✅已修复 |
| 10 | examples/http-server/app.ts L4 | docstring "covering all 25 SDK methods" 不精确 | P2(极低) | 改为 "covering all 25 public SDK API methods (plus /health)" | ✅已修复 |
| 11 | API.md | TestController (/api/test) 未文档化 | P2(低) | 跳过（有意排除，@Profile("!prod")） | ⏭跳过 |
| 12 | API-zh-CN.md | 更新日志停留在 0.1.0（2026-03-13） | P2 | 更新 changelog 增加 0.1.0-beta 记录 | ✅已修复 |

**编译/测试结果**:
- Backend: `mvn clean compile` ✅
- Java SDK: `mvn clean compile` ✅
- Go SDK: `go build ./...` + `go test ./...` ✅（267 tests passed）
- JS SDK: `npx tsup` build ✅（CJS+ESM+DTS） + `npx vitest run` ✅（198 tests passed）
- Regression: `bash scripts/regression-test.sh` ✅（46/46 tests passed）

**汇总**: 修复 11 个，跳过 1 个（TestController 有意排除）。

## 2026-03-31 | 集中修复批次 #2

**来源**: backend-review-findings.md (#11, 2026-03-31 03:05) — 5 个未修复 P2 问题

| # | 文件 | 问题 | 级别 | 修复方式 | 状态 |
|---|------|------|------|----------|------|
| 1 | SettingsService.java L148-165 | `String.valueOf(null)` 返回字面量 "null" | P2 | 所有 `String.valueOf(updates.get(...))` → `Objects.toString(v, "")` | ✅已修复 |
| 2 | SettingsService.java L117 | `ATOMIC_MOVE` 跨文件系统抛异常 | P2(低) | 添加 `AtomicMoveNotSupportedException` catch，fallback 到 `REPLACE_EXISTING` | ✅已修复 |
| 3 | SettingsService.java L28 | `settings` 字段非线程安全 | P2 | 添加 `volatile` 关键字 | ✅已修复 |
| 4 | AppSettings.java L382 | `toMap()` 中 `CLAUDE_MEM_CONTEXT_MAX_OBSERVATIONS` 返回 String 而非 int | P2(低) | 新增 `getContextMaxObservationsInt()` 方法，`toMap()` 改用 int 版本 | ✅已修复 |
| 5 | ViewerController.java L497-498 | 同样的 `String.valueOf(null)` 模式 | P2 | 改用 `Objects.toString(v, "")` | ✅已修复 |

**编译/测试结果**:
- Backend: `mvn clean compile package -DskipTests` ✅
- Regression: `bash scripts/regression-test.sh --skip-build` ✅（46/46 tests passed）
- Phase 3 Acceptance: `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` ✅（25/25 tests passed）

**汇总**: 修复 5 个 P2 问题（null 安全、原子写入回退、线程安全、类型一致性）。

## 2026-03-31 | 集中修复批次 #3

**来源**: backend-review-findings.md (#12, 2026-03-31 04:56) — 1 个未修复 P2 问题

| # | 文件 | 问题 | 级别 | 修复方式 | 状态 |
|---|------|------|------|----------|------|
| 1 | ModeService.java L46 | `modeCache` HashMap 非线程安全 | P2 | 改用 `ConcurrentHashMap` | ✅已修复 |

**附注**: ModeController Swagger 问题 (#2) 经核实无问题（200/400 响应示例已正确分离）。

**编译/测试结果**:
- Backend: `mvn clean compile package -DskipTests` ✅
- Regression: `bash scripts/regression-test.sh --skip-build` ✅（46/46 tests passed）
- Phase 3 Acceptance: `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` ✅（25/25 tests passed）

**汇总**: 修复 1 个 P2 问题（线程安全：HashMap → ConcurrentHashMap）。
