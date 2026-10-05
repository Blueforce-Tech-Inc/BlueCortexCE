# Health Check History 15 — 第 273–278 轮巡检报告（第 283 轮迁出）

> **归档规则**：巡检报告按压缩阈值逐字迁出，内容不作任何改写。
> **归档文件创建后不得修改。**

> 触发原因：写入第 283 轮后本文件达 **1001 行**、越过 `MAX_LINES=1000`（`doc-growth-check.sh` 退出码 2）。
> 承既有规则：**按轮号**匹配 `## 第 N 轮 — ` 提取，并逐块断言边界干净（块内无嵌套轮次标题，11/11 通过）。
> 本次迁出 6 块，工作文件保留第 279–283 轮。

---

## 第 273 轮 — 2026-10-05T23:43:13+08:00

### 健康预检

HEAD=`b75e9ea`、工作区仅本轮改动、37777 健康
（`{"service":"claude-mem-java","status":"ok"}`）、指纹与基线一致、五份活动文档退出 0。

### 代码方向（Java SDK）：P2-64 —— Jackson 把 record 的 `isX()` 泄漏成 wire 字段（已修）

**问题**：Jackson 对 record 的**每个无参访问器**都按属性序列化，因此
`ObservationUpdate.isEmpty()` 被发上 wire 成 `"empty": false`——
**每个 PATCH 请求都带一个调用方从未设置过的字段**，与该类 Javadoc 自称的
*"only explicitly set fields are sent"* **直接矛盾**。`ExtractionResponse.isFound()` 同理多出 `"found"`。

**修复前实测**（直接序列化编译产物）：`{"title":"T","empty":false}`。

**影响判定**：后端 `MemoryController` 忽略未知键（活体 PATCH 带 `empty` 仍 200 且
title 已更新），故**无功能损坏**；但报文与成文契约不符，且属「已损坏行为」而非契约设计，
故按既定规则可修。

**修复**：两个访问器加 `@JsonIgnore`。
**修复后实测** `{"title":"T"}`，且 null→省略、`facts=[]`→照发、`extractedData={}`→照发等
**原有语义全部保持**，`isFound()` 反序列化后仍正确求值。**无任何测试断言该字段**。
Java SDK 196/0/0/0 全绿、BUILD SUCCESS。

**顺带实证**：`facts:[]` 与 `facts:null` **都能清空**，故 P2-26/P2-27 记录的清空限制
对 Java 的**列表字段不成立**（Java 可用空集合清空）。**再次印证：跨 SDK 一致性问题
必须先实测能力再判，不能因「Go/Python 不能」就推定「Java 也不能」。**

**一个探针自身出错并已先识别**：首次建探针观测时误用 `POST /api/memory/save`——
**正是 P2-48 记录的幻影端点（404）**，改用 `/api/ingest/observation` 才成功。

### 完整验收（新鲜度闸门第三次适用）

指纹 `9d1f8307…` → `8bb93d16…`。本轮改的是 Java SDK，**不在后端二进制内**：
`backend/pom.xml` 不依赖 Java SDK，且 fat jar 内 `cortexce/client/dto/` 类数为 **0**。
故 37777 仍新鲜，直接对其验收：

- 回归 `bash scripts/regression-test.sh --skip-build` → **46 / 0 失败 / 1 跳过**
- `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` → **25 / 0 / 0**

**全通过 → 基线推进**至 `accepted_commit: b75e9ea` / `code_fingerprint: 8bb93d16…`。

### 文档方向（运维/用户指南，doc round 174）

**已修 1**：根 README `### Common Commands` 的 `cd ~/.cortexce` 只对 README 自己
**Step 1–3 建立的**那套安装布局成立；对**上面 Quick Start Option 1**（推荐路径，
`cd BlueCortexCE`）的读者会直接 `cd: no such file or directory`。
两版均改为显式列出两种布局。

> **自我更正**：本轮开始时我判定「`~/.cortexce` 全仓再无第二处提及」，
> **这是错的**——README 自身第 85、129–132 行就在用该目录，Step 3 还把
> `docker-compose.yml` 与 `proxy/` 复制进去。核实 `docker-compose.yml` 用的是
> **预构建镜像、只依赖 `.env`**，该布局**确实自洽**。真正的缺陷不是「目录不存在」，
> 而是**同一份 README 提供两种安装布局，而命令块只默认其中一种且未加说明**。
> 探针报错与缺陷真伪同形，**先识别再采信**这条规则再次生效。

**已修 2**：DOCKER_README 中英 E2E 章节 **H3 次序不一致**（EN 把 Test Coverage 排在
Docker Compose Test / Test Ports 之前，ZH 排在之后）。**纯移动，15 删 15 增、零内容变化**。
上轮曾统一过两文件的 H2 顺序，H3 残留是那次统一的遗漏。

