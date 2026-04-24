# 35 — 上游新提交分析（2026-04-21 ~ 04-25）：Hindsight 批量修复 + Compression Eval 设计

**覆盖范围**：`cc6e8941`（entry 34 截止点）→ `6f1eed39`（最新 main），约 1340 commits。

内存相关核心提交 6 个（Hindsight 5 个 + context cache 1 个），外加 1 个未合并设计 PR（Compression Eval Harness）。

---

## §1 Hindsight 批量修复与功能增强

### 1.1 `93a74f74` — 修复共享 Event Loop 跨 Provider 关停（Bug Fix #11923）

**问题根因**：

模块级 `_loop` / `_loop_thread` 在所有 `HindsightMemoryProvider` 实例间共享（plugin loader 每个 `AIAgent` 创建一个 provider，gateway 每个并发会话创建一个 `AIAgent`）。

`HindsightMemoryProvider.shutdown()` 在任意一个 session 结束时停止了共享 loop，导致所有 sibling provider 的 aiohttp `ClientSession` 和 `TCPConnector` 永远无法 close，表现为：

```
Unclosed client session
Unclosed connector
```

**修复方案**：`shutdown()` 不再停止共享 loop。每个 provider 仍通过 `self._client.aclose()` 关闭自己的 client。共享 loop 以 daemon thread 运行，进程退出时回收。

```python
# plugins/memory/hindsight/__init__.py
def shutdown(self) -> None:
    # 不再调用 _stop_loop()，只关闭自己的 client
    if hasattr(self, '_client'):
        asyncio.run(self._client.aclose())
```

**CE 借鉴**：如果 CE 未来引入 Hindsight（`25` 已有嵌入式 Daemon 分析），需要特别注意 provider shutdown 不要干扰共享 event loop。此 bug 在多会话并发场景（gateway 典型场景）必现。

**测试**（`TestSharedEventLoopLifecycle`）：
- `test_shutdown_does_not_stop_shared_event_loop` — 两 provider 共享 loop，shutdown 一个后另一个 loop 仍存活
- `test_client_aclose_called_on_cloud_mode_shutdown` — 每个 provider 的 aiohttp session 仍正确关闭

---

### 1.2 `403c82b6` — 可配置 HINDSIGHT_TIMEOUT（default: 120s）

**动机**：Hindsight Cloud API 每次请求可耗时 30-40s，硬编码 30s timeout 过激进，频繁 timeout errors。

**变更**：
1. 新增 `HINDSIGHT_TIMEOUT` 环境变量（default: 120s）
2. 加入 config schema（setup wizard 可视化）
3. 在 `_run_sync()` 和 client 创建时同时使用可配置 timeout
4. 读取优先级：`config.json` → env var → 120s default

**CE 借鉴**：CE 的 StructuredExtractionService 也有外部 LLM 调用超时问题。可参考此模式，通过 env var + config 双路配置超时，而不是硬编码。

---

### 1.3 `f1ba2f0c` — `_run_sync` 中实际应用配置的 Timeout

**问题**：上条 commit 添加了 `HINDSIGHT_TIMEOUT` 配置，但 `_run_sync` 仍使用硬编码 `_DEFAULT_TIMEOUT`（120s），配置值从未真正生效。

**修复**：所有 async 操作（recall/retain/reflect/aclose）统一经过实例方法 `self._run_sync()`，该方法使用 `self._timeout`（从配置读取）。

```python
# 修复后：_run_sync 使用 self._timeout
async def _run_sync(self, coro):
    return await asyncio.wait_for(coro, timeout=self._timeout)
```

**CE 借鉴**：配置读取后必须验证实际使用路径，防止"配置了但没生效"的假象。

---

### 1.4 `f9c6c5ab` — document_id 按 Process 隔离，修复 /resume 数据丢失（Bug Fix #6602）

**问题根因**：以 `session_id` 作为 `document_id` 重用，导致 /resume 时数据丢失——新进程加载时 `_session_turns` 为空，下一次 retain 覆盖整个之前存储的内容。

