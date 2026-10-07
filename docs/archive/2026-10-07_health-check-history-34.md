# 健康检查历史 34 — 第 340、341 轮（第 353 轮迁出）

> **归档规则**：承 `-19` ~ `-33` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
> 一次迁两轮承第 342 轮体例。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数取自**最终内容**的计算值并与 `doc-growth-check.sh` 实测对照，
> 不手写——本脚本首版用占位指针先算一遍，差了 1 行 / 1 字节（占位比最终多一个空行）。

> 本批为写入第 353 轮报告后 `docs/drafts/health-check-task.md` 达 **1607 行 /
> 103427 字节**（**行数越线** 1500，字节未越 150000）而迁出 **2 轮**：第 340、341 轮。
>
> 第 312~339 轮在工作文件里已是一行指针（更早各轮在 `-19` ~ `-32` 中），
> **第 340、341 轮是最早仍保留完整报告的两轮**。只迁第 340 轮**不够**：
> 1618 → 1563 行，仍高于 1500，故一次迁两轮。
> 轮次边界按**轮号**匹配 `## 第 N 轮 — ` 定位，**不按行号相邻**——第 342 轮教训。
>
> 迁出后实测 **1342 行 / 85455 字节**。

## 块 1 / 2：第 340 轮全文（第 353 轮逐字迁出，原 136 行）

## 第 340 轮 — 2026-10-07T10:00:00+08:00

代码方向：**Demo**（**P2-96 已修**）；文档方向：**SDK README**（doc round 240
—— **P2-97 已修**，另在归档索引上抓到 **P2-98 / P2-99** 两条并已修）

### 健康预检

37777 在监听（pid 4029，保留），health 200（db / diskSpace / messageQueue / ping 四项 UP），
HEAD = `1234c9f`，工作区干净。指纹 `5d960e9b…` / 2218 与基线一致。

### 代码方向（Demo）：`DemoErrors` 自陈的 catch 块数差 1

`examples/cortex-mem-demo/.../DemoErrors.java:20` 的类 Javadoc 自陈
「十二个控制器合计 **forty** 个 `catch (Exception e)` 块」。实测 **forty-one**：

| 方法 | 结果 |
|---|---|
| 裸 `grep -c "catch (Exception e)"` 逐文件相加 | **42**（含 Javadoc 自己那 1 处引用） |
| 剥掉块注释与行注释后计 | **41** |
| 放宽为 `catch (Exception <任意变量名>)` | 同样 **41** |
| 按 `git show <rev>:` 在 5 个提交上复算 | **一律 41** —— 源码里**从未**是 40 |

**另外三个计数经核实全对**（这一步必要，因为「修一个不修其余」正是本条的要害）：
**12 个控制器** ✓、**3 个块到达助手** ✓（`ObservationsController` 2 + `FeedbackController` 1）、
**10 个平铺 500** ✓（12 − 2，与「3 个块」自洽）。已改为 `forty-one`；
`mvn -o clean test` → **40 tests / 0 failures / BUILD SUCCESS**。

**与第 328 轮的对照**：第 328 轮记的是「裸 grep 得 42，剥注释后得 **40**」——前半句对，
**后半句的减法错了**（42 − 1 = 41）。即一次「已核实」的结论里藏着一个未复核的算术步骤，
而后续多轮都直接引用了它。本轮把它改正。

### 文档方向（SDK README，doc round 240）：JS SDK README

审 `js-sdk/cortex-mem-js/README.md`（370 行，引用仅 2 次，是四份 README 中最少的一）。

**P2-97（已修）**：方法表里 **3 行**写 `getQualityDistribution(project)` /
`triggerExtraction(project)` / `getStats(project?)`，而源码 `client.ts:397/430/517`
三处签名都是 `projectPath`。**三重反证**：同一张表另外 3 行（`triggerRefinement`、
`getLatestExtraction`、`getExtractionHistory`）用的就是 `projectPath`；**同一份文档的
必填参数表**也是 `projectPath`；**Python SDK README 对同样三个方法用的是 `project_path`**。

**而这是上次修复的漏网之鱼**：`docs/archive/2026-09-30_doc-review-history.md:150` 记载
2026-04-01 那一轮**已经修过同一类错**（`getLatestExtraction` / `getExtractionHistory`
的 `project`→`projectPath`）——**只改了那 2 行**，同表另外 3 行原样留下。
**同一次审查只改一半，比不改更糟**：它让下一位读者以为这一列已经核过。

