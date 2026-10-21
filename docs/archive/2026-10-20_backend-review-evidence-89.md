# 后端审查证据归档 -89（2026-10-20）

> 第 388 轮（Demo + Java demo README，doc round 287）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1251 行 / 149671 字节**）。
> 本文档体量：**345 行 / 25623 字节**。

## 本轮的两条主线

① **一条「存活信号不能被别的东西代理」这一族的第三个实例** —— 而且这次**方向相反**：
   第 183 轮修的是 `/memory/health` 的**假绿**（用静默降级的方法当存活信号），
   本轮在 demo 里同时找到**恒绿**（6 个静态 health 端点）与**假红**
   （两个 e2e 脚本拿后端健康当 demo 存活的代理）⇒ 三者同根不同向。

② **两个「同族里唯一没被钉住的那一个」** —— 第 4 次与第 5 次出现这个形状：
   demo 的 **44** 个端点暴露面**零测试钉住**；六个静态 health 端点的**恒 ok** 语义
   也零钉住（唯一有测试的那个，恰好是唯一能区分后端死活的那个）。

本轮全部改动**零行为变更**：主源码一行未动，加的是测试与文档；改的两个脚本只改**判定**，
不改任何端点行为。

---

## P2-167: Java demo 的 44 个端点暴露面零测试钉住

- **Scope**: → [scope-9][a6]
- **暴露面（程序化提取，不是手数的）**: 12 个 Controller、**44** 条 `(verb, path)`：

  | Controller | 端点数 | 路径 |
  |---|---|---|
  | ChatController | 1 | `GET /chat` |
  | ExperiencesController | 3 | `/demo/experiences`、`/demo/iclprompt`、`/demo/experiences/health` |
  | ExtractionController | 4 | `/demo/extraction/{latest,history,health}`、`POST /demo/extraction/run` |
  | FeedbackController | 2 | `POST /demo/feedback`、`GET /demo/feedback/health` |
  | IngestController | 2 | `POST /demo/ingest/{prompt,session-end}` |
  | ManagementController | 8 | `/demo/manage/{version,stats,quality,modes,settings,projects,health}`、`POST /demo/manage/refine` |
  | MemoryController | 7 | `/memory/{experiences,experiences/filtered,icl,icl/truncated,quality,health}`、`POST /memory/refine` |
  | ObservationsController | 7 | `/demo/observations`、`/demo/observations/{id}`（GET/PATCH/DELETE）、`POST /demo/observations/{batch,create}`、`GET /demo/observations/status` |
  | ProjectsController | 1 | `GET /demo/projects` |
  | SearchController | 2 | `/demo/search`、`/demo/search/health` |
  | SessionLifecycleController | 6 | `/demo/session/{start,prompt,tool,end,user,lifecycle}` |
  | ToolsController | 1 | `GET /demo/tool` |

  ⇒ **重复的 `(verb, path)` 组合：0**。
- **缺口（测试文件的覆盖盘点）**: demo 原有 **5** 个测试文件、共 **40** 个用例 ——
  `DemoErrorsTest`（8）、`DemoParamsTest`（13）、`FileReadToolTest`（8）、
  `FileReadToolRootEdgeCaseTest`（7）、`MemoryHealthControllerTest`（4）。
  **44 个端点里只有 1 个**（`GET /memory/health`）有端点级测试。
  ⇒ 端点路径改一个字母、动词翻一下、少一个 Controller，**没有任何测试会响**。
- **修法（本轮已修，只加测试，实现零改动）**: 新增 `DemoEndpointSurfaceTest`（**3** 个用例）：
  1. `shouldServeExactlyTheDeclaredEndpointSurface` —— 断言 **44** 条 `(verb, path)` 的
     **集合相等**（`assertEquals(expected, actual)`，**不是** `containsAll`）⇒
     多一条、少一条、改一个动词都会红。
  2. `shouldNotDeclareTwoEndpointsWithTheSameVerbAndPath` ⇒ 同一 `(verb, path)` 不重复。
  3. `shouldKeepTheHealthEndpointOfEveryControllerThatHasOne` ⇒ 钉住「哪些 Controller 有
     `/health` 兄弟端点」（**6** 个）。
  实现用**反射读注解**（`getDeclaredMethods()` + `@GetMapping`/`@PostMapping`/… 的 `value()`），
  **不起 Spring 上下文、不连后端** ⇒ 测试是纯的、可离线跑。
  ⭐ 类级 `@RequestMapping` 前缀与方法级路径按 Spring 的规则**简单拼接**。
