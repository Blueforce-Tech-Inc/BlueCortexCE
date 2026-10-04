# CortexCE 巡检历史归档 — 第 241~250 轮

> **归档日期**: 2026-10-04（第 251 轮）
> **覆盖轮次**: 第 241 ~ 250 轮（10 条）
> **时间范围**: 2026-10-04 04:45 ~ 09:06
> **触发原因**: 写入第 251 轮报告后活动文件达 1074 行，越过 `MAX_LINES=1000` 阈值（`doc-growth-check.sh` 返回 `COMPACTION_REQUIRED`、退出码 2）。
> **提取方式**: 按**轮号**匹配 `## 第 N 轮 — ` 提取（非按物理位置）——第 157 轮曾被误插入第 156 轮正文，物理顺序因此不可信；每块均断言其自报轮号与标题一致（本次 10/10 通过）。
> **内容**: 下方逐字保留，未作任何改写。
> **本文件创建后不得修改**。

---

## 第 241 轮 — 2026-10-04 04:45 — Demo + SDK README

### 轻量健康预检

- 后端 37777 UP；37778–37781 全空闲
- 指纹 `1495f67b…`、基线 `0091ab0` / `1495f67b…` 一致；工作区干净
- 测试基线：Java **192** / Go **359** / Python **435** / JS **247**

### 代码方向（Demo）— P2-37 记录不修

补上第 235 轮**从未比对过的那一维：方法**。四家 demo 端点**名字**已确认 23 中 21 同名，
但方法层没比过。`/chat` 暴露出**方法、输入位置、响应结构、行为语义四路全不同**：

| Demo | 方法 | 入参 | 响应 | 实质 |
|---|---|---|---|---|
| Go / Python / JS | POST | JSON body | `{response, project, timestamp, memoryContext?, experienceCount?}` | 回显，**不记录** |
| **Java** | **GET** | **查询参数** | `{response, project, conversation_id}`，**无 timestamp / memoryContext** | **真实调用 LLM** + 自动捕获 |

**活体证据**（本会话首次启动 Java demo，37778，PID 43601，本轮结束后已停）：
照抄另三家的 `POST`+JSON → **`{"status":405,"error":"Method Not Allowed","path":"/chat"}`**；
用 Java 自己的 GET 写法**确实进入 handler**（500 是 LLM 密钥失效这一**已知环境问题**，
按「没验证的不写」不作为缺陷计）。

**分歧是被写下后搁置的**：`ChatController` 自己的 Javadoc 明写「Go/Python/JS 都是
`POST /chat`」，**紧接着改用 `@GetMapping`**。

**记录不修**：加 `POST` 映射属公开端点契约变更；「该是真实 LLM 调用还是对齐薄回显」
属 demo 定位的产品决策。**Demo 代码一字未改。**

### 文档方向（SDK README）— 核实无误、未改，带出 P2-38

四家 README 都详述了 `limit` 负数（P2-30），却对 **`count` / `maxChars` 非正数**只字未提
——而那正是第 238/239/240 轮连续三轮命中的同一字段。Java README 的 maxChars 节只给正例，
**而第 225 轮恰恰改过这个行为**并写进了 SDK 的 Javadoc。**四份 README 描述自身行为均准确，未改。**

**计划外发现 → P2-38（记录不修，留待 Java SDK 方向）**：
`Builder.count()` 显式拒绝非正数，**公开构造器完全不校验**，`toWireFormat()` 无条件下发。
**真实 JUnit 探针并排实测**：`builder count=0 → REJECTED`；构造器 `0 → count:0`、
`-1 → count:-1`、`null → count:4`。**一处自我修正**：最初假设「Java 是连续第三家同型缺陷」，
读代码发现 builder **有**校验，遂收窄为「两条路径不一致」，**探针实测后才落笔**。

### 压缩（第九、第十次，且首次迁移 `复核记录` 以外的段）

P2-37+P2-38 加入后达 1037 行，而前三批已用尽**全部** `复核记录`、仍差 29 行。
遂**首次迁移 `Reproduction` 实测记录**：依据是**实测记录属可复现证据、⏸ 规则保护的是决策推理**，
P2-25/26/30/31 的 Reproduction（89 行）逐字移入
`2026-10-04_backend-review-reproduction-4.md`，Scope/Problem/Evidence/Status 全部保留，
迁出前以 `git show HEAD` 逐字校验。**回落 944 行——四次压缩以来第一次真正腾出余量**。
已标注这是**扩展规则而非套用规则**，提请项目决策。

### 探针清理

JUnit 与 vitest 探针均用**绝对路径**移除并逐一 `git status` 确认
（一次 `rm` 用了未展开的 shell 变量、**实际未执行**，已当场识别并重做）。

### 变更检测与验收

本轮**只改 `.md`、未动任何产品代码** → 指纹 `1495f67b…` **未变且与基线一致**，
按门控**不跑完整验收、不推进基线**。

### 轮换推进

代码审查：Demo 完成，下一方向 Backend。
文档审查：SDK README 完成（一百四十二轮），下一方向 设计文档（一百四十三轮）。

## 第 242 轮 — 2026-10-04 05:20 — Backend + 设计文档

### 轻量健康预检

- 后端 37777 UP；37778–37781 全空闲（Java demo 已于上轮结束时停掉）
- 指纹 `1495f67b…`、基线 `0091ab0` / `1495f67b…` 一致；工作区干净
- 测试基线：Java **192** / Go **359** / Python **435** / JS **247**

### 代码方向（Backend）— P2-39 记录不修

审 `ImportController`（13 个 controller 中此前未被作为审查对象的一个）。

**外层 `@Transactional` 与逐行 catch 相撞，一行坏数据毁掉整批**：
`bulkImport` 带 `@Transactional` 且逐行 `try/catch` 收集错误，而
`ImportService.importSession` **同样带 `@Transactional`（REQUIRED 并入同一事务）**。
数据库异常穿出内层方法 → Spring 把**共享事务标记 rollback-only** → 控制器 catch
**吞掉**它并继续统计 → 提交时抛 **`UnexpectedRollbackException`** → **HTTP 500、
逐行统计全部丢失、整批合法行回滚**。

**活体对照（同一份三行输入打两个端点）**：

| 端点 | 外层事务 | 结果 |
|---|---|---|
| `/api/import/sessions` | 无 | **200**，`imported: 2, errors: 1`，错误精确到 varchar(255)；**两行成功落库** |
| `/api/import` | 有 | **500**，只有 `Internal Server Error`；**两行一条未落库** |

直查库确认 `r242-a-*` 在、`r242-b-*` 不在；日志确认 `UnexpectedRollbackException` 与
`Transaction silently rolled back`；**探针数据已清理（删 3 行、残留 0）**。

**确定性触发**：`content_session_id varchar(255)`、`status varchar(50)` 均由用户提供且
**无长度校验**。**该坑已被踩过一次、只修了一个字段**：`importSession` 第 233-236 行的注释
记录过 `project_path` 缺失导致「提交时才失败、只看到 `Could not commit JPA transaction`」
并为此补了前置校验。

**记录不修**：去掉 `@Transactional` → 放弃 `@Operation` 明写的 atomic 语义；
保留原子性但不吞 rollback-only → 仍是 500，只是从「假 500」变「真 500」。
**需先决定该端点承诺「全有或全无」还是「逐行部分成功」，属产品契约决策。**
只补 varchar 校验**不足以解决**且会造成「已解决」错觉。**后端代码一字未改。**

