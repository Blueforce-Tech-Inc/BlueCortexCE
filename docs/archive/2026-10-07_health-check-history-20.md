# Health Check History 20 — 第 307–311 轮巡检报告（第 321 轮迁出）

> **归档规则**：巡检报告按轮次保留；达增长阈值时把最早的若干轮逐字迁入此处。
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/health-check-task.md` 逼近 `MAX_LINES=1500` 而**提前**迁出
> **5 轮**（第 307–311 轮）：写入第 321 轮报告后已达 1445 行、
> 仅余 55 行余量，下一轮报告必然越限，故在本轮一并迁出而非留给下一轮仓促处理。
> 工作文件保留第 312 轮起。

## 第 307 轮 — 2026-10-06T15:40:00+08:00

代码方向：**Go SDK**（零缺陷）；文档方向：**SDK README**（doc round 207，零缺陷）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致，工作区干净。
doc-growth 五份全 OK（health-check 压缩后为 1265 行 / 78152 字节，退出 0）。

### 代码方向（Go SDK）—— 上下文取消：实测真的中止，且**从未到达服务端**

本轮取 Go 特有的角度：**调用方 ctx 是否真的一路传到底**。

- **全包只有 1 处 `http.NewRequestWithContext(ctx, …)`**（`client_impl.go:219`），
  **0 处 `http.NewRequest`**（不带 context 的那个）——同样是单点漏斗。
- **0 处 `context.Background()` / `context.TODO()`**——没有任何地方偷偷换成背景上下文。
- `Client` 接口共 **27 个方法**，其中 **25 个首个参数就是 ctx**，
  只有 `Close()` 与 `String()` 不是——**生命周期/调试方法，本就无 HTTP**，与第 161 轮的记录吻合。

**但静态结论不能只靠读码**，写了临时 Go 测试实测（已删）：

| 场景 | 结果 |
|---|---|
| **预先取消**的 ctx | 返回 `context canceled`，且**服务端 handler 被触达 0 次** |
| **对照组**：同一 stub、同一路径、未取消的 ctx | **成功**，返回 0 items |

**对照组是关键**：它证明前者的失败确实归因于取消，**而不是 stub、路径或响应形态有问题**——
弱版本只断言「取消时报错」会被一个「stub 本身就不通」的实现蒙混通过。
**请求在离开客户端前就被中止，服务端一次都没被触达。零缺陷。**

**探针错一处**：第一版测试把参数类型猜成 `SearchRequest`、响应字段猜成 `Count`，
编译直接失败；查 `client.go:41` 得知真实签名是 `dto.ObservationsRequest` /
`dto.ObservationsResponse{Items, HasMore, Total, Offset, Limit}` 才改正。
**编译失败是探针错的最早信号**，没有据此下任何结论。

### 文档方向（SDK README，doc round 207）—— 零缺陷

第 193 轮做过 Python+Go、第 196 轮做过 Java+JS、第 202 轮做过错误面，
本轮取**方法清单的可证伪计数**。

**①Go SDK README 的「25 methods」精确成立**
接口 27 个方法中，**恰好 25 个带 ctx**（即 API 方法），
而 **25 个全部被 README 提及、零遗漏**；`Close()` 另有专段（`:121`、`:126`）说明它只释放空闲连接，
`String()` 亦有交代。**「Full API coverage — 25 methods」既没夸大也没漏报。**

**②中文版同样准确**：「25 个方法」，**25 个全被提及、零遗漏**；
**H2 数量 EN 11 / ZH 11 对齐**（行数 417 vs 386，差在正文详略）。

**本轮零改动（README 本身）。**

### 变更检测

本轮**未改任何代码**（临时测试已删）。指纹仍 `0f0ca2c7…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Python SDK**；文档方向：**设计文档**（doc round 208）。

## 第 308 轮 — 2026-10-06T16:15:00+08:00

代码方向：**Python SDK**（零缺陷）；文档方向：**设计文档**（doc round 208，零缺陷）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致，工作区干净。doc-growth 全 OK。

### 代码方向（Python SDK）—— session 归属：实测正确，且我的断言先错了

本轮查**资源归属**：调用方自带 `requests.Session` 时，`client.close()` 会不会把它关掉。