**修复方案**：`document_id` 改为 `{session_id}-{startup_timestamp}`，每个进程生命周期有独立 document_id：
- 同 session，同进程：turn 累加到一个 document（已有行为）
- Resume（新进程，同 session）：写入新 document，旧 document 保留
- Fork：子进程获得自己的 document，父进程的不受影响

**新增 lineage tags**（支持 recall 过滤同一 session 的所有进程文档）：
```python
# retain 时附加 tags
{"tags": [f"session:{session_id}", f"parent:{parent_session_id}"]}
```

**CE 借鉴**：CE 的 SessionDB 也有类似风险——如果 session resume 机制复用同一个 session_id 做存储键，而内存状态丢失，可能导致"幽灵写入"覆盖问题。

---

### 1.5 `edff2fbe` — bank_id_template：运行时动态派生 Bank 名

**功能**：新增可选 `bank_id_template` 配置，在 `initialize()` 时从运行时上下文派生 bank 名。

**支持的 placeholder**：

| Placeholder | 来源 | 示例 |
|-------------|------|------|
| `{profile}` | `agent_identity` kwarg | `hermes-{profile}` |
| `{workspace}` | `agent_workspace` kwarg | `{workspace}-{profile}` |
| `{platform}` | cli/telegram/discord 等 | `hermes-{user}` |
| `{user}` | gateway sessions | `hermes-{user}` |
| `{session}` | session id | `hermes-{session}` |

unsafe 字符会被 sanitize，空 placeholder 优雅折叠（如 `hermes-{user}` 无 user 时变为 `hermes`）。渲染为空时 fallback 到静态 `bank_id`。

**典型用途**：
```yaml
# 按 Hermes profile 隔离
bank_id_template: hermes-{profile}

# gateway 下按用户隔离
bank_id_template: hermes-{user}
```

**CE 借鉴**：CE 的 MemoryModeService 中 observation 写库时，是否也应该按"用户/workspace/profile"做逻辑隔离？bank_id_template 的 placeholder 派生模式值得参考。

---

## §2 `346601ca` — Codex OAuth Context Length 缓存失效（Bug Fix #15078）

### 2.1 问题

PR #14935 添加了 Codex-aware context resolver，但只有新查询触发 live `/models` probe。已在 `~/.hermes/context_length_cache.yaml` 中缓存了错误值（如 `1,050,000` from models.dev）的用户，永远使用这个错误值。

**症状**（用户报告）：
1. 启动 banner 显示 context 按 1M 计算
2. Compression 触发过晚
3. OpenAI 硬拒绝：`'context length will be reduced from 1,050,000 to 128,000'`（实际 boundary 是 272k）

### 2.2 修复

在 `get_model_context_length()` 的 step-1 缓存返回时，对 `openai-codex` provider 的值 >= 400k 做特殊处理：
- 400k 是 Codex OAuth 的硬上限（live probe 值都 <= 272k）
- 任何 >= 400k 的缓存值都是 #14935 之前的遗留值
- 删除该磁盘缓存条目，降级到 step-5（live probe）
- 非 Codex provider 和合法的 272k Codex 条目不受影响

```python
# agent/model_metadata.py
def _invalidate_cached_context_length(key: str) -> None:
    """从 context_length_cache.yaml 删除单个条目并重写文件"""
    ...

# step-1 缓存检查中
if provider == 'openai-codex' and cached_value >= 400_000:
    _invalidate_cached_context_length(key)
    # fall through to step-5 live probe
```

**CE 借鉴**：CE 也有缓存机制（如 embedding 结果缓存、model metadata 缓存）。如果缓存 schema 变更，应主动失效旧条目，否则历史缓存会悄悄破坏新功能。

---

## §3 `9f5c13f8`（未合并）— Compression Eval Harness 设计

**分支**：`origin/design/compression-eval-harness`（非 main，PR 阶段）

