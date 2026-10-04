# Backend Review 已解决条目归档（第十三批）

> **Moved by**: 定时项目维护任务，第 260 轮（2026-10-04）。
> **Reason**: 以下 4 条**无条件已解决**条目（Status 含 ✅、整条无 ⏸、无待决问题）
> 的正文整体逐字迁出，工作文件只保留标题与一行指针。
> 先例：第 250 轮第三批曾对 P2-11 / P2-38 做过同样的整体迁出。
> **与第 254/256 轮规则的差别**：那两轮只允许迁出证据类内容、要求保留 Problem 与 Status，
> 因为 ⏸ 条目的 Problem/Status 是**决策推理**；本批条目没有任何待决推理可留。
> **本批我第一次又把边界写错了**：结束点取成了「下一个 `## ` 行」，
> 而最后一条候选之后还有 5 个条目，于是整段被吞进归档、工作文件反而变长。
> 处置为删掉错误归档 → `git checkout HEAD` 恢复 → 重写尚未提交的 P2-58 → 用
> **「下一个 `### ` 或 `## `，先到者」** 的边界重做。
> 归档文件创建后不再修改。

### P2-50: 读 Cursor 注册表失败被当成「空注册表」，而这个空结果**会被写回**


- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 读失败返回**空 Map**，而这两个调用方都是「读 → 改 → 写回」，
  **一个读失败于是成了注册表的新内容**。同类的写路径 `writeRegistryUnlocked` 却**抛异常**——
  **读写不对称，且不对称的那一侧是破坏性的**。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Status**: ✅ **已修** —— 详见 [`2026-10-04_backend-review-status-9.md`](../archive/2026-10-04_backend-review-status-9.md)（第 256 轮；无条件已解决，Status 段整体迁出，Problem 段保留于此）。

### P2-52: `target/` 里残留 10 个**源码已删**的测试类——其中一个仍在失败，使 `mvn test` 退出非零


- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: Maven 不会因为源文件被删而清理 `target/test-classes`，
  于是 **`mvn test` 会继续编译目录里已有的陈旧 class 并执行它们**。
  这类 class 是历轮排查留下的探针（源码在确认结论后按惯例删除、未提交），
  **它们在源码里不存在，却仍在测试阶段运行**。本轮的 `NestingProbeTest` 正是
  第 249 轮为 P2-44 写的探针，它**断言外层作用域应当存活**——
  而那正是 P2-44 记录的**未修缺陷**，所以它**必然失败**。
  后果有二：① 源码全绿的工作区上 `mvn test` **退出非零**（实测 `mvn -o clean test`
  前后分别是「失败 1」与「全过」）；② 测试计数被抬高（见 Evidence）。
- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Status**: ✅ **已修** —— 详见 [`2026-10-04_backend-review-status-9.md`](../archive/2026-10-04_backend-review-status-9.md)（第 256 轮；无条件已解决，Status 段整体迁出，Problem 段保留于此）。

### P2-53: Go SDK 的 `WithTimeout` 把「太小的值」重置成**默认最大值**——请求 50ms 实际得到 30s


- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Problem**: 归一化写的是
  `if cfg.Timeout < 100*time.Millisecond { cfg.Timeout = 30 * time.Second }`——
  **触发条件是「太小」，赋的却是「默认值里的最大值」**。于是调用方
  `WithTimeout(50*time.Millisecond)` 得到 **30 秒**，比要求的值长 **600 倍**，
  且方向正好相反：想用短超时给健康探针兜底的人，拿到的是最长的那个。
  `ConnectTimeout` 同样（`10 * time.Second`）。
  **两处证据把意图钉死为「地板」而非「重置」**：
  ①**同一段代码的下一行** `RetryBackoff` 用的是**同一个触发常量**而赋值
  `100 * time.Millisecond`——它才是地板；②**Python SDK** 同一概念是
  `self._timeout = max(0.1, timeout)`，注释写「Minimum 100ms to prevent immediate timeout」。
  三家对照：Java 的 `readTimeout` **完全不钳制**、Python 钳到 0.1s 地板、**Go 钳到 30s 天花板**——
  **Go 是唯一把下限做成上限的一家**。`DefaultClientConfig` 本身就已是 30s / 10s，
  所以这段归一化**只会在调用方显式传小值时触发**，而那正是它要服务的场景。
- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**: ✅ **已修** —— 详见 [`2026-10-04_backend-review-status-9.md`](../archive/2026-10-04_backend-review-status-9.md)（第 256 轮；无条件已解决，Status 段整体迁出，Problem 段保留于此）。

### P2-54: Python SDK 另有两处裸 TypeError——且既有测试的 docstring 早已写明我踩的那个坑


- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Problem**: 两处都用**数值比较**判参数，与第 251 轮修掉的 `max_chars` **完全同族**：
  `count=None` → `TypeError: '>' not supported between instances of 'NoneType' and 'int'`、
  `limit=None` → `TypeError: '<' not supported between instances of 'NoneType' and 'int'`，
  **裸 TypeError 逃出 SDK**，而不是 SDK 自己的 `ValidationError`。
  **四家对照**：JS 用 `req.count !== undefined` / `limit?: number` 显式防住、
  Java 与 Go 是 primitive（None 不可能发生）、**只有 Python 会炸**。
  **同文件内的既有约定本就是真值判断**——`search` 与 `list_observations` 的
  `if limit:` / `if offset:` 天然对 None 安全，实测四个入口传 None 全部 OK，
  **只有这两处是例外**。
- **本次修复过程中被既有测试当场抓住的一次自我犯错（值得单列）**：
  我第一版改成了 `if count:`（照搬同文件其它处的真值写法），结果
  `test_retrieve_experiences_drops_negative_count` **立刻失败**——
  **负数在 Python 里是真值**，`-1` 会被发上 wire。
  而**那条测试的 docstring 早就写着**：「A truthiness test is not enough:
  every non-zero int is truthy in Python.」**警告一直躺在仓库里，我读到了那段
  注释所在的方法却没读注释**。最终形式与第 251 轮一致：
  `if count is not None and count > 0` / `if limit is not None and limit > 0`，
  两处都补了「真值判断在这里是错的，因为负数为真」的注释。
- **Status**: ✅ **已修** —— 详见 [`2026-10-04_backend-review-status-9.md`](../archive/2026-10-04_backend-review-status-9.md)（第 256 轮；无条件已解决，Status 段整体迁出，Problem 段保留于此）。