**读码即规范**：`session is not None` → `_owns_session = False`，否则 `True`（`:92-97`）；
`close()`（`:817`）**只在 `_owns_session` 为真时**关底层；
`:100` 的注释还专门解释了为何 per-request header **不写进 `session.headers`**
（调用方的 session 与其全部其它流量共享）。

**实测**（双向，且两侧都断言）：

| 场景 | 实测 |
|---|---|
| 调用方自带 session → `client.close()` | 底层 `.close()` 被调 **0 次**，且该 session **仍可用**（200）、自定义 header 保留 |
| 客户端自建 session → `client.close()` | 底层 `.close()` 被调 **1 次** |
| 客户端已关闭后再调方法 | 抛 `CortexError` ✓ |
| 连续两次 `close()` | 底层被调 **2 次** |

**探针错，且是断言先错**：第一版我用「关闭后再用该 session 应抛异常」来判定「有没有关」。
**这个判据本身不成立**——`requests.Session.close()` 只丢弃连接池，Session 对象**照常重建新池**。
**一个完全正确的实现也会被我的断言判成失败。** 改用间谍对象直接验「`close()` 有没有被调用」后一次通过。
第四行（两次 `close()` → 底层 2 次）随之浮现，**但这不构成缺陷**：
另行实测 `requests.Session.close()` **连关 3 次也不报错、且随后请求照常 200**——重复调用无害。
**记为观察，不判缺陷**（加一行 `and not self._closed` 即可，但那是洁癖不是 bug）。

### 文档方向（设计文档，doc round 208）—— 实施计划的验收计数，逐条对上

`docs/drafts/phase-3-design/25.md`（实施计划）Step 11 写着一句可证伪的声明：

> This script is the **definition of done** — all **12** tests must pass.
> (Note: `phase3-acceptance-test.sh` has **15** tests covering the full acceptance test suite.)

**①12 与 15 两个数字分别对应两个脚本，逐个核实**：
- `demo-v15-extraction-test.sh` 的 test 函数**恰好 12 个**（我的首次 grep 数出 13，
  因为**把 `cleanup_test_data()` 也算进去了**——**探针错**；13 − 1 个清理函数 = 12）✓
- `phase3-acceptance-test.sh` 的 test 函数**恰好 15 个** ✓

**②25.md 有 13 个「Acceptance Test」小节，比脚本多 1 个——查清后并不矛盾**：
第 13 个是「SDK Demo Integration」，内容是**针对 demo 的 curl 片段与人工核对**，
**本就不是脚本里的函数**；而 25.md **从未声称脚本有 13 个**，它说的就是「12」。
**声明与实现一致，13 个小节是「计划条目数」、12 是「脚本函数数」，两个口径各自成立。**

**本轮零改动（设计文档本身）。**

### 变更检测

本轮**未改任何代码**（临时探针已删）。指纹仍 `0f0ca2c7…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**JS/TS SDK**；文档方向：**架构文档**（doc round 209）。

## 第 309 轮 — 2026-10-06T16:50:00+08:00

代码方向：**JS/TS SDK**（零缺陷）；文档方向：**架构文档**（doc round 209，零缺陷）

**本轮最重要的一件事发生在取样阶段**：我差点把一条**早已记录**的幻影端点当成新发现再写一遍，
而我引用的「证据」并不来自磁盘上的文件。

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致，工作区干净。doc-growth 全 OK。

### 代码方向（JS/TS SDK）—— 并发：20 个请求同时在飞，无一串话

实例级可变状态**只有 `private closed = false`**，其余全是函数内局部变量；`doFetch` 内也没有共享对象被改。
**但并发正确性不能只靠读码**，写了临时 vitest（已删）：

| 实验 | 结果 |
|---|---|
| **20 个请求并发**，各带**不同 query**，且**故意让完成顺序与发出顺序不同** | 每个都拿到**自己那份**响应，20 个 query 无一重复、无一丢失 |
| 6 个并发中第 3 个返 500 | **恰好 1 个被拒、5 个正常**——单点失败不污染邻居 |

**「错开完成顺序」是故意的**：若存在共享的 params/headers 缓冲，响应乱序就会交叉，
顺序一致的实现是测不出来的。**零缺陷。**

**探针错三处**：①查询参数名我猜成 `q`，实为 **`query`**（`buildSearchParams`）——
表现为 20 个空串，**这个「全空」的结果本身就是信号**；②`sed -i ''` 在本机又失败（**幸好没损坏文件**），
按既定约束改用 edit 工具；③**断言把字典序数组与数值序数组比**——
收到的是 `'0','1','10','11',…`，而期望是 `'0','1','2','3',…`；**收到的 20 个值其实全都在**，
是 `Array#sort` 对字符串按字典序排。两边同样排序后一次通过。