### 文档方向（设计文档）— DOC-1 已修

`phase-3-design/index.md:84`「**体量合规**：30 个文件，最大 42KB，均 ≤50KB ✅」是
该目录**唯一的合规举证**。实测最大文件 `25.md` = **44,374 字节 = 43.3 KiB / 44.4 KB**，
**两种口径都对不上 42KB**。合规结论仍成立，但**低报最大值会掩盖文件逼近阈值**。
已改为「30 个文件（不含本索引），最大 44.4KB（`25.md`，44,374 字节），均 ≤50KB ✅」。

**两处刻意不报**：①「30 个文件」看似与实测 31 个 `.md` 差一，但按「不含索引的子文档」
读**正确**；②`index.md:3` 称「原根文件（271KB）已拆分」而该文件现仅 564 字节的桩——
带「原」字且明说「已拆分」，属**可辩护的历史陈述**。另核实该根文件确实存在，引用非悬空。
**index 覆盖度核实无误**：引用与磁盘文件一一对应。

### 探针自身出错（1 次，先识别再采信）

第一版探针用 camelCase 字段，三行全返回 `"contentSessionId is required"`，
**无法区分「校验生效」与「字段名写错」**；改用 snake_case 后才拿到有效对照。
**附带观察（不单独立项）**：camelCase 触发的错误信息报的是 **Java 字段名**而非用户
实际发来的 wire 字段名；且 `API.md` 对三个 import 端点**无任何字段清单或示例**。

### 变更检测与验收

本轮**只改 `.md`、未动产品代码** → 指纹 `1495f67b…` **未变且与基线一致**，
按门控**不跑完整验收、不推进基线**。

### 轮换推进

代码审查：Backend 完成，下一方向 Java SDK（**将实施 P2-38 修法**）。
文档审查：设计文档完成（一百四十三轮），下一方向 架构文档（一百四十四轮）。

## 第 243 轮 — 2026-10-04 05:30 — Java SDK + 架构文档

### 轻量健康预检

- 后端 37777 UP；37778–37781 全空闲
- 指纹 `1495f67b…`、基线 `0091ab0` / `1495f67b…` 一致；工作区干净
- 测试基线：Java **192** / Go **359** / Python **435** / JS **247**

### 代码方向（Java SDK）— P2-38 已修（实施上一轮写明的修法）

`ExperienceRequest` 的构造器/builder **校验分裂**：改为**紧凑构造器**校验，
它同时覆盖规范构造器、两个便捷构造器与 builder，**任何构造路径都绕不过**；
`Builder.count()` 复用同一处 `requirePositiveCount()`，避免错误消息重复漂移。
`null` 仍合法（`toWireFormat()` 仍映射为后端默认 4），**合法输入 wire 行为未变**。

**按断言清扫才看清根因**：`ICLPromptRequest` **本就无此问题**——它的守卫在**序列化层**
（只在 `maxChars > 0` 时下发），任何构造路径都绕不过；而 `ExperienceRequest` 走的是
**下发**（`count != null ? count : 4`）而非丢弃，**所以只能依赖构造期校验**。

**同一条缺陷的四个实例至此全部收敛**：Python（239 轮修）、JS（240 轮修）、
Go（238 轮记 P2-36）、Java（本轮修）。

**4 条新测试**（三条构造路径拒绝非正数 / **正数 1 仍上线即防过度修复** / `null` 仍映射为 4），
client **139 → 143**、总测试数 **192 → 196**，两份 Java README 已同步。
**双向注入验证为真**：移除紧凑构造器后**恰好 1 条失败**，三条对照理应不失败。

### 文档方向（架构文档）— 核实无误、未改

把「哪几个迁移动过这张表」做成可机械核验的断言：解析全部 **16** 个迁移文件、
只认真正的 `CREATE/ALTER/DROP TABLE` 与 `CREATE INDEX ... ON`：

| 表 | 文档标注 | 实测 | 结果 |
|---|---|---|---|
| `mem_sessions` | V1 V4 V11 V12 V13 V15 V18 | 同 | **逐项一致** |
| `mem_observations` | V1 V2 V7 V8 V11 V12 V13 V14 V16 V17 V18 | 同 | **逐项一致** |

**探针自身错一次并先识别再采信**：第一版用「文件是否提及该表」判断，得出
「**V8 也动了 mem_sessions、文档漏列**」；复查发现 **V8 只是让 `mem_observations`
的外键*引用* `mem_sessions`、并未 ALTER 该表**——**探针把「提及」当成了「修改」，
据此撤回了该判断**。

**技术栈版本核实为真**：Spring Boot 3.3.13（与 `pom.xml` 一致）、Java 21、
pgvector 0.8（活体 0.8.1，相容）。
**迁移计数刻意不报**：`ls *.sql` 为 16 而项目处处说「V1–V18」——**两者都成立**，
V9/V10 确不存在、「V1–V18」是**版本区间**而非文件数，且 `ARCHITECTURE.md`
**并未声称文件数量**。

### 变更检测与验收

本轮**改了 Java 源码** → 指纹 `1495f67b…` → `7f81c2a8…`，按门控跑完整验收：
回归 **45/0/1** + EXTRACTION **25/0/0** 全通过，基线推进至 `52a341c` / `7f81c2a8…`。

### 轮换推进

代码审查：Java SDK 完成，下一方向 Go SDK。
文档审查：架构文档完成（一百四十四轮），下一方向 运维/用户指南（一百四十五轮）。

## 第 244 轮 — 2026-10-04 05:55 — Go SDK + 运维/用户指南

### 轻量健康预检

- 后端 37777 UP；37778–37781 全空闲
- 指纹 `7f81c2a8…`、基线 `52a341c` / `7f81c2a8…` 一致；工作区干净
- 测试基线：Java **196** / Go **359** / Python **435** / JS **247**

### 代码方向（Go SDK）— P2-40 记录不修

**四家一致的能力缺口**：`GET /api/summaries` 与 `GET /api/prompts` 活体可用、
`API.md` 有完整记载（`/api/prompts` 8 处）、WebUI 在用，而**四家 SDK 无一有方法、
四家 demo 也都没暴露对应端点**（三家 demo 里唯一的 "summar" 字样是统计字段
`totalSummaries`，不是端点）。

**写的一侧却齐备**：`POST /api/ingest/session-end`（会话结束即生成摘要）与
`POST /api/ingest/user-prompt` **四家全部有方法**（Go 3/3、Python 4/3、JS 6/6、Java 2/2）。
**即：SDK 用户可以产生摘要与提示词，却永远读不回来。**

**记录不修**：补方法属**新增公开 API**；且四家一致缺失 → 要么有意划定、要么共同疏漏，
两种解读都需项目拍板。**若将来实施**需与既有分页约定对齐（共用
`Math.min(Math.max(1, limit), MAX_PAGE_SIZE)` 钳制；`hasMore` 驼峰而条目字段 snake_case）。

**探针自身出错一次并先识别再采信**：初版探针用 `dto.SummariesResponse` 解析活体响应，
**编译失败——该类型根本不存在**，而这恰恰印证了「没有 summaries 方法」；
改以统计四家非测试代码对 summar 相关标识符的引用数，四家**均为 0**。

