# Auxiliary Client — Provider Resolution Chain 深度解析

> **来源**: `agent/auxiliary_client.py` (2615 lines)
> **快照时间**: 2026-04-23
> **前置**: `11-field-review-and-bypass-roadmap.md` §2（辅助模型路由提及）
> **目的**: 深入解析 Hermes 辅助任务 LLM 路由机制，为 BlueCortexCE 提供 provider fallback chain 设计参考

---

## 1. 核心定位：为什么需要 Auxiliary Client

Hermes 主 agent 循环使用用户配置的主模型（如 GPT-5.3、Claude Opus）。但许多**旁路任务**不需要重型模型：

| 任务 | 使用场景 | 典型模型 |
|------|----------|----------|
| `compression` | 上下文压缩摘要 | gemini-3-flash-preview |
| `session_search` | 历史会话搜索 | gemini-3-flash-preview |
| `vision` | 图片/多模态分析 | mimo-v2-omni |
| `web_extract` | 网页内容提取 | gemini-3-flash-preview |
| `flush_memories` | 记忆 Provider 写回 | 任意可用模型 |
| `skills_hub` | 技能发现 | gemini-3-flash-preview |

**核心原则**: 每个辅助任务可独立配置 provider/model，通过统一的 `call_llm()` 入口调用。

### 1.1 架构分层

```
call_llm(task="compression")
  │
  ├─ _resolve_task_provider_model()    ← 任务级配置解析
  │    ├─ explicit args (provider/model/base_url/api_key)
  │    ├─ config.yaml auxiliary.{task}.provider/model
  │    └─ "auto" (full auto-detection)
  │
  ├─ _get_cached_client()              ← 客户端缓存 + 创建
  │    └─ resolve_provider_client()    ← 核心路由
  │         ├─ _try_openrouter()
  │         ├─ _try_nous()
  │         ├─ _try_custom_endpoint()
  │         ├─ _try_codex()
  │         ├─ _try_anthropic()
  │         └─ _resolve_api_key_provider()
  │
  └─ _build_call_kwargs()              ← 请求参数适配
       └─ client.chat.completions.create(**kwargs)
```

---

## 2. Auto-Detection Chain（`_resolve_auto`）

### 2.1 核心逻辑

```python
# Step 1: 非聚合器主 provider → 直接用主模型
if main_provider not in _AGGREGATOR_PROVIDERS:
    return resolve_provider_client(main_provider, main_model)

# Step 2: 聚合器 fallback chain
for label, try_fn in _get_provider_chain():
    client, model = try_fn()
    if client is not None:
        return client, model
```

**关键设计**: 如果用户的主 provider 不是聚合器（OpenRouter/Nous），直接复用主 provider 的凭证做辅助任务。这确保了 Alibaba、DeepSeek、Z.AI 等直接 provider 用户不需要额外配置 OpenRouter key。

### 2.2 Provider Chain 顺序

```
1. OpenRouter    (OPENROUTER_API_KEY)
2. Nous Portal   (~/.hermes/auth.json active provider)
3. Custom        (config.yaml model.base_url + OPENAI_API_KEY)
4. Codex OAuth   (Responses API via chatgpt.com)
5. Anthropic     (native Anthropic API)
6. API-key pool  (z.ai, Kimi, MiniMax 轮询)
7. None          (返回 None, None)
```

### 2.3 Aggregator Providers 集合

```python
_AGGREGATOR_PROVIDERS = frozenset({
    "openrouter", "nous", "ai-gateway", "opencode-zen",
    "opencode-go", "kilocode", "copilot"
})
```

聚合器 = 用户的凭证只通向一个中间层，实际模型由中间层选择。非聚合器 = 用户直接持有 provider 凭证。

---

## 3. Provider-Specific Adapters

### 3.1 Codex OAuth → Responses API 适配

Codex 使用完全不同的 API（Responses API），但 auxiliary 消费者期望 `client.chat.completions.create()`。适配器 `_CodexCompletionsAdapter` 做了完整的格式转换：

```
chat.completions format          Responses API format
─────────────────────────        ──────────────────────
system message            →      instructions field
content: [{type: "text"}] →      [{type: "input_text"}]
content: [{type: "image_url"}] → [{type: "input_image"}]
tools.function.name       →      tools[].name (flat)
max_tokens                →      (omitted — Codex 不支持)
temperature               →      (omitted — Codex 不支持)
```

**关键细节**:
- 流式响应使用 `responses.stream()` 收集 delta events
- 空 output 回填：从 stream events 重建 `final.output`
- function_call 与 text delta 互斥处理

### 3.2 Anthropic Native 适配

`_AnthropicCompletionsAdapter` 将 OpenAI 格式转换为 Anthropic Messages API：
- `build_anthropic_kwargs()` 处理 system 分离、tool 格式转换
- `normalize_anthropic_response()` 将 Anthropic 响应转回 OpenAI choice 格式
- 支持 OAuth token (Claude Code credentials)

### 3.3 API-Key Provider Pool

`_resolve_api_key_provider()` 按 `PROVIDER_REGISTRY` 顺序轮询：

| Provider ID | Aux Model | 特殊处理 |
|-------------|-----------|----------|
| gemini | gemini-3-flash-preview | — |
| zai | glm-4.5-flash | — |
| kimi-coding | kimi-k2-turbo-preview | User-Agent header |
| minimax | MiniMax-M2.7 | — |
| anthropic | claude-haiku-4-5 | 需显式配置 |
| ai-gateway | google/gemini-3-flash | — |

