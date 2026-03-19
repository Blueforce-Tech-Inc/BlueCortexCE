# MCP Server 实现现状与改进规划

> 创建时间: 2026-03-19
> 状态: Draft
> 适用范围: `backend/` 中基于 Spring Boot + Spring AI 的远程 MCP Server

## 1. 文档目的

本文档用于系统化总结当前 `CortexCE` 中 MCP Server 的实现现状、已验证能力、已知限制、技术判断，以及下一步改进规划。

这份文档重点回答四个问题：

1. 当前 MCP Server 到底实现到了什么程度。
2. 当前为什么采用 SSE，而不是直接切到 MCP 标准的 Streamable HTTP。
3. 当前方案在什么场景下可以接受，在什么场景下有明显风险。
4. 下一步应如何演进，才能逐步走向更标准、更稳健的实现。

## 2. 结论摘要

### 当前状态

当前后端已经具备一个**可工作的远程 MCP Server**，基于：

- `Spring Boot 3.3.13`
- `Spring AI 1.1.2`
- `spring-ai-starter-mcp-server-webmvc`

当前 MCP Server：

- 已接入 5 个 MCP tools
- 已通过端到端测试验证工具链路
- 当前采用 **SSE 旧式 HTTP+SSE 传输**
- 当前配置为**单协议**，不同时暴露 SSE 与 Streamable HTTP 两套入口

### 当前技术判断

1. **SSE 在本项目当前部署模型下可用，但不应被表述为“长期理想方案”。**
2. **MCP 官方规范已经转向 Streamable HTTP，SSE 仅适合作为兼容层或过渡方案。**
3. **在 Spring AI 1.1.2 + WebMVC 这一组合下，`protocol: STATELESS` 的实测结果是 `/mcp` 未正确注册，可观察到 404，因此当前无法落地标准单端点 Streamable HTTP。** 根因推测为该版本 AutoConfiguration 存在 Bug（PR #4179 已重构修复）；`STATELESS` 本身是合法枚举值。
4. **因此，当前“继续使用 SSE”不是因为 SSE 更优，而是因为它是当前版本栈里唯一已验证可工作的方案。**

### 推荐结论

- **短期**: 保持 SSE 单协议实现，继续作为本地/单机场景的可用方案。
- **中期**: 跟踪 Spring AI 对 Streamable HTTP 的成熟支持，并准备迁移验证（**下一步目标协议：`STREAMABLE`**）。
- **长期**: 迁移到更符合 MCP 新规范的单端点 HTTP 传输，并补齐会话、重试、恢复、部署拓扑和安全策略。

## 3. 当前实现快照

## 3.1 技术栈

当前后端依赖显示：

- Spring Boot: `3.3.13`
- Spring AI: `1.1.2`
- MCP Server Starter: `spring-ai-starter-mcp-server-webmvc`

相关依赖位于 `backend/pom.xml`。

## 3.2 服务监听与网络边界

服务当前默认监听：

- `server.port: 37777`
- `server.address: 127.0.0.1`

这意味着默认情况下服务绑定在本机回环地址，而不是直接暴露到 `0.0.0.0`。这对本地开发和本机调用是有利的，也符合 MCP 规范对于本地服务“尽量仅绑定 localhost”的建议。

## 3.3 MCP 配置现状

当前 `application.yml` 中 MCP 配置为：

```yaml
spring:
  ai:
    mcp:
      server:
        name: claude-mem-mcp-server
        version: 0.1.0
        type: SYNC
        protocol: SSE
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
        capabilities:
          tool: true
          resource: false
          prompt: false
          completion: false
```

这里反映了几个关键事实：

- 当前实现是**工具型 MCP Server**
- 当前未开放 MCP `resource`、`prompt`、`completion`
- 当前消息交互采用旧式 **SSE + message endpoint** 双端点模型

## 3.4 当前工具面

当前代码中实际注册的 MCP 工具位于 `backend/src/main/java/com/ablueforce/cortexce/mcp/ClaudeMemMcpTools.java`，共 5 个：

| Tool | 作用 | 说明 |
|------|------|------|
| `search` | 记忆搜索 | 支持语义搜索，必要时回退文本搜索 |
| `timeline` | 时间线上下文 | 基于 anchor 或 query 获取上下文 |
| `get_observations` | 拉取完整 observation | 按 ID 批量获取详情 |
| `save_memory` | 手动保存记忆 | 创建 manual memory 并写入 embedding |
| `recent` | 最近会话摘要 | 获取项目最近若干次 summary |

