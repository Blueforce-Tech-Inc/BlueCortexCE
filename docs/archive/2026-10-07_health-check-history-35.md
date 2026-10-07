# 健康检查历史 35 — 第 342、343 轮（第 355 轮迁出）

> **归档规则**：承 `-19` ~ `-34` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
> 一次迁两轮承第 342 轮体例。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数取自**最终内容**的计算值并与 `doc-growth-check.sh` 实测对照，
> 不手写——本脚本首版用占位指针先算一遍，差了 1 行 / 1 字节（占位比最终多一个空行）。

> 本批为写入第 355 轮报告后 `docs/drafts/health-check-task.md` 达 **1508 行 /
> 95235 字节**（**行数越线** 1500，字节未越 150000）而迁出 **2 轮**：第 342、343 轮。
>
> 第 312~339 轮在工作文件里已是一行指针（更早各轮在 `-19` ~ `-32` 中），
> **第 342、343 轮是最早仍保留完整报告的两轮**。只迁第 342 轮**不够**：
> 1618 → 1563 行，仍高于 1500，故一次迁两轮。
> 轮次边界按**轮号**匹配 `## 第 N 轮 — ` 定位，**不按行号相邻**——第 342 轮教训。
>
> 迁出后实测 **1355 行 / 85579 字节**。

## 块 1 / 2：第 342 轮全文（第 355 轮逐字迁出，原 77 行）

## 第 342 轮 — 2026-10-07T12:10:00+08:00

代码方向：**Java SDK**（**零缺陷**）；文档方向：**SDK README**（doc round 242
—— **零缺陷**，配对审 Java SDK README 双语）

### 健康预检

HEAD = `961b797`，工作区干净，`main…origin/main` 无分叉，37777 UP，
指纹 `aa9c977e…` / 2218 与基线一致，37778 / 37790 / 37791 空闲。

### 代码方向（Java SDK）：零缺陷

**回归审计三条全过**（第 336 轮以来该目录**逐字节未变**——
`git diff 5fd8a06..HEAD -- cortex-mem-spring-integration` 为空，前提是验证过的不是假设）：

- **P2-92**：`CortexMemoryTools` 的注释仍如实描述两条分支，
  `Math.max(1, Math.min(defaultCount, 10))` 的构造函数钳制在位，
  `effectiveCount = (count == null || count <= 0) ? defaultCount : Math.min(count, 10)` 一致；
- **P2-64**：两个 `@JsonIgnore` 都在（`ExtractionResponse.isFound()` 与 `ObservationUpdate.isEmpty()`），
  且 dto 包里 `isX()` 访问器**只有这两个**，无第三个漏网；
- **P2-65**：Python `is_retryable(err_or_status: int | BaseException)` 签名完好。

**新角度一（承上一轮）：15 个线上会发 `null` 的字段，Java DTO 扛不扛得住？**
逐字段核 `ObservationResponse` 的 24 个组件——**全部是包装类型**
（`Float qualityScore`、`Integer promptNumber`、`Long createdAtEpoch`、
`Integer accessCount`、`List<String>`、`Map<String,Object>`），
**没有一个原始类型**，故上一轮 P2-102 那 15 个 `null` **不会**让 Java 客户端在解包处崩。
`mapToObservationResponse` 也是手写映射，每个数值字段都先判 `!= null` 再转型。**零缺陷。**

**新角度二：`strList` 的跨家一致性**。wire 上 `facts` 等四个 JSONB 列到达时是
**JSON 编码字符串**，而 record 声明 `List<String>`。Java 的 `strList` 先试 JSON 解析、
非 List 则返 `null`、解析失败才降级逗号切分——那么**合法 JSON 标量**（如 `"123"`）呢？
读码结论是 Java/JS/Python **丢弃**、Go **保留**，**3 对 1 分歧**。
**按纪律用受控实验验证，没有只靠读码**：临时 Go 测试实测
`"123" → ["123"]`、`"abc" → ["abc"]`、`["a","b"] → ["a","b"]`、裸 `123 → nil`；
探针文件已删。JS 同输入实测 `"123" → undefined`（`safeStringOrStringList`）。

**但判定为「核实为真、不立条目」**，两条理由：

