# 上游新提交分析（2026-04-11 ~ 04-24）：压缩地板、磁盘清理、Context Engine 插件化

**覆盖区间**：`6f1eed39` → `93ddff53`（约 1340 commits 中记忆相关核心提交）
**关联前文**：[`34`](34-upstream-new-commits-session-lifecycle-and-context-engine.md)（Session 生命周期大重构） · [`35`](35-hindsight-batch-fixes-and-compression-eval-harness.md)（Hindsight 批量修复 + eval harness 设计）

---

## 1. 压缩地板 + Budget 警告移除 + Activity 追踪（`ce6fb1c2`）

**日期**：2026-04-11
**主题**：修复「Agent 中途停止任务」网关 bug，定位三个根因。

### 1.1 压缩触发阈值地板（64K tokens minimum）

**问题**：50% 阈值在 100K context 模型上，50K tokens 即触发压缩，导致多步骤计划丢失。

**解决方案**：
```python
self.threshold_tokens = max(
    int(context_length * self.threshold_percent),
    MINIMUM_CONTEXT_LENGTH,  # 64K
)
```

**影响**：
- 模型 context < 64K 在启动时被拒绝
- 短 context 模型不再被 50% 阈值过早压缩

**对 Claude-Mem 的借鉴**：
- Structured Extraction 的 `max_token * 0.5` 阈值同样应设地板
- Phase 3 设计的 summary budget 计算（`max_tokens * 0.3`）应增加地板保护

### 1.2 Budget 警告移除 → Grace Call 机制

**问题**：70%/90% 迭代 budget 警告在 tool result 中注入 `[BUDGET WARNING: Provide your final response NOW]`，导致模型放弃复杂任务。

**解决方案**：
- 完全移除正常执行期间的警告
- 当 budget 真正耗尽（90/90）：注入 user message 要求总结，允许一次 grace API call，然后才 fallback 到 `_handle_max_iterations`

**对 Claude-Mem 的借鉴**：
- 5 个 hook 阶段中不应插入强制终止消息
- 应使用 user message 注入而非 tool result 注入

### 1.3 Activity Touches During Long Terminal Execution

**问题**：`_wait_for_process` 每 0.2s 轮询但从不报告 activity，网关 1800s inactivity timeout 在长时间运行命令时被误触发。

**解决方案**：
- Thread-local activity callback 每 10s 触发一次
- Agent 在每次 tool call 前 wire `_touch_activity`

**对 Claude-Mem 的借鉴**：
- Polling 型工具（shell exec）应定期调用 activity callback
- 不然 gateway session 会在长时间 LLM 调用期间过期

---

## 2. Summarizer Pipeline 中的 Secrets Redaction（`1f804d17` + `fcae077d`）

**日期**：2026-04-13
**主题**：防止 API key、token、密码等敏感信息泄露到 summary 中。

### 2.1 两阶段 redacting

**Stage 1**（`1f804d17`）：序列化前 redact
```python
content = redact_sensitive_text(msg.get("content") or "")
args = redact_sensitive_text(fn.get("arguments", ""))
```

**Stage 2**（`fcae077d`）：summarizer output redact（新增测试覆盖）

### 2.2 Summary Prompt 强化

```python
"NEVER include API keys, tokens, passwords, secrets, credentials, "
"or connection strings in the summary — replace any that appear "
"with [REDACTED]. Note that the user had credentials present, but "
"do not preserve their values."
```

Focus topic 指令也同步更新：
```
"Even for the focus topic, NEVER preserve API keys, tokens, passwords,
or credentials — use [REDACTED]."
```

**对 Claude-Mem 的借鉴**：
- Observation/Summary 写入 DB 前必须 redact
- Phase 3 Structured Extraction 的 prompt 应包含 `[REDACTED]` 指令
- 特别是 `EmbeddingService` 中发送给 LLM 的 content（可能包含 API key 配置）

---

## 3. Context Engine 插件发现系统（`fec7b222`）

**日期**：2026-04-08（早于本轮其他提交，但前次分析遗漏）
**主题**：将 Context Engine 从硬编码改为可插拔架构，与 MemoryProvider 模式对齐。

