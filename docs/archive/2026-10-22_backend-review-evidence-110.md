# 后端审查证据归档 -110（2026-10-22）

> 第 409 轮（Go SDK 代码方向 + 架构文档文档方向，doc round 308）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1343 行 / 149615 字节**）。
> 本文档体量：**190 行 / 17213 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。
> 头部数字用**解析求解** `D = base + len(str(D))` 得到并与磁盘实测比对（逐位迭代每轮漂移 1 字节、永不收敛，故不用）。

## 本轮新写的一条（正文逐字保留在本归档）

### P2-215: Go SDK 的 **19** 个出站 query 槽里 **2** 个改名**完全不可见** —— 而 `search.query` 恰是搜索词本身

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: Go SDK 在 `client_methods.go` 里用手写字符串拼 query 串，共 **19** 个槽、分布在 **8** 个端点：
  `search`{project,query,type,concept,source,limit,offset,orderBy}、
  `observations`{project,offset,limit}、`refine`{project}、`quality-distribution`{project}、
  `extraction/run`{projectPath}、`extraction latest`{projectPath,userId}、
  `extraction history`{projectPath,limit,userId}、`stats`{project}。
  旧测试对这些名字的断言形态是**逐参数取值比较**（`q.Get("project") != "/my-proj"`），
  这比 Java SDK 的 `contains("project=")` **更敏感**（改名会让取值为空 ⇒ 必然变红），
  但它**只覆盖被写下来的那些名字** ⇒ 覆盖面仍是缺口。
  ⇒ 症状与 P2-209（JS SDK）、P2-212（demo）、P2-213（后端）、P2-214（Java SDK）**同族**：
  参数名写错**不报错**，后端只绑定它声明过的名字，那个参数**静默失效**。
- **实测（修复前）：一次变异全部 19 槽、每槽用独立新名（`xZZ1`…`xZZ19`），一次跑出全部结论。**
  手法说明：Go 的 `t.Errorf` **不中止**测试，且每个端点各有自己的测试 ⇒
  一个端点的断言失败**不会掩盖**另一个端点的结论 ⇒ 一轮即可同时得到「哪些槽被检出」与「哪些槽无任何测试提及」。
  ⇒ **17 槽被检出**（refine.project、search 的 project/source/limit/offset/type/concept、
  observations 的 project/limit/offset、quality-distribution.project、extraction-run.projectPath、
  latest 的 projectPath/userId、history 的 projectPath/limit/userId）；
  ⇒ **2 槽完全不可见**：
  - **`search.query`** —— 全测试树里唯一的断言是 `TestSearch_OmitsEmptyParams` 里的
    `q.Get("query") != ""`，即一条**反向**断言；**反向断言对改名完全免疫**
    （改名后 `q.Get("query")` 依然为空 ⇒ 断言照样通过）。
    而 `query` 恰恰是**搜索词本身** —— 它若被改名，SDK 会静默地不搜索。
  - **`search.orderBy`** —— 该名字在**整个 Go SDK 测试树里零提及**，
    连它来自的 DTO 字段 `OrderBy` 都**从未被任何测试赋过值** ⇒ 该分支从未被走到过。
- **修法**：新增 `query_params_test.go`（**12** 例）—— 8 个端点的**完整 query 键集合**
  （**19** 槽，按**集合相等**比对：**改名会被发现，多发的参数也会被发现**）＋
  2 个**条件省略族**（search 省略可选字段；observations 的零值 limit/offset 与 latest 的空 userId 不发）＋
  覆盖下限（8 个端点 / 至少 19 个槽）＋ **可证伪自检**（比较必须能区分「改名」「多一个键」「顺序不同」）。
  每一例同时断言 **HTTP 方法**与**路径**（第 408 轮 Java SDK 那次「标签不是测量」的教训直接应用）。
  期望值**手写**（从被测物自身推导 ⇒ 恒真断言），并与活体 OpenAPI spec 逐槽核对。
  ⇒ Go SDK 套件：core **340 → 352**（+12），全仓合计 **477 → 489**。
- **负控（只改那 2 个旧套件看不见的槽）**：把 `search.query` → `xZZ2`、`search.orderBy` → `xZZ8`
  ⇒ **整套件恰好 2 红，且两红都是新测试**
  （`TestSearch_SendsExactlyTheDocumentedQueryKeys` 与 `TestOptionalQueryParamsAreOmittedRatherThanSentEmpty`），
  报错精确到
  `query keys of GET /api/search drifted: expected [concept limit offset orderBy project query source type], got [concept limit offset project source type xZZ2 xZZ8]`；
  **旧测试零反应** ⇒ 旧套件对这 2 槽**完全失明**。变异按字节还原（`grep -c xZZ` = **0**）。