排除「表里写的是后端 query 参数」这个解释：`triggerExtraction` 的 query 参数恰恰
**就叫** `projectPath`（`client.ts:433-435`）。已改 6 行（双语各 3），
`git diff` = **6 增 6 删**、无其他改动；阴性 grep EXIT=**1**、阳性 grep EXIT=**0**。

**核实为真、不立条目的项**（本轮大头）：

- **259 个测试 = 246 + 5 + 8**：`vitest run` 实跑，三个文件的**逐文件**计数与 README 的
  三项分解逐字对上（`client.test.ts` 246 / `truncated-body.test.ts` 5 /
  `parse-int-param.test.ts` 8）。
- **25 个 API 方法**：`client.ts` 公开方法 **27** 个，扣掉 `close()` 与 `toString()`
  恰为 25；API Reference 表列的 27 行与之**逐行对应**。
- **25 条端点路径**：`client.ts` 里 21 处 URL 字面量 + 4 处模板串，与表格**逐条吻合**，
  HTTP 方法一并核过（`PATCH /api/session/{id}/user`、`PATCH`/`DELETE /api/memory/observations/{id}`）。
- **必填参数表 18 行** 与 `validateRequired` 的 **28 处调用**逐行对应，含
  `ids` 三重检查（非空 / 至多 100 / 无空元素）与 `limit` 不得为负。
- **「seven clearable fields」是对的**：后端 `@Operation` 自己枚举的可清空字段恰为
  **7** 项（`content` / `narrative` 是同一列的两个别名）；`ObservationUpdate` 侧是 8 个字段。
- **「四家中只有 JS 能把字符串字段清空为 NULL」是对的**，另三家逐一验过：
  Java `@JsonInclude(NON_NULL)`（`ObservationUpdate.java:19`）、
  Go `*string` + `omitempty`（nil 才省略，**结构上无法表达 null**）、
  Python `to_wire()` 只发非 `None` 字段。
- **10 MiB 上限**：`10 * 1024 * 1024`、两道检查（`Content-Length` 读前 / 读后兜底）、
  `utf8ByteLength` 遍历而非 `TextEncoder` —— 全部与源码一致。
- **Quick Start 那句注释**：`POST /api/session/start` 活体返回**恰好 5 个键**
  （`session_db_id` / `context` / `prompt_number` / `session_id` / `updateFiles`），
  与「三个有类型的字段 + 两个只在 wire 上的」完全对应。
- **`refinedFromIds` 是 TEXT 列**：实体里是 `String` 字段、无 JSON 类型注解。

**一处看着像缺陷、实则不是**：README 说活体实测 `GET /api/observations` 无 `project`
时「返回 16 个不同项目」。本轮复测得 **14**。但那句话自带限定「**against a populated
backend**」——它是对一个**持续增长的开发库**的一次快照，**不是系统行为的不变量**；
改成 14 明天同样会过期。而它要证明的**机制**已完整复现：无 `project` 时返回 100 条 /
14 个不同项目，字面量 `?project=` 时返回 **0** 条。**不立条目。**

### 归档索引维护（本轮顺手抓到两条）

为登记本轮新建的归档去核对 `docs/archive/README.md`，结果：

- **P2-98（已修）**：磁盘 **95** 份归档、登记 **88** 行 → **6 份既有归档从未登记**
  （`-evidence-24` / `-43` / `-44`、`-history-22` / `-23`、以及 evolver 分析稿）。
  成因是同一个：**每轮创建归档并提交，却没在同一轮追加登记表行**。
  第 318 轮已经发现并修过这一条（`history-18`），当时只修了那一处、**机制没修**。
  已补齐 6 行并登记本轮的 `-45` / `-46`；复跑差集 **missing 0**、磁盘 96 == 登记 96。
- **P2-99（已修）**：登记表第 86 行把**裸 `|` 写进了代码 span**
  （`git diff -U0 | grep Status`）。GFM 表格里 `|` **即使在反引号内也必须转义**，
  否则照样切单元格——该行按 `|` 切分得 **7 段**，而表头只有 **4 列**。
  这是 **HEAD 里就有**的（第 267 轮带入）。改为转义写法后全表 **97 行零畸形**。

