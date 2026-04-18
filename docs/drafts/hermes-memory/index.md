# Hermes Agent 记忆系统 — 文档索引（渐进式披露）

本目录将原 `hermes-memory-analysis.md` **无损拆分**为多份 Markdown（每份 ≤50KB），按**主题方面（aspect）**分子目录；**文件名均为英文**，便于跨工具与版本管理。正文可为中文。

## 阅读顺序（建议）

1. **总览与立场** → [`00-overview/01-architecture-positioning-and-toc.md`](00-overview/01-architecture-positioning-and-toc.md)（含原文档级「目录」清单；章节正文已分散至下列文件，锚点链接仅在同文件内有效）
2. **借鉴总表** → [`20-recommendations/02-bluecortexce-recommendations.md`](20-recommendations/02-bluecortexce-recommendations.md)
3. **上下文 / 注入 / Prefetch** → [`40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md`](40-context-compression/03-memory-context-injection-and-prefetch-lifecycle.md)
4. **Honcho / 多模态等深度** → `50-honcho-holographic-deep/` 下各篇
5. **演进与 Provider / 工具细节** → `60-evolution/` 下各篇

## 按方面（aspect）浏览

| 方面 | 路径 | 说明 |
|------|------|------|
| 00-overview | [`00-overview/`](00-overview/) | 元信息、架构定位、原「目录」索引 |
| 20-recommendations | [`20-recommendations/`](20-recommendations/) | BlueCortexCE 借鉴建议汇总（及紧密相关小节） |
| 40-context-compression | [`40-context-compression/`](40-context-compression/) | Memory context 注入、Prefetch、Session 截断等 |
| 50-honcho-holographic-deep | [`50-honcho-holographic-deep/`](50-honcho-holographic-deep/) | Honcho 四工具、多模态澄清等 |
| 60-evolution | [`60-evolution/`](60-evolution/) | Hooks、Supermemory、内置 Memory Tool、HRR 等后续版本增量 |

## 分片清单（机器可读）

完整列表见 [`_manifest.txt`](_manifest.txt)（路径、首段标题、字节数）。

## 维护与再生

- **重新从单文件生成全部分片**：在仓库内执行  
  `python3 docs/drafts/hermes-memory/_split_hermes_memory.py`  
  脚本会校验**拼接结果与源文件逐字节一致**（见脚本内 `assert`）。
- **源文件位置**：`docs/drafts/hermes-memory-analysis.md`（现为占位符，指向本目录；**权威正文在 `hermes-memory/` 各分片中**）。
- **给 AI 文档助理的操作说明**：见 [`AGENT.md`](AGENT.md)。

## 相关占位

- [`misc.md`](misc.md) — 暂不便归入某一方面的摘录
- [`staging.md`](staging.md) — 工作进度与待合并草稿

## 自检记录（分片完成后至少 5 项）

| # | 检查项 | 结果 |
|---|--------|------|
| 1 | 各分片字节数 ≤ 51200（50KB 上限） | 最大约 48955 字节，通过 |
| 2 | 去掉分片首行 `<!-- split ... -->` 后按 `_manifest.txt` 顺序拼接 | 与拆分前仓库中的整文件 **逐字节一致**；拆分合入后请用 `git show HEAD^:docs/drafts/hermes-memory-analysis.md`（或含该文件最后一版整稿的提交）对照 |
| 3 | 再生脚本无损：`python3 _split_hermes_memory.py` 内 `assert joined == text` | 生成时已通过 |
| 4 | `_manifest.txt` 行数与 `**/*.md` 分片数量一致（不含 index/AGENT/misc/staging） | 10 条 ↔ 10 个分片文件 |
| 5 | `index.md` 中显式链接的路径均在仓库中存在 | 已核对 |
| 6 | 分片文件名均为英文（`docs/drafts/hermes-memory/**/*.md` 分片） | 已重命名并通过 |

> 若 `hermes-memory-analysis.md` 在 git 中已更新，重新跑 `_split_hermes_memory.py` 后应重复第 2、3 项校验。