**零缺陷（已逐项核实）**：

| 维度 | 结果 |
|---|---|
| DOCKER_README 环境变量 vs `docker-compose.yml` | 23 / 23 **集合完全相等**（零缺零多） |
| 可选变量默认值 vs compose 默认值 | 20 / 20 逐个吻合 |
| 仓库结构树 | 8 / 8 项全在，`webui` 确为 submodule |
| E2E 覆盖清单 vs `docker-e2e-test.sh` | **11 个 `test_*` 函数与文档 11 项逐项同序** |
| 测试端口 | 15432/38888、15433/38889 精确吻合 |
| 脚本引用（3 份运维文档 × 37 个脚本） | 0 个幻影 |
| DEPLOYMENT 中英 H2/H3/H4/围栏/表格 | 11 / 37 / 35 / 94 / 119 **五项全等**，编号次序完全一致 |
| DEPLOYMENT 内部链接 + 目录锚点 | 0 断链；两版目录各 8 条链接**全部可解析** |
| DEPLOYMENT 引用端点 vs 活体 OpenAPI | 12 条**全部真实存在** |

**两个探针命中的是文档已解释过的事，非缺陷**：
① `backup.sh` / `recovery.sh` 不是引用仓库脚本，而是文档内**让用户自建的脚本示例**
（`#!/bin/bash` + `# backup.sh` 注释 + cron 里写 `/path/to/backup.sh` 占位路径）；
② `/api/session/start` 我一开始怀疑是 `/api/ingest/session-start` 的笔误，
**活体 OpenAPI 证明它是真实的 POST 端点**，怀疑不成立。

### 变更检测

改了代码（Java SDK 两个 DTO）且指纹变化 → **已跑完整验收并推进基线**。

---

## 第 274 轮 — 2026-10-06T00:12:00+08:00

### 健康预检

HEAD=`9b985ee`、工作区干净、仅 37777 在监听且健康、指纹与基线一致（`8bb93d16…`）、
五份活动文档退出 0。

### 代码方向（Python SDK）：P2-65 —— `is_retryable` 与 Go/JS 同名却收不同类型的参数（已修）

**取的角度是跨 SDK 错误谓词集对拍**。结果：Go 15 / JS 14 / Python 15 个 `is*` 谓词，
差集只有两处，**其中一处是真缺陷**：

Go `IsRetryable(err error)`、JS `isRetryable(err: unknown)` —— **两家都只有一个重试判定函数，且收 error**。
Python 有**两个**：`is_retryable(status_code)` 与 `is_retryable_error(err)`，
**与 Go/JS 同名的那个收的是状态码**。两个名字都**在 `__all__` 里公开导出**，
而 README 对二者**零提及**。

**实测（未改任何代码前）**：`is_retryable(e)` 对 `RateLimitError`(429)、`APIError(502/503/504)`
**全部返回 `False`、不抛异常**，而 Go/JS 对同样输入返回 `True`。
**后果是调用方自己的重试循环永远不触发、且没有任何迹象说明原因**；
不重试的错误返回 `False` 恰恰是对的，所以这个坑**只在本该重试时暴露**。

**已修**（按「**纯加宽 / 向后兼容即可修**」）：参数加宽为 `int | BaseException`，
收异常转发 `is_retryable_error`，收状态码**行为一行未变**，其它类型 fail-closed 返回 `False`。
**未改名、未删任何公开符号。**
**未单方面做的**：把两个函数改名以真正对齐 Go/JS 属**改已发布公开 API 的名字**，记录不实施。

**+12 条测试（441 → 453），双向注入**：只回退这一处加宽则**恰好 7 条失败**
（4 个可重试 API 错误、2 个网络错误、与 `is_retryable_error` 的一致性对拍），
**另 5 条在两种状态下均不失败**（3 条 fail-closed 对照、1 条不重试错误对照、1 条状态码向后兼容守卫）。

**内部用法本就正确**：`client.py:199` 在 `except APIError` 内传 `e.status_code`，**不受影响**。
**顺带更正** Python SDK README 测试数（两版 441 → 453，226 + 140 + 87）——
**否则就是本循环反复在抓的「改了测试没回头改这个数」同型陈旧**，故一并处理。

**非缺陷**：Go 独有 `IsInternal`(500)，JS 与 Python 都没有对应谓词——**是 Go 多一个，不是别家缺**。

### 完整验收（新鲜度闸门第四次适用）

