# Health Check History 14 — 第 267–272 轮巡检报告（第 277 轮迁出）

> **归档规则**：工作文件保留较近轮次；此处逐字保留**完整历史**。
> **归档文件创建后不得修改。**

---

## 第 267 轮 — 2026-10-05T21:34:20+08:00

### 健康预检

轮初工作区干净、`HEAD = 646272f`；37777 健康（200）且**新鲜度闸门成立**（进程 17:51:24 晚于
最新后端提交 17:50:12）。五份活动文档全 OK，退出 0。指纹 `699a9450…` **与基线一致**。

### 代码方向（Python SDK）：六个角度全核，**零缺陷**

1. **超时**：单旋钮 `timeout`（默认 30s，下钳 0.1）与 JS 同型；Go/Java 是连接+读取双旋钮，
   属各自 API 风格差异，**非分裂**。
2. **会话/安全层（起本地回显服务器实测）**：SDK 自带 key **从未**进入调用方传入的 session；
   请求确实带上了 key；调用方自己的 `X-Caller-Tag` 保留；`client.close()` 之后调用方自己的
   session 仍可用（200）。**四项全成立**。
3. **DTO 对后端真实 null 载荷**：`sessionId` → `''`、`extractedData` → `{}`、`createdAt` → `0`，
   `to_dict()` 省略空字段，**不抛异常**。
4. **README 方法数（本轮的主要疑点）**：README 三处写「25 methods」。实测 `CortexMemClient`
   有 **26 个**公共方法，多出来的是 `close()`——但它在 API 表里位于**独立的 `### Lifecycle` 段**，
   不在那 5 张功能表内，**故 25 未陈旧**。且不只数得对：把三家的方法名规范化后做**集合比较**，
   Python（去 `close`）/ Go / Java **各 25 个且两两差集为空**，逐项同名。
5. **重试范围声明**（README 179–186 行）逐家核到代码：Python `_fire_and_forget` 的调用点
   **恰 3 个**、`client_methods.go` 的 `doFireAndForget` **恰 3 个**、JS `doFireAndForget`
   **恰 3 个**；Java 的 `executeWithRetry*` 调用点**恰 10 个**（两个抽取读 + 三个变更 +
   `triggerRefinement` / `triggerExtraction` + 基础三个），与文中「10 of its 25」**吻合**。
6. **测试数**：`PYTHONPATH="$PWD" python3.11 -m pytest tests/ -q` → **441 passed**，
   分项 `test_client` **214** + `test_dto` **140** + `test_demo` **87** = **441**，与 README
   声称的分解**分毫不差**。（本机 `python3` 走 pyenv 3.10.6 **无 pytest**，须用 `python3.11`。）

**未改一行代码。**

### 文档方向（架构文档，doc round 168）：七个角度全核，**零缺陷**

行号引用 2 处、组件计数（29 服务 / 6 仓储 / 6 事件类 / 13 控制器）、端点数（ContextController
7 个、Viewer「15 methods」对活体 OpenAPI 逐路径数操作数 = 15）、配置摘录逐项、32 个 mode、
安全章节四处断言、`proxy/` 目录树 10 项、`wrapper.js` 事件→端点映射——**全部核实为准确**。
详见 `doc-review-task.md` 第 168 轮条目。

**两个探针自身出错、先识别再采信**：①数 Go 方法时 `awk` 的范围表达式把嵌套接口的
`String() string` 也截了进来得 27，改用「遇行首 `}` 即止」后为 25；②核端点存在性时拿文档里的
**前缀**去和活体**全路径**等值比较，误报 `/api/cursor/*` 与 `/api/test/*`「活体无对应者」，
改按前缀匹配后两者都在。

### 本轮唯一真缺陷：P2-61（⏸ 记录不修）

