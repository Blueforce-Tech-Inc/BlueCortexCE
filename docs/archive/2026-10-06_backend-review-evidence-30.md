# Backend Review Evidence 30 — P2-46 迁移块（第 278 轮）

> **归档规则**：工作文件保留 **Problem** 的问题陈述与 **Status**；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 1：P2-46 原始条目（逐字）

### P2-46: 验收脚本的 `cleanup()` **定义了却从未被调用**——其幻影端点从未生效，而 Test 6 的前提因此早已不成立

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 这条比一般的文档错误重要，因为它**削弱的是我自己每轮据以判断的验收门控**。
  两处缺陷叠加：
  ① `cleanup()` 在全文件**只出现一次**（定义处）——`grep -nE "cleanup|trap|EXIT"` 只有第 45 行，
  `main` 里没有调用，也没有 `trap ... EXIT`，`bash -n` 通过。它是死代码。
  ② 它内部那行清理请求本身也不成立：`DELETE /api/memory/observations?project_path=...`
  **活体 404**。活体 OpenAPI 里该前缀下**只有 `/api/memory/observations/{id}` 一条路径**
  （`patch` 与 `delete`），**没有任何按 `project_path` 批量删除的端点**；
  `/api/observations`（GET 列表）才是脚本真正该用的。or-true 兜底把 404 吞掉，
  因此这个失败**永远不会让脚本失败**。
- **Status**: ⏸ **记录不修** —— 属脚本方向，不在本轮（Python SDK）的代码轮换内；
  且**若真把清理接上，Test 6 会切回 `not_found` 分支、累积数据会被删除**，
  属于会改变门控自身行为的改动，需在自己的轮次里单独做 A/B。
- **对既有结论的影响（必须如实记录）**: 第 249–251 轮的「EXTRACTION 25/0/0 全通过」
  **仍是 25 条全部通过**，但 **Test 6 走的是兜底分支**、**Test 14 的断言已因数据累积而恒真**。
  这不使任何一条已记录的修复失效（被修代码路径本就在别处被独立验证），
  但今后引用该数字须带上这两条限定（基线区块已写明）。
- **同族事实（已修）**: 同一幻影端点也出现在**设计文档** `phase-3-design/25.md:699`
  （`demo-v15-extraction-test.sh` 的 Cleanup 段），已改为脚本真正使用的
  「先 `GET /api/observations` 取 id、再逐条 `DELETE /api/memory/observations/{id}`」，
  并实跑验证（观测数 1 → 0）。该脚本本身**行为正确**（`cleanup_test_data` 测试前后各调一次，
  `limit=100` 恰等于 `Constants.MAX_PAGE_SIZE`，不截断），**只有验收脚本是坏的**。

