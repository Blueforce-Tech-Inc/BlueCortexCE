# OpenClaw 集成配置指南

本文档说明如何将 Claude-Mem Java 后端集成到 OpenClaw Gateway 中。

> ✅ **已验证** — 插件已在 OpenClaw Gateway 中成功加载并运行 (2026-03-31)。

---

## 集成架构概述

Claude-Mem 与 OpenClaw 的集成分为**两层**：

```
┌─────────────────────────────────────────────────────────────────────┐
│                    OpenClaw + Claude-Mem 集成架构                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Layer 1: 记忆捕获（Plugin - 自动）                                   │
│  ├── OpenClaw 插件监听 8 个生命周期事件                               │
│  ├── 自动记录工具使用为 Observation                                   │
│  └── 用户无需任何操作                                                │
│                                                                      │
│  Layer 2: 主动搜索（Skill - 按需）                                   │
│  ├── AgentSkills 兼容的 SKILL.md 文件                                │
│  ├── Agent 根据用户问题自动判断是否需要搜索记忆                        │
│  ├── 通过 REST API 调用 Java 后端进行语义搜索                         │
│  └── 无需 MCP 协议（OpenClaw 创始人反对 MCP）                         │
│                                                                      │
│  上下文注入机制：                                                     │
│  ├── before_prompt_build 钩子 → appendSystemContext                 │
│  ├── 通过系统提示词注入记忆上下文                                      │
│  └── 每次 LLM 调用前自动获取最新上下文                                │
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

> **注意**：OpenClaw 使用 `appendSystemContext` 机制注入记忆上下文，而非文件同步方式。

---

## 前置条件

### 1. 编译 OpenClaw 插件

```bash
# 进入 openclaw-plugin 目录
cd /path/to/your/BlueCortexCE/openclaw-plugin

npm install
npm run build
```

### 2. 启动 Java 后端

确保 Java 后端在 `localhost:37777` 运行。有多种启动方式：

```bash
# 方式一：直接运行 JAR
cd /path/to/your/BlueCortexCE/backend
export OPENAI_API_KEY=your_api_key
export SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key
java -jar target/cortex-ce-0.1.0-beta.jar --spring.profiles.active=dev &

# 方式二：Docker 运行
docker compose -f /path/to/your/BlueCortexCE/docker-compose.yml up -d
```

验证后端已启动:

```bash
curl http://127.0.0.1:37777/actuator/health
# 应返回: {"status":"UP",...}
```

---

## Layer 1: 插件集成（记忆捕获）

OpenClaw 支持三种插件集成方式，选择其中一种即可。

### 两个配置文件的关系

| 文件 | 位置 | 作用 |
|------|------|------|
| `openclaw.plugin.json` | 插件目录内 | **插件清单**：定义插件 ID、名称、配置 schema（不接受用户配置值） |
| OpenClaw 主配置文件 | `~/.openclaw/openclaw.json` | **用户配置**：启用插件、提供具体配置值 |

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
│  │ plugins.entries."claude-mem-java".config: {                   │  │
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
mkdir -p ~/.openclaw/extensions/claude-mem-java

# 复制必要文件
cp /path/to/your/BlueCortexCE/openclaw-plugin/openclaw.plugin.json ~/.openclaw/extensions/claude-mem-java/
cp /path/to/your/BlueCortexCE/openclaw-plugin/dist/index.js ~/.openclaw/extensions/claude-mem-java/
```

**优点**：无需修改 OpenClaw 配置文件，使用 `configSchema` 中定义的默认值。

**验证**：
```bash
openclaw plugins list          # 应显示 claude-mem-java
openclaw plugins doctor        # 检查是否有错误
```

---

### 方式二：通过配置文件指定路径

在 OpenClaw 配置文件中指定插件路径和配置值。

**配置文件位置**：`~/.openclaw/openclaw.json`

```json
{
  "plugins": {
    "allow": ["claude-mem-java"],
    "entries": {
      "claude-mem-java": {
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

**`plugins.allow`**: 显式声明信任的插件 ID，消除 Gateway 启动时的 "untracked local code" 警告。

**优点**：可以覆盖默认配置值，适合需要自定义配置的场景。

**注意**：`load.paths` 指向包含 `openclaw.plugin.json` 的目录。

---

### 方式三：命令行安装

使用 OpenClaw CLI 命令安装。

```bash
# 从本地目录安装
openclaw plugins install /path/to/your/BlueCortexCE/openclaw-plugin

# 安装后启用
openclaw plugins enable claude-mem-java

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

---

### 集成方式对比

| 方式 | 复杂度 | 适用场景 |
|------|--------|----------|
| 方式一：自动发现 | ⭐ 简单 | 开发测试、使用默认配置 |
| 方式二：配置文件 | ⭐⭐ 中等 | 需要自定义配置、多环境管理 |
| 方式三：命令安装 | ⭐⭐ 中等 | 生产环境、版本管理 |

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

