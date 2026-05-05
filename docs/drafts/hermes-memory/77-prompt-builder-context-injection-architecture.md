# Hermes Agent Prompt Builder 与系统提示词组装架构 — 深度解析

> **日期**：2026-05-05
> **目的**：深度分析 `prompt_builder.py`（1180 行）的上下文文件扫描机制、`_build_system_prompt()` 系统提示词组装流程，以及与 `context_engine.py` 双扫描防护体系的对照；提炼 BlueCortexCE 可落地借鉴。
> **上游源码**：`agent/prompt_builder.py` + `run_agent.py` `_build_system_prompt()` + `agent/context_engine.py`

---

## 1. 架构定位

Hermes 的系统提示词不是静态模板，而是**多层可组合块（layered blocks）**，由 `prompt_builder.py` 和 `run_agent.py` 的 `_build_system_prompt()` 协作完成组装：

| 层次 | 来源 | 说明 |
|------|------|------|
| 1 | SOUL.md / DEFAULT_IDENTITY | Agent 身份层（`load_soul_md()`） |
| 2 | HERMES_AGENT_HELP_GUIDANCE | 指向 `hermes-agent` skill 的帮助提示 |
| 3 | Tool-aware Behavioral Guidance | 工具感知行为引导（`MEMORY_GUIDANCE` / `SESSION_SEARCH_GUIDANCE` / `SKILLS_GUIDANCE` / `KANBAN_GUIDANCE`） |
| 4 | Nous Subscription Prompt | Nous 订阅提示 |
| 5 | Tool Use Enforcement | 模型特定执行纪律（GPT/Codex vs Gemini/Gemma） |
| 6 | Memory Store Frozen Snapshot | 内置 MemoryStore 快照（`<memory-context>` 围栏） |
| 7 | External Memory Provider Block | 外部 Provider 系统提示块（`MemoryManager.build_system_prompt()`） |
| 8 | Skills Manifest | Skills 索引（两级缓存：进程内 LRU + 磁盘快照） |
| 9 | Context Files | 上下文文件（AGENTS.md / .cursorrules 等） |
| 10 | Timestamp / Session Metadata | 时间戳 + Session ID + Model + Provider |
| 11 | Alibaba Model Workaround | 特定 Provider 模型名 workaround |
| 12 | Environment Hints | 执行环境提示（WSL / Termux 等） |
| 13 | Platform Formatting Hint | 平台格式提示（Telegram / WhatsApp 等） |

**关键设计原则**：
- **冻结快照（Frozen Snapshot）**：系统提示词在 Session 开始时组装一次，缓存在 `self._cached_system_prompt`，压缩事件后重建，最大化 prefix cache 命中
- **Tool-aware 引导**：只有对应工具在 `valid_tool_names` 中时才注入相关引导文本
- **Provider 兼容**：外部 Provider 可通过 `system_prompt_block()` 方法贡献自己的系统提示块

---

## 2. `prompt_builder.py` — 上下文文件注入扫描

### 2.1 威胁模式库

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
```

**10 类攻击模式**覆盖：指令忽略 / 身份欺骗 / 系统提示覆盖 / HTML 注释隐藏 / CSS 隐藏 / 翻译执行 / 凭证窃取。

### 2.2 不可见 Unicode 字符集

```python
_CONTEXT_INVISIBLE_CHARS = {
    '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',   # Zero-width chars
    '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',   # Directional override chars
}
```

### 2.3 `_scan_context_content()` 扫描与阻断逻辑

```python
def _scan_context_content(content: str, filename: str) -> str:
    findings = []
    # Check invisible unicode
    for char in _CONTEXT_INVISIBLE_CHARS:
        if char in content:
            findings.append(f"invisible unicode U+{ord(char):04X}")
    # Check threat patterns
    for pattern, pid in _CONTEXT_THREAT_PATTERNS:
        if re.search(pattern, content, re.IGNORECASE):
            findings.append(pid)
    if findings:
        logger.warning("Context file %s blocked: %s", filename, ", ".join(findings))
        return f"[BLOCKED: {filename} contained potential prompt injection ({', '.join(findings)}). Content not loaded.]"
    return content