### 文档方向（架构文档，doc round 209）—— 零缺陷，**但取样差点出错**

**①本轮最大的收获是一次取样错误的拦截。**

我先核 `ARCHITECTURE.md` 的 Data Flow 段里各端点是否真实。
测得 `POST /api/ingest/session-start` → **404**，
而我在**会话开始时注入的 AGENTS.md 副本**里看到一条
「`POST /api/ingest/session-start` | Initialize session」——
**看起来是「AGENTS.md 有一个幻影端点」的新发现。**

**去磁盘上核对，结论完全相反**：

| 文件 | 幻影计数 | git 状态 |
|---|---|---|
| **`AGENTS.md`**（磁盘真实文件） | **0** | **已跟踪、干净** |
| `CLAUDE.md:131`、`:294` | **2** | **未跟踪、被 gitignore** |

**注入进上下文的 AGENTS.md 副本与磁盘上的文件不一致。**
若照副本下结论，我会「修」一个**根本没有该问题的文件**，并把 P2-48 重复记一遍。
**教训：上下文里注入的文档副本不是证据，磁盘上的文件才是。**

**②查清后确认：这条早已被记录，不是新发现。**
`backend-review-findings.md` 的 **P2-48** 写的正是 `CLAUDE.md`、9 条幻影、gitignored、记录不修——
**现行记录本来就是对的**；只有第 252 轮的**归档**把文件误记成 `AGENTS.md`，
而**归档创建后不得修改**，故不动。

**③架构文档本身完全正确（双语）**：`ARCHITECTURE.md:298` 与 `-zh-CN.md:297` 都明写
`POST /api/ingest/session-start` 返回 **404**（2026-10-03 对活体实测）、正确路径是 `POST /api/session/start`、
且「`IngestionController` **恰好暴露四个端点**」——**与我今天对活体 `/v3/api-docs` 的核对逐字吻合**
（`observation` / `session-end` / `tool-use` / `user-prompt`，不多不少）。

**本轮零改动。**

### 变更检测