### 压缩与归档

两批：**`-45`**（`P2-78`、`P2-96`）与 **`-46`**（`P1-2`、`P2-97`），
均为**已完全结案、不再有待决动作**的整条迁出。两次终验都用**删除前快照双向比对**：
逐块正文经**多重集（Counter）**核到**丢失 0 / 多余 0 / 交叠 0**，
边界断言覆盖 `- **`、`### ` 与 `## ` 三种行首。
1537 → 1483 → **1490 行 / 145791 字节**，`doc-growth-check.sh` 退出码 **0**。

**`-46` 的归档头一度写错字节数**（手写 `150516`，实测 `148569`）。
因「归档不可改」，**从快照还原工作文件、删除重建**，并把头部改成**从实测值计算**，
另加一条 `PRE_LINES == 1524` 漂移断言。

### 变更检测与完整验收

本轮改了代码（`DemoErrors.java`，纯注释）→ 指纹由 `5d960e9b…` / 2218 变为
**`864aff73…` / 2218**（记录数未变）。**新鲜度豁免路径核实**：`backend/pom.xml` 对
`cortex-mem-demo` **零引用**，且 `git log 6e5890d..HEAD -- backend/` **为空**
→ 37777 上跑的 jar 必然含最新后端代码，被改的 demo 不可能在其中。

- `bash scripts/regression-test.sh --skip-build` → **45 通过 / 0 失败 / 1 跳过 / 46 总计**（exit 0）
- `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` → **25 通过 / 0 失败 / 0 跳过**（exit 0）

全通过，**基线已推进**至 `864aff73…` / `1234c9f`。
修正完成后再测一次指纹：**仍是 `864aff73…` / 2218** —— README 属纯 `.md`，被正确排除，
闸门继续有效，无需重跑。37777 保留运行。

### 本轮踩到的仪器错误（四处，全部先识别再采信）

①找「哪几个块到达助手」的正则**漏了 `clientStatus`**，得 2 个，差点把 Javadoc 的
「three」当成错的——**是我的正则少了一个方法名，不是文档写错了**。
②`-46` 归档头的字节数是**手写**的，与脚本自己打印的实测值对不上；
靠两者比对发现，否则就是一个永久写错的归档。
③校验归档表格行的检查器**连错三次**：第一版按 `len(split('|')) != 7` 判 4 列表格，
把 80 多行正常行全标成异常、**反而漏掉注入的坏行**；第二版数裸 `|`，分不出转义写法；
第三版数未转义管道，但注入的探针行被行首前缀过滤直接跳过；
第四版的断言又把注入行的行号算错一格。**三版都由阳性对照暴露**——
若不做阳性对照，第一版会让我把「97 行里 96 行正常」当成「96 行畸形」。
④压缩脚本的头部 f-string 少一个右括号，`SyntaxError` **发生在写盘之前，文件未动**。

### 下一轮

代码方向：**Backend**；文档方向：**API 文档**（doc round 241）。

## 块 2 / 2：第 341 轮全文（第 353 轮逐字迁出，原 133 行）

## 第 341 轮 — 2026-10-07T11:45:00+08:00

代码方向：**Backend**（**P2-103 已修**，另有一个零覆盖文件审完零缺陷）；
文档方向：**API 文档**（doc round 241 —— **P2-101 / P2-102 已修**）

### 健康预检

HEAD = `b0d743c`，工作区干净，`main…origin/main` 无分叉，37777 在监听（pid 4029，保留），
health 200，指纹 `864aff73…` / 2218 与基线一致，37778 / 37790 / 37791 空闲。

### 本轮中途出了一次真事故，先说清楚

审到一半时 `curl` 返回 **exit 7（连接失败）**——**37777 已经不在监听**。
本轮开始时它还在，**而我没有执行过任何 kill / mvn / docker**。排查结论：

- 无 OOM 记录、无 `hs_err_pid*.log`、无 heap dump；
- 首次拉起 jar 直接失败并给出线索：
  `FATAL: the database system is starting up`；
- 查监听者发现 **5432 是 OrbStack（pid 1167）在听**，而 psql 报
  `the database system is not yet accepting connections / Consistent recovery state has not been yet reached`。