```

**特点**：
- 阻断式（Block）而非截断式：发现注入后返回占位符文本，不尝试净化
- 完整扫描：正则匹配覆盖所有 10 类攻击模式
- Unicode 扫描：显式遍历 10 种零宽/方向覆盖字符

### 2.4 扫描调用点

`_scan_context_content()` 被 `build_context_files_prompt()` 调用，扫描 AGENTS.md、.cursorrules 等上下文文件。**不扫描** SOUL.md（SOUL.md 作为身份层走独立 `load_soul_md()` 路径）。

---

## 3. 系统提示词组装 — `_build_system_prompt()` 全流程

### 3.1 整体流程（run_agent.py:4870）

```python
def _build_system_prompt(self, system_message: str = None) -> str:
    # 1. SOUL.md 或 DEFAULT_IDENTITY
    if self.load_soul_identity or not self.skip_context_files:
        _soul_content = load_soul_md()
        if _soul_content:
            prompt_parts = [_soul_content]
            _soul_loaded = True
    if not _soul_loaded:
        prompt_parts = [DEFAULT_AGENT_IDENTITY]

    # 2. Help guidance
    prompt_parts.append(HERMES_AGENT_HELP_GUIDANCE)

    # 3. Tool-aware behavioral guidance
    tool_guidance = []
    if "memory" in self.valid_tool_names:
        tool_guidance.append(MEMORY_GUIDANCE)
    if "session_search" in self.valid_tool_names:
        tool_guidance.append(SESSION_SEARCH_GUIDANCE)
    if "skill_manage" in self.valid_tool_names:
        tool_guidance.append(SKILLS_GUIDANCE)
    if "kanban_show" in self.valid_tool_names:
        tool_guidance.append(KANBAN_GUIDANCE)
    if tool_guidance:
        prompt_parts.append(" ".join(tool_guidance))

    # 4. Tool use enforcement (model-specific)
    if _inject:
        prompt_parts.append(TOOL_USE_ENFORCEMENT_GUIDANCE)
        if "gemini" in model_lower or "gemma" in model_lower:
            prompt_parts.append(GOOGLE_MODEL_OPERATIONAL_GUIDANCE)
        if "gpt" in model_lower or "codex" in model_lower:
            prompt_parts.append(OPENAI_MODEL_EXECUTION_GUIDANCE)

    # 5. System message (caller-provided)
    if system_message is not None:
        prompt_parts.append(system_message)

    # 6. Memory Store frozen snapshot (built-in memory)
    if self._memory_store:
        if self._memory_enabled:
            mem_block = self._memory_store.format_for_system_prompt("memory")
            if mem_block:
                prompt_parts.append(mem_block)
        if self._user_profile_enabled:
            user_block = self._memory_store.format_for_system_prompt("user")
            if user_block:
                prompt_parts.append(user_block)

    # 7. External memory provider system prompt blocks
    if self._memory_manager:
        _ext_mem_block = self._memory_manager.build_system_prompt()
        if _ext_mem_block:
            prompt_parts.append(_ext_mem_block)

    # 8. Skills manifest (two-layer cache)
    skills_prompt = build_skills_system_prompt(...)
    if skills_prompt:
        prompt_parts.append(skills_prompt)

    # 9. Context files (with injection scanning)
    if not self.skip_context_files:
        context_files_prompt = build_context_files_prompt(cwd=_context_cwd, skip_soul=_soul_loaded)
        if context_files_prompt:
            prompt_parts.append(context_files_prompt)

    # 10. Timestamp + metadata
    prompt_parts.append(timestamp_line)

    # 11. Platform hint
    if platform_key in PLATFORM_HINTS:
        prompt_parts.append(PLATFORM_HINTS[platform_key])

    return "\n\n".join(p.strip() for p in prompt_parts if p.strip())
