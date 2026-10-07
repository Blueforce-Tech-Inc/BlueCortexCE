# Backend Review Evidence 44 — 第 330/331 轮已结案的整条条目（第 331 轮迁出）

> **归档规则**：承 `-40` / `-41` / `-42` / `-43` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为写入 P2-93 / P2-94 后文件**双阈值同时越线**（实测 **1582 行 / 151365 字节**，
> 行数与字节双双超过 1500 / 150000）而迁出 **3 条**：P2-92（第 330 轮结案）、
> P2-94（本轮结案）、P2-80（第 323 轮结案，正文残余）。
>
> **本批两条新条目是同一种失实的两个面**：P2-92 是**注释**描述了一个构造函数早已防住的缺陷；
> P2-94 是**文档**里的 module 路径多了一节、指向一个不存在的模块。二者的正确值都
> **无需推断**（就在同一文件里 / 就在 `go.mod` 里），所以都能单方面改；
> 这与 P2-87（版本钉，正确值只能推断）形成对照 —— **能不能改，取决于正确值是否被权威确定**。

## 块 1 / 3：P2-80 全文（第 331 轮逐字迁出）

### P2-80: CORS 的 origin 列表**不 trim**——按文档教的「逗号分隔」写，**只有第一个域名生效**
- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/config/WebConfig.java:44-46`
  （修复前的 `allowedOrigins.split(",")`）；指导文档 `docs/drafts/spring-ai-integration-plan.md:138`
  「需配置 `claudemem.cors.allowed-origins`（**多个域名用逗号分隔**）」。
- **Problem**: `String.split(",")` **不会去掉分隔符两侧的空白**，于是第 2 个及之后的元素
  带着前导空格，成为字面量 `" https://b.example"`，与永不带前导空格的 `Origin` 头**永不相等**。
  **这不是报错，是静默失效**：
  **活体实测（37790，配置 `http://a.example, http://b.example`，逗号后一个空格）**：

  | 请求头 `Origin` | 实测 |
  |---|---|
  | `http://a.example` | **200** + `Access-Control-Allow-Origin: http://a.example` + `ACAC: true` |
  | `http://b.example` | **403 "Invalid CORS request"**，**无任何 CORS 头** |

  日志中 `IllegalArgumentException` **0 次**——**与 P2-79 是两种不同机制**
  （那条是每个请求抛异常导致全站 500，这条什么都不抛，只是第二个及以后的域名静默失配）。
  **为什么值得记**：文档教的就是「逗号分隔」，而人在逗号后打一个空格是极自然的写法，
  结果**第一个域名之外的全部失效且无任何线索**。
- **⚠️ 一处探针错，先质疑探针再采信数据**：初版探针用 `curl -o 文件` 后去 grep 响应头，
  读到的永远是空（`-o` 存的是**响应体**、`-D` 才是响应头），
  于是 a.example 一度显示「200 但无 ACAO」。**改用 `-D` 倒原始响应头后**，
  `Access-Control-Allow-Origin: http://a.example` **确实存在**——
  **数据没错，是探针错了**；改正后结论反而更硬（b.example 是确凿的 403）。
- **Status**: ✅ **已修** —— 逐字迁入 [`…-40.md`](../archive/2026-10-07_backend-review-evidence-40.md)（第 323 轮）。
- **⚠️ 第 314 轮复查 2/3：在本修复里发现一条加宽边 → 复查计数重置为 1/3**
  **实测（37790，配置 `' *'`，即星号前有一个空格）**：

  | `Origin` | 实测 |
  |---|---|
  | `http://anything.example` | **200** + `ACAO: *`，`ACAC` 头 **0** 次 |
  | `https://totally-unrelated.example` | **200** + `ACAO: *` |

  **对照修复前的行为**：`origins[0]` 会是字面量 `" *"`，既不等于 `"*"`（故凭据被算成开启），
  列表里又没有字面 `"*"`（故 Spring 不抛异常）——**结果是没有任何 origin 能匹配，对所有来源一律失效**。
  **trim 之后**它变成干净的 `["*"]`，于是走通配分支 → **对全网回显 `ACAO: *`**。
  即：**本修复把一个「什么都不匹配」的输入，变成了一个「什么都匹配」的输入。**
  **这与 P2-79 里那条「直觉修法」落到同一个结果上**（都是关凭据 → 回 `*`），
  **故本条不能独立结案**：trim 对「多源列表」是纯粹的好处，
  但它与「通配 + 凭据」这条策略问题**在边界上交汇**，
  **必须与 P2-79 一并决策**（`allowedOriginPatterns` 或启动期 fail fast）才能收口。
  **今天的实际风险为 0**：该配置项全仓从未被设置（与 P2-78、P2-79 同一条证据）。
