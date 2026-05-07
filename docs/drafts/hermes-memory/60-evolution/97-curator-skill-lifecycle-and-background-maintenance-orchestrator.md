# Curator — 技能生命周期管理与后台维护编排器

**日期**：2026-05-07  
**上游文件**：`agent/curator.py`（1674 行）

**相关已有文档**：无独立文档覆盖 curator.py；doc [`78`](../78-cross-cutting-architectural-patterns-synthesis.md)（Cross-cutting 模式总览）提及后台维护但无详细分析；doc [`96`](96-hermes-memory-architecture-synthesis-and-ce-roadmap.md)（架构综合）提及 Curator 作为 Skill 生命周期管理工具但未展开

---

## 1. 概述：Curator 是什么

Curator 是 Hermes Agent 的**后台技能维护编排器**，运行在辅助模型（auxiliary model）上。它不是会话记忆系统，而是对 agent 创建的技能（skills）进行周期性评审和维护。

**核心职责**：
- 基于时间戳自动转换技能生命周期状态（active → stale → archived）
- 生成评审 agent 对技能库进行"伞形化"（umbrella-building）整合
- 持久化 curator 状态（`last_run_at`、paused 等）

**关键设计原则**：
- **绝不自动删除**——只归档（archive），归档后可恢复
- **仅触碰 agent 创建的技能**——不碰 bundled/hub-installed 技能
- **固定技能绕过所有自动转换**
- **使用辅助客户端**——不干扰主会话的 prompt cache

---

## 2. 调度机制：Inactivity-Triggered 非 Cron

### 2.1 与 cron 的本质区别

大多数后台任务使用 cron 定时调度，但 Curator 使用**空闲触发**（inactivity-triggered）机制：

```python
DEFAULT_INTERVAL_HOURS = 24 * 7   # 7 天
DEFAULT_MIN_IDLE_HOURS = 2        #  agent 需空闲 2 小时
DEFAULT_STALE_AFTER_DAYS = 30     # 30 天无活动 → stale
DEFAULT_ARCHIVE_AFTER_DAYS = 90   # 90 天无活动 → archived
```

Curator 的调度检查**不在 cron daemon**中运行，而是每当 agent 空闲且距上次运行超过 `interval_hours` 时，由 `maybe_run_curator()` 启动分叉的评审 agent。

### 2.2 `should_run_now()` 门控逻辑

```python
def should_run_now(now: Optional[datetime] = None) -> bool:
    if not is_enabled():
        return False
    if is_paused():
        return False

    state = load_state()
    last = _parse_iso(state.get("last_run_at"))

    if last is None:
        # 首次运行：仅播种状态，不立即执行
        # 等一个完整 interval 再跑第一次
        state["last_run_at"] = now.isoformat()
        state["last_run_summary"] = "deferred first run..."
        save_state(state)
        return False

    return (now - last) >= interval
```

**首次运行行为**：新安装或更新后，不立即运行。等一个完整 interval（7天），让技能库积累足够的活动数据。强制立即运行用 `hermes curator run`。

### 2.3 与 BlueCortexCE 的关联

CE 目前没有后台维护调度系统。借鉴 Curator 的空闲触发模式可以实现：
- **自动会话归档**：会话 N 天无活动 → 触发后台总结 + 归档
- **自动观察合并**：相似的长期 observation 定期合并
- **自动过时内容清理**：Observation/Summary 超过一定时间后降级或删除

---

## 3. 生命周期状态机：纯函数自动转换

### 3.1 三状态转换

Curator 在 `apply_automatic_transitions()` 中实现了一个**无 LLM 的纯函数**状态机：

```
ACTIVE ──(anchor ≤ stale_cutoff)──→ STALE
  ↑                                 │
  └──(anchor > stale_cutoff)────────┘  (重新使用后恢复)

ACTIVE ──(anchor ≤ archive_cutoff)──→ ARCHIVED
STALE  ──(anchor ≤ archive_cutoff)──→ ARCHIVED
```

其中 `anchor = last_activity_at or created_at`，带时区感知的 UTC 比较。

### 3.2 关键代码