**顺带核实无误**：Go DTO 的响应标签确实匹配后端混用命名约定，未发现静默丢字段。
探针目录 `probe244` 已用绝对路径清理，`git status` 确认干净。

### 文档方向（运维/用户指南）— DOC-1 已修（双语）

**开发指南通篇只讲怎么构建后端，四个 SDK 一个都没有。**
`DEVELOPMENT.md` 从 Prerequisites 到 Debugging 全部后端向，
`cortex-mem-spring-integration/` **全文只出现 1 次**（目录树第 414 行）——
而四家 SDK 合计 **1,237 个测试**，照这份指南**无法构建或测试其中任何一个**。
**一个更具体的坑**：后端那节通篇用 `./mvnw`，而
**`cortex-mem-spring-integration/` 下根本没有 `mvnw`**（`backend/mvnw` 存在），
**照抄必然失败**。

两版各补一节「构建与测试 SDK」，命令**全部是本会话实际跑通过的**：
Java 三模块 `mvn test`（注明无 wrapper）、Go 的 `./test-all.sh`（359 即此口径）与
`go test ./...`（仅根 + dto）之别、Python 的 `PYTHONPATH="$PWD" pytest`、
JS 的 `npm test`/`lint`/`build`；并写明 **`npx jest` 在 JS SDK 跑不起来**
（项目用 **vitest**）——**这正是第 239 轮踩过的探针错误**，顺手固化进文档。

### 压缩（第十一次，第五批）

P2-40 加入后 findings 达 1031 行 → 把最旧的 **7 段** `Evidence`/`Reproduction`/`规模`
实测记录（P1-1、P2-8、P2-28、P2-29 两段、P2-32、P2-34）逐字移入第五批归档，
承第四批同规则、迁出前以 `git show HEAD` 逐字校验。**回落 894 行，本轮净减 137。**

### 变更检测与验收

本轮**只改 `.md`、未动任何产品代码** → 指纹 `7f81c2a8…` **未变且与基线一致**，
按门控**不跑完整验收、不推进基线**。

### 轮换推进

代码审查：Go SDK 完成，下一方向 Python SDK。
文档审查：运维/用户指南完成（一百四十五轮），下一方向 SDK README（一百四十六轮）。

## 第 245 轮 — 2026-10-04 06:06 — Python SDK + SDK README

### 健康预检

后端 37777 UP；37778–37781 全空闲。指纹 `7f81c2a82239d775bfc59b4dbb7c0b77ac936e3379841cd5cf72352daffff134`，
与基线（commit `52a341c` / 同指纹）**一致**。测试基线 Java **196** / Go **359** / Python **435** / JS **247**。

### 代码方向（Python SDK）— P2-41 新立（记录不修）

**后端每条观测都在返回、文档也把它写成了查询过滤器、而四家 SDK 全都既不暴露、也不接受。**

逐键对拍活体观测与 Python 的 dataclass 响应模型，发现四个字段**后端每条观测都在返回**：
`platform_source`（V18）、`content_hash`（V8，P2-29 去重键的组成部分）、`relevance_count`（V17）、`step_number`。
其中 `platformSource` 更被 `API.md` **作为查询过滤器**写进文档（4 处），WebUI 也已按它过滤——
**而四家 SDK 既不在响应模型里暴露这些字段、也不在搜索参数里接受 `platformSource`**（四家源码 grep 零命中）。

**后果具体**：同一项目下混用 Claude 与 Codex 两个客户端写入时，SDK 用户拿到的观测列表
**无从判断每条来自哪个平台**，只能自己去查库。

**记录不修**：补字段或补过滤器都是**新增公开 API 与新增配置键**，按纪律留待项目决策；
且**四家完全一致地缺失**，与上一轮的 P2-40 同理——要么是有意的范围划定、要么是共同疏漏，
两种解读都指向需要项目层面拍板。

**一个反向对照证明这不是「一律精简」而是「有选择」**：同样不在核心路径上的 `narrative` 字段**四家都有**。

**活体证据**：观测带 `platform_source='claude'`、`content_hash='1ed602d868bef3f8'`、`relevance_count=0`。

**探针自身错一次并先识别再采信**：第一版探针把 dataclass 当 dict 处理，**报告每个键都被丢弃**；
改用 `dataclasses.fields()` 重写后，15 个「丢弃」里**大部分只是改名**
（`content_session_id`→`session_id`、`project`→`project_path`、`hasMore`→`has_more`、
`springBoot`→`spring_boot`、`extractedData`→`extracted_data`），**真正缺失的才是那四个**。
若按首版结果下结论，会把一次纯改名误报成 15 处缺陷。

**本轮测试**：Python `PYTHONPATH="$PWD" pytest tests/ -q` → **435 passed**，与基线一致。

### 文档方向（SDK README）— 核实无误、未改

把「README 方法表 vs 客户端公开方法」做成可机械核验的断言：
Python SDK **26 对 26、双向零差异**（documented-but-missing: none；defined-but-undocumented: none）。

**探针两处不可靠已修正**：Python 方法签名**跨行**导致漏匹配
（`def build_icl_prompt(` 之后换行才是 `self,`），首版因此误报「文档写了但代码没有」；
Go 侧则是 README 提取的正则**过窄**。两处修正后结论才成立。

### 变更检测与验收

本轮**只改 `.md`、未动任何产品代码** → 指纹 `7f81c2a8…` **未变且与基线一致**，
按门控**不跑完整验收、不推进基线**。

### 轮换推进

代码审查：Python SDK 完成，下一方向 JS/TS SDK。
文档审查：SDK README 完成（一百四十六轮），下一方向 设计文档（一百四十七轮）。

## 第 246 轮 — 2026-10-04 07:01 — JS/TS SDK + 设计文档

### 健康预检

后端 37777 UP（HTTP 200）；37778–37781 全空闲。指纹 `7f81c2a8…`，与基线（`52a341c` / 同指纹）一致。
JS `npm test` **247** 通过，与基线一致。

### 代码方向（JS/TS SDK）— `ObservationUpdate` 类型已修

**类型比它自己实现的行为更窄：文档承诺的「null = 清空」在 TypeScript 里根本写不出来。**

`src/dto/observation.ts` 的八个字段声明为 `title?: string`、`facts?: string[]`、
`extractedData?: Record<string, unknown>`。而本包 `tsconfig.json` 是 `"strict": true`，
故 `{ title: null }` 是**编译错误**：`TS2322: Type 'null' is not assignable to type 'string | undefined'`。

三处证据说明**只有类型是错的**，行为与文档本来就对：

1. `client.ts:371-372` 的注释明写「PATCH semantics: null = clear field, undefined = skip, both are valid」，
   且 `hasField` 判定用的是 `v !== undefined`，null 本就被当作有效字段。
2. **活体后端**：对探针观测发 `PATCH {"title":null,"subtitle":null,"source":null,"facts":null,
   "concepts":null,"extractedData":null,"content":null}` → **200** `{"status":"updated"}`，
   回读七个字段**全部为 NULL**。能力在后端确实存在。
3. **SDK 自己的测试就是物证**：`client.test.ts` 里那两个用例名为
   *should accept null fields for PATCH clear semantics* 与 *should accept all-null fields (clear all)*，
   却都必须写 `null as unknown as string` 才能表达它声称在测的能力。

