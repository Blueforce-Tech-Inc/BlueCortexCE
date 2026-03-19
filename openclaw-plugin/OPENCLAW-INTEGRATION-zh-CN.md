# OpenClaw 手动集成配置指南

本文档说明如何将 Claude-Mem Java 后端集成到 OpenClaw Gateway 中。

> ⚠️ **实验性功能 - 未经真实验证**
>
> 本插件基于 OpenClaw 官方 SDK 类型定义编写，事件名称和 API 调用方式已与官方规范对齐。
> 但**尚未在真实的 OpenClaw Gateway 环境中测试**。
>
> 使用前请：
> 1. 确保已正确安装并配置 OpenClaw Gateway
> 2. 将编译后的插件放置在 `~/.openclaw/extensions/cortexce/` 目录
> 3. 使用 `openclaw plugins doctor` 检查插件是否正确加载
> 4. 如遇到问题，请提交 issue 反馈

---

## 集成架构概述

Claude-Mem 与 OpenClaw 的集成分为**两层**：

```
┌─────────────────────────────────────────────────────────────────────┐
│                    OpenClaw + Claude-Mem 集成架构                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Layer 1: 记忆捕获（Plugin - 自动）                                   │
│  ├── OpenClaw 插件监听 7 个生命周期事件                               │
│  ├── 自动记录工具使用为 Observation                                   │
│  ├── 自动同步 MEMORY.md 到 workspace                                 │
│  └── 用户无需任何操作                                                │
│                                                                      │
│  Layer 2: 主动搜索（Skill - 按需）                                   │
│  ├── AgentSkills 兼容的 SKILL.md 文件                                │
│  ├── Agent 根据用户问题自动判断是否需要搜索记忆                        │
│  ├── 通过 REST API 调用 Java 后端进行语义搜索                         │
│  └── 无需 MCP 协议（OpenClaw 创始人反对 MCP）                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 与其他 IDE 集成方式对比

| IDE | Layer 1: 记忆捕获 | Layer 2: 主动搜索 | 搜索方式 |
|-----|------------------|------------------|---------|
| **Claude Code** | Hooks + wrapper.js | MCP Server | MCP 协议 |
| **Cursor IDE** | Hooks + wrapper.js | MCP Server | MCP 协议 |
| **TRAE** | .rules 系统注入 | MCP Server | MCP 协议 |
| **OpenClaw** | Plugin（本文档） | **Skill**（本文档） | REST API |

> **注意**：OpenClaw 创始人明确表示不喜欢 MCP 协议，因此使用 **AgentSkills + REST API** 的方式实现主动搜索。

---

## 前置条件

### 1. 编译 OpenClaw 插件

```bash
cd ~/.cortexce/openclaw-plugin
npm install
npm run build
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

---

## Layer 2: 主动搜索 Skill 配置

让 OpenClaw Agent 能够在**需要时主动搜索**历史记忆，无需用户手动调用命令。

### Skill 工作原理

```
┌─────────────────────────────────────────────────────────────────────┐
│                    OpenClaw AgentSkills 渐进式披露                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Level 1: 触发判断 (~100 tokens)                                     │
│  ├── 读取 SKILL.md 的 name + description                            │
│  ├── 判断用户问题是否需要搜索记忆                                     │
│  └── 例如："上次我们", "之前是怎么", "search memory"                 │
│                                                                      │
│  Level 2: 完整技能内容 (按需加载)                                    │
│  ├── 当 Agent 判断需要搜索时加载完整 SKILL.md                        │
│  ├── 获取三步工作流、API 端点、curl 示例                             │
│  └── Agent 自动执行搜索逻辑                                          │
│                                                                      │
│  Level 3: 引用文件 (按需加载)                                        │
│  └── 如有脚本或数据文件，按需加载                                     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 安装 Skill

**方式一：全局 Skill（推荐）**

```bash
# 创建全局 skills 目录
mkdir -p ~/.openclaw/skills