指纹 `8bb93d16…` → `767325cc…`。本轮改动是 Python-only：
`backend/pom.xml` 对 Python SDK **零引用**、fat jar 内 `.py` 条目数 **0**。
**另补一条比前三轮更严的核实**：`37777` 启动于 17:51:24，而 `backend/src` 最新改动是 22:13，
故逐个查了**启动后被改过的 backend 文件**——只有第 270 轮那两个，
并对其 diff 做「**非注释增删行**」过滤，结果**为空**，即该轮为**纯注释变更、行为等价**。
结论：37777 运行的是行为等价的当前后端，且本轮改动不可能在其中。

- 回归 `bash scripts/regression-test.sh --skip-build` → **0 失败 / 1 跳过**（断言数见下）
- `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` → **25 / 0 / 0**

### **回归脚本的「Passed」不是不变量 —— 历轮报告里的 46 有误导性**

**同一份脚本、同一个未改动的二进制、连续四次运行**：

| 运行 | Passed | Failed | Skipped | Total |
|---|---|---|---|---|
| 1 | **47** | 0 | 1 | 48 |
| 2 | **45** | 0 | 1 | 46 |
| 3 | 45 | — | — | — |
| 4 | 45 | — | — | — |

**脚本自 2026-09-30 起未改动**（`git log` 核实），本轮 diff 也**未触碰 `scripts/`**。
第 3、4 次的 PASS 集合逐行对拍，**唯一差异是一个随机观测 UUID**。

**计数口径是断言数而非测试数**：`log_success()` 每调用一次 `TESTS_PASSED++`，
静态调用点 **52** 个，而实测产出 45–47。**故「46/0/1」自始至终是某一次运行的读数，
不是这套回归的规模**。**本轮未能定位波动机制，不臆测**——已确认它**不是**循环内重复计数
（全脚本仅 1 处 `log_success` 在循环内、且有界至 1 次），也不是随机 UUID 造成的（UUID 只出现在消息文本里）。

**行动项**：后续报告一律按「N 次断言、0 失败」表述，不再把它当作可对拍的固定数字。

### 文档方向（API 文档，doc round 175）：**零缺陷，两个探针假阳性**

活体 OpenAPI **62 路径**，双语各 **66 条**端点字面量：

| 维度 | API.md | API-zh-CN.md |
|---|---|---|
| 活体路径未被文档覆盖 | **0** | **0** |
| 文档有而活体无 | 4（见下，**全部证伪**） | 4（同） |

**两个探针假阳性，均先识别再采信**：
① **首版探针 `cited=0`**——它要求方法与路径出现在同一行标题里，而 API 文档
（正是第 160 轮记录过的结构差异）**把路径放标题、方法放下方代码块**，故一个都没匹配上。
**一个 62/62 全缺的假象差点被我写成「文档整份缺失」**。改按字面量提取后才是上表。
② 4 条「幻影」里 3 条是**主机名**（`api.anthropic.com` / `api.deepseek.com` / `api.siliconflow.cn`），
第 4 条 `/api/memory/observations` 的两处**都在 changelog 表格里描述历史修改的散文**——
**正是第 160 轮已明确排除过的那一类**。

### 压缩（第 31 次）

一次迁出**两个块**（P2-59 的字面响应体与控制器普查、P2-62 的行号与反射实测表），
Problem 的问题陈述与 `- **已修**` / `- **不修的理由**` / `- **Status**` 一行未动。

**采用删除前快照做双向验证**（第 272 轮踩过「反向检查得出误导结论」的坑）：
应迁出 44 条非空行**丢失 0**、应保留 864 条非空行**丢失 0**、指针数 2 = 归档块数 2、
三种边界行首均非空、18 个归档链接全部可解析。1000 → **985**。

**并首次手工补上第 271 轮 P2-63 暴露、当时记为「属规则变更待决」的那条断言**——
「工作文件里曾存在的条目是否还在」：条目数 **54 → 54、丢失 0**（新写入 P2-65 后为 55）。

写入 P2-65 后达 **1004 行、触发 `COMPACTION_REQUIRED`（退出码 2）**——**正是历轮记录的、
下一条 finding 必然越线的那个结构性阻塞**。收紧 P2-65 自身表述后回到 **1000/1000、退出 0**。

---

## 第 275 轮 — 2026-10-06T00:47:00+08:00

### 健康预检

HEAD=`04b73a2`、工作区干净、仅 37777 在监听且健康、指纹 `767325cc…` 与本轮验收一致、
五份活动文档退出 0。

### 代码方向（JS/TS SDK）：P2-66 —— 注释写了一条后端并不遵循的优先级规则