### 3.1 背景

团队手工修改 `agent/context_compressor.py` 的 prompts 和 `_template_sections`，没有任何自动化检查验证压缩是否仍保留：文件路径、错误码、活跃任务。

Factory.ai 2025-12 的 write-up（https://factory.ai/news/evaluating-compression）提出 probe-based eval 方法论，评分 6 个维度。Hermes 采纳该方法论但不公开发布分数。

### 3.2 目录结构

```
scripts/compression_eval/
├── DESIGN.md           # 设计文档（fixture 格式、probe 格式、6 维度评分）
├── README.md           # 简短说明
├── run_eval.py         # placeholder（打印 "not implemented" 退出 1）
├── scrub_fixtures.py   # 清洗 pipeline（.jsonl → JSON fixture）
└── fixtures/
    ├── feature-impl-context-priority.json   # 75 msgs / ~17k tokens
    ├── debug-session-feishu-id-model.json   # 59 msgs / ~13k tokens
    └── config-build-competitive-scouts.json # 61 msgs / ~23k tokens
```

### 3.3 Scrubber Pipeline（10 步）

将真实 session（`~/.hermes/sessions/*.jsonl`）转换为公开安全 fixture：

1. `redact_sensitive_text` — API keys、tokens、connection strings
2. 用户路径归一化 — `/home/teknium/` → `/home/user/`
3. 个人 handle 替换 — `Teknium`/`teknium`/`teknium1` → `user`
4. Reasoning scratchpad 剥离 — 移除 `<think>...</think>` 块和 `<REASONING_SCRATCHPAD>` 块
5. 丢弃 `session_meta` 行
6. 平台 user mention 替换 — `<@123456>` → `<@user>`
7. 第一条 user 消息 paraphrase（保留 task intent，移除 personal voice）
8. System prompt 替换为通用 placeholder（避免泄露维护者的 soul/skills/memory 系统块）
9. 孤立空 assistant 消息和 tool 输出截断（> 2000 chars 截断，标注大小）
10. Fixture 总大小 < 150KB/个，< 500KB 目录总量

### 3.4 Probe 格式

```json
{
  "fixture": "401-debug",
  "probes": [
    {
      "id": "recall-error-code",
      "type": "recall",
      "question": "What was the original error code and endpoint?",
      "expected_facts": ["401", "/api/auth/login"]
    },
    {
      "id": "artifact-files-modified",
      "type": "artifact",
      "question": "Which files have been modified in this session?",
      "expected_facts": ["session_store.py", "redis_client.py"]
    },
    {
      "id": "continuation-next-step",
      "type": "continuation",
      "question": "What should we do next?",
      "expected_facts": ["re-run the integration tests", "restart the worker"]
    },
    {
      "id": "decision-redis-approach",
      "type": "decision",
      "question": "What did we decide about the Redis issue?",
      "expected_facts": ["switch to redis-py 5.x", "pooled connection"]
    }
  ]
}
```

**四种 probe 类型**（来自 Factory 方法论）：`recall` / `artifact` / `continuation` / `decision`。`expected_facts` 提供具体锚点而非纯 LLM 主观评分。

**评分维度（6 个）**：
1. Error trace preservation
2. File path fidelity
3. Active task continuity
4. Decision traceability
5. Artifact completeness
6. Tool call recall

### 3.5 CE 借鉴（Phase 3 Structured Extraction）

这是对 CE **最有借鉴价值**的新增设计：

**Phase 3  Structured Extraction 的质量保障缺口**：
- CE 的 `StructuredExtractionService` 同样面临"改了提取模板，不知道结果是否变差"的问题
- 目前没有自动化 probe 测试来验证：
  - User preference extraction 是否保留关键字段
  - Allergy extraction 是否漏掉新增的过敏原
  - 所有字段的语言是否正确