# 复制 Skill 文件
cp -r ~/.cortexce/openclaw-plugin/skills/claude-mem-search ~/.openclaw/skills/
```

**方式二：项目级 Skill**

```bash
# 在项目根目录创建 skills 目录
mkdir -p /path/to/your-project/skills

# 复制 Skill 文件
cp -r ~/.cortexce/openclaw-plugin/skills/claude-mem-search /path/to/your-project/skills/
```

### Skill 文件位置

```
~/.openclaw/skills/claude-mem-search/    # 全局（所有项目可用）
└── SKILL.md                              # AgentSkills 兼容格式

# 或

/path/to/project/skills/claude-mem-search/  # 项目级（仅该项目可用）
└── SKILL.md
```

### 验证 Skill 安装

```bash
# 检查 Skill 文件是否存在
ls -la ~/.openclaw/skills/claude-mem-search/SKILL.md

# 重启 OpenClaw Gateway 使配置生效
openclaw gateway restart
```

### 触发关键词

当用户问以下问题时，Agent 会**自动激活**搜索技能（无需手动调用命令）：

- **中文**: "上次我们怎么做...", "之前是怎么...", "搜索记忆...", "查找之前..."
- **英文**: "what did we do before", "last time we...", "search memory", "recall when..."

---

## 完整 SKILL.md 示例

以下是 `~/.openclaw/skills/claude-mem-search/SKILL.md` 的完整内容：

```markdown
---
name: claude-mem-search
description: Searches Claude-Mem memory system for historical observations,
  summaries, and context from past sessions. Use when user asks about
  "what did we do before", "last time we", "search memory", "find previous",
  "recall when", "上次我们", "之前是怎么", "搜索记忆", "查找之前", or when
  context from past work sessions would be helpful for the current task.
  Requires Claude-Mem Java backend running at localhost:37777.
---

# Claude-Mem Memory Search

Search and retrieve context from past development sessions stored in Claude-Mem.

## When to Use

Activate this skill when:
- User references past work ("上次我们...", "之前是怎么...", "last time we...")
- User asks to search history ("搜索记忆", "查找之前的实现", "recall when...")
- Current task may benefit from historical context
- User wants to know what was done in a previous session

## Prerequisites

Claude-Mem Java backend must be running:

```bash
curl -s http://127.0.0.1:37777/actuator/health
# Should return: {"status":"UP",...}
```

If not running, tell user to start the Java backend first.

---

## Three-Step Memory Retrieval Workflow

### Step 1: Search Memory Index

Search for relevant observations by semantic query or filter:

```bash
# Semantic search
curl -s "http://127.0.0.1:37777/api/search?project=PROJECT_PATH&query=SEARCH_QUERY&limit=5"

# Filter by type
curl -s "http://127.0.0.1:37777/api/search?project=PROJECT_PATH&type=discovery&limit=5"

# Filter by concept
curl -s "http://127.0.0.1:37777/api/search?project=PROJECT_PATH&concept=architecture&limit=5"
```

**Parameters:**
- `project` (required): Project path, e.g., `/Users/username/projects/myapp`
- `query`: Semantic search query
- `type`: Filter by observation type (discovery, decision, error, etc.)
- `concept`: Filter by concept (architecture, testing, security, etc.)
- `limit`: Max results (default: 20)

**Returns:** List of observations with IDs and metadata.

### Step 2: Get Timeline Context (Optional)

Get observations around a specific anchor point:

```bash
# By anchor ID
curl -s "http://127.0.0.1:37777/api/context/timeline?project=PROJECT_PATH&anchorId=OBSERVATION_ID&depthBefore=3&depthAfter=3"

# By query (finds best matching anchor)
curl -s "http://127.0.0.1:37777/api/context/timeline?project=PROJECT_PATH&query=SEARCH_QUERY&depthBefore=3&depthAfter=3"
```

### Step 3: Get Full Observation Details

Fetch complete details for specific observation IDs:

```bash
curl -s -X POST "http://127.0.0.1:37777/api/observations/batch" \
  -H "Content-Type: application/json" \
  -d '{"ids": ["id1", "id2"], "project": "PROJECT_PATH"}'
```

---

## Quick Access Methods

### Recent Sessions

```bash
curl -s "http://127.0.0.1:37777/api/context/recent?project=PROJECT_PATH&limit=3"
```

### Save Manual Memory

```bash
curl -s -X POST "http://127.0.0.1:37777/api/memory/save" \
  -H "Content-Type: application/json" \
  -d '{"text": "Important insight", "title": "Key Decision", "project": "PROJECT_PATH"}'
```

---

## Response Format

All responses are JSON. Each observation contains:
- `id`: Unique identifier
- `title`: Short title
- `content`: Full content text
- `type`: Observation type
- `concepts`: Related concepts
- `createdAtEpoch`: Timestamp
- `filePath`: Related file path (if any)
```

---

### 文件来源

Skill 文件位于本项目：
```
~/.cortexce/openclaw-plugin/skills/claude-mem-search/SKILL.md
```

### REST API 端点参考

Skill 通过 REST API 调用 Java 后端（无需 MCP 协议）：

| 功能 | MCP Tool | REST API | 方法 |
|------|----------|----------|------|
| 语义搜索 | `search` | `/api/search` | GET |
| 获取时间线 | `timeline` | `/api/context/timeline` | GET |
| 获取完整详情 | `get_observations` | `/api/observations/batch` | POST |
| 保存记忆 | `save_memory` | `/api/memory/save` | POST |
| 最近会话 | `recent` | `/api/context/recent` | GET |

### API 调用示例

```bash
# 语义搜索
curl -s "http://127.0.0.1:37777/api/search?project=/path/to/project&query=login&limit=5"

# 获取时间线上下文
curl -s "http://127.0.0.1:37777/api/context/timeline?project=/path/to/project&query=login&depthBefore=3&depthAfter=3"

# 批量获取详情
curl -s -X POST "http://127.0.0.1:37777/api/observations/batch" \
  -H "Content-Type: application/json" \
  -d '{"ids": ["id1", "id2"], "project": "/path/to/project"}'

# 获取最近会话
curl -s "http://127.0.0.1:37777/api/context/recent?project=/path/to/project&limit=3"
```

### 三步记忆检索工作流

1. **搜索索引** → 返回匹配的 Observation ID 列表
2. **获取时间线** → 获取锚点前后的上下文
3. **获取完整详情** → 按 ID 获取完整内容

详见 `skills/claude-mem-search/SKILL.md`。

---

## Layer 1: 插件集成（记忆捕获）

OpenClaw 支持三种插件集成方式，选择其中一种即可。

### 两个配置文件的关系

| 文件 | 位置 | 作用 |
|------|------|------|
| `openclaw.plugin.json` | 插件目录内 | **插件清单**：定义插件 ID、名称、配置 schema（不接受用户配置值） |
| OpenClaw 主配置文件 | `~/.openclaw/config.json` 或项目目录 | **用户配置**：启用插件、提供具体配置值 |

```
┌─────────────────────────────────────────────────────────────────────┐
│  openclaw.plugin.json (插件目录内 - 不可修改)                         │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ configSchema: {                                               │  │
│  │   workerPort: { type: "number", default: 37777 }  ← 定义结构   │  │
│  │ }                                                             │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                          ↓ 定义配置结构                               │
├─────────────────────────────────────────────────────────────────────┤
│  OpenClaw 主配置文件 (用户可修改)                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ plugins.entries."cortexce".config: {                   │  │
│  │   workerPort: 37777  ← 提供具体值（必须符合 schema）            │  │
│  │ }                                                             │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 方式一：自动发现（推荐，最简单）

将编译后的插件复制到 OpenClaw 扩展目录，OpenClaw 会自动发现并加载。