- **变异测试（三次，各自单变量）**:
  - 变异 A：`ExperiencesController.health()` 的 `status` 由 `ok` 改为 `degraded`
    ⇒ `StaticHealthEndpointsTest` 恰好 **1** 红（见 P2-168）；
  - 变异 B：`ProjectsController` 新增一个端点 `GET /demo/projects-extra`
    ⇒ `shouldServeExactlyTheDeclaredEndpointSurface` 恰好 **1** 红；
  - 变异 C：`ToolsController` 的 `/demo/tool` 改名为 `/demo/tools`
    ⇒ 同上恰好 **1** 红。
  三次均按 sha256 逐字节还原（`b3ec20f5…` / `4aa404ef…` / `33b532e8…`），
  还原后 `git diff src/main/` 为空。
- **一次手打预期集出错（被新测试当场抓住）**: 我手写的 44 条预期集有**两处错**
  —— 漏了 `GET /demo/tool`、把 `POST /demo/ingest/prompt` 写成了 GET。
  新测试直接报出 expected/but-was 的**完整差集** ⇒ 修正预期集。
  ⭐ 修正前用**第二条独立来源**核对过两处（直接读 `ToolsController` 与 `IngestController`
  的注解），确认是**预期集错、实现对** ⇒ 以实现为准。
  ⭐ 这正是「手打的标识符是输入，不是测量」——但这次**有第二条独立来源**兜住，
  所以没有把「我抄错了」误判成「代码错了」。
- **活体对照（独立于注解）**: 起了一个 demo 实例逐条打 README 端点表的 **14** 条
  ⇒ **全部路由正确**（无 404 / 405）⇒ 声明的暴露面与实际服务的一致。

---

## P2-168: 6 个静态 health 端点恒返 200 `status:"ok"` —— 后端宕机时仍报「健康」

- **Scope**: → [scope-9][a6]
- **两种语义（读实现得出）**:
  - **真检查（1 个）**: `MemoryController.getMemoryHealth`（`GET /memory/health`）——
    调 `cortexClient.healthCheck()`，**失败返 503** `{"status":"unavailable","detail":"…"}`，
    成功返 200 且带 `sample_retrieval`。
  - **静态（6 个）**: `ExperiencesController`(`/demo/experiences/health`)、
    `ExtractionController`(`/demo/extraction/health`)、
    `FeedbackController`(`/demo/feedback/health`)、
    `ManagementController`(`/demo/manage/health`)、
    `SearchController`(`/demo/search/health`)、
    `ObservationsController`（`GET /demo/observations/status`，方法名也叫 `health`，
    但路径用 `/status` 以避开 `/{id}` 路径变量——源码注释明写了这个理由）。
    六个的实现体**都是一行** `ResponseEntity.ok(Map.of("endpoint", "<名字>", "status", "ok"))`，
    **不碰后端**（虽然它们的 Controller 都注入了 `CortexMemClient`）。
