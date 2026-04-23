# Hermes Context 文件扫描机制深度解析 — BlueCortexCE 缺口

> **日期**：2026-04-24（cron 巡检）
> **定位**：补充 `05-ce-context-security-gap-inventory.md` §2 中"AGENTS.md / SOUL.md class file scanning"条目，提供完整源码锚点与 CE 对照。
> **关联**：上游快照 [`12`](../60-evolution/12-upstream-hermes-agent-memory-snapshot.md)；CE 缺口总表 [`05`](05-ce-context-security-gap-inventory.md)。

---

## 1. 概述：两套扫描机制的分工

Hermes 有**两套独立的扫描防线**，分别守护不同类型的上下文文件：

| 扫描函数 | 文件 | 触发时机 | 守护对象 |
|---------|------|----------|----------|
| `_scan_memory_content` | `tools/memory_tool.py` | MEMORY.md / USER.md **写入前** | 持久化 curated memory 内容 |
| `_scan_context_content` | `agent/prompt_builder.py` | AGENTS.md / SOUL.md / .cursorrules **读取注入前** | 上下文文件内容 |

**BlueCortexCE 现状**：**均缺失**。CE 的 `IngestionController.handleUserPrompt` 仅做长度截断，无任何 pattern 扫描。

---

## 2. `_scan_memory_content` — 记忆写入扫描

### 2.1 源码位置

`tools/memory_tool.py:90`（约 30 行）

### 2.2 扫描 patterns

```python
_MEMORY_THREAT_PATTERNS = [
    # Prompt injection
    (r'ignore\s+(previous|all|above|prior)\s+instructions', "prompt_injection"),
    (r'you\s+are\s+now\s+', "role_hijack"),
    (r'do\s+not\s+tell\s+the\s+user', "deception_hide"),
    (r'system\s+prompt\s+override', "sys_prompt_override"),
    (r'disregard\s+(your|all|any)\s+(instructions|rules|guidelines)', "disregard_rules"),
    (r'act\s+as\s+(if|though)\s+you\s+(have\s+no|don\'t\s+have)\s+(restrictions|limits|rules)', "bypass_restrictions"),
    # Exfiltration via curl/wget with secrets
    (r'curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)', "exfil_curl"),
    (r'wget\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)', "exfil_wget"),
    (r'cat\s+[^\n]*(\.env|credentials|\.netrc|\.pgpass|\.npmrc|\.pypirc)', "read_secrets"),
    # Persistence via shell rc
    (r'authorized_keys', "ssh_backdoor"),
    (r'\$HOME/\.ssh|\~/\.ssh', "ssh_access"),
    (r'\$HOME/\.hermes/\.env|\~/\.hermes/\.env', "hermes_env"),
    # HTML comment injection
    (r'<!--[^>]*(?:ignore|override|system|secret|hidden)[^>]*-->', "html_comment_injection"),
    # Hidden div injection
    (r'<\s*div\s+style\s*=\s*["\'][\s\S]*?display\s*:\s*none', "hidden_div"),
]

_MEMORY_INVISIBLE_CHARS = {
    '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',  # Zero-width + BOM
    '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',    # Unicode bidirectional override
}
```

### 2.3 行为

- 写入 MEMORY.md / USER.md 前调用
- 发现 threat pattern → **拒绝写入**，返回错误信息
- 发现 invisible unicode → **拒绝写入**
- 日志输出 `logger.warning("Memory file %s blocked: %s", filename, ", ".join(findings))`
- 阻断而非消毒（block, not sanitize）

### 2.4 CE 对照

CE 的 `ObservationEntity` / `SummaryEntity` 写入路径（`IngestionController.handleUserPrompt` 等）**无任何 pattern 扫描**。攻击者可向记忆数据库注入 prompt injection 内容，这些内容会在后续 `/api/context/generate` 等 API 调用时被拼入 LLM 上下文。

---

## 3. `_scan_context_content` — 上下文文件注入前扫描

### 3.1 源码位置

`agent/prompt_builder.py:55`（约 25 行）

### 3.2 扫描 patterns