```

**组装原则**：
- 所有层之间用 `\n\n` 分隔
- 空块自动跳过
- `skip_context_files` 控制是否加载 AGENTS.md 等文件
- 系统提示词缓存：仅在压缩事件后重建

---

## 4. 工具感知行为引导文本详解

### 4.1 `MEMORY_GUIDANCE` — 记忆使用规范

```python
MEMORY_GUIDANCE = (
    "You have persistent memory across sessions. Save durable facts using the memory "
    "tool: user preferences, environment details, tool quirks, and stable conventions. "
    "Memory is injected into every turn, so keep it compact and focused on facts that "
    "will still matter later.\n"
    "Prioritize what reduces future user steering — the most valuable memory is one "
    "that prevents the user from having to correct or remind you again. "
    "User preferences and recurring corrections matter more than procedural task details.\n"
    "Do NOT save task progress, session outcomes, completed-work logs, or temporary TODO "
    "state to memory; use session_search to recall those from past transcripts. "
    "If you've discovered a new way to do something, solved a problem that could be "
    "necessary later, save it as a skill with the skill tool.\n"
    "Write memories as declarative facts, not instructions to yourself. "
    "'User prefers concise responses' ✓ — 'Always respond concisely' ✗. "
    "'Project uses pytest with xdist' ✓ — 'Run tests with pytest -n 4' ✗. "
    "Imperative phrasing gets re-read as a directive in later sessions and can "
    "cause repeated work or override the user's current request. Procedures and "
    "workflows belong in skills, not memory."
)
```

**关键原则**：
1. **声明式 vs 命令式**：记忆用声明式事实，不写给自己看的命令
2. **记忆 vs Skills 边界**：工作进度/TODO 放 session_search，workflow/procedure 放 skills
3. **价值优先级**：减少未来用户重复纠正的记忆 > 任务细节
4. **compact 原则**：每轮都注入，体积要小

### 4.2 `SESSION_SEARCH_GUIDANCE` — 跨会话搜索提示

```python
SESSION_SEARCH_GUIDANCE = (
    "When the user references something from a past conversation or you suspect "
    "relevant cross-session context exists, use session_search to recall it before "
    "asking them to repeat themselves."
)
```

简洁明了：优先 recall，再询问。

### 4.3 Model-Specific 执行纪律

| 模型家族 | 注入的 Guidance | 关键内容 |
|----------|-----------------|----------|
| GPT/Codex | `OPENAI_MODEL_EXECUTION_GUIDANCE` | tool persistence / mandatory tool use / prerequisite checks / verification |
| Gemini/Gemma | `GOOGLE_MODEL_OPERATIONAL_GUIDANCE` | absolute paths / verify-first / parallel calls / conciseness |
| 通用 | `TOOL_USE_ENFORCEMENT_GUIDANCE` | 不要描述行动，要直接执行工具 |

---

## 5. Skills Manifest 两级缓存架构

### 5.1 缓存层次

```
Layer 1: 进程内 LRU Cache
  Key: (skills_dir, external_dirs, available_tools, available_toolsets, platform_hint, disabled)
  → 进程重启后 cache miss

Layer 2: 磁盘快照 (.skills_prompt_snapshot.json)
  验证：mtime + size manifest
  → 进程重启后命中，但 mtime 变化时重建

Cold Path: 全量文件系统扫描 → 写磁盘快照
```

### 5.2 Skill 过滤条件

```python
def _skill_should_show(conditions, available_tools, available_toolsets) -> bool:
    # fallback_for: 当主工具可用时隐藏（fallback 场景）
    # requires: 需要特定工具/toolset 才显示
```

### 5.3 外部 Skill 目录

支持 `skills.external_dirs` 配置外部只读 Skill 目录，与本地 `~/.hermes/skills/` 并排扫描，本地同名优先。

---

## 6. Context File 扫描与双扫描防护体系

### 6.1 两个扫描函数对照

| 维度 | `_scan_context_content()` (prompt_builder.py) | `_scan_memory_content()` (context_engine.py) |
|------|---------------------------------------------|---------------------------------------------|
| 扫描对象 | AGENTS.md / .cursorrules 等上下文文件 | MemoryStore 记忆内容 / 检索结果 |
| 威胁模式 | 10 类正则模式 | 未公开（但 doc 76 确认存在） |
| Unicode 扫描 | ✅ 10 种零宽/方向字符 | 可能存在 |
| 阻断策略 | 占位符文本阻断 | `sanitize_context()` 过滤 |
| 日志 | `logger.warning()` | `logger.warning()` |
| 调用点 | `build_context_files_prompt()` | `sanitize_context()` → 记忆注入 |

### 6.2 Memory Context 围栏 (`<memory-context>`)

`build_memory_context_block()` 在 `context_engine.py` 中定义，为 Provider 检索结果包上 `<memory-context>` 围栏标签。`sanitize_context()` 在输出到 API 前扫描内容，过滤逃逸的 fence 片段。

### 6.3 SOUL.md 特殊处理

SOUL.md **不经过** `_scan_context_content()` 扫描，走独立的 `load_soul_md()` 路径。这是因为 SOUL.md 作为 Agent 身份定义，其内容本身就是有意注入的系统级指令，不需要被当作潜在注入来源扫描。

---

## 7. BlueCortexCE 差距与可执行借鉴

### 7.1 注入扫描差距（P0）

| 维度 | Hermes | BlueCortexCE (CE) |
|------|--------|-------------------|
| 威胁模式库 | 10 类正则，分类明确 | **未实现** |
| 不可见 Unicode 扫描 | 10 种零宽/方向字符 | **未实现** |
| 上下文文件扫描 | `_scan_context_content()` | 仅 `IngestionController` 长度截断 |
| 阻断 vs 净化 | 阻断（Block）策略 | 截断（Truncate）策略 |

### 7.2 可执行借鉴

**1. ContextSecurityService 实现**

```java
// BlueCortexCE: 新建 ContextSecurityService
public class ContextSecurityService {
    // 10类威胁模式（中文环境需适配）
    private static final List<ThreatPattern> THREAT_PATTERNS = List.of(
        new ThreatPattern("prompt_injection", Pattern.compile(
            "ignore\\s+(previous|all|above|prior)\\s+instructions", Pattern.CASE_INSENSITIVE)),
        // ... 覆盖 doc 76 威胁模型
    );
    
