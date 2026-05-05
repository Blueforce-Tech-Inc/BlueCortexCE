# Compression Eval Harness + Scrubber Pipeline（1e6285c53）

**上游 Commit**：`1e6285c53`（2026-04-24）
**Prerequisite**：doc [`49`](49-compression-eval-harness-and-structured-extraction-quality.md)（2026-04-26，早期版本）；本篇为完整版，覆盖 `scripts/compression_eval/` 全量 18 个文件。

**相关文档**：doc [`83`](83-upstream-0ce1b9fe2-to-13a7cbcd6-memory-analysis.md)（上游 54 commits 分析，含本 commit 的上下文）；doc [`78`](78-cross-cutting-architectural-patterns-synthesis.md)（P2 Compression Eval Harness）

---

## 背景：为什么需要压缩质量评估

Hermes Agent 的 `context_compressor.py` 提示词和 `_template_sections` 靠人工修改，发布时没有自动化检查来验证压缩是否仍然保留了文件路径、错误码、当前任务等关键信息。之前在"测试套件全绿"和"用户在生产环境遇到糟糕的摘要"之间存在**零信号区**。

Compression Eval Harness 填补了这个空白。

---

## 架构概览

```
scripts/compression_eval/
├── DESIGN.md                    # 完整架构、fixture/probe 格式、scrubber 管道、评分标准
├── README.md                    # 使用说明、成本估算、运行时机
├── scrub_fixtures.py            # 将 ~/.hermes/sessions/*.jsonl 转换为公开安全的 JSON fixture
├── compressor_driver.py         # ContextCompressor 单次强制压缩包装器
├── grader.py                   # 两阶段（Continuation + Grading）LLM 调用
├── rubric.py                   # 六维评分 rubric + judge prompt builder
├── report.py                   # Markdown 报告渲染器（PR body 即开即用）+ --compare-to 增量模式
├── run_eval.py                 # CLI 入口（--fixtures/--runs/--judge-model 等）
├── fixtures/                   # 三个已清理的 session 快照
│   ├── feature-impl-context-priority.json  （75 msgs / ~17k tokens）
│   ├── debug-session-feishu-id-model.json  （59 msgs / ~13k tokens）
│   └── config-build-competitive-scouts.json（61 msgs / ~23k tokens）
├── probes/                     # 每个 fixture 对应的 probe bank
│   ├── feature-impl-context-priority.probes.json   （11 probes）
│   ├── debug-session-feishu-id-model.probes.json  （10 probes）
│   └── config-build-competitive-scouts.probes.json （10 probes）
└── results/                   # 运行时输出目录（.gitkeep）

tests/scripts/test_compression_eval.py   # 33 个 hermetic 单元测试
```

**两阶段 pipeline**：
1. **Continuation Phase**：用压缩后的状态，让 compressor model 回答 probe 问题
2. **Grading Phase**：用 judge model 对回答评分（0-5，6 个维度）

**为什么放 `scripts/` 而非 `tests/`**：需要 API 凭证 + 真实 API 调用（~50 美分/次运行），与 `scripts/run_tests.sh` 的 hermetic/并行/免凭证风格不兼容。

---

## Scrubber Pipeline（`scrub_fixtures.py`）

将真实生产 session JSONL 转换为可公开分享的 fixture，是整个 eval 的关键前提。**9 步 scrubber 管道**：

| Step | Scrubber | 做法 |
|------|----------|------|
| 1 | Secrets redaction | `agent.redact.redact_sensitive_text` — API key / bearer token / env var 抹除 |
| 2 | Username path normalisation | 文件路径中的用户名归一化 |
| 3 | Personal handle scrubbing | 个人社交 handle 替换 |
| 4 | Email / git-author normalisation | 邮箱和 git author 归一化 |
| 5 | Reasoning scratchpad stripping | 推理中间步骤（reasoning 字段）剥离 |
| 6 | Platform-mention scrubbing | 平台特定 mention 抹除 |
| 7 | First-user paraphrase | 首个用户消息轻度改写，保留任务意图去除"语气" |
| 8 | System-prompt placeholder | 系统提示词替换为占位符 |
| 9 | 2KB tool-output truncation | 工具输出截断到 2KB |

**`agent.redact.redact_sensitive_text` 复用**：上游已将 secrets redaction 集中化，scrubber 和 compressor 都调用同一函数，保证口径一致。

