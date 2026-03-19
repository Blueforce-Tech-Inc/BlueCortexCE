# MCP Server 传输协议分析与改进规划

> 创建时间：2026-03-19  
> 适用版本：Spring AI 1.1.2 / MCP Spec 2025-06-18

---

## 1. 背景

BlueCortexCE 的 Java 后端通过 Spring AI MCP Server 对外暴露记忆工具，供 Claude Code、Cursor 等 AI 客户端调用。传输协议的选择直接影响：

- 连接稳定性（是否会断开/超时）
- 部署复杂度（是否需要会话黏性）
- 协议合规性（是否符合最新 MCP 规范）

本文档记录当前实现现状、SSE 协议的已知问题，以及迁移到 Streamable HTTP 的规划。

---

## 2. 当前实现现状

### 2.1 技术栈

| 组件 | 版本 / 配置 |
|------|------------|
| Spring AI | 1.1.2 |
| MCP Starter | `spring-ai-starter-mcp-server-webmvc` |
| 传输协议 | **SSE**（`protocol: SSE`） |
| SSE 端点 | `GET /sse` |
| 消息端点 | `POST /mcp/message?sessionId=xxx` |
| 服务器类型 | SYNC |

### 2.2 配置（`application.yml`）

```yaml
spring:
  ai:
    mcp:
      server:
        name: claude-mem-mcp-server
        version: 0.1.0
        type: SYNC
        # SSE: Spring AI 1.1.2 WebMVC 可用。
        # STATELESS 在本版本未正确注册 /mcp 端点（返回 404）。
        protocol: SSE
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
        capabilities:
          tool: true
          resource: false
          prompt: false
          completion: false
```

### 2.3 已实现的 MCP Tools（5 个）

| Tool | 描述 | 工作流步骤 |
|------|------|-----------|
| `search` | 语义搜索，返回 observation ID 列表 | Step 1 |
| `timeline` | 获取锚点周围的时间线上下文 | Step 2 |
| `get_observations` | 批量获取完整 observation 详情 | Step 3 |
| `save_memory` | 手动保存记忆（type=discovery） | 独立 |
| `recent` | 获取最近会话摘要 | 独立 |

### 2.4 测试状态

| 测试套件 | 状态 | 最后运行 |
|---------|------|---------|
| MCP E2E 测试 | ✅ 16/16 通过 | 2026-03-19 |
| 回归测试 | ✅ 31 通过，1 跳过 | 2026-03-19 |
| WebUI 集成测试 | ✅ 11/11 通过 | 2026-03-19 |
| Thin Proxy 测试 | ✅ 18/18 通过 | 2026-03-19 |

### 2.5 MCP E2E 测试的 SSE 流程

当前测试脚本（`scripts/mcp-e2e-test.sh`）实现了完整的 SSE 握手流程：

```
1. GET /sse  →  建立 SSE 长连接
2. 从 event:endpoint 事件中提取 MESSAGE_ENDPOINT（含 sessionId）
3. POST {json-rpc} → MESSAGE_ENDPOINT
4. 从 SSE 流中读取 event:message 获取响应
```

---

## 3. SSE 协议的稳定性问题

### 3.1 协议层面的根本缺陷

SSE（Server-Sent Events）是 MCP 的**旧传输协议**（spec 版本 2024-11-05），已被 MCP 官方在 2025-03-26 规范中正式替换。

**核心问题**：SSE 使用 Transfer-Encoding，中间代理（Nginx、CDN、负载均衡器）可以合法地缓冲所有数据包直到流关闭，导致事件无法实时到达客户端。没有任何 HTTP 头可以强制禁止这种行为。