`TestController` 的类级 `@Profile("!prod")` 指向**本仓库不存在的 profile**——只有
`application-dev.yml` 与 `application-prd.yml`，**没有 `application-prod.yml`**；全仓
`SPRING_PROFILES_ACTIVE` 只出现 `prd`（`docker-compose.yml:52` 默认）、`dev`（两个 e2e 脚本）
与「不设」三种。`!prod` 在这三种下**全部匹配**，即该门控**永不排除任何东西**；它唯一会生效的
场景（`SPRING_PROFILES_ACTIVE=prod`）恰恰**没有对应配置文件**。而紧邻的 `@Tag` 描述却写着
「Only available in non-production environments」，**与实际行为相反**。活体（`dev` profile）
`/api/test/llm` 200、`/api/test/all` 200、`/api/test/embedding` 500（**已失效的嵌入密钥**，
即 P2-28 已记录的那条，非本轮新缺陷）。`ARCHITECTURE.md:880` 列出 `/api/test/*` 时不带
profile 限定，**据此反而准确**——因为门控确实从不生效，故**未改该行**。⏸ 不修：改成 `!prd`
会让三个端点在默认 compose 部署下**消失**，属**对外契约变更**；且本轮代码方向为 Python SDK。

### 第二十五次压缩（为写入 P2-61 腾位）

findings 已 1000/1000 满，第 265 轮已记明 **Scope+Evidence 杠杆耗尽**、且「无条件已解决可
整体迁出」候选为 0。本轮启用**第三种杠杆**：⏸ 条目**保留 Problem 本身、只迁出其中可复现的
实测细节**——`P2-24` / `P2-23` / `P2-48` 三条的 Problem 段正文逐字迁入
[`2026-10-05_backend-review-evidence-16.md`](../archive/2026-10-05_backend-review-evidence-16.md)，
Problem（压缩后）与 **Status 一行未动**。归档文本用 `sed -n '起,止p'` **按行号精确抽取后直接
重定向写入**，杜绝人工转录误差。**顺带修掉一处既有损坏**：P2-48 有一条**逐字重复**的
`- **Scope / Evidence**` 指针（`diff` 两行完全相同），系早前压缩残留。

**终验**：1000 → **973**（`git diff --stat` +21 / −48）、条目数 **48 未变**、
`git diff -U0 | grep Status` **零命中**（Status 行未被触碰）、指针数 **3 = 归档块数 3**、
全部归档链接可解析。写入 P2-61 后 **988 行**，`doc-growth-check.sh` 退出 0。

### 变更检测

**纯 `.md`**（findings、新归档、`archive/README.md`、`doc-review-task.md`），指纹
`699a9450…` **未变且与基线一致** → 按门控**不跑完整验收、不推进基线**。

## 第 268 轮 — 2026-10-05T21:48:55+08:00

### 健康预检

工作区干净、`HEAD = 659f33c`；五份活动文档全 OK，退出 0。指纹 `699a9450…` **与基线一致**。

### 代码方向（JS/TS SDK）：**一处陈旧数字，已修（双语）**

本轮进场的疑点是 Python README 的方法数（上一轮已查清），换到 JS 后**同型复发**：
两份 README 第 12 行写「**250 unit tests**（237 client + 5 truncated-body + 8 http-server example）」，
而 `npm test` 实测 **259**（246 / 5 / 8）。**两法独立确认**（vitest 默认 reporter 与
`--reporter=json` 逐文件数 `assertionResults`），且 `grep -cE '^\s*(it|test)\(' client.test.ts`
得 **246**，与 runner 计数一致，无动态生成测试。

**git 历史给出确切成因**：`64c8ffa`（2026-10-04）时 `client.test.ts` 恰为 **237** 个
`it/test`，README 的 250/237 **当时是对的**（第 250 轮刚把它从 247/234 更正过来）；
其后 `0fa1081` 又加了 **9** 个测试 → 246，**未同步 README**。**这正是第 250 轮抓到过的
同一个形状**（「改了测试却没同步文档」），隔 18 天在同一处复发。已双语更正为 **259（246+5+8）**。

**其余五个角度核实无误**：
1. **重试判定** `isRetryable` = 429/502/503/504 + `TypeError` + `name === 'AbortError'`
   （**按 name 而非 `instanceof DOMException`**，注释明写为 Node 兼容），与 Python 的
   `is_retryable_error`、Go 的 `IsRetryable` 状态码集合一致。
