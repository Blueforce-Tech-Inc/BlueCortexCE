# OpenClaw 插件未来改进计划

> 基于 TS 版本调查报告 `/Users/yangjiefeng/Documents/claude-mem/docs/drafts/openclaw-memory-integration-investigation.md`
>
> 生成日期：2026-04-02
>
> 当前状态：已完成 `before_prompt_build` 钩子和 MEMORY.md 文件同步移除

---

## 已完成改进 ✅

| 改进项 | 状态 | 提交 |
|--------|------|------|
| 实现 `before_prompt_build` 钩子 | ✅ 已完成 | `2a84a9d` |
| 移除 MEMORY.md 文件同步 | ✅ 已完成 | `2a84a9d` |
| 新增 `appendSystemContext` 上下文注入 | ✅ 已完成 | `2a84a9d` |

---

## 待实现改进项

### 1. 60 秒 TTL 专用缓存端点（高优先级）

**问题**：当前 Java 后端的 `ContextCacheService` 使用 5 分钟 TTL，与 TS 版本的 60 秒 TTL 不一致。

**建议方案**：新增 `/api/context/inject-openclaw` 端点

| 维度 | 说明 |
|------|------|
| 端点 | `/api/context/inject-openclaw?projects=xxx` |
| TTL | 60 秒（与 TS 版本一致） |
| 影响范围 | 仅影响 OpenClaw 插件调用，不影响其他调用方 |
| 实现位置 | `ContextController.java` |

**实现代码示例**：

```java
// ContextController.java 新增端点
@GetMapping(value = "/inject-openclaw", produces = MediaType.APPLICATION_JSON_VALUE)
public Map<String, Object> injectContextOpenClaw(
        @RequestParam(required = false, defaultValue = "") String projects) {
    // 使用 60 秒 TTL 的缓存
    return contextService.getContextForOpenClaw(projects);
}
```

**插件端改动**：

```typescript
// 修改 before_prompt_build 中的 API 调用
async function getContextForPrompt(ctx?: EventContext): Promise<string | null> {
  const projectPath = ctx?.workspaceDir || projectName;

  // 调用新的 OpenClaw 专用端点
  const contextText = await workerGetText(
    workerPort,
    `/api/context/inject-openclaw?projects=${encodeURIComponent(projectPath)}`,
    api.logger
  );
  // ...
}
```

**优点**：
1. 不影响现有 Claude Code hooks 的缓存行为
2. 与 TS 版本语义对齐
3. 实现简单，只需新增端点

---

### 2. SSE 观察推送（中等优先级）

**TS 版本能力**：TS 版本通过 SSE 连接到 `/stream` 端点实现观察实时推送。

**当前状态**：Java 后端已实现 SSE 支持（`StreamController.java`），但 OpenClaw 插件未使用。

**实现方式**：
```typescript
// OpenClaw 插件中添加 SSE 连接
// 与后端 /stream 端点建立 SSE 连接
// 接收实时观察推送
```

**复杂度**：较高，需要维护长连接。

---

### 3. `message_received` 事件支持（低优先级）

**TS 版本能力**：TS 版本监听 `message_received` 事件来捕获来自渠道的入站用户提示并初始化会话。

**当前状态**：我们未实现此事件监听。

**事件表**：

| 事件 | TS 版本处理逻辑 | 我们的实现 |
|------|----------------|------------|
| `message_received` | 捕获入站用户提示并初始化会话 | ❌ 未实现 |

---

### 4. `syncMemoryFileExclude` 配置支持（可选）

**TS 版本能力**：TS 版本支持 `syncMemoryFileExclude` 配置来排除某些项目。

**我们的状态**：我们已移除整个 MEMORY.md 同步机制。

**是否需要**：如果未来需要部分兼容 TS 版本行为，可考虑添加此配置。

---

## 实施建议时间表

| 阶段 | 时间 | 改进项 |
|------|------|--------|
| **已完成** | - | `before_prompt_build` 钩子、MEMORY.md 同步移除 |
| **短期** | 1-2 小时 | 60 秒 TTL 专用缓存端点 |
| **中期** | 半天 | SSE 观察推送 |
| **长期** | 待定 | `message_received` 事件、排除配置支持 |

---

## 参考文档

- [TS 版本调查文档](../../claude-mem/docs/drafts/openclaw-memory-integration-investigation.md)
- [OpenClaw 集成文档](../../openclaw-plugin/OPENCLAW-INTEGRATION.md)
