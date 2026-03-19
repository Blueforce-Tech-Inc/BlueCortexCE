# Claude Code 手动集成配置指南

本文档说明如何将 Claude-Mem Java Thin Proxy 手动集成到 Claude Code 中。

---

## 前置条件

### 1. 安装依赖

```bash
cd ~/.cortexce/proxy
npm install
```

### 2. 启动 Java 后端

```bash
cd ~/.cortexce

# 设置 API keys (从 .env 文件加载)
export OPENAI_API_KEY=your_api_key
export SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key

# 启动后端 (dev profile 会自动加载 .env)
java -jar target/cortexce-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev &
```

验证后端已启动:
```bash
curl http://127.0.0.1:37777/actuator/health
# 应返回: {"status":"UP",...}
```

### 3. 验证 Thin Proxy

```bash
cd java/scripts
./thin-proxy-test.sh
# 应全部通过
```

---

## 配置 Claude Code Hooks

Claude Code 支持三种配置文件位置：

| 配置文件 | 范围 | 说明 |
|---------|------|------|
| `~/.claude/settings.json` | 用户级 | 所有项目生效，适合个人通用配置 |
| `.claude/settings.json` | 项目级 | 团队共享，提交到仓库 |
| `.claude/settings.local.json` | 项目级本地 | 仅自己使用，不提交到仓库 |

编辑任一配置文件:

```json
{
  "hooks": {
    "SessionStart": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js session-start --url http://127.0.0.1:37777",
        "blocking": false
      }]
    }],
    "PostToolUse": [{
      "matcher": "Edit|Write|Read|Bash",
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js tool-use --url http://127.0.0.1:37777",
        "blocking": false
      }]
    }],
    "SessionEnd": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js session-end --url http://127.0.0.1:37777",
        "blocking": false
      }]
    }],
    "UserPromptSubmit": [{
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js user-prompt --url http://127.0.0.1:37777",
        "blocking": false
      }]
    }]
  }
}
```

### 字段说明

| 字段 | 说明 |
|-----|------|
| `SessionStart` | 会话启动时触发 (每次 Claude Code 启动时) |
| `PostToolUse` | 工具执行后触发 (仅 Edit/Write/Read/Bash) |
| `SessionEnd` | Claude 停止时触发 (保存 lastAssistantMessage + 生成摘要) |
| `UserPromptSubmit` | 用户提交提示时触发 (记录用户提示) |

### 可选配置

| 字段 | 说明 |
|-----|------|
| `matcher` | 工具名称过滤模式 (如 `Edit|Write|Read\|Bash`)，留空表示匹配所有 |
| `blocking` | `false`=失败不阻断，`true`=失败阻断 Claude 操作 |

---

## 环境变量配置

### 文件夹 CLAUDE.md 更新 (Folder CLAUDE.md Updates)

**功能**: 自动更新子文件夹中的 CLAUDE.md 文件，注入该文件夹相关的观察记录。

**与 TS 版本对齐**: 完全对齐 `src/utils/claude-md-utils.ts` 的 `updateFolderClaudeMdFiles()` 功能。

#### 启用方式

**方式一：命令行参数（推荐，更直观）**

```json
{
  "hooks": {
    "PostToolUse": [{
      "matcher": "Edit|Write|Read|Bash",
      "hooks": [{
        "type": "command",
        "command": "~/.cortexce/proxy/wrapper.js tool-use --url http://127.0.0.1:37777",
        "async": true
      }]
    }]
  }
}
```

**方式二：环境变量**

```json
{
  "hooks": {
    "PostToolUse": [{
      "matcher": "Edit|Write|Read|Bash",
      "hooks": [{
        "type": "command",
        "command": "CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true ~/.cortexce/proxy/wrapper.js tool-use --url http://127.0.0.1:37777",
        "async": true
      }]
    }]
  }
}
```

**方式三：Shell 配置文件**

