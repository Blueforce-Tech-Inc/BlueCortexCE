# BlueCortexCE 上下文安全 — 与 Hermes 扫描能力对照（盘点）

> **目的**：用少量代码锚点说明 **当前 Java 后端与相关层**已具备的防护，以及相对 Hermes（如 `memory_tool` / prompt 扫描、`<memory-context>` 消毒）的**缺口**，便于排期与验收。  
> **Hermes 期望行为摘要**：[`02-bluecortexce-recommendations.md`](02-bluecortexce-recommendations.md) §10.5；**注入落点表**：[`04-ce-injection-and-context-api-surface.md`](04-ce-injection-and-context-api-surface.md)。  
> **接力队列**：[`../11-research-backlog.md`](../11-research-backlog.md)  
> **日期**：2026-04-23（代码实地复核）

---

## 1. 已观察到能力（本仓库）

| 类别 | 说明 | 代码锚点（示例） |
|------|------|------------------|
| 模板占位符逃逸 | 对用户可控片段转义 `{{` / `}}`，降低 **prompt 模板注入** | [`TemplateService.escapeTemplateValue`](../../../../backend/src/main/java/com/ablueforce/cortexce/service/TemplateService.java) |
| 用户 prompt 长度 | `handleUserPrompt` 对 `promptText` **截断**到 `MAX_USER_PROMPT_LENGTH`；注释称 sanitize，**当前实现主要为长度限制** | [`IngestionController.handleUserPrompt`](../../../../backend/src/main/java/com/ablueforce/cortexce/controller/IngestionController.java) |
| 向量参数校验 | 降低畸形向量/query 带来的风险（含测试提及 SQL injection 场景） | [`VectorValidator`](../../../../backend/src/main/java/com/ablueforce/cortexce/util/VectorValidator.java) |
| 上下文路径 | `ContextService` 等对路径规范化、拒绝 `..` 逃逸 | [`ContextService`](../../../../backend/src/main/java/com/ablueforce/cortexce/service/ContextService.java)（路径相关分支） |
| 会话消息清理 | `stripSystemReminders` 等 **呈现层**处理 | [`ContextService.stripSystemReminders`](../../../../backend/src/main/java/com/ablueforce/cortexce/service/ContextService.java) |
| Tag 防递归写入（TS 层） | `webui/src/utils/tag-stripping.ts` 在存储前剥离 `<claude-mem-context>`、`<private>`、`<system_instruction>`、`<system-instruction>`、`<persisted-output>`、`<system-reminder>`；**防止观察内容被重复注入** | [`tag-stripping.ts`](../../../../webui/src/utils/tag-stripping.ts)（**非**围栏消毒，用于递归写入防护） |

## 2. 相对 Hermes 的常见缺口（待产品/安全拍板）

| Hermes 思想 | CE 现状（截至 2026-04-23 实地复核） | 代码锚点 |
|-------------|-------------------------------------------|------------|
| 写入记忆前 **injection 模式 + 不可见 Unicode** 扫描 | 后端 Java 层**无**统一入口；TS 层 `tag-stripping.ts` 仅针对递归存储标签，不扫描 injection 模式或零宽字符 | [`IngestionController.handleUserPrompt`](../../../../backend/src/main/java/com/ablueforce/cortexce/controller/IngestionController.java)（仅长度截断）；[`tag-stripping.ts`](../../../../webui/src/utils/tag-stripping.ts)（仅递归防护标签） |
| **`<memory-context>`** 类围栏包裹上下文输出 | 后端 `/api/context/inject`、`/api/context/semantic`、`/api/context/generate` 返回**纯文本**，输出无任何 fence 标记（`renderEmptyState` → `"# project — no memories yet"`；`renderTimeline` → `## Last Session Summary` 等）。**即使后续添加 fence，输出层也无 strip 逻辑**。 | [`ContextService.renderEmptyState`](../../../../backend/src/main/java/com/ablueforce/cortexce/service/ContextService.java)（行 873，无 fence）；[`context-injection.ts`](../../../../webui/src/utils/context-injection.ts)（写文件用 `<claude-mem-context>`，不用于 LLM 注入输出） |
| **伪造 fence 逃逸检测**（Hermes `sanitize_context` 先 strip 伪造闭合标签再包装） | 后端 **完全没有**对应逻辑。攻击者若能在 Observation 内容中注入 `</memory-context>` 或类似闭合标签，无法被检测或转义。 | 无对应类 |
| AGENTS.md / SOUL.md 类文件扫描 | 依部署路径可能在 **客户端**；后端若提供预览/注入合并需对齐 | 对照 [`02`](02-bluecortexce-recommendations.md) §10.5 表格 |

## 3. 说明

- 本文件**不做威胁建模全文**；仅服务「Hermes 借鉴项 ↔ CE 代码事实」的快速对齐。  
- 插件层（TypeScript）若单独做围栏，应在 [`04`](04-ce-injection-and-context-api-surface.md) 与本文之间**只维护一处**详细清单，另一处用链接。
