# 49. Compression Eval Harness — Probe-Based Quality Framework

**Date**: 2026-04-26  
**Commit**: `1e6285c5` (upstream)  
**Author**: Teknium  
**Status**: 新增（本轮分析）

## TL;DR

Hermes 上游新增了一套**离线评估框架**（`scripts/compression_eval/`），用 probe 问题验证 ContextCompressor 压缩质量。核心思想：真实会话 → 提取 probe 问题 → 压缩 → 用压缩结果回答 probe → LLM judge 打分。**Phase 3 Structured Extraction 可直接借鉴此方法论**做提取质量评估。

---

## 1. 背景与动机

### 问题
ContextCompressor 的 prompt 和 `_template_sections` checklist 是手工调优的，每次修改都没有自动化验证。团队不知道 prompt 改动后，压缩结果是否还保留了文件路径、错误码、活跃任务等关键信息。

### 解法
参考 Factory.ai 2025-12 的压缩评估方法论，构造 probe-based eval：
1. 用真实会话 transcript（含敏感数据的 session JSONL）
2. 编写 probe 问题库（关于会话中已知的事实）
3. 对 transcript 压缩后，用压缩结果回答 probe
4. LLM judge 对答案打分

---

## 2. 目录结构

```
scripts/compression_eval/
├── DESIGN.md                 # 架构设计、fixture格式、probe类型
├── README.md                 # 使用方法、成本估算、注意事项
├── run_eval.py               # 入口 CLI（fire风格）
├── scrub_fixtures.py         # 从 ~/.hermes/sessions/*.jsonl 生成 fixture
├── fixtures/                 # 已审核的会话快照（脱敏后）
│   ├── feature-impl-context-priority.json   # 75 msgs / ~17k tokens
│   ├── debug-session-feishu-id-model.json   # 59 msgs / ~13k tokens
│   └── config-build-competitive-scouts.json # 61 msgs / ~23k tokens
├── probes/                   # 与 fixture 配对的 probe 库
│   └── <fixture>.probes.json
├── rubric.py                 # 评分 rubric + dimension 定义
├── grader.py                 # judge-model 调用 + 分数解析
├── compressor_driver.py      # ContextCompressor 单次压缩封装
└── results/                 # gitignored，运行时输出
```

---

## 3. 六大评估维度（Rubric）

来源：`rubric.py` — 6 个维度，0-5 分。

| 维度 | 描述 | CE 提取场景对应 |
|------|------|----------------|
| **accuracy** | 具体事实是否正确——文件路径、函数名、PR号、错误码、命令输出、行号 | 提取的偏好项是否与原始观察一致 |
| **context_awareness** | 答案是否反映会话**当前状态**，而非中间快照 | 提取结果是否反映用户当前偏好（而非过时信息） |
| **artifact_trail** | 是否正确列举了 artifacts（读取的文件、修改的文件、运行的命令、调用的工具） | 提取项是否可追溯到源 observation |
| **completeness** | 是否回答了 probe 问题的**所有部分** | 是否提取了**所有**相关的偏好/过敏信息 |
| **continuity** | 下一个 assistant 能否仅凭此答案继续工作（无需重新获取文件） | 提取结果是否足以在后续会话中直接使用 |
| **instruction_following** | 答案是否符合 probe 要求的格式（列表/数字/短句/是/否） | 提取结果是否符合 YAML 模板 schema |

### 分数标准

| 分 | 含义 |
|----|------|
| 0 | 无有用信息；错误或幻觉 |
| 1 | 重大遗漏或关键事实错误 |
| 2 | 部分正确但有重大遗漏 |
| 3 | 大部分正确，有小的遗漏或不精确 |
| 4 | 正确完整，仅有微小不精确 |
| 5 | 完全正确、完整、格式符合要求 |

### Judge Prompt 结构