```bash
# ~/.zshrc 或 ~/.bashrc
export CLAUDE_MEM_FOLDER_CLAUDEMD_ENABLED=true
```

> **注意**: 推荐使用 `async: true` 而不是 `blocking: false`。`async: true` 让 wrapper 在后台运行，不阻塞 Claude Code，用户体验更好。

#### 功能行为

| 行为 | 说明 |
|------|------|
| 触发时机 | PostToolUse hook 执行后（如 Edit、Write 操作） |
| 更新范围 | 仅更新子文件夹的 CLAUDE.md，**跳过项目根目录** |
| 活跃文件排除 | 跳过正在读写的 CLAUDE.md 文件夹（避免 "file modified since read" 错误） |
| 安全目录排除 | 跳过 `node_modules`、`.git`、`build`、`__pycache__`、Android `res/` |
| 数据来源 | 调用 `/api/search/by-file?isFolder=true` API 获取文件夹相关观察 |
| 写入方式 | 替换 `<claude-mem-context>...</claude-mem-context>` 标签内容 |

#### 代码位置

| 功能 | 文件 | 行号 |
|------|------|------|
| 主函数 | `wrapper.js` | 623-720 |
| 文件夹过滤 | `wrapper.js` | 808-833 |
| 活跃文件排除 | `wrapper.js` | 628-641 |
| 格式化输出 | `wrapper.js` | 729-799 |
| 后端 API | `ViewerController.java` | 431 |

---

### stdin 机制

Hook 命令通过 **stdin** 接收 JSON 格式的事件上下文，包含：
- `session_id`: 会话 ID
- `cwd`: 当前工作目录
- `tool_name`: 工具名称 (PostToolUse)
- `tool_input`: 工具输入参数
- `tool_response`: 工具执行结果
- `transcript_path`: 会话记录文件路径 (SessionEnd)

---

## 快速测试命令

### 测试 SessionStart Hook

```bash
echo '{
  "hook_event_name": "SessionStart",
  "session_id": "test-123",
  "cwd": "/tmp"
}' | node ~/.cortexce/proxy/wrapper.js session-start --url http://127.0.0.1:37777
```

### 测试 PostToolUse Hook

```bash
echo '{
  "hook_event_name": "PostToolUse",
  "session_id": "test-123",
  "cwd": "/tmp",
  "tool_name": "Edit",
  "tool_input": {"path": "test.txt", "old_string": "old", "new_string": "new"},
  "tool_response": {"success": true}
}' | node ~/.cortexce/proxy/wrapper.js tool-use --url http://127.0.0.1:37777
```

### 测试工具过滤 (Glob 应跳过)

```bash
echo '{
  "hook_event_name": "PostToolUse",
  "session_id": "test-123",
  "cwd": "/tmp",
  "tool_name": "Glob",
  "tool_input": {"pattern": "**/*.txt"}
}' | node ~/.cortexce/proxy/wrapper.js tool-use --url http://127.0.0.1:37777
# 应静默退出 (exit 0)
```

---

## 文件结构

```
java/
├── proxy/
│   ├── wrapper.js           # CLI Wrapper (主入口)
│   ├── package.json         # NPM 依赖
│   └── CLAUDE.md          # 英文文档
├── cortexce/        # Spring Boot 后端
│   ├── src/main/java/...
│   ├── target/*.jar        # 编译产物
│   └── .env              # API Keys 配置
└── scripts/
    ├── thin-proxy-test.sh  # Thin Proxy 快速测试
    └── regression-test.sh   # 完整回归测试
```

---

## 故障排除

### "Cannot connect to Java API"

```bash
# 检查 Java 后端是否运行
curl http://127.0.0.1:37777/actuator/health

# 检查端口
lsof -i :37777
```

### Hook 没有触发

- 确认 `~/.claude/settings.json` 路径正确
- 确认 `matcher` 模式匹配工具名称
- 检查 Claude Code 日志

### 退出码说明