### 3.1 插件目录结构

```
plugins/
  context_engine/
    __init__.py          # discover_context_engines(), load_context_engine()
    <name>/
      __init__.py        # ContextEngine ABC 实现
      plugin.yaml        # description 元数据
```

### 3.2 选择优先级（4 层）

```python
# 1. config.yaml context.engine 设置
# 2. plugins/context_engine/<name>/ 目录（repo-shipped）
# 3. general plugin system（用户安装的插件）
# 4. Fall back to built-in ContextCompressor
```

### 3.3 ABC 增强（`ContextEngine`）

新增 class attributes：
- `threshold_percent`
- `protect_first_n`
- `protect_last_n`

新增方法：
- `update_model()`：带 threshold 重计算（子类可 override）
- `switch_model()`：run_agent.py 中替代直接内部访问

### 3.4 生命周期 wiring

```python
# 在 shutdown_memory_provider() 中调用
context_engine.on_session_end()
```

**对 Claude-Mem 的借鉴**：
- Structured Extraction 可作为 ContextEngine 插件实现（而非硬编码在 `StructuredExtractionService`）
- `update_model()` 模式适用于 Phase 3 中模型切换时的 schema 重新绑定
- 4 层选择优先级设计值得直接参考

---

## 4. Compression Eval Harness 设计（`9f5c13f8`）

**日期**：2026-04-24
**状态**：Design PR（未合并），纯设计文档 + scrubbed fixtures。

### 4.1 动机

> "we edit agent/context_compressor.py prompts and `_template_sections` by hand and ship without any automated check that compression still preserves file paths, error codes, or the active task."

引用 Factory.ai Dec 2025 write-up 的 probe-based eval 方法论。

### 4.2 Probe 格式（6 维度评分）

| Dimension | Description |
|-----------|-------------|
| recall | 文件路径、错误码是否保留 |
| artifact | 关键产出物（生成的代码/文档）是否可还原 |
| continuation | 多步骤任务能否接续 |
| decision | 关键决策是否被记录 |
| safety | secrets 是否被正确 redact |
| coherence | 摘要内部逻辑一致性 |

### 4.3 Fixture Scrubber Pipeline

```python
# scrub_fixtures.py 清洗步骤
1. redact_sensitive_text        # API keys, tokens
2. username path normalization   # /Users/name → /Users/human
3. personal handle scrubbing   # @handle → @user
4. email + git-author normalization
5. reasoning scratchpad stripping  # <think>/</think> 移除
6. platform user-mention scrubbing
7. first-user paraphrase        # 真实问题 → 示例问题
8. system-prompt placeholder
9. orphan-message pruning
10. tool-output size truncation
```

### 4.4 三条 scrubbed fixtures

- `feature-impl-context-priority.json` — 75 msgs / ~17k tokens（Investigate → patch → test → PR → merge 场景）
- `debug-session-feishu-id-model.json` — 59 msgs / ~13k tokens（飞书调试场景）
- `config-build-competitive-scouts.json` — 61 msgs / ~23k tokens（11 cron jobs 配置积累场景）

### 4.5 对 Claude-Mem 的直接迁移价值

**Phase 3 Structured Extraction 质量保障可迁移**：

```python
# 类似的设计可用于 Structured Extraction 的 eval harness
class ExtractionProbe:
    def evaluate(self, extracted: dict, ground_truth: dict) -> dict:
        return {
            "field_completeness": ...,
            "type_correctness": ...,
            "semantic_accuracy": ...,
            "safety_redaction": ...,
        }
```

关键设计原则：
- Fixture 必须可公开（无 PII）
- Cost 预估：~$1/run，LLM grading
- 不在 CI 中运行（成本和非确定性）
- 先 design review，再实现

---

## 5. Session Artifact Disk Cleanup（`62919b1e`）

**日期**：2026-04-12
**主题**：自动清理积累的磁盘 artifacts，防止存储爆炸。

### 5.1 积累的 artifacts