**四家对照**：Java 用 `@JsonInclude(NON_NULL)` 省略 null、Go 用 `omitempty`、Python 跳过 `None`
——**只有 JS 能在运行期真正把字符串字段清空为 NULL**，但在本轮之前**只能靠类型断言**做到。
本轮修好后，JS 成为四家中**唯一一个不绕过类型系统就能清空字符串字段**的 SDK。

**修法**：八个字段一律放宽为 `T | null`（`string | null` / `string[] | null` /
`Record<string, unknown> | null`），并补 Javadoc 写明 PATCH 三态——**省略 = 跳过**、
**传值 = 设置**、**传 `null` = 清空**。**纯加宽、向后兼容**：原先能编译的代码一律照旧，
wire 格式一字未改（`JSON.stringify` 本就没有 omitempty，null 一直在上 wire），不新增任何 API、字段或配置键。

**顺带更正 P2-27 能力表的错因**：该表把 JS 判为「能发 null」，理由是「原样透传给 `JSON.stringify`」。
**运行期确实透传，类型不允许**——那行判定在当时并不成立。Python `dto.py` 里「JS can send `null`」
那句同样只是运行期成立。已加注说明，并写明四家的**净能力**（能否真正清空字符串字段）此前未被反映。

**测试**：3 条新测试，**247 → 250**。三条分别钉住
「八个字段全部无断言可达」/「`null`（清空）与 `[]`（替换）结果不同且都上 wire」/
「`undefined` 仍被省略、不与 null 混淆」。另把两个旧用例里的 `as unknown as string` 去掉——
**断言是否多余，本身就是类型是否修对的检验**。

**双向注入验证为真（类型层）**：探针文件对八个字段逐一赋 `null`——
修复后 **0 error**；回退到修复前类型 → **恰好 8 个** `TS2322`，且**全部**是 null 赋值；
恢复后 **0 error**。对照组（非空字符串、`[]`、`{}`、`undefined`）在两种状态下**都不失败**，
证明既钉住了修复也没有过度修复。

**探针边界（重要，且是新立 P2-42 的由来）**：`tsconfig.json` 的 `exclude` 含 `"**/*.test.ts"`，
而 `npm run lint` 就是 `tsc --noEmit`——**测试文件根本不参与类型检查**。
实测：往 `client.test.ts` 注入 `const __bad: string = 42;` 后 `npm run lint` **依然退出 0、无输出**。
`vitest` 同样不做类型检查。故本轮**不能**用 `npm test` 证明类型修复，类型层验证只能靠直指
`src/dto/observation.ts` 的探针文件。已独立立 **P2-42**（记录不修：补 `tsconfig.test.json`
属构建配置变更，且会一次性暴露三个测试文件里既有的潜在类型错误）。

### 文档方向（设计文档）— 两处行号引用已修（第 147 轮）

**一次改动打坏两处引用，而其中一处正是它自己在同一个 commit 里刚「更正」过的。**

**第一遍机械清扫（结论：文档是自觉的，不改）**：提取 `phase-3-design/` 全部反引号标识符
得 95 个候选，其中 **12 个在后端源码里查不到**。逐个核实后发现**这 12 个全部已被文档就地标注**
——`extraction_state`（7/8/15/18/23.md 标「0 命中 / never built」）、
`extraction_audit`（18.md 标「never built」）、`idx_extraction_state_source`（19.md 标「从未存在于任何迁移」）、
`maxPriorChars`（24.6.md 明写「两个符号如今只存在于本设计集」）、
`scheduledExtraction`（0.1.md 明写「This class was never written」）、
`ExtractionTemplate`（2.md 注明实际是 `ExtractionConfig.TemplateConfig`）、
`__system__`（18.md 里是**备选方案 C**，非存在性声明）、`promptTemplate`（注明是 YAML `prompt` 的 Java 字段名）、
`lastExtractedAt` / `includeExtractions`（变更日志条目且注明「从未实现」）。
**即：不存在捏造，这批「查不到」正是设计集在如实标注自己的虚构部分。**

**第二遍清扫（结论：两处真错，已修）**：`File.java:NNN` 引用共 7 处，逐条核对行号实际内容，
**5 处精确命中**，2 处指错，且**两处同源**：

- **`17.md`**：称 `findDuplicateByContentHash()` 的三参签名「在 `ObservationRepository.java:421`」。
  这个 421 是**第 237 轮（`4fa6ed4`）刚把 `:411` 改正过来的值**，而那次更正在**同一个 commit 里**
  给该方法补了 4 行 `@param projectPath` Javadoc——**改文档与改被引用的源码同批进行，刚核对过的行号当场失效**。
  现状：421 落在 `@Query` 正文的最后一行（`AND project_path = :projectPath`），
  真正的签名在 **425**、`@Query` 在 **417**。顺带更正第 237 轮的另一处数字：它称改前「`@Query` 在 414」，
  据该 commit 的 hunk（`@@ -408,6 +408,10 @@`）实际在 **413**。
- **`7.md`**：称 `findNewObservations(...)`「在 `ObservationRepository.java:630`」。
  以 `ff45e88` 当时的文件核验，**630 在写下时是正确的**；同样被那 4 行 Javadoc 推到 **644**。
  而今 630 落在 **`findBySourceIn`** 内部——**那恰好是紧接下一条要拿来对比的另一个方法**，
  指错方法比指错行号更容易误导。

**修后复核**：7 处引用**逐一命中其声称的构造**（`dlq.setType("dlq_" + ...)` / `@Transactional` /
`findBySourceIn(` / `extractAppendOnly(...)` / `findDuplicateByContentHash(` / `findNewObservations(` /
`findDuplicateByContentHash(contentHash, windowStart, projectPath)`）。两处修订均注明来源轮次与失效原因，
并在文中固化教训：**行号引用不能与被引用文件的改动同批提交**。

### 探针数据清理

活体验证用的观测 `b24c2fb9-4852-4be0-974b-51f8ad296286`（`r246-nullclear-probe`）已删除，
`mem_sessions` 残留行删 1，`mem_observations` 删 0（已被 DELETE 端点移除）。临时探针目录已清理。

### 变更检测与验收

本轮**改了 JS SDK 源码** → 指纹 `7f81c2a8…` → **`2a30b422…`**，按门控跑完整验收：
回归 **45/0/1**、EXTRACTION **25/0/0**，**全通过**，基线推进至 `db84dc5` / `2a30b422…`。

### 轮换推进

代码审查：JS/TS SDK 完成，下一方向 Demo。
文档审查：设计文档完成（一百四十七轮），下一方向 架构文档（一百四十八轮）。

## 第 247 轮 — 2026-10-04 07:38 — Demo + 架构文档

### 健康预检

后端 37777 UP；37778–37781 全空闲。指纹 `2a30b422…`，与基线（`db84dc5` / 同指纹）一致。
JS **250**（247 基线 + 第 246 轮新增 3 条）、Python **435**，均与基线一致。

### 代码方向（Demo）— JS 与 Python demo 的 `extractedData` 守卫已修

**两个 demo 把「清空」和「类型错误」当成同一件事，于是拒绝了后端明确接受的请求。**

JS demo（`examples/http-server/app.ts:309`、`:328`）与 Python demo
（`examples/http-server/app.py:427`、`:451`）各有一道守卫，形如
「`extractedData` 必须是对象」。它把 `null` 一并拒了——JS 显式写了 `|| ... === null`，
Python 则是**副作用**：`isinstance(None, dict)` 为 False，于是 null 被顺带拦下。
**而 `null` 在这个 PATCH 上是「清空该列」，后端接受并落库 NULL**（第 246 轮已实测）。