1. **触发条件是 API 制造不出来的数据**——`PATCH /api/memory/observations/{id}` 对非列表
   返 400（`facts must be a list of strings`），导入路径也只会产出列表或 null；
   要造出这个状态只能直接改库或某个 importer 先坏掉。
2. **文档没写错，是没写**：JS README 那句是「a string that is **not valid JSON**
   degrades to a comma-separated split」——对**合法 JSON 标量**它保持沉默。
   **遗漏 ≠ 失实**，按既定纪律不立条目；且分歧方向是 Go 更保守，不是谁错了。

### 文档方向（SDK README，doc round 242）：Java SDK README 双语，零缺陷

配对审同一模块的 README（861 行 EN / 842 行 ZH，第 336 轮之后该目录未变）。

- **测试数 196 = 143 + 46 + 7** 仍然成立（第 336 轮用 `mvn clean test` 的每模块
  `Tests run:` 行逐项验过；本轮以「目录未变」为前提复核，前提本身已验证）。
- **方法表 25 条**，EN/ZH 各 25、**零差异**。
- **`ExtractionResponse` 示例逐项对回 record**：示例注释列的 7 个字段
  （status / template / sessionId / extractedData / createdAt / observationId / message）
  与 record 的 7 个组件**同序同名**，类型也对——`createdAt: 1234567890` 对应
  `Long createdAt`（epoch 毫秒），`message: null` 对应可空的 `String message`。**无误**。
- **P2-102 不涉及此处**：该 README **没有观测字段表**，故上一轮改的可空性声明无需同步。
- **双语对拍零差异**：H2 = 19、H3 = 24、代码围栏 = 64、表头 = 9，四项两种语言**逐项相等**。

### 变更检测

本轮**未改任何代码或既有文档**，未新增 finding。指纹 `aa9c977e…` / 2218 **与基线一致**
→ **不跑完整验收、不推进基线**。37777 保留运行。

### 本轮踩到的仪器错误（三处）

①两次把 `cortex-mem-spring-client` 当成模块路径（实际是 `cortex-mem-client`），
写死路径连错两次，改用 glob 定位。
②跨家比较时只把 Java 的实验做成了受控实验，Go 补了、JS 只截到 4 行输出（漏掉 `"123"` 那行），
差点在缺一行输入的情况下下结论——补跑后才拿到完整 5 组。
③上一轮遗留的教训：`/tmp` 里的脚本会被清空，本轮的 Go 探针文件我**用完即删**，
不留依赖。

### 下一轮

代码方向：**Go SDK**；文档方向：**设计文档**（doc round 243）。

## 块 2 / 2：第 343 轮全文（第 355 轮逐字迁出，原 80 行）

## 第 343 轮 — 2026-10-07T12:50:00+08:00

代码方向：**Go SDK**（**零缺陷**）；文档方向：**设计文档**（doc round 243
—— 审一份**全库零覆盖**的设计稿，**零缺陷**）

### 健康预检

HEAD = `d0e32da`，工作区干净，`main…origin/main` 无分叉，37777 UP，指纹与基线一致。

### 一条运维教训：后端是被**任务超时**杀的，不是崩的

进本轮时收到一条后台任务 failed：`Background bash timed out after 3000000ms`。
**37777 已无监听、无 cortex 进程**，而日志末尾是 `12:26:43 Scheduled refinement completed for 2241 projects`
——**正常的定时精炼，戛然而止，零报错**。

这与上一轮 PG 的 crash recovery **是两回事**（那条有 `database system is starting up` 与 recovery 状态为证）。
若按上一轮的路径去查，会得出错误根因。**判据：进程消失先看日志末尾**——
正常业务日志戛然而止 = 被外部中止；有崩溃/恢复痕迹才是真故障。

**成因是我自己的操作缺陷**：后端被我挂在受管后台任务里，任务到期即被连带杀掉
（此前能长期存活的 pid 4029 应是 detached 启动的）。
**第一次修法又错了**：按 Linux 习惯写了 `nohup setsid java …`，
**macOS 没有 `setsid`**，日志里只有 `nohup: setsid: No such file or directory`，进程压根没起。
改用 `nohup … > log 2>&1 < /dev/null & disown` 后，
**在另一次独立调用里**验证仍在监听（pid 61458）——这才是「活下来了」的阳性对照，
否则测到的只是启动那一瞬间。