**因果链**：PostgreSQL（跑在 OrbStack 容器里）崩溃并进入 crash recovery 重放 WAL →
后端失去连接后退出。机器上同时跑着大量 Cursor 扩展宿主与 docker 进程，资源压力大，但
**崩溃的直接诱因我没有证据，不臆测**。

**恢复与核验**：等 PG 就绪后拉起后端，**逐表核对行数与本轮早先的实测完全一致**——
`mem_observations` 38967、`mem_summaries` 6590、`mem_user_prompts` 2910，
**crash recovery 一行未丢**。

### 代码方向（Backend）

**回归审计（近期修复是否还在 HEAD 上）**——四条全部完好：

- **P2-78**：`WebConfig` 的 `allowedMethods` 仍含 `PATCH`，注释仍写明为什么；
- **P2-68**：`updateObservation` 的 Javadoc 与 `@Operation` 一致（都是「null 清空」）；
- **P2-25**：活体复测 `maxChars=0 → 100`、省略 → 4000、`50 → 100`，
  **该条记录的失实仍然成立**；
- `SummaryGenerationService:159` 的 `retrieveExperiences(..., 4, ...)` 一度看着像 bug，
  查下来 `ICLPromptRequest` **根本没有 `count` 字段**——字面量 4 是必然。

**零覆盖扫描**（后端 85 个 java 文件，语料 = findings + **98 份归档**）：
按类名扫，**只剩 1 个从未出现在任何记录里的文件**——`entity/SummaryEntity.java`。
扫描器经 **4 组阳性对照**（抽掉一个已覆盖类名必须落进零覆盖、还原必须复原、
不存在的名字必须读作零、两份语料只许差这一个）。

**`SummaryEntity` 审计：零缺陷。** 15 个字段对 **V1 建表 + V13 会话 ID 统一 + V18 平台源**
逐列吻合，再对**活体 `\d mem_summaries`** 交叉验证（15 列、类型、可空性、默认值、5 个索引、
外键全部一致）。

**追查 `created_at` 的 NULL，本想立条目结果不是**：`created_at` 有 `DEFAULT NOW()`，
而 JPA 捕获路径只设 `createdAtEpoch`，故该列**对每一条捕获写入都是 NULL**。
实测：观测 20590/38967、摘要 6589/6590、用户提示 **2910/2910 全空**。
但——**`ARCHITECTURE.md:624-640` 双语已完整记载**（机制、代码路径、两个实测比例、
`DESC` 下 NULL 排最后故结果静默取反、派生方法必须命名 `…OrderByCreatedAtEpochDesc`），
`SummaryRepository:77` / `ObservationRepository:236` / `ExpRagService` 也都有注释。
**按第 316 轮纪律不立新条目**。

**但顺着这条线查出一个真的（P2-103，已修）**：`ExpRagService:237` 的注释把 NULL 的成因
写成 `(pre-migration data)`。**实测否定了那个括号**——观测与提示的**最新**一行都在 NULL 一侧
（2026-10-07 01:53 / 01:52），非空一侧止于 2026-05-07；摘要表全表唯一那条非空还是
`ImportService` 写的。**当前路径就在持续制造 NULL，不存在「迁移完就不再有」**。
注释已改为如实写出机制并指向 `ARCHITECTURE.md`；`git diff -U0` 逐行核验**可执行行 0**。

### 文档方向（API 文档，doc round 241）

**P2-101（已修）**：架构文档让读者去 `backend-review-findings.md` 找 **P1-3**——
而那条目 2026-10-03 就被压缩迁入了 `2026-10-03_backend-review-history-resolved-2.md`，
findings 里**连一行指针都没留**（现有 90 条，P1 段只有 P1-1 与 P1-2）。
**不是只修看得到的两处**：全库非归档 `.md` 共 **422 处**编号提及、**49 处**指向未定义编号，
逐条分类后 **46 处不成立**（历史叙述 / `P2-91` 自身主题 / changelog 里的历史记录），
只有这 2 处正文指针要动。修法：补上逐字迁入的归档链接并说明 findings 已不再保留它。
**双向注入验证为真**：回退成原文后检查器报 `(:640, 'P1-3')`，修复后报 `none`。

**P2-102（已修）**：2026-10-03 那次把 `created_at` 改成 `string | null` 的修正，
**只改了同表 16 个同型字段里的 1 个**。在全部 38,967 条观测上实测：