**活体三连（修复前，同一个观测、同一路径）**：

| 请求 | demo 响应 | 含义 |
|---|---|---|
| `{"title": null}` | **200** `{"status":"updated"}` | 清空字符串字段，成功 |
| `{"extractedData": null}` | **400** `extractedData must be a JSON object` | **清空被拒** |
| `{"extractedData": "oops"}` | **400** `extractedData must be a JSON object` | 真正的类型错误 |

后两行**状态码与文案完全相同**，而语义相反；第一行则证明同一个 PATCH body 里
`title` 能清空、`extractedData` 不能。**同一个 demo 请求后端同一路径**：
`PATCH /api/memory/observations/{id}` 带 `{"extractedData": null}` → **200** `{"status":"updated"}`，
回读 `extractedData` 为 **NULL**。**即 demo 拒掉的是真实后端会照办不误的请求。**

**修法**：守卫只拦真正的类型错误（字符串、数组、数字、布尔），放行 `null`。
四处（JS 两处、Python 两处）各改一行。**Java demo 的 create 路径本来就是这样写的**
（`edObj != null && !(edObj instanceof Map)`，见 `ObservationsController.java:175`），
本次是让另外两家与它对齐，不是发明新语义。

**双向注入验证为真（两个 demo 各跑一遍活体）**：
修复后 `{"extractedData": null}` → **200** 且直查 `mem_observations` 确认该列**确为 NULL**；
**对照组两条在修复前后都不通过**——`"oops"` 与 `[1,2]` **仍为 400**，
即守卫的本意被完整保留，没有把校验放空。另加一条正例 `{"extractedData":{"a":1}}` → 200，
确认正常写入未受影响。

**一处需要如实说明的读侧归一**：经 JS demo 清空后回读显示 `{}` 而非 `null`——
那是 **JS SDK 读取层**的既有归一（`parseObservation` 里 `safeRecord(...) ?? {}`），
不是写入没生效。直查数据库确认为 NULL，**写入侧与后端行为一致**。

**新立 P2-43（记录不修）**：追这条线时发现 **Python SDK 的两种调用风格对 `None` 语义相反**——
`ObservationUpdate(title=None).to_wire()` → `{}`、`is_empty()` → True（**发不出去**），
而 `update_observation(id, title=None)` 走 kwargs 分支**逐字段原样拷贝**，
`{"title": null}` 真的上 wire 并清空（探针 + demo 活体双证）。
**这意味着 P2-27 能力表把 Python 判为「✗」只对 dataclass 路径成立**，已加注说明。
**记录不修**：两种收法都改变现有调用方的可观测行为——让 dataclass 也发 `None` 会让
原本静默不发请求的调用突然发出 PATCH；让 kwargs 过滤 `None` 则**抽掉一个今天真实可用的能力**
（Python demo 的 PATCH 正是走 kwargs）。先决定哪种才是受支持的清空方式，属 API 契约决策。

**四家 demo 现状核实**（除已修的两家外）：Java demo 的 PATCH 对**所有** null 返 400
（`null instanceof String` 为 false），这是其 SDK `@JsonInclude(NON_NULL)` 限制的**忠实映射**，
不是独立缺陷；Go demo 无此守卫，但其 `dto.ObservationUpdate` 的 `omitempty` 同样表达不了 null。
**探针自身错一次并先识别再采信**：最初想用「四家 demo 端点覆盖对拍」找差异，
第一版正则只认 `@GetMapping("/x")`，漏掉 `@GetMapping(value = "/x")`，
把 44 个端点的子路径全吞成类级前缀、看起来像大面积重复映射——**改兼容写法后
44 个端点零重复**，此前的「重复」全是探针造成的。

### 文档方向（架构文档）— 两处「真实代码」引用已修（双语，第 148 轮）

**同一节里两处标注为「真实代码」的引用都不准，而它们正是用来证明「本项目确实用了 Java 21 特性」的。**

- **DOC-1（已修，双语）** `OffsetPageRequest.java:109` 那段 `equals` 摘录**不忠实**：
  真实实现比较**四个**字段，摘录只写了**两个**——`page` 与 `size`，
  **漏掉了 `offset == that.getOffset()` 与 `sort.equals(that.getSort())`**。
  读者会据此以为值对象的相等性不看 offset 与 sort。已改为**逐字引用真实实现**（107-114 行），
  并注明「instanceof 那一行本身在 109」。同一段里紧邻的 `ToolUseRequest` record 摘录**是逐字准确的**
  （含 `@JsonProperty("extractedData")` 的驼峰与 `prompt_number`），可见作者对这段是有考究的——
  **正因为其余摘录都准确，这一处的手工删减才更难察觉**。
- **DOC-2（已修，双语）** `MemoryRefineService:88` 指向的是一行 **Javadoc**
  （` * @param projectPath Project path to refine`），而 `@Async` 在该文件位于
  **91、143、212** 三处，对应的 `refineMemory` 的注解在 **91**。已改为 `:91`。
  同一行并列的 `SummaryGenerationService:83` **本来就是准的**（正落在 `@Async` 上），
  `SummaryGenerationService:108` 亦准。

**修后复核**：架构文档两版共 5 处源码引用，**逐条落在所声称的位置**，
且 EN / ZH 的行号与代码主体**完全一致**（差异仅在注释行本身）。

### 探针数据清理

活体验证用的 3 条观测（`r247-demo-probe` / `r247-inject` / `r247-jsinject`）
各删 1 条观测 + 1 条会话；本轮启动的 Python demo（37780）与 JS demo（37781）**均已停止**，
后端 37777 非本轮启动故保留。临时日志已清理。

### 变更检测与验收

本轮**改了 JS 与 Python 的 demo 源码** → 指纹 `2a30b422…` → **`3d3bc4f5…`**，按门控跑完整验收：
回归 **45/0/1**、EXTRACTION **25/0/0**，**全通过**，基线推进至 `64c8ffa` / `3d3bc4f5…`。
另跑 JS `tsc --noEmit` + **250** 测试、Python **435** 测试，均全过。

### 轮换推进

代码审查：Demo 完成，下一方向 Backend。
文档审查：架构文档完成（一百四十八轮），下一方向 运维/用户指南（一百四十九轮）。

## 第 248 轮 — 2026-10-04 08:02 — Backend + 运维/用户指南

### 健康预检

后端 37777 UP；37778–37781 全空闲。指纹 `3d3bc4f5…`，与基线（`64c8ffa` / 同指纹）一致。
Java **196**、Go **359**、Python **435**、JS **250**，均与基线一致。

### 代码方向（Backend）— `CursorController.updateContext` 传错了参数，已修

**一个给 Cursor 用的端点，把「项目显示名」当成了「项目路径」去查库——于是它对着一个有两千多条记忆的项目，
自信地返回 `success: true` 并写下「还没有记忆」。**

`CursorController.java:197` 写的是 `contextService.generateContext(projectName)`。
而 `ContextService.generateContext(String projectPath)` 的 Javadoc 明写 `@param projectPath Project path`，
实现也当路径用：`validateProjectPath(projectPath)` 之后拿它去查
`observationRepository.findByTypeAndConcepts(projectPath, …)` 与
`summaryRepository.findByProjectLimited(projectPath, 100)`——两张表都是 `project_path` 列。
传进去的却是注册表里的**显示名**（`my-project` 这类），**匹配不到任何一行**。

