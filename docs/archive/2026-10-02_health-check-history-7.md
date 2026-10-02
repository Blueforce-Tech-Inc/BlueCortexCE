# 巡检与健康检查历史（第 163~170 轮）

> **Archived from**: `docs/drafts/health-check-task.md`
> **Archived on**: 2026-10-02（活动文件达 1000 行阈值，写入前压缩迁移）
> **Contents**: 第 163~170 轮（8 条），按轮号提取并逐块断言自报轮号
> **Rule**: 本文件创建后不得修改。

## 第 163 轮 — 2026-10-02 18:15 — Python SDK + API 文档

### 代码方向：Python SDK

**未发现缺陷。** 针对本轮字段做了完整活体核验（本轮真正的缺陷在别处，见下）：

- `_to_str_list` 对 `refined_from_ids` 的逗号分隔形态处理**正确**——对着活体记录，
  100 条里 61 条带该字段，**0 条解析失败**，61 条全部还原出 17 个 ID（靠第 157 轮加的
  逗号切分降级）。
- `_to_dict` 只接受真实 dict，但这是对的：`ObservationEntity.extractedData` 是普通
  `Map<String,Object>` 字段、`@JsonProperty("extractedData")`，**没有** JSON 字符串 getter
  （`writeValueAsString` 在该实体中只出现 4 次，对应 facts/concepts/files_read/files_modified）。
- 389 测试全绿。

### 跨方向修复：GO-4（P1，迄今最严重的 Go SDK 缺陷）

本轮方向审完后，在核验 `refined_from_ids` 时撞上一个远超预期的 Go SDK 缺陷。
**先审完本轮方向再跨方向修复**，理由与第 158 轮一致：证据、根因、修复方案均已在手，
且该缺陷让一个主要读路径对绝大多数真实项目**直接失败**。

**`mem_observations.refined_from_ids` 根本不是 JSONB 列。** 三重证据一致：

| 证据 | 内容 |
|---|---|
| V11 迁移 | `ADD COLUMN refined_from_ids TEXT`，`COMMENT … IS 'Comma-separated IDs of merged observations'` |
| 唯一写入方 | `ExtractionStorageService:93-96`，`Collectors.joining(",")`，**从不 JSON 编码** |
| 活体记录 | 同一条记录里 `refined_from_ids` 用 `json.loads()` 解析在第一个 UUID 处抛 `JSONDecodeError`；而 `concepts` 是 `'["extraction","user_preference"]'` 正常解码 |

**Go 的 `StringList.UnmarshalJSON` 假定所有列表列要么是 JSON 数组、要么是 JSON 编码数组，
两者都不是时返回错误。** 而 `encoding/json` 只要有一个 `UnmarshalJSON` 返回错误，
**整个外层结构体的解析就中止**——所以爆炸半径是整页：

```
修复前：ListObservations ERROR: cortex-ce: failed to parse /api/observations response:
        StringList: cannot parse string-encoded JSON: invalid character 'f' after top-level value
修复后：err=<nil>  items=100  RefinedFromIds non-empty=61
        GetObservationsByIds -> 1 observations -> RefinedFromIds len=17
```

实测规模：扫 6000 条观察记录，**3525 条（59%）带 `refined_from_ids`**——即任何使用
Phase 3 结构化抽取的项目，用 Go SDK 都**列不出来**。

修复：`StringList` 接受逗号分隔串，并且**彻底不再返回错误**——单个列表列的异常形态
不该让携带它的整条观察作废。Python/JS/Java 三家都是降级为空，Go 现在与它们一致。

**四家横向对比（同一份活体记录）**：Python 靠第 157 轮的逗号降级还原出全部 17 个 ID ✓；
JS 的 `safeStringOrStringList` 有逗号切分 ✓；Java 的 `strList` 有逗号降级 ✓；
**Go 是唯一坏的一个**。

新增 6 个测试，全部使用真实 wire 形态，其中包含一整条抽取观察的响应体，以及一个混合
null / 逗号分隔 / JSON 编码三种值的分页夹具。退回修复后 6 个失败；恢复「非字符串形态
返回错误」后 1 个失败。

**一处自我纠错值得记录**：第一次做第二个退回验证时脚本报「无失败」，我差点就这么记下。
实际原因是补丁重新引入了 `fmt.Errorf` 却没恢复 `fmt` 导入，**包编译失败因而没有输出任何
FAIL 行**——被我的脚本误读成「测试抓不住」。补上导入重做后，正确地失败在
`TestStringList_NeverErrorsOnUnrecognisedShape`。**验证脚本自身出错的代价，是把一个
「测试无效」的错误结论写进报告。**

Go SDK：核心 230、dto **61 → 67**、eino 8、genkit 13、langchaingo 12；gofmt 与 vet 干净；
go-sdk-e2e **39/39**（Demo 起于 37779，本轮结束已停）。

### 文档方向：API 文档

**DOC-1（P1，已修）`refined_from_ids` 在两版 API 文档里的描述是错的，且表格自相矛盾。**

`docs/API.md:1379` 写「JSON-encoded array of source observation UUIDs」，
而**同一表格上方三行的示例**写的是 `"refined_from_ids": "obs-abc-123,obs-def-456"`——
逗号分隔。类型 `string | null` 本来就对，错的只是描述。两版均已更正，并写明
「这是 `TEXT` 列而非 JSONB，后端用 `,` 拼接且从不 JSON 编码」。

同时更正第 157 轮那条变更日志——它把 `refined_from_ids` 与四个 JSONB 列归为一类
（「这些 JSONB 列被序列化为 JSON 编码字符串」）。该条标为 unreleased，故直接改写而非追加。