- **连带发现（已修，属同一族）**：四家 SDK README 发布的测试总数**全部陈旧**，
  违反了 **P2-90** 定的「凡新增或删除测试的提交，必须同批更新这几处」规则 ——
  第 403 轮（Go）、第 405 轮（JS）、第 408 轮（Java）各自加了测试，却都**没有**同批更新发布值。
  逐条用**实测**数字核对并修正（双语各一处或三处）：
  - **Go SDK README**：`441` → **`489`**、`371` → **`419`**、`core 304` → **`core 352`**（各 3 处）；
    实测口径：9 个 module 逐个 `go test -v` 计数（core 352 + dto 67 + eino 8 + genkit 13 + langchaingo 12 +
    `examples/http-server` 37 = 489；根模块 `go test ./...` 见 419 = core + dto）。
  - **JS SDK README**：`388` → **`395`**；分解补上漏掉的 `+ 7 query-parameters`
    （vitest 逐文件实测：255 client + 97 required-arguments + 15 wire-format + 5 truncated-body +
    **7 query-params** + 8 http-server example + 5 error-contract + 3 surface = 395）。
  - **Java SDK README**：`309` → **`326`**、`256 client` → **`273 client`**
    （surefire 逐类 `tests=` 求和：client 273 + spring-ai 46 + starter 7 = 326）。
  - **Python SDK README**：`483` → **`487`**；分解补上漏掉的 `+ 4 wire 格式`
    （`pytest --collect-only`：231 client + 140 DTO + 89 demo + 11 error-contract + 12 required-arguments +
    **4 wire-format** = 487）。
  ⇒ **顺带核实过、确认不需要改的两处**：`docs/drafts/patrol-task.md` 的「已完成实施基准」块
  明写「数字为 **2026-10-03** 实测」⇒ **带日期的快照**，按既定规则不算失实陈述；
  架构文档的索引节只列 **9** 个 `CREATE INDEX`（迁移共建 **50** 个）⇒ 该节是**示意性**列举而非穷举
  （读其措辞与注释后确认），故**不**扩写 41 个索引。
- **Status**: ✅ **已修（409 轮，Go SDK 侧，只加测试 + 改 README 计数；SDK 主源码零改动）**。

## P2-87 结转：架构文档的「后端参考提交」**当时无法判断意图**，本轮拿到决定性证据

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem（沿用原文）**: 架构文档声明后端与 `webui/` 子模块是**版本配对**关系，并点名两个提交；
  但其中「后端参考提交」在**任何地方都解析不到**，于是「版本配对声明半边失效」。
  该条此前记为 ⏸，理由是：**无法判断哪个提交才是意图**（写错一个编号的含义比标出一个冲突更糟）。
- **本轮证据（六道，逐条可复核）**：
  1. `ed37a1b227b6067befe5c9ada331989d02cbfad0` 在**父仓与 `webui/` 子模块仓里都不存在**
     （两处 `git cat-file -t` 皆 `could not get object info`）⇒ **悬空引用**，不是「在另一个仓里」。
  2. 文档点名的 webui 提交 `72e7804b13f89b03177f746867e0dd341fb12c8a` **能解析**
     —— 它是 `webui/` 的 **gitlink**（`git ls-tree HEAD` 显示 mode **160000**），
     在子模块仓里存在（`72e7804b merge: sync upstream and align Java Viewer`）。
     ⇒ **此前把它与后端那个一起记成「解析不到」是不准确的**：webui 那一半一直是好的。
  3. 引入该 gitlink 的父仓提交是 **`6e5890d`**（`git log -S '72e7804b…' -- webui` 唯一命中）。
  4. **`6e5890d` 正是写下该段落的那次提交**（`git log -S 'ed37a1b2…' -- docs/ARCHITECTURE.md` 唯一命中它），
     且它改了 **21** 个后端文件（含新建 `V19__viewer_session_indexes.sql`）⇒ 它**就是**该配对里的后端一侧。
  5. 文档自己写明「The parent repository must record that WebUI gitlink **together with the backend changes**」
     ⇒ 后端参考提交**按定义**就是记录该 gitlink 的那次提交 ⇒ 指向 `6e5890d`。
  6. `6e5890d` 是 `HEAD` 的祖先（`git merge-base --is-ancestor` 通过）。
- **修法**：双语各一处，把后端参考提交改为 `6e5890d…`（全称 `6e5890dac5a3f50876d67ad3d60d530657809763`）。
  修后**两份文档里的两个 hash 各自在它所属的仓库里都能解析**（后端那个在父仓、webui 那个在子模块仓），
  「版本配对」这句话从此**可被工具验证**，不再只靠人读。
- **Status**: ✅ **已修（409 轮，文档方向，架构文档双语）**。

