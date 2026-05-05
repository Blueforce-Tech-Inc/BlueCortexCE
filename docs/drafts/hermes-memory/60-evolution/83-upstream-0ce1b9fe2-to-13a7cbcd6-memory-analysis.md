# 上游新提交分析（0ce1b9fe2 → 13a7cbcd6，54 commits）

**时间**：2026-05-05 18:12 CST
**扫描范围**：`0ce1b9fe2..origin/main`（54 commits）
**上次扫描起点**：`0ce1b9fe2`（doc 68 覆盖）
**下次扫描起点**：`origin/main` `13a7cbcd6`

---

## 总览

54 个新 commit 中，**6 个记忆/上下文系统相关**，其余为 Teams/Telegram/IRC 集成、TUI 改进、periodic memory logging、凭据 redaction 等。

| 优先级 | Commit | 主题 |
|--------|--------|------|
| ⭐ P1 | `1e6285c53` | Compression Eval Harness（完整 eval 框架） |
| ⭐ P1 | `72d53e14a` | Summary Pipeline Credential Redaction |
| ⭐ P2 | `02e328c41` | Image Token Charging in Compressor |
| ⭐ P2 | `4a3eac5fe` | `/recap` Slash Command（无 LLM Session 摘要） |
| P2 | `6366fb9c8` | Periodic Gateway Memory Logging |
| P3 | `319141a0d` | session_search: None tool_name Truncation Fix |

---

## ⭐ P1 — Compression Eval Harness（1e6285c53）

完整文档：→ [`82`](82-compression-eval-harness-and-scrubber-pipeline.md)

核心：18 个文件、33 单元测试、两阶段（Continuation+Grading）LLM 评估框架、六维 rubric、9 步 scrubber 管道（将生产 JSONL 转换为公开安全 fixture）。

**CE 直接借鉴**：Phase 3 Structured Extraction 质量评估可直接复用 rubric + grader + report 框架。

---

## ⭐ P1 — Summary Pipeline Credential Redaction（72d53e14a）

**Commit**：`72d53e14a`（2026-04-19）
**来源**：Port from `openclaw/openclaw#67801`

**问题**：ContextCompressor 的 summarizer prompt 要求模型保留"特定值"（文件路径、命令、错误信息等）以便生成具体交接。但这个指令同时导致 API key、bearer token、env var 赋值语句通过工具输出（terminal / read_file / curl -v）被**逐字复制**到持久化摘要，并在每次压缩时重新注入。

**修复**：在三个注入点应用 `agent.redact.redact_sensitive_text`：

| 注入点 | 说明 |
|--------|------|
| Serializer 输出（主要防御） | `_summarize()` 中序列化后、发送给 summarizer LLM 前 |
| 迭代压缩时 previous-summary 重新注入 | `_generate_summary()` 的 `self._previous_summary` 回注路径 |
| LLM 返回的 summary 存储前 | 存入 `_previous_summary` 前 |

**测试**：6 个回归用例覆盖 API key 前缀、env 赋值、Authorization header、JSON token 字段、非秘密内容保留、summarizer echo 防御。

**CE 借鉴**：`StructuredExtractionService` 的 extraction output 如果写入 SessionEntity，也应该在存储前做 secrets redaction。尤其是 extraction prompt 中可能要求保留的"特定值"（如用户偏好字段）需要审计是否包含敏感信息。

---

## ⭐ P2 — Image Token Charging in Compressor（02e328c41）

**Commit**：`02e328c41`（2026-04-27）

**两个改进**：

### 1. 图像大小封顶 + Pillow 缩放（`build_native_content_parts`）

- 每张图片 20MB 上限（匹配 `vision_tools._MAX_BASE64_BYTES`，最严格的 provider 是 Gemini inline data）
- 首次尝试用 `vision_tools._resize_image_for_vision`（基于 Pillow）缩放到 5MB
- Pillow 缺失或缩放仍超限时，图片丢弃并在 `skipped[]` 中报告，调用方回退到文本 enrichment

### 2. 图像 Token 计量（`context_compressor.py`）

- 新常量 `_IMAGE_TOKEN_ESTIMATE = 1600`（flat，匹配 Claude Code 的 `IMAGE_TOKEN_ESTIMATE`）
- 新 helper `_content_length_for_budget(raw_content)`：统计文本 part 的 `len(text)` 加上每张图片的 `_IMAGE_CHAR_EQUIVALENT`（1600×4=6400 chars）
- 两个 tail-cut 位置（`_prune_old_tool_results` L527 和 `_find_tail_cut_by_tokens` L1126）都调用该 helper，防止多图对话逃逸压缩预算

**CE 借鉴**：`StructuredExtractionService` 计量输入 token 时，如果有图像输入（如用户上传截图），应使用固定 token 估算而非按字符计。

---

## ⭐ P2 — `/recap` Slash Command（4a3eac5fe）

**Commit**：`4a3eac5fe`（2026-05-01）