**角度是顺延 P2-64 的新问题**：「计算型访问器泄漏到 wire」这个缺陷形状，在另外三家是否存在？
**逐一核实，答案是「结构上不可能」**：

| SDK | 机制 | 能否泄漏 |
|---|---|---|
| Python | 显式 `_WIRE_FIELDS` 白名单 + `to_wire()` | 否（白名单） |
| JS | 纯 `interface` → 普通对象字面量 | 否（无方法/getter） |
| Go | `encoding/json` 只序列化**结构体字段** | 否（`IsEmpty()`/`HasConflict()` 是方法） |
| Java | record 访问器全部按属性 | **是**（即 P2-64，已修） |

**故 P2-64 的影响面到此为止，是 Java 独有的。**

**真正的新发现（P2-66）** 在 `content`/`narrative` 这对别名上。原注释称
`content` 走「backend uses "narrative" wire field」、`narrative` 则
「When both are set, backend processes **whichever is present**」——**两条都与实测不符**。

**根因先从代码读出、再用活体证实**：`MemoryController.java:318` 写的是
`body.getOrDefault("content", body.get("narrative"))`——
**`getOrDefault` 在 key 存在时返回其映射值，哪怕该值就是 `null`**，
故 `content` 的显式 null 会压过 `narrative`。
且 `mem_observations` **根本没有 `narrative` 列**（`information_schema` 核实，只有 `content`）。

**实测四例**（本轮自建探针观测，用完已删、已核实剩余 0 行）：

| 情形 | 发出 | 落库 |
|---|---|---|
| A | `content=A, narrative=B` | **A** |
| B | **`content=null, narrative=C`** | **NULL —— narrative 被整个丢弃** |
| C | 仅 `narrative=D` | D |
| D | 仅 `content=E` | E |

**四家对拍**：Go 有 `HasConflict()`（`dto/observation.go`，13 处测试引用）、
Java Builder 在两者同设时抛 `IllegalStateException`、Python 抛 `ValidationError` ——
**只有 JS 一处检测都没有**，而那唯一一处说明还写错了。
**危害**：JS 用户写 `{content:null, narrative:"文本"}`（本意是设置 narrative）
会**静默清空该字段并丢弃所写内容**，注释却告诉他 narrative「会被处理」。

**已修**：两条 JSDoc 改为如实描述并附实测四例（**零行为变更**），`tsc --noEmit` 干净、**259/259** 全绿。
**检测不实施、只记录**：给 JS 补冲突拒绝是**让原本被接受的调用变成抛错**，属收窄已发布契约。

### 文档方向（SDK README，doc round 176）：**四家测试数逐一实测，全部吻合，零缺陷**

按「**数字必须连同计数命令一起核对**」，四家全测：

| SDK | README 声明 | 实测 | 拆分 |
|---|---|---|---|
| Java | 196 | **196** | 143 client + 46 spring-ai + 7 starter（BUILD SUCCESS） |
| Go | 362 | **362** | 302 根 + 8 eino + 27 http-server + 13 genkit + 12 langchaingo |
| Python | 453 | **453** | 226 + 140 + 87 |
| JS | 259 | **259** | 246 + 5 + 8 |

**Go 的口径值得记一笔**：第一次用 `--- PASS` 行数得 309，**比 README 少 53**——
该口径**漏掉表驱动子测试**。改用 `go test -json` 的 `Action:pass` 事件后**精确得 362**。
README 里「根模块只跑到 302 / 362」那句也随之得到验证。

**一个探针假阳性**：grep「N 个单元测试」在 Go 中文版返回 0 条，看似**中英缺口**；
实际中文版**有**同样的 362 声明，只是措辞是「362 个测试」——**是我的正则没覆盖中文格式**，
**差一步就把一个不存在的缺口写成缺陷**。

### 完整验收（新鲜度闸门第五次适用）

指纹 `767325cc…` → `361944d0…`。本轮改动是 JS/TS + 文档，不在后端二进制内
（`backend/pom.xml` 无引用、jar 内无相关产物）；37777 启动后 `backend/src` 仅第 270 轮那两个文件被改、
且其 diff **零非注释行**（同第 274 轮核实），故运行的是行为等价的当前后端。

- 回归 → **0 失败 / 1 跳过**（断言数按第 274 轮的结论不作为不变量引用）
- `EXTRACTION_ENABLED=true` Phase 3 → **25 / 0 / 0**
- Java SDK `mvn -o test` → **BUILD SUCCESS**，四模块 143 / 46 / 7 = **196**
- JS `tsc --noEmit` 干净、`npm test` **259/259**

### 压缩（第 32、33 次）+ **三处我自己的探针/流程缺陷**

