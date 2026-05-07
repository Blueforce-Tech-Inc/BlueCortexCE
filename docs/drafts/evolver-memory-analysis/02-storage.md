# 02 — 存储设计

## 2.1 文件布局

```
${EVOLUTION_DIR}/
├── memory_graph.jsonl          # 追加日志（只追加，从不删除）
└── memory_graph_state.json     # 可变状态（last_action）

${MEMORY_DIR}/                  # 通过 getMemoryDir() 获取
├── YYYY-MM-DD.md               # 每日日志（当天事件摘要）
└── narrative.md                # 人类可读叙事时间线（Markdown）

${EVOLUTION_DIR}/               # 通过 getEvolutionDir() 获取
├── evolution_state.json        # Cycle 计数器 + 最后运行时间
├── solidify_state.json         # Solidify 状态（run_id、baseline 等）
└── dormant_hypothesis.json     # 中断假设（TTL=1h）
```

路径解析由 `src/gep/paths.js` 统一管理，支持环境变量覆盖：
- `MEMORY_GRAPH_PATH` → 覆盖 memory_graph.jsonl 路径
- `getEvolutionDir()` → 解析 evolver 的 `.evolver/` 工作目录

## 2.2 JSONL 追加日志设计

### 2.2.1 写入机制

```javascript
function appendJsonl(filePath, obj) {
  const dir = path.dirname(filePath);
  ensureDir(dir);
  fs.appendFileSync(filePath, JSON.stringify(obj) + '\n', 'utf8');
}
```

每次事件写入一条 JSON 行，`\n` 分隔符。**O(1) 追加，无锁竞争**。

### 2.2.2 读取机制（只读尾部）

```javascript
function tryReadMemoryGraphEvents(limitLines = 2000) {
  // 如果文件 ≤ 512KB：全量读取
  // 如果文件 > 512KB：从尾部读取 512KB，跳过不完整的行
  const TAIL_BYTES = 512 * 1024;
  const stat = fs.statSync(p);
  if (stat.size <= TAIL_BYTES) {
    raw = fs.readFileSync(p, 'utf8');
  } else {
    // seek 到 size - 512KB，读取
    fs.readSync(fd, buf, 0, TAIL_BYTES, stat.size - TAIL_BYTES);
    raw = buf.toString('utf8');
    // 跳过第一个不完整的行（可能从中间截断）
    const firstNewline = raw.indexOf('\n');
    if (firstNewline >= 0) raw = raw.slice(firstNewline + 1);
  }
  // 解析、过滤无效行、返回最近 limitLines 条
}
```

**设计动机**：
- 避免全量读取（GB 级文件）
- 2000 条事件已足够覆盖最近数百个 cycle 的图推理
- 超过 2000 条时，旧事件对当前决策影响已通过衰减降低

### 2.2.3 原子写入（状态文件）

```javascript
function writeJsonAtomic(filePath, obj) {
  const tmp = `${filePath}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n');
  fs.renameSync(tmp, filePath);  // POSIX 原子替换
}
```

状态文件（state.json、solidify_state.json）使用原子替换，避免写入中断导致文件损坏。

## 2.3 可变状态管理（State File）

memory_graph_state.json 是**唯一**的可变状态文件，内容结构：

```json
{
  "last_action": {
    "action_id": "act_xxx",
    "signal_key": "log_error|recurring_error",
    "signals": ["log_error", "recurring_error"],
    "mutation_id": "mut_xxx",
    "mutation_category": "repair",
    "mutation_risk_level": "medium",
    "personality_key": "pers_xxx",
    "personality_state": { "creativity": 0.8, "rigor": 0.7 },
    "gene_id": "gene_distilled_xxx",
    "gene_category": "self_repair",
    "hypothesis_id": "hyp_xxx",
    "capsules_used": ["capsule_xxx"],
    "had_error": true,
    "created_at": "2026-05-03T01:00:00Z",
    "outcome_recorded": false,
    "baseline_observed": { "recent_error_count": 3, "scan_ms": 1500 }
  }
}
```

**关键字段**：`outcome_recorded` 标记，防止重复记录同一个 cycle 的 outcome。

## 2.4 Signal Key 规范化

```javascript
function computeSignalKey(signals) {
  // 1. 规范化错误签名（路径/数字归一化）
  // 2. 去重 + 排序
  // 3. 用 | 连接
  const list = normalizeSignalsForMatching(signals);
  const uniq = Array.from(new Set(list.filter(Boolean))).sort();
  return uniq.join('|') || '(none)';
}

function normalizeSignalsForMatching(signals) {
  const out = [];
  for (const s of list) {
    if (str.startsWith('errsig:')) {
      const norm = normalizeErrorSignature(str.slice('errsig:'.length));
      if (norm) out.push(`errsig_norm:${stableHash(norm)}`);
      continue;
    }
    out.push(str);
  }
  return out;
}
```

**错误签名归一化**：
```javascript
function normalizeErrorSignature(text) {
  return s
    .toLowerCase()
    .replace(/[a-z]:\\[^ \n\r\t]+/gi, '<path>')  // Windows 路径
    .replace(/\/[^ \n\r\t]+/g, '<path>')           // Unix 路径
    .replace(/\b0x[0-9a-f]+\b/gi, '<hex>')
    .replace(/\b\d+\b/g, '<n>')
    .replace(/\s+/g, ' ')
    .slice(0, 220);
}
```

**目的**：让"第 3 次出现文件 /workspace/src/utils.js 第 15 行错误"和"第 5 次出现 /workspace/src/utils.js 第 22 行错误"归一化到同一个 key，使得相似错误的经验可以被累积。

## 2.5 存储设计对 Claude-Mem 的借鉴价值

| 设计点 | EvoMap 做法 | Claude-Mem 现状 | 改进建议 |
|--------|------------|----------------|---------|
| 追加日志 | JSONL 追加，不修改历史 | PostgreSQL UPDATE | 考虑 Append-only Observation 表 |
| 尾部读取 | 只读最后 512KB | 全量查询 | 引入基于时间的窗口查询 |
| 状态文件 | 独立 JSON 文件 | DB 字段 | 保持现状 |
| 原子写入 | rename 原子替换 | DB 事务 | 保持现状 |

---

_Next: [03-signals.md](./03-signals.md) — 信号提取机制详解_