- **✅ 第 316 轮复查 3/3：技术验证完成，本条的代码侧结案。**
  补测了前两轮**未覆盖**的两个边界，均正确：

  | 边界 | 配置 | 实测 |
  |---|---|---|
  | **CRLF**（多行环境变量） | `'http://a.example,\r\nhttp://b.example'` | 两个源**各自精确放行**并回**各自的** ACAO；未列出的 **403**；异常 **0** |
  | **退化输入**（只有分隔符与空白） | `' , '` | `parseOrigins` 丢弃空元素后得**空数组** → **CORS 退回全关的安全默认**（任意 origin 403、无 ACAO），普通请求仍 **200** |

  **三轮合计覆盖**：逗号后空格（313）、制表符 + 前后空白 + 空元素 + 通配（315）、
  CRLF + 退化输入（316）。**`parseOrigins` 在所有非通配输入上行为正确**。
  **仍然开放的不是本条的技术问题**，而是上条那条**通配 + 凭据的策略决策**（与 P2-79 合并待决）。
  **据此本条代码侧结案；若日后决定改通配策略，需连带复看本条的加宽边。**

## 块 2 / 3：P2-92 全文（第 331 轮逐字迁出）

### P2-92: `CortexMemoryTools` 的注释说 `defaultCount` **未被钳制**，而它上面两行的构造函数**恰恰钳了**
- **Scope / Evidence**:
  `cortex-mem-spring-integration/cortex-mem-spring-ai/src/main/java/com/ablueforce/cortexce/ai/tools/CortexMemoryTools.java`
  —— 注释 `:66-71`，被它描述的那行代码 `:46`，以及被它断言的分叉 `:72`
- **矛盾就在同一个类的 6 行之内**:
  ```java
  // :46  构造函数
  this.defaultCount = Math.max(1, Math.min(defaultCount, 10));   // ← 钳到 [1, 10]
  ...
  // :66-71  注释却写
  // "`defaultCount` itself is not clamped, so configuring
  //  cortex.mem.default-experience-count above 10 means a call that omits `count` asks for
  //  more than the range this parameter advertises. ... the two branches do not agree."
  // :72
  int effectiveCount = (count == null || count <= 0) ? defaultCount : Math.min(count, 10);
  ```
  注释描述的场景**在代码里不可能发生**：`:46` 已经把 `defaultCount` 钳进 `[1, 10]`，
  省略 `count` 时最坏也只发 **10**，落在 `@ToolParam` 宣称的 `1-10` 之内。
  **两条分支实际是一致的**，注释说的「不一致」不存在。
- **配置链路（确认钳制只发生在这一处，没有别处再放开）**:
  `CortexMemProperties.defaultExperienceCount`（默认 `4`，setter **不做任何钳制**）
  → `CortexMemAutoConfiguration:121-123` 原样传入 → `CortexMemoryTools:46` 钳制。
- **受控实验**（临时 `CortexMemoryTools` + Mockito `CortexMemClient` 探针，捕获真正上线的
  `ExperienceRequest.count()`；测完即删）：

  | 配置的 defaultCount | 模型给的 count | **实际发出** |
  |---|---|---|
  | 20 | 省略 | **10**（不是 20） |
  | 10 | 省略 | 10 |
  | 3 | 省略 | 3 |
  | -5 | 省略 | **1** |
  | 20 | 99 | 10 |
  | 20 | 0 | 10 |

  六行全部落在 `1-10`，**没有任何一行越界**，与注释所称的缺陷相反。
- **为什么可以单方面改**（判据：**失实陈述 + 正确值被权威确定**）:
  正确值就在同一文件的 `:46`，并已由上表实测坐实；改动**只涉及注释，零行为变化**。
  与 P2-87（版本钉，正确值只能推断）不同，本条不存在需要推断的成分。
- **一处顺带核实**：Java SDK README 对该配置项的描述是中性的
  （`README.md:194` / `README-zh-CN.md:201` 均只写「Max experiences per retrieval」/
  「每次检索的最大经验数」），**未复述这条错误断言**，故文档侧无需改动。
- **两处探针自身错误，均在采信前识别**:
  ①`ExperienceRequest` 是 **record**，访问器是 `count()` 而非 `getCount()`，首版编译失败；
  ②首版 `capturedCount` 把 mock 建好却**没有交给 tools**，却去 verify 那个 mock，
  Mockito 报「Wanted but not invoked … Actually, there were zero interactions with this mock」——
  读 surefire 报告确认后才改，**没有把探针报错当成产品缺陷**。
- **Severity**: 低（纯注释失实，不影响行为；但它是**给下一个维护者设的误导性路标**，
  且描述的是一个已不存在的缺陷）。
