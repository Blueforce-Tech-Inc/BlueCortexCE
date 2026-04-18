# Hermes 记忆分析文档 — AI 文档助理工作说明

本文件对齐 OpenClaw / 内部任务中「Hermes Agent 记忆系统探索与借鉴分析」的**文档架构规范**，供后续 AI agent **演进本目录**时使用。

## 必须遵守

1. **禁止**再向单一巨型 `.md` 堆叠全文（阈值：**50KB/文件**）。
2. **重构优先于追加**：若发现某文件将超限，先拆分或归类，再写入新内容。
3. **方面明确**：每个文件应服务一个清晰主题（架构 / 存储 / 检索 / 上下文 / Hook / 某 Provider 等）；拿不准时先写入 [`staging.md`](staging.md)，再整理入对应 aspect 子目录。**分片文件名请使用英文**（内容可中文），与当前 `_split_hermes_memory.py` 中 `CHUNK_ENGLISH_NAMES` 一致。
4. **信息完整性**：拆分或移动段落时不得丢代码引用、表格与结论；合并前在 `staging` 中保留 diff 说明（可选）。

## 推荐工作流

1. 阅读 [`index.md`](index.md) 与 [`_manifest.txt`](_manifest.txt)，定位相关分片。
2. 小改动：直接编辑对应 `**/*/*.md`。
3. 大段新增：写入 [`staging.md`](staging.md) → 拆成 ≤50KB → 归入 `00-overview` / `10-core-memory` / … / `60-evolution` 等（命名与 [`_split_hermes_memory.py`](_split_hermes_memory.py) 中 `aspect_prefix` 规则一致，或扩展该脚本）。
4. 若需**从新的单文件重新切分**：更新 `docs/drafts/hermes-memory-analysis.md` 为完整源（或改脚本 `SRC` 路径），运行 `_split_hermes_memory.py`，并更新 `index.md` / `_manifest.txt`（脚本会重写 manifest）。

## 与 BlueCortexCE 的关系

分析目标不变：**为旁路型记忆系统提供可落地借鉴**；新增内容应保留「内置型 vs 旁路型」的**翻译**视角，避免照搬 Hermes 实现细节。

## 自检（每次合并前至少做）

1. 每个 Markdown 文件 `wc -c` ≤ 51200。
2. 运行 `_split_hermes_memory.py` 后脚本内 **lossless** 断言通过（若从单文件生成）。
3. `index.md` 中的链接路径仍有效。
4. 新增长内容已标版本/日期（与原稿风格一致为佳）。
