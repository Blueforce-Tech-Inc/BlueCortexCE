# OpenClaw 插件未来改进计划

> 生成日期：2026-04-02
>
> 当前状态：已完成 `before_prompt_build` 钩子和 MEMORY.md 文件同步移除

---

## 背景

本项目（Java 版本的 Claude-Mem）的 OpenClaw 插件通过事件钩子与 OpenClaw Gateway 集成。当前架构如下：

```
OpenClaw Gateway
    │
    ├── session_start ──→ 初始化会话
    ├── before_agent_start ──→ 跟踪工作区
    ├── before_prompt_build ──→ 注入上下文 via appendSystemContext  ✅ 已实现
    ├── tool_result_persist ──→ 记录工具观察
    ├── agent_end ──→ 生成摘要
    ├── session_end ──→ 清理会话
    └── gateway_start ──→ 重置跟踪
                │
                ▼
    Claude-Mem Java Backend (localhost:37777)
```

---

## 已完成改进 ✅

| 改进项 | 状态 | 提交 |
|--------|------|------|
| 实现 `before_prompt_build` 钩子 | ✅ 已完成 | `2a84a9d` |
| 移除 MEMORY.md 文件同步 | ✅ 已完成 | `2a84a9d` |
| 新增 `appendSystemContext` 上下文注入 | ✅ 已完成 | `2a84a9d` |

---

## 上下文注入机制说明

当前实现通过 `before_prompt_build` 钩子，在每次 LLM 调用前向 OpenClaw 返回上下文：

```typescript
api.on("before_prompt_build", async (_event, ctx) => {
  const contextText = await workerGetText(
    workerPort,
    `/api/context/inject?projects=${encodeURIComponent(projectPath)}`,
    api.logger
  );

  if (contextText && contextText.trim()) {
    return { appendSystemContext: contextText };
  }
});
```

OpenClaw Gateway 会将 `appendSystemContext` 的内容附加到系统提示词末尾。

---

## 待实现改进项

### 1. 60 秒 TTL 专用缓存端点（高优先级）

**问题**：当前后端 `ContextCacheService` 使用 5 分钟 TTL，但 OpenClaw 插件每次 LLM 调用都会请求上下文，高频调用可能导致后端压力。

**建议方案**：新增 `/api/context/inject-openclaw` 端点

| 维度 | 说明 |
|------|------|
| 端点 | `/api/context/inject-openclaw?projects=xxx` |
| TTL | 60 秒 |
| 影响范围 | 仅影响 OpenClaw 插件调用，不影响其他调用方 |
| 实现位置 | `ContextController.java` |

**后端实现示例**：

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
const contextText = await workerGetText(
  workerPort,
  `/api/context/inject-openclaw?projects=${encodeURIComponent(projectPath)}`,
  api.logger
);
```

**优点**：
1. 减少高频调用对后端的压力
2. 与 OpenClaw 插件的调用频率匹配
3. 不影响其他调用方（如 Claude Code hooks）

---

### 2. SSE 观察推送（中等优先级）

**背景**：后端已实现 SSE 支持（`StreamController.java`），可通过 `/stream` 端点推送观察数据。

**当前状态**：OpenClaw 插件仅通过 HTTP 请求获取上下文，未建立 SSE 长连接。

**实现方式**：
```typescript
// 建立 SSE 连接接收实时观察
const eventSource = new EventSource(`${workerBaseUrl}/stream?session=${sessionId}`);
eventSource.onmessage = (event) => {
  // 处理实时观察推送
};
```

**复杂度**：较高，需要维护长连接和重连逻辑。

---

### 3. `message_received` 事件支持（低优先级）

**背景**：OpenClaw 插件目前未监听 `message_received` 事件。

**潜在价值**：该事件可用于捕获来自渠道的入站用户提示，可在用户发送消息时初始化会话而非等到 Agent 启动。

**事件表**：

| 事件 | 触发时机 | 潜在用途 |
|------|----------|----------|
| `message_received` | 收到用户消息时 | 尽早初始化会话 |

---

## 实施建议时间表

| 阶段 | 改进项 | 复杂度 |
|------|--------|--------|
| **已完成** | `before_prompt_build` 钩子、MEMORY.md 同步移除 | - |
| **短期** | 60 秒 TTL 缓存端点 | 低 |
| **中期** | SSE 观察推送 | 高 |
| **长期** | `message_received` 事件 | 中 |

---

## 相关文档

- [OpenClaw 集成文档](../../openclaw-plugin/OPENCLAW-INTEGRATION.md)
- [OpenClaw 集成文档（中文）](../../openclaw-plugin/OPENCLAW-INTEGRATION-zh-CN.md)