- **2×2 活体对照矩阵（决定性证据）**: 起两个 demo 实例，一个指向**活后端** 37777，
  一个指向**死后端** `http://127.0.0.1:1`；各打 7 个 health 端点：

  | 端点 | 后端**死** | 后端**活** | 能否区分？ |
  |---|---|---|---|
  | `GET /demo/experiences/health` | **200** `{"status":"ok","endpoint":"experiences"}` | **200** 同上 | ❌ **逐字节相同** |
  | `GET /demo/extraction/health` | **200** `{"status":"ok","endpoint":"extraction"}` | **200** 同上 | ❌ |
  | `GET /demo/feedback/health` | **200** `{"status":"ok","endpoint":"feedback"}` | **200** 同上 | ❌ |
  | `GET /demo/manage/health` | **200** `{"status":"ok","endpoint":"manage"}` | **200** 同上 | ❌ |
  | `GET /demo/search/health` | **200** `{"status":"ok","endpoint":"search"}` | **200** 同上 | ❌ |
  | `GET /demo/observations/status` | **200** `{"status":"ok","endpoint":"observations"}` | **200** 同上 | ❌ |
  | `GET /memory/health` | **503** `{"status":"unavailable","detail":"Backend did not answer GET /api/health — capture and retrieval are not working"}` | **200** `{"status":"ok","sample_retrieval":"empty"}` | ✅ |
  | `GET /actuator/health` | **503** `{"status":"DOWN"}` | **200** `{"status":"UP"}` | ✅ |

  ⇒ **同一进程、同一时刻，7 个「健康」端点里 5 个说「ok」、2 个说「不可用」**。
  ⇒ 任何用那 5 个做存活探针的脚本，在后端完全宕机时**必然报「健康」**。
- **这不是新问题，而是第 183 轮那个坑的同族（且那次已写进 Javadoc）**:
  `MemoryController.getMemoryHealth` 的 Javadoc 原文写着 ——
  「Reachability is decided by `healthCheck()`, never by the sample retrieval below.
  `retrieveExperiences` is one of the client methods that **degrades silently** … so using
  its result as a liveness signal reports "ok" precisely when memory is least likely
  to work. Verified against a running demo pointed at a dead backend: this endpoint
  answered 200 with `status: "ok"` while every memory call was failing.」
  ⇒ **那次修的正是这个形状**，只修了 `/memory/health` 一个；**另外 6 个原样留着**。
- **零测试 + 零文档**:
  - 44 个端点里唯一有端点级测试的就是 `/memory/health`（`MemoryHealthControllerTest` 4 例）
    ⇒ **恰好是唯一能区分后端死活的那个有测试**，另外 6 个恒绿的一个都没有；
  - Java demo README 的端点表**只列了** `/memory/health` 与 `/actuator/health`
    ⇒ 那 6 个静态端点**在文档里根本不存在** ⇒ 读者无从知道它们存在，更无从知道它们恒绿。
- **修法（本轮已修）**:
  1. **加测试** `StaticHealthEndpointsTest`（**7** 个用例）：6 个静态端点各断言
     **200** + `status: ok` + `endpoint: <名字>` + **`verifyNoInteractions(client)`**
     （证明不探后端）；第 7 个是**阳性对照** —— 同一个 mock 在 `/memory/health` 上
     **确实**被观察到（`verify(client).healthCheck()`）⇒ 「零交互」意味着「没去问」，
     而不是「mock 失灵」。
  2. **补文档**：Java demo README 端点表补上这 6 条并逐条标注「静态、不问后端」；
     表后加一段写明「**七条里只有两个真检查后端**」，并给出上表的实测读数与
     第 183 轮那次修复的来龙去脉。
  3. ⭐ **不改这 6 个端点的行为** —— 让它们真去问后端会**改变调用方看到的东西**
     （从恒 200 变成可能 503）⇒ 属**对外契约变更**，按纪律**只记不实施**。
- **变异测试**: 变异 A（把 `ExperiencesController.health()` 的 `status` 改成 `degraded`）
  ⇒ `shouldAnswerExperiencesHealthStatically` 恰好 **1** 红，其余 **49** 绿。

---

## P2-169: `demo-v15-test.sh` 的 `curl -sf` 把业务合法的 404 判成失败 ⇒ 第一个测试必然失败

- **Scope**: → [scope-9][a6]
- **症状（活体）**: 跑 `scripts/demo-v15-test.sh` ⇒ **2 通过 / 1 失败**，失败行是
  `[FAIL] GET /demo/extraction/latest failed: ` —— **冒号后面是空的**。