```python
_CONTEXT_THREAT_PATTERNS = [
    (r'ignore\s+(previous|all|above|prior)\s+instructions', "prompt_injection"),
    (r'do\s+not\s+tell\s+the\s+user', "deception_hide"),
    (r'system\s+prompt\s+override', "sys_prompt_override"),
    (r'disregard\s+(your|all|any)\s+(instructions|rules|guidelines)', "disregard_rules"),
    (r'act\s+as\s+(if|though)\s+you\s+(have\s+no|don\'t\s+have)\s+(restrictions|limits|rules)', "bypass_restrictions"),
    (r'<!--[^>]*(?:ignore|override|system|secret|hidden)[^>]*-->', "html_comment_injection"),
    (r'<\s*div\s+style\s*=\s*["\'][\s\S]*?display\s*:\s*none', "hidden_div"),
    (r'translate\s+.*\s+into\s+.*\s+and\s+(execute|run|eval)', "translate_execute"),
    (r'curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)', "exfil_curl"),
    (r'cat\s+[^\n]*(\.env|credentials|\.netrc|\.pgpass)', "read_secrets"),
]

_CONTEXT_INVISIBLE_CHARS = {
    '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',
    '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',
}
```

### 3.3 行为

- 在 `build_context_files_prompt()` 中调用（`load_soul_md()` / `_load_agents_md()` / `_load_cursorrules()` 等）
- 读取文件后、注入 system prompt **前**扫描
- 发现威胁 → **返回占位文本**而非原文：
  ```python
  return f"[BLOCKED: {filename} contained potential prompt injection ({', '.join(findings)}). Content not loaded.]"
  ```
- 日志：`logger.warning("Context file %s blocked: %s", filename, ", ".join(findings))`

### 3.4 覆盖的文件类型

| 文件 | 扫描函数 |
|------|----------|
| SOUL.md | `load_soul_md()` → `_scan_context_content(content, "SOUL.md")` |
| AGENTS.md | `_load_agents_md()` → `_scan_context_content(content, rel)` |
| .cursorrules | `_load_cursorrules()` → `_scan_context_content(content, name)` |
| .cursor/rules/*.mdc | 同上 |
| HERMES.md / .hermes.md | `_load_hermes_md()` → `_scan_context_content(content, name)` |

### 3.5 CE 对照

CE 的 SOUL.md / AGENTS.md 等文件在读取后直接拼入 system prompt，**没有任何 pattern 扫描**。虽然 CE 部署场景下这些文件可能由客户端管理，但如果 CE 提供文件预览或 API 注入功能，则存在与 Hermes 相同的攻击面。

---

## 4. 两套扫描的共同设计原则

1. **阻断优于消毒**：发现 injection 即 block/fallback，不尝试修复
2. **零宽字符 + RTL override 双向覆盖**：覆盖常用隐写手段
3. **针对 LLM attack patterns**：不只防 XSS/SQLi，专防 prompt injection
4. **日志可追溯**：所有 block 均有 warning 日志，含文件名和 findings
5. **轻量**：正则匹配，无 ML / embedding 计算

---

## 5. BlueCortexCE 实施建议

| 优先级 | 措施 | 参照 |
|--------|------|------|
| **P0（立即）** | 在 `IngestionController.handleUserPrompt` 添加 `_scan_memory_content` 等效扫描 | Hermes `tools/memory_tool.py:90` |
| **P1（近期）** | 如果 CE 有 AGENTS.md/SOUL.md 注入路径，添加 `_scan_context_content` 等效扫描 | Hermes `agent/prompt_builder.py:55` |
| **P1** | 将 invisible unicode 检测提取为公共工具类（`InvisibleCharUtil`） | 两套扫描共享 `_CONTEXT_INVISIBLE_CHARS` 思想 |
| **P2** | 考虑 threat pattern 列表可配置化（而非硬编码） | 便于安全团队更新 pattern 库 |

**注意**：CE 的 TS 层 `tag-stripping.ts` 剥离 `<claude-mem-context>` 等标签，但**不是** prompt injection 扫描——它是防止**已存储内容被重复注入**到 UI，不是防止恶意内容进入 LLM 上下文。两者的 threat model 不同。

---

## 6. 与 `05-ce-context-security-gap-inventory.md` 的关系

本文档深化该文件 §2 中"AGENTS.md / SOUL.md class file scanning"条目，提供：
- 两套扫描的源码完整清单（`memory_tool.py` + `prompt_builder.py`）
- CE 等效实现缺失的具体代码锚点
- 实施优先级建议

本文件不重复该文档的 CE 现有能力盘点，两者互补。