本轮**未改任何代码**（临时测试已删）。指纹仍 `0f0ca2c7…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Demo**；文档方向：**运维/用户指南**（doc round 210）。

## 第 310 轮 — 2026-10-06T17:30:00+08:00

代码方向：**Demo**（两条新 finding，零代码改动）；文档方向：**运维/用户指南**（doc round 210，四处已修）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致。doc-growth 全 OK。

### 代码方向（Demo）—— 先跑起 demo 做受控实验，两条 finding

**未审过的文件优先**：`FeedbackController` / `ProjectsController` / `SessionStartClient` 在历史轮次里几乎零命中。

**①主候选：40 个 catch 块里只有 3 个做后端 4xx 直通。**
`FeedbackController.java:68-74` 的注释把理由写死了（不直通会「既误报状态又丢掉解释」），
但该机制只落在 `FeedbackController`、`ObservationsController` ×2 三处，其余 37 处一律转 500。
**活体实测（demo 37778 + 后端 37777）**：

| 请求 | 后端直打 | 经 demo |
|---|---|---|
| `PATCH /api/session/no-such/user` | **404** | **500** ← 缺陷点 |
| `POST /demo/feedback` 未知 observationId（**对照组**） | 404 | **404** ✓ |

对照组与缺陷点**在同一 JVM、同一次运行**内，故不是 `clientStatus` 失效或后端漂移。
→ **P2-76**（⏸ 记录不修：响应码变更属对外契约变更）。
**其余 37 处不可达**：`ManagementController` / `MemoryController` 入参都是 demo 自校验的自由文本，
调用方构造不出后端 4xx —— **实际暴露面是一个端点，不是 37 个**。

**②四个 `/memory/*` 端点把「存在但为空」报 500，而「缺失」报 400。**
`task` 是必填 `@RequestParam` 且四个方法体都没有空白检查，直落 SDK 的 `requireNonBlank`：

| 请求 | 实测 |
|---|---|
| `GET /memory/experiences`（不传 task） | **400**（Spring 必填校验） |
| `GET /memory/experiences?task=` | **500** |
| `GET /memory/icl?task=` / `.../filtered?task=` / `.../truncated?task=` | **500** 同上 |
| `GET /demo/experiences?task=`、`GET /demo/iclprompt?task=` | **400** ✓ 正确形态 |

→ **P2-77**（⏸ 记录不修）。**中间断言先错后改**：初判为「`experiences` 返 400 而 `icl` 返 500，是两者不对称」——
**那个 400 来自「参数缺失」不是「参数为空」**；补测 `?task=` 后 `experiences` **同样 500**。
原判据站不住，问题反而更整齐：**四个端点在「空」这一形态上完全一致地错**。

**顺带的运行期回归证据**：P1-2 沙箱仍然有效 —— `../../etc/passwd` 与 `/etc/passwd`
均被拒且响应不含任何内容（200 是因为工具按契约返回错误**字符串**供模型读，不是 HTTP 失败）。

### 文档方向（运维/用户指南，doc round 210）—— `DEVELOPMENT.md` 结构树四处失实，双语已修

扫 `DEVELOPMENT.md` 结构树的可证伪计数与文件名，**四处为假**（`zh-CN` 逐字同错）：

| 文档原文 | 磁盘实况 |
|---|---|
| `repository/ … (5 repositories)` | **6**（`ObservationFeedbackRepository` 是 V17 新增） |
| `entity/ … (5 entities)` | **6**（`ObservationFeedbackEntity` 同上） |
| `application-prod.yml  # Production profile` | **`application-prd.yml`**，且 profile 名是 `prd` |
| `scripts/ … (40+ scripts)` | **37** 个 `.sh`（目录共 38 个文件，含 `README.md`） |

**交叉验证**：P2-61 早已独立确立「本仓库只有 `dev` 与 `prd`，**没有 `application-prod.yml`**」——
本轮是那条事实在**文档侧的第二个受害者**。核实为真的部分同样记下：
`service (29 services)` ✓、`db/migration (V1–V8, V11–V18)` ✓（正好 16 个，V9/V10 正确缺席）、
**13 个控制器类名全部命中**、主类 `ClaudeMemApplication.java` ✓、Java SDK「3 模块且无 `./mvnw`」✓。
Build Profiles 段**逐字验证**：实跑 `mvn -Pdev validate` 输出与文档引用的 WARNING **完全一致**，
`help:all-profiles` 确有 `native` / `nativeTest`。

**四处按「失实陈述的文档修正」直接修**（双语各四处，含对齐列宽），改后逐项复查：陈旧表述 **0 处**、
`application-prd.yml` 与 `application-dev.yml` 同列对齐。

### 变更检测

本轮**未改任何代码**（新建 findings 两条 + 修正文档四处）。指纹仍 `0f0ca2c7…` / 885，
**与基线一致** → **不跑完整验收、不推进基线**。demo 进程（pid 49548）与探针已清理，
**37777 未受影响**，37778 已释放。

### 下一轮

代码方向：**Backend**；文档方向：**API 文档**（doc round 211，轮换回到起点）。

### 收尾：写完本轮报告后触发压缩（1533 行 → 876 行）

写完本报告后本文件达 **1533 行**、越过 `MAX_LINES=1500`，故同轮内执行压缩：
第 **294–300** 轮（7 条）逐字迁入新归档 `2026-10-06_health-check-history-18.md`（第 310 轮迁出）。
**验证全部以删除前快照双向比对**：工作文件 ∪ 归档 == 原始 17 轮且不交叠、
**归档逐字 == 原文第 294–300 轮切片**、**工作文件逐字 == 原文「294 轮之前 + 301 轮之后」**、
原始行**丢失 0 / 多余 0**、归档内 7 个块**无嵌套 `## 第 N 轮` 标题**。

**过程中本轮自查抓到并修正了自己造出的一个错误**：首版拼接时多写了一个结尾换行符
（`"\n".join(行列表)` 本身已还原原文，末尾再加 `"\n"` 就多一个字节），
表现为多重集比对多出 **2 个空行**、逐字比对 False。按既定纪律**归档有误只能删除重建**，
已删除重建并重跑全部八项验证。另一次探针错：把 `python3.` 当成解释器名。

**顺带补上一处既有缺陷**：归档 **14/15/16/17** 早已存在于磁盘，但在工作文件里**指针数为 0**
——前四次压缩都没留指针，被迁出的第 267–293 轮**无法从工作文件发现**。本轮补齐 5 条指针
（14–18），7 条指针的相对链接**全部可解析**。

## 第 311 轮 — 2026-10-06T18:45:00+08:00

代码方向：**Backend**（P2-78 已修，进入 3 轮复查 1/3）；文档方向：**API 文档**（doc round 211，两处已修，双语）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `0f0ca2c7…` / 885，与基线一致。doc-growth 全 OK。
**开工时工作区已有他人未提交 WIP**（`skills→.agents/skills` 重命名、`.gitignore`、`AGENTS.md`，
其后又扩到 `README*.md`、`docs/DEVELOPMENT*.md` 的结构树）——**全程不碰、不 stash、不丢弃**。

### 代码方向（Backend）—— CORS 允许方法清单漏了 PATCH

**选点**：历史零覆盖的后端文件里挑连贯主题 → `config/MdcAutoFilter.java` + `config/WebConfig.java`。

**①先排除一个看着危险的**：`MdcAutoFilter` 把请求头 `X-Correlation-ID` **原样回写进响应头**，
且经 `ClaudeMemLogAppender` 进日志，看着像头部反射 + 日志注入。**实测后不成立**：
原始字节注入 LF → Tomcat 直接 **400**；CRLF → **被剥离**，响应头里 `X-Injected-Header` **未出现**，
探针值也**未进入日志**（`claude-mem.log` 增长 0 字节）。**Tomcat 的头部净化挡住了，记为非问题。**

**②真缺陷**：`WebConfig.addCorsMappings` 的 `allowedMethods` 是
`GET, POST, PUT, DELETE, OPTIONS` —— **漏了 PATCH**，而活体有**两个 PATCH 端点**
（`/api/session/{sessionId}/user`、`/api/memory/observations/{id}`；活体方法分布
`GET 37 / POST 25 / PUT 1 / PATCH 2 / DELETE 2`）。

**判别实验（关键是那个对照组）**：

| `Access-Control-Request-Method` | 37790（已开启 CORS） | 37777（未开启） |
|---|---|---|
| `GET`/`POST`/`PUT`/`DELETE`/`OPTIONS` | **200** + `Allow-Methods` 头 | **403** |
| **`PATCH`** | **403，无任何 CORS 头** | **403** |

37777 上 GET 与 PATCH **同为 403** → 37790 上 PATCH 的 403 **不是「CORS 没开」那个基线**，
而是**被允许清单单独拒绝**。**同一实例 GET 200 / PATCH 403 就是判别实验本身。**

**为什么今天触发不了**：`claudemem.cors.allowed-origins` **全仓从未被设置**
（只在 `@Value` 默认值与 `docs/drafts/spring-ai-integration-plan.md:138` 出现；
`application*.yml` / `docker-compose.yml` / `.env.example` / 脚本**零命中**），
默认空值 → 预检**一律 403，CORS 默认关闭（安全默认成立）**。
但那份 draft **明确指导浏览器前端用户去配置它**，照做就恰好丢掉这两个端点。

**修复**：`allowedMethods` 补 `"PATCH"`。**归类为「纯加宽」而单方面实施**——
只对运维已显式配置的 origin 生效，不改变任何现有客户端行为，且不引入有意义攻击面
（能跨域调 GET/POST/PUT/DELETE 的 origin 本就比 PATCH 权限更高）。
**修后实测**（重启 37790 载入新 jar，`unzip -p … WebConfig.class | strings | grep PATCH` 确认含新代码）：
六方法**全 200**，另一条 PATCH 路径同样 200；**回归检查：未授权 origin 仍 403 且 ACAO 头 0 次**。
`mvn -o compile` 与 `package -DskipTests` 均 EXIT=0。→ **P2-78，进入连续 3 轮复查计数（1/3）。**

### 文档方向（API 文档，doc round 211）—— Error Codes 一节两处失实，双语已修

**①七个「业务错误码」全是幻影。** `MISSING_FIELD` / `INVALID_FORMAT` / `NOT_FOUND` /
`RATE_LIMIT_EXCEEDED` / `DB_ERROR` / `LLM_ERROR` / `EMBEDDING_ERROR`——
在后端与**四家 SDK** 中**全部零命中**（`NOT_FOUND` 的命中全是 `HttpStatus.NOT_FOUND` 这个 HTTP 枚举）。
**活体触发七类不同错误，响应体只有两种形态、都不带 `code` 字段**：
应用错误 `{"error":"observationId is required"}`（逐端点手写的自由文本）；
框架错误 `{timestamp,status,error,path}`（Spring 默认，产生于控制器执行之前）。
**危害具体**：按这七个码写分支的客户端会**每次都走进错误分支**。

**②HTTP 状态码表九项里四项不可达**：`HttpStatus.CREATED` / `UNAUTHORIZED` / `FORBIDDEN` /
`SERVICE_UNAVAILABLE` 在后端**各 0 命中**。401/403 尤其误导——**本 API 不做鉴权**；
403 仍可能出现，但来源是 **Spring 的 CORS 预检拒绝**，不是应用返回。
429 属实但**仅守 `POST /api/ingest/tool-use`**（第 305 轮已查清），已在表里点明。

**改法**：删掉幻影业务码表 → 改为如实描述**两种真实响应体** + JSON 示例 + 一句
「请按状态码分支，不要匹配文案」；状态码表**加一列可达性**逐项标注。
**顺带记下一条此前无人写下的事实**：path 里的 id 畸形走的是**框架错误**形态，
即 `PATCH`/`DELETE /api/memory/observations/{id}` 返 **400** 而非 404（P2-75 在文档侧的落点）。
**刻意未改**：`docs/API.md` **完全没有 CORS 章节**——但按「遗漏 ≠ 失实」，不制造修改。

### 变更检测与完整验收

本轮**改了后端代码** → 指纹 `0f0ca2c7…` → **`06e01132…`**（记录数 885 不变）。
**归因可查**：`code-fingerprint.sh` 的范围只有 `CODE_PATHS` + 根 `package*.json` /
`Dockerfile` / `docker-compose.yml`，**不含** `.gitignore` / `AGENTS.md` / `README.md` /
`docs/` / `.agents/` → 指纹变化 **100% 来自本轮 `WebConfig.java`**，与他人 WIP 无关。

**新鲜度处理**：37777 非本轮启动且其 jar 不含本轮改动 → **不重启、不复用**；
另起 **37790** 跑**新 jar**，用脚本自带的 `SERVER_URL` / `BACKEND_URL` 覆盖指向它：

| 套件 | 结果 |
|---|---|
| `regression-test.sh --skip-build` | **45 通过 / 0 失败 / 1 跳过 / 46** |
| `phase3-acceptance-test.sh`（`EXTRACTION_ENABLED=true`） | **25 通过 / 0 失败 / 0 跳过** |

**均与上次基线一致 → 基线推进**（`accepted_commit: 398c9e8`，指纹 `06e01132…`）。
验证后停掉 37790（自己启动的），**37777 全程未受影响**。

### 一件必须记下的协作事故

写报告期间**另一个执行体并发提交**，把我的四个文件卷进了它的 `3d415a7`
（`chore: sync project docs and pending work`），该提交同时含它自己的 README/DEVELOPMENT 改动。
**逐项核对后确认我的内容完整无损**（`allowedMethods` 那一行、`P2-78` 条目 4 处关键行、
API 两份文档幻影码已清零）。**未做任何回滚**——那会连带丢掉对方的改动。
本轮记录以 `398c9e8` 为准，并在此注明代码改动实际落在 `3d415a7`。

### 下一轮

代码方向：**Java SDK**；文档方向：**SDK README**（doc round 212）。
**P2-78 复查计数 1/3**，第 312 轮须复查第 2 遍。