**连带更正我自己在第 162 轮刚写进架构文档的错误**：`ARCHITECTURE.md` 的 Wire format 小节
写了「`refined_from_ids` behaves the same way」——**错**，且与同一文件 170 行之前的
`refined_from_ids TEXT, -- V11: comma-separated merged IDs` 直接矛盾。两版均已改写为
显式的例外说明。这也是「写完新内容要回头核验」的又一个实例：第 162 轮的文本当时是对的
（我以为），但那个判断本身是错的。

**核实无误未改**：观察响应表两版各 **31 字段、键集合相同、顺序相同、类型零差异**；
`facts`/`concepts` 在**请求**侧标注 `string[]` 是对的（请求侧确实接受真实数组），
在**响应**侧标注 `string` 也是对的（JSON 编码串）——两侧不同是正确的，不是缺陷。

### 验收

指纹 `e0617744…` → `3b4d3a8c…`（Go SDK 有代码改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `aaa384e` / `3b4d3a8c…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **P1-2 导入端点把校验失败报成成功跳过**（第 160 轮记录，四处调用点，见 findings.md）。
3. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
4. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
5. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
6. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
7. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
8. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
9. push 权限阻塞（`wubuku` 403）。
10. 并行巡检进程争写状态文件。
11. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Python SDK 完成（新循环第十六轮），下一方向 JS/TS SDK；
文档审查轮换推进：API 文档完成（六十四轮），下一方向 SDK README。

---

## 第 164 轮 — 2026-10-02 18:33 — JS/TS SDK + SDK README

### 代码方向：JS/TS SDK

**JS-4（P1，已修）`doFetch` 吞掉了读响应体时的传输层错误。**

`resp.text()` 外面包着一个 catch，返回空 body 加上响应自带的 status，注释理由是
「resp.text() can throw if body is null in some runtimes」。但**按 Fetch 规范，
`resp.text()` 对 null body 是 resolve 成 `''` 而不是抛异常**——该场景并不存在。
它真正会抛的是**响应体读不完整**，而那些错误被丢掉了。对着一个声明 Content-Length
后立刻断连的服务器实测：

| | 结果 |
|---|---|
| 原生 `fetch` | 抛 `TypeError: terminated`（正确报告截断） |
| SDK 修复前 | 抛 `Error: failed to parse /api/version response`，`isRetryable` = **false** |
| SDK 修复后 | 抛 `TypeError: terminated`，`isRetryable` = **true** |

**后果比「消息误导」更严重**：capture 路径上一个被截断的 200 会返回「空 body + 200」，
`requestNoContent` 因此看不到错误，`doFireAndForget` 就**为一次从未完整到达的响应报告成功**；
而由于 TypeError 已经被丢掉，`isRetryable`（它正是为了识别 TypeError 与 AbortError
以便重试而写的那两个分支）根本没见到它，**这个瞬时失败永远不会被重试**。
客户端自身的请求超时若在读 body 期间触发，AbortError 同样被吞成「空的 200」。

修复：`TypeError` 与 `AbortError` 原样重抛，运行时自身的分类得以保留，既有的重试逻辑
才能据此动作。原注释想防的那个窄场景**保留**给两者都不是的值。

**这与上一轮 Go 的 `StringList` 是同一类缺陷、极性相反**：那里是意外形态毁掉整条记录，
这里是读取失败被报成干净成功。四家里 Go/Python/Java 都把截断的响应体判为可重试，
**JS 是唯一不重试的**。

新增 5 个测试，全部用**真实服务器**而非 mock fetch：重抛、可重试分类、对「截断两次
后成功」的服务器必须重试三次、读 body 期间的超时、以及保留下来的窄降级分支。
退回修复后 **5 个中 3 个失败**。

**一处自我纠错**：新测试第一次跑时我有一个断言写错了——用 204 + `requestJSON` 去验证
保留下来的降级分支，失败于「failed to parse」，因为 `requestJSON` 不像 Python 的
`_request_json` 那样对空 body 特殊处理。那是 mock 的问题不是代码的问题，断言已移到
no-content 路径（空 body 在那里不是解析错误）。

JS 219 → **224** 全绿，tsc 干净，CJS + DTS 构建成功，js-sdk-e2e **27/27**。

### 文档方向：SDK README

**DOC-1（P1，已修）Python 与 JS 的 README 仍在把 `refined_from_ids` 与四个 JSONB 列归为一类。**

这正是第 163 轮在 API 文档与架构文档里更正掉的那个错误，在 README 里依然活着：
两边都写着「后端把这些 JSONB 列序列化成字符串」。**结论（→ `list[str]` / `string[]`）
是对的，错的归类**。四份文件（Python EN/ZH、JS EN/ZH）均已拆出单列，并写明
「`TEXT` 列、后端用 `,` 拼接、完全没有 JSON 这一层」。

**DOC-2（P2，已修）Go 的 README 完全没提列表列的 wire 形态**，而 Python/JS 都有——
尽管三种形态的处理正是 `dto.StringList` 一处完成的。EN/ZH 均已补上，
并说明 `StringList` 接受三种形态且**从不返回错误**。

**DOC-3（P2，已修）四家 README 的测试数与 patrol-task 基准全部过时**，且是逐个重新实测
而非按增量推算：

| 位置 | 原值 | 实测 |
|---|---|---|
| Go README | 255（client 194 + dto 61） | **297**（client 230 + dto 67） |
| JS README | 212 | **224** |
| Python README | 374 | **389** |
| Java README | 173（120 + 46 + 7） | **180**（127 + 46 + 7） |
| patrol-task 基准 | Java 175 / Go 335 / Python 385 / JS 216 | **180 / 343 / 389 / 224** |

Go 的集成包「额外 33 个」经核实**仍然正确**（genkit 13 + langchaingo 12 + eino 8），
未动。这批数字是第 156 轮刚按实测更正过的，此后每轮加测试都会再次漂移——
本轮再次全量重测而非沿用旧值。

**核实无误未改**：四家 README 的中英标题层级序列仍逐位一致、围栏平衡。

### 验收

指纹 `3b4d3a8c…` → `66345caf…`（JS SDK 有代码改动，且记录数 876 → 877，新增测试文件）→
完整验收：`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `57bfbc0` / `66345caf…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **P1-2 导入端点把校验失败报成成功跳过**（第 160 轮记录，四处调用点，见 findings.md）。
3. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录）。
4. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
5. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
6. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
7. **Java README 无 Wire Format 段落**（本轮核实发现，未补——补齐需新增整节，
   超出「小而准」的范围，留待专门一轮）。
8. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
9. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
10. push 权限阻塞（`wubuku` 403）。
11. 并行巡检进程争写状态文件。
12. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：JS/TS SDK 完成（新循环第十七轮），下一方向 Demo；
文档审查轮换推进：SDK README 完成（六十五轮），下一方向 设计文档。

---

## 第 165 轮 — 2026-10-02 18:49 — Demo + 设计文档

### 代码方向：Demo（JS Demo）

本轮审的是 **JS Demo**（`js-sdk/cortex-mem-js/examples/http-server/app.ts`，495 行）——
159 轮是 Java Demo、148 是 Go、153 是 Python，**JS Demo 从未被内部审过**。

**DEMO-2（P3，已修）`/search` 是全文件唯一接受整数尾随垃圾的 handler。**

它手写 `parseInt` + `isNaN` 判断，而 `parseIntParam`（带正则、拒绝尾随垃圾）
**本来就在同一个文件里**、已被另外 4 个 handler 使用。活体对比：

| | 修复前 | 修复后 |
|---|---|---|
| `/search?limit=10abc` | **200** | 400 `limit must be an integer` |
| `/observations?limit=10abc` | 400 | 400 |
| `/search?offset=5xyz` | **200** | 400 `offset must be an integer` |
| `/observations?offset=5xyz` | 400 | 400 |

正常路径无回归：不带参数、`limit=3`、`limit=0`、缺 `project`、`limit=101`
（仍返区间提示）全部与修复前一致。js-demo-e2e **27/27**。

顺带更正文件头注释：原写「Exposes 26 REST endpoints covering all 25 public SDK API
methods (plus /health)」——**把 health 重复计了一次**。精确说法已写入：26 条路由覆盖
全部 25 个公开 API 方法，其中 `buildICLPrompt` 由两条路由提供（`/chat` 与 `/iclprompt`），
`close()` 是无路由的生命周期方法。核实方式：抽出 26 条注册路由（与 26 行启动横幅一致）
与 Demo 实际调用的 26 个不同 client 方法（其中 `close` 无路由）。

**核实无误未改**：

- `ids` 传字符串/数字/对象到 `/observations/batch` 得到 **400 而非崩溃**——Demo 的
  长度/去空格循环确实放过了字符串，但 SDK 施加同样的检查、后端拒绝错误类型，
  两种路径最终都是正确的 400。
- 全局错误处理器**已经**把 `ValidationError` 映射到 400、`APIError` 透传
  `statusCode`，所以不像第 159 轮之前的 Java Demo 那样需要因果链解包。
- 尾随垃圾的 batch id 报 Spring 的 "Bad Request"，是因为后端默认错误体只带
  `error: "Bad Request"` 而不暴露反序列化细节——这是 Spring 的行为，不是 SDK 的。

**一处自我纠错**：我一度用临时 tsc flags 检查 `examples/`，报出 `parseIntParam(...).message`
的联合类型错误。但其中一行是我**没动过的既有代码**，说明是我的 flags 与项目 tsconfig 不一致。
用项目自身 compilerOptions 复核后 **examples 零类型错误**——顺带查明项目的
`tsconfig.json` 只 `include: ["src/**/*"]`，即 **`examples/` 从未被 `npm run lint` 类型检查**
（本身不是缺陷，记录在案）。

### 文档方向：设计文档

**DOC-1（P2，已修）`phase-3-design/25.md` 的前置检查在本机必然失败。**

清单第 1 步是 `java -version 2>&1 | grep "21"`，而本仓库实际在 **JDK 24.0.1** 上开发、
运行中的后端自报 `{"java":"24.0.1"}`。该命令在本机**实测失败**；而清单开头明写
「Each step must pass verification before proceeding to the next」——**照文档执行会永远
卡在第 1 步**。

根因：pom 里是 `<java.version>21</java.version>`，那是**字节码目标**而非 JDK 约束，
24 是合法环境。已改为显式的 `>= 21` 比较，并**逐字执行验证**新命令在本机通过、
旧命令仍然失败。顺带查出 `docs/DEVELOPMENT*.md` 里的两处 `java -version` 只是打印、
不带 grep，故无问题。

**核实无误未改**：设计文档全集中 12 处 `45/45` 回归计数**仍然准确**；
`refined_from_ids` 在整个 phase-3-design 目录中**一次都没出现**，所以第 163 轮的更正
不需要同步到这里。

### 验收

指纹 `66345caf…` → `31b3d088…`（JS Demo 有代码改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `ed946a9` / `31b3d088…`。
Demo 端口 37778–37781 本轮结束全部关闭（JS Demo 起于 37781，本轮自行停止）。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）。
2. **P1-2 导入端点把校验失败报成成功跳过**（第 160 轮记录，四处调用点，见 findings.md）。
3. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录，JDK 24 下 Mockito inline 问题）。
4. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
5. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
6. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
7. **Java SDK README 无 Wire Format 段落**（第 164 轮核实发现，待专门一轮补齐）。
8. **JS SDK 的 `examples/` 从未被类型检查**——`tsconfig.json` 只含 `src/**/*`，
   `npm run lint` 与 CI 都不覆盖示例代码（本轮核实，当前无类型错误）。
9. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
10. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
11. push 权限阻塞（`wubuku` 403）。
12. 并行巡检进程争写状态文件。
13. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Demo 完成（新循环第十八轮），下一方向 Backend；
文档审查轮换推进：设计文档完成（六十六轮），下一方向 API 文档。

---

## 第 166 轮 — 2026-10-02 19:10 — Backend + API 文档

### 归档压缩

写入前活动文件已 941 行，追加本轮必然突破 1000 行，故先归档：第 158~162 轮（5 条）移入
`docs/archive/2026-10-02_health-check-history-6.md`（534 行 / 31717 字节），更新
`docs/archive/README.md`，活动文件回落到 422 行 / 25081 字节。归档脚本沿用第 5 次确立的
**按轮号提取 + 逐块断言自报轮号**模式，并校验 5 个区块自报轮号与提取轮号一致。
顺带补全了活动文件里「巡检历史（已归档）」一节——它此前只列到 history-4，
第 160 轮建了 history-5 却没更新此处；现改为完整表格并列出全部 6 个归档文件。

### 代码方向：Backend

**BACK-4（P1，已修）P1-2 关闭——导入端点把 `ImportResult.error(...)` 报成成功跳过。**

第 160 轮记录、四处调用点、需 Backend 轮集中修，本轮正是那个轮次。`ImportResult` 有
**三个**工厂（`imported`/`duplicate`/`error`），而 `ImportController` 的四处单记录循环
只按 `imported()` 分支，于是 `error()` 落进 skip 计数；`errors`/`errorMessages`
**只接收抛出的异常**。同一类失败因此仅因「抛出」还是「返回」而被报告成两种样子：

| 失败形态 | 修复前 | 修复后 |
|---|---|---|
| 校验失败**返回** | `skipped:1, errors:0, errorMessages:[]` | `skipped:0, errors:1, ["projectPath is required"]` |
| 同类失败**抛出** | `errors:1` | `errors:1`（不变） |
| 真正的重复 | `skipped:1` | `skipped:1`（**不变**） |
| 混合批次 | — | `imported:1, errors:2` |

第一行修复前是一条**已被静默丢弃的记录**却报告为干净的成功。字段名拼错、大小写用错或
漏掉必填字段的客户端，会收到「一切正常」——这正是导入/迁移期间的静默数据丢失。

采纳了该条自己提出的「更干净的替代方案」：给 `ImportResult` 加**显式判别方法**
`isError()`（`!imported && id == null`），而不是让 4 处调用点各自依赖 `id == null`
这一隐式约定；`isError()` 上写了完整 javadoc，说明三种工厂的判别方式与
「抛出/返回不对称」的根因。校验**先于**重复判定，故「既无效又是重复」的记录报为 error。

**BACK-5（P2，已修）`importSession` / `importSummary` 不校验 `projectPath`，产生 opaque 错误。**
验证 P1-2 修复时撞到：两张表的 `project_path` 都是 `NOT NULL`，而这两个方法只校验 id 字段。
修复前实测：

```
POST /api/import/sessions   → {"errors":1,"errorMessages":["Could not commit JPA transaction"]}
POST /api/import/summaries  → {"errors":1,"errorMessages":["could not execute statement [ERROR: null
                              value in column \"project_path\" ... violates not-null constraint…]"]}