**可迁移的 CE 设计**：
```yaml
# CE Structured Extraction Eval Framework
structured_extraction_eval/
├── probes/
│   ├── user-preference-probes.yaml   # recall/continuation probes
│   ├── allergy-probes.yaml
│   └── cross-turn-probes.yaml
├── fixtures/                         # 清洗后的真实 session 片段
│   └── sample-sessions/
├── scrub_fixtures.py                # 复用 Hermes 的 scrubber 思路
└── run_eval.py                     # LLM grader
```

**核心差异**：Hermes 测压缩质量（压缩后 LLM 能否回答问题），CE 测提取质量（提取结果是否准确、完整、一致）。

---

## §4 `1ef1e4c6` — pre_gateway_dispatch Hook（Gateway 层面，非内存专用）

**相关但不限于内存系统**，作为 CE Gateway 集成参考记录于此。

### 4.1 Hook 设计

在 `gateway/run.py::_handle_message` 中，消息在 auth/pairing 链之前触发：

```python
# 在 internal-event guard 之后，auth 之前
if not is_internal:
    _hook_results = _invoke_hook(
        "pre_gateway_dispatch",
        event=event,
        gateway=self,
        session_store=self.session_store,
    )
    for _result in _hook_results:
        if _result.get("action") == "skip":
            return None  # 丢弃，不触发 pairing flow
        if _result.get("action") == "rewrite":
            event = dataclasses.replace(event, text=_result["text"])
```

**返回结果协议**：
| Action | 效果 |
|--------|------|
| `{"action": "skip", "reason": "..."}` | 丢弃，无回复 |
| `{"action": "rewrite", "text": "..."}` | 替换 `event.text`，继续处理 |
| `{"action": "allow"}` / `None` | 正常 dispatch |

### 4.2 动机场景

- Listen-only group chat（缓冲 ambient 消息，@mention 时折叠）
- Human-handover silent ingest（owner 手动处理聊天时静默记录消息）
- 未经授权 sender 的自定义处理（不触发 pairing-code flow）

### 4.3 CE 借鉴

CE 的 gateway 集成（如 Feishu adapter）可以在消息路由前通过类似 hook 实现：
- 消息过滤（如敏感词检测）
- 消息改写（如指令注入）
- 静默 ingest（不回复但记录到记忆系统）

---

## §5 小结

| Commit | 类型 | 重要性 | 对 CE 价值 |
|--------|------|--------|-----------|
| `93a74f74` | Bug fix | ⭐⭐⭐ | Hindsight 多会话并发场景必现 bug，CE 未来引入 Hindsight 需参考 |
| `f9c6c5ab` | Bug fix | ⭐⭐⭐ | Session resume 数据丢失根因，CE SessionDB 类似风险需排查 |
| `edff2fbe` | Feature | ⭐⭐⭐ | bank_id_template 运行时派生，CE 记忆隔离可参考 |
| `403c82b6` | Feature | ⭐⭐ | 可配置超时模式，CE LLM 调用超时可借鉴 |
| `f1ba2f0c` | Bug fix | ⭐⭐ | 配置生效路径验证，CE 需防止"配置了但没用上" |
| `346601ca` | Bug fix | ⭐⭐ | 缓存 schema 变更后的主动失效，CE 缓存设计需考虑 |
| `9f5c13f8` | Design（未合并）| ⭐⭐⭐⭐ | Compression eval probe methodology，**Phase 3 Structured Extraction 质量保障可直接迁移** |
| `1ef1e4c6` | Feature | ⭐⭐ | Gateway pre-auth hook，CE gateway 消息拦截可参考 |

**核心趋势**：
1. **Hindsight 稳定性冲刺**：5 个 commits 集中修复 shutdown/timeout/resume/bank isolation 问题，Hindsight 正从"能用"向"生产就绪"演进
2. **Compression Eval 基础设施**：从手工调优转向可验证的质量保障，Phase 3 Structured Extraction 可直接复用 probe methodology
3. **Gateway Hook 系统扩展**：`pre_gateway_dispatch` 在 auth 前提供可控拦截点