---

## Probe 格式（4 类型）

每个 probe 包含：
- `question`：给 compressor model 的问题
- `type`：类型之一
- `expected_facts`：锚点（PR 编号/文件路径/错误码/命令）

**4 种 probe 类型**：

| Type | 含义 | 例子 |
|------|------|------|
| `recall` | 从压缩状态回忆事实 | "用户问的 bug 是哪个 issue？" |
| `artifact` | 追踪 artifact 生成历史 | "哪个 commit 引入了这个函数？" |
| `continuation` | 继续未完成任务 | "上一轮正在修哪个文件？" |
| `decision` | 追踪决策历史 | "我们为什么选这个方案？" |

---

## 六维 Rubric（`rubric.py`）

| Dimension | 含义 | 满分 5 分描述 |
|-----------|------|--------------|
| **accuracy** | 回答事实准确度 | 全部 facts 正确无幻觉 |
| **context_awareness** | 对对话上下文的理解 | 准确引用 session 中已有的信息 |
| **artifact_trail** | artifact 生成历史的连续性 | 准确描述 artifact 从创建到当前的演变 |
| **completeness** | 任务完成度 | 完整覆盖任务的所有子步骤 |
| **continuity** | 跨压缩边界的连续性 | 压缩前的上下文被准确保留 |
| **instruction_following** | 对系统指令的遵循 | 严格按 `instructions` 的要求格式输出 |

**评分输出格式**：`{dimension: score, ...}`，带 JSON fallback 解析器处理 LLM 返回的杂散文本。

---

## 关键设计决策

### 强制单次压缩（`compressor_driver.py`）

真实 session fixture 低于默认 100k token 触发阈值，所以 `ContextCompressor.compress()` 不会自动触发。driver 显式强制调用，使得 prompt 变更可以归因到分数变化（而不是阈值触发与否的方差）。

### 噪声测量

单次运行噪声：overall 3.25 → 3.17（delta −0.08）。单维度在 ±0.5 之间波动。
**结论**：<0.3 的跨运行差异比较是合理的置信区间。精确测量（N=10）作为 open follow-up。

### 实际运行数据

```
Fixture: debug-session-feishu-id-model
Compression: 13081 -> 3055 tokens (76.6% ratio), 59 -> 10 messages
Overall score: 3.25
artifact_trail: 1.50（最弱项，与 Factory 论文观察一致）
```

---

## CE Phase 3 Structured Extraction 质量评估借鉴

Compression Eval Harness 的设计可直接迁移用于 **Phase 3 Structured Extraction 质量保障**：

| Hermes 组件 | CE Phase 3 对应 |
|-------------|----------------|
| `scrub_fixtures.py` | Session observations 作为 fixture |
| `probes/`（recall/artifact/continuation/decision） | Extraction correctness probes（字段齐全/类型正确/值合理） |
| `rubric.py` 六维 | Phase 3 extraction quality rubric（accuracy/field_completeness/type_correctness/value_plausibility） |
| `grader.py`（两阶段） | Extraction output → LLM judge 评分 |
| `report.py`（markdown PR body） | 质量报告用于 CI 门禁 |

**具体迁移路径**：
1. 复用 `scrub_fixtures.py` 的 PII 清理逻辑（CE 的 SessionEntity/ObservationEntity 已脱敏）
2. 复用 `rubric.py` 的 JSON-with-fallback 解析器
3. 复用 `report.py` 的 `--compare-to` 增量模式（比较修改前后的 extraction 质量）
4. 用 Phase 3 acceptance test fixture 作为 probes，extraction output 作为 answers

**相关文档**：doc [`49`](49-compression-eval-harness-and-structured-extraction-quality.md)（早期版）；doc [`78`](78-cross-cutting-architectural-patterns-synthesis.md)（P2 Compression Eval Harness）

---

## 验收

- 33/33 单元测试通过（0.33s）
- 50/50 相邻 `test_context_compressor.py` 测试通过（无回归）
- E2E 干跑（debug-session-feishu-id-model，openai/gpt-5.4-mini）：13081→3055 tokens，score 3.25

---

## 上游上下文

- **首次引入**：`1e6285c53`（2026-04-24）
- **后续**：已在 doc 49 中部分记录；本篇为基于完整源码的深度版
- **下次扫描起点**：`1e6285c53`
