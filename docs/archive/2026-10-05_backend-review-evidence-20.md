> **来源**: `docs/drafts/backend-review-findings.md` 中 **P2-39** 的两段补充说明
>   （「同一类问题的既有痕迹」与「附带一处次要观察」），逐字迁出，源文件留一行指针。
> **依据**: 承第 241、244、247、253、254 轮确立的规则——**实测与旁证属可复现的细节**，
>   ⏸ 规则保护的是**决策推理**。该条 `- **Problem**`（问题是什么）与 `- **Status**`
>   （为什么这么定）**一行未动**。
> **本文件创建后不得修改**。

---

<!-- P2-39 -->
- **同一类问题的既有痕迹**：`ImportService.importSession` 第 233-236 行已有一段注释，
  记录过 `project_path` 缺失导致「save 在提交时才失败、调用方只看到
  `Could not commit JPA transaction`」并为此**补了前置校验**。也就是说**这个坑已被踩过一次、
  修过其中一个字段**，而 `content_session_id` 与 `status` 的 `varchar` 宽度**至今未校验**，
  外层事务的 rollback-only 语义**也从未被处理**。
- **附带一处次要观察（不单独立项）**：wire 格式是 **snake_case**
  （`spring.jackson.property-naming-strategy: SNAKE_CASE`），传 camelCase 的
  `contentSessionId` 会得到错误信息 **`"contentSessionId is required"`**——
  该信息**报的是 Java 字段名而非用户实际发来的 wire 字段名**，具有误导性。
  且 `API.md` 对 `/api/import/sessions`、`/summaries`、`/prompts` **只有一句
  「Request body: Array of session objects」，没有任何字段清单或示例**，
  用户无从得知该用 snake_case。