| 退出码 | 含义 |
|-------|------|
| 0 | 成功/静默跳过 |
| 1 | 非阻塞错误 (stderr 显示，继续) |
| 2 | 阻塞错误 (喂给 Claude 处理) |

---

## MCP 工具配置

Java 后端支持两种 MCP 传输协议：

| 协议 | 端点 | 说明 |
|------|------|------|
| **SSE** (默认) | `/sse` | Server-Sent Events，稳定可靠 |
| **Streamable HTTP** | `/mcp` | 现代 HTTP 协议，适合多实例部署 |

### 配置 MCP 协议

MCP 协议配置位于 `backend/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: SSE  # 或: STREAMABLE
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
        streamable-http:
          mcp-endpoint: /mcp
```

### 使用 SSE 协议（默认）

```bash
# SSE 是默认协议，基本配置无需额外设置
# 添加 MCP 服务器（SSE 传输）：
claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse
```

> **✅ 客户端兼容性**：SSE 推荐用于最大兼容性，支持各种 MCP 客户端（Claude Code、Cursor IDE 等），无需会话管理。

### 使用 Streamable HTTP 协议（备选）

```bash
# 方式一：环境变量
export SPRING_AI_MCP_SERVER_PROTOCOL=STREAMABLE

# 方式二：修改 application.yml
# 设置: protocol: STREAMABLE

# 添加 MCP 服务器（HTTP 传输）：
claude mcp add --transport http cortexce http://127.0.0.1:37777/mcp
```

> **⚠️ STREAMABLE 客户端要求**：MCP 客户端必须正确实现 `Mcp-Session-Id` 头处理。如果遇到 "Session ID missing" 错误，请改用 SSE。
claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse
```

> **💡 提示**：统一测试脚本 `scripts/mcp-e2e-test.sh` 会自动检测服务端使用的协议，运行对应的测试，无需手动选择测试脚本！

### 协议对比

| 特性 | SSE | Streamable HTTP |
|------|-----|----------------|
| 端点 | `/sse` + `/mcp/message` | 单端点 `/mcp` |
| 会话管理 | 需要 sessionId | 需要 Mcp-Session-Id |
| 多实例支持 | 需要会话黏性 | 原生支持 |
| 规范版本 | 旧 (2024-11-05) | 新 (2025-03-26+) |

### 验证 MCP 连接

```bash
# 运行 MCP 端到端测试（自动检测协议）
cd java/scripts
./mcp-e2e-test.sh

# 应看到所有测试通过
# Protocol tested: SSE/STREAMABLE
# Tests: X passed
```

---

## 与 TypeScript 版本对比

| 功能 | TypeScript | Java |
|-----|-----------|------|
| Worker 服务 | Bun 长期运行 (37777) | Spring Boot (37777) |
| Hook 触发 | `node worker-service.cjs hook ...` | `node wrapper.js session-start` |
| API 通信 | 内置 | HTTP POST |
| 数据库 | SQLite3 | PostgreSQL |
| 向量搜索 | Chroma | PostgreSQL pgvector |
| MCP Server | ✅ stdio 协议 | ✅ SSE 协议 |
| MCP 工具 | search, timeline, get_observations, save_memory | search, timeline, get_observations, save_memory |

Java 版本使用更传统的 C/S 架构：wrapper.js 作为 CLI 薄代理，Java 后端处理所有业务逻辑。

---

## 后续优化

- [ ] 自动检测并启动 Java 后端
- [x] 支持 Cursor IDE hooks (已完成)
- [x] 实现 MCP Server (已完成 - Spring AI MCP Server WebMVC)
- [ ] 添加 metrics/可观测性

---

## wrapper.js 支持的命令

| 命令 | 说明 |
|------|------|
| `session-start` | Handle SessionStart event (session init + context) |
| `tool-use` | Handle PostToolUse event (observation) |
| `session-end` | Handle SessionEnd event (trigger summary generation) |
| `user-prompt` | Handle UserPromptSubmit event (record user prompt) |