```

**两条消息都没点名 `projectPath`。** 已补校验，返回 `"projectPath is required"`。
正确范式同样就在本仓库内：`importObservations` 早已显式校验 `sessionId` 与 `title`。

**改 API 响应前已核对 WebUI**：`webui/.../DataRoutes.ts` 的 `POST /api/import` 是
**自包含的 worker 路由**，直接写自有 SQLite store（`this.dbManager.getSessionStore()`）、
自带两态 `{imported}` 结果，**从不调用后端这四个端点**——本次改动对 WebUI **零影响**。

**后端单测**：163 个、0 失败、**11 个 error 全在 `TimelineServiceTest`**，即第 149 轮起
记录的既有问题（JDK 24 下 Mockito 无法 mock `EmbeddingService`，失败在
`MockitoExtension.beforeEach`、早于任何断言）。已复核错误文本确为该失败，且本轮只动
`ImportService` 与 `ImportController`，与 `TimelineService` 无交集。

### 文档方向：API 文档

**DOC-1（P1，已修）导入响应三个计数的语义完全没有文档——正是 P1-2 长期存活的土壤。**

`docs/API.md` 对 sessions/summaries/prompts 三个端点只写「Response: Same format as
Import Observations」，**从未说明 `skipped` 与 `errors` 各代表什么**。只检查 `success`
的调用方无法区分「重复跳过（无害）」与「被拒绝（数据丢失）」。

两版均已补：三桶语义表（`imported` 已写入 / `skipped` 是重复、无损失 / `errors` 被拒绝、
`errorMessages` 说明原因）、`success` 只表示请求完成而非每条落地、活体实测矩阵，
以及「请求字段必须是 snake_case，因全局 `SNAKE_CASE` 策略下未绑定的 camelCase 字段
等同缺失」这一踩坑点。

**变更日志**两版各新增一条并标为 **BEHAVIOUR CHANGE**——依赖旧 skip 计数的调用方
会看到不同的数字，这是必须让调用方知道的事。

### 验收

指纹 `31b3d088…` → `d5ce380a…`（Backend 有代码改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `038f93f` / `d5ce380a…`。

`backend-review-findings.md` 同步更新：P1-2 标记为已修复并写入复测矩阵；新增 P2-6 条目
（虽已当场修复，但因改变 API 响应内容而保留可追溯记录）。
**一处计数自查纠错**：我一度把 P2 记为 1，但该列只统计**未处理**条目（⏸/📌），
已修复的 P2-6 不应计入——已改正为 P2=0，并在表下写明该列语义。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）——Backend 现存**唯一** Open 条目。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录，JDK 24 下 Mockito inline 问题）。
3. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
4. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
5. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
6. **Java SDK README 无 Wire Format 段落**（第 164 轮核实发现，待专门一轮补齐）。
7. **JS SDK 的 `examples/` 从未被类型检查**（`tsconfig.json` 只含 `src/**/*`）。
8. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
9. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
10. push 权限阻塞（`wubuku` 403）。
11. 并行巡检进程争写状态文件。
12. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Backend 完成（新循环第十九轮），下一方向 Java SDK；
文档审查轮换推进：API 文档完成（六十七轮），下一方向 SDK README。

---

## 第 167 轮 — 2026-10-02 19:41 — Java SDK + SDK README

### 代码方向：Java SDK

**JAVA-3（P1，已修）三个读方法把失败报成空结果。**

`listObservations` / `getObservationsByIds` / `getProjects` 都是
`catch (Exception e) { …; return 空值; }`——**空结果与「查询确实没有匹配」无法区分**。
Go/Python/JS 三家在这三个方法上都是抛出。而这个吞异常的 catch 正是第 158 轮那个
`ClassCastException` 长期不可见的原因：mapper 抛错 → catch 变成「0 条观察」→
一个实际有 20 条的项目看起来是空的。

**这里有真实的分界，不是随意划分**：另有四个方法
（`retrieveExperiences` / `buildICLPrompt` / `getQualityDistribution` / `healthCheck`）
由 Spring AI 集成在 `@Tool` 方法与自动配置的健康指示器中调用，记忆后端故障**不能**
打断 agent 的对话轮次——它们的降级是**刻意的**，现已在每个方法上写明理由。
而本轮修的这三个**没有 Spring AI 调用方**（已提取 spring-ai 与 starter 模块中
全部 `client.*` 调用逐一核对），对它们而言空结果是谎言。现三个方法都抛出
`RuntimeException`，消息经第 161 轮的 `describe()` 携带后端原文。

**两个既有测试钉死了缺陷，其中一个是本仓库该模式最清晰的样本**：

- `getObservationsByIds_onError_returnsFallback`：enqueue 500 后断言 `isEmpty()`——
  把静默降级以「returnsFallback」之名**表述成有意设计**。
- `listObservations_onError_returnsFallback`：对空页做同样的事。
- `getObservationsByIds_sendsCorrectBody`：夹具用 JSON 数组 `"[]"`，而 SDK 按对象解析
  该响应 → 提取抛错；而该测试**只断言请求、从不检查返回值**，于是异常被正在被修的
  那个 catch 丢弃，测试照样通过。**与第 158 轮同一形状、只是深了一层**：
  夹具形状错误，而失败之所以不可见，正因为代码把失败吞了。

三个测试全部按真实行为重写，且每个「应抛出」的测试都配一个「确实为空时仍返回空而非
抛错」的用例——**这才是让改动安全的关键**。退回验证：三处分别失败 **3 / 1 / 1** 个测试。

Java SDK 180 → **186 全绿**（client 127→133、spring-ai 46、starter 7）。

**核实无误未改**（均先取证再下结论）：

- `ObservationResponse.refinedFromIds` 建模为 `String`、javadoc 写明 comma-separated，
  与第 163 轮确立的 `TEXT` 列一致。活体探测第 166 轮 Go 解析失败的那一页（offset=600）：
  100 条中 12 条带该字段、全部有值，抽样一条展开为 **17 个**逗号分隔 ID，
  `concepts` 正确解码为 `[extraction, user_preference]`。
- **一处怀疑被核实推翻**：12 与早前记录的 61 不符，一度怀疑 SDK 漏发 `offset`。
  实测同一 URL 用 `curl` 现在**也返回 12**——是数据集变了（期间多轮回归与验收新增了
  观察记录），不是 bug。差点把一个不存在的缺陷写进报告。
- `getStats` 正确透传 `project`（Go SDK 在第 145 轮出过的正是这个 bug）。

### 文档方向：SDK README

**DOC-1（P2，已修）Java SDK README 缺 Wire Format 段落**（第 164 轮记录的待办，本轮完成）。
Go/Python/JS 三家都有，唯独 Java 没有，而 Java 侧恰恰有一处最容易误解的建模差异。
EN/ZH 均已补：四个 JSONB 列以 JSON 编码字符串到达、`refinedFromIds` 是逗号分隔的
`TEXT` 列而**非**列表、`@JsonProperty` 的命名映射。

**DOC-2（P1，已修）错误契约完全没有文档，而这正是本轮改动的东西。**

新增 Error Handling 段落（EN/ZH），因为该契约**刻意不统一**，调用方写 `try/catch`
前必须知道某个方法属于哪一类：哪些**向外抛出**（附理由表）、哪些为 Spring AI 路径
**优雅降级**、哪些用 `fell_back`/`error` **标记降级**。四家 README 中只有 Java 需要
这张表，因为只有它的行为是分裂的。

测试数重测：Java README 180 → **186**，patrol-task 基准同步。

### 验收

指纹 `d5ce380a…` → `1d4c7d92…`（Java SDK 有代码改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `f3f7486` / `1d4c7d92…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）——Backend 现存唯一 Open 条目。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（第 149 轮记录，JDK 24 下 Mockito inline 问题）。
3. **AGENTS.md 幻影端点**（第 154 轮记录）——该文件被 gitignore。
4. **三 SDK 一致缺口**（Observation 9 字段、`projectPath`、`/api/projects` V18 字段）。
5. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
6. **JS SDK 的 `examples/` 从未被类型检查**（`tsconfig.json` 只含 `src/**/*`）。
7. **LICENSE 版权归属不一致**（J-2）——仍待用户决策。
8. `docs/drafts/` 三个超 50KB 文件——待明确规范适用范围。
9. push 权限阻塞（`wubuku` 403）。
10. 并行巡检进程争写状态文件。
11. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Java SDK 完成（新循环第二十轮），下一方向 Go SDK；
文档审查轮换推进：SDK README 完成（六十八轮），下一方向 架构文档。