这 5 个工具已经构成了一套完整的“先索引、再定位、再拉全文”的 MCP 工作流。

## 3.5 当前传输链路

当前远程 MCP 交互采用 Spring AI 的旧式 SSE 工作流：

1. 客户端 `GET /sse`
2. 服务端通过 `event:endpoint` 返回消息提交地址
3. 客户端向 `POST /mcp/message?sessionId=...` 发送 JSON-RPC
4. 服务端通过已建立的 SSE 通道回推响应

这与 MCP 新版规范中的 **单一 MCP endpoint** 设计并不相同。

## 4. 当前测试与验证现状

## 4.1 已通过的回归验证

在最近一次调整为 SSE 之后，已通过以下验证：

| 测试 | 结果 |
|------|------|
| `scripts/mcp-e2e-test.sh` | 16/16 通过 |
| `scripts/regression-test.sh` | 31 通过，1 跳过 |
| `scripts/webui-integration-test.sh` | 11/11 通过 |
| `scripts/thin-proxy-test.sh` | 18/18 通过 |

这说明：

- 当前 MCP 工具调用链路是可工作的
- 当前改动没有破坏既有 WebUI 与代理行为
- SSE 方案已经在当前仓库的自动化脚本层面获得验证

## 4.2 MCP E2E 脚本当前验证内容

`scripts/mcp-e2e-test.sh` 当前是按照 SSE 模型实现的，主要流程为：

1. 通过 `GET /sse` 建立连接
2. 解析 `event:endpoint`
3. 提取 `sessionId`
4. 使用 `/mcp/message` 发送 `initialize`
5. 再验证 `tools/list`
6. 逐个验证 `search`、`timeline`、`get_observations`、`save_memory`、`recent`

这意味着当前自动化测试已经与当前服务的 SSE 交互模型对齐。

## 5. 为什么当前没有采用 Streamable HTTP

## 5.1 规范层面：MCP 已经转向 Streamable HTTP

根据 MCP 官方规范：

- 2025-03-26 之后，**Streamable HTTP** 取代旧式 HTTP+SSE
- 2025-06-18 规范中，标准 HTTP 传输已经明确收敛为 **Streamable HTTP**
- SSE 不再是主推的标准远程传输模型

官方 RFC 引入 Streamable HTTP 的主要动机包括：

- HTTP+SSE 不支持 resumability
- 旧模型要求服务端维持高可用长连接
- 对基础设施和中间件不够友好
- 希望支持真正的 stateless server

这说明：**从协议演进方向看，未来应该迁移到 Streamable HTTP，而不是长期停留在旧式 SSE。**

## 5.2 工程层面：当前版本栈下 STATELESS 实测不可用

本项目已做过直接尝试：

- 在 Spring AI 1.1.2 下将协议改为 `STATELESS`
- 期望暴露标准单端点 `/mcp`
- 实际结果是 `/mcp` 未正确可用，返回 404

**根因说明**：`STATELESS` 是 Spring AI 中合法的枚举值（`McpServerProperties.ServerProtocol` 枚举包含 `SSE`、`STREAMABLE`、`STATELESS` 三个值，已从官方 Javadoc 确认）。404 的真实原因更可能是 **Spring AI 1.1.2 中 `STATELESS` 模式的 AutoConfiguration 在 WebMVC 下存在 Bug**，导致端点未被正确注册。Spring AI PR #4179 正是针对这一问题的重构，引入了对 Streamable HTTP 的正式支持。

因此，当前问题不是"我们理念上更喜欢 SSE"，而是：

- **当前依赖版本下，目标方案尚未在本项目成功跑通**
- **而 SSE 是当前唯一已打通并已回归验证的方案**
- **下一步迁移方向为 `STREAMABLE` 协议**（非 STATELESS；本项目的工具型 MCP Server 场景下 STREAMABLE 更稳妥，见 `mcp-server-transport-analysis.md` 5.3 节）。可先尝试 `protocol: STREAMABLE` 验证，或升级到 Spring AI 1.1.3+。

## 5.3 为什么没有采用“双协议并行”

本项目当前明确选择了**单协议**，而不是同时暴露：

- 一套旧式 SSE 入口
- 一套新式 Streamable HTTP 入口

这样做的原因是：

