# 上游增量分析 `1fc8733a6..origin/main`（20 commits）

**时间**：2026-05-06 07:58 CST  
**起点**：`1fc8733a6`（doc 88：无核心记忆系统代码变更，33 Provider 全量可插拔化）  
**终点**：`origin/main` (`0d41e94ca`)  
**新增 commit**：19 个

---

## 概述

本次 20 个新 commit 扫描结果：**1 个核心记忆系统相关发现**（Hindsight cross-process deduplication via `update_mode='append'`），另有 1 个 API Server SSE 性能优化可能影响内存上下文流式传输。

| 类别 | 数量 | 代表 commit |
|------|------|------------|
| i18n / 文档 | 9 | French locale、Chinese README、VS Code setup |
| Hindsight 记忆 Provider | 1 | `3082fa082` ⭐ 核心 |
| API Server SSE 优化 | 1 | `3188e63b0` 相关 |
| Kanban | 3 | kanban worker lifecycle、metadata handoff |
| AUTHOR_MAP | 6 | 贡献者映射更新 |
| 其他 | 0 | — |

---

## ⭐ P1 记忆发现：Hindsight `update_mode='append'` 跨进程去重

**Commit**：`3082fa082`  
**文件**：`plugins/memory/hindsight/__init__.py`  
**作者**：nicoloboschi  
**关闭 issue**：dedup half of #20115

### 问题背景

Hermes Hindsight Provider 在多进程场景下，同一 session 的多次 retain 会产生 N 份独立的 document（因为每个进程都用 `f"{session_id}-{start_ts}"` 作为 `document_id`），导致跨进程记忆碎片化。

### 解决方案

Hindsight ≥ 0.5.0 支持 `update_mode='append'` 语义。Hermes 通过 `/version` 探测自动检测：

1. **探测机制**（模块级缓存，每 API URL 只发一次）：
   - `_fetch_hindsight_api_version(api_url, api_key)` → GET `/version`
   - `_check_api_supports_update_mode_append(api_url, api_key)` → 布尔缓存结果
   - 探测失败时优雅降级到 per-process unique doc_id 模式

2. **新方法 `_resolve_retain_target(fallback_document_id)`**：
   - 返回 `(document_id, update_mode)` 元组
   - Hindsight ≥ 0.5.0 → `(session_id, 'append')`
   - 旧版 API → `(fallback_document_id, None)`（保持 #6654 修复兼容）

3. **集成点**：
   - `sync_turn()` 中的 retain 调用已接入 `_resolve_retain_target`
   - `on_session_switch()` 的 flush 路径在旋转 `_session_id` **之前**解析 doc_id，确保 flush 到旧 session

4. **local_embedded 特殊处理**：
   - daemon 端口是 per-profile 动态分配的，从运行中 `client.url` 取探测 URL，而非配置的默认地址

5. **版本门控常量**：
   ```python
   _MIN_VERSION_FOR_UPDATE_MODE_APPEND = "0.5.0"
   ```

### CE 借鉴价值

**高**。CE 的 PostgreSQL 多租户场景同样面临"跨进程/会话合并"问题。CE StructuredExtractionService 的 append-only 设计思路与此一致。可以参考：

- 模块级 `threading.Lock` + `Dict[str, bool]` 缓存模式
- 版本探测 + 优雅降级的 API 兼容性策略
- 在跨边界操作（flush/switch）前解析目标资源的命名策略

**相关已有文档**：[`22`](60-evolution/22-hindsight-knowledge-graph-deep-dive.md)（Hindsight KG 深度解析）  
**锚点**：Hindsight provider cross-process dedup，doc 22 补充。

---

## 相关发现：API Server SSE Token Batching（`3188e63b0`）

**Commit**：`3188e63b0`  
**文件**：`gateway/platforms/api_server.py`  
**作者**：bogerman1

### 变更内容

- **SSE 事件节流**：每 50ms 批量发送 text-delta 事件，将 SSE 事件率从 ~500/turn 降到 ~20/turn
- **tool_call.arguments 截断**：超过 100KB 的参数在 `response.completed` 事件中被截断（防止 848KB+ 超大 SSE 事件导致静默挂起）
- **MAX_REQUEST_BYTES**：1MB → 10MB（避免长对话请求被静默 400）
- **异常处理**：agent crash 时发送正确的 error chunk，避免 TransferEncodingError

### 与记忆系统的关联

SSE 节流对 BlueCortexCE 的 `/api/context/generate` 流式响应有参考价值。当 CE 实现 memory context streaming 时，批次化 delta 发送可减少前端渲染压力。

---

## 定时巡检（2026-05-06 07:58 CST）

- [x] **本地 Hermes Agent Repo 同步**：`git fetch origin main` ✅
- [x] **上游代码增量扫描**（`1fc8733a6..origin/main`，20 commits）：1 个核心记忆系统相关发现 → 本 doc `89`
  - ⭐ P1：Hindsight `update_mode='append'` 跨进程去重（`3082fa082`）
  - 相关：API Server SSE token batching（`3188e63b0`）
- [x] **文档架构规范自检**：
  - 入口 `hermes-memory-analysis.md` 仅 1553 字节 ✅
  - `hermes-memory/` 61 篇正文，最大 46922 字节（`09`），全部低于 50KB 上限 ✅
  - 新增 doc `89`（~6300 字节）
- [x] **Backlog 全部项 `[x]`**：v11.0 完成。下一轮巡检继续跟踪上游新 commit（起点：`origin/main` `0d41e94ca`）