---

## 第 168 轮 — 2026-10-02 20:20 — Go SDK + 架构文档

### 代码方向：Go SDK —— 未发现缺陷

本轮 Go SDK 审查**没有找到缺陷**。四项核实全部通过：

- **查询参数名与后端逐个对拍**：Go 侧每个 query 参数常量与后端 `@RequestParam` 名称一致。
- **四家方法集完全一致**（25 个 API 方法 + 生命周期方法），`/tmp/sdk_methods.py` 对拍通过。
- **`ProjectsResponse` 只建模 `projects`**：V18 新增的 `sources` 四家 SDK 一致未建模 ——
  属一致取舍，单改一家反而制造新的差异面。
- **`Search` 的 `limit`**：后端静默钳制，Go 不做本地校验，与 Python 行为相同。

静态与测试复核（全部重跑，非缓存）：

```
gofmt -l .        → 干净
go vet ./...      → 干净
go test -count=1  → ok (core 230 PASS + dto 67 PASS = 297)
eino 8 / genkit 13 / langchaingo 12 / http-server 示例 13
                  → 合计 343
```

343 与 `patrol-task.md` 的基准完全吻合，README 的 297 口径（client 230 + dto 67）
也准确，**无需更新任何测试数字**。

> 取数教训：`go test ./...` 只覆盖根 module 的 2 个包，而 eino/genkit/langchaingo/http-server
> 是**四个独立 go.mod**。只看根 module 会得出 297 而非 343。已逐 module 用
> `go test -list` 与 `-v | grep -c -- '--- PASS'` 双向核对（http-server 的 13 是
> 2 个顶层函数 + 11 个子测试，恰好解释了顶层与 PASS 行的差额）。