2. **零运行时依赖**：`package.json` **没有 `dependencies` 字段**，只有 `devDependencies` ✓
3. **双格式 CJS+ESM**：`main: ./dist/index.js` + `module`/`exports.import: ./dist/index.mjs` ✓
4. **Node 18+**：`engines.node: ">=18.0.0"` ✓
5. **`npm run lint`（`tsc --noEmit`）退出 0**；`truncated-body` 那 726ms 是**真实超时测试**
   （`timeout: 300` + 停摆服务器），非卡死。
6. **25 个 API 方法**核实无误（`startSession`…`getSettings` 共 25，另加 `close`/`toString`）。

**未改任何 `.ts`。**

### 文档方向（运维/用户指南，doc round 169）：**五个角度全核，零缺陷**

- **`TESTING.md`**：§3 表中 16 个脚本 + §3.5 的 3 个工具**逐个存在**（20/20）；
  `phase3-acceptance-test.sh` 的「15 test functions」实测**恰 15** 且函数名逐个列出；
  `run-all-e2e.sh` 的「10 local E2E suites」实测编号**恰为 1/10…10/10**；
  `regression-test.sh` 的 5 个选项**全部存在**且 `--help` 实跑退出 0；
  §7 的 MCP 自动探测与 `mcp-e2e-test.sh:97` 的判定条件**逐字一致**，
  活体实测 `/sse` 200 / `POST /mcp` 404 **正是文档描述的 SSE 态**；
  §8「CI/CD」只列 `docker.yml`、**未声称跑测试**——与 P2 记录一致。
- **`DEPLOYMENT.md` §2.4**：与 `docker-compose.yml` 逐行 diff，**键值集合完全一致**
  （剥注释 + 排序后 `diff` 为空），差异**纯为注释与排版**；§4.1 迁移表 **16 行**
  文件名与 `db/migration/` 下的 16 个文件**逐字吻合**，且**未虚列 V9/V10**；
  V1「5 core tables」实测**恰 5** 张表；§4.3 的容器名 `cortex-ce-postgres` 与库名
  `claude_mem` 均与 compose 一致。
- **双语同步**：`TESTING` 的 15/10、`DEPLOYMENT` 迁移表 16 行，两版计数**逐项相同**。

**一个探针命中的是文档已经解释过的事**：我扫 §5 的 49 个环境变量名，发现
`CLAUDEMEM_RATE_LIMIT_{MAX_REQUESTS,WINDOW_SECONDS,CLEANUP_INTERVAL_SECONDS}`
在后端配置中查无此物——但**文档自己就写着**「none of its keys appear in
`application.yml`」，并说明 `RateLimitService` 直接从 `@Value` 默认值读取。
实测确认：`@Value("${claudemem.rate-limit.max-requests:10}")` /
`window-seconds:60` / `cleanup-interval-seconds:300`，**三个默认值与文档表格逐项吻合**。
**探针错、文档对，未据此改任何一处**（与第 266 轮同型）。

### 变更检测

**纯 `.md`**（两份 JS SDK README），指纹 `699a9450…` **未变且与基线一致** →
按门控**不跑完整验收、不推进基线**。JS `npm test` 本轮**259/259 全绿**（+ `lint` 退出 0）。

## 第 269 轮 — 2026-10-05T22:08:00+08:00

### 健康预检

工作区干净、`HEAD = 618c01e`；37777 健康（200）且**新鲜度闸门成立**；五份活动文档全 OK，退出 0。
指纹 `699a9450…` **与基线一致**。

### 代码方向（Demo）：**P1-2 —— demo 任意文件读取，已记 finding + 已加文档警告**

本轮换个角度：从 `FileReadTool` 而不是控制器切入。`readFile` 直接
`Files.readString(Path.of(path))`，**无根目录约束、无 `..` 检查、无白名单**；而
`?project=` 只约束**记忆捕获**的项目，与文件读取无关。同时 `application.yml`
**只设 `server.port`、未设 `server.address`**，Spring Boot 默认绑 `*:37778`——
而后端显式设了 `address: ${SERVER_ADDRESS:127.0.0.1}`，两者姿态相反。

