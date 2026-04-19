# BlueCortexCE：上下文「产出」链路速写（时间线 vs 语义 vs ICL）

> **角色**：给 Agent **快速定位 Java 调用链**，补充 [`12`](./12-bluecortex-api-memory-surface.md) 的「谁调谁」；细节以源码为准。  
> **最后更新**：2026-04-19

---

## 1. 时间线注入（`generateContext` 主干）

**入口**：`ContextController` / `SessionController` 等调用的 `ContextService.generateContext(...)`。

**主干步骤**（概念序）：

1. `validateProjectPath` → 取 `ObservationEntity`：`observationRepository.findByTypeAndConcepts(...)`（type/concept 过滤 + 条数上限）。  
2. 取 `SummaryEntity` 列表，与观察 **混排** 为 `TimelineItem`。  
3. `renderTimeline`：Markdown 头（项目名、token 经济摘要）、按日分组、按文件分组渲染观察/摘要。  
4. 可选：`getPriorSessionMessages` → `renderPreviouslySection`，拼在末尾。

代码锚点：

```229:285:backend/src/main/java/com/ablueforce/cortexce/service/ContextService.java
    public String generateContext(String projectPath, ContextConfig config, String sessionId) {
        // ...
        List<ObservationEntity> observations = observationRepository.findByTypeAndConcepts(
            projectPath, types, concepts, conceptsEmpty, config.getTotalObservationCount()
        );
        // ...
        List<TimelineItem> timeline = buildTimeline(observations, summaryItems);
        String timelineContext = renderTimeline(project, timeline, observations, config);
        // ...
    }
```

---

## 2. 语义注入（`POST /api/context/semantic`）

> **仅覆盖 Java 链**。Node worker 上同名路由由 `SearchRoutes` + **Chroma** 实现，Hook（`session-init`）默认命中 worker；见 [`12-bluecortex-api-memory-surface.md`](./12-bluecortex-api-memory-surface.md) §1–2。

**不**复用 `generateContext`；**Java** 路径为：**embed(query)** → **`SearchService.search`** → 手工拼 `## Relevant Past Work (semantic match)` + 每条 `Observation` 的 title/content。

```425:484:backend/src/main/java/com/ablueforce/cortexce/controller/ContextController.java
    @PostMapping(value = "/semantic", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> semanticContext(@RequestBody Map<String, Object> body) {
        // ...
        float[] queryVector = embeddingService.embed(query);
        SearchService.SearchRequest searchRequest = new SearchService.SearchRequest(
            project, query, queryVector, null, null, null, null, null, limit, 0, null
        );
        SearchService.SearchResult result = searchService.search(searchRequest);
        // ... append obs title/content ...
    }
```

**约束**：`q` 长度 `< 20` 时直接空返回（与 Hook 侧 prompt 长度配合）。

---

## 3. ICL / Spring 体验块（`ExpRagService`）

与 **Hook 注入**并行的一条「把历史观察变成 Experience 段落」的路径：`MemoryController` 等可调 `expRagService.buildICLPrompt(task, experiences, maxChars)`；内部拼「Relevant historical experiences」块并在 `maxChars` 下截断。

```177:218:backend/src/main/java/com/ablueforce/cortexce/service/ExpRagService.java
    public String buildICLPrompt(String currentTask, List<Experience> experiences, int maxChars) {
        if (experiences == null || experiences.isEmpty()) {
            return "Current task:\n" + (currentTask != null ? currentTask : "");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Relevant historical experiences:\n\n");

        int footerReserve = 50;
        int availableForExperiences = Math.max(0, maxChars - footerReserve);

        for (int i = 0; i < experiences.size(); i++) {
            Experience exp = experiences.get(i);
            String expBlock = String.format("### Experience %d\n**Task**: %s\n**Strategy**: %s\n**Outcome**: %s\n**Quality**: %.2f\n\n",
                i + 1, exp.task(), exp.strategy(), exp.outcome(), exp.qualityScore());

            if (sb.length() + expBlock.length() > availableForExperiences) {
                log.debug("ICL prompt truncated at experience {} to stay within {} char limit", i, maxChars);
                break;
            }

            sb.append(expBlock);
        }

        sb.append("---\n\n");
        sb.append("Current task:\n");

        String currentTaskBlock = currentTask;
        int remaining = maxChars - sb.length();
        if (remaining < 0) {
            currentTaskBlock = "";
            log.debug("No space for current task in ICL prompt (limit: {})", maxChars);
        } else if (currentTaskBlock != null && currentTaskBlock.length() > remaining) {
            currentTaskBlock = currentTaskBlock.substring(0, Math.max(0, remaining - 3)) + "...";
            log.debug("Current task truncated to fit within {} char limit", maxChars);
        }

        return sb.toString();
    }
```

---

## 4. 观察写入时的 LLM 提示（与「读出」对照）

`AgentService.callLlmAndSaveObservation` 使用 `TemplateService.escapeTemplateValue` / `truncate` 填充模板，再 `llmService.chatCompletionWithUsage` —— 这是 **写入侧** 防护与 **读出侧** `renderTimeline` **独立**，改一端需考虑另一端是否要对齐（例如统一脱敏规则）。

```380:396:backend/src/main/java/com/ablueforce/cortexce/service/AgentService.java
        String systemPrompt = templateService.getInitPromptTemplate()
            .replace("{{userPrompt}}", templateService.escapeTemplateValue(userPrompt))
            .replace("{{date}}", now);

        String userMsg = templateService.getObservationPromptTemplate()
            .replace("{{toolName}}", templateService.escapeTemplateValue(toolName))
            .replace("{{occurredAt}}", now)
            .replace("{{cwd}}", templateService.escapeTemplateValue(cwd))
            .replace("{{toolInput}}", templateService.escapeTemplateValue(
                templateService.truncate(toolInput, Constants.MAX_TOOL_CONTENT_LENGTH)))
            .replace("{{toolOutput}}", templateService.escapeTemplateValue(
                templateService.truncate(toolResponse, Constants.MAX_TOOL_CONTENT_LENGTH)));

        LlmService.LlmResponse llmResponse = llmService.chatCompletionWithUsage(systemPrompt, userMsg);
```

---

## 5. 相关文档

- HTTP 表：[`12-bluecortex-api-memory-surface.md`](./12-bluecortex-api-memory-surface.md)  
- **Bun Worker vs Java**（本文仅覆盖 Java；Hook 侧 Chroma 见）：[`15-runtime-integration-surfaces.md`](./15-runtime-integration-surfaces.md)  
- **摄入 / 写入**（瘦代理 → `AgentService`）：[`16-ingestion-write-path-sketch.md`](./16-ingestion-write-path-sketch.md)  
- **会话 start**（`SessionController` + 缓存）：[`17-session-lifecycle-java-sketch.md`](./17-session-lifecycle-java-sketch.md)  
- 双路径产品含义：[`10-aspect-bluecortex-implementation-map.md`](./10-aspect-bluecortex-implementation-map.md) §3 · [`09-aspect-bluecortex-bridge.md`](./09-aspect-bluecortex-bridge.md) §3.3  
- 出口安全盘点：[`../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md`](../hermes-memory/20-recommendations/05-ce-context-security-gap-inventory.md)  
- 总导航：[`../memory-research-hub.md`](../memory-research-hub.md)