### 文档方向：架构文档

**DOC-1（P2，已修）PostgreSQL 端口写错**（`docs/ARCHITECTURE.md:1100` 与
`docs/ARCHITECTURE-zh-CN.md:1100`）。

Network Security 表格把 PostgreSQL 固定写成 `127.0.0.1:5432`，但实际取决于运行方式。
三个事实依据**全部实地核对**后才改：

| 依据 | 内容 |
|------|------|
| `docker-compose.yml:35` | `"${POSTGRES_PORT:-5433}:5432"` —— Docker 路径宿主端口是 **5433** |
| `README.md:308` | 明写 `5433` 是为避开本地已占用的 5432 |
| `backend/src/main/resources/application.yml:74` | 默认 `jdbc:postgresql://127.0.0.1/claude_mem_dev`，未写端口即 5432 |

现改为两端口并列，并补一段说明：Docker 路径必须用 `SPRING_DATASOURCE_URL` 覆写指向
发布出来的端口，否则后端的 5432 默认值会去找一个并不存在的服务器。

**写作过程中自查出的一处错误**：说明段落最初插在表格的 PostgreSQL 行与 Proxy 行之间，
把表格从中间截断，`| Proxy |` 行会孤立成普通文本。两种语言版本均已重排为
「表格完整 → 段落说明」。