**活体实测（本轮自己起 demo 在 37778，验证后已停、探针已删）**：探针文件**逐字回显**；
`/etc/passwd` 返回 200；`/tmp/../etc/hosts` **穿越有效**；另两个入口
（`GET /demo/session/tool`、`POST /demo/session/lifecycle`）**同样成立**；
`lsof` 双向对照 demo `*:37778` vs 后端 `127.0.0.1:37777`。

**影响面已核实而非假设**：另三家 demo 对文件读取**零命中**（此能力为 Java demo 独有）；
后端已有 `PathValidationUtil`（注释明写防穿越）而 demo **一处未用**；demo 自带 e2e
只读 `/tmp`，**从无越界用例**。

⏸ **记录不修**（加路径约束是收窄行为 = 对外契约变更）；**已修**：demo README 端点表下
新增事实性警告（无沙箱、绑全网卡、不要照抄），**零行为变更**。**本轮未改任何 Java 代码。**

### 第二十六次压缩

为写入 P1-2 腾位，沿用第 267 轮确立的第三种杠杆：P2-21 的 Problem 段实测正文
逐字迁入 `evidence-17`，Problem（压缩后）与 Status 一行未动。**终验**：988 → 979
（Status 行 `git diff -U0` 零命中）、指针数 = 归档块数、全部链接可解析。
写入 P1-2 后 **994 行**，`doc-growth-check.sh` 退出 0；Current Status 的 P1 由 1 改为 2。

### 文档方向（API 文档，doc round 170）：**四个角度全核，零缺陷**

- **端点覆盖**：活体 **62 条**路径在 `API.md` / `API-zh-CN.md` 中**各 0 条缺失**。
- **第 264 轮的逐参数范围表（9 行）逐行实测全部吻合**，且**四种钳制写法各不相同**：
  `Math.min(Math.max(1,x), Constants.MAX_PAGE_SIZE)` / `…, 20` /
  `if (limit<1) limit=1; if (limit>100) limit=100;` / `…, 10000`。
- **「下钳到 0」六行**实测**全值域 200**（`-5 / 0 / 1 / 5 / 5000`），第 264 轮修复仍成立。
- **中英范围表各 11 行，同步。**

**三次探针自身出错、先识别再采信**：①缺必填 `project` 让四个请求齐返 400；
②epoch 区间 578 天触发 `Date range exceeds 1 year maximum`；③缺 `anchor` 触发业务 400
`No anchor found`。**决定性的是对照**——合法值 `depthBefore=5` 同样返 400，
一度让「第 264 轮修复已回退」看起来成立，**直到跑出对照才确认 400 与 depth 无关**。

### 变更检测

**纯 `.md`**（findings、两个新归档、`archive/README.md`、demo README、doc-review-task），
指纹 `699a9450…` **未变且与基线一致** → 按门控**不跑完整验收、不推进基线**。

## 第 270 轮 — 2026-10-05T22:25:17+08:00

### 健康预检

工作区干净、`HEAD = 3a6922d`；37777 健康（200）且**新鲜度闸门成立**（进程 17:51:24 晚于
最新后端提交 17:50:12）；五份活动文档全 OK，退出 0。

### 代码方向（Backend）：**P2-62 —— Javadoc 描述的语义与代码不符，已修注释（零行为变更）**

从服务层里从未被审过的 `ProjectFilterService` 切入。方法 Javadoc 写
"Handles both `~` (current user) and `~username` (specific user) forms"，
行内注释写 "expand to that user's home (best effort)" 与 "Fallback: if we can't resolve
`~username`, use current home"——**但代码里根本不存在 resolve 分支**：
`replaceFirst("^~" + username, userHome)` 把 `~username` **整段**替换成**当前**用户的家目录。

**反射直接调用实测**（非读码推断）：

| 输入 | 实际输出 |
|---|---|
| `~/proj` | `/Users/<当前>/proj` |
| `~alice/proj` | `/Users/<当前>/proj` ← **alice 静默消失** |
| `~alice`（无斜杠） | `~alice`（原样返回） |
| `~a.b/proj` | `/Users/<当前>/proj` ← `.` 成了正则通配 |