### 代码方向（Go SDK）：零缺陷

- **P2-84**：`client_impl.go:131` 仍是 `strings.TrimRight(cfg.BaseURL, "/")`，
  且保留着「TrimSuffix 只去一个」的成因注释；测试 `:3192` 同样在位。
- **P2-53**：`WithTimeout(d)` 只做 `c.Timeout = d`，**不再有「太小就重置为最大值」的钳制**，
  已是 resolved 形态。
- **计数未漂移**（前提是**验证过的**）：`git diff f05c02d..HEAD -- go-sdk` 为空，
  故第 337 轮逐模块实测的 **310 顶层 / 363 含子测试**仍然成立
  （P2-90 记的「README 写 362、实测 363」这条 ⏸ 记录也随之仍然准确）。

### 文档方向（设计文档，doc round 243）：`mcp-server-transport-analysis.md`，零缺陷

按类名对 **findings + 全部 101 份归档 + 巡检报告**扫一遍「设计文档」清单，
**五份零提及**：`mcp-server-transport-analysis.md`、`sdk-improvement-research.md`、
`cortex-mem-integration-capture-analysis.md`、`memory-research-hub.md`、
`screenpulse-implementation-plan.md`。扫描器经 **3 组阳性对照**（已审文档不得被报、
不存在名字应被报、抽掉一个名字必须恰好多出那一个）确认零集为真。
选了其中**断言最可验证**的一份来审（21 KB，创建于 2026-03-19）。

逐条对拍结果——**能验的全对**：

| 文档断言 | 实测 |
|---|---|
| §2.1 Spring AI **1.1.2** | `backend/pom.xml:22` ✓ |
| §2.1 `spring-ai-starter-mcp-server-webmvc` | `pom.xml:72` ✓ |
| §2.1 协议 **SSE**、`GET /sse`、`POST /mcp/message`、**SYNC** | `application.yml:111-117` ✓ |
| §2.2 `name` / `version` / `type` / `protocol` / `sse-endpoint` / `sse-message-endpoint` | 逐项一致 ✓ |
| §2.2 引用的 **`capabilities`** 四项 | `application.yml:120-124` 逐字一致 ✓ |
| §2.3 **5 个 MCP Tools** 名称 | `ClaudeMemMcpTools` 的 `@Tool(name=…)`：`search` / `timeline` / `get_observations` / `save_memory` / `recent` ✓ |
| §2.5 测试脚本实现了完整 SSE 握手 | `scripts/mcp-e2e-test.sh:142-164` 确实提取 `event:endpoint` → `data:/mcp/message` ✓ |

§2.4 的测试计数（回归 31、WebUI 11/11、Thin Proxy 18/18）是 **2026-03-19 的带日期快照**，
文档首行即标「创建时间：2026-03-19」，与第 340 轮判「16 个不同项目」同一处理——
**带日期的快照不是失实陈述**，不立条目。

### 本轮踩到的仪器错误（三处，全部先识别再采信）

①**把被 `head -20` 截断的 grep 输出当成「`capabilities` 不存在」**，
差点写出一条「文档引用了不存在的配置块」的假 finding——实际它在 `application.yml:120`，
只是夹在 `streamable-http` 之后、恰好落在截断线外。**这是「读数来自仪器输出而非事实」的老坑。**
②抓 `@Tool` 名称的第一版正则（`-A 2` 配 `-oE`）返回**空**，我没有采信，
改用直接抓 `name = "` 才拿到 5 个——**一个只可能返回零的比较按仪器坏了处理**。
③压缩脚本的连续性判据先写成「行号相邻」，而两个轮次之间隔着整个 329 轮报告，
由断言当场拦下，改为「按轮号判断、其间不夹其他轮次」。

### 变更检测

本轮**未改任何代码或既有文档**，未新增 finding。指纹与基线一致
→ **不跑完整验收、不推进基线**。37777 保留运行（detached，pid 61458）。

### 下一轮

代码方向：**Python SDK**；文档方向：**架构文档**（doc round 244）。