**核实无误未改**：Data Privacy 的四条隐私标签（`<private>` / `<claude-mem-context>` /
`<system_instruction>` / `<system-instruction>` + entirely-private skip）描述与实际
行为逐条相符，**不写即为正确**。

结构校验：`/tmp/arch_parity.py` 全通过 —— 49 个标题、层级序列一致、围栏平衡、
锚点可解析。

### 验收

指纹 `1d4c7d92…`，**与第 167 轮基线完全一致**（记录数 877 未变），且本轮无任何代码改动
→ 按门控规则**跳过完整验收、基线不推进**。这是纯文档轮，重跑完整验收只会复验同一份代码。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）—— Backend 现存唯一 Open 条目。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（JDK 24 下 Mockito inline 无法 mock `EmbeddingService`）。
3. **AGENTS.md 幻影端点**（`GET /api/sessions` 活体 404）—— 该文件被 gitignore。
4. **三 SDK 一致缺口**（Observation 11 字段、`projectPath`、`/api/projects` V18 字段）。
5. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
6. **JS SDK 的 `examples/` 从未被类型检查**（`tsconfig.json` 只含 `src/**/*`）。
7. **LICENSE 版权归属不一致**（J-2）—— 仍待用户决策。
8. `docs/drafts/` 三个超 50KB 文件 —— 待明确规范适用范围。
9. push 权限阻塞（`wubuku` 403）。
10. 并行巡检进程争写状态文件。
11. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Go SDK 完成（新循环第二十一轮），下一方向 Python SDK；
文档审查轮换推进：架构文档 完成（六十九轮），下一方向 用户指南。

---

## 第 169 轮 — 2026-10-02 20:57 — Python SDK + 用户指南

### 代码方向：Python SDK

**PY-1（P1，已修）`_request_json` 把「响应体存在但无法解析」降级成 `None`**，
而 `None` 正是它对「真的没有内容」返回的值。19 个调用点无一例外地用
`data or {}` 或 `isinstance` 兜底，于是**失败与「后端确实没有数据」完全无法区分**。

这不是理论问题：**反向代理或网关失败时返回的正是 200 状态的 HTML 错误页**。
后果具体到可以复现：

- `start_session` 返回一个结构完整、`session_id` 为空的响应对象——调用方接下来整个
  会话都会用这个空 ID，该会话后续所有 observation 都被记到垃圾键下。
- 每个读方法都返回一个结构完整的空对象，而不是报错。

**活体 A/B（真实 HTTP 服务器返回 200 + HTML，三家 SDK 同一输入）**：

| SDK | 行为 |
|-----|------|
| **Python** | **正常返回** `session_id='' session_db_id='' update_files=[]` |
| Go | 抛 `cortex-ce: failed to parse /api/session/start response: invalid character '<'` |
| JS | 抛 `cortex-ce: failed to parse /api/session/start response` |

Java 亦抛（`IllegalStateException: startSession returned null response body`）——
**四家里只有 Python 吞掉**。修复后三家抛出完全同形。

