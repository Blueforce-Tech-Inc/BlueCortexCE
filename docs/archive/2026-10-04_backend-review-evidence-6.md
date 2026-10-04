# CortexCE backend-review-findings 实测记录归档 — 第 6 批

> **归档日期**: 2026-10-04（第 254 轮）
> **来源**: P2-51 的 **Evidence** 段（`projects` 单值被静默忽略的三组活体对照），逐字迁出。
> **依据**: 承第 241/244/247/253 轮同规则——实测记录是可复现的证据，⏸ 规则保护的是决策推理。
> **Scope / Problem / Status 一行未动。**
> **本文件创建后不得修改**。

---

- **Evidence（活体，第 254 轮，三组对照，各用不同 `project_path` 以避开缓存）**:
  | 请求 | 返回的 context 首行 |
  |------|--------------------|
  | `projects: "openclaw"`（单个值） | `# r254-nocache-A — no memories yet` |
  | `projects: "openclaw,/tmp/phase3-acceptance-test"`（含逗号） | `# openclaw recent context … 📊 25 observations 📖 6,109 read tokens` |
  | 完全不传 `projects` | `# r254-nocache-C — no memories yet` |
  第一行与第三行**等价**——单值被忽略的活体证据。
  **首版探针三组返回完全相同、险些据此判「`projects` 不生效」**，
  原因是**上下文缓存按 `project_path` 命中**、第二次起走缓存分支——
  **与第 248 轮判 Cursor 修复时踩的是同一个坑**，改用不同 `project_path` 强制未命中后才分出高下。