最后一行暴露第二个问题：`username` **未转义即拼进正则**（`replaceFirst` 首参是 pattern）。

**影响面已核实**：该类**未注册为 Bean**、生产代码**零引用**，类 Javadoc 自陈
"not currently wired into any processing pipeline" —— **当前影响为零**；风险在于有人照
该 Javadoc 把它接上后静默改写路径。**测试文件头曾声称覆盖 "expandHomeDirectory edge
cases"，而全文零个 `~` 用例**（唯一一处 `~` 就是那行声明本身）。

⏸ **记录不修**（正确解析方式属设计决策，且无生产调用方可验证）；**已修**：Javadoc 改为
如实描述并附实测表；测试头的不实覆盖声明已更正，并写明**为何不补测试**——补了就等于把
可疑行为钉死。

**顺带核实无误**：`TokenService.calculateEconomics` 的 `savings / totalDiscoveryTokens`
**有** `totalDiscoveryTokens > 0` 守卫，无除零。

### 第二十七次压缩

为写入 P2-62 腾位，沿用第三种杠杆：P2-29 的 Problem 段实测正文逐字迁入 `evidence-19`。
**首次抽取时误把要保留的 Scope/Evidence 行一并纳入**，复查归档首行后重写为只含 Problem 正文。
994 → 983。**写入 P2-62 后一度达 1001 行、`COMPACTION_REQUIRED`（退出码 2）**，收紧 P2-62
自身表述后为 **998 行**、退出 0。

### 文档方向（SDK README，doc round 171）：**四个角度全核，零缺陷**

1. **四家重试默认值「均为 3」**逐个核到代码：Java `maxAttempts = 3`、
   Go `client_impl.go:105 MaxRetries: 3`（注释自嘲 "despite the name"）、JS `?? 3`、
   Python `max_retries = 3`。Python README「all four default to 3」**属实**。
2. **超时/退避单位换算说明**属实：Python 秒、Go/Java `Duration`、JS 毫秒；
   Go `WithTimeout 30s` 对应 Java `readTimeout`、Go `WithConnectTimeout 10s` 对应
   Java `connectTimeout`，README 自己已写明这层对应。
3. **三家的安装命令**与实际包名/模块路径逐字吻合：JS `@cortex-mem/js-sdk` = `package.json`
   的 `name`；Go `…/go-sdk/cortex-mem-go` = `go.mod` 的 `module`；Python
   `pip install -e ./python-sdk/cortex-mem-python` 且 `from cortex_mem import` 对应包目录名。
4. **一处看着可疑、实则忠实源码**：Go README 分类表有一行字面叫 **「P1」**
   （含 `GetProjects`/`GetStats`/`GetModes`/`GetSettings`），与本仓库 P0/P1/P2 严重级用词
   撞名。**查源码后确认是忠实镜像**——`client.go:96` 与 `client_methods.go:325` 的段头正是
   `// ==================== P1 Management ====================`，其后**恰是**这四个方法。
   README 没有写错，改它反而会与源码自己的分组脱节。**未改**。

### 变更检测与完整验收

改了 2 个 Java 文件（`ProjectFilterService.java`、`ProjectFilterServiceTest.java`），
指纹 `699a9450…` → **`9d1f8307…`**，按门控**跑完整验收**。`git diff` 过滤注释后
**净非注释变更为零**（纯 Javadoc），但注释不影响字节码**不构成跳过验收的理由**。

**新鲜度闸门第二次适用**：另起 `37790` 跑本轮构建的 jar（构建 22:19:27、进程 22:22:36，
均晚于最新后端提交 17:50:12），**未重启非本轮启动的 37777**；验证后已停该实例。

| 验收项 | 结果 |
|---|---|
| 回归（`SERVER_URL=…:37790`） | **46 / 0 / 1** ✅ |
| Phase 3（`EXTRACTION_ENABLED=true`） | **25 / 0 / 0** ✅ |
| 后端 `mvn -o test` | **167** 全绿，BUILD SUCCESS |
| `mvn -o package` | EXIT=0 |