## 本轮文档方向另修的一处：架构文档的迁移清单漏了 **V19**（双语共 8 处）

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: `docs/ARCHITECTURE.md` 与 `docs/ARCHITECTURE-zh-CN.md` 的「PostgreSQL + pgvector →
  Schema Overview」段，为五张表各列一行「该表由哪些迁移产生」。磁盘上已有 **V19**
  （`V19__viewer_session_indexes.sql`，由 `6e5890d` 新建），它为四张表各建了一个索引，
  而四行清单**全部停在 V18** ⇒ 现在时断言与磁盘不符（**失实陈述**）。
- **判定方法（按断言，不靠眼估）**：脚本对**每个**迁移文件判定它是否**修改**某张表 ——
  `CREATE TABLE` / `ALTER TABLE` / `DROP TABLE` / `CREATE INDEX … ON <表>` 算**修改**，
  而**外键引用**（如 V8 里 `FOREIGN KEY … REFERENCES mem_sessions(memory_session_id)`）**不算**。
  ⇒ 该规则很关键：第一版把「出现表名」当成「修改该表」，会误判 V8 改了会话表。
  ⇒ 归纳成一句就是「**CREATE/ALTER/DROP TABLE 或 CREATE INDEX ON 该表**」才算**修改**、**外键引用不算**。
  逐表实测结果：
  | 表 | 文档写的 | 实际 | 结论 |
  |---|---|---|---|
  | `mem_sessions` | V1 + V4, V11, V12, V13, V15, V18 | V1 + V4, V11, V12, V13, V15, V18, **V19** | 漏 V19 |
  | `mem_observations` | V1 + V2, V7, V8, V11, V12, V13, V14, V16, V17, V18 | 同上 + **V19** | 漏 V19 |
  | `mem_summaries` | V1 + V13, V18 | V1 + V13, V18, **V19** | 漏 V19 |
  | `mem_user_prompts` | V1 + V5, V18 | V1 + V5, V18, **V19** | 漏 V19 |
  | `mem_pending_messages` | V1 + V3, V6 | V1 + V3, V6 | ✅ 正确 |
- **修法**：四张表的清单各补 `, V19`，**双语共 8 处**；脚本带「原文出现次数必须恰好为 1」的断言，
  改完再扫一遍「仍有清单停在 V18」⇒ **0** 处。
- **Status**: ✅ **已修（409 轮，文档方向，架构文档双语）**。

## 本轮核实**未改**的几处（读上下文后的定性，避免把示意当缺失）

- 架构文档的 **Java 21+ Features Used** 块（`docs/ARCHITECTURE.md:955` 起）：
  `dto/ApiRequests.java` 的 `ToolUseRequest` 逐字核对 —— **9** 个组件、
  `@JsonProperty` 名字与顺序（`session_id` / `cwd` / `tool_name` / `tool_input` / `tool_response` /
  `platform_source` / `source` / `extractedData` / `prompt_number`）与文档片段**完全一致**；
  `dto/OffsetPageRequest.java:107-114` 的模式匹配 —— `instanceof Pageable that` 实际在 **109** 行，
  落在所引区间内 ⇒ **两处引用均属实**（P2-208 的更正成立）。
- 架构文档声明的 `ContextController → /api/context/* (7 endpoints incl. /semantic)`：
  活体 spec 的 `/api/context/*` 恰为 **7** 条（generate / inject / preview / prior-messages / recent /
  semantic / timeline）⇒ **属实**。
- 架构文档的 EN / ZH **结构对齐**：两份各 **55** 个标题，**标题深度序列逐项相同**；
  围栏数 **44** 与 **42**，均**偶数**（平衡）。
- 索引节只列 **9** 个 `CREATE INDEX`（迁移共建 **50** 个）⇒ 该节是**示意性**列举
  （其注释形如「-- Vector indexes (HNSW, V2)」）⇒ **不**构成失实陈述 ⇒ **不**扩写。
- `docs/drafts/patrol-task.md` 的「已完成实施基准」块明写「数字为 **2026-10-03** 实测」
  ⇒ **带日期的快照**，按既定规则**不算失实陈述** ⇒ **不改**
  （改掉反而会毁掉它刻意保留的、逐轮记录每个数字如何演进的轨迹）。

## 本轮的验收

- **Go SDK 套件**：core **340 → 352**（+12）；根模块 `go test ./...` **419**（= core + dto）；
  全仓逐 module 合计 **489**（core **352** + dto **67** + eino **8** + genkit **13** + langchaingo **12** +
  `examples/http-server` **37**）⇒ 上一轮的「477 → 489」口径成立。
