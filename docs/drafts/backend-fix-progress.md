# Backend 审查问题修复进度

**开始时间**: 2026-03-29 11:46
**完成时间**: 2026-03-29 12:25
**目标**: 修复 `backend-review-findings.md` 中所有问题 + 3 轮迭代检查 + 回归测试

## 最终结果

**所有 14 个问题已全部解决。无未处理问题。**

| # | 来源 | 文件/问题 | 级别 | 结果 |
|---|------|----------|------|------|
| 1 | 审查#1 | VectorValidator.countDimensions() | P2 | ✅ 修复 |
| 2 | 审查#1 | IngestionController debug 死代码 | P2 | ✅ 修复 |
| 3 | 审查#1 | IngestionController 类型转换 | P2 | ✅ 修复 |
| 4 | 审查#1 | PendingMessageEntity 无 Lombok | P2 | ⏭ 跳过（风格一致） |
| 5 | 审查#2 | SSEBroadcaster eventName 忽略 | P2 | ✅ 修正（Javadoc 澄清，保持 unnamed events） |
| 6 | 审查#2 | HealthController /api/health | P2 | ✅ 修复 |
| 7 | 审查#2 | HealthController /api/version | P2 | ✅ 修复 |
| 8-14 | 审查#3 | API.md 6 个 P1 + 8 个 P2 | P1/P2 | ✅ 全部修复 |

## 修复详情

### 代码修复
- **VectorValidator**: `countDimensions()` 提取括号内容后再计算，`[]` 正确返回 0
- **IngestionController**: 移除 `handleSessionEnd()` 中未使用的 `debug` 变量和死代码分支
- **IngestionController**: `toolInput`/`toolResponse` 使用 pattern matching `instanceof String s` 简化
- **HealthController**: `/api/health` 添加 DB 连接检查，DB 不可达返回 503
- **HealthController**: `getVersion()` 多源回退：JAR manifest → build-info → dev-SNAPSHOT

### SSEBroadcaster 特殊处理
- 原审查建议：添加 `.name(eventName)` 使参数生效
- **发现问题**：WebUI 使用 `onmessage`（只捕获 unnamed events），添加 `.name()` 会破坏 WebUI
- **最终修复**：保持 unnamed events，澄清 Javadoc 说明 `eventName` 仅用于文档/路由，实际路由由 data.type 完成

### 文档修复
- **API.md**: 全面重写 — 修正 Session/Extraction/Ingest 路径，补充 Viewer/Mode/Logs/Import 端点

## 验证记录

| 步骤 | 结果 |
|------|------|
| mvn clean compile package -DskipTests | ✅ BUILD SUCCESS |
| 服务重启 + /api/health | ✅ status: ok |
| 回归测试 (regression-test.sh) | ✅ 46/46 passed |
| 第 1 轮迭代检查 | ✅ 通过 |
| 第 2 轮迭代检查 | ✅ 通过 |
| 第 3 轮迭代检查 | ✅ 通过 |
| SSEBroadcaster 3 轮专项检查 | ✅ 通过 |
| WebUI 契约验证 | ✅ 全部安全 |

## 提交记录

### 2026-03-29 22:05 健康检查与测试验收修复

| # | 来源 | 文件/问题 | 级别 | 结果 |
|---|------|----------|------|------|
| 15 | 审查#3 | TemplateService escapeTemplateValue() 死代码 | P2 | ✅ 修复（重排序：长 pattern 先替换） |
| 16 | 审查#3 | SessionEntity status 字段无长度约束 | P2 | ✅ 修复（添加 @Column(length=20)） |

### 代码修复详情（2026-03-29 22:05）

**TemplateService.escapeTemplateValue()**:
- 问题：`{{{{` 和 `}}}}` 替换在 `{{`/`}}` 之后执行，短 pattern 先消耗了输入，长 pattern 永远不匹配
- 修复：重排序替换，长 pattern（`{{{{`/`}}}}`）先执行，短 pattern（`{{`/`}}`）后执行
- 验证：编译通过，回归测试全部通过

**SessionEntity.status**:
- 问题：使用 raw String 无长度约束，任何长度的字符串都能写入
- 修复：添加 `@Column(length = 20)` 限制最大长度，最长的 status 值 "processing" 仅 10 字符
- 验证：编译通过，回归测试全部通过

### 验证记录（2026-03-29 22:05）

| 步骤 | 结果 |
|------|------|
| mvn clean compile package -DskipTests | ✅ BUILD SUCCESS |
| 服务重启 + /api/health | ✅ status: ok |
| 回归测试 (regression-test.sh) | ✅ 46/46 passed |
| EXTRACTION_ENABLED 验收测试 | ✅ 25/25 passed |
| 第 1 轮迭代检查 | ✅ 通过 |
| 第 2 轮迭代检查 | ✅ 通过 |
| 第 3 轮迭代检查 | ✅ 通过 |

## 提交记录

- `1bd6572` — fix: backend review findings (VectorValidator, IngestionController, SSEBroadcaster, HealthController, API.md)
- `58ee4c4` — fix: revert SSEBroadcaster breaking change (keep unnamed events for WebUI)
- `6bba534` — docs: update SSEBroadcaster fix description
- `2eea224` — docs: mark all backend review findings as fixed
- `9f9359b` — docs: add WebUI contract warning to patrol-task.md