写入 P2-66 后达 **1014 行、退出码 2**。两次压缩：P2-27 的活体行级统计、P2-20 的实测清单与排查陷阱。

**顺带修掉 P2-14 一处逐字重复句**——「一个从未接线的特性，只留下方法、注释和一条为它建的索引。」
在 Problem 段**逐字出现两次**（一次跨第 7-8 行、一次在第 9 行整行），系早前压缩残留。
**工作文件删掉重复那行；归档侧按「创建后不得修改」保留原样并在块首注明。**

**本批暴露的三处自身缺陷，全部先识别再采信**：

1. **「指针数 = 归档块数」这个断言一直是错的**——我按**字符串出现次数**计数，
   而一个 Markdown 指针天然出现**两次**（链接文字 + URL）。
   **故上一批的 evidence-23 实为「1 个指针行 / 2 个归档块」，却因为数到 2 次出现而误判通过。**
   本批起改按**指针行**计数，并为 P2-59 与 P2-14 **各补上缺失的指针**。
2. **「应保留的行仍在工作文件」用了过时的基准**——基准是压缩前快照，
   而本轮在快照之后又两次收紧了 P2-66 自身，于是报出两条「丢失行」；
   **那正是我自己改掉的行**，属基准过时而非压缩损坏。
3. 验证脚本 f-string 内嵌反斜杠触发 `SyntaxError`、`next()` 未给默认值抛 `StopIteration`，均改写后重跑。

**教训：断言通过的原因，必须和断言想验证的东西一致。**
这正是第 271 轮 P2-63 那条「缺一条断言」的同类问题——**这次缺的不是断言，是断言本身的正确性**。

---

## 第 276 轮 — 2026-10-06T01:12:00+08:00

### 健康预检

HEAD=`eeb71e8`、工作区干净、37777 健康、指纹 `361944d0…` 与基线一致、五份活动文档退出 0。

### 代码方向（Demo）：**零缺陷**，但把 P2-64 的审计做完，并**更正了我自己上一轮写下的过宽断言**

**第一步：把 P2-64 的缺陷形状拿到另外三家求证**——结论是**结构上不可能**，且原因各不相同：
Python 走显式 `_WIRE_FIELDS` 白名单、JS 是纯 `interface`、Go 的 `encoding/json` 只序列化结构体字段
（`IsEmpty()`/`HasConflict()` 是方法）。**P2-64 的影响面到此为止，是 Java 独有的。**

**第二步：全量列出 SDK 21 个 DTO record 的非 static 无参方法**，逐个判断是否会泄漏。
初版探针把 `static` 方法与**嵌套 `public static class Builder` 内部的方法**也算进去了——**全是假阳性**。

**第三步：受控实验定论。** 一个 record 同时带 `isEmpty()` / `getSubtitle()` / `content()` /
`total()` / `hasThing()`，实测输出：

```json
{"title":"T","narrative":"N","empty":false,"subtitle":"G"}
```

**只有符合 JavaBeans 约定的 `isXxx()` 与 `getXxx()` 泄漏**；`content()` / `total()` / `hasThing()`
**全部不可见**。直接对 `ObservationResponse` 与 `QualityDistribution` 序列化验证：`content` 与 `total` 键**均未出现**。

> **这推翻了我第 273 轮亲手写下的那句话**——两个 DTO 的 Javadoc 与 findings 的 P2-64 条目里
> 都写着「Jackson 对 record 的**每个访问器**都按属性序列化」，**这句是错的**。
> **只差一步，我就按静态分析给 `content()` 和 `total()` 也加上 `@JsonIgnore`，
> 制造两个不存在的缺陷。** 三处（两处源码 Javadoc + findings 条目）已按实测更正为准确规则。

**据此全量复查的结论**：带 `isXxx()` 布尔访问器的 record **只有已修的那两个**，
**没有任何 record 带 `getXxx()` 访问器**。**故 P2-64 的修复是完整的，无遗漏项。**

**Demo 侧本身**：`Map.of` 的两个崩溃面都查了——**参数上限**（我一度断言「三处超过 10 个参数」，
**错的是我**：`Map.of` 是最多 **10 对**即 20 个参数，三处 6/6/7 对**都在限内**）、
**null 值抛 NPE**（`ExtractionController` 每个字段都做了三元兜底，`ManagementController` 直传 SDK 字段，
但唯一的可空字段 `project` 经后端源码与活体响应核实**每条路径都回显、永不为 null**，**NPE 不可达，不作为缺陷写入**）。
**连续第三次栽在正则探针上**（两个 `Map.of` 扫描脚本都返回 0，而代码里明明有），**已改为直接读代码**。

### 文档方向（设计文档，doc round 177）：**零缺陷，处置表 8/8 准确**

