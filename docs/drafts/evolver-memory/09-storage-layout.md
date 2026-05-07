# 9. 存储布局

## 9.1 目录结构

```
workspace/                         # getWorkspaceRoot()
├── memory/                        # getMemoryDir()
│   └── evolution/                 # getEvolutionDir()
│       ├── memory_graph.jsonl      # Append-only 事件图谱
│       ├── memory_graph_state.json # 当前状态快照（atomic 读写）
│       ├── reflection_log.jsonl   # 反思日志
│       └── evolution_narrative.md  # 人类可读叙事
└── logs/
    └── evolver_loop.log           # 运行时日志

workspace/assets/gep/             # getGepAssetsDir()
├── genes/                         # Gene 资产目录
├── capsules/                      # Capsule 资产目录
└── EVOLUTION_PRINCIPLES.md       # 进化原则文档
```

## 9.2 作用域隔离（Session Scope）

当 `EVOLVER_SESSION_SCOPE` 环境变量设置时，记忆数据按作用域隔离：

```js
// 例如：EVOLVER_SESSION_SCOPE=discord_channel_12345
function getEvolutionDir() {
  const baseDir = path.join(getMemoryDir(), 'evolution');
  const scope = getSessionScope();
  if (scope) return path.join(baseDir, 'scopes', scope);
  return baseDir;
}
```

**安全净化**：scope 值经过严格过滤，只允许 `[a-zA-Z0-9_\-\.]`，防止路径遍历。

**典型用途**：
- Discord 多频道隔离
- 多项目隔离
- 多租户场景

## 9.3 环境变量汇总

| 变量 | 默认值 | 用途 |
|------|--------|------|
| `MEMORY_DIR` | `workspace/memory` | 记忆根目录 |
| `EVOLUTION_DIR` | `MEMORY_DIR/evolution` | 进化目录 |
| `MEMORY_GRAPH_PATH` | `evolution/memory_graph.jsonl` | 覆盖图谱路径 |
| `MEMORY_GRAPH_PROVIDER` | `local` | `local` 或 `remote` |
| `MEMORY_GRAPH_REMOTE_URL` | — | 远程 KG 服务地址 |
| `MEMORY_GRAPH_REMOTE_KEY` | — | 远程认证密钥 |
| `MEMORY_GRAPH_REMOTE_TIMEOUT_MS` | `5000` | 远程超时 |
| `EVOLVER_SESSION_SCOPE` | — | 作用域隔离标识 |
| `EVOLVER_REPO_ROOT` | 自动检测 | 仓库根目录 |
| `OPENCLAW_WORKSPACE` | 自动检测 | 工作区根目录 |
| `EVOLVER_TRACE_LEVEL` | `minimal` | `none` / `minimal` / `standard` |
| `SEMANTIC_MATCH_WEIGHT` | `0.4` | 语义匹配权重 |

## 9.4 文件操作原子性

State 文件（`memory_graph_state.json`）使用 **写-重命名** 原子模式：

```js
function writeJsonAtomic(filePath, obj) {
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n');
  fs.renameSync(tmp, filePath); // 原子替换
}
```

这避免了写入中途崩溃导致文件损坏的问题。

## 9.5 Tail-Only 读取优化

```js
function tryReadMemoryGraphEvents(limitLines = 2000) {
  const TAIL_BYTES = 512 * 1024; // 512KB
  // 大文件：从末尾读取 512KB，跳到第一个换行符，取最后 2000 行
}
```

对 GB 级日志文件，这避免了全量加载到内存。