- **两个缺陷（同一个 `curl -sf` 造成）**:
  1. **`-f` 让任何 HTTP ≥ 400 短路** ⇒ 脚本提前失败；
  2. **`-s` 让 curl 在 HTTP 错误时不输出响应体** ⇒ `$response` 为空
     ⇒ **失败消息不可诊断**（连状态码都没有）。
  活体确认：`curl -sf <latest 的 URL>` ⇒ **退出码 22、输出为空**。
- **为什么 404 是正确业务语义**: `/demo/extraction/latest` 在「该 project/template/user
  没有抽取结果」时返 **404 `{"error":"No extraction found"}`** —— 后端
  `ExtractionController.java:76` 就是这么写的（`"not_found", templateName, …, "No extraction found"`）。
  而脚本的 `TEST_PROJECT="/tmp/demo-v15-test"` 在任何环境都几乎不可能有抽取数据：
  实测该 project 下 `mem_observations` **0 行**（库里 `llm_extraction` 源有 18373 行，
  但都在别的 project 下）⇒ **`curl -f` 遇 404 短路 ⇒ 第一个测试恒失败**。
- **脚本本意（自证）**: `test_extraction_latest` 的注释写「Response should be JSON
  (may be empty or have status)」，且后半段**专门做 JSON 校验** ⇒ 作者**本意**是接受
  各种响应体 ⇒ **`-f` 与本意冲突**。
- **修法（本轮已修，脚本层，零行为变更）**:
  - 抽出 `fetch()`：用 `curl -s -w '\n%{http_code}'` **显式拆出状态码与响应体**
    （不再用 `-sf`）；
  - `latest` 接受 **200 与 404**，并**加一条断言**：404 的响应体必须含
    `No extraction found`（否则分不清「无数据」与「路由错了」）；
  - 另两个测试要求 **200**；
  - 失败消息一律带 **HTTP 状态码 + 完整响应体**。
- **变异测试（证明是「更精确」而非「更松」）**:
  - 变异 A：把 `latest` 的 URL 指向一个不存在的路径（**404 但 body 不含
    `No extraction found`**）⇒ 脚本**红**，且失败消息带状态码与完整 body
    ⇒ 新加的 body 断言**真的在把关**；
  - 5xx 仍判失败：打到一个指向死端口的 demo 实例 ⇒ **1 通过 / 2 失败**，
    失败消息直接指出后端 I/O 错误。
- **前后对照**: 修前 **2/0/1**（exit 1）⇒ 修后 **3/0/0**（exit 0），
  与 Java demo README 写的「V15 demo tests (3 tests - extraction endpoints)」**吻合**。

---

## P2-170: 两个 demo e2e 脚本的 `check_demo` 拿后端健康当 demo 存活的代理

- **Scope**: → [scope-9][a6]
- **活体读数（同一个 demo 实例，后端死）**:
  | 探针 | 响应 |
  |---|---|
  | `GET /actuator/health` | **503** `{"status":"DOWN"}` |
  | `GET /demo/observations/status` | **200** `{"status":"ok","endpoint":"observations"}` |
- **根因**: `check_demo`（`demo-v14-test.sh:33` 与 `demo-v15-test.sh:33`，两处同形）写的是
  `curl -sf "${DEMO_URL}/actuator/health"`。而 `/actuator/health` **聚合后端健康**
  ⇒ 后端一挂就返 503 ⇒ 脚本报「Demo is NOT running」并 `exit 1`，
  还打印「Start with: cd examples/cortex-mem-demo && mvn spring-boot:run -Plocal」
  ⇒ **后端故障被伪装成「demo 没启动」**，排查方向被直接带偏。
- **修正前后（同一条命令、同一实例）**:
  - 修前：`[WARN] Demo is NOT running at http://127.0.0.1:37778` ⇒ 跳过整个套件；
  - 修后：`[INFO] Demo is running at http://127.0.0.1:37778` ⇒ 继续跑 ⇒
    **1 通过 / 2 失败**，失败消息写明
    `returned HTTP 500: {"error":"Get latest extraction failed: … I/O error on GET request for \"http://127.0.0.1:1/api/extraction/user_preferences/latest\": null"}`
    ⇒ **诊断方向正确**：一眼看出是后端不可达，而不是去起 demo。