- **21 条内部链接零断链**。
- **四个抽取配置键零幻影**：`EXTRACTION_ENABLED` / `_MAX_CANDIDATES` / `_BATCH_SIZE` / `_MAX_BATCHES`
  **全部被 `application.yml:34-37` 真实读取**，且设计文档里的配置块与 yml **逐行一致**、默认值全对；
  `# inert at these defaults` 的注解也成立（20 × 10 = 200 > 100 候选，第二批永不触发，即 P2-17）。
- **迁移版本正确**：`17.md:20` 明写「V1–V18；V9 和 V10 不存在」，与 `db/migration/` 实有 16 个文件吻合。
- **类名引用 12 个「未找到」里 7 个是我扫描范围错**（只扫了后端四个包，而这些类属 Java SDK 或 `config`/`dto` 包）。
  余下 5 个逐一查原文：`AllergyInfo` 在**代码示例的注释**里，其余四个在 `15.md:98-101` 的
  **文件处置表**中被显式标注为 `➡️ Inlined` / `➡️ Not needed` / `➡️ Merged into`。
- **处置表逐行核实 8/8**：4 个「✅ Created」**全部存在**、4 个「➡️ 未创建」**确实不存在**、
  `Merged into ExtractionConfig.TemplateConfig` 中的 `TemplateConfig` **确在**（4 处引用）。
  **设计文档主动记录了哪些提案文件没有被创建——这是少见的诚实，予以肯定。**

### 完整验收

指纹 `361944d0…` → `6422ca55…`。本轮改动是 Java SDK 的两处 **Javadoc**（零行为变更）
与文档，**不在后端二进制内**；37777 启动后 `backend/src` 仅第 270 轮那两个文件被改且 diff 零非注释行。
回归 **0 失败 / 1 跳过**、Phase 3 **25 / 0 / 0**、Java SDK `mvn -o test` **EXIT=0**（196 全绿）。

### 压缩（第 34 次）

P2-64 的更正块把文件顶到 1007 行、退出码 2；迁出 P2-19 的三家行号引用后回到 **1000/1000、退出 0**。
双向验证：应迁出 24 条、应保留 888 条**均丢失 0**，条目 56 → 56，四个归档的**指针行数 = 块数**全部成立。

> **本批又撞上第 275 轮刚写进归档的同一条教训**：「应保留的行仍在工作文件」以压缩前快照为基准，
> 而本轮在快照之后**又收紧了 P2-64 的更正块**，于是报出 2 条「丢失行」——**那正是我自己改写的行**。
> **上一批刚记下的教训，下一批就重犯。** 已改进：验证脚本改为**显式列出有意改动的区间并排除**，
> 而不是靠字符串模式去猜。

---

## 第 277 轮 — 2026-10-06T01:44:00+08:00

### 健康预检

HEAD=`5f9dfe3`、工作区干净、37777 健康、指纹 `6422ca55…` 与基线一致、五份活动文档退出 0。

### 代码方向（Backend）：P2-67 —— `AsyncConfig` 的 Javadoc 把「按任务超时」列为它提供的能力

**取的角度是「类 Javadoc 声称的能力是否真的存在」**（P2-62 同型的推广）。

`AsyncConfig` 的类 Javadoc 列了三条能力，第二条是「**Timeout handling for async methods**」。
**核实结果：全后端不存在任何按任务超时机制。**

- `getAsyncExecutor()` 只配了 core/max/queue/threadNamePrefix/拒绝处理器/关机等待，**无超时**；
- 全后端搜 `setTimeout` / `TimeoutInterceptor` / `Future.get(` **零命中**，`AsyncConfig` 内 `setTimeout` 计数 **0**；
- 唯一与时长有关的是 `await-termination-seconds`，它约束**关机时等运行中任务多久**，
  **不是任务能跑多久**——两者极易混为一谈；
- 5 个 `@Async` 方法（`processToolUseAsync`、`completeSessionAsync`、`refineMemory`、
  `quickRefine`、`deepRefineProjectMemories`）**任一都没有时间上限**。

**影响是具体的**：一次卡住的 LLM 调用或 embedding 会**长期占住一个池线程**；队列打满后拒绝处理器
**回退到调用线程执行**，把阻塞带回调用方——而 `@Async` 的整个前提就是不阻塞调用方。

**第二处较轻的不实**：行内注释称线程池参数「values from application.yml with defaults」，
而 **`application.yml` 里没有 `claudemem.async` 块**，四个 `@Value` 默认值（10/50/100/60）永远生效。

