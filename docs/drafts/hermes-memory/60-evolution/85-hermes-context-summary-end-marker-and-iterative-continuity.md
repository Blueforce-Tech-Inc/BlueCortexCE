# Hermes Context Summary End Marker & 迭代压缩连续性 — CE 借鉴深度解析

> **日期**：2026-05-06 01:17 CST  
> **上游 commits**：`2eef395e1`（2026-04-29）+ `4a3e3e20e`（2026-04-29）  
> **关联 doc**：doc 84（上游扫描，含摘要） · doc 76（CE P0 缺口盘点） · [`02-bluecortexce-recommendations.md`](../20-recommendations/02-bluecortexce-recommendations.md) §10.3/10.5  
> **目的**：从两条上游修复中提炼对 BlueCortexCE **上下文围栏**和**迭代提取引擎**的落地借鉴

---

## 1. 背景：两条修复的上下文

两条 commit 均作用于 `agent/context_compressor.py`，属于 Hermes **Compaction（压缩/摘要）** 核心逻辑：

| Commit | 主题 | 触发条件 |
|--------|------|----------|
| `2eef395e1` | **Summary End Marker** | head 以 assistant/tool 结尾、tail 以 assistant 开头 → summary 以 standalone `role="user"` 插入 |
| `4a3e3e20e` | **Iterative Summary Continuity** | 同进程迭代压缩（前一次压缩输出作为下一次压缩输入） |

两者解决的问题不重叠，但共同指向一个核心命题：**如何让模型准确区分"背景摘要"和"待回复输入"**。

---

## 2. Commit `2eef395e1` — Summary End Marker 深度解析

### 2.1 问题根因

当压缩后的对话满足特定结构时（head 末位是 assistant/tool，tail 首位是 assistant），Hermes 选择将 summary 作为**独立的 `role="user"` 消息**插入，以避免 role 连续（如连续两个 assistant）。

但 summary 内容中包含对历史用户请求的**原文引用**（如 `"## Active Task" 用户说：...`），弱模型（weak/local）会将这个引用误解为**新的用户输入**，导致：
- 模型忽略真正的用户最新消息
- 模型尝试"回复"引用中的历史用户请求

### 2.2 修复方案

```python
# agent/context_compressor.py（新增逻辑）
if not _merge_summary_into_tail and summary_role == "user":
    summary = (
        summary
        + "\n\n--- END OF CONTEXT SUMMARY — "
        "respond to the message below, not the summary above ---"
    )
```

**关键特征**：
1. **仅对 standalone role="user" 路径添加**（merge-into-tail 路径已有类似信号，无需重复添加）
2. **marker 是显式指令性文本**，而非特殊 token 或 XML 标签
3. marker 明确告诉模型：**摘要在上，当前消息在下，请回复下面的**

### 2.3 CE 借鉴分析

**与 CE 的关联**：CE 的 `/api/context/generate` 生成的上下文注入段包含：
- `# Last Session Summary` — 摘要
- `## Active Task` — 当前任务（来自历史用户消息的引用）
- 直接拼接，无任何分隔信号

**问题相同**：当模型看到 `## Active Task: "用户说：帮我写一个排序算法"` 时，可能将引用内容当作新输入。

**CE 可借鉴方案**：

| 方案 | Hermes 做法 | CE 可落地实现 |
|------|-----------|---------------|
| A. 指令性分隔符 | `--- END OF CONTEXT SUMMARY — respond to the message below ---` | 在 summary 段落后追加 `<!-- CONTEXT_SUMMARY_END --> respond to the message below, not the summary above` |
| B. XML fence | `<memory-context>` 在外层包裹 | 定义 `<context-summary>` 块，内含 `</context-summary>` strip 逻辑 |
| C. Markdown fence | 无 | 在 summary block 后加 `---above is context, respond below---` |

**推荐方案 A**（指令性分隔符）：
- 无需修改 CE 的围栏 strip 逻辑
- 可在 `ContextService.renderTimeline()` / `renderSessionSummary()` 返回的 markdown 中追加
- 对所有 LLM 注入路径统一生效（`/api/context/generate`、`/api/context/inject`、`/api/context/semantic`）

---

## 3. Commit `4a3e3e20e` — Iterative Summary Continuity 深度解析

### 3.1 问题根因

Hermes 的压缩在**长对话**中可能触发**多次迭代压缩**（上一次压缩的输出成为下一次压缩的输入）。原始代码存在两个问题：

**问题 1：同进程重复喂给（same-process double-feed）**

```python
# 原始代码伪逻辑
self._previous_summary = old_summary  # 保存上一次摘要
turns_to_summarize = messages[compress_start:compress_end]
# 若前一次压缩的摘要已存在于 compress_end 范围内，
# 它会被再次摘要，导致摘要内容被重复喂给 LLM
```

**问题 2：进程重启后摘要身份丢失（resume rehydration）**

压缩后的 summary 以特殊格式存储：
```markdown
## Context Summary
[摘要内容]
```

重启后，`_previous_summary`（内存属性）丢失。再次压缩时，系统无法识别上一轮摘要是否已被处理，可能将其当作普通用户消息重新摘要。

### 3.2 修复方案

```python
# 新增方法：_find_latest_context_summary
@classmethod
def _find_latest_context_summary(
    cls,
    messages: List[Dict[str, Any]],
    start: int,
    end: int,
) -> tuple[Optional[int], str]:
    """在压缩窗口内查找最新的 handoff summary。"""
    for idx in range(end - 1, start - 1, -1):
        content = messages[idx].get("content")
        if cls._is_context_summary_content(content):  # 检查是否以 SUMMARY_PREFIX 开头
            return idx, cls._strip_summary_prefix(content)
    return None, ""

# 在 compress() 中：
summary_idx, summary_body = self._find_latest_context_summary(
    messages, compress_start, compress_end
)
if summary_idx is not None:
    if summary_body and not self._previous_summary:
        self._previous_summary = summary_body  # 重新获得摘要身份
    turns_to_summarize = messages[summary_idx + 1:compress_end]  # 跳过已摘要部分
```