> **探针自身出的两个错，都已识别**（与第 163 轮同类）：JS 探针用 `baseUrl` 而选项名是
> `baseURL`，被静默忽略后回落到默认后端 37777——日志里 `[server]` 一行从未出现、
> 却返回了真实 UUID，正是这个原因；Go 探针的包名与 SDK 根包（`cortexmem`）冲突。
> 若不核对 `[server]` 日志与返回的 UUID 是不是真来自探针服务器，就会把「JS 也返回正常」
> 误报成一个新缺陷——实际上 JS 一直是抛错的。

**204 与零长度响应体仍然不算错误**，各方法保留其文档化的默认值，只有真正的解析失败
才抛。这是让改动安全的关键。

三个测试把旧行为钉成了「期望」，其中一个夹具恰好就是 **200 + HTML 错误页**。已按真实
行为重写，并给每个「应当抛出」的用例配一个「确实为空时仍返回默认值」的配对用例。
**退回验证：4 个测试失败**（正是 4 个「应当抛出」的），4 个「真的为空」用例在退回下
仍绿，说明改动没有波及它们。

Python SDK 389 → **394 全绿**；python-sdk-e2e **28/28**；python-demo-e2e **27/27**。

**核实无误**（先取证再下结论）：

- `CortexError` 已在 `cortex_mem/__init__.py` 的 `__all__` 导出，MRO 确认**不是**
  `APIError` 子类——这正是 README 必须补说明的原因。
- Python Demo **早已注册** `@app.errorhandler(CortexError)`（`examples/http-server/app.py:50`），
  返回结构化 JSON 400，不会出现未捕获的 Flask 500。该 handler 的存在反过来印证
  `CortexError` 就是这类失败的预期异常类型。
- 四家 SDK 的重试状态码集完全一致（`{429, 502, 503, 504}`，均不重试 500）。
- 19 个调用点逐一核对，`data or {}` / `isinstance` 兜底对 204/空体仍成立。
- `_fire_and_forget` 走 `_request_no_content`，不经过 `_request_json`，与重试逻辑无交互。

**DOC（随代码）**：两份 README 新增「Malformed Response Bodies」段——`APIError` 只覆盖
HTTP 状态码，解析失败抛的是 `CortexError`，而既有的 `except APIError` 示例**捕获不到它**。

### 文档方向：用户指南

**DOC-1（P2，已修）PostgreSQL 端口分野未说明**，波及
`docs/TESTING.md` + `docs/TESTING-zh-CN.md` 与 `docs/DEVELOPMENT.md` +
`docs/DEVELOPMENT-zh-CN.md`（这是第 168 轮架构文档那处修正的同源延伸）。

两份指南都假定 PostgreSQL 在 5432——**对原生安装和它们各自给出的
`docker run -p 5432:5432` 而言是正确的**，不是错误陈述。真正的缺口是：项目自己的
`docker compose up -d` 把库发布在宿主机 **5433**（`docker-compose.yml:35`），而两处
都没提。于是用 compose 起步的用户一旦遇到问题，照 TESTING.md 的排障命令会**在 5432
上再起一个空数据库**，而数据在 compose 容器的 5433 上——问题看起来更糟了。

已在 TESTING 的「前置条件」与「PostgreSQL 连接失败」、DEVELOPMENT 的 Docker 替代方案
三处加注（中英双语），并在 TESTING 的变更日志记一笔。

**两处怀疑经核实被推翻，未改**：

- 「10 个本地 E2E 套件」——**属实**，`run-all-e2e.sh` 自身标注 `1/10`–`10/10`；
  变更日志里的「12」指的是第 3 节的脚本表格行数，不是编排套件数。
- `phase3-acceptance-test.sh`「15 test functions」——**属实**，脚本确实定义 15 个
  `test_*` 函数（套件报告的 25 是这 15 个函数下的断言数）。差点改掉两个正确的数字。

### 验收

指纹 `1d4c7d92…` → `f9f44de3…`（Python SDK 有代码改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `216c319` / `f9f44de3…`。文档提交后指纹复测仍为 `f9f44de3…`，
确认纯 `.md` 变更不推进基线。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）—— Backend 现存唯一 Open 条目。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（JDK 24 下 Mockito inline 问题）。
3. **AGENTS.md 幻影端点**（`GET /api/sessions` 活体 404）—— 该文件被 gitignore。
4. **三 SDK 一致缺口**（Observation 11 字段、`projectPath`、`/api/projects` V18 字段）。
5. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
6. **JS SDK 的 `examples/` 从未被类型检查**（`tsconfig.json` 只含 `src/**/*`）。
7. **Python SDK 无响应体大小限制**（四家 README 已显式记录为有意差异，本轮复核仍成立）。
8. **LICENSE 版权归属不一致**（J-2）—— 仍待用户决策。
9. `docs/drafts/` 三个超 50KB 文件 —— 待明确规范适用范围。
10. push 权限阻塞（`wubuku` 403）。
11. 并行巡检进程争写状态文件。
12. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：Python SDK 完成（新循环第二十二轮），下一方向 JS/TS SDK；
文档审查轮换推进：用户指南 完成（七十轮），下一方向 回到 API 文档。

---

## 第 170 轮 — 2026-10-02 21:05 — JS/TS SDK + API 文档

### 代码方向：JS/TS SDK

**JS-1（P2，已修）响应体大小上限用「字符数」而非「字节数」判定。**

同一个 10 MiB 上限有两道检查，却用了两把尺子：读取前用 `Content-Length`（**字节**），
读取后的兜底用 `text.length`（**UTF-16 码元**）。而 README 明写「the cap is 10 MiB —
10,485,760 **bytes**」——多字节响应体可以在码元数上低于上限、字节数上却远超上限。

**活体证明**（对构建产物、无 `Content-Length`、合法 JSON 的 CJK 响应体）：