**Anthropic 特殊逻辑**: 只有用户显式配置了 Anthropic 凭证才会使用，避免静默使用 Claude Code 的 token。

---

## 4. Payment / Connection Error Fallback

### 4.1 触发条件

```python
def call_llm(...):
    try:
        return client.chat.completions.create(**kwargs)
    except Exception as first_err:
        # max_tokens → max_completion_tokens 重试
        # 402 / credit error → payment fallback
        # DNS / connection refused → connection fallback
```

### 4.2 Fallback 规则

- **只在 auto 模式下 fallback**: 用户显式指定 provider 时不 fallback（`is_auto = resolved_provider in ("auto", "", None)`）
- **max_tokens 兼容**: 先尝试 `max_tokens`，报错后改用 `max_completion_tokens`（OpenAI 新模型要求）
- **402 / 连接错误**: 遍历 `_get_provider_chain()` 找下一个可用 provider

### 4.3 Credit Exhaustion 典型场景

1. 用户 OpenRouter 余额耗尽 → 自动切换到 Codex OAuth
2. Codex OAuth token 过期 → 切换到 Anthropic native
3. 所有 provider 都不可用 → RuntimeError

---

## 5. Per-Task Configuration（`config.yaml`）

```yaml
auxiliary:
  compression:
    provider: auto          # 或 "openrouter", "nous", "custom"
    model: gemini-3-flash   # 可选，覆盖默认
    timeout: 120            # 秒
  vision:
    provider: xiaomi
    model: mimo-v2-omni
  session_search:
    provider: auto
    timeout: 60
```

**优先级**: explicit args > config.yaml > auto-detection

---

## 6. Vision 特殊处理

Vision 有独立的解析路径 `resolve_vision_provider_client()`:

1. 主 provider 是否支持 vision → 直接用
2. OpenRouter → 自动选 vision 模型
3. Nous Portal → mimo-v2-omni (免费层)
4. Codex OAuth → gpt-5.2-codex (支持 vision via Responses)
5. Anthropic → claude-haiku-4-5
6. Custom endpoint → 本地 vision 模型（Qwen-VL, LLaVA 等）

**Provider Vision Model 覆盖**:
```python
_PROVIDER_VISION_MODELS = {
    "xiaomi": "mimo-v2-omni",
    "zai": "glm-5v-turbo",
}
```

---

## 7. 客户端缓存（`_get_cached_client`）

辅助客户端被缓存（`_client_cache` dict），避免每次 `call_llm()` 调用都重建 client。缓存 key 是 `(provider, model, base_url, api_mode, main_runtime_hash)`。

**缓存失效**: provider/model/base_url 变化时自动失效。

---

## 8. 对 BlueCortexCE 的借鉴价值

### 8.1 Provider Resolution Chain

CE 当前没有类似机制。如果 CE 需要支持多种 LLM provider（OpenAI、Gemini、本地模型等），可以参考：

```
CE auxiliary chain:
  1. 用户配置的主 provider（直接复用）
  2. OpenRouter (aggregator)
  3. 本地 Ollama/vLLM
  4. None
```

### 8.2 Per-Task Model 选择

CE 的 `ContextService.generate()` 当前硬编码使用主模型做摘要。可以引入类似机制：
- 压缩任务 → 便宜模型（flash/haiku）
- Embedding → 专用 embedding 模型
- 结构化提取 → 支持 JSON mode 的模型

### 8.3 Error-Driven Fallback

CE 的 LLM 调用当前没有 fallback 机制。如果主 provider 402/503，整个操作失败。可以参考 Hermes 的 `_try_payment_fallback`：

```java
// CE conceptual implementation
try {
    return primaryClient.chat(prompt);
} catch (PaymentException | ConnectionException e) {
    if (isAutoMode()) {
        return fallbackChain.next().chat(prompt);
    }
    throw e;
}
```

### 8.4 Codex Responses API 适配模式

如果 CE 需要支持非 OpenAI 兼容的 API（如 Anthropic、Gemini native），adapter 模式很有价值：消费者始终调用 `chat.completions.create()`，adapter 负责格式转换。

---

## 9. 关键代码路径

| 功能 | 文件 | 行号 |
|------|------|------|
| `call_llm()` 入口 | `agent/auxiliary_client.py` | 2263 |
| `_resolve_auto()` | `agent/auxiliary_client.py` | 1126 |
| `resolve_provider_client()` | `agent/auxiliary_client.py` | 1262 |
| `_try_payment_fallback()` | `agent/auxiliary_client.py` | 1079 |
| `_resolve_task_provider_model()` | `agent/auxiliary_client.py` | 2031 |
| Codex adapter | `agent/auxiliary_client.py` | 295-430 |
| Anthropic adapter | `agent/auxiliary_client.py` | 495-580 |
| Vision resolver | `agent/auxiliary_client.py` | 1662 |
| API-key pool轮询 | `agent/auxiliary_client.py` | 681 |

---

## 10. 与 ContextCompressor 的关系

ContextCompressor 的 `_generate_summary()` 通过 `call_llm(task="compression")` 调用辅助模型。这意味着：

1. 压缩使用的模型完全由 `auxiliary.compression` 配置决定
2. 如果 compression provider 挂了，`call_llm` 自动 fallback
3. Summary model fallback: 如果配置的 summary model 404/503，ContextCompressor 自动降级到主模型（`_summary_model_fallen_back` flag）

**关键设计**: 辅助任务的模型选择与主 agent 完全解耦，主模型变更不影响压缩/搜索等旁路任务。