| NULL 率 | 字段 |
|---|---|
| **100%** | `user_comment`、`last_accessed_at` |
| 92% | `feedback_updated_at`、`feedback_type`、`quality_score` |
| 79–81% | `refined_at` 79%、`subtitle` 81% |
| 46–48% | `content_hash` 48%、`facts` / `files_read` / `files_modified` 47%、`extractedData` 46% |
| 6–35% | `source` 35%、`prompt_number` 15%、`narrative` 6% |

这 **15 个**全部仍声明为非空 `string` / `int` / `float` / `object`，而同一张表里
**6 个字段早已如实标了 `| null`**。**键从不缺失**：活体 `GET /api/observations` 每行恒为
**34 个键**，稀疏字段到达时是**显式 `null`**（三条连续样本逐键核对）。
**前几轮为什么没逮到**：比的是**字段是否存在**（第 334 轮「字段↔活体对拍」），
不是**类型声明的可空性**——字段在、值是 `null`，逐键对拍看起来「全中」。
双语各 15 行已改，并在双语 changelog 各加一条 2026-10-07 记录。
**未改的 9 个是核实过的**（NULL 率 0.0–0.09%），`POST /api/memory/observations` 的
**请求体**表刻意不动（它自带 `Required` 列）。

### 压缩与归档

三批：`-48`（P2-101 / P2-102）、`-49`（P2-103）与 health-check 的 `history-24`（第 328 轮报告），三次终验都用**删除前快照双向比对**，
逐块正文经**多重集（Counter）**核到**丢失 0 / 多余 0 / 交叠 0**。
1551 → 1492 → 1496 行 / 148093 字节；health-check 1531 → **1473 行 / 94527 字节**，`doc-growth-check.sh` 全 OK。
归档索引同步补到 **99 份全部登记、零畸形行**。

**一个需要作者决策的结构性问题**：findings 现在**只剩 4 行余量**（1496 / 1500）。
本循环**连续两轮共建了 6 份归档**，而每轮仍在新增 2–3 条 findings——
**压缩频率本身已经成了问题**，建议调整上限或改变归档粒度，见待决清单。

### 变更检测与完整验收

本轮改了后端代码（`ExpRagService.java`，纯注释）→ 指纹 `864aff73…` → **`aa9c977e…`**
（记录数仍 2218）。**新鲜度按实跑核实**：改的是后端自身，**豁免路径不适用**，
故 `mvn -o clean package -DskipTests` EXIT=0 重建 jar（11:40），
**停掉本轮自己启动的 37777 进程**并以新 jar 重启，确认 `ExpRagService.class` 与 jar 均为 11:40、
在跑实例确含本轮改动后才跑门控。

- `bash scripts/regression-test.sh --skip-build` → **45 通过 / 0 失败 / 1 跳过 / 46 总计**（exit 0）
- `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` → **25 通过 / 0 失败 / 0 跳过**（exit 0）

全通过，**基线已推进**至 `aa9c977e…` / `b0d743c`。37777 保留运行。

### 本轮踩到的仪器错误（七处，全部先识别再采信）

①改 `\| null` 时**把它插成了独立单元格**，行变 4 列而表头 3 列——**正是 P2-99 那类缺陷**；
由「未转义管道数」断言当场拦下，回退后按表头列数重做并加了结构守卫。
②紧接着写 changelog 行时，正文里的 `... | null` **没转义**，多出一个分隔符——同一个断言再次拦下。
③校验 changelog 行宽时**把 archive/README 的 5 管道约定套到了 3 列的 changelog 上**，
于是把全部 60 行报成畸形；真实读数是 `{4: 60}`——**一致性本身就是证据**。
④悬空指针检查器的第三条阳性对照用了 `P9-99`，**在正则范围之外**——是我的对照写错，不是检测器错。
⑤验证 P2-101 修复的那条对照**把探针写到了 `/tmp` 却去审原文件**。
⑥零覆盖扫描的第二条对照断言写成了 `.replace(X, X)`——**空操作**，断言必然失败。
⑦`/tmp` 里的压缩脚本与快照**在本轮中途被清空**（与 PG/OrbStack 重启同期），
压缩脚本只好重写一遍——**这次把「脚本可复现」本身当成了隐性依赖**。

### 下一轮

代码方向：**Java SDK**；文档方向：**SDK README**（doc round 242）。