1. 双协议会增加配置、测试、文档和客户端兼容复杂度。
2. 双协议会掩盖问题，容易让“过渡态”长期存在。
3. 当前更重要的是先确定一个**明确、可测试、可维护**的工作路径。

因此，当前决策是：

- **只保留 SSE**
- **在 Streamable HTTP 真正可用时再切换**

## 6. 当前 SSE 方案的稳定性评估

## 6.1 先给结论

**SSE 不是“绝对不稳定”，但它也绝不是一个可以不加限定地称为“稳定”的方案。**

更准确的说法应该是：

- 在**单机、本地、简单网络**场景下，SSE 往往可以工作良好
- 在**负载均衡、多实例、代理/CDN、复杂网络**场景下，SSE 的稳定性和可预期性明显下降

## 6.2 当前项目里，SSE 为什么还能接受

当前项目的默认运行形态是：

- 本机启动
- 绑定 `127.0.0.1`
- 由本地工具或本地客户端访问
- 暂无多实例负载均衡链路

在这种前提下，SSE 的主要风险源被削弱了：

- 没有跨实例路由漂移
- 没有公网代理层缓冲
- 没有云负载均衡 idle timeout

所以，**在当前实际部署假设下，SSE 是“可接受的工程折中”**。

## 6.3 SSE 的主要风险

### 1. 双端点 + 会话状态

当前模型依赖：

- 先 `GET /sse`
- 再 `POST /mcp/message`

如果未来改成多实例部署，这两个请求可能落到不同实例。只要会话状态没有外部化或没有 sticky routing，就可能出现连接失败、消息丢失或会话失配。

这是 Spring AI 社区 issue 中已经明确暴露的问题。

### 2. 长连接依赖中间层行为

SSE 对下列中间层非常敏感：

- 反向代理
- CDN
- 网关
- 压缩器
- 连接空闲超时策略

常见问题包括：

- 连接被静默断开
- 数据被代理缓冲，不能及时 flush
- gzip/压缩影响事件实时性
- 客户端没有及时感知后端已断开

### 3. 恢复能力弱

旧式 HTTP+SSE 模型本身不具备新版规范所强调的完整 resumability 设计，客户端恢复和消息重投能力较弱。

### 4. 对浏览器/跨域场景不够完整

当前 `WebConfig` 中的 CORS 映射覆盖了：

- `/api/**`
- `/stream`

但没有看到针对 `MCP /sse` 与 `/mcp/message` 的专门配置。这在当前本机同源/非浏览器使用中未必是问题，但如果未来希望让浏览器端或跨域前端直接访问 MCP，这部分需要补齐。

### 5. 与新规范存在语义偏差

当前使用的是旧式 SSE 双端点模型，而非新版规范强调的：

- 单 MCP endpoint
- `POST` / `GET` 统一语义
- 可选 SSE 升级
- `Mcp-Session-Id`
- `Last-Event-ID`
- resumability / redelivery

这意味着当前实现虽然“可工作”，但**不是目标终局形态**。

## 7. 需要澄清的一个误区：Streamable HTTP 也不自动等于“彻底无状态”

这是后续设计里非常重要的一点。

很多讨论会把 Streamable HTTP 简化成：

- “换成新协议”
- “于是所有扩缩容问题就消失”

这并不准确。

根据 MCP 新规范：

- Streamable HTTP 允许服务端是 stateless
- 但也允许服务端建立 session
- 一旦引入会话状态，依然会涉及会话路由、共享状态、恢复策略

换句话说：

- **新传输协议提供了更好的上限**
- **但是否真正无状态，取决于服务端实现方式**

对本项目而言，如果未来希望做到真正的 stateless MCP server，需要同时满足：

1. 初始化阶段不依赖进程内 session 状态
2. 工具调用尽量做到请求内自闭合
3. 流式响应不依赖不可恢复的本地连接上下文
4. 必要状态通过 header、token、持久化或消息总线传递

否则，即使切到 Streamable HTTP，也仍然可能遇到多实例路由和会话一致性问题。

## 8. 当前实现的优点

虽然当前还不是理想终态，但现状并非“半成品不可用”，它有明显优点：

### 1. 工具层已经成型

当前 5 个工具已经能覆盖：

- 搜索
- 时间线定位
- 详情提取
- 手动记忆保存
- 最近摘要回看

这已经满足记忆系统的核心 MCP 使用场景。

### 2. 已与业务服务层打通

当前 MCP tools 不是孤立 demo，而是直接复用了现有服务层能力：