> 来源：[Server Sent Events are still not production ready after a decade](https://dev.to/miketalbot/server-sent-events-are-still-not-production-ready-after-a-decade-a-lesson-for-me-a-warning-for-you-2gie)

### 3.2 生产环境已知故障场景

| 场景 | 问题 | 严重程度 |
|------|------|---------|
| 反向代理（Nginx/Caddy） | 压缩模块延迟 flush，事件不实时 | 高 |
| 云平台（Azure App Service） | 后续请求 SSE 流失败 | 高 |
| CDN / 防火墙 | 60–100 秒空闲超时，长连接被断开 | 中 |
| 多实例部署 | `/sse` 和 `/mcp/message` 可能路由到不同实例，会话失效 | 高 |
| 压缩中间件 | 压缩帧未填满时不 flush，事件延迟 | 中 |
| 客户端断开 | 服务端不一定能感知（silent disconnect） | 中 |

> 实际案例：某开发者遭遇 20 分钟内 SSE 事件完全不到达客户端的生产事故，原因是中间网络设备缓冲。

### 3.3 Spring AI MCP SSE 的已知 Bug

| Issue | 描述 | 状态 |
|-------|------|------|
| [#3947](https://github.com/spring-projects/spring-ai/issues/3947) | 多实例部署下 SSE 连接失败（会话状态不同步） | 未解决（截至 2025-07 仍为 waiting-for-triage） |
| [#2486](https://github.com/spring-projects/spring-ai/issues/2486) | WebFlux + HTTP Client 并存时产生重复 SSE 连接 | 已修复 |
| [#2702](https://github.com/spring-projects/spring-ai/issues/2702) | SSE 错误处理问题 | 已修复 |
| 默认 10 秒超时 | 工具调用耗时较长时静默失败 | 需手动配置 |

### 3.4 当前部署环境的风险评估

| 维度 | 当前状态 | 风险 |
|------|---------|------|
| 部署方式 | 单机、localhost:37777 | **低**（无代理层、无多实例） |
| 客户端 | Claude Code / Cursor（本地） | **低**（直连，无中间代理） |
| 网络环境 | 本地开发环境 | **低** |
| 未来扩展 | 若部署到云或多实例 | **高**（需立即迁移） |

**结论**：当前单机本地部署下，SSE 基本够用，但不具备生产级稳定性。

---

## 4. MCP 协议演进：为什么要迁移到 Streamable HTTP

### 4.1 官方规范变化

| 规范版本 | 标准传输协议 |
|---------|------------|
| 2024-11-05 | HTTP+SSE（旧） |
| **2025-03-26** | **Streamable HTTP**（替代 SSE） |
| 2025-06-18 | Streamable HTTP + stdio（SSE 仅作向后兼容） |

MCP 官方在 2025-03-26 起，用 **Streamable HTTP** 取代 HTTP+SSE。最新规范（2025-06-18）的标准传输协议只有两种：**Streamable HTTP** 和 **stdio**，SSE 退居向后兼容层。

### 4.2 Streamable HTTP 的优势

| 特性 | SSE（旧） | Streamable HTTP（新） |
|------|----------|----------------------|
| 连接模型 | 长连接（必须维持） | 无状态 HTTP，按需 SSE |
| 代理兼容性 | 差（易被缓冲） | 好（标准 HTTP POST/GET） |
| 负载均衡 | 需要会话黏性 | 无状态，天然支持水平扩展 |
| 断线恢复 | 无（需重新握手） | 可通过 SSE 事件 ID 实现断线恢复（规范推荐，非强制 header 名称） |
| 多实例部署 | 高风险 | 原生支持 |
| MCP 规范合规 | 已废弃 | 当前标准 |

### 4.3 Streamable HTTP 工作原理

```
客户端                          服务端
  │                               │
  │  POST /mcp  (InitializeReq)   │
  │──────────────────────────────>│
  │  200 OK + Mcp-Session-Id      │
  │<──────────────────────────────│
  │                               │
  │  POST /mcp  (工具调用)         │
  │──────────────────────────────>│
  │  200 application/json         │  ← 简单响应
  │  或 200 text/event-stream     │  ← 流式响应（按需 SSE）
  │<──────────────────────────────│
  │                               │
  │  DELETE /mcp (终止会话，可选)   │
  │──────────────────────────────>│
```

- **单一端点**：`/mcp`（同时支持 POST 和 GET）
- **无状态优先**：简单工具调用直接返回 JSON，不需要 SSE
- **按需 SSE**：需要流式响应时，服务端才升级为 SSE 流

> **注**：`DELETE /mcp` 是 MCP 规范中客户端主动终止会话的**可选**机制（规范原文：clients "SHOULD send" DELETE，服务端可以返回 405 拒绝）。服务端不实现 DELETE 也符合规范。

---

## 5. 为什么之前 STATELESS 返回 404

### 5.1 问题复现

在本次开发过程中，尝试将协议切换为 `protocol: STATELESS`，结果 `/mcp` 端点始终返回 404。

### 5.2 根因分析

经过调研，Spring AI 1.1.x 的 `McpServerProperties.ServerProtocol` 枚举共有三个有效值（已从官方 Javadoc 确认）：

| 协议 | `protocol` 值 | 说明 |
|------|--------------|------|
| 旧 SSE 协议 | `SSE` | 双端点：`/sse` + `/mcp/message` |
| 新 Streamable HTTP（有状态） | `STREAMABLE` | 单端点 `/mcp`，支持持久连接 |
| 新 Streamable HTTP（无状态） | `STATELESS` | 单端点 `/mcp`，不支持向客户端发消息 |

**根因（推测，尚未通过 debug 日志或官方 sample 完全确认）**：`STATELESS` 是一个合法的枚举值，配置名称本身没有错误。404 的原因可能是以下之一：
1. Spring AI 1.1.2 中 `STATELESS` 模式的 AutoConfiguration 在 WebMVC 下存在 Bug，导致 `/mcp` 端点未被正确注册（Spring AI PR #4179 正是针对 Streamable HTTP AutoConfiguration 的重构）
2. 本地配置存在遗漏（如缺少必要的依赖或属性）

建议用官方 sample 项目或开启 debug 日志（`logging.level.org.springframework.web.servlet.mvc.method.annotation: DEBUG`）再次验证，以确认根因。

> 注：`STATELESS` 不支持向 MCP 客户端发送消息请求（如 elicitation、sampling、ping）。`STREAMABLE` 支持完整能力。

### 5.3 STATELESS vs STREAMABLE：选型建议（针对工具型 MCP Server）

**官方定位**：`STATELESS` 是 Spring AI 正式支持的一等公民，与 `SSE`、`STREAMABLE` 并列于 MCP 官方文档中，适用于无会话、高吞吐场景。但功能上有**设计上的限制**，而非实现 Bug。

**成熟度与生态**：

| 维度 | STATELESS | STREAMABLE |
|------|-----------|------------|
| 官方支持 | ✅ 正式 | ✅ 正式 |
| 会话能力 | ❌ 无，不支持 session state | ✅ 支持 `Mcp-Session-Id`、持久连接 |
| `McpSyncServerExchange` / `McpSyncRequestContext` | ❌ 不可用，含此参数的工具会**静默不注册**（#5373，2026-01） | ✅ 可用 |
| `@McpTool` 自动注册 | 1.1.0-M1 曾出现不生效（#4372，已由 PR #4396 修复） | 成熟 |
| 社区实践 | 较少，多为极简/高并发场景 | **主流**，工具链式调用、资源变更通知等均依赖会话 |
| 适用场景 | 每次调用完全自包含、无跨请求上下文 | 记忆、多步 workflow、需会话上下文 |

**对本项目（BlueCortexCE）的建议**：

- **不建议以 `STATELESS` 作为主协议**：记忆工具、多步 retrieval workflow（search → timeline → get_observations）后续可能涉及跨请求上下文、主动 elicitation 等，无会话约束会限制扩展。
- **优先路线**：以 `STREAMABLE` 为目标协议（即 6.2 Step 1 配置），充分利用 MCP 的会话能力与 Spring AI 对 `STREAMABLE` 的完整支持。
- **STATELESS 仅作为可选优化**：仅在非常确定“每次调用完全自包含、无任何会话依赖”且追求极简/高并发时考虑，并预期可能遇到框架层小坑（如含 `McpSyncServerExchange` 的工具在 stateless 下被静默跳过）。

> 一句话：`STATELESS` 是**正式支持但相对年轻、使用面较窄**的模式；“能上生产，但要自己扛更多坑”。对本项目更稳妥的是 `STREAMABLE`。

### 5.4 STREAMABLE 验证结果 ✅ (2026-03-19 实测)

**验证结论**：`protocol: STREAMABLE` 在 Spring AI 1.1.2 下**完全可用**！

**验证步骤**:
1. 配置 `protocol: STREAMABLE` + `streamable-http.mcp-endpoint: /mcp`
2. 重启服务
3. 发送 initialize 请求

**验证命令**:
```bash
curl -X POST http://127.0.0.1:37777/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream,application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

**验证结果**:
| 测试项 | 结果 |
|--------|------|
| `/mcp` 端点注册 | ✅ 200 OK |
| initialize 请求 | ✅ 返回 `Mcp-Session-Id` header |
| tools/list | ✅ 返回 5 个工具 |
| tools/call (search) | ✅ 成功返回结果 |

**注意事项**:
- Accept 头必须是 `text/event-stream,application/json`（不能用纯 `application/json`）
- STREAMABLE 是有状态协议，需要管理 Session ID
- 后续请求需要携带 `Mcp-Session-Id` header

### 5.5 STREAMABLE 协议客户端要求与健壮性

**重要说明**：以下要求来自 MCP 官方规范 (2025-03-26)，服务端实现是正确的，客户端必须遵守。

#### 协议层面的正确行为

根据 MCP 规范，STREAMABLE HTTP 传输要求：

1. **Accept 头**：客户端必须发送 `Accept: text/event-stream,application/json`
2. **Session ID 管理**：
   - `initialize` 请求返回 `Mcp-Session-Id` header
   - **所有后续请求必须携带 `Mcp-Session-Id` header**
   - 如果缺少 session ID，服务端正确返回错误

#### 错误场景处理

| 场景 | 服务端行为 | 说明 |
|------|-----------|------|
| 缺少 Accept 头 | 返回错误或 JSON 响应 | Accept 头错误导致 SSE 解析失败 |
| 缺少 `Mcp-Session-Id` | 返回 `"Session ID missing"` | 这是**正确的协议行为**，不是服务端 Bug |
| Session 过期/丢失 | 返回 session not found | 客户端需要重新初始化 |

#### 为什么服务端这样处理是正确的

MCP 规范明确要求：
> "clients using the Streamable HTTP transport **MUST include** [Mcp-Session-Id] in the Mcp-Session-Id header on all of their subsequent HTTP requests"

如果客户端不遵守协议：
- ❌ 这是**客户端 Bug**
- ❌ 服务端正确地拒绝了无效请求
- ✅ 我们的服务端实现符合规范

#### 已知兼容性问题

| 客户端 | Session 管理 | 说明 |
|--------|-------------|------|
| Claude Code (`claude mcp add --transport http`) | ✅ 自动处理 | 正确发送 Accept 头和 Session ID |
| Cursor IDE | ✅ 自动处理 | MCP 客户端自动处理会话 |
| 第三方 MCP 客户端 | ⚠️ 需验证 | 必须遵守 MCP 规范 |

#### 服务端健壮性措施

虽然错误处理是协议正确的，但我们可以：

1. **日志增强**：记录 session 相关错误便于调试
2. **监控指标**：跟踪 session 错误率
3. **文档完善**：清晰说明客户端要求

**结论**：服务端实现符合 MCP 协议规范。"Session ID missing" 错误表明客户端未正确实现协议，这是客户端问题，不是服务端 Bug。

---

## 6. 迁移规划：SSE → Streamable HTTP

### 6.1 迁移目标

| 目标 | 说明 |
|------|------|
| 协议合规 | 符合 MCP spec 2025-03-26+ |
| 稳定性提升 | 消除 SSE 长连接依赖 |
| 扩展性 | 支持未来多实例/云部署 |
| 向后兼容 | 不破坏现有 Claude Code 集成 |

### 6.2 迁移步骤

#### Step 1：验证 `STREAMABLE` 模式（低风险）

> **选型说明**：本步骤明确选用 `STREAMABLE`（有状态），而非 `STATELESS`。理由见 5.3 节：本项目为记忆工具型 MCP Server，需多步 workflow 与会话上下文；`STATELESS` 成熟度与扩展性不及 `STREAMABLE`。

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        type: SYNC
        streamable-http:
          mcp-endpoint: /mcp
          keep-alive-interval: 30s
```

预期：`/mcp` 端点正确注册，支持 POST 和 GET。

#### Step 2：更新测试脚本

`scripts/mcp-e2e-test.sh` 需要更新为 Streamable HTTP 流程：

```bash
# 旧流程（SSE）：
# 1. GET /sse → 获取 sessionId
# 2. POST /mcp/message?sessionId=xxx → 发送请求
# 3. 从 SSE 流读取响应

# 新流程（Streamable HTTP）：
# 1. POST /mcp → InitializeRequest → 获取 Mcp-Session-Id
# 2. POST /mcp + Mcp-Session-Id header → 工具调用
# 3. 直接从 HTTP 响应读取结果（JSON 或 SSE）
```

#### Step 3：验证客户端兼容性

| 客户端 | 支持 Streamable HTTP | 备注 |
|--------|---------------------|------|
| Claude Code（claude CLI） | 需验证（见注） | `claude mcp add --transport http <url>` 语法已存在 |
| Cursor IDE | 需验证 | 查看 Cursor MCP 文档 |
| Spring AI MCP Client | ✅ 支持 | `spring-ai-starter-mcp-client` |

> **Claude Code 说明**：`--transport http` 选项已在较新版本中出现（issue #1387 中有用户报告可用），但官方文档尚未完整说明；且存在已知 Bug（[#5960](https://github.com/anthropics/claude-code/issues/5960)）：长时间流式工具调用只显示第一个 chunk，后续输出不实时（该 Bug 在 2026-01 被标记为 resolved，但建议实际测试验证）。迁移前应以实测为准，不应仅凭 issue 讨论判断支持状态。

#### Step 4：向后兼容处理

MCP 规范提供了向后兼容方案：

> 服务端可以同时保留旧 SSE 端点（`/sse` + `/mcp/message`）和新 Streamable HTTP 端点（`/mcp`），供不同版本客户端使用。

但用户明确要求**单一协议**，不做双协议方案。迁移后直接切换，不保留 SSE 端点。

#### Step 5：生产部署加固

迁移到 Streamable HTTP 后，可进一步配置：

```yaml
streamable-http:
  mcp-endpoint: /mcp
  keep-alive-interval: 30s   # 定期 ping 客户端，检测连接健康
  disallow-delete: false      # 允许客户端主动终止会话
```

### 6.3 迁移风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 客户端不支持 Streamable HTTP | 中 | 高 | 先验证客户端版本，再切换 |
| Spring AI 1.1.2 STREAMABLE 有 Bug | 低 | 中 | 先在开发环境验证 |
| 测试脚本需要重写 | 确定 | 低 | 已有 SSE 版本作参考 |

### 6.4 优先级与时间线

| 阶段 | 任务 | 优先级 | 预估工作量 |
|------|------|--------|-----------|
| P0 | 验证 `protocol: STREAMABLE` 能否正常注册 `/mcp` | 高 | 1 小时 |
| P1 | 更新 `mcp-e2e-test.sh` 支持 Streamable HTTP 流程 | 高 | 2 小时 |
| P2 | 验证 Claude Code / Cursor 客户端兼容性 | 高 | 1 小时 |
| P3 | 切换生产配置，运行全量回归测试 | 高 | 1 小时 |
| P4 | 更新 `application.yml.example` 和文档 | 低 | 30 分钟 |

---

## 7. 现阶段建议

### 7.1 短期（当前）

**维持 SSE**，原因：

1. 当前是单机本地部署，无代理层，SSE 稳定性风险低
2. `STATELESS` 之前验证失败，`STREAMABLE` 尚未验证
3. 所有测试（16/16 MCP E2E）已通过，不应在未验证的情况下引入变更

**需要关注的风险**：

- 若 Claude Code 或 Cursor 升级后默认使用 Streamable HTTP 客户端，可能无法连接 SSE 服务端
- 若部署到云环境（有 Nginx/CDN），SSE 稳定性会显著下降

### 7.2 中期（下一个迭代）

**迁移到 Streamable HTTP**，步骤见第 6.2 节。

关键验证点：

```bash
# 验证 STREAMABLE 模式
curl -X POST http://localhost:37777/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

预期：返回 `InitializeResult` 而非 404。

### 7.3 长期（生产部署）

- 启用 `keep-alive-interval: 30s` 防止空闲连接被中间设备断开
- 若多实例部署，Streamable HTTP 天然支持，无需会话黏性
- 关注 MCP 路线图：官方方向是进一步简化会话管理、弱化会话依赖，使实现趋向无状态化（具体细节仍在演进中，非已确定的"完全无状态协议"）

---

## 8. 参考资料

| 资料 | 链接 |
|------|------|
| MCP 官方传输协议规范（2025-06-18） | https://modelcontextprotocol.io/specification/2025-06-18/basic/transports |
| MCP 2026 路线图 | https://blog.modelcontextprotocol.io/posts/2026-mcp-roadmap/ |
| Spring AI Streamable HTTP 文档 | https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-streamable-http-server-boot-starter-docs.html |
| Spring AI STATELESS 文档 | https://docs.spring.io/spring-ai/reference/1.1/api/mcp/mcp-stateless-server-boot-starter-docs.html |
| SSE 稳定性问题（DEV Community） | https://dev.to/miketalbot/server-sent-events-are-still-not-production-ready-after-a-decade-a-lesson-for-me-a-warning-for-you-2gie |
| Spring AI #3947（多实例 SSE 失败） | https://github.com/spring-projects/spring-ai/issues/3947 |
| MCP RFC: SSE → Streamable HTTP | https://github.com/modelcontextprotocol/specification/pull/206 |
| SSE vs Streamable HTTP 对比 | https://brightdata.com/blog/ai/sse-vs-streamable-http |
| Spring AI McpServerProperties.ServerProtocol Javadoc | https://docs.spring.io/spring-ai/docs/1.1.x/api/org/springframework/ai/mcp/server/common/autoconfigure/properties/McpServerProperties.ServerProtocol.html |
| Spring AI PR #4179（重构 MCP AutoConfiguration） | https://github.com/spring-projects/spring-ai/pull/4179 |
| Claude Code #1387（Streamable HTTP 支持） | https://github.com/anthropics/claude-code/issues/1387 |
| Claude Code #5960（Streamable HTTP 流式 Bug） | https://github.com/anthropics/claude-code/issues/5960 |
| Spring AI #5373（STATELESS 下 McpSyncServerExchange 静默不注册） | https://github.com/spring-projects/spring-ai/issues/5373 |
| Spring AI #4372（STATELESS 下 @McpTool 自动注册失效，1.1.0-M1，已由 #4396 修复） | https://github.com/spring-projects/spring-ai/issues/4372 |
| Spring AI PR #4396（修复 STATELESS 工具注册） | https://github.com/spring-projects/spring-ai/pull/4396 |
| Spring AI MCP Overview（SSE/STREAMABLE/STATELESS 并列） | https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html |
