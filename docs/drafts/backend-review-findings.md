> **用途**: 记录 Backend 代码审查发现的问题（仅 review，不修复）
> **维护者**: PM Agent
> **更新频率**: 每次巡检审查 Backend 时更新
> **修复策略**: 积累到一定量后统一修复

# Backend 代码审查问题记录

## 审查规则

- 每次随机抽查 1-2 个 backend 源文件
- 仅记录问题，不做修复（节省巡检时间）
- 严重级别：P0（必须修复）/ P1（应该修复）/ P2（建议修复）
- 达到 5 个 P0 或 10 个 P1 问题时，触发集中修复

## 问题列表

_暂无问题记录_

<!-- 格式示例：
### 2026-03-29 | SearchService.java

| 文件 | 行号 | 问题 | 级别 | 状态 |
|------|------|------|------|------|
| SearchService.java | 45 | 空指针风险：未检查 searchRequest 的 null 值 | P1 | 待修复 |
-->

---

### 2026-03-29 | Backend 审查 #1

**抽查文件**: `PendingMessageEntity.java`, `VectorValidator.java`, `IngestionController.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | VectorValidator.java | ~148 `countDimensions()` | 对空向量 `[]` 返回 1 而非 0（逻辑上应返回 0 个维度）。当前未被调用，但作为 public 方法存在误导风险 | P2 ✅已修复 |
| 2 | IngestionController.java | ~138 `handleSessionEnd()` | `debug` 变量声明但从未从 request body 赋值，debug 分支永远不执行。要么删除 dead code，要么从 body 中提取 debug 字段 | P2 ✅已修复 |
| 3 | IngestionController.java | ~108 | `toolInput`/`toolResponse` 类型转换逻辑可简化：`(value instanceof String s) ? s : value.toString()` | P2 ✅已修复 |
| 4 | PendingMessageEntity.java | 全文 | 无 Lombok，全部手写 getter/setter，Java 21 可考虑 record 或 Lombok 减少样板代码 | P2 ⏭跳过（代码库风格一致） |

**审查结论**: 整体质量良好，安全意识到位（P0/P1 注释清晰），无 P0/P1 级别问题。

---

### 2026-03-29 | Backend 审查 #2

**抽查文件**: `SSEBroadcaster.java`, `HealthController.java`

| # | 文件 | 行号 | 问题 | 级别 |
|---|------|------|------|------|
| 1 | SSEBroadcaster.java | ~63 `broadcast()` | `eventName` 参数被接受但完全忽略（仅 data，不调用 `.name(eventName)`） | P2 ✅已修复 |
| 2 | HealthController.java | ~78 `/api/health` | 无实际健康检查——永远返回 "ok" 即使 DB 宕机 | P2 ✅已修复 |
| 3 | HealthController.java | ~110 `/api/version` | `getVersion()` 在 IDE 下返回 null，fallback 不具信息量 | P2 ✅已修复 |

**审查结论**: 无 P0/P1 问题。SSEBroadcaster 并发安全处理良好（snapshot copy），HealthController 结构清晰。

---

### 2026-03-29 | 文档审查 #1 — API.md 全面对照

**审查范围**: `docs/API.md` vs 实际 Controller 端点映射

| # | 问题 | 文档路径 | 实际路径 | 级别 |
|---|------|----------|----------|------|
| 1 | Session API 路径全部错误 | `POST /api/sessions` | `POST /api/session/start` | **P1** ✅已修复 |
| 2 | Session API 路径错误 | `GET /api/sessions/{id}` | `GET /api/session/{id}` | **P1** ✅已修复 |
| 3 | Session API 虚构端点 | `GET /api/sessions` (列表) | 不存在 | **P1** ✅已移除 |
| 4 | Session API 虚构端点 | `DELETE /api/sessions/{id}` | 不存在 | **P1** ✅已移除 |
| 5 | Messages API 路径错误 | `POST/GET /api/sessions/{id}/messages` | 通过 ingest 端点 | **P1** ✅已重写 |
| 6 | Extraction 路径缺少 {templateName} | `GET /api/extraction/latest` | `GET /api/extraction/{templateName}/latest` | **P1** ✅已修复 |
| 7 | Extraction 路径缺少 {templateName} | `GET /api/extraction/history` | `GET /api/extraction/{templateName}/history` | **P1** ✅已修复 |
| 8 | 缺失 PATCH user 端点文档 | — | `PATCH /api/session/{id}/user` | P2 ✅已添加 |
| 9 | Search 重复出现在两个章节 | Memory + Search 各一次 | `POST /api/memory/search` | P2 ✅已去重 |
| 10 | Ingest 缺失端点 | — | `POST /api/ingest/tool-use` | P2 ✅已添加 |
| 11 | Ingest 缺失端点 | — | `POST /api/ingest/observation` | P2 ✅已添加 |
| 12 | Viewer 端点全部未文档化 | — | 多个 Viewer 端点 | P2 ✅已添加 |
| 13 | Mode 端点全部未文档化 | — | 7 个 Mode 端点 | P2 ✅已添加 |
| 14 | Logs 端点全部未文档化 | — | `GET/POST /api/logs/*` | P2 ✅已添加 |

**修复结果**: API.md 全面重写，所有 P1 + P2 问题已修复（2026-03-29 commit `1bd6572`）。