**同一方法内自相矛盾，是最强的证据**：往下三行，写文件用的是
`cursorService.writeContextFile(entry.workspacePath(), context)`——**路径**。
`CursorService.writeContextFile(String workspacePath, String context)` 的签名也明确要求路径。
也就是说这个方法**读数据用名字、写数据用路径**。

**活体证据（决定性 A/B，两个实例跑同一输入）**：
为不干扰非本轮启动的 37777，**另起 37790 实例**跑本轮构建产物
（`backend/target/cortex-ce-0.1.0-beta.jar`，见文档方向）。
注册 `r248-ab` → `workspacePath=/Users/yangjiefeng/Documents/claude-mem`（**1,632 条**观测
命中 `generateContext` 默认的类型过滤器），同一注册、同一路径，只差实现：

| 实例 | 实现 | 产物 |
|---|---|---|
| 37777（未修复，传 projectName） | 旧 | **163 字节**，`# r248-ab — no memories yet` |
| 37790（已修复，传 workspacePath） | 新 | **17,750 字节**，50 条观测 + 时间线 + token 统计 |

**修复**：改用 `entry.workspacePath()`，并把「为什么不能用 projectName」写进注释。
**一处差点误判、已先识别再采信**：首次验证选的项目是 `/tmp/phase3-acceptance-test`（22,799 条观测），
修复后**内容仍然是空的**——差一步就要判「修复无效」。实查后发现
`ContextConfig` 默认只认 `{bugfix, feature, decision, refactor, discovery, change}` 六种 type 与
七种 concept，而该项目的 type 全是 `extracted_user_preference`(18,369) / `user_statement`(3,742) / `test`(688)，
**一条都不匹配，空结果是过滤器正常工作、不是修复失败**。换到有匹配数据的项目后 A/B 才成立。
**这正是「探针/样本自身出错必须先识别再采信」**：样本选错时的表现与缺陷未修复完全相同。

**验证边界（如实说明）**：`scripts/regression-test.sh` 与 `scripts/phase3-acceptance-test.sh`
对 `/api/cursor` **零引用**（`grep -c` 均为 0），故标准套件**不覆盖**该端点，
本次修复的依据是上述**双实例 A/B 活体对照**，不是回归套件。
另需说明：完整验收跑在 37777 上，而 37777 是**本轮之前**启动的旧实例，**不含本轮的 CursorController 修复**——
按纪律只停止本轮自己启动的进程，故未重启它。

**顺带核实无误**：`TestController` 有 `@Profile("!prod")` 守卫；`LogsController` 的滑动窗口、
跨文件前置合并与末尾裁剪逻辑均正确；`StreamController` 的 `initial_load` / `processing_status`
两事件与清理回调无误。`/api/test/embedding` 把上游报错原文回显（实测 `401 … Token is invalid.`），
未含密钥、且 500 与 OpenAPI 注解一致，**不作为缺陷记录**。

### 文档方向（运维/用户指南）— 十处构建命令的 jar 名全错，已修（第 149 轮）

**照文档复制粘贴构建命令，得到的 jar 名有三种，而磁盘上一个都不存在。**

本轮构建后实测产物为 **`target/cortex-ce-0.1.0-beta.jar`**——`<artifactId>cortex-ce</artifactId>`、
`<version>0.1.0-beta</version>`、**无 `finalName`**，故 Maven 默认命名即 `artifactId-version.jar`。
盘查非归档区发现**三种错名**：

| 错名 | 出处 | 问题 |
|---|---|---|
| `cortexce-0.1.0-SNAPSHOT.jar` | `proxy/CLAUDE-CODE-INTEGRATION.md` + zh-CN、`proxy/CURSOR-INTEGRATION.md` + zh-CN | artifactId 是 `cortex-ce`（**带连字符**），`cortexce` 这种写法**从来不可能由 Maven 产出** |
| `claude-mem-java-0.1.0-SNAPSHOT.jar` | `CLAUDE.md:232`、`scripts/openclaw-plugin-test.sh`(×2)、`scripts/thin-proxy-test.sh` | 用的是 pom 里的 `<name>claude-mem-java</name>`，而 `<name>` **不参与** jar 命名 |
| `backend-0.1.0-SNAPSHOT.jar` | `scripts/deploy-webui.sh:123`、`scripts/test-llm-provider.sh:115` | 第三种错法；`deploy-webui.sh` 那条还多错一处——写的是 `cd java/backend`，而**仓库里根本没有 `java/` 目录**（脚本自己的 `JAVA_DIR` 变量算出的就是仓库根） |

**修法用通配符 `target/cortex-ce-*.jar` 而非写死版本号**：这已是同类错误第二次发生
（`docs/archive/2026-09-30_doc-review-history.md` 记着 2026-04-02 修过一次，
把 `claude-mem-java-0.1.0-SNAPSHOT.jar` 改成 `claude-mem-java-0.1.0-beta.jar`——
**版本对了、artifactId 仍然错**，直到今天才真正修对）。写死版本号下次升版必然再次腐烂；
`AGENTS.md`（磁盘版）、两个 Dockerfile、`proxy/README.md` 用的都已是通配符，本次与之对齐。
已核验 `ls target/cortex-ce-*.jar` 解析到唯一产物，且该 jar **本轮实测可正常启动**。
四个改动过的脚本 `bash -n` 语法自检均通过；非归档区复查**零命中**。
`docs/archive/` 下的同类记载属归档文件，按规则**不得修改**。

**核实无误、未改**：磁盘版 `AGENTS.md` 的 Build & Run 已用 `mvn clean install` + `target/cortex-ce-*.jar`，
**本来就是对的**；`backend/README.md:84`、`HEARTBEAT.md`、`openclaw-plugin/` 三份、
`phase3-acceptance-test.sh` 等亦均正确。**一处版本差异需说明**：本会话开头的 `AGENTS.md`
副本与磁盘版内容不同（前者含 `./mvnw` 与 `claude-mem-java-0.1.0-SNAPSHOT.jar`），
**以磁盘为准**核实，磁盘版无误。

### 探针数据清理

`r248-ab` / `r248-test` 两个注册项在 37777 与 37790 上均已注销；
`/Users/yangjiefeng/Documents/claude-mem/.cursor` 与 `/tmp/phase3-acceptance-test/.cursor`
（均为本轮新建）已整体删除；本轮启动的 37790 实例已停止。
37777 非本轮启动，保留。构建产物 `backend/target/` 在 gitignore 内。

### 变更检测与验收

本轮**改了后端 Java 源码与四个 shell 脚本** → 指纹 `3d3bc4f5…` → **`a1bb27b5…`**，
按门控跑完整验收：回归 **45/0/1**、EXTRACTION **25/0/0**，**全通过**，
基线推进至 `f062f42` / `a1bb27b5…`。

### 轮换推进

代码审查：Backend 完成，下一方向 Java SDK。
文档审查：运维/用户指南完成（一百四十九轮），下一方向 API 文档（一百五十轮）。

## 第 249 轮 — 2026-10-04 08:34 — Java SDK + API 文档

### 健康预检