| 类型 | 路径模式 | 典型大小 |
|------|---------|---------|
| Session transcript JSON | `~/.hermes/sessions/session_<id>.json` | ~2 GB 总计 |
| API request dump | `~/.hermes/sessions/request_dump_<id>.json` | varies |
| Checkpoint shadow repos | `~/.hermes/checkpoints/<hash>/` | ~12 GB 总计 |
| Gateway JSONL | `~/.hermes/sessions/<id>.jsonl` | varies |

### 5.2 Retention 策略

```python
SESSION_FILE_RETENTION_DAYS = 30
REQUEST_DUMP_RETENTION_DAYS = 7
CHECKPOINT_RETENTION_DAYS = 14
JSONL_TRANSCRIPT_RETENTION_DAYS = 30
```

### 5.3 安全机制

- **从不删除活跃 session 的文件**（基于 DB `ended_at` 判断）
- **从不触碰 sessions.json state file**
- **Checkpoint 使用 age-based 删除**（无 session ID 关联）
- **Dry-run 模式**可用
- **所有错误被捕获和日志记录**，不 crash gateway

### 5.4 自动化

- Gateway session expiry watcher 中每日自动触发
- `hermes sessions prune --include-files --dry-run` CLI 支持

### 5.5 对 Claude-Mem 的借鉴

Claude-Mem 的 PostgreSQL 存储可能积累：
- 大量 `observations` / `summaries` / `prompts` 行
- `context_segments` 表的长文本
- 向量索引的磁盘占用

建议：
- 实现类似 `session_cleanup.py` 的 `ObservationCleanupService`
- 按 `created_at` retention 策略清理 old observations
- 提供 `--dry-run` 和 `--include-vectors` CLI 标志

---

## 6. 防止 Stuck Session Resume Loop（`46db738e`）

**日期**：2026-04-12
**修复**：`#7536` — Gateway 重启后的 stuck session resume loops。

**根因**：session resume 时某些状态不一致导致无限循环。

**对 Claude-Mem 的借鉴**：
- Session resume 路径应有最大重试次数保护
- 长期运行的 Claude-Mem session 可能有类似问题

---

## 7. 关键设计模式总结

| 上游设计 | Claude-Mem 可借鉴场景 | 可执行性 |
|---------|---------------------|---------|
| 64K compression floor | Phase 3 summary budget 地板计算 | ⭐⭐⭐ 高 |
| Secrets redaction in summarizer | Observation/Summary DB 写入前 redact | ⭐⭐⭐ 高 |
| Activity callback in long polling | Shell exec tool 定期 heartbeat | ⭐⭐⭐ 高 |
| Context Engine plugin discovery (4-layer) | StructuredExtractionService 插件化 | ⭐⭐ 中 |
| Compression eval harness + scrubber | Phase 3 Structured Extraction eval | ⭐⭐ 中 |
| Session artifact disk cleanup | ObservationCleanupService | ⭐⭐ 中 |
| Grace call vs budget warning | 不在 hook 中注入强制终止消息 | ⭐⭐⭐ 高 |

---

## 8. 下一步行动建议

### 高优先级（立即可执行）

1. **Phase 3 Structured Extraction prompt 增加 `[REDACTED]` 指令**（借鉴 `1f804d17`）
   - 在所有 embedding/summary prompt 中增加 secrets 过滤
   - 检查 `EmbeddingService.java` 中发送给 LLM 的 content

2. **Shell exec tool 增加 activity callback**（借鉴 `ce6fb1c2` §3）
   - 防止长时间命令触发 session expiry

### 中优先级

3. **设计 Compression Eval Harness for Phase 3**
   - 参考 `9f5c13f8` 的 scrubber pipeline 设计
   - 创建 fake session fixtures 用于 structured extraction eval
   - 与 `docs/drafts/phase-3-design.md` v29 集成

4. **实现 ObservationCleanupService**
   - 参考 `62919b1e` 的 retention + safety 机制
   - 按 `created_at` 清理 old observations
   - 提供 dry-run CLI

### 低优先级（架构演进）

5. **StructuredExtractionService 插件化**
   - 参考 `fec7b222` 的 4 层选择优先级
   - 支持多种 extraction schema 作为插件

---

**文档版本**：v1（2026-04-25）
**下次巡检**：待上游新 memory 相关 commit 后更新