- `SearchService`
- `TimelineService`
- `EmbeddingService`
- `ObservationRepository`
- `SummaryRepository`
- `SessionRepository`

这说明 MCP 已经是现有记忆系统的一层正式入口，而不是独立旁路实验。

### 3. 已形成端到端测试闭环

当前已有脚本级验证，可以较稳定地回答：

- 服务是否起来
- 工具是否能列出来
- 工具调用是否成功
- 典型错误路径是否能被覆盖

### 4. 与本地部署模型匹配

当前服务默认监听 `127.0.0.1`，本地使用是合理的，这降低了 SSE 的不少现实风险。

## 9. 当前实现的主要缺口

## 9.1 协议缺口

- 尚未实现标准单端点 Streamable HTTP
- 尚未验证 `Mcp-Session-Id`
- 尚未验证 `Last-Event-ID` 恢复语义
- 尚未验证 `DELETE` 终止会话
- 尚未验证新版 `MCP-Protocol-Version` 协商行为

## 9.2 可靠性缺口

- 未做长连接心跳/保活专项验证
- 未做连接中断后自动恢复验证
- 未做高并发 / 多客户端并发验证
- 未做多实例 / 负载均衡环境验证

## 9.3 安全缺口

相对于 MCP 新规范建议，当前仍缺少明确专项验证或实现说明：

- `Origin` 校验
- MCP 专用端点的单独访问控制策略
- 远程部署下的认证方案
- 跨域访问的显式策略说明

目前本地绑定 `127.0.0.1` 已经降低了暴露面，但这不能替代完整的远程安全设计。

## 9.4 可观测性缺口

当前还缺少针对 MCP Server 的独立观测指标，例如：

- active session 数
- initialize 成功率
- tools/call 延迟分布
- SSE 建连成功率
- SSE 断线原因
- message endpoint 错误码分布

没有这些指标，后续做协议迁移和稳定性对比会比较被动。

## 10. 改进规划

下面给出一个相对务实的分阶段规划。

## 10.1 Phase A: 巩固当前 SSE 方案

目标：把当前 SSE 方案从“能跑”提升到“本地单机场景下可持续维护”。

建议项：

1. 为 MCP 端点补齐清晰文档：
   - 当前使用 SSE 旧协议
   - 客户端连接方式
   - 不支持的能力
2. 补充 MCP 相关日志字段：
   - sessionId
   - method
   - tool name
   - request duration
   - error category
3. 为 `scripts/mcp-e2e-test.sh` 增加更明确的失败输出：
   - 初始化失败
   - endpoint 解析失败
   - message endpoint 超时
4. 评估是否需要给 `/sse` 增加保活事件或心跳注释。
5. 若未来存在浏览器调用需求，补齐 `/sse` 与 `/mcp/message` 的 CORS 策略。

完成标准：

- 文档、日志、测试脚本能支撑日常维护
- 本地场景出问题时能快速定位是连接、协议还是工具层问题

## 10.2 Phase B: 为 Streamable HTTP 迁移做验证准备

目标：在不破坏现网行为的前提下，先做“可迁移性验证”，而不是直接大改。

建议项：

1. 调研并验证 Spring AI 更新版本是否已稳定支持 Streamable HTTP。
2. 明确当前 `STATELESS` 404 的根因：
   - Spring AI 版本限制
   - Starter / AutoConfiguration 限制
   - WebMVC 与其他 transport 的兼容问题
   - 本地配置遗漏
3. 准备一个最小化 PoC：
   - 最小 Spring Boot 应用
   - 单 tool
   - 单 endpoint `/mcp`
   - 仅验证 `initialize` / `tools/list` / `tools/call`
4. 为 PoC 编写独立 E2E 脚本，验证：
   - `POST /mcp` 初始化
   - `Mcp-Session-Id`
   - `MCP-Protocol-Version`
   - 单响应与流式响应两种模式

完成标准：

- 确认“当前依赖版本是否可支持”
- 确认“迁移后测试脚本如何改写”
- 确认“是否存在无法接受的框架级缺陷”

## 10.3 Phase C: 设计真正可迁移的目标态

目标：以 **`STREAMABLE`** 为下一步迁移方向。避免仅做肤浅的配置切换，而应设计一个真正能长期维护的目标实现（含会话、重试、恢复等）。

需要明确的设计问题：

1. 目标是**完全 stateless**，还是**显式 stateful session**？
2. 如果保留会话，如何做：
   - header 传递
   - 共享状态
   - 会话过期
   - 多实例路由