- **修法（本轮已修，脚本层，零行为变更）**: 判据改为 `/demo/observations/status` ——
  那是 demo **自身**的静态端点，**不依赖后端** ⇒ 只回答「demo 是否在跑」。
  脚本注释里写明了为什么不用 `/actuator/health`（含上面那条实测读数）。
- **变异测试（两次，各自单变量）**:
  - 变异 A：判据改回 `/actuator/health` ⇒ 回到「Demo is NOT running」⇒ 修正有效；
  - 变异 B：判据指向一个不存在的路径 ⇒ 仍正确判「demo 没在跑」⇒
    判据**仍能识别 demo 真的没起**（不是无脑放宽）。
- **影响面（按断言清扫）**:
  - `scripts/run-all-e2e.sh:15` **不跑**这两个脚本（列在「需要额外服务」而排除）⇒
    **那个套件不受影响**；
  - 但 `docs/TESTING.md:53`（脚本表）与 `:78`（「Run all demo tests」的示例）、
    `examples/cortex-mem-demo/README.md:155`（E2E 步骤）、`scripts/README.md:35` 与 `:537`
    都把它当**会通过**的流程推荐 ⇒ 读者照做会看到失败。
- ⭐ 与 **P2-168** 同族但**方向相反**：那是**假绿**（恒说「ok」），这是**假红**
  （后端故障被说成「demo 没启动」）⇒ 同一个病根：**拿别的组件的状态当自己的存活信号**。

---

## P2-79 的证据段（⏸ 条目，按第 254 轮规则迁出）

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文。

- **活体三配置对照（37790，逐次重启、改同一处配置）**：

  | `claudemem.cors.allowed-origins` | 普通请求 `GET /api/stats` | 预检 | 日志异常数 |
  |---|---|---|---|
  | `http://a.example,*`（星号非首位） | 500 | 403，无 CORS 头 | 每次请求抛异常 |
  | `*,http://a.example`（星号在首位） | 200 | 200 + `ACAO: *` | 0 |
  | `http://a.example`（无星号） | 200 | 200 + ACAO + 凭据 | 0 |

  失效形态远重于「跨域不工作」：整个 API 返回 500，连不带 `Origin` 的普通请求也是 500。
  异常原文：`When allowCredentials is true, allowedOrigins cannot contain the special value "*"…`
- ⚠️ 第 314 轮更正本条第二行（且更正方向是加重，不是减轻）：
  初版记作「200，但**无 ACAO 头** → 浏览器照样拦截；两种错法不同，都错」。
  那个 ACAO 断言是用坏探针测的——`curl -o 文件` 存的是响应体，却去 grep 响应头，
  读到的永远是空。第 313 轮已在同一段代码上栽过同一次并当场改正，本条当时未同步更正。
  **用 `-D` 重测，结论相反**（37790，配置 `*,http://a.example`，原样无空格）：

  | `Origin` | 实测 |
  |---|---|
  | `http://a.example`（列出） | 200 + `Access-Control-Allow-Origin: *`，`ACAC` 头 0 次 |
  | `http://zzz-not-listed.example`（未列出） | 200 + `Access-Control-Allow-Origin: *` |

  即第二种写法不是「坏了但安全」，而是对所有 origin 全开——
  `allowCredentials` 被算成 false，Spring 便直接回显通配，通配压过同列表里的具体源。
  **所以本条不是「两种错法都错」，而是「一种把整个 API 打挂、一种把它对全网敞开」**，
  安全权重由此上升（初版低估了自己）。第 1 行的 500 与第 3 行的 ACAO 不受影响：
  前者的 500 取自 `-w` 的状态码本身（非响应头 grep），后者出自第 311 轮用 `-D` 的测量。
  今天不可触发：该配置项全仓从未被设置（与 P2-78 同一条证据）。

---

