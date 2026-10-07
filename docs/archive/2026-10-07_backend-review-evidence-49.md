# Backend Review Evidence 48 — 第 341 轮已结案条目的整条正文（第 341 轮迁出）

> **归档规则**：承 `-40` ~ `-47` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为写入 P2-103 后文件**行数越线**（实测 **1521 行 / 149764 字节**）
> 而迁出 **1 条**：P2-103，第 341 轮结案（纯注释）。
>
> 它与同批迁出的 P2-101 / P2-102 同属一类**成因写错**：前两条是文档说错了对象在哪、
> 说错了对象的**成因**。三条都不是首次笔误，而是**已记载的事实被复述成了另一个样子**——
> 而复述的那一句，恰好是后来者判断「还需不需要再查」的唯一依据。
## 块 1 / 1：P2-103 全文（第 341 轮逐字迁出）

### P2-103: `ExpRagService` 把 `created_at` 为 NULL 的成因写成「pre-migration data」——而**当前的捕获路径就在持续制造 NULL**
- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/service/ExpRagService.java:237`
  （`toExperience` 内）——原注释
  `// P2: Fallback createdAt from epoch if getCreatedAt() returns null (pre-migration data)`。
- **Problem**: 回退逻辑**本身是对的**（`createdAt` 为 NULL 时由 `createdAtEpoch` 推出），
  失实的是那个括号。**「pre-migration data」暗示迁移修完就不再发生**，而实测相反：

  | 表 | 非空 `created_at` 的**最新**一行 | NULL 的**最新**一行 |
  |---|---|---|
  | `mem_observations` | 2026-05-07 08:36 | **2026-10-07 01:53（当天）** |
  | `mem_summaries` | 2026-10-02（`ImportService` 所写，全表仅 1 行） | 2026-05-07 08:37 |
  | `mem_user_prompts` | **无** | 2026-10-07 01:52 |

  即**观测与提示的最新行全在 NULL 一侧**。原因在代码里可直接读出：DDL 的
  `DEFAULT NOW()` 只对**直接 SQL 插入**生效，而 JPA 捕获路径
  （`AgentService:267`、`SummaryGenerationService:159`）**只设 `createdAtEpoch`**，
  无 `@PrePersist`、无 JPA auditing；**只有 `ImportService` 显式赋值**。
  该机制**已由 `ARCHITECTURE.md` 双语完整记载**，所以这不是「没人知道」——
  是**同一件事在这份代码注释里被写成了另一个成因**。
- **为什么可以单方面改**（判据：**失实陈述 + 正确值被权威确定**）：
  正确成因由三处独立佐证（两个写入点的源码、`ARCHITECTURE.md` 的双语注记、活体逐表最新行时间）；
  改动**只涉及注释**，`git diff -U0` 逐行核验**可执行行 0**。
- **Severity**: 低（回退逻辑正确、行为零影响；错的是一句会让后来者以为「等迁移跑完就好了」的原因说明）。
- **Status**: ✅ **已修（第 341 轮，纯注释零行为变更）** —— 改为如实写出「DDL 默认值只对 SQL 插入生效、
  JPA 捕获路径只设 `createdAtEpoch`、只有 `ImportService` 赋值」，并指向 `ARCHITECTURE.md`。
  `mvn -o clean package -DskipTests` EXIT=0；重建 jar 后**停掉本轮自己启动的 37777 进程**并以新 jar 重启，
  按实跑满足新鲜度闸门（改的是后端自身，豁免路径不适用）；回归 **45/0/1** + EXTRACTION **25/0/0**。