```python
def apply_automatic_transitions(now: Optional[datetime] = None) -> Dict[str, int]:
    stale_cutoff = now - timedelta(days=get_stale_after_days())
    archive_cutoff = now - timedelta(days=get_archive_after_days())

    for row in _u.agent_created_report():
        if row.get("pinned"):
            continue  # 固定技能绕过所有转换

        anchor = last_activity or created_at or now
        if anchor <= archive_cutoff and current != STATE_ARCHIVED:
            _u.archive_skill(name)   # 移入 .archive/
            counts["archived"] += 1
        elif anchor <= stale_cutoff and current == STATE_ACTIVE:
            _u.set_state(name, STATE_STALE)
            counts["marked_stale"] += 1
        elif anchor > stale_cutoff and current == STATE_STALE:
            _u.set_state(name, STATE_ACTIVE)  # 重新使用后恢复
            counts["reactivated"] += 1
```

### 3.3 归档策略：只移不删

```python
def archive_skill(name: str) -> tuple[bool, str]:
    # 1. 移动技能目录到 ~/.hermes/skills/.archive/<name>/
    # 2. 更新 skill_usage 表的 state = STATE_ARCHIVED
    # 3. 保留所有文件，可通过 skill_manage 恢复
```

Curator 永不删除技能。即使是 prune（无吸收目标的真正过时技能）也只归档。

---

## 4. 伞形化评审 Agent：LLM 驱动的技能整合

### 4.1 评审提示词设计

Curator 的核心是 `CURATOR_REVIEW_PROMPT`，指导 LLM 进行**伞形化整合**（umbrella-building consolidation）：

**目标**：将窄技能（one-session-one-skill）整合为宽技能（class-level umbrella with subsections）

**三种整合方式**：
1. **MERGE INTO EXISTING UMBRELLA** — 集群中已有足够宽的技能作为伞
2. **CREATE A NEW UMBRELLA** — 无合适伞，需新建 class-level 技能
3. **DEMOTE TO REFERENCES/TEMPLATES/SCRIPTS** — 内容窄但有价值，作为伞的支持文件保留

### 4.2 评审输出格式

LLM 必须输出：
1. **人类可读摘要**：描述处理的集群、补丁、决策
2. **结构化 YAML 块**：
```yaml
consolidations:
  - from: <old-skill>
    into: <umbrella-skill>
    reason: <为何合并>
prunings:
  - name: <skill-name>
    reason: <为何归档>
```

Curator 用 `_parse_structured_summary()` 解析 YAML，与启发式分类交叉验证。

---

## 5. 原子状态持久化：`.curator_state` 写入安全

### 5.1 Temp File + fsync + Atomic Replace

```python
def save_state(data: Dict[str, Any]) -> None:
    path = _state_file()
    fd, tmp = tempfile.mkstemp(
        dir=str(path.parent),
        prefix=".curator_state_",
        suffix=".tmp"
    )
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, sort_keys=True, ensure_ascii=False)
            f.flush()
            os.fsync(f.fileno())   # 强制刷入磁盘
        os.replace(tmp, path)        # 原子替换
    except BaseException:
        os.unlink(tmp)              # 失败时清理 temp 文件
        raise
```

这与 Hermes 的原子文件写入模式（doc [`95`](95-atomic-file-write-and-char-limit-design.md)）完全一致，是经过验证的安全写入模式。

### 5.2 与 doc 95 原子写入的对应关系

| 步骤 | Curator | doc 95 模式 |
|------|---------|-------------|
| 1 | `tempfile.mkstemp` | `temp_file = path.with_suffix('.tmp')` |
| 2 | `f.flush() + os.fsync()` | `f.flush() + os.fsync(f.fileno())` |
| 3 | `os.replace(tmp, path)` | `os.replace(temp_path, path)` |
| 4 | `except: os.unlink(tmp)` | `except: temp_path.unlink(missing_ok=True)` |

CE 落地：`ContextService` 的 `MEMORY.md` 写入应复用此模式（见 doc 95）。

---

## 6. 分类启发式：区分整合与修剪

Curator 需要区分两类移除：
- **Consolidation（整合）**：技能内容被吸收进伞形技能，仍存在
- **Pruning（修剪）**：技能内容无价值，纯粹归档

### 6.1 分类信号

通过解析本轮 `skill_manage` 工具调用，判断移除的技能是否被其他技能引用：

```python
# 如果某工具调用的 target（存活技能）在其 file_path/content 中引用了被移除技能名
# → consolidation
# 否则 → pruning
```

```python
def _classify_removed_skills(
    removed: List[str],
    after_names: Set[str],
    tool_calls: List[Dict[str, Any]],
) -> Dict[str, List[Dict[str, Any]]]:
    # 路径匹配：needle 必须是完整路径组件名
    # 内容匹配：word-boundary regex 防止误匹配
    # 目标必须仍在 destinations 中（存活或新增）
```