## P2-82 的证据段（⏸ 条目，按第 254 轮规则迁出）

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文。
> 工作文件侧保留：`- **Scope**` 一行、`- **Status**` 一行、⚠️ 更正定性的**结论**（压缩成一句）与「未证实的部分，不写」。

- **同一道安全阀的三家形态**（工作文件侧原表逐字）：

  | SDK | 上限 | 形态 |
  |---|---|---|
  | Go | 10 MB | `MaxResponseBytes = 10 << 20`，导出具名常量；`io.LimitReader(body, Max+1)` 读超一字节再显式报错 |
  | JS | 10 MB | `const maxSize = 10 * 1024 * 1024`，三道检查：读前查声明的 `Content-Length`、读后查 `text.length`、外加 UTF-8 字节感知的第三次 |
  | Python | 无 | `_request` 拿到 `requests.Response` 后**直接取 `resp.content`**，全路径零体积判断 |

- **探针读数**：

  ```text
  grep -nE 'MAX_RESPONSE|max_response|10 \* 1024 \* 1024|10485760' python-sdk/cortex-mem-python/cortex_mem/*.py
  ```

  零命中。`requests` 默认把整个响应体缓冲进内存，故一个超大响应（或拦截它的代理）
  会在 Python 侧被完整读入，而 Go 与 JS 会中止并显式报错。
  这不是「少写了个常量」而是失效形态不同：同一次异常后端响应，
  Go/JS 得到「响应体超过上限」的可诊断错误，Python 得到一次内存暴涨。
  佐证这道阀是加的且必要：JS 的注释记着它是在实测「多字节字符使 `text.length`
  低估体积达上限的 1.5 倍」之后才补的第三道检查——即它确实在真实场景下漏过。

- ⚠️ 第 316 轮更正定性（**逐字原文**；工作文件侧只留结论一句）: 这不是「无人知晓的缺口」，而是已双语记载的有意权衡**
  **上一轮定性为缺陷是错的**。`python-sdk/cortex-mem-python/README.md:229-238` 有一节
  「**Response Size Limit**」，中文版 `:229-238` 同节同步，内容逐条如下：
  - 明写「There is **no** response size cap in this SDK」；
  - 给出**原因**：「`requests` gives **no portable hook** for a streaming size check」；
  - 写出**后果**：「a very large response is bounded only by the memory available to your process」；
  - 给出**建议**：「Keep `limit` modest when searching or listing large observation sets」。
  **即技术事实不变（Python 无上限、Go/JS 有 10 MiB），但它的性质是「已记载的取舍」，
  不是「静默的缺口」**——与 P2-78（配错了没人知道）不同类。
  **仍然剩下的唯一问题是「要不要改这个取舍」，那是产品决策**（是否值得为 Python 引入
  流式读取并承担 `requests` 之外的复杂度），**不是补缺口**，维持不修。
  **对拍参考**：Go README `:227-235` 记着常量名、`10 << 20`、读 `Max+1` 字节与错误原文；
  JS README `:171-179` 记着报错文案并**主动澄清**「文案写 `10MB`，实际上限是 10 MiB = 10,485,760 字节」。
  **三家文档各自准确，双语同步。**

---

## P2-75 的证据段（⏸ 条目，按第 254 轮规则迁出）

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文。
> 工作文件侧保留：`- **Scope**` 一行、`- **Status**` 一行、⚠️「差点误记」的**结论**（压缩成一句）。

- **活体实测**（37777，工作文件侧原表逐字）：

  | 请求 | 实测 |
  |---|---|
  | `PATCH /api/memory/observations/not-a-uuid` | 400 `{"status":400,"error":"Bad Request","path":"…"}` |
  | `DELETE /api/memory/observations/not-a-uuid` | 400 同上 |
  | `PATCH /api/memory/observations/00000000-…-000000000000`（格式合法、不存在） | 404（空体） |
  | `POST /api/memory/feedback` `{"observationId":"not-a-uuid"}`（id 在体里） | 400 `{"error":"Invalid observationId format: not-a-uuid"}` |

