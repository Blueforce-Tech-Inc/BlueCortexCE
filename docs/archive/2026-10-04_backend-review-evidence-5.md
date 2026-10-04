# CortexCE backend-review-findings 实测记录归档 — 第 5 批

> **归档日期**: 2026-10-04（第 253 轮）
> **来源**: `docs/drafts/backend-review-findings.md` 中 **P2-41 / P2-46 / P2-48 / P2-50**
>   四条的 **Evidence** 段，逐字迁出，源文件各留一行指针。
> **依据**: 承第 241、244、247 轮确立的规则——**实测记录是可复现的证据**（每段都写明
>   日期、端点与技术手段），⏸ 规则保护的是**决策推理**。**Scope / Problem / Status 一行未动。**
> **本文件创建后不得修改**。

---

<!-- P2-41 -->
- **Evidence（活体 + 四家逐文件比对）**:
  | 事实 | 证据 |
  |------|------|
  | 后端每条观测都带这三个字段 | 活体 `GET /api/observations?limit=1` → `platform_source='claude'`、`content_hash='1ed602d868bef3f8'`、`relevance_count=0` |
  | 文档把它当过滤器 | `API.md` 中 `platformSource` 出现 **4** 处，含列表端点参数 |
  | 四家 SDK 都不接受该过滤器 | 对四家 SDK 源码 `grep -i platformsource\|platform_source` → **零命中** |
  | 四家响应 DTO 都没有该字段 | `Observation` 字段清单逐个列出：Go / JS / Java / Python **均无** |
  | `narrative` 则四家都有 | Go / JS / Java / Python **均暴露**——说明这不是「响应 DTO 一律精简」，而是有选择 |

<!-- P2-46 -->
- **Evidence（活体，第 251 轮）**: 该项目 `latest` 返回 `{"status":"ok", …}` 而**非** `not_found`；
  `history` 已累积 **50** 条（`limit=50`）；`GET /api/observations` 返回 `hasMore: true`。
  Test 6 的注释写着「**should return not_found for new project**」，但它两个分支都 pass，
  清理从不生效，**第一个分支自首次成功抽取后就是死代码**，实际一直走 `elif`——
  **本轮验收输出直接印证**：跑出来的是 `PASS Test 6: GET latest returns status field`。
  - **累积已把 Test 14 退化成恒真式**：它名为「Re-extraction **removes** invalidated preference」，
    但真正的断言只有 `pref_count >= 1`（脚本第 **515** 行，计数取自 504–514 的内联 python），
    而本轮输出是 `Re-extraction updated with **1173** preferences`——
    累积到这个量级，「至少有一条偏好」必然成立，该测试**已不再验证任何移除语义**；
    同函数末尾的 `Bonus — Xiaomi correctly removed` 分支本轮**未触发**。

<!-- P2-50 -->
- **Evidence**: **决定性 A/B，见归档**——未修复实例把 `cursor-projects.json` 从 2211 字节
  截断到 1105 字节后再调 `POST /api/cursor/register`，得 **HTTP 200 `{"success":true}`**，
  而磁盘上的注册表**只剩 1 个条目，原有 16 个项目被静默全部丢弃**（已按 sha256 备份逐字还原）；
  修复后实例跑同一序列得 **HTTP 500** 且**注册表未被写回**，对照组 `GET /api/cursor/projects`
  在同一时刻仍 200 并返回已缓存条目。

<!-- P2-48 -->
- **Evidence**: 其中 `POST /api/ingest/session-start` 是本轮探针的**意外来源**——
  验证 `backend/README.md` 的 walkthrough 时误用了该路径，**404** 之下才查出幻影只在
  `CLAUDE.md` 里；`backend/README.md` 本身**完全正确**（只列 4 个真实 ingest 端点，
  walkthrough 用的 `tool-use` 与 `session-end` 均真实存在）。