**核心设计**：
1. **`_find_latest_context_summary`**：从压缩窗口末尾向前扫描，找到最近的 `## Context Summary` 块
2. **strip prefix**：移除 `## Context Summary\n` 前缀，只保留摘要体
3. **identity rehydration**：如果 `_previous_summary` 为空但找到了 handoff，说明是重启后首次压缩，从 handoff 消息中恢复摘要身份
4. **skip already-summarized**：将扫描起始位置调整为 `summary_idx + 1`，避免重复摘要

### 3.3 CE 借鉴分析

**与 CE 的关联**：CE 的 Structured Extraction 有**迭代提取引擎**（`deepRefineProjectMemories` → `reExtractForSession`）。Phase 3 设计中有类似的 prior extraction 传递问题：

```java
// Phase 3 设计中的 prior extraction 传递
String priorJson = summarizePriorExtraction(projectPath, prior);
prompt = prompt.replace("{{prior_extraction}}", priorJson);
```

**CE 面临的风险**（与 Hermes 相同）：

| 风险 | Hermes 场景 | CE 对应场景 |
|------|-----------|------------|
| 重复摘要 | 同一段摘要被摘要两次（双重压缩） | 同一批 observations 被重复提取 |
| 身份丢失 | 重启后 `_previous_summary` 丢失，handoff 被误当作用户消息 | Session 重启后，prior extraction JSON 被误当作用户输入 |
| 摘要蔓延 | 历史摘要越来越长，新内容被压在新摘要中 | extraction 结果累积，token 成本失控 |

**CE 可借鉴的实现模式**：

```java
// 伪代码：CE 迭代提取中的连续性保护
public ExtractionResult runExtraction(Session session, 
                                       String priorJson, 
                                       List<ObservationEntity> newObs) {
    // 1. 检测 priorJson 是否已是"提取结果"（而非用户消息）
    if (priorJson != null && isExtractionResult(priorJson)) {
        // 2. 验证 prior 不在新一批 observations 中（避免重复提取）
        List<ObservationEntity> toExtract = filterOutAlreadyExtracted(
            newObs, priorJson
        );
        if (toExtract.isEmpty()) {
            return parseExtractionResult(priorJson); // 直接复用
        }
    } else {
        // 3. prior 不是提取结果，可能是旧格式或空
        List<ObservationEntity> toExtract = newObs;
    }
    
    // 4. 执行提取，传入 prior 作为上下文
    return llm.extract(toExtract, priorJson);
}
```

**关键 CE 设计要点**（对应 Hermes 三个新方法）：

1. **`isExtractionResult()`**（对应 `_is_context_summary_content`）：通过 JSON 结构和特定字段（如 `extraction_version`、`template_name`）判断是否为提取结果
2. **`filterOutAlreadyExtracted()`**（对应 `_find_latest_context_summary` + `summary_idx + 1`）：从待处理列表中排除已在 prior 中覆盖的内容
3. **身份重获（rehydration）**：Session 重启后，若 `ObservationEntity` 中存在 `type="extraction_result"` 的记录，自动恢复 prior extraction JSON

---

## 4. 综合 CE 落地路径

### 4.1 Context Summary End Marker（立即可落地）

| 步骤 | 操作 | 文件 |
|------|------|------|
| 1 | 在 `ContextService` 中定义 `CONTEXT_SUMMARY_END_MARKER` 常量 | `ContextService.java` |
| 2 | 在 `renderTimeline()`、`renderSessionSummary()` 返回的 markdown 中，在 summary block 后追加 marker | `ContextService.java` |
| 3 | 在文档中说明 marker 作用（防止模型混淆背景与输入） | `docs/` |

### 4.2 Iterative Extraction Continuity（Phase 3 实施时纳入）

| 步骤 | 操作 | 对应 Phase 3 设计章节 |
|------|------|----------------------|
| 1 | 定义 extraction result 识别方法（`isExtractionResult`） | Section 15 |
| 2 | 实现 `filterOutAlreadyExtracted` 逻辑 | Section 24.1（prior extraction token 控制） |
| 3 | 在 `reExtractForSession()` 中复用 prior 而非重新提取 | Section 15.7（并发控制） |

---

## 5. 上游源码锚点（CE 可对照阅读）

| 功能 | Hermes 源码 | CE 对应 |
|------|-----------|---------|
| Summary end marker | `agent/context_compressor.py:1418-1430` | `ContextService.renderTimeline()` |
| `_find_latest_context_summary` | `agent/context_compressor.py:1015-1029` | `ObservationRepository.findByType()` |
| `_is_context_summary_content` | `agent/context_compressor.py:1008-1011` | JSON schema 字段检测 |
| Summary rehydration | `context_compressor.py:1340-1348` | Session 重启后 `deepRefineProjectMemories` |

---

## 6. 结论

| 上游修复 | 核心思想 | CE 落地价值 |
|---------|---------|------------|
| `2eef395e1` END OF CONTEXT SUMMARY marker | **显式分隔符**让模型区分背景与输入 | ⭐⭐⭐ 可立即在 `ContextService` 中实施，解决 CE 模型混淆 summary 与用户输入的问题 |
| `4a3e3e20e` iterative continuity | **handoff 识别 + skip 已摘要 + identity rehydration** 防止重复摘要 | ⭐⭐ Phase 3 迭代提取引擎的关键参考，解决 prior extraction 重复提取和 token 膨胀问题 |
