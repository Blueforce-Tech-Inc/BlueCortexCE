# Cursor IDE 手动集成配置指南

本文档说明如何将 Claude-Mem Java Port 集成到 Cursor IDE 中。

---

## 概述

Cursor IDE 支持两种集成方式：
1. **Hooks** - 在特定事件触发时执行脚本
2. **MCP (Model Context Protocol)** - 提供工具供 AI 调用

Java Port 后端已实现 `CursorService` 来管理项目注册和上下文文件，但需要手动配置 Cursor IDE。

---

## 前置条件

### 1. 启动 Java 后端

```bash
cd ~/.cortexce

# 设置 API keys
export OPENAI_API_KEY=your_api_key
export SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key

# 启动后端
java -jar target/cortexce-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev &
```

验证后端已启动:
```bash
curl http://127.0.0.1:37777/actuator/health
# 应返回: {"status":"UP",...}
```

### 2. 安装 Thin Proxy 依赖

```bash
cd ~/.cortexce/proxy
npm install
```

---

## 方式一：Cursor Hooks 配置

Cursor IDE 使用 `.cursor/hooks.json` 或 `~/.cursor/hooks.json` 配置 hooks。

### Hook 配置文件

在项目根目录创建 `.cursor/hooks.json`：

```json
{
  "version": 1,
  "hooks": {
    "beforeSubmitPrompt": [
      {
        "command": "node ~/.cortexce/proxy/wrapper.js cursor session-init --url http://127.0.0.1:37777"
      },
      {
        "command": "node ~/.cortexce/proxy/wrapper.js cursor context --url http://127.0.0.1:37777"
      }
    ],
    "afterMCPExecution": [
      {
        "command": "node ~/.cortexce/proxy/wrapper.js cursor observation --url http://127.0.0.1:37777"
      }
    ],
    "afterShellExecution": [
      {
        "command": "node ~/.cortexce/proxy/wrapper.js cursor observation --url http://127.0.0.1:37777"
      }
    ],
    "afterFileEdit": [
      {
        "command": "node ~/.cortexce/proxy/wrapper.js cursor file-edit --url http://127.0.0.1:37777"
      }
    ],
    "stop": [
      {
        "command": "node ~/.cortexce/proxy/wrapper.js cursor summarize --url http://127.0.0.1:37777"
      }
    ]
  }
}
```

### Hook 事件说明

| 事件 | 触发时机 | 用途 |
|------|---------|------|
| `beforeSubmitPrompt` | 用户提交 prompt 前 | 初始化会话 + 注入上下文 |
| `afterMCPExecution` | MCP 工具执行后 | 记录观察 |
| `afterShellExecution` | Shell 命令执行后 | 记录观察 |
| `afterFileEdit` | 文件编辑后 | 记录文件编辑观察 |
| `stop` | 会话停止时 | 生成摘要 |

---

## 方式二：MCP 配置（推荐）

Cursor IDE 支持 MCP (Model Context Protocol) 来提供工具。

### Java Port MCP Server

Java Port 已实现 MCP Server（基于 Spring AI MCP Server WebMVC），提供以下工具：

| 工具 | 功能 | 说明 |
|------|------|------|
| `search` | 搜索记忆索引 | Step 1: 语义搜索，返回 ID 列表 |
| `timeline` | 获取上下文时间线 | Step 2: 获取锚点周围的观察记录 |
| `get_observations` | 获取完整观察详情 | Step 3: 根据 ID 获取完整内容 |
| `save_memory` | 手动保存记忆 | 存储重要信息供后续检索 |

### MCP 配置文件

**项目级配置** (推荐): 在项目根目录创建 `.cursor/mcp.json`

**用户级配置**: `~/.cursor/mcp.json`

```json
{
  "mcpServers": {
    "cortexce": {
      "url": "http://127.0.0.1:37777/sse"
    }
  }
}
```

> **注意**:
> - Cursor 会自动检测传输协议，无需显式指定 `"type": "sse"`
> - Java Port 使用 SSE 协议，确保 Java 后端已启动 (`localhost:37777`)
> - 如果遇到连接问题，尝试在 Cursor 设置中禁用 HTTP/2: Settings → Network → Disable HTTP/2

### MCP 工具使用流程

Claude 会自动调用这些工具来检索历史记忆：

```
用户问: "上次我们怎么解决登录问题的？"
    ↓
Claude 调用 search 工具
    ↓
返回相关观察记录 ID 列表
    ↓
Claude 调用 get_observations 获取详情
    ↓
Claude 基于历史上下文回答
```

### 验证 MCP 连接

```bash
# 运行 MCP 端到端测试
cd ~/.cortexce/scripts
./mcp-e2e-test.sh

# 应看到所有测试通过
# Tests: 9 passed
```