    // 10种不可见Unicode
    private static final Set<Integer> INVISIBLE_UNICODE = Set.of(
        0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF,  // Zero-width
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E   // Directional override
    );
    
    public String scanAndBlock(String content, String source) {
        // 1. 不可见字符检测
        for (char c : content.toCharArray()) {
            if (INVISIBLE_UNICODE.contains((int) c)) {
                return blockMessage(source, "invisible_unicode");
            }
        }
        // 2. 正则威胁扫描
        for (ThreatPattern p : THREAT_PATTERNS) {
            if (p.pattern().matcher(content).find()) {
                return blockMessage(source, p.id());
            }
        }
        return content;
    }
}
```

**2. 注入点**

在 `ContextController.generate()` 或 `ContextService.buildContext()` 中：
- 用户输入文本 → `scanAndBlock()` → 阻断
- Provider 检索结果 → `scanAndBlock()` → 阻断
- 上下文文件内容（AGENTS.md 等）→ `scanAndBlock()` → 阻断

**3. Tool-Aware Guidance 文本**

参考 `MEMORY_GUIDANCE` 设计 BlueCortexCE 的工具感知引导：
- Observation 写入规范（声明式 vs 命令式）
- Memory vs Skills 边界（长期事实 vs 工作流程）
- Session Search 使用触发条件

**4. 系统提示词分层架构**

CE 的 `ContextController` 可借鉴分层设计：
- Layer 1: 身份层（SOUL.md）
- Layer 2: 工具感知引导（工具注册时动态注入）
- Layer 3: 记忆快照（冻结，不每轮重建）
- Layer 4: 上下文文件（带扫描）
- Layer 5: 时间戳/元数据

**5. Skills 两级缓存**

CE 的 `ModeManager` 或 `TemplateService` 可借鉴两级缓存：
- Layer 1: 进程内 `ConcurrentHashMap` 缓存
- Layer 2: 磁盘快照（JSON 文件 + mtime 验证）

---

## 8. 上游源码关键位置

| 源码 | 行号 | 内容 |
|------|------|------|
| `run_agent.py` | 4870–5070 | `_build_system_prompt()` 完整组装逻辑 |
| `run_agent.py` | 130 | `from agent.prompt_builder import ...` |
| `run_agent.py` | 133 | `from agent.prompt_builder import MEMORY_GUIDANCE / SESSION_SEARCH_GUIDANCE / ...` |
| `run_agent.py` | 4976–4978 | MemoryStore 快照 + External Provider Block 注入 |
| `prompt_builder.py` | 1–80 | 文件头 + `_CONTEXT_THREAT_PATTERNS` + `_CONTEXT_INVISIBLE_CHARS` |
| `prompt_builder.py` | 38–79 | `_scan_context_content()` 完整实现 |
| `prompt_builder.py` | 150–184 | `MEMORY_GUIDANCE` / `SESSION_SEARCH_GUIDANCE` / `SKILLS_GUIDANCE` 全文 |
| `prompt_builder.py` | 186–331 | `KANBAN_GUIDANCE` / `TOOL_USE_ENFORCEMENT_GUIDANCE` / 模型特定指导全文 |
| `prompt_builder.py` | 578–820 | `_build_skills_manifest()` + 两级缓存架构 |
| `context_engine.py` | (需对照) | `_scan_memory_content()` / `build_memory_context_block()` / `sanitize_context()` |

---

## 9. 结论

`prompt_builder.py` + `_build_system_prompt()` 共同构成 Hermes 的**系统提示词工厂**，其核心价值：

1. **注入扫描双层防护**：`prompt_builder._scan_context_content()` 扫描上下文文件，`context_engine._scan_memory_content()` 扫描记忆内容，形成完整的输入安全防线
2. **工具感知动态引导**：不同工具组合触发不同的引导文本，而非静态模板
3. **模型特定执行策略**：GPT/Codex/Gemini 等不同模型注入不同执行纪律文本
4. **冻结快照最大化缓存**：系统提示词仅在压缩事件后重建，最大化 prefix cache 命中
5. **Skills 两级缓存**：磁盘快照使 Skills 扫描在进程重启后依然高效

**BlueCortexCE 优先实施**：
- P0: `ContextSecurityService` 实现 10 类威胁模式 + Unicode 扫描
- P1: Tool-Aware Memory Guidance 文本设计
- P2: 系统提示词分层架构重构