# 复制 Skill 文件（从项目目录）
cp -r /path/to/your/BlueCortexCE/openclaw-plugin/skills/claude-mem-search ~/.openclaw/skills/
```

**方式二：项目级 Skill**

```bash
# 在项目根目录创建 skills 目录
mkdir -p /path/to/your-project/skills

# 复制 Skill 文件
cp -r /path/to/your/BlueCortexCE/openclaw-plugin/skills/claude-mem-search /path/to/your-project/skills/
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

## Skill 文件来源

Skill 文件位于本项目：

| 位置 | 说明 |
|------|------|
| 源码目录 | `openclaw-plugin/skills/claude-mem-search/SKILL.md` |
| 安装后 | `~/.openclaw/skills/claude-mem-search/SKILL.md` |

**注意**：不要复制 SKILL.md 的全部内容到本集成文档中——AgentSkills 系统会自动按需加载 Skill 文件的完整内容。只需确保 Skill 文件正确安装到上述位置即可。

详见 [SKILL.md](skills/claude-mem-search/SKILL.md) 查看完整的三步记忆检索工作流和 API 调用示例。

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

插件监听 OpenClaw Gateway 的 8 个生命周期事件：

| 事件 | 时机 | 插件行为 |
|------|------|----------|
| `session_start` | 用户发起新会话 (`/new`, `/reset`) | 初始化 claude-mem 会话 |
| `after_compaction` | 上下文压缩后 | 重新初始化会话 |
| `before_agent_start` | Agent 执行前 | 跟踪工作区目录 |
| `before_prompt_build` | 每次 LLM 调用前 | **通过 appendSystemContext 注入记忆上下文** |
| `tool_result_persist` | 工具执行后 | 记录观察 |
| `agent_end` | Agent 执行结束 | 生成摘要 + 完成会话 |
| `session_end` | 会话结束 | 清理会话跟踪 |
| `gateway_start` | Gateway 启动 | 重置会话跟踪 |

---

## 上下文注入机制

### appendSystemContext 工作流程

```
1. 每次 LLM 调用前
       ↓
2. OpenClaw 调用 before_prompt_build 钩子
       ↓
3. 插件调用 /api/context/inject 获取上下文
       ↓
4. 返回 { appendSystemContext: context }
       ↓
5. OpenClaw 将上下文附加到系统提示词末尾
       ↓
6. LLM 接收包含记忆上下文的完整提示词
```

### 与文件同步方式的区别

| 方面 | 旧方式（MEMORY.md 文件同步） | 当前方式（appendSystemContext） |
|------|---------------------------|--------------------------------|
| 更新机制 | 文件写入 | 系统提示词注入 |
| 实时性 | Agent 启动时/工具使用后 | 每次 LLM 调用前 |
| 竞态条件 | 存在（文件读写竞争） | 无 |
| 复杂性 | 高（路径、权限、文件操作） | 低（直接 API 调用） |

---

## API 端点映射

插件通过 HTTP 调用 Java 后端 API：

| 功能 | 插件调用端点 |
|------|-------------|
| 会话初始化 | `/api/session/start` |
| 记录工具使用 | `/api/ingest/tool-use` |
| 会话完成 | `/api/ingest/session-end` |
| 获取 Timeline | `/api/context/inject` |

---

## 与 TypeScript 版本对比

| 特性 | TypeScript 版本 | Java 版本 |
|------|---------------|----------|
| 后端 | TypeScript Worker | Java Spring Boot |
| SSE 支持 | ✅ 有 | ❌ 无 |
| MEMORY.md | ✅ | ✅ |
| 观察记录 | ✅ | ✅ |
| 命令 | 不同 | `/claude-mem-status`, `/claude-mem-projects` |

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

### 记忆上下文没有注入

- 确认 Java 后端正常响应 `/api/context/inject` 请求
- 检查 OpenClaw 日志中是否有 `[claude-mem] Context injected` 记录
- 确认 `/api/context/inject` 返回非空上下文

### 观察记录没有保存

- 检查 `tool_result_persist` 事件是否触发
- 确认工具名称不以 `memory_` 开头（这些工具被过滤）
- 查看 Java 后端日志

---

## 文件结构

```
BlueCortexCE/                          # 项目根目录
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/...
│   ├── target/
│   │   └── cortex-ce-0.1.0-beta.jar  # 编译产物
│   └── .env                          # API Keys 配置
├── openclaw-plugin/                  # OpenClaw 插件
│   ├── src/index.ts                  # 插件主代码
│   ├── openclaw.plugin.json          # 插件配置
│   ├── package.json                  # NPM 配置
│   ├── skills/
│   │   └── claude-mem-search/        # Skill 文件
│   │       └── SKILL.md
│   └── dist/                         # 编译产物
└── proxy/                            # Claude Code Thin Proxy
    └── wrapper.js                    # CLI Wrapper
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
| `backend/README.md` | Java 后端文档 |
| `proxy/CLAUDE-CODE-INTEGRATION.md` | Claude Code 集成指南 |