**基线已推进**至 `accepted_commit: 3a6922d` / `code_fingerprint: 9d1f8307…`。
引用 25/0/0 时仍须一并引用 P2-46 的限定（`cleanup()` 幻影、Test 6 走兜底分支、
Test 14 断言已恒真）。

**环境注记**：记忆中「`TimelineServiceTest` 11 error（JDK 25 Mockito）」在本轮**不复现**，
该类实测 11 测试 0 错误。

## 第 271 轮 — 2026-10-05T22:52:00+08:00

### 健康预检

工作区干净、`HEAD = ae21c6b`；37777 健康（200）；五份活动文档全 OK，退出 0。
指纹 `9d1f8307…` **与基线一致**（第 270 轮刚推进过）。

### 代码方向（Go SDK）：**核实无误；但本轮角度撞出一桩旧事故（P2-63）**

四个角度：①**DTO 与活体逐字段对拍**——`StringList.UnmarshalJSON` **从不返回错误**，
`null` 落在首个分支被正确处理；四个时间字段的 Go 声明均为 `string`，而实体里
`created_at` / `last_accessed_at` / `refined_at` / `feedback_updated_at` **同为
`OffsetDateTime`**，活体实测 `refined_at` 序列化为 ISO 字符串，故四处理解一致、`null` 进
`string` 也不报错。②**三个 adapter**——`context.Background()` 只出现在测试里，生产代码
**无丢弃 ctx**；eino 的错误策略与其 Javadoc 自述一致（与 langchaingo **有意不同**，
且理由写在注释里）。③**`WithRetrieverCount` 的 Javadoc 极其详实**，逐条写明 `count=0`
被 `omitempty` 丢弃、负数原样上送的后果，并交叉引用 P2-36——**该条目确实存在**。
④**新角度：全仓交叉引用完整性**。

### P2-63（本轮真正的发现）：一次压缩把 P2-30 整块销毁且未进归档

顺着 ③ 的 P2-36 引用做全仓扫描，发现 **20 个被引用的条目里有 4 个在工作文件中查不到**。
逐个甄别后，三条指向**已归档**条目（`P1-3`/`P2-1` 在 `2026-02-12_code-review-v1.md`、
`P2-31` 在 `resolved-15.md`），属历史记录，合理。**第四条不同**：

- `git log -S'### P2-30:'` 只有**两条**命中：`1491f5b`（建立）与 **`adf4366`（销毁）**。
- `adf4366` 对工作文件 **+41 / −143**，diff 中**无任何含 P2-30 的新增行**。
- 该提交新建的归档 `…-reproduction-5.md` 头部自述内容为 P1-1 / P2-8 / P2-28 / P2-29 /
  P2-32 / P2-34，**不含 P2-30**。
- 压缩日志第 141 轮明写迁出 P2-30 的 Reproduction 时「Status stayed put」——
  **故其主体本应留在工作文件，不是有意归档**。

**幸存部分**：`Impact` / `Status` / `Reproduction` 在 `reproduction-4.md`，`复核记录` 在
`provenance-2.md`；**`Problem` 彻底丢失**。后果是
`python-sdk/cortex-mem-python/cortex_mem/client.py:403` 的 docstring 明写邻近站点
「is recorded as **P2-30**」——**读者被指向一个查不到的条目**。

✅ **已恢复**：`Problem` 据第 133 轮日志**重建并显式标注为非逐字**，`Impact`/`Status`/
`Reproduction` 保持指向原始归档，事故经过与幸存清单集中在
`2026-10-05_backend-review-recovered-P2-30.md`。**这正是 P2-47 事故（第 258 轮）记录的
同一失效模式复发**；现行断言只覆盖「边界行首」与「指针数=归档块数」，**没有一条检查
「工作文件里曾存在的条目是否还在」**——补这一条属规则变更，未自行添加。

### 第二十八、二十九次压缩

P2-39 与 P2-57 的实测正文各外迁一批。**顺带修掉第二处既有损坏**：P2-39 有一条
**逐字重复**的 `- **Scope / Evidence**` 指针（`diff` 两行相同），与第 267 轮在 P2-48
修掉的同型。**并核实 P2-57 的一个待办早已完成**：`client.py` 的 Javadoc 示例已改为
显式说明为何默认值用 IPv4 字面量并**回指 P2-57**，工作文件侧相应标为 ✅。