3. 工具调用是否需要 streaming progress？
4. 是否需要保留 GET 建立 SSE 流的能力，还是只支持请求内流式响应？
5. 是否需要会话显式销毁接口？

推荐方向：

- 优先追求“**请求内可闭合**”的工具调用模型
- 尽量减少对长生命周期进程内会话状态的依赖
- 把多实例、代理、中断恢复作为设计输入，而不是上线后再补救

## 10.4 Phase D: 正式迁移到 Streamable HTTP

迁移动作建议：

1. 引入支持稳定的 Spring AI / MCP Java SDK 版本
2. 暴露标准单端点，例如 `/mcp`
3. 改写 E2E 测试脚本
4. 增加协议兼容测试矩阵
5. 在确认稳定后移除旧 SSE 配置和说明

迁移完成标准：

- `initialize`、`tools/list`、`tools/call` 全部通过
- 单端点 `/mcp` 正常工作
- 关键头部与版本协商行为符合规范
- 回归测试无破坏
- 文档和运维说明同步更新

## 11. 建议的优先级

如果只考虑接下来 1 到 2 个迭代，建议优先级如下：

### P0

- 保持当前 SSE 单协议不再来回切换
- 完善这份文档
- 补齐 MCP 端点的日志和失败定位信息

### P1

- 独立验证 Spring AI 新版本的 Streamable HTTP 支持情况
- 编写最小 PoC
- 明确 `STATELESS` 404 根因

### P2

- 设计并验证迁移后的 E2E 测试方案
- 补可观测性与安全策略

### P3

- 正式切换到单端点 Streamable HTTP
- 清理 SSE 兼容代码和历史文档

## 12. 当前建议的对外表述

为了避免文档或沟通中产生误导，建议当前统一使用如下表述：

> 当前 MCP Server 已可用，基于 Spring AI 1.1.2 的 WebMVC MCP Server 实现，采用 SSE 传输并已通过端到端验证。在本地单机场景下可正常工作。  
> 但从 MCP 协议演进方向和长期工程目标看，后续仍应迁移到更符合新规范的 Streamable HTTP（目标协议为 `STREAMABLE`，见 `mcp-server-transport-analysis.md`）。  
> 当前未迁移的原因是：在现有版本栈中，Streamable HTTP 标准单端点实现（`STREAMABLE`/`STATELESS`）尚未在本项目成功跑通（实测 `STATELESS` 返回 404）。

这比简单说：

- “SSE 很稳定”
- “STATELESS 不行所以放弃”

都更准确。

## 13. 后续行动清单

建议下一步按下面顺序推进：

1. 合并并维护本文档。
2. 在文档中持续记录 `STATELESS` 相关验证结论。
3. 建一个最小 Streamable HTTP PoC 仓库或模块。
4. 升级并验证 Spring AI 对新传输的支持情况。
5. 基于 PoC 决定是否进入正式迁移。

## 14. 参考资料与事实核查来源

### 官方规范

- MCP Transports Spec (2025-06-18): https://modelcontextprotocol.io/specification/2025-06-18/basic/transports
- MCP RFC: Replace HTTP+SSE with Streamable HTTP: https://github.com/modelcontextprotocol/specification/pull/206

### Spring AI / 社区问题

- Spring AI Issue #3947, SSE 多实例下会话分布问题: https://github.com/spring-projects/spring-ai/issues/3947

### 背景阅读

- SSE 在生产环境中的代理、缓冲、超时问题讨论，可作为背景参考：
  - https://dev.to/miketalbot/server-sent-events-are-still-not-production-ready-after-a-decade-a-lesson-for-me-a-warning-for-you-2gie
  - https://github.com/caddyserver/caddy/issues/6293

### 相关文档

- MCP Server 传输协议分析（STREAMABLE vs STATELESS 选型、迁移步骤）: `docs/drafts/mcp-server-transport-analysis.md`

## 15. 最终判断

当前 MCP Server 的结论不是“还没做完”，而是：

- **功能上已经可用**
- **协议上仍处于过渡态**
- **短期方案是 SSE**
- **长期方向应是 Streamable HTTP**（采用 **`STREAMABLE`** 协议）

因此，当前最合理的工程策略不是反复切协议，而是：

- 先把 SSE 方案文档化、测试化、可观测化
- 再基于更成熟的框架支持，有计划地迁移到标准单端点实现
