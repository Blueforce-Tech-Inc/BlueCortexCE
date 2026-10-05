> **来源**: `docs/drafts/backend-review-findings.md` 中 **P2-29** 的 **Problem 段中的实测证据正文**，
>   逐字迁出，源文件留一行指针。
> **依据**: 承第 241、244、247、253、254 轮确立的规则——**实测记录是可复现的证据**
>   （写明哈希值、分组计数与活体 `pg_constraint` 查询结果），⏸ 规则保护的是**决策推理**。
>   压缩后的 `- **Problem**`（问题是什么）与三个既有归档指针**一行未动**。
> **本文件创建后不得修改**。

---

<!-- P2-29 -->
- **Problem**: 去重键是 `(content_session_id, tool_name, SHA-256(tool_input))`，
  判定条件额外要求 `status <> 'failed'`。三处各自独立地削弱了它：

  1. **键里没有 `tool_response`。** 哈希只覆盖 `toolInput`，故「同样的工具、
     同样的入参、结果不同」的调用在前一条仍 `pending`/`processing` 时被**直接丢弃**，
     而调用方只拿到一条 "Duplicate tool-use event skipped" 日志加上
     HTTP `200 {"status":"accepted"}`——**与真正入队完全无法区分**。
     对 fire-and-forget 的 SDK 捕获路径而言，调用方只能得出「已记录」这个错误结论。
  2. **`tool_name` 未规范化。** 它是客户端自由文本，却参与键的比较。实测同一
     session 内 `Read` 与 `read` 携带**完全相同的 input 哈希**
     （`45ff9481fce2…`）时**双双入队**，即大小写不同即可绕过去重。
     不过要如实说明规模：全表按 `(session, lower(tool_name), hash)` 精确分组后，
     大小写孪生组**只有 1 个，且就是本次探针**——**生产数据里从未发生过**。
     真正普遍的是命名本身跨客户端不一致（`Read` / `readFile` / `read`、
     `Edit` / `edit` / `write_file` 同时存在），近 30 天仍有 `readFile` 13 次、
     `write_file` 4 次在流入。
  3. **检查与写入不是原子的，而唯一的兜底约束并不存在。**
     `PendingMessageEntity` 声明了
     `@UniqueConstraint(name = "uk_session_tool_input", columnNames = {...})`，
     但 `application.yml:91` 是 `spring.jpa.hibernate.ddl-auto: none`，
     且**全部 18 个 Flyway 迁移中没有任何一条创建该约束**；活体
     `pg_constraint` 查询确认该表只有 pkey、两个 CHECK 和一个 FK，
     手工插入一条完全相同的三元组**成功**（已回滚）。
     后果是 `AgentService` 里那段
     `catch (DataIntegrityViolationException)`——注释写着
