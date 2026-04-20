# 60-evolution/14-multi-provider-plugin-discovery.md

# Multi-Provider Plugin Discovery 系统分析

> **来源**：Hermes Agent `plugins/memory/__init__.py`
> **快照时间**：2026-04-19
> **定位**：60-evolution 子稿，交叉索引 `12`/`13`

---

## 1. 设计背景

Hermes Agent 的记忆系统采用**插件化多 Provider 架构**，允许在运行时选择不同的记忆后端。记忆 Provider 与通用插件系统独立，位于 `plugins/memory/<name>/`，无需用户额外安装，始终可用。**同一时刻只能有一个活跃 Provider**，通过 `config.yaml` 的 `memory.provider` 字段选择。

---

## 2. 核心组件

### 2.1 `discover_memory_providers()` — 轻量级发现

```python
def discover_memory_providers() -> List[Tuple[str, str, bool]]:
    """扫描 plugins/memory/ 查找可用 Provider。
    
    Returns: [(name, description, is_available), ...]
    不导入 Provider，只读 plugin.yaml 做元数据 + 调用 is_available() 做可用性检查。
    """
```

**关键设计点**：
- **不导入模块本体**（只读 `plugin.yaml` + 轻量 `is_available()` 检查），避免加载开销
- 返回可用性布尔值，允许 UI 层展示"未配置 API Key"等提示
- 描述从 `plugin.yaml` 读取，支持国际化

### 2.2 `load_memory_provider()` — 按名加载

```python
def load_memory_provider(name: str) -> Optional[MemoryProvider]:
    """加载并返回指定名称的 MemoryProvider 实例。"""
```

**加载策略（双重fallback）**：

| 模式 | 说明 |
|------|------|
| `register(ctx)` 插件风格 | 模拟 `_ProviderCollector` ctx，捕获 `register_memory_provider()` 调用 |
| MemoryProvider 子类 | 找到继承 `MemoryProvider` 的类，直接实例化 |

```python
class _ProviderCollector:
    """Fake plugin context that captures register_memory_provider calls."""
    def __init__(self):
        self.provider = None
    def register_memory_provider(self, provider):
        self.provider = provider
    # No-op for other registration methods
    def register_tool(self, *args, **kwargs): pass
    def register_hook(self, *args, **kwargs): pass
    def register_cli_command(self, *args, **kwargs): pass
```

### 2.3 子模块预注册

加载 Provider 模块时，会**预注册同级 `.py` 文件为子模块**，使相对导入生效：

```python
# 例如 holographic/store.py, holographic/retrieval.py
for sub_file in provider_dir.glob("*.py"):
    if sub_file.name != "__init__.py":
        full_sub_name = f"plugins.memory.{name}.{sub_file.stem}"
        # 立即 exec_module 到 sys.modules
```

---

## 3. 活跃 Provider 读取

```python
def _get_active_memory_provider() -> Optional[str]:
    """从 config.yaml 读取活跃 Provider 名称（轻量，只读配置，不加载插件）。"""
```

- 读取路径：`config.yaml` → `memory.provider`
- 纯配置读取，不触发任何插件加载
- CLI 初始化阶段（argparse）即可调用

---

## 4. CLI 命令发现

```python
def discover_plugin_cli_commands() -> List[dict]:
    """仅为活跃 Provider 注册 CLI 命令（只导入 cli.py，轻量）。"""
```

- 只加载活跃 Provider 的 `cli.py`，其他跳过
- 返回结构：`{name, help, description, setup_fn, handler_fn, plugin}`
- 允许记忆 Provider 提供独立的 CLI 子命令（如 `honcho memory setup`）

---

## 5. 可用 Provider 清单

| Provider | 定位 | 关键特性 |
|----------|------|----------|
| **honcho** | 本地 Honcho 云 API | 4 tool schema，profile/search/context/remember；本地 SQLite 缓冲 |
| **supermemory** | Supermemory.ai 云 API | 语义长期记忆，实体抽取，session 级对话注入 |
| **mem0** | Mem0 云 API | （轻量占位，README only） |
| **holographic** | 本地 SQLite（HRR） | `fact_store` + `fact_feedback`；信任评分，矛盾检测，HRR 组合检索 |
| **retaindb** | RetainDB 云 API | 写缓冲队列（SQLite），文件存储，dialectic 合成，SOUL.md 自模型 |
| **openviking** | Volcengine DB | 文件系统层级，viking:// URI，L0/L1/L2 tiered context，atexit 安全网；详见 [`18`](18-three-new-memory-providers.md) |
| **byterover** | ByteRover CLI | 层级 Context Tree，模糊+LLM 检索，超时分级（query 10s / curate 120s）；详见 [`18`](18-three-new-memory-providers.md) |
| **hindsight** | 知识图谱 | 实体消解，多策略检索，Reflect 综合推理，本地嵌入 daemon，Bank Mission；详见 [`18`](18-three-new-memory-providers.md) |

---

## 6. 与 CE 的对照

| 维度 | Hermes | BlueCortexCE |
|------|--------|--------------|
| 插件发现 | `discover_memory_providers()` 扫描目录 | 无（硬编码集成） |
| 活跃选择 | `config.yaml memory.provider` | 无（单一实现） |
| 多 Provider 同时 | 不支持（单活跃） | 支持多路复用 |
| CLI 集成 | `discover_plugin_cli_commands()` | 无独立 CLI |
| 相对导入支持 | 子模块预注册到 `sys.modules` | N/A |

### 借鉴价值

1. **`discover_*` 模式**：BlueCortexCE 可引入轻量级 Provider 发现机制，通过扫描 `extensions/` 目录自动注册
2. **`register(ctx)` 模式**：CE 的 Feishu 等扩展已采用类似 Collector 模式，可进一步泛化
3. **is_available 分离**：Provider 加载前先做可用性检查，避免运行时错误
4. **CLI 插件发现**：CE 的 OpenClaw CLI 可借鉴，按channel插件动态注册子命令

---

## 7. 关键代码路径

```
plugins/memory/__init__.py
├── discover_memory_providers()      # 扫描入口
├── load_memory_provider(name)       # 加载入口
├── _load_provider_from_dir(path)    # 内部加载器（register + subclass 双模式）
├── _ProviderCollector              # 模拟 ctx，捕获 register_memory_provider
├── _get_active_memory_provider()    # 读配置
└── discover_plugin_cli_commands()   # CLI 发现
```

---

## 8. 待跟进

- [x] `openviking` / `byterover` / `hindsight` Provider 分析 → 新增 [`18-three-new-memory-providers.md`](18-three-new-memory-providers.md)
- [ ] Provider `plugin.yaml` 元数据规范是否已稳定？
- [ ] 多 Provider 切换时 `session_id` / `user_id` 迁移路径未覆盖