**一次自己犯的错并当场修正**：恢复 P2-30 时用 `edit` 替换锚点，**误删了 P2-29 尾部的两行
指针**（`Reproduction` 与 `P2-31 已整体迁出`），靠打印替换点前三行核对发现并补回。

写入 P2-30 与 P2-63 后一度达 **1013 行、`COMPACTION_REQUIRED`（退出码 2）**；
两批压缩后回到 **1000 行整**，状态 OK、退出 0。决策推理（`不修的理由`）与全部 Status 行
`git diff -U0` **零命中**。

### 文档方向（设计文档，doc round 172）：**两个角度全核，零缺陷**

- **内部链接 25 条，零断链**。
- **`文件:行号` 引用仍是上轮那 7 处，无新增无漂移**；抽查 5 处精确命中：
  `AgentService.java:241`（去重查询）、`ExtractionStorageService.java:49`（`@Transactional`）、
  `:127`（DLQ 构造）、`StructuredExtractionService.java:211`（`findBySourceIn` 调用）、
  `:315`（`return extractAppendOnly`）。

### 变更检测

**纯 `.md`**，指纹 `9d1f8307…` 未变且与基线一致 → 按门控**不跑完整验收、不推进基线**。

## 第 272 轮 — 2026-10-05T23:05:00+08:00

### 健康预检

工作区干净、`HEAD = b8d7f0a`；37777 健康（200）；五份活动文档全 OK，退出 0。
指纹 `9d1f8307…` **与基线一致**。

### 代码方向（Demo）：**三个角度全核，零缺陷**

1. **`resolveProjectPath` 的 null 兜底是死代码**——该方法仅在 `projectKey` 为 null/blank 时
   返回 null，而 `ToolsController:42-45` 调用前**已先判过** `project != null && !isBlank()`，
   故 `resolved != null ? resolved : project` 的 else 分支**永不触发**。无害，不改。
2. **装箱参数缺省值**：全量扫描 `@RequestParam` 的 `Integer/Long/Double/Boolean`，
   **无一个「装箱且无 `defaultValue`」**（故不存在拆箱 NPE 面）。`ExperiencesController` 的
   `count` / `maxChars` 都有 `defaultValue`，第 54 行的 `count < 0 || count > 100` 拆箱安全。
3. **`CortexSessionContext.begin/end` 配对**：4 个控制器共 `begin` 6 次 / `end` 6 次，
   且**逐处确认都在 `finally` 块内**（`SessionLifecycleController` 三处分别在三段独立的
   try-finally 里）。**未复现 P1-1 的 ThreadLocal 泄漏形态**。

### 文档方向（架构文档，doc round 173）：**双语结构对拍，零缺陷**

新角度是**逐标题结构对拍**（第 267 轮只抽查了关键断言，未比对骨架）：

| 维度 | EN | ZH | 结论 |
|---|---|---|---|
| 标题总数 | 54 | 54 | 一致 |
| H1–H3 骨架 | 42 | 42 | **逐项一致** |
| 表格行 | 106 | 106 | 一致 |
| 代码围栏 | 40 | 38 | 差 1 个代码块，见下 |

**差的那个已查清，判为非缺陷**：EN 把「`mem_pending_messages` 表」与
「`observation_feedback` 表 (V17)」分成**两个** `sql` 块，ZH 把两者**合并为一个**。
**内容逐行对等**（表 DDL 相同、三个 `CREATE INDEX` 相同、注释头对应），仅
`signal_type` 一行的分隔符 EN 用 `,` 而 ZH 用 `、`——**合理本地化**。
**零信息损失、渲染亦正常**（ZH 渲染为一个 pre 块，可读性不受影响），
故**不制造修改**。此即第 267 轮定下的「零缺陷不硬凑」。

### 变更检测

**纯 `.md`**，指纹 `9d1f8307…` 未变且与基线一致 → 按门控**不跑完整验收、不推进基线**。