**灵感来源**：Claude Code `/recap`（v2.1.114，April 2026）

**功能**：为当前 session 生成紧凑的近期活动摘要（turn 数、工具使用、文件操作、最后用户提问、最后助手回复）。

**关键设计原则**：
- **纯本地计算**：从内存对话历史或 gateway transcript 计算，**无 LLM 调用**，无辅助模型，无 prompt-cache 失效
- **跨平台**：CLI 和所有 gateway 平台（Telegram / Discord / Slack / …）统一通过 `hermes_cli.session_recap.build_recap` 辅助函数
- **工具词汇定制**：识别文件编辑工具（patch / write_file / read_file / skill_manage / skill_view）并列出涉及路径
- **运行中可用**：加入 `ACTIVE_SESSION_BYPASS_COMMANDS` 和 gateway/run.py 的 Level-2 早期拦截，可在对 agent 发指令时读取当前状态

**源码**：`hermes_cli/session_recap.py`（316 行），`cli.py` 新增 `_handle_recap_command()`（36 行）

**CE 借鉴**：CE 的 `/api/sessions/{id}/summary` 可以实现类似的**无 LLM 轻量摘要**，基于 session 消息结构计算（turn 数 + 工具调用统计 + 涉及文件列表）。成本：零 API 调用，毫秒级响应。

---

## P2 — Periodic Gateway Memory Logging（6366fb9c8）

**Commit**：`6366fb9c8`（2026-04-29）
**来源**：Port from `cline/cline#10343`

**功能**：在 `agent.log` / `gateway.log` 中每 N 分钟（默认 5 分钟）输出 `[MEMORY] rss=...MB ...` 行，供时间序列分析发现长期 gateway 进程中的内存泄漏。

**新增模块**：`gateway/memory_monitor.py`

- daemon thread：启动时记录 baseline，停止时输出 final snapshot
- 优先使用 `resource.getrusage()`（stdlib），fallback 到 `psutil`
- 两者都不可用时以一条 WARNING 自禁用
- 配置：`hermes_cli/config.py` 中 `logging.memory_monitor { enabled, interval_seconds }`

**CE 借鉴**：CE Java 后端可以添加类似机制，通过 JMX 或定时日志输出 heap 使用量。关键：独立于业务逻辑的巡检线程，输出 grep-friendly 格式供监控系统采集。

---

## P3 — session_search: None tool_name Truncation Fix（319141a0d）

**Commit**：`319141a0d`（2026-04-30）

**问题**：`_format_conversation` 在 `role == "TOOL" and tool_name` 条件下才做截断。如果 TOOL row 的 `tool_name` 为 None，会落入 generic 分支，以**未截断**形式渲染为 `[TOOL]: <huge-blob>`，淹没实际对话内容，影响 summarization 质量。

**修复**：所有 TOOL row 都截断；`tool_name` 缺失时渲染为 `[TOOL]`。

**CE 借鉴**：CE 的 session search 或 summary 生成时，如果遇到 tool result 缺失字段，应做防御性截断而非渲染原始 blob。

---

## 上游源码重构（65ca3ba93）

**Commit**：`65ca3ba93`

**变更**：`git mv` 所有源码文件到 `hermes_agent/` 子包。后续源码分析时行号映射需重新建立。上次重构（`193f3b833`）已记录从 `acp_adapter/` 到 `hermes_agent/acp/` 的迁移，本轮扩展到全量。

**影响**：历史分析文档中的行号引用（如 `context_compressor.py L527`）需用新包路径 `hermes_agent/context_compressor.py` 重新验证。

---

## 其他相关提交（非记忆系统核心）

| Commit | 主题 | 说明 |
|--------|------|------|
| `d755601d2` / `c9ebf0c93` | Discord IDs in SessionSource | `guild_id` / `parent_chat_id` / `message_id` 加入 SessionSource |
| `8877688b3` | Hindsight: preserve timeout on reconfig | 重配置时保留自定义 timeout |
| `9d42aca29` | Hindsight: preserve LLM key on blank setup | 重新运行时保留现有 LLM key |
| `bb5c3c107` | TUI 主题 token 迁移 | 非记忆相关 |
| `4a95029e6` | `invalid-return-type` 诊断修复 | 全局类型诊断，非记忆相关 |
| `4b1634197` | rewrite all imports for `hermes_agent` package | 全量 import 重写，配合 `65ca3ba93` |
| `193f3b833` | `git mv acp_adapter/` → `hermes_agent/acp/` | ACP adapter 包迁移 |

---

## 文档更新记录

| 文档 | 更新内容 |
|------|----------|
| [`70`](70-compression-eval-harness-and-scrubber-pipeline.md) | **新增**（5493 字节）— Compression Eval Harness + Scrubber Pipeline 完整分析 |
| `index.md` | 条目 70 + 71 追加 |
| `AGENT.md` | 预警表追加 doc 70（5493 字节）|
| 本文档 | **新增**（~8000 字节）— 54 commits 分析 |