---

## 上下文文件

### 自动更新

Java Port 后端会在会话结束时自动更新上下文文件：

```
.cursor/rules/claude-mem-context.mdc
```

### 手动注册项目

通过 API 注册项目以启用自动上下文更新：

```bash
# 注册项目
curl -X POST http://127.0.0.1:37777/api/cursor/register \
  -H "Content-Type: application/json" \
  -d '{"projectName": "my-project", "workspacePath": "~/.cortexce/proxy/workspace"}'

# 查看已注册项目
curl http://127.0.0.1:37777/api/cursor/projects

# 手动更新上下文
curl -X POST http://127.0.0.1:37777/api/cursor/context/my-project
```

### 上下文文件格式

```markdown
---
alwaysApply: true
description: "Claude-mem context from past sessions (auto-updated)"
---

# Memory Context from Past Sessions

## Recent Activity

### Feb 14, 2026

| ID | Time | T | Title |
|----|------|---|-------|
| #1 | 10:00 AM | 🔵 | Fixed login bug |

...
```

---

## 项目注册表

Java Port 使用 `~/.claude-mem/cursor-projects.json` 管理已注册的项目：

```json
{
  "my-project": {
    "workspacePath": "/Users/xxx/projects/my-project",
    "installedAt": "2026-02-14T10:00:00Z"
  }
}
```

---

## API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/cursor/register` | POST | 注册项目 |
| `/api/cursor/register/{name}` | DELETE | 注销项目 |
| `/api/cursor/projects` | GET | 列出所有已注册项目 |
| `/api/cursor/context/{name}` | POST | 更新项目的上下文文件 |
| `/api/cursor/register/{name}` | GET | 检查项目是否已注册 |

---

## 故障排除

### 上下文文件没有更新

1. 检查项目是否已注册：
   ```bash
   curl http://127.0.0.1:37777/api/cursor/projects
   ```

2. 检查 `.cursor/rules/` 目录是否存在

3. 手动触发更新：
   ```bash
   curl -X POST http://127.0.0.1:37777/api/cursor/context/my-project
   ```

### Hooks 没有触发

1. 检查 `.cursor/hooks.json` 路径正确
2. 检查 wrapper.js 有执行权限：`chmod +x wrapper.js`
3. 检查 Java 后端是否运行
4. 开启调试模式：`export CLAUDE_MEM_DEBUG=true`

### Cursor 架构限制

⚠️ **重要提示**：Cursor 的 Hooks 设计有功能限制，与 Claude Code 不同：

| 工具类型 | Claude Code Hook | Cursor Hook | 能否捕获 |
|---------|-----------------|-------------|---------|
| MCP 工具 | `PostToolUse` | `afterMCPExecution` | ✅ 可以 |
| Shell 命令 | `PostToolUse` | `afterShellExecution` | ✅ 可以 |
| 文件编辑 | `PostToolUse` | `afterFileEdit` | ✅ 可以 |
| **Read 文件** | `PostToolUse` | ❌ 无对应 Hook | ❌ 无法 |
| **Write 文件** | `PostToolUse` | ❌ 无对应 Hook | ❌ 无法 |
| **Glob 搜索** | `PostToolUse` | ❌ 无对应 Hook | ❌ 无法 |

这意味着 Cursor Agent 内置工具的使用不会被记录，只有 MCP 工具、Shell 命令、文件编辑会被捕获。

**调试模式**：
```bash
# 开启调试日志
export CLAUDE_MEM_DEBUG=true
```

---

## 与 TS 原版对比

| 功能 | TS 原版 | Java Port |
|------|---------|-----------|
| Hook 安装 CLI | `claude-mem cursor install` | 需手动配置 |
| MCP Server | ✅ stdio 协议 | ✅ SSE 协议 |
| MCP 工具 | search, timeline, get_observations, save_memory | search, timeline, get_observations, save_memory |
| 项目注册 | 自动 | API 调用 |
| 上下文文件 | 自动更新 | 自动更新 (需先注册) |

---

## 待实现功能

- [x] wrapper.js 添加 `cursor` 子命令 (已完成)
- [x] Java Port 实现 MCP Server (已完成 - Spring AI MCP Server WebMVC)
- [ ] 自动检测并注册项目
- [ ] 安装/卸载 CLI 命令

---

## 相关文件

| 文件 | 说明 |
|------|------|
| `~/.cortexce/proxy/wrapper.js` | Thin Proxy CLI |
| `java/cortexce/.../CursorService.java` | 项目注册 + 上下文管理 |
| `java/cortexce/.../CursorController.java` | REST API |
| `~/.claude-mem/cursor-projects.json` | 项目注册表 |
