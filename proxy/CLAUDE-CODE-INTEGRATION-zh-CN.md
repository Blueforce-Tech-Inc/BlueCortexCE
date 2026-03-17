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
export SILICONFLOW_API_KEY=your_embedding_key

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

Java Port 已实现 MCP Server（基于 Spring AI MCP Server WebMVC），提供以下工具：

| 工具 | 功能 | 说明 |
|------|------|------|
| `search` | 搜索记忆索引 | Step 1: 语义搜索，返回 ID 列表 |
| `timeline` | 获取上下文时间线 | Step 2: 获取锚点周围的观察记录 |
| `get_observations` | 获取完整观察详情 | Step 3: 根据 ID 获取完整内容 |
| `save_memory` | 手动保存记忆 | 存储重要信息供后续检索 |

### Claude Code MCP 配置

#### 方式一：CLI 命令添加 (推荐 ✅)

**这是最可靠的方式，确保 MCP 服务器被正确加载。**

```bash
# 添加 SSE 类型的 MCP 服务器
claude mcp add --transport sse cortexce http://127.0.0.1:37777/sse

# 查看已配置的服务器
claude mcp list

# 使用 /mcp 命令查看当前会话的 MCP 服务器
/mcp
```

#### 方式二：项目级 `.mcp.json` 文件

> ⚠️ **重要提示**：项目级 `.mcp.json` 可能不会自动加载！Claude Code 有时不会自动识别项目根目录的 MCP 配置文件。

在项目根目录创建 `.mcp.json` (可提交到 Git，团队共享)：

```json
{
  "mcpServers": {
    "cortexce": {
      "type": "sse",
      "url": "http://127.0.0.1:37777/sse"
    }
  }
}
```

如果使用 `.mcp.json` 后 `/mcp` 命令看不到服务器，请改用 **方式一** (`claude mcp add` 命令)。

#### 方式三：用户级配置文件

也可以直接编辑 `~/.claude/settings.json`，添加 `mcpServers` 配置：

```json
{
  "mcpServers": {
    "cortexce": {
      "type": "sse",
      "url": "http://127.0.0.1:37777/sse"
    }
  }
}
```

#### 方式对比

| 方式 | 可靠性 | 适用场景 |
|------|--------|---------|
| `claude mcp add` | ✅ 最可靠 | 首选方案 |
| `~/.claude/settings.json` | ✅ 可靠 | 用户级配置 |
| `.mcp.json` | ⚠️ 可能不生效 | 不保证加载 |

> **💡 建议**：优先使用 `claude mcp add` 命令，这是最稳定的方式。

#### Hooks 配置 (独立于 MCP)

Hooks 配置放在 `~/.claude/settings.json` (用户级) 或 `.claude/settings.local.json` (项目级)：

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

> **注意**: MCP 配置和 Hooks 配置是独立的。MCP 提供工具调用能力，Hooks 提供自动记录能力

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
cd java/scripts
./mcp-e2e-test.sh

# 应看到所有测试通过
# Tests: 9 passed
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