```bash
# 创建目标目录
mkdir -p ~/.openclaw/extensions/cortexce

# 复制必要文件
cp ~/.cortexce/openclaw-plugin/openclaw.plugin.json ~/.openclaw/extensions/cortexce/
cp ~/.cortexce/openclaw-plugin/dist/index.js ~/.openclaw/extensions/cortexce/
```

**优点**：无需修改 OpenClaw 配置文件，使用 `configSchema` 中定义的默认值。

**验证**：
```bash
openclaw plugins list          # 应显示 cortexce
openclaw plugins doctor        # 检查是否有错误
```

---

### 方式二：通过配置文件指定路径

在 OpenClaw 配置文件中指定插件路径和配置值。

**配置文件位置**：`~/.openclaw/config.json` 或项目目录的 `openclaw.json`

```json
{
  "plugins": {
    "enabled": true,
    "load": {
      "paths": ["~/.cortexce/openclaw-plugin"]
    },
    "entries": {
      "cortexce": {
        "enabled": true,
        "config": {
          "workerPort": 37777,
          "project": "my-project",
          "syncMemoryFile": true
        }
      }
    }
  }
}
```

**优点**：可以覆盖默认配置值，适合需要自定义配置的场景。

**注意**：`load.paths` 指向包含 `openclaw.plugin.json` 的目录。

---

### 方式三：命令行安装

使用 OpenClaw CLI 命令安装。

```bash
# 从本地目录安装
openclaw plugins install ~/.cortexce/openclaw-plugin

# 安装后启用
openclaw plugins enable cortexce

# 重启 Gateway
openclaw gateway restart
```

**优点**：OpenClaw 自动管理插件文件。

---

### 配置字段说明

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `workerPort` | number | 37777 | Java 后端端口 |
| `project` | string | "openclaw" | 项目名称，用于记忆追踪 |
| `syncMemoryFile` | boolean | true | 是否同步 MEMORY.md 文件 |

---

### 集成方式对比

| 方式 | 复杂度 | 适用场景 |
|------|--------|----------|
| 方式一：自动发现 | ⭐ 简单 | 开发测试、使用默认配置 |
| 方式二：配置文件 | ⭐⭐ 中等 | 需要自定义配置、多环境管理 |
| 方式三：命令安装 | ⭐⭐ 中等 | 生产环境、版本管理 |

---

## 可用命令

插件注册了两个命令：

### /claude-mem-status

检查 Java 后端健康状态和会话统计。

```bash
/claude-mem-status
```

返回示例：
```
Claude-Mem Java Backend Status
Status: UP
Port: 37777
Active sessions: 2
```

### /claude-mem-projects

列出所有已追踪的项目。

```bash
/claude-mem-projects
```

返回示例：
```
Claude-Mem Projects
  - my-project
  - openclaw
  - workspace-abc
```

---

## 事件监听

插件监听 OpenClaw Gateway 的 7 个生命周期事件：

| 事件 | 时机 | 插件行为 |
|------|------|----------|
| `session_start` | 用户发起新会话 (`/new`, `/reset`) | 初始化 claude-mem 会话 |
| `after_compaction` | 上下文压缩后 | 重新初始化会话 |
| `before_agent_start` | Agent 执行前 | 同步 MEMORY.md + 跟踪工作区 |
| `tool_result_persist` | 工具执行后 | 记录观察 + 同步 MEMORY.md |
| `agent_end` | Agent 执行结束 | 生成摘要 + 完成会话 |
| `session_end` | 会话结束 | 清理会话跟踪 |
| `gateway_start` | Gateway 启动 | 重置会话跟踪 |

---

## MEMORY.md 同步机制

### 同步流程

```
1. before_agent_start 事件触发
       ↓
2. 插件调用 /api/context/inject 获取 timeline
       ↓
3. 写入 workspaceDir/MEMORY.md
       ↓
4. Agent 启动时读取 MEMORY.md 获取上下文
```

### 同步时机