```
You are grading an answer from a COMPRESSED handoff summary.
Grade on 6 dimensions (0-5)...

Dimension definitions:
- accuracy: ...
- context_awareness: ...
...

Answer the following probe: <probe_question>
<continuation_answer>

Grade each dimension with brief reason:
{"accuracy": X, "reason": "...", ...}
```

---

## 4. Probe 类型（DESIGN.md）

四类 probe：

| 类型 | 问什么 | CE 提取场景 |
|------|--------|-----------|
| **recall** | 会话中提过的事实（文件名、PR号、错误码） | "用户在第3轮说了什么偏好？" |
| **artifact** | 创建/修改了哪些 artifacts | "从哪些 observation 提取了这个偏好？" |
| **continuation** | 下一步该做什么 | "基于已提取的偏好，下一轮该如何回复？" |
| **decision** | 做了哪些决策及原因 | "这个偏好是在什么上下文下表达的？" |

### Probe 格式（JSON）

```json
{
  "id": "artifact-01",
  "type": "artifact",
  "question": "这个会话创建了哪几个文件？",
  "expected_facts": ["src/memory.py", "tests/test_memory.py"],
  "notes": "验证 artifact_trail 维度"
}
```

---

## 5. 两阶段评估流程（grader.py）

### Phase 1 — Continuation
用压缩后的 messages + probe question，模拟下一个 assistant turn。让 continuing model 用**仅压缩上下文**回答问题。

```python
CONTINUATION_SYSTEM = """
You are the continuing assistant in a long session. Earlier turns have been
compacted into a handoff summary. Answer using ONLY what you can determine
from the conversation history (including the handoff summary). Do NOT invent
details. Be direct and concrete — cite file paths, PR numbers, error codes.
"""
```

### Phase 2 — Grading
独立的 judge-model 调用，用 rubric 的 6 个维度对答案打分。

**关键设计**：两阶段分离保证了评估的公正性——continuation model 和 judge model 是分开的，避免自我评分偏差。

---

## 6. Fixture 生成流程（scrub_fixtures.py）

从 `~/.hermes/sessions/*.jsonl` 脱敏生成 fixture：

1. `redact_sensitive_text()` — 全链路 secrets 过滤（JWT/URL参数/form body/Discord mentions）
2. 用户名路径归一化 + personal handle 清理 + email/git-author 归一化
3. reasoning scratchpad 剥离
4. 平台 mention 清理
5. 第一条 user message 轻 paraphrase（保留任务意图，去除"vibe"）
6. 2KB tool output 截断
7. orphan 消息裁剪

**CE 借鉴**：Structured Extraction 的 evaluation fixture = session observations，脱敏后可用于测试。

---

## 7. 对 Phase 3 Structured Extraction 的借鉴

### 7.1 可直接迁移的思路

**Probe-based Extraction Quality Eval**：

| Hermes Compression Eval | Structured Extraction Eval |
|------------------------|---------------------------|
| Fixture = 压缩后的 session transcript | Fixture = session observations |
| Probe questions = 会话中已知的事实 | Probe questions = 已知的偏好/过敏项 |
| Continuation model 回答 probe | Extraction service 输出 extraction |
| Judge model 评分（6维度） | Judge LLM 评分（adapted rubric） |

### 7.2 adapted Rubric for Structured Extraction

| 维度 | Extraction Quality 问题 |
|------|------------------------|
| accuracy | 提取的 category/value 是否与原始 observation 一致？ |
| completeness | 是否从**所有**相关 observation 中提取了信息？ |
| artifact_trail | 每个提取项是否能追溯到源 observation？ |
| context_awareness | 提取的偏好是否反映**当前**状态（而非过时偏好）？ |
| continuity | 提取结果是否足以在后续 `/api/memory/icl-prompt` 中使用？ |
| instruction_following | 提取结果是否符合 YAML 模板 schema（类型、格式）？ |

### 7.3 评估流程设计