**已修**（零行为变更）：类 Javadoc 如实列出它真正提供的两件事、新增一段写明「不存在按任务超时」
及其搜索证据、讲清 `await-termination-seconds` 的真实语义、指向 P2-67；行内注释注明 yml 无该配置块。
`mvn -o compile` EXIT=0。**能力本身只记录不实施**——加真正的超时需先定策略（中断，还是跑完但丢弃结果），属设计决策。
**该类其余部分核实为真**：`AsyncUncaughtExceptionHandler` 确实存在、点名的两个 critical 方法确实存在、
队列满的回退处理器确有日志与兜底 try/catch。

**本轮另有三处「零缺陷但探针报错」的记录**（又一次印证先识别再采信）：
① 实体字段 ↔ 活体数据库列对拍，首版正则**无法解析 `@Column(name=…, nullable=false)`**，
误报 19 个「实体有而列无」；② 修正后仍误报 `platform_source` 未映射，实为**我的逐行解析器丢了配对**
（`UserPromptEntity.java:43`、`SessionEntity.java:78` **确实映射了**）；
**最终结论是映射完整、零缺陷**，活体数据佐证：38727 条观测与 9365 条会话**全部**有 `platform_source` 值。

### 文档方向（架构文档，doc round 178）：**零缺陷**

- 「Async Processing」段是**代码示例**、并未声称超时能力，不构成缺陷。
- **迁移归属逐条核实为真**：`ARCHITECTURE.md:537,559` 分别声明
  `mem_sessions` 由 V1+V4/V11/V12/V13/V15/V18 改、`mem_observations` 由 V1+V2/V7/V8/V11/V12/V13/V14/V16/V17/V18 改。
  首版探针按「文件里出现表名」判定，得出**两处漏项**（V8→sessions、V15→observations）；
  **查原文后两条都是偶然提及**：V8 只在 `FOREIGN KEY REFERENCES mem_sessions(...)` 里提到它
  （ALTER 目标全是 `mem_observations`），V15 只在一条注释里提到 observations（ALTER 目标是 `mem_sessions`）。
  **文档准确，是探针的判定口径错了。**
- 活体数量：service **29**、controller **13**、entity **6**、repository **6**、event **6**——与历轮记录一致。

### 完整验收

指纹 `6422ca55…` → `ccf8bdd1…`。本轮改的是**后端源码**（Javadoc/注释，零行为变更），
故 37777 **不含**本轮改动；因是**纯注释**、行为等价，且**另跑了当前后端源码自身的测试**：

- 后端 `mvn -o test` → **167 / 0 / 0 / 0，BUILD SUCCESS**（独立于运行中进程，证明当前源码本身是好的）
- 回归 → **0 失败 / 1 跳过**
- `EXTRACTION_ENABLED=true` Phase 3 → **25 / 0 / 0**

### 压缩（第 35、36 次）+ 顺带修掉两处既有损坏

P2-64/P2-67 的写入先后把文件顶出 1000 行；迁出 P2-60 的活体实测与 P2-58 的字段对照表后回到 **1000/1000、退出 0**。

**顺带清掉第 273 轮遗留的 4 处同型重复指针**（P2-55/56/57/58），**全文扫描确证重复指针已清零**。

**另修掉一处更严重的既有损坏**：**P2-56 的 Problem 断在半句**——
「`InvalidParamAdvice` 只匹配 `InvalidParam`，」之后直接跳到下一条 bullet。
**`git log` 逐提交比对确认：该截断从条目首次写入的 `49cd42a` 就存在，不是压缩事故，是撰写时就没写完。**
补全前先**核到当前代码**（`DemoParams.java:112-118` 确只有 `@ExceptionHandler(InvalidParam.class)`、
全 demo 再无第二个 advice），再以归档 `2026-10-05_health-check-history-12.md:263` 的同期叙述交叉印证，
**确认无歧义后才落笔**。

**终验**：条目 56 → 57、应迁出与应保留行**双向丢失 0**、六个归档的**指针行数 = 块数**全部成立、
24 个归档链接可解析、三种边界行首非空。

---

## 第 278 轮 — 2026-10-06T02:05:00+08:00

### 健康预检

HEAD=`ed11dee`、工作区干净、37777 健康、仅 37777 在监听、指纹 `ccf8bdd1…` 与基线一致、五份活动文档退出 0。

### 代码方向（Java SDK）：重试幂等性核实 + **P2-68（后端，Javadoc 自相矛盾）**

**先核实 P2-19 提到的「重试面更宽」那一半**。Java SDK 对 10 个方法做了重试包装，
其中三个是**写操作**：`submitFeedback`(POST)、`updateObservation`(PATCH)、`deleteObservation`(DELETE)。