- **Status**: ✅ **已修（第 330 轮，零行为变更）** —— 注释改为如实描述两条分叉都由 `[1,10]`
  钳制，并附上上表的实测数字。`mvn test` 全绿（exit 0）。

## 块 3 / 3：P2-94 全文（第 331 轮逐字迁出）

### P2-94: `go-sdk-design.md` 的 module 路径**多了一节** —— 45 处指向一个不存在的模块，Quick Start 照抄必然失败
- **Scope / Evidence**: `docs/drafts/go-sdk-design.md`，共 **45 处**
  （Quick Start 的 `go get` 与两条 import 在 `:118/:132-133`；目录树 `:23,:238,:252,:256,:260`；
  模块布局图 `:810-813`；其余散于 §3、附录 F/K/P/Q 等代码示例）。
- **错误形态**：`github.com/Blueforce-Tech-Inc/BlueCortexCE/go-sdk/cortex-mem-go` 被写成
  `…/go-sdk/cortex-mem-go/**cortex-mem-go**`，**末节重复**。
- **权威正确值**：`go.mod` 的 `module` 行即模块身份，四份 go.mod 全部是**单节**：
  `…/go-sdk/cortex-mem-go`，其三个子模块为 `…/cortex-mem-go/{eino,genkit,langchaingo}`。
  全文 `grep '^module .*cortex-mem-go/cortex-mem-go' --include=go.mod` **零命中**，
  `go-sdk/cortex-mem-go/cortex-mem-go/` 目录**不存在**。
- **受控实验（含负对照）**：
  建两个临时探针模块，`replace` 指向同一真实目录：

  | 探针 | import 路径 | `go build ./...` |
  |---|---|---|
  | **A（设计文档写法）** | `…/cortex-mem-go/cortex-mem-go` | **失败**，EXIT=1 —— `no required module provides package …/go-sdk/cortex-mem-go/dto` |
  | **B（正确写法，负对照）** | `…/go-sdk/cortex-mem-go` | **成功**，EXIT=0 |

  A 之所以能被 Go 接受，**纯粹因为探针自己写了 `replace` 把它伪造成一个模块**；
  `go list -m all` 显示的也正是这条 replace。去掉 replace 它就不存在。
  注意 A 的报错信息本身就很说明问题：SDK **自己的内部 import 是单节的**，
  所以一旦外部按双节引入，SDK 立刻无法解析自己的 `dto` 包。
- **危害等级高于一般的排版错**：这不是示意片段，是**可复制执行的安装命令**。
  照 `go get …/cortex-mem-go/cortex-mem-go` 执行必然 404/找不到模块。
  且本仓**有前车之鉴**：第 202 轮归档记录的 G-1 正是「`go.mod` 已更新为新路径，
  但内部 import 仍用旧路径，SDK 无法作为独立模块构建」——**同一类错误已经真实伤过一次**。
- **为什么可以单方面改**（判据：**失实陈述 + 正确值被权威确定**）：
  正确值就在 `go.mod` 里，且已由上表的对照实验坐实；改动**纯文档，零行为变化**。
  **45 处是同一次机械替换**（`go-sdk/cortex-mem-go/cortex-mem-go` → `go-sdk/cortex-mem-go`），
  `git diff` 复核：45 增 45 删，**不含该 token 的行改动数为 0**。
  改后 `grep -c` 归零，三处可执行行与 README `:18,:31-32` **逐字一致**。
- **同轮另两处，均为小改**：
  ①执行摘要目录树把 `genkit/` 标为「**Genkit 插件（预留）**」，而它**早已实现**：
  `genkit/retriever.go` + 13 个测试，`go test ./...` 全过 → 改为如实描述。
  ②§2 目录树与磁盘有 6 处文件名分化、5 处漏列（逐项核实后列进日期注记）。
  **文件名刻意不改写**——规划与实现的差异属历史记录，按纪律只加注。
- **顺带核实为真**：核心包 `go.mod` **无任何 `require`**，故执行摘要「零强制依赖、
  核心包只依赖 Go 标准库」成立。「Phase 1 封装 15 个核心方法」是**计划表**里的
  Phase 1 交付口径（现接口 27 个方法），**不属失实陈述**，未动。
- **Severity**: 中（纯文档，但可执行且必然失败；同类错误在本仓有前例）。
- **Status**: ✅ **已修（第 331 轮，纯文档零行为变更）** —— 45 处路径已改正；
  §5.4 的「CI 集成」那段 workflow 经核实**至今不存在**（`.github/workflows/` 只有 `docker.yml`），
  已加日期注记说明，但**是否接入 CI 属待作者决定事项，不代为实现、也不删改该节**。

