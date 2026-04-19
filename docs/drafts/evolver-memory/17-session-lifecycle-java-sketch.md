# BlueCortexCE：会话生命周期速写（Java — start 与 end）

> **角色**：补齐 **`16`** 未覆盖的 **Java** **session-start** 主路径；与 **ingest** 中的 **session-end** 形成「一头一尾」对照。  
> **非本文范围**：Claude Code **`session-init` Hook** 使用 **Bun Worker** **`POST /api/sessions/init`**（`SessionRoutes`），**不是** 下文 `POST /api/session/start`。对照见 [`12-bluecortex-api-memory-surface.md`](./12-bluecortex-api-memory-surface.md) §2–§3。  
> **配对阅读**：[`16-ingestion-write-path-sketch.md`](./16-ingestion-write-path-sketch.md)（tool-use / user-prompt / session-end）  
> **最后更新**：2026-04-19

---

## 1. Session start（wrapper.js → Java）

**路由**：`POST /api/session/start`（`SessionController`，`@RequestMapping("/api/session")`）。

**主干**（概念序）：

1. `sessionManagementService.initializeSession(contentSessionId, projectPath, …)` — 建或复用会话。  
2. **上下文**：先 `contextCacheService.getContextIfFresh(projectPath)`；未命中则 **`contextService.generateContext`**（单项目）或 **`generateContextMultiProject`**（worktree 多 `projects`），再写入缓存。  
3. 响应含 **`context`**、`update_files`、`session_db_id`、`prompt_number` 等（见类注释）。

代码锚点：

```166:197:backend/src/main/java/com/ablueforce/cortexce/controller/SessionController.java
        // 2. Generate context from observations (try cache first)
        String context = "";
        if (projectPath != null && !projectPath.isEmpty()) {
            context = contextCacheService.getContextIfFresh(projectPath);

            if (context == null) {
                if (projectsParam != null && !projectsParam.isBlank() && projectsParam.contains(",")) {
                    List<String> projects = parseProjectsParam(projectsParam);
                    context = contextService.generateContextMultiProject(
                        projects,
                        new ContextService.ContextConfig()
                    );
                    log.debug("Generated multi-project context for {} projects", projects.size());
                } else {
                    context = contextService.generateContext(
                        projectPath,
                        new ContextService.ContextConfig(),
                        contentSessionId
                    );
                    log.debug("Generated fresh context for project {} (cache miss)", projectPath);
                }

                cacheContextForProject(projectPath, context);
            } else {
                log.debug("Returned cached context for project {}", projectPath);
            }
        }
```

**与读出链关系**：此处生成的 `context` 即 **时间线类** `generateContext` 产物，细节见 [`14`](./14-context-output-pipeline-sketch.md)。

---

## 2. Session end（ingest）

**路由**：`POST /api/ingest/session-end` → `SummaryGenerationService.completeSessionAsync` — 见 [`16`](./16-ingestion-write-path-sketch.md) §4。

---

## 3. 相关文档

- HTTP 总表：[`12`](./12-bluecortex-api-memory-surface.md)  
- 运行时（Worker vs Java）：[`15`](./15-runtime-integration-surfaces.md)  
- 总导航：[`../memory-research-hub.md`](../memory-research-hub.md)
