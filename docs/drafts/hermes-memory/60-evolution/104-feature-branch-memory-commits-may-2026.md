# 104: Feature-Branch 记忆系统 Commits（2026-05 上半月）

**日期**：2026-05-07
**分支状态**（本地 ~/.hermes/hermes-agent）：
- 本地已同步至 `origin/main` (`49c3c2e0d`)
- 相比上次扫描基准 `53a024994`，`origin/main` 新增 7 commits，**0 记忆系统相关**
- 以下重要 commits 位于 feature branches，**尚未合入 origin/main**

> ⚠️ 这些是 feature branch commits，不是 origin/main 的一部分。未来 merge 后将合入常规扫描。

---

## P0：`243681a8e` — Compression 后 cached agent 未清除

**分支**：`fix/terminal-safety-filter-false-positives`（未合入）
**日期**：2026-05-02
**影响**：⭐⭐⭐ P0

### 问题

```
Compression creates a tmp_agent to do the work, but the gateway's
agent cache still holds the old agent with its stale _cached_system_prompt.
SOUL.md edits, memory updates, config changes — all invisible until manual /new.
```

压缩使用临时 agent 完成工作，但 gateway 的 agent cache 仍持有旧的 `_cached_system_prompt`。后果：
- SOUL.md 编辑后不生效
- Memory 更新后不生效  
- Config 更改后不生效
- 必须手动 `/new` 才能刷新

### 修复

```python
# gateway/run.py - 新增 9 行
# Both session hygiene and /compress evict the cached agent,
# forcing a fresh build from current files on the next turn.
```

**修复代码**（`gateway/run.py`）：
- session hygiene 触发时 evict cached agent
- `/compress` 触发时 evict cached agent
- 下一次 turn 时从当前文件重新构建

### CE 借鉴

**相关已有发现**：CE `ContextService` 的 cached agent 问题已在 doc 92 中记录（`aa88dcc57` memory authority 降级 + 压缩后未清除双重问题）。

**CE 当前状态**：
- CE 是 stateless REST service，无本地 agent cache
- 但 `/api/context/generate` 有 `updateFiles` 机制（proxy 层注入 `MEMORY.md` 等文件路径变化）
- **风险场景**：当 CE 的 memory banks 文件（Soul/User/Memory）在 context 生成期间被外部更新时，in-flight 请求可能读到 stale snapshot

**落地建议**：
- P1：在 `ContextService` 增加 `lastModified` 检查，对比 `updateFiles` 中记录的文件修改时间
- P1：`proxy/wrapper.js` 的 `updateFiles` 机制已部分覆盖此场景

---

## P0：`0a9d84dd0` — 保留 Context Compaction 后 Memory Authority

**分支**：unknown（未合入）
**日期**：2026-04-29
**影响**：⭐⭐⭐ P0（直接修复 doc 92 记录的 `aa88dcc57` P0）

### 问题（Doc 92 跟进）

Doc 92 记录的 `aa88dcc57` P0 发现：
- Context compaction 触发时，`SUMMARY_PREFIX` 指示模型将 summary 视为"background reference, NOT active instructions"
- 由于 memory 也在 system prompt 中，与 summary 同样被降级
- 导致 MEMORY.md、USER.md 在 compaction + session resume 后被模型忽略

### 修复详情

**文件变更**：`agent/context_compressor.py` + `agent/memory_manager.py`

**1. `SUMMARY_PREFIX` 增加显式声明**：
```python
"IMPORTANT: Your persistent memory (MEMORY.md, USER.md) in the system "
"prompt is ALWAYS authoritative and active — never ignore or deprioritize "
"memory content due to this compaction note. "
```

**2. System prompt compression note 更新**：
```python
# _compression_note 末尾追加：
"Your persistent memory (MEMORY.md, USER.md) remains fully authoritative "
"regardless of compaction."
```

**3. `build_memory_context_block` 措辞升级**：
```python
# 旧：
"Treat as informational background data."

# 新：
"Treat as authoritative reference data — "
"this is the agent's persistent memory and should inform all responses."
```

**4. 正则向后兼容**：
```python
# _INTERNAL_NOTE_RE 更新为匹配新旧措辞：
r'\[System note:\s*The following is recalled memory context,\s*NOT new user input\.\s*'
r'Treat as (?:informational background data|authoritative reference data[^\]]*)\.\]\s*'
```

**修复**: NousResearch/hermes-agent#17251

### CE 借鉴

**直接关联**：CE 的 StructuredExtractionService（Phase 3 核心）在 context 溢出时同样面临 compaction 场景。

**CE 当前状态**：
- CE 没有进程内 compaction（stateless），但 HTTP API 层在高并发时可能有 stale context 问题
- `ContextService.generateContext()` 的 `ICL_MAX_TOKENS` 截断机制等效于 Hermes 的 head+tail truncation
- Session resume 时 context 重建依赖 `SessionEntity.updateFiles`

**关键发现**：`_INTERNAL_NOTE_RE` 的 backward-compatible regex 设计值得借鉴。CE 在字段名迁移（camelCase → snake_case）时也应采用类似模式。

---

## `359e08d38` — Compaction role=user Fallback 边界标记

**分支**：unknown（未合入）
**日期**：2026-04-29
**影响**：⭐⭐ P1

### 问题

当 head 以 assistant/tool 结尾，tail 以 assistant 开头时，summary 被插入为独立的 `role="user"` message。此时 body 中的 `## Active Task` 引用被弱模型（weak/local）误读为新的用户输入（#11475, #14521）。

### 修复

在 `role="user"` fallback 的 summary 中标记 end of summary，防止 body 内容被误解析。

### CE 借鉴

CE 当前无此类问题（stateless，无 role=user 注入），但如未来引入多角色消息合成，应注意此边界。

---

## Feature Branch Commits 汇总表

| Commit | 分支 | 类型 | 影响 | CE 借鉴 |
|--------|------|------|------|---------|
| `243681a8e` | `fix/terminal-safety-filter-false-positives` | P0 fix | Cached agent post-compression | ⭐⭐⭐ P1: ContextService 文件变更检测 |
| `0a9d84dd0` | (未合入) | P0 fix | Memory authority preservation | ⭐⭐⭐ P1: backward-compatible regex 设计 |
| `359e08d38` | (未合入) | P1 fix | role=user summary boundary | ⭐ P3: 多角色消息合成边界 |
| `b6c53ef0b` | `codex-port/hook-output-spill` | feat | Hook output disk spill | 见 doc 94 对照 |
| `b0c84756b` | origin/main ✅ | fix(tui) | Memory tool previews one-line | 纯 TUI，无 CE 关联 |
| `1d24cb0e6` | origin/main ✅ | fix(tui) | Live render memory pressure | 纯 TUI，无 CE 关联 |

---

## 与 Doc 92 的关联

Doc 92（`92-upstream-aa88dcc57-memory-analysis.md`）记录了两个 P0：
1. **P0-a**：`aa88dcc57` 压缩后 cached agent 未清除 → `243681a8e` 修复
2. **P0-b**：`SUMMARY_PREFIX` 导致 memory authority 降级 → `0a9d84dd0` 修复

两个 P0 均已在 feature branch 修复，待 merge 到 origin/main。

---

## 下一步

- 跟踪 `243681a8e` 和 `0a9d84dd0` 合入 origin/main 的时间
- 合入后更新 doc 92 状态为 "fixed"
- 评估 CE 的 `ContextService` 是否需要类似的 agent cache eviction 机制