```
body: 5,242,939 UTF-16 码元, 15,728,699 UTF-8 字节（合法 JSON）
cap : 10,485,760
text.length > cap ?  false   <-- 兜底实际测的
byte length  > cap ? true    <-- README 承诺的

修复前 RESULT: ACCEPTED a 15728699-byte body (1.50x the cap)
修复后 RESULT: REJECTED — cortex-ce: response body exceeds 10MB limit
```

四字节字符（U+1D11E 星平面）在 2:1 的比例下有同样问题，故两类都补了测试。

字节数**靠遍历字符串**得出而非 `TextEncoder`——后者会额外分配一个与响应体等大的
缓冲区，而这里恰恰是内存防护。`text.length * 3 > maxSize` 是零成本早退：UTF-8 每个
码元最多 3 字节，这么短的字符串无论内容如何都够不到字节上限。

**模糊对拍**：把辅助函数与 `TextEncoder` 在 **4029 个字符串**上比对（ASCII、BMP、
孤立代理对、星平面全覆盖）——**0 处不匹配**；且「字节数 > 3×码元数」的样本 **0 个**，
早退前提成立。（第二次检查我先把条件写反了，把恒成立的关系报成 4029 个违规，改正后
为 0——与第 163 轮同样的教训：验证脚本自身出错必须先识别。）

JS SDK 224 → **227 全绿**；**退回验证：2 个测试失败**（两个「应当拒绝」），「应当接受」
的用例保持绿；lint（`tsc --noEmit`）、CJS+ESM+DTS 构建、js-sdk-e2e **27/27** 全部干净。
diff 为纯增量（+107 行、0 删除），**第 164 轮的 `resp.text()` 承重逻辑完全未动**。

**DOC（随代码）**：两份 README 原先把这一点记录为「a slightly lenient bound」——
既与两段之上的字节数自相矛盾，也**低估了实际差距**（实测 1.5 倍，理论可达 3 倍）。
现已改为准确描述字节口径检查。

### 文档方向：API 文档

**DOC-1（P2，已修）`/api/logs` 的 `lines` 钳制行为两版均未记录。**
`LogsController` 中为 `Math.min(Math.max(1, lines), 10000)`，越界值被静默钳制、
**从不返回 400**，而参数表只写了「Maximum lines to return / 默认 1000」。活体实测：

| 请求 | `returnedLines` |
|------|-----------------|
| `?lines=0` | 1 |
| `?lines=-5` | 1 |
| `?lines=50000` | 10000 |
| `?lines=3` | 3 |

同时补：`returnedLines` 永远不超过钳制后的 `lines`；`totalLines` 统计被搜索文件的全部
行数、可能不止一个文件（优先读今天，今天行数不足才回退昨天，`files` 列出实际读取的文件）。

> **一处自我更正**：初稿写了「跨日时 `returnedLines` 可能超过 `lines`」，读控制器后
> 发现不成立——`subList(size - validatedLines, size)` 已将其限制住。**删除而非发布。**

**核实无误未改**（先取证再下结论）：

- API.md 声称的「67 live endpoints」**属实**——从控制器 `@*Mapping` 重新推导正是 67。
- EN/ZH 端点覆盖对称；抽查的 6 个端点中英文均存在。
- `/api/logs` 活体返回的键名 `[files, logs, path, exists, totalLines, returnedLines]`
  与文档示例**完全一致**（驼峰 `totalLines` 确为真实 wire 形态）。
- 第 166 轮加的 `projectPath` 校验与导入三桶语义均已记录（`projectPath is required`
  中英文各 3 处）。

> **端点对拍脚本自身出过两次错**（第 163 轮同类）：方法级 `@RequestMapping` 是**替换**
> 类级路径而非追加（产出 `/api/api` 这类幻影重复）；以及我剥离了代码块——而英文版恰恰
> 把请求行放在代码块里，导致 EN 侧只提取到 34 个而 ZH 侧 84 个。两处都修正后才得到
> 上述结论。差集里的「12 个缺失」经抽查也全部证伪（正则过严，文档把动词与路径分行放置）。

### 验收

指纹 `f9f44de3…` → `ebcc4945…`（JS SDK 有代码改动）→ 完整验收：
`regression-test.sh --skip-build` **45 passed / 0 failed / 1 skipped**；
`EXTRACTION_ENABLED=true phase3-acceptance-test.sh` **25/0/0**。
全通过，基线推进至 `d7a48e0` / `ebcc4945…`。文档提交后指纹复测仍为 `ebcc4945…`。

### 未解决项

1. **P1-1 流式会话不传播**（第 144 轮记录，维持不修）—— Backend 现存唯一 Open 条目。
2. **`TimelineServiceTest` 11 个 error 为既有问题**（JDK 24 下 Mockito inline 问题）。
3. **AGENTS.md 幻影端点**（`GET /api/sessions` 活体 404）—— 该文件被 gitignore。
4. **三 SDK 一致缺口**（Observation 11 字段、`projectPath`、`/api/projects` V18 字段）。
5. **Java Demo 的 `/observations/*` 无 E2E 覆盖**（第 159 轮记录）。
6. **JS SDK 的 `examples/` 从未被类型检查**（`tsconfig.json` 只含 `src/**/*`）。
7. **Python SDK 无响应体大小限制**（四家 README 已显式记录为有意差异，本轮复核仍成立）。
8. **LICENSE 版权归属不一致**（J-2）—— 仍待用户决策。
9. `docs/drafts/` 三个超 50KB 文件 —— 待明确规范适用范围。
10. push 权限阻塞（`wubuku` 403）。
11. 并行巡检进程争写状态文件。
12. `CLAUDE.md` 被 `.gitignore` 忽略。

代码审查轮换推进：JS/TS SDK 完成（新循环第二十三轮），下一方向 Demo；
文档审查轮换推进：API 文档 完成（七十一轮，回到轮换起点），下一方向 SDK README。