- `deleteObservation` 按 UUID 删除、**幂等**；
- `updateObservation` 是字段覆盖、**幂等**；
- `submitFeedback` 查后端 `MemoryController.java:247-257`：**`findById` → `setFeedbackType` →
  `setUserComment` → `setFeedbackUpdatedAt` → `save`**，是**对既有行的字段覆盖而非 INSERT**，
  重试只会刷新一次时间戳——**幂等**。

**结论：三个被重试的写操作全部幂等，无缺陷。** 这是 P2-19 那条「重试面更宽」在正确核实后的收口。

**本轮真正的发现（P2-68）** 在核实过程中撞见：读 `MemoryController` 的 `updateObservation` 时，
**同一个方法上两处说明直接相反**——

| 位置 | 说法 | 对错 |
|---|---|---|
| `:268` Javadoc 块注释 | 「**Null values in the body are ignored**（field left unchanged）」 | **错** |
| `:273` `@Operation` 描述 | 「**null values clear the field**, absent fields are left unchanged」 | **对** |

**活体实测站在 `@Operation` 这边**——第 275 轮的探针 PATCH `{"content":null,"narrative":"C"}`
落库 **NULL**（narrative 被整个丢弃），**不是**「忽略」。

**危害在于两份说明的可信度不同**：`@Operation` 是**机器可读的那一份**，
`/v3/api-docs`、SDK 生成器、`docs/API.md` 全部以它为准；而**读源码的人看到的却是 Javadoc**，
即恰好相反的指示。「null 被忽略」也正是 P2-26 / P2-27 / P2-66 一直在绕开的那条错误行为。

**已修**（零行为变更），并**全后端扫过**：这句错误表述**全文仅此一处**，正确表述共 **8 处**。
`mvn -o compile` EXIT=0。

### 文档方向（运维/用户指南，doc round 179）：**发现一处双语同形缺陷**

`docs/TESTING.md` / `-zh-CN` **零幻影脚本**。按纪律核两个计数：

- `phase3-acceptance-test.sh` 的 **15 个测试函数** —— 精确吻合（逐个函数名核实）；
- `run-all-e2e.sh` 的「**一次运行全部 10 个本地 E2E 套件**」—— **这是错的**。

**脚本把 5/10 包在条件里**：`run-all-e2e.sh:129-133` 是
`if is_streamable_mcp; then run_suite "5/10 …" else skip_suite "5/10 …" fi`，
判定函数 `is_streamable_mcp()` 探测 `POST /mcp` 的 `initialize` 是否返回 200；
**脚本自己的头注释也写明了这一点**。

**活体实测**：本机 `POST /mcp initialize` 返回 **404**（后端默认 MCP 传输是 SSE），
即 `is_streamable_mcp()` 为假——**在本机默认配置下 10 个套件里只会跑 9 个**。
所以那句「runs all 10」不只是含糊，**在默认配置下是假的**。

**成因值得记**：TESTING.md 的 changelog 写着「Verified `run-all-e2e.sh` really does run 10 local
suites … so both counts were left unchanged」——**那次核实数的是「定义了几个套件」，
而文档问的是「会跑几个」**。数字对、问题问错了。

**双语均已修正**，写明条件、判定方式、以及「默认安装下跑 9 个、报 1 个跳过是预期行为」。
修正后两版结构对拍：标题各 36、表格字符各 186、围栏各 20，**完全一致**。

### 完整验收

指纹变化，本轮改动是**后端 Javadoc（零行为变更）** + 文档。
因 37777 不含本轮改动且为纯注释，**另跑当前后端源码自身测试**：

- 后端 `mvn -o test` → **167 / 0 / 0 / 0，BUILD SUCCESS**（独立于运行中进程）
- 回归 → **0 失败 / 1 跳过**
- `EXTRACTION_ENABLED=true` Phase 3 → **25 / 0 / 0**

### 压缩（第 37、38 次）+ **一处闸门认知更正**

**本批发现：行数不是唯一闸门。** findings 一度停在 **998 行 / 102431 字节**，
按「`lines > 1000` 才越线」的旧认知判为合规——**但字节上限 102400 已被突破、退出码 2**。
**此前多轮把「1000/1000」当安全线，这个认知是不完整的：行数与字节是两个独立阈值。**
逐字节微调时又一度**行数不降反升**（把长行拆成多行），说明该场景下**只能整体迁出、不能靠改写挤字节**。

终验：条目 58、应迁出（P2-46 23 条 + P2-14 25 条）与应保留 892 条**双向丢失 0**、
**八个归档的指针行数 = 块数全部成立**、26 个归档链接可解析、重复指针已清零。
1015 → **980 行 / 102342 字节**。