```
Session observations (脱敏)
    ↓
Extraction probe questions
  - "用户在这轮说了喜欢什么？"
  - "用户的过敏原是什么？"
  - "用户的项目偏好是什么？"
    ↓
StructuredExtractionService.extractAppendOnly()
    ↓
Judge LLM 评分
  - accuracy: 提取项是否与预期一致
  - completeness: 是否所有相关项都被提取
  - schema_compliance: 是否符合 template schema
```

### 7.4 与压缩评估的差异

| 方面 | Compression Eval | Extraction Eval |
|------|----------------|-----------------|
| 输入 | session messages | session observations |
| 操作 | 压缩（summarize） | 提取（structured output） |
| 预期 | 保留关键事实 | 正确识别 structured data |
| ground truth | probe 中的 `expected_facts` | probe 中的 `expected_items` |
| 输出 | 压缩后的事实答案 | structured JSON extraction |

### 7.5 实现建议

Phase 3 Structured Extraction 的 quality eval 可以放在 `scripts/extraction_eval/`：

```
scripts/extraction_eval/
├── DESIGN.md
├── fixtures/              # session observations (脱敏)
│   └── <session>_observations.json
├── probes/               # extraction probe questions
│   └── <session>.probes.json
├── rubric.py             # extraction-adapted 6-dimension rubric
├── grader.py            # judge LLM 调用
├── extractor_driver.py  # StructuredExtractionService 封装
├── scrub_observations.py # 从 DB 或 JSONL 生成 fixture
└── results/
```

**何时运行**：
- 每次 extraction prompt 改动后
- 每次 StructuredExtractionService 逻辑变更后
- CI 无法自动运行（需要 LLM 调用），但可作为 pre-merge check

---

## 8. 上游新提交：上下文相关的其他变更

### 8.1 `125de020` — Context Custom Provider Context Length（已分析）

**影响**：自定义 provider 的 per-model `context_length` 配置现在在每次 `/model` 切换时都被正确读取，而非仅在 agent 启动时。

CE 借鉴：无直接关系，但验证了「context length 必须在每次模型切换时重新计算」这一原则。

### 8.2 `25ba6a4a` — Reasoning Session-Scoped by Default

**影响**：`/reasoning` 命令现在默认 session-scoped（可加 `--global` 持久化到 config.yaml）。

**CE 借鉴**：Structured Extraction 的 `EXTRACTION_ENABLED` 也可以考虑类似的 session-scope vs global 分离，允许 per-session 临时禁用提取而不影响全局配置。

### 8.3 `9daa0620` — Reasoning Content Ordering Fix（Cross-Provider Isolation）

**影响**：修复 `_copy_reasoning_content_for_api` 中的 ordering，确保 reasoning_content 在跨 provider 切换时正确隔离。

**CE 借鉴**：如果 Structured Extraction 使用多个 LLM provider，提取结果需要类似的 provider 隔离机制。

---

## 9. 总结

| 发现 | 来源 | CE 关联度 | 可执行性 |
|------|------|-----------|----------|
| Compression Eval Harness 方法论 | `1e6285c5` | ⭐⭐⭐ 直接可用 | 高 — 评估框架可直接迁移 |
| 6维度 Rubric | `rubric.py` | ⭐⭐⭐ 直接可用 | 高 — 维度定义可直接adapt |
| 两阶段评估（continuation + grading 分离） | `grader.py` | ⭐⭐ 高 | 高 — 架构模式可直接用 |
| Fixture scrub pipeline | `scrub_fixtures.py` | ⭐⭐ 中 | 中 — observation 脱敏需要适配 |
| Session-scoped reasoning override | `25ba6a4a` | ⭐ 低 | 中 — 可考虑 extraction scope 分离 |

**最高优先级借鉴**：将 Compression Eval Harness 方法论迁移到 Structured Extraction quality evaluation。这是 Phase 3 acceptance test 计划（Section 26）的自动化质量保障补充——acceptance test 验证功能正确性，eval harness 验证提取质量维度。

---

## Changelog

- **2026-04-26 v1**: 初始文档 — Compression Eval Harness (`1e6285c5`) 源码分析 + Phase 3 迁移建议
