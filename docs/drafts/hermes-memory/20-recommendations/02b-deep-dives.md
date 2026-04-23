# BlueCortexCE 借鉴建议 — 深度专题

> **来源**：原 `02-bluecortexce-recommendations.md` §11–§15（preemptive split；2026-04-24）。

本文档为原 doc 02 的深度专题集，涵盖 SessionSearch 成本控制、生命周期 Hook、Hindsight 知识图谱、AuxiliaryClient 路由链、Honcho Dialectic 五项专题。

---

## 11. SessionSearch LLM Summarization 成本控制策略

### 11.1 关键参数（hard-coded 守卫）

```python
# tools/session_search_tool.py:23-24
MAX_SESSION_CHARS = 100_000     # 单个 session 截断上限
MAX_SUMMARY_TOKENS = 10_000     # LLM 输出 hard cap
limit = min(limit, 5)           # 最大 5 个 session
```

**三层硬性限制**：Session 数量 cap 5 / 输入长度 100k chars / 输出 10k tokens。

### 11.2 并行 summarization

```python
async def _summarize_all():
    coros = [_summarize_session(...) for ...]
    return await asyncio.gather(*coros, return_exceptions=True)
```

全并行，5 个 session 总延迟 ≈ 1 个 LLM 调用。

### 11.3 超时保护（60s）+ 重试（3次 + 指数退避）

### 11.4 LLM 失败降级（Raw Preview Fallback）

```python
if result:
    entry["summary"] = result
else:
    preview = conversation_text[:500] + "\n...[truncated]"
    entry["summary"] = f"[Raw preview — summarization unavailable]\n{preview}"
```

### 11.5 Auxiliary Client 7级路由链

OpenRouter → Nous Portal → Custom endpoint → Codex OAuth → Native Anthropic → Direct API-key → None

### 11.6 与 BlueCortexCE 对比

| 维度 | Hermes | CE |
|------|--------|-----|
| 并行化 | asyncio.gather | 无 |
| 超时保护 | 60s | 无 |
| 重试 | 3次+退避 | 无 |
| 失败降级 | Raw preview | 无 |

---

## 14.5 CE 确认缺口：`LlmService` 无任何 Fallback 链（2026-04-24 代码核实）

**结论**：CE `LlmService` 仅单 Client，无 fallback；失败时返回 `LlmResponse.empty()`（content="", tokens=0），对调用方无感知。

**关键代码**（`backend/.../service/LlmService.java`）：

```java
// 构造函数：只选第一个可用 Client，无 fallback 链
public LlmService(List<ChatClient> chatClients) {
    this.chatClient = chatClients.stream()
        .filter(c -> !c.getClass().getSimpleName().contains("Placeholder"))
        .findFirst();   // ← 第一个即唯一，无备选
    // ...
}

// chatCompletionWithUsage：异常时返回空响应，不重试不降级
public LlmResponse chatCompletionWithUsage(String systemPrompt, String userPrompt) {
    try {
        ChatResponse response = chatClient.orElseThrow(...).prompt()
            .system(systemPrompt).user(userPrompt).call().chatResponse();
        // ...
        return new LlmResponse(content, totalTokens);
    } catch (Exception e) {
        log.error("LLM call failed: {}", e.getMessage());
        return LlmResponse.empty();  // ← 静默失败，调用方无法区分
    }
}
```

**对比 Hermes AuxiliaryClient**（`agent/auxiliary_client.py`）：
- 7 级 Provider 降级链（OpenRouter → Nous Portal → Custom → Codex → Anthropic → Direct → None）
- Payment Error Recovery（429/402 触发下一 Provider）
- Per-Task 模型覆盖（`task_config.model` 透传）
- Auxiliary Model 专用路由（压缩/嵌入/session search 各用独立模型）

**CE 受影响路径**：
| 服务 | LLM 用途 | 失败后果 |
|------|----------|----------|
| `SummaryGenerationService` | 生成摘要 | 返回空摘要，影响 timeline |
| `MemoryRefineService` | 记忆精化 | 静默跳过 |
| `StructuredExtractionService` | 结构化提取 | 静默返回部分结果 |
| `LlmQualityScorer` | 质量评分 | 回退到 rule-based |
| `AgentService` | Agent 生成 | 返回空消息 |

**可执行行动**：
1. **短期**：为 `LlmService.chatCompletionWithUsage` 增加重试机制（3次 + 指数退避）
2. **中期**：仿 `AuxiliaryClient` 实现 `ChatClient` 列表的 Provider 链式调用
3. **长期**：per-task 模型选择（压缩用便宜模型，embedding 用专用模型）

---

## 12. 生命周期 Hook 完整集成分析

5个 Hook：`on_turn_start` / `on_pre_compress` / `on_memory_write` / `on_delegation` / `on_session_end`。

CE 缺少 `on_pre_compress` 和 `on_delegation` 等效。

---

## 13. Hindsight Provider — 知识图谱 + 实体消歧架构

TEMPR 四路检索：最新/最近/最常提及/最相关。详见 [`60-evolution/22-hindsight-knowledge-graph-deep-dive.md`](../60-evolution/22-hindsight-knowledge-graph-deep-dive.md)。

---

## 14. AuxiliaryClient 完整路由链解析

7级 Fallback 链。详见 [`60-evolution/23-auxiliary-client-resolution-chain.md`](../60-evolution/23-auxiliary-client-resolution-chain.md)。

---

## 15. Honcho Dialectic — Peer Q&A + Observation 模式

Honcho 是"对等体视角"（Peer），Hindsight 是"记忆库视角"（所有记忆）。CE 当前是简单向量检索，缺少 LLM 合成推理层。

**最高优先级借鉴**：/api/context/generate 本质是 Dialectic Query 的自托管版，应增加 LLM 合成推理层。

---
