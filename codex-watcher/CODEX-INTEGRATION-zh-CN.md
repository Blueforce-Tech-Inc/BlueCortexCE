# Codex CLI 集成

**版本：** 1.0.0
**日期：** 2026-04-16
**项目：** BlueCortexCE (Java Backend)

---

## 概述

本文档描述了如何将 Codex CLI 与 BlueCortexCE 记忆系统集成。Codex CLI 会话通过 transcript watching 进行监控，观察结果记录到 Java 后端。

## 架构

```
~/.codex/sessions/**/*.jsonl
        │
        ▼ (codex-watcher)
BlueCortexCE Backend (localhost:37777)
        │
        ▼
PostgreSQL 数据库 + 上下文注入
```

## 组件

### 1. OpenClaw 插件增强

位于 `openclaw-plugin/`，此插件已增强以下功能：

- **熔断器模式**：后端不可达时防止 CPU 空转
  - 阈值：3 次连续失败
  - 冷却时间：30 秒
  - 冷却后自动恢复探测

- **上下文缓存**：60s TTL 的 `before_prompt_build` 上下文注入缓存
  - 减少冗余 API 调用
  - 在 `gateway_start` 时自动清除

- **搜索命令**：
  - `/claude-mem-search <query> [limit]` - 搜索观察结果
  - `/claude-mem-recent [project] [limit]` - 最近上下文
  - `/claude-mem-timeline <query> [depthBefore] [depthAfter]` - 时间线查询

### 2. Codex Watcher

位于 `codex-watcher/`，此 Node.js CLI 工具监控 Codex CLI 会话文件：

- **文件监控**：使用 `chokidar` 监控 `~/.codex/sessions/**/*.jsonl`
- **JSONL 解析**：将 Codex 会话事件解析为结构化格式
- **API 集成**：将观察结果记录到 Java 后端
- **安装器**：管理 Codex transcript watch 配置

## 设置

### 前置条件

1. Java 后端运行在端口 37777
2. Node.js 18+

### OpenClaw 插件

```bash
cd openclaw-plugin
npm install
npm run build
```

### Codex Watcher

```bash
cd codex-watcher
npm install
npm run build

# 安装 Codex watcher 配置
node dist/index.js install

# 开始监控
node dist/index.js start
```

## 配置

### 环境变量

| 变量 | 默认值 | 描述 |
|----------|---------|-------------|
| `CLAUDE_MEM_BACKEND_URL` | `http://127.0.0.1:37777` | Java 后端 URL |
| `CLAUDE_MEM_PROJECT` | `codex` | Codex 会话的项目名称 |

### OpenClaw 插件配置

在 `openclaw.plugin.json` 中：

```json
{
  "id": "claude-mem-java",
  "config": {
    "workerPort": 37777,
    "project": "openclaw"
  }
}
```

## Codex 事件

| 事件类型 | 操作 | 后端 API |
|------------|--------|-------------|
| `session_meta` | session_context | POST /api/session/start |
| `turn_context` | session_context | POST /api/session/start |
| `user_message` | session_init | POST /api/session/start |
| `assistant_message` | assistant_message | (仅记录) |
| `function_call` | tool_use | POST /api/ingest/tool-use |
| `function_call_output` | tool_result | POST /api/ingest/tool-use |
| `turn_aborted` | session_end | POST /api/ingest/session-end |

## 命令

### Codex Watcher

```bash
# 开始监控
node dist/index.js start

# 安装配置
node dist/index.js install

# 卸载配置
node dist/index.js uninstall

# 检查状态
node dist/index.js status

# 显示帮助
node dist/index.js help
```

### OpenClaw 命令

```bash
# 检查后端健康状态
/claude-mem-status

# 列出项目
/claude-mem-projects

# 搜索观察结果
/claude-mem-search <query> [limit]

# 最近上下文
/claude-mem-recent [project] [limit]

# 时间线查询
/claude-mem-timeline <query> [depthBefore] [depthAfter]
```

## 故障排除

### 后端不可达

如果后端不可达，熔断器将：
1. 3 次失败后：打开熔断器（丢弃所有调用）
2. 30 秒冷却后：以 HALF_OPEN 状态探测
3. 成功后：关闭熔断器

检查后端健康状态：
```bash
curl http://127.0.0.1:37777/actuator/health
```

### 未检测到会话

1. 确认 Codex CLI 已安装且已使用
2. 验证会话存在：`ls ~/.codex/sessions/`
3. 使用调试输出运行 watcher

### 安装问题

1. 确保 `~/.claude-mem/` 目录可写
2. 检查 Node.js 权限
3. 验证 `chokidar` 依赖已安装

## 文件结构

```
codex-watcher/
├── package.json
├── tsconfig.json
├── src/
│   ├── index.ts       # 主入口
│   ├── api.ts         # 后端 API 客户端
│   ├── events.ts      # Codex 事件类型
│   ├── watcher.ts     # 文件监控器
│   └── installer.ts   # 配置安装器
└── dist/              # 编译输出
```

## 相关文档

- [OpenClaw 集成](../openclaw-plugin/OPENCLAW-INTEGRATION-zh-CN.md)
- [Java 后端 API](../backend/src/main/java/com/ablueforce/cortexce)
