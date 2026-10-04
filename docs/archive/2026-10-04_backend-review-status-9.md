# CortexCE backend-review-findings 已解决条目归档 — 第 9 批

> **归档日期**: 2026-10-04（第 256 轮）
> **来源**: **P2-54**（✅ 已修）的 `- **Status**` 段。
> **为何 Status 也可迁出**: 本条是**无条件已解决**条目——修法已定、测试已加、
>   注入已验、活体已复核，**没有任何待决的决策推理**。
>   第 254 轮确立的「Problem + Status 必留」规则针对的是**带 ⏸ 的待决条目**；
>   本条属其中**唯一可整体迁出的例外**，按第 250 轮「无条件已解决条目整体迁出」的先例处理。
> **Problem 段（问题是什么）仍完整保留在工作文件中。**
> **本文件创建后不得修改**。

---

- **Status**: ✅ **已修** —— 注解放宽为 `Optional[int]`（`from typing import Optional`），
  `get_extraction_history` 的 docstring 写明缘由与 JS 侧对照。
  **+2 条测试**（439 → **441**）。**双向注入**：回退到修复前的裸数值比较后
  **恰好 2 条失败**（两条新增的 None 用例），
  两条既有用例（**负数丢弃**与 **`count=1` 必须仍上 wire**）在两种状态下**均不失败**。
  活体复核：`count=None` / `limit=None` 均 OK；`count=-1` 被正确丢弃；
  **`limit=-1` 正确抛 `ValidationError`**（负数守卫仍在，且现在对 None 也安全）。


---

> **追加（第 256 轮同批）**: 以下三段同属**无条件已解决**条目
> （**P2-52** / **P2-50** / **P2-53**，均无 ⏸、无任何待决推理），
> 依同一例外处理，其 `- **Status**` 段一并逐字迁出。
> **三者的 Problem 段（问题是什么）仍完整保留在工作文件中。**

<!-- - **Status**: ✅ **已处置** —— 执行 `mvn -o clean test`，陈旧类清除、计数回到 -->
- **Status**: ✅ **已处置** —— 执行 `mvn -o clean test`，陈旧类清除、计数回到 196、
  退出码 0。**未改任何源码或脚本**：这十个 class 都不在版本控制内，
  `clean` 即是正解；`mvn clean` 本就是文档与 CI 的标准起手式。
  **遗留的纪律问题（比缺陷本身更值得记）**：本会话早前跑 Java SDK 测试时用了
  `mvn … | tail; echo $?` —— **`$?` 取的是 `tail` 的退出码、恒为 0**，
  于是**一次真实的测试失败被完美地掩盖了**。
  「管道会吞掉上游退出码」是 Bash 的基本事实，本轮**又一次**靠人工核对才发现
  （与第 252 轮 `grep -P`、第 253 轮行号偏移同属「探针自身出错」一类）。



<!-- - **Status**: ✅ **已修** —— `readRegistryUnlocked` 在**文件存在但无法解 -->
- **Status**: ✅ **已修** —— `readRegistryUnlocked` 在**文件存在但无法解析**时改为抛
  `UncheckedIOException`，与 `writeRegistryUnlocked` 对称；**「文件不存在 = 空的」保持不变**
  （那才是真正的空）。方法 Javadoc 写明了为什么不能返回空：返回空会被写回。
  调用方本就 `catch (Exception)` 并返回 500，无需改动。
- **同区域新发现（记录不修）**: **数据目录有两个互不相干的键**——`CursorService` 用
  `@Value("${claudemem.data-dir:…}")`，`AppSettings` 用 `CLAUDE_MEM_DATA_DIR`。
  设后者只会挪走 `settings.json`，**`cursor-projects.json` 仍落在 `~/.claude-mem/`**——
  本轮第一次起隔离实例就这么把 4 个探针写进了真实注册表（原始 16 条未丢，已清理）；
  正确写法是 `-Dclaudemem.data-dir=...`。两键并存、语义重叠、文档未说明，需项目拍板收敛。



<!-- - **Status**: ✅ **已修** —— 两处改为地板 `100 * time.Millisecond`，与  -->
- **Status**: ✅ **已修** —— 两处改为地板 `100 * time.Millisecond`，与 `RetryBackoff`
  及 Python SDK 一致；注释改写为说明「为什么是地板」，并记录修复前的实测。
  **新增 3 条测试**（`config_internal_test.go`，**必须是内部测试包**：
  归一化结果存在未导出的 `httpClient` 上，外部测试包 `cortexmem_test` **完全无法观察**
  ——**这正是该缺陷能存活的原因：没有任何测试断言过归一化路径**）：
  地板与透传、两个对照组（`RetryBackoff` 地板、默认值 30s/10s/500ms 不变）。
  **双向注入**：回退到修复前的 `= 30 * time.Second` / `= 10 * time.Second` 后
  **恰好 1 条失败**（`TestClientTimeoutIsFlooredNotReset`），
  **两条对照组在两种状态下都不失败**。根模块 **299 → 302**，
  覆盖率 **95.2% → 95.7%**，全模块 `test-all.sh` 九个模块全绿。
  **两份 README 的测试数已双语同步 359 → 362**（根模块 299 → 302、core 232 → 235、
  实测日期 2026-10-03 → 2026-10-04）。