- **四家 SDK 发布测试数（本轮收尾时逐家复测，与本归档所写完全一致）**：
  - Go **489** / core **352**（口径：9 个 module 逐个 `go test -v` 计数 `=== RUN`）；
  - JS **395**（vitest 逐文件实测：client **255** + required-arguments **97** + wire-format **15** +
    truncated-body **5** + query-params **7** + `examples/http-server/parse-int-param` **8** +
    `error-contract` **5** + `surface` **3**）；
  - Java **326**（surefire 逐类 `tests=` 求和：client **273** + spring-ai **46** + starter **7**）；
  - Python **487**（`pytest --collect-only`：client **231** + DTO **140** + demo **89** + error-contract **11** +
    required-arguments **12** + wire-format **4**）。
- **负控（单变量）**：只把 `search.query` → `xZZ2`、`search.orderBy` → `xZZ8` ⇒ 整套件**恰好 2 红**，
  且**两红都是新测试**（`TestSearch_SendsExactlyTheDocumentedQueryKeys` 与
  `TestOptionalQueryParamsAreOmittedRatherThanSentEmpty`），旧测试**零反应**；
  变异已按字节还原（`grep -c xZZ` = **0**）。
- **回归**：`SERVER_URL=http://127.0.0.1:37790 bash scripts/regression-test.sh --skip-build`
  ⇒ **46** 通过 / **0** 失败 / **1** 跳过（总 **47**），exit **0**。
- **Phase3**：`BACKEND_URL=http://127.0.0.1:37790 EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`
  ⇒ **25 / 0 / 0**，exit **0**。
- **新鲜度闸门**：本轮验证用后端跑在 **37790**（pid **21484**，本轮新起）；
  `find backend/src -newer backend/target/cortex-ce-0.1.0-beta.jar` 为**空** ⇒ jar 比全部后端源码都新。
- **指纹**：`5d7d91d3…` → **`30f89d710ac1438aaa26e9dc53cb0381677c18d281a7ac5f767d4e283bbd0829`**，
  记录数 **2248 → 2249**（新增 `go-sdk/cortex-mem-go/query_params_test.go` 一个文件）。
- **未受影响**：后端主源码**零改动**（本轮代码方向只动 Go SDK 的测试文件与四家 README 的计数）；
  后端套件仍 **196 / 196**。

## 本轮的压缩记账

- findings 终值：**1343 行 / 149615 字节**（余量 **385 字节**；阈值 **150000** 字节 / **1500** 行，**未改**）。
- **C8 本轮确认枯竭**（不是「接近枯竭」）：本轮的测量脚本读出「可迁余量 **8519** 字节」，
  但脚本的分类器把**决策推理**误判成了证据类 bullet ⇒ 该读数**不可信**。
  ⇒ 改为**逐条人工判定**全部 **17** 条候选（**21** 个 bullet 块，字节由脚本逐块实测），真实构成如下：

  | 类别 | 涉及条目 | 字节 | 处置 |
  |---|---|---|---|
  | **决策推理**（C8 规则必须留在工作文件） | P2-55 / P2-57 / P2-58 / P2-60 / P2-79 / P2-95 | **3147** | 不可迁（`- 不修的理由:` / `- 不单方面改` / `- ⚠️ 为什么不能顺手改:` / `- 修法是明确且零信息损失的（EN）:`） |
  | **追加式审计标注与更正**（只可标注、不可改写） | P2-75 / P2-77 / P2-82 / P2-115 / P2-211 | **4534** | 不可迁 |
  | **已经是指针行**（内容早就在归档里） | P2-21 / P2-39 / P2-40 / P2-41 / P2-45 / P2-57 | **633** | 无内容可迁 |
  | 仅剩的少量散文 | P2-44 / P2-60 | **205** | P2-44 那行自述「全文见归档」、P2-60 那行是 Status 类事实 ⇒ 实际可迁 **≈ 0** |
  | **合计** | **17** 条 | **8519** | **可迁 ≈ 0 ⇒ C8 枯竭** |

  ⇒ 本轮**未执行** C8 迁移：迁移脚本已写好但**未运行**，它的三个目标（P2-57 / P2-79 / P2-55）
  经判定**全是「不修的理由」类决策推理**，迁走就违反 C8 规则。
- 压缩额度由第 **292 / 293** 轮杠杆提供：
  ① 缩短已结案条目的 Status 行 —— **P2-212 / P2-213 / P2-215 / P2-216** 四条
  （每条新行由**原行的逐字切片**拼成：括号内的**全部** token 与**全部**归档链接逐字保留，只删尾部描述性散文）；
  ② 按同一「只删尾部散文、事实 token 逐字可断言」的原则，缩短**本轮自己写的** P2-87 第 409 轮标注。
- **事实 token 逐字校验**：缩短后各行保留的**反引号 / 引号 / 数字** token，逐个断言**逐字存在于该行链接的归档**里；
  比对前**先摘掉链接目标**（否则归档文件名里的日期数字会变成假 token）。
- **硬证明四件套**（沿用第 378 轮）：条目标题数不变、归档链接数不变、行数不变、字节变化可复算。