- **文档侧的措辞**（问题所在）: 文档把 `400` 写成「Invalid field types in request body（e.g. `title must be a string`）」、
  `404` 写成「Observation with given UUID not found」——读起来就是「id 写错属于 404」。
  实际不是：path 里的 id 写错是 400。按 404 当「不存在」来写的客户端会落到通用错误分支。
  全库检索确认：中英双语 API 文档从未提及这一情形（`not-a-uuid` / `malformed id` / `畸形` 均 0 命中）。

- ⚠️ 一处差点误记、已撤回（**逐字原文**；工作文件侧只留结论一句）: 初判为「后端对畸形 id 返 404、demo 注释失实」——探针打错了路径。
  `MemoryController` 类级是 `@RequestMapping("/api/memory")`，真实全路径为
  `/api/memory/observations/{id}`；我探的 `/api/observations/{id}` 根本不存在，
  那个 404 是路由未匹配而非 id 校验。改打正确路径后得到 400，
  且**第 102 / 201 轮早已裁决过同一件事**（那句 `Invalid observationId format` **只对 `submitFeedback` 成立**）。
  若照字面采信，会写下一条完全错误的「失实陈述」。
---

## 压缩记录（第 388 轮）

- **第四次启用 C8**（⏸ 待决条目的证据段整体迁入归档；工作文件留 `- **Scope**` 一行 + 一行 `- **Evidence**` 指针，
  `- **Problem**` 的核心判断、`- **Status**`、决策段与计数影响留在工作文件）。
  本轮共迁 **3** 条：**P2-79**（CORS 凭据开关）、**P2-82**（响应体上限）、**P2-75**（畸形 id 返 400）。

  | 记法 | 行 | 字节 |
  |---|---|---|
  | **归档侧持有量**（三条迁出段在本归档内的体量） | **96** | **7306** |
  | **工作文件侧迁出量**（P2-79 **23** 行 / 1425 字节；P2-82 **22** 行 / 1639 字节；P2-75 **11** 行 / 634 字节） | **56** | **3698** |
  | **全文件净变化**（相对本轮写入前的 1274 行 / 148339 字节；同轮还写入了 4 条新条目） | **−23** | **+1332** |

  三个数**不可混用**（第 384 轮记过一次教训）：归档侧持有量含归档自己的框架行，
  工作文件侧迁出量是净差额，全文件净变化还含 4 条新条目的写入。
- **C6 已枯竭**：本轮实测工作文件里的**行内**归档链接共 **26** 处，而 **26** 个目标**互不相同**
  （每个只被引用 1 次）⇒ 引用式转换一条也换不掉；`[a1]`~`[a47]` 共 47 个引用式定义已全部在用。
- **未动 `## Processing Rules`**：压缩杠杆的**定义**是指令不是证据，留在工作文件。
- **C8 的边界纪律（本轮又遇到一次）**：迁出时若条目正文里有「上表第二行」这类**指代**，
  必须把指代一并改成可解析的（「归档里那张对照表第二行」）——否则工作文件会留下一句指向已不存在的表的陈述。
  本轮 P2-79 的 ⚠️ 段正是这种情况，已就地改指。
- **一次自己踩到的坑（探针/流程自身出错）**：第一次替换 P2-79 时，`old_string` 的边界**没有覆盖**条目尾部的
  ⚠️/Status/计数影响三段，而 `new_string` 又把这三段写了进去 ⇒ **尾部被复制成两份**。
  断言只查了「`old_string` 命中 1 次」，**没有查替换后条目是否出现重复** ⇒ 脚本 exit 0、文件里却有重复块。
  处置：读回该条目全文、定位并删除重复的那一份，再继续。⇒ **替换类操作必须读回结果逐段看**，
  只查「命中次数」不够。
- **归档文件创建后不得修改**：头部两处体量数字**迭代至不动点**后写盘；写盘后**独立回读校验**
  （占位符已全部替换、头部两处数字互校、6 个新编号齐备、三条旧证据段确实已从工作文件迁出）。