### 6.2 匹配策略

| 字段 | 匹配方式 | 防止误匹配 |
|------|---------|-----------|
| `file_path` | 完整路径组件名（stem 或目录名） | "api" 不匹配 "references/api-design.md" |
| `content`/`file_content` | word-boundary regex `\b<needle>\b` | "test" 不匹配 "latest"/"testing" |
| `_raw` | substring fallback | truncate 时降级为字符串搜索 |

---

## 7. 报告系统：结构化输出 + 人类可读摘要

### 7.1 报告目录结构

```
~/.hermes/logs/curator/{YYYYMMDD-HHMMSS}/
├── run.json       # 机器可读的结构化数据
└── REPORT.md      # 人类可读摘要
```

### 7.2 运行模式

- **Dry-run**（`hermes curator run --dry-run`）：预览，不执行任何写入/归档操作
- **Live run**（`hermes curator run`）：执行真实操作
- **自动触发**（`maybe_run_curator`）：空闲时触发，只在 `should_run_now()` 为 true 时运行

---

## 8. 与 BlueCortexCE 的关联：后台维护编排器设计

### 8.1 CE 目前缺失的能力

CE 没有后台维护系统。所有生命周期管理都是手动或请求时触发。借鉴 Curator 模式可以实现：

### 8.2 建议的 CE 后台维护组件

#### 8.2.1 Observation 自动归档

```python
# CE 中的 Curator 类似组件（伪代码）
class MemoryMaintenanceScheduler:
    """
    调度规则：
    - OBSERVATION_STALE_AFTER_DAYS = 30
    - OBSERVATION_ARCHIVE_AFTER_DAYS = 90
    - ARCHIVE = 降级为 Summary（保留语义，删除原始 embedding）
    """
    def apply_automatic_observation_transitions(self, now: datetime):
        stale_cutoff = now - timedelta(days=30)
        # 30天无引用的 observation → 标记为 stale
        # 90天无引用的 observation → 转为 summary 或降级
```

#### 8.2.2 相似 Observation 自动合并

类似 Curator 的伞形化逻辑，CE 可以定期：
1. 聚类相似的 observations
2. 合并为更高层次的 summary
3. 原始 observation 降级为 reference

#### 8.2.3 Session 自动过期刷新

```python
def on_session_finalize(session_id: str, messages: list):
    """会话结束时触发后台总结 + 入库"""
    # 与 Curator 的 review agent 类似
    # 但在会话结束时同步触发，不等待空闲
```

### 8.3 关键架构模式提取

| 模式 | Curator 实现 | CE 借鉴 |
|------|------------|---------|
| 空闲触发调度 | `should_run_now()` + idle hours | `session_idle_checker` |
| 纯函数状态转换 | `apply_automatic_transitions()` | `ObservationLifecycleManager` |
| LLM 驱动的整合 | `CURATOR_REVIEW_PROMPT` | `MemoryConsolidationPrompt` |
| 原子状态持久化 | temp+fsync+replace | `MemoryStateManager` |
| Dry-run 模式 | `DRY_RUN_BANNER` | 所有维护操作支持 dry-run |
| 结构化+人类双输出 | `run.json` + `REPORT.md` | API response + human summary |

---

## 9. 代码质量评估

| 维度 | 评分 | 说明 |
|------|------|------|
| **线程安全** | ✅ 优秀 | 无共享可变状态；`maybe_run_curator` 在分叉的 AIAgent 中运行 |
| **内存管理** | ✅ 优秀 | 1674 行，无明显泄漏；分叉进程隔离内存 |
| **输入验证** | ✅ 良好 | `_parse_iso` 容错；`_default_state()` 提供安全默认值 |
| **错误处理** | ✅ 优秀 | 所有文件操作在 `try/except` 中；temp 文件在 `except BaseException` 中清理 |
| **可观测性** | ✅ 优秀 | 报告系统完整（run.json + REPORT.md）；`counts` 字典跟踪每个操作 |
| **配置管理** | ✅ 优秀 | `curator.*` config namespace；容错回退到默认值 |

---

## 10. 维护信息

- **文件**：`agent/curator.py`
- **大小**：1674 行，~62KB（估算）
- **Owner**：PM Agent（后台维护）
- **更新频率**：随上游 curator.py 变更更新；定期巡检新 commit
- **最后审查**：2026-05-07（v14.0，本文档首次记录）