后端 37777 UP；37778–37781、37790 全空闲。指纹 `a1bb27b5…`，与基线（`f062f42` / 同指纹）一致。
Java **196**（143+46+7）、Go **359**、Python **435**、JS **250**，均与基线一致。

### 代码方向（Java SDK）— 新立 P2-44（记录不修）+ 补 Javadoc 约束

**Bridge advisor 与手动 `begin/end` 不能嵌套——而外层作用域会被**静默**销毁。**

`CortexSessionContext.begin()` 是裸的 `CURRENT.set(new SessionInfo(...))`、`end()` 是裸的
`CURRENT.remove()`，**既无重入保护、也不保存/恢复**；而
`CortexSessionContextBridgeAdvisor.adviseCall/adviseStream` 每见到 `CONVERSATION_ID` 就无条件
`begin`、在 `finally` 里 `end`。**探针实测**（一次性用例，未提交）：

| 时点 | `isActive()` | `getSessionId()` | `getProjectPath()` |
|---|---|---|---|
| 调用前 | `true` | `outer-session` | `/outer/project` |
| **调用后** | **`false`** | **`unknown-session`** | **（空串）** |

断言「外层应当存活」**失败**，即缺陷成立。**全程无异常、无告警**——此后同一外层作用域里的
任何 `@Tool` 调用都会以 `unknown-session` 与空项目路径入库。调用**内部**看到的是 advisor 自己的
上下文，即**内层正确、外层被毁**。
**与 P1-1 无关**：P1-1 是流式下 ThreadLocal 跨线程丢失，本条是**单线程内的嵌套**，两条路径独立。

**为什么不是示例代码的活 bug**：`ChatController` 刻意把两条路径二分——带 `conversationId` 的请求
走 bridge 且**不**手动 `begin`；不带的手动 `begin`，而 bridge 因无 `CONVERSATION_ID` 直接透传。
**从不嵌套**。故这是**误用场景**，而失败是静默的。

**Status：⏸ 记录不修**（P2-44）。两种收法都改变现有调用方的可观测行为：
①在 `CortexSessionContext` 上加保存/恢复（需把 private 的 `SessionInfo` 暴露为公开类型，
属**新增公开 API**）；②已激活时跳过 `begin/end`、让外层胜出（零新增 API，
但内外 session 不同时内层会被记到**外层**会话上，属跨会话串号）。真正的修法要先决定
**两者冲突时谁该赢**，属产品决策。
**已做的零风险部分**：在 advisor 的类 Javadoc 写明「不可嵌套」这一约束、外层被静默销毁的实测
后果、以及 demo 为何二分——**纯注释，行为一字未改**，Java **196** 测试与基线一致。
现有 6 个该 advisor 的测试**无一覆盖嵌套**。

**顺带核实无误**：`CortexMemHealthIndicator` 的 UP/DOWN/异常三分支正确；
`CortexMemAutoConfiguration` 的条件装配正确（嵌套类的 `log` 虽声明在使用之后，
但字段作用域覆盖整个类体，合法且可编译）；`DefaultMemoryRetrievalService:39` 的
`count > 0 ? count : defaultCount` 守卫在位（第 243 轮所修）。

### 文档方向（API 文档）— 一处「输入错误被报成服务端故障」已修（双语，第 150 轮）

**`GET /api/context/recent?limit=-1` 返回 HTTP 500——而同一批端点的其它负值参数都正常。**

沿第 240 轮（`count`）建立的「数值参数非正数语义」这条线继续扫，实测发现
`/api/context/recent?limit=-1` → **500 Internal Server Error**，**稳定复现 3/3**。
相邻端点均正常：`/api/context/preview?maxObservations=-1` 200、
`/api/timeline?depthBefore=-1` 与 `depthAfter=-1` 均 200、`/api/logs?lines=-1` 200。

**根因**：`ContextController.java:187` 把 `limit` **原样**交给
`summaryRepository.findByProjectLimited(...)`，而那是一条原生查询 `@Query("... LIMIT :limit")`。
**PostgreSQL 直接拒绝负 LIMIT**——实测 `SELECT 1 LIMIT -1` 抛
`InvalidRowCountInLimitClause: LIMIT must not be negative`，Spring 把它翻成 500。
**即客户端输入错误被报成服务端故障。**

**修法**：`Math.min(Math.max(1, limit), 20)`，与本 controller 内
`POST /api/context/semantic` 对**同名同义**的 `limit` 已有做法一致（`ContextController.java:454`），
也与全后端 `Math.min(Math.max(1, x), MAX)` 的惯例一致。

**双向注入验证为真（两个实例、同一项目 `openclaw`，该��目有 4,610 条摘要）**：
为不干扰非本轮启动的 37777，另起 37790 跑本轮构建产物。

| `limit` | 37777（未修复） | 37790（已修复） |
|---|---|---|
| `-1` | **HTTP 500** | 200，1 条 |
| `0` | 200，谎称「No previous sessions found」（实有 4,610 条） | 200，1 条 |
| `5` | 200，5 条 | 200，**5 条（对照组，完全一致）** |
| `5000` | 200，**4,610 条（无上限）** | 200，**20 条（有界）** |

对照组 `limit=5` 两种实现**逐字一致**，证明没有过度修复；
`limit=5000` 一栏顺带暴露了旧实现**响应无上界**（一次吐 4,610 条摘要），上界不只是防负数。

**文档已同步（双语）**：`API.md` / `API-zh-CN.md` 的 `/api/context/recent` 参数表补上
「Clamped to **[1, 20]** / 钳制到 [1, 20]」，并各加一段说明**为何**要有这条钳制
（负数会打到 PostgreSQL 而 500；`0` 会产出误导性的「无历史会话」文案），
以及同一区间也用于 `/api/context/semantic`。

**API.md 全面对拍核实无误**：活体 OpenAPI **62 条路径**，API.md 与中文版**双向各覆盖 62/62**，
**零幻影端点**（扫出的 `/api` 与 `/api/memory/observations` 均出自散文提及，非端点声明）；
活体**全部 query 参数名**在 API.md 中**均有载**，零漂移。
**探针两次自身出错并先识别再采信**：①首版正则要求动词与路径相邻，漏掉了
`#### GET \`/api/context/recent\`` 这类（中间有反引号）与代码块里的裸 `/stream`，
一度报出「4 条未载、1 条幻影」；②放宽后又把 `/api/memory/observations/{id}` 的子串
与变更日志里的散文提及误判为端点。两次修正后结论才成立。

### 验证边界（如实说明）

`/api/context/recent` **有**被 MCP e2e 脚本覆盖（两处断言 `200`），但**未被**
`regression-test.sh` / `phase3-acceptance-test.sh` 覆盖。
**单元测试在本环境不可行**：曾写好 `ContextControllerLimitTest`（5 条，含 in-range 对照），
但 Maven 实际运行在 **JDK 25**（Homebrew；PATH 上的 `java -version` 是 24.0.1，两者不一致），
Mockito 无法 mock 具体类 `ContextService`（`Mockito cannot mock this class`），
与既有的 `TimelineServiceTest` 11 error **同因**。该测试文件**已移除、未提交**，
本次修复的依据是上述**双实例活体 A/B**。
另：完整验收跑在 37777 上，而 37777 是本轮之前启动的旧实例、**不含本轮的 ContextController 修复**。

### 变更检测与验收