| 事件 | 是否同步 | 说明 |
|------|----------|------|
| `before_agent_start` | ✅ | Agent 启动前获取上下文 |
| `tool_result_persist` | ✅ | 每次工具使用后更新 |
| `session_start` | ❌ | 仅初始化会话 |
| `agent_end` | ❌ | 仅摘要和完成 |

---

## API 端点映射

插件通过 HTTP 调用 Java 后端 API：

| 功能 | 插件调用 | Java 后端端点 |
|------|----------|---------------|
| 会话初始化 | `/api/sessions/init` | `/api/session/start` |
| 记录工具使用 | `/api/sessions/observations` | `/api/ingest/tool-use` |
| 会话完成 | `/api/sessions/complete` | `/api/ingest/session-end` |
| 获取 Timeline | `/api/context/inject` | `/api/context/inject` |

---

## 与 TypeScript 版本对比

| 特性 | TypeScript 版本 | Java 版本 |
|------|---------------|----------|
| 后端 | TypeScript Worker | Java Spring Boot |
| SSE 支持 | ✅ 有 | ❌ 无 |
| MEMORY.md | ✅ | ✅ |
| 观察记录 | ✅ | ✅ |
| 命令 | `/claude-mem-status`, `/claude-mem-feed` | `/claude-mem-status`, `/claude-mem-projects` |

### 为什么 Java 版本没有 SSE？

Java 版本采用 **Thin Proxy** 架构理念：
- Thin Proxy = CLI 模式，运行即退出，不维护长连接
- SSE 需要常驻进程，与 Thin Proxy 理念冲突
- 保持轻量、快速、资源友好

**替代方案**：用户可以通过 WebUI (localhost:37777) 或 MCP 工具查看观察记录。

---

## 故障排除

### "Claude-Mem Java backend unreachable"

```bash
# 检查 Java 后端是否运行
curl http://127.0.0.1:37777/actuator/health

# 检查端口
lsof -i :37777
```

### MEMORY.md 没有同步

- 确认 `syncMemoryFile` 配置为 `true`
- 检查 OpenClaw 日志中是否有错误
- 确认 Java 后端正常响应 `/api/context/inject` 请求

### 观察记录没有保存

- 检查 `tool_result_persist` 事件是否触发
- 确认工具名称不以 `memory_` 开头（这些工具被过滤）
- 查看 Java 后端日志

---

## 文件结构

```
java/
├── openclaw-plugin/           # OpenClaw 插件
│   ├── src/index.ts          # 插件主代码
│   ├── openclaw.plugin.json   # 插件配置
│   ├── package.json           # NPM 配置
│   └── dist/                  # 编译产物
├── cortexce/          # Spring Boot 后端
│   ├── src/main/java/...
│   ├── target/*.jar          # 编译产物
│   └── .env                  # API Keys 配置
└── proxy/                     # Claude Code Thin Proxy
    └── wrapper.js            # CLI Wrapper
```

---

## 快速测试命令

### 测试后端连接

```bash
# 使用 /claude-mem-status 命令
/claude-mem-status
```

### 测试项目列表

```bash
# 使用 /claude-mem-projects 命令
/claude-mem-projects
```

### 测试 API 端点

```bash
# 健康检查
curl http://127.0.0.1:37777/actuator/health

# 项目列表
curl http://127.0.0.1:37777/api/projects

# Timeline 注入
curl "http://127.0.0.1:37777/api/context/inject?projects=openclaw"
```

---

## 后续优化

- [ ] 添加 SSE 观察流支持（需要独立服务）
- [ ] 支持更多消息渠道（Telegram/Discord）
- [ ] 添加 WebUI 内嵌视图

---

## 相关文档

| 文档 | 说明 |
|------|------|
| `docs/drafts/claude-mem-openclaw-support-analysis.md` | OpenClaw 支持深度分析 |
| `java/CLAUDE.md` | Java Port 完整文档 |
| `java/proxy/CLAUDE-CODE-INTEGRATION.md` | Claude Code 集成指南 |