本轮**改了后端 Java 源码与 Java SDK 的 Javadoc** → 指纹 `a1bb27b5…` → **`7bf3693c…`**，
按门控跑完整验收：回归 **45/0/1**、EXTRACTION **25/0/0**，**全通过**，
基线推进至 `01ced58` / `7bf3693c…`。

### 轮换推进

代码审查：Java SDK 完成，下一方向 Go SDK。
文档审查：API 文档完成（一百五十轮），下一方向 SDK README（一百五十一轮）。

## 第 250 轮 — 2026-10-04 09:06 — Go SDK + SDK README

### 健康预检

后端 37777 UP；37778–37781、37790 全空闲。指纹 `7bf3693c…`，与基线（`01ced58` / 同指纹）一致。
Go `./test-all.sh` 4/4 模块通过；Java **196**、Python **435**、JS **250** 均与基线一致。

### 代码方向（Go SDK）— 新立 P2-45（记录不修）

**会话启动有一个真实生效的「多项目上下文」能力、API.md 也写清楚了——而四家 SDK 一律发不出去。**

后端的会话启动契约有 **7** 个字段（`session_id` / `project_path` / `cwd` / `user_id` /
`projects` / `is_worktree` / `parent_project`），**四家 SDK 一律只暴露 3 个**：
Go 是 `dto.SessionStartRequest` 结构体、Java 是同名 record、JS 是同名 interface、
Python 是 `start_session(session_id, project_path, user_id=None)` 三个位置参数。

**`projects` 是真实生效的能力**：`SessionController` 在它含逗号时走 `parseProjectsParam`
并生成多项目上下文。**活体实测（同一 `project_path`，只差 `projects`）**：
不带时返回 `"# phase3-acceptance-test — no memories yet"`；
带 `"projects":"openclaw,/tmp/phase3-acceptance-test"` 后返回
`"# openclaw recent context … 📊 25 observations | 📖 6,109 read tokens"`。
`API.md` 的字段表**完整记载**了它（「Multi-project support, comma-separated」），
所以不是未公开特性。**后果**：SDK 用户永远拿不到多项目上下文，只能自己发 HTTP。

**Status：⏸ 记录不修**——补字段属**新增公开 API**，且**四家完全一致地缺失**，
与 P2-40 / P2-41 同理：要么是有意划定的范围、要么是共同疏漏，都指向项目层面拍板。
若将来实施，注意四家模型形态各不相同（Go/Java/JS 是对象字段，Python 是位置参数）。

**同区域另两处事实（一并记录，留待各自轮次）**：
①**`is_worktree` / `parent_project` 只进日志**——控制器读了两者后**仅用于一条 `log.info`**，
不落库、不参与 `initializeSession`；而本该让 worktree 真正生效的 `WorktreeDetector`
**在 `backend/src/` 内零调用者**（除自身文件外无任何引用）。活体佐证：带
`is_worktree:true` + `parent_project` 的请求与不带时的响应**完全相同**。
**但 `API.md` 把两者作为正式字段记载并写进了示例 body**，**文档描述的是尚未实现的能力**——
属 API 文档方向的问题，留待下一轮 API 文档审查更正。
②**`CLAUDE.md` 的 Go 测试数与 Go README 互相矛盾**（见文档方向的探针纠正），留待项目决策。

**核实无误、未改**：Go 客户端的查询参数**处处 `if x > 0`**（负值与零一律省略、走���端默认），
`GetExtractionHistory` 另有显式负值拒绝；HTTP 层用 `io.LimitReader(MaxResponseBytes+1)`
**多读一字节**以显式报超限而非静默截断、`url.Parse(BaseURL+path)` 保留路径前缀、
已取消的 ctx 快速失败；`StringList.UnmarshalJSON` 覆盖数组 / JSON 字符串 / 空 / 逗号分隔 TEXT
四种形态且从不返回错误（`null` 也正确降级）；错误映射把 404/400/401/409/429/403/422 与 5xx
各映射到独立哨兵，重试只认 429/502/503/504。

### 文档方向（SDK README）— 两份 JS README 的测试数已更正；Go 的 359 复核**成立**（第 151 轮）

**抓到了自己第 246 轮留下的陈旧数字：两份 JS README 仍写 247 个单元测试，而实际已是 250。**
分解也对不上：`234 client + 5 truncated-body + 8 http-server` 中，
`client.test.ts` 已从 234 增至 **237**（第 246 轮新增的 3 条）。双语已更正为
`250（237 + 5 + 8）`——**第 246 轮改了测试却没同步文档，正是本轮该抓的东西**。

**Go README 的 359 复核成立，不改**（此处差点误判，先识别再采信）：
初测 `test-all.sh` 四模块合计得 **332**，一度以为 README 的 359 是陈旧数字。
**但 README 自己定义了口径并给出分解**：根模块 299（core 232 + dto 67）
+ eino 8 + genkit 13 + langchaingo 12 + **`examples/http-server` 27** = **359**。
其中 **dto = 67 与我的实测逐字吻合**。我的 332 之所以小 27，正是因为
`test-all.sh` 的 `MODULES` 列表**不含 `examples/http-server`**——**是我的口径窄了，不是文档错了**。
**教训**：测试数这类数字必须连同**计数命令**一起核对，跨口径直接相减必然得出错误结论。
据此另记：`CLAUDE.md` 的「372（278 core + 61 dto + …）」与 README 的 359 **互相矛盾**
（core/dto 拆分对不上、且漏了 `examples/http-server` 的 27 条），
**以 Go README 为准**，但 `CLAUDE.md` 的更正留待项目决策（该文件是否纳入版本控制仍在待决）。

**四家 README 方法覆盖核实无误**：Go 接口 **27** 个方法，README **27/27 全覆盖、零幻影**；
Java 接口 **25** 个方法，EN 与中文版**各 25/25**；测试数声明 Java **196**（143+46+7，与实测一致）、
Python **435**（实测 435 一致）。**探针两次自身出错并先识别再采信**：
①对四家用同一个正则找「独立反引号方法名」，但 Java README 用的是 `client.方法(` 写法、
Python/JS 又有各自格式，导致 Java 匹配到 **0** 个——按各自真实写法重做才对；
②一度把 `Memory` 这个散文用词当成幻影方法。

### 变更检测与验收

本轮**只改 `.md`**（两份 JS README + 巡检/发现文档）→ 指纹 `7bf3693c…` **未变且与基线一致**，
按门控**不跑完整验收、不推进基线**。

### 压缩（第十四次，第七批）

P2-45 加入后 findings 达 **1019 行**、触发 `COMPACTION_REQUIRED`（脚本退出码 2）。
Evidence / Reproduction 层已基本耗尽（仅剩 18 行且多为指针），
故改用 2026-09-30 起的「**无条件已解决条目整体迁出**」先例：
**P2-11（51 行）与 P2-38（35 行）** 状态均已写为 ✅ 已修、决策已定，逐字迁入
`2026-10-04_backend-review-resolved-3.md`，源文件留一行指针，迁出前逐字校验。
**回落 935 行**。

### 探针数据清理

活体验证用的 `r250-a` / `r250-b` / `r250-c` 三个会话（各 1 行）已从 `mem_sessions` 删除，
`mem_observations` / `mem_summaries` 均无残留。

### 轮换推进

代码审查：Go SDK 完成，下一方向 Python SDK。
文档审查：SDK README 完成（一百五十一轮），下一方向 设计文档（一百五十二轮）。

---
