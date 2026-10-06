# Health Check History 18 — 第 294–300 轮巡检报告（第 310 轮迁出）

> **归档规则**：巡检报告按轮次保留；达增长阈值时把最早的若干轮逐字迁入此处。
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/health-check-task.md` 越过 `MAX_LINES=1500`（**1533 行**）而迁出
> **7 轮**（第 294–300 轮），工作文件保留第 301–310 轮。

## 第 294 轮 — 2026-10-06T10:02:00+08:00

- **轮次**: 294 | **性质**: **执行用户在第 293 轮后的三项决定**（非轮换审查轮）
- **起点**: `HEAD` = `291fb37`（第 293 轮）

### 决定一：字节上限 102400 → 150000（行数 1000 → 1500）

第 293 轮的系统扫描证明 findings 已达**内容保全压缩的实际下限**（可迁出的内联明细候选数为 0）。
按用户选择，**上调阈值并保持行/字节比例不变**：

- `scripts/doc-growth-check.sh`：`MAX_LINES 1000 → 1500`、`MAX_BYTES 102400 → 150000`
  （两个值本就支持 `MAX_LINES` / `MAX_BYTES` 环境变量覆盖，改的是默认值）
- 同步更新三处引用这两个数字的文档：`docs/drafts/cron-combined-task.md`、
  `docs/drafts/doc-review-task.md`、`docs/drafts/health-check-task.md`（本文件顶部规则段）

**效果立竿见影**：本轮 P1-2 的修复记录（约 1.1 KB）直接写入，findings 达 103434 字节仍为 `OK`，
**再未发生任何压缩**。连续三轮（291/292/293）每轮一次压缩的成本就此消除。

### 决定二：修 P1-2（经用户明确授权）

此前多轮 ⏸ 的理由是「加路径约束属**收窄已发布端点语义**、是对外契约变更」——按纪律这类变更
必须由用户拍板，**现取得授权**。两步都做了：

**① 绑回环**：`application.yml` 补 `server.address: ${SERVER_ADDRESS:127.0.0.1}`。
**保留环境变量覆盖**——需要刻意对外暴露时仍可 `SERVER_ADDRESS=0.0.0.0` 启动。

**② 路径约束**，下在 `FileReadTool` 内部——它是**四条调用路径的唯一收口**（`ToolsController`、
`SessionLifecycleController` ×2、以及模型经 `@Tool` 的调用）。要点：

- 相对路径按根解析；**绝对路径仅当已在根内才接受**（`Path.resolve` 对绝对参数会直接返回该参数，
  不会被根覆盖，所以必须显式判 `startsWith`）。
- 逃逸在**读取之前**拒绝，**响应不回显内容**。
- **两重检查不可互相替代**：`normalize()` 剥掉词法 `../` 但**不跟符号链接**；`toRealPath()` 跟链接
  但对不存在的路径会抛——而「路径写错」正是常态。代码注释与测试都写明了这一点。
- 三个控制器的 `?path=` 默认值由绝对路径 `/tmp/hello.txt` 改为相对路径 `hello.txt`
  （沙箱化后原默认值必然被拦；`~` 不做 shell 展开，`~/.ssh/id_rsa` 会按字面相对路径解析而落空）。

**活体验证**（本轮自启 demo 于 37778，验证后已停）：

| 探针 | 结果 |
|---|---|
| `hello.txt` | **正常返回** `sandbox-probe` |
| `/etc/passwd` | **读取前被拒**，无内容 |
| `/Users/<me>/.ssh/id_rsa` | **读取前被拒**，无内容 |
| `../pom.xml` | **读取前被拒**，无内容 |
| `/etc/../etc/passwd` | **读取前被拒**（`normalize` 归一化） |
| `~/.ssh/id_rsa` | 按字面相对路径解析 → `NoSuchFileException`，**未触及真实文件** |
| `lsof` 绑定 | `*:37778` → **`127.0.0.1:37778`** |
| 从 `10.166.1.125:37778` 访问 | **连接失败**（000）——网络可达性风险消除的直接证据 |

**测试**：新增 `FileReadToolTest` **8 条**（含符号链接逃逸、根内绝对路径、父目录穿越、空白与 null、
以及「根内但不存在」必须报**读取错误**而非**越界错误**——两者不可混淆）。demo 套件 **25 → 33 全绿**。

**文档同步**：demo README 端点表下的警告由「风险存在、不要在共享网络上跑」改为如实描述现状
（含「可用 `SERVER_ADDRESS` 覆盖」与「由 `FileReadToolTest` 钉住」）。findings 的 P1-2 改为
✅ 已修并写明授权来源与实测证据；**P1 Open 2 → 1**（仅剩 P1-1）。

### 决定三：push 暂不处理

按用户选择，继续本地提交，push 照常尝试。**本批 22 次提交积压本地**。

### 完整验收（指纹已变，门控生效）

改动含 `examples/cortex-mem-demo/*` 与 `scripts/doc-growth-check.sh`，指纹由 `7c7dc3fa…`
变为 `5c55196e…`（`.sh` 计入指纹），故跑完整验收。

**新鲜度论证**：本轮改动在 `examples/` 与 `scripts/`，`backend/pom.xml` 对 `cortex-mem-demo`
**零引用**、`.sh` 也不编入后端，故 37777 无需包含本轮改动；启动后触及 `backend/` 的三个提交
（`ae21c6b` / `ed11dee` / `665b5aa`）**非注释增删行数各为 0**，后端无未提交改动——
运行中的实例仍是行为最新的后端代码，直接用于验收。

- **回归 45 通过 / 0 失败 / 1 跳过**（`Passed` 计数本就不是不变量）
- **`EXTRACTION_ENABLED=true` Phase 3 验收 25 / 0 / 0**
- **基线已推进** → `accepted_commit: 291fb37` / `code_fingerprint: 5c55196e…`

### 本轮遗留的持续义务

P1-2 的修复是**代码改动**，按既定规则须**连续 3 轮**深入检查无新问题方可结案；
若发现新问题或再次修改，计数重置。

### 下一轮

代码方向：**Demo**；文档方向：**API 文档**（doc round 195）。

## 第 295 轮 — 2026-10-06T10:26:00+08:00

- **轮次**: 295 | **代码方向**: Demo（P1-2 修复复查**第 1 轮**） | **文档方向**: API 文档（doc round 195）
- **起点**: `HEAD` = `cb0ac07`（第 294 轮）

### 健康预检

活体 `/api/health` → ok；指纹 `5c55196e…`（883 条）。仅 37777 在监听（java pid 42092）。

### 代码方向（Demo）—— P1-2 修复复查第 1 轮：**通过**

上轮验证的是「根内可读、根外被拒」这条**主路径**，但**没有验证「根本身是特殊路径」时解析器会怎样**，
而那恰恰是安全逻辑最容易出错的地方。新增 `FileReadToolRootEdgeCaseTest` **7 条**，专打六类边界：

1. **根本身是符号链接** —— **本轮风险最高的一条**。macOS 上 `/tmp` → `/private/tmp`、
   `/var` → `/private/var` 都是符号链接，而 `toRealPath()` 会把它们解成真实路径；
   **若比较时两侧口径不一致（一边解一边不解），合法文件会被全部误判为越界**。
   实测正确：根配成符号链接时 `inside.txt` 正常读出内容。**这验证了上轮那句
   「用 `root.toRealPath()` 作比较基准」不是随手写的，而是必需的。**
2. **根不存在** —— 实际行为是返回 `NoSuchFileException` 而非「越界」。**这是安全的**
   （根不存在则其下不可能有可读文件），但与我第一版断言的消息形状不符。
3. **根不存在 + 路径与根共享字符串前缀**（`/definitely/not/here-elsewhere`）—— **仍被正确拒绝**，
   因为 `Path.startsWith` 按**路径段**比较而非按字符串比较。这是最经典的沙箱 bug，现已钉住。
4. 根配置带尾部斜杠 —— 正常。5. 根配置为空串 —— 落到进程工作目录，根外绝对路径仍被拒。
6. 根内的 `./a/../a/f.txt` 点段 —— 正确归一化。

**一处「失败」是我的断言写错，不是代码有洞**：根不存在时 `Files.exists` 为 false，
`toRealPath()` 那段**根本不执行**，于是走读取分支得到 `NoSuchFileException`——
**无内容泄漏、不抛异常**。我把断言从「消息应长什么样」改成「真正要验证的性质」
（拿到的是字符串不是异常、响应里没有任何内容），并**补了一条**真正有意义的相邻断言
（根不存在时共享前缀的路径仍被拒）。

**demo 套件 33 → 40 全绿。P1-2 复查计数 1/3，本轮未发现新问题。**

顺带补了一处 README：我把 `?path=` 默认值改成了 `hello.txt`，而该文件不在仓库里，
新鲜检出会得到一条 `NoSuchFileException`——**这是我引入的行为，用一行说明交代清楚**。

### 文档方向（API 文档，doc round 195）—— 零缺陷

取**分页契约**作角度，因为 `API.md` 对它写下了**可证伪的具体数字**：
五个端点共用 `Math.min(Math.max(1, limit), MAX_PAGE_SIZE)`，`?limit=0` 与 `?limit=-5` 返 1 条、
`?limit=500` 返 100 条、`?limit=7` 返 7 条，`offset` 下界为 0。

**探针自身出错三处，全部先识别再采信**——若照字面采信，会写下「四个端点的 limit 全部失效」这种
完全相反的不实结论：

1. **用了没有数据的项目** `/tmp/test-project`（实际只有 1 条观测），于是 `limit=7` 返 1 条被误判为不符。
   改用库中真实有数据的项目（`/tmp/phase3-acceptance-test` 22943 条、`openclaw` 4610 条、
   `claude-mem` 368 条）后**全部对上**。
2. **响应键猜错**：`/api/search` 实际用 `observations` 而非 `results`。
3. **`/api/search/by-file` 参数名猜错**：实际是 `filePath`（且要求绝对路径），我传了 `file=`。

**终验（修正后）**：

| 端点 | limit=0 | limit=-5 | limit=7 | limit=500 |
|---|---|---|---|---|
| `/api/observations` | 1 | 1 | 7 | 100 |
| `/api/summaries` | 1 | 1 | 7 | 100 |
| `/api/prompts` | 1 | 1 | 7 | 100 |
| `/api/search` | 1 | 1 | 7 | 100 |

**16/16 与文档声明完全一致。** `offset=-5` 与 `offset=0` 返回同一批（确被钳到下界），
且 `offset=5` 返回**不同**批——**后者证明 offset 真的生效，而不是恒为 0**（否则「钳到 0」这条断言
也可能被一个永远返回 0 的实现蒙混通过）。`hasMore` 在 limit=3 / 100 且库中 22943 条时均为 `true`；
越界 `offset=22943` 返回空页。

**`/api/search/by-file`**：文档**诚实标注**「只验过下界，因为没有 fixture 能匹配同一路径下多于一条记录」。
**我第一版用了一个编造的 `filePath`，limit=0 返 0——正是该标注所警告的情形**。
从库里取出真实文件路径后重测：`limit=0 → 1`、`limit=-5 → 1`，**与文档一致**。
（`limit=7` 与 `limit=500` 都返 2，因该路径只匹配 2 条，**上界 100 在此端点无法被观测**——
这正是文档自己声明的覆盖限制，**不是新缺陷**。）

**本轮零改动（API 文档本身）**。

### 变更检测与完整验收

新增一个测试类 → 指纹 `5c55196e…` → `f06cc9a8…`（885 条），门控生效。
新鲜度论证同第 294 轮：本轮改动在 `examples/cortex-mem-demo/src/test/`，`backend/pom.xml` 对 demo 零引用。

- **回归 45 通过 / 0 失败 / 1 跳过**
- **`EXTRACTION_ENABLED=true` Phase 3 验收 25 / 0 / 0**
- **基线已推进** → `accepted_commit: cb0ac07` / `code_fingerprint: f06cc9a8…`

### 下一轮

代码方向：**Demo**（P1-2 复查**第 2 轮**）；文档方向：**SDK README**（doc round 196）。

## 第 296 轮 — 2026-10-06T10:52:00+08:00

- **轮次**: 296 | **代码方向**: Demo（P1-2 修复复查**第 2 轮**） | **文档方向**: SDK README（doc round 196）
- **起点**: `HEAD` = `c6d14c9`（第 295 轮）

### 健康预检

活体 `/api/health` → ok；指纹 `f06cc9a8…`（885 条）。仅 37777 在监听。

### 代码方向（Demo）—— P1-2 修复复查第 2 轮：**通过**（改从 HTTP 面切入）

上一轮验的是**解析器**对「根是什么」的六类边界。本轮换角度：**不经解析器单测，直接打 HTTP**，
因为上轮完全没覆盖的恰恰是**解码时机**——Spring 的 `@RequestParam` 会**先做 URL 解码再交给解析器**，
那么 URL 编码的穿越符会不会在校验之前就已被还原成真正的 `../`？这是只能实测、不能推理的问题。

自启 demo 于 37778，13 组探针全部通过：

| 探针 | 结果 |
|---|---|
| 根内相对路径 `hello.txt` | 正常返回 |
| **URL 编码的 `%2e%2e%2f`** | **读取前被拒** ← **本轮最关键的一条** |
| URL 编码的绝对路径 `%2fetc%2fpasswd` | 读取前被拒 |
| 双重编码 `%252e%252e%252f` | 还原为字面文件名，落空 |
| 混合编码 `..%2f%2e%2e%2f` | 读取前被拒 |
| 反斜杠穿越（Linux 上是字面文件名） | 落空 |
| NUL 字节 `%00` | 读取前被拒（`InvalidPathException` 被接住） |
| 4000 字符超长路径 | 安全，**不返回 500** |
| 纯空白 / `.` | 正确处理 |

**另外两个 `readFile` 调用点也逐一验过**（不能只修一个入口）：`/demo/session/tool?path=` 与
`/demo/session/lifecycle` 的 `toolPath=`，编码穿越**同样被拒**，根内路径**同样能读**。
加上 `/demo/tool` 与 `@Tool`（同一方法），**四条调用路径全部覆盖**。

**拒绝消息指明允许的根（便于排错），且不回显任何文件内容。**

**探针自身出错两处，先识别再采信**：①第一版把响应体**截断到 200 字符再解析**，
而错误消息里含完整 4000 字符路径，`json.loads` 因此失败返回 `None`——**看起来像「超长路径没被拦」**；
②端点 B 我用了 `/tool`，实际因类上有 `@RequestMapping("/demo/session")` 而在 **`/demo/session/tool`**，
返回 404；另有一处把响应键猜成 `toolResult`，实际是 **`tool_result`**（snake_case）。
**三处若照字面采信，会记下「另外两个端点没被约束」这个完全相反的结论。**

**P1-2 复查计数 2/3，本轮未发现新问题。** 验证后已停 demo，工作区已清理。

### 文档方向（SDK README，doc round 196）—— 零缺陷

第 193 轮覆盖了 Python 与 Go，本轮取 **Java 与 JS**（另两家），角度是**公开方法清单与可证伪计数**。

**Java README**：
- **接口 25 个方法全部被 README 提及，零幻影**；且用**不依赖单行匹配**的方式（合并跨行签名后整体扫描）
  复核，**仍是 25**——不是正则凑巧漏掉了跨行签名。
- README 写死了「**10 of the 25** public methods retry」与「**remaining 15** never retry」。
  逐方法枚举 `executeWithRetrySilent` / `executeWithRetry` / `executeWithRetryReturn` 的调用点：
  **恰好 10 个走重试、恰好 15 个不走**，且那 15 个里确实包含 README 点名的「every other read:
  `search`, …」。**25 / 10 / 15 三个数字全部精确吻合。**
- **接口 25 个方法在 impl 中全部有实现**，无「接口有、实现缺」。

**JS README（英中双份）**：25 个 `async` 公开方法**全部被提及**，零遗漏。
唯一报出的「幻影」`close` 是**我的正则只匹配 `async`** 所致——`close(): void` 确实存在（同步方法）。
**探针错，不是文档错。**

**附带独立印证**：Python README 在第 193 轮写的「the Java SDK retries 10 of its **25** methods」，
其中的 **25** 本轮被独立核实为真——**上一轮的跨家断言在两轮之后仍然站得住**。

**本轮零改动（两份 README 本身）。**

### 变更检测

本轮**未改任何代码**（仅 `docs/drafts/` 下四份工作文件）。指纹仍 `f06cc9a8…` / 885 条，
**与基线一致** → 按既定规则**不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Demo**（P1-2 复查**第 3 轮**，达成即结案）；文档方向：**运维/用户指南**（doc round 197）。

## 第 297 轮 — 2026-10-06T11:20:00+08:00

代码方向：**Demo**（P1-2 复查第 3 轮 → **3/3 结案**）；文档方向：**运维/用户指南**（doc round 197）

### 健康预检

后端 37777 在监听（java pid 42092，`127.0.0.1:37777`，启动 2026-10-05 17:51:24），
`GET /actuator/health` 正常。37778–37781 / 37790 / 37795 全空闲。指纹 `f06cc9a8…` / 885 条。
doc-growth 全部 OK（health-check 574/46870、backend-review-findings 960/103434、
backend-fix-progress 381/19243、patrol-task 137/9411、doc-review-task 237/87273）。

### 代码方向（Demo）—— P1-2 复查第 3 轮通过，**结案**

前两轮验的是「拦得住吗」：第 295 轮走解析器边界（7 条，含最高风险的**根本身是符号链接**，
证伪）；第 296 轮走 HTTP 面（13 组，含 `%2e%2e%2f` 编码穿越实测被拦、四条调用路径全覆盖）。

**本轮换了一个此前从未验过的角度：我的修复有没有破坏 demo 的核心演示。**

`FileReadTool` 在第 294 轮新增了构造器依赖（`DemoProperties`）。构造器注入一旦出问题，
Spring 的 AOP 代理可能起不来，**demo 赖以展示的 `@Tool` 捕获路径会整体静默失效**——
而那正是这个 demo 的核心。所以必须验它还活着。

`CortexToolAspect.interceptToolExecution`（`@Around`，`CortexToolAspect.java:50`）捕获
`toolInput` 与 `toolResponse = {"result": truncate(result.toString())}`（`MAX_VALUE_LENGTH=4000`），
写入 `mem_pending_messages`。

**活体验证**（启动 demo → 调 `/demo/session/lifecycle`）：

| 探针 | 捕获到的 `tool_input` | 判读 |
|---|---|---|
| `toolPath=hello.txt` | `{"path=hello.txt"}` | 合规文件**仍被捕获** |
| `toolPath=../../etc/passwd` | `{"path=../../etc/passwd"}` | **被拒路径同样被捕获** |

第二行值得单独说：`readFile` 被拒时**返回错误串而不抛异常**，所以 `joinPoint.proceed()` 正常返回，
aspect 照常捕获。**若当初把拒绝实现成抛异常，这条捕获记录就不会出现**——也就是说，
「被拒」与「可观测」并不矛盾，两者同时成立。

**结论：pending message 的存在即证明 aspect 仍在触发，构造器新增依赖没有破坏 AOP 代理。**
观测 `status=failed` 是已知环境限制（本机 LLM chat 端点密钥失效，摘要生成必失败），**非本次回归**。

**P1-2 复查计数 3/3，达成结案。** 验证后已停 demo（37778），`hello.txt` 探针文件已清理。

### 文档方向（运维/用户指南，doc round 197）—— 零缺陷

**①断言清扫（因我自己的改动而做）**：第 294 轮给 demo 绑了回环、加了读文件根约束，
所以先扫遍 `README.md`、`DOCKER_README.md` / `-zh-CN`、`docs/DEPLOYMENT.md` / `-zh-CN`、
`docs/TESTING.md` / `-zh-CN`、`docs/DEVELOPMENT.md`、`docs/ARCHITECTURE.md` / `-zh-CN`、
`docs/API.md`、`AGENTS.md` 中 sandbox／任意文件／37778 相关表述。

**仅两处命中**，逐一看过：都是 **compose 的后端 `SERVER_PORT=37778` 示例**，与 demo 绑定无关。
**无过期表述遗留。**（这是「修复后必做」的一次清扫，不是例行公事——本轮若不扫，
就会把「demo 仍对外网开放」这种不存在的风险写进报告。）

**②compose ↔ 指南环境变量双向对拍**：

| 方向 | 结果 |
|---|---|
| `docker-compose.yml` 23 个插值变量 → 四份指南 | **各 23/23 全覆盖，零遗漏** |
| compose 变量 → 指南里没提的 | **无** |

四份指南指 `DOCKER_README.md`、`DOCKER_README-zh-CN.md`、`docs/DEPLOYMENT.md`、`docs/DEPLOYMENT-zh-CN.md`。

**③`SERVER_ADDRESS` 两处表述逐字核对，均准确**：
- `DOCKER_README.md:85` 明说「被 compose 写死为 `0.0.0.0` 且**不可覆盖**」——与 `docker-compose.yml:60`
  （`SERVER_ADDRESS: 0.0.0.0`）**逐字相符**。这正是 P2-70 记录的那个不能单方面收紧的绑定。
- `DOCKER_README.md:288-291` 正确解释裸 `docker run` 为何**必须**自行设置
  （容器内 loopback 经发布端口不可达），并指出 compose 已设。**第 283 轮补的那句，理由依然成立。**

**④`CLAUDE_MEM_MODES_DIR` 判定为应用配置而非 compose 变量**：
对应 `backend/src/main/resources/application.yml:171` 的 `modes-dir: ${CLAUDE_MEM_MODES_DIR:}`。
它确实只出现在后端配置里，compose 没有引用它——**文档不写它是对的**。

**探针错一处，先识别再采信**：第一版脚本只抓 `${}` 插值，漏掉
`docker-compose.yml:28/29/31` 的 `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`
三个**字面容器环境键**，一度把它们报成「三份指南都漏了」。**是探针错，不是文档错。**

**本轮零改动（运维/用户指南本身）。**

### 变更检测

本轮**未改任何代码**（仅 `docs/drafts/` 下四份工作文件）。指纹仍 `f06cc9a8…` / 885 条，
**与基线一致** → 按既定规则**不跑完整验收、不推进基线**。

### 下一轮

代码方向：**Demo**（新一轮审查）；文档方向：**设计文档**（doc round 198）。

## 第 298 轮 — 2026-10-06T11:58:00+08:00

代码方向：**Demo**（零缺陷）；文档方向：**设计文档**（doc round 198，一项已修）

### 健康预检

后端 37777 在监听（java pid 42092，`127.0.0.1:37777`），`/actuator/health` 全 UP
（db / diskSpace / messageQueue / ping）。37778–37781 / 37790 / 37795 全空闲。
指纹 `f06cc9a8…` / 885 条。doc-growth 五份活动文档全 OK。

### 代码方向（Demo）—— 零缺陷，但**我自己差点写下一条假缺陷**

P1-2 已于第 297 轮结案，本轮取新角度。**读 `DemoErrors` 时发现它的类 Javadoc 里有可证伪的计数声明**，
而且这个声明**本身就是 P2-59 记录过的根因**——这给了本轮一个天然的复核靶子。

**①独立重推 P2-59 的根因计数（finding 写下约 30 轮之后）**

P2-59 写的是「12 个控制器共 40 个 `catch (Exception e)`，只有 3 个走到 `DemoErrors`，其余 10 个一个都没有」。
本轮**不查文档、直接从源码重新数**：

| 声明 | 实测 | 判读 |
|---|---|---|
| 12 个控制器 | 12 | ✅ |
| 40 个 `catch (Exception e)` | **40**（另 2 个在 `DemoErrors`/`FileReadTool`，非控制器） | ✅ |
| 只有 3 个走 helper | `ObservationsController` 2 + `FeedbackController` 1 | ✅ |
| 其余 10 个控制器没有 | 12 − 2 = 10 | ✅ |
| 「答一个平的 500」 | 37 个 catch 块**逐个**核对，**全部** `internalServerError()` | ✅ |

**数字在约 30 轮之后仍与源码逐字吻合，没有漂移。**（`ProjectsController` 一个 catch 都没有，
严格说它不是「答 500」而是「没有失败路径」——但它只回一个配置 map，不影响该句的实质结论，不判缺陷。）

**②追 Javadoc 最后一句的跨仓库断言——先是我的探针错了**

`DemoErrors` 断言：「Go demo 把 NotFound 映射成 404，Python 与 JS demo 原样透传后端状态」。
我先按目录名找另外三个 demo，`find -type d -iname "*demo*"` **只找到 Java 一个**——
差一步就记下「另外三个 demo 不在本仓库、该断言不可验证」。**这是探针错**：
Python 与 JS 的 demo 就在本仓库，目录叫 **`examples/http-server`** 而非 `demo`；
Go 的在 `go-sdk/cortex-mem-go/examples/http-server/main.go`。

**③我读错了那一行，实验把我驳回**

Python demo `app.py:52` 读作
`status = exc.status_code if 400 <= exc.status_code < 600 else 502`。
我把它看成「4xx 才透传、5xx 折成 502」，于是准备记一条新缺陷：
*Go 的注释说三家都 surface 后端真实状态码，而 Python 把 5xx 换成 502，是失实陈述。*

**用 Flask test client 做受控实验**（在该 app 上注册一条抛 `APIError(n)` 的路由，走 demo 自己的
`@app.errorhandler(APIError)`，即真实处理器而非副本）：

| 注入的后端状态 | demo 实际应答 | | 注入的后端状态 | demo 实际应答 |
|---|---|---|---|---|
| 400 / 404 / 429 | 400 / 404 / 429 | | 500 / 502 / 503 | **500 / 502 / 503** |
| 0 / 200 / 301 / 399 | 502 | | 599 / 600 / 700 | 599 / 502 / 502 |

**我的读法是错的。** `400 <= status < 600` 覆盖 **400–599**，**5xx 本来就在区间内**；
那个 `else 502` 兜的是「非错误状态码漏进错误路径」（0/2xx/3xx/≥600），不是 5xx 折叠。
**若不跑这一步，就会把一条完全站不住的「失实陈述」写进记录。** 三条跨仓库断言**全部为真**：
Go 确有 4 处 `errors.Is(err, ErrNotFound)` → 404；JS 确为 `errorJson(res, err.statusCode, …)` 无钳位；
Python 实测 400–599 原样透传。

**本轮代码方向零缺陷、零改动。**

### 文档方向（设计文档，doc round 198）—— 一项已修

`docs/drafts/phase-3-design/` 的索引 `index.md` 有一张 30 行的大小表，底部还有一句
**给出精确字节数**的合规声明。**精确数字就是为了可核对**——于是逐行核对。

**①30 行里 18 行的「大小」对不上**，且十进制 KB 与 KiB **两种口径都解释不了**：

| 文件 | 文档 | 实测 | | 文件 | 文档 | 实测 |
|---|---|---|---|---|---|---|
| `23.md` | 8KB | **21.11 KiB**（差 2.6 倍） | | `19.md` | 8KB | 9.38 KiB |
| `25.md` | 42KB | **43.85 KiB** | | `21.md` | 11KB | 13.18 KiB |
| `15.md` | 21KB | 23.49 KiB | | `17.md` | 2KB | 5.12 KiB |
| `10.md` | 7KB | 8.50 KiB | | `0.1.md` | 5KB | 6.58 KiB |
| `11.md` | 6KB | 7.43 KiB | | `24.6.md` | 10KB | 12.44 KiB |

**②这列数字不是「后来漂移」，是「当时就没对」——决定性证据**

`index.md` 上一次被改是 `52a341c`，提交标题就写着
「**fix a stale size-compliance figure**」。若只是内容涨了才失准，那次修完就该对上。
但把子文档在该 commit 时的字节数与现在逐一比对：**`10.md`(8,700)、`11.md`(7,609)、`15.md`(24,053)、
`21.md`(13,492)、`24.6.md`(12,734)、`18.md`(3,395) 六个文件字节数一字未变，表格却仍写着
7KB / 6KB / 21KB / 11KB / 10KB / 2KB。**上两次修改之间，没有任何一处内容变化能解释这个偏差。**

**③页脚那句精确声明确实漂了，但漂的方式很具体**：`44,374 字节` 在 `52a341c` 时**是准确的**
（正是 `25.md` 当时的大小），10-04 的两个提交（`5af3e8e`、`4acc0f3`）把它推到 **44,898**，
无人再更新。**即：页脚被认真修对过一次，表格从未被认真修过。**

**已修（纯文档，零行为变更）**：
- 30 行大小**全部由文件系统实测重算**（十进制 KB 向下取整），改完**复核 30 行 0 不符**；
- 页脚字节数改为实测 `44,898`，KB 值改为 `44.9`；
- **新增「大小口径」说明**（十进制 KB 向下取整 + 核验命令 `ls -l docs/drafts/phase-3-design/*.md`）
  ——**这一条才是防复发的关键**：口径不写出来，下一个人只会照着改数字、再漂一次；
- 根指针 `phase-3-design.md` 里同样失准的两个体积一并校正（`2.md` 29KB→30KB、`25.md` 42KB→44KB）。

**④顺带核实，均无问题**：表内 30 个文件全部存在、**零幻影零遗漏**；
「建议阅读顺序」引用的文件全部可解析；两个章节锚点可定位
（`0.1.md` §Bug 2 → 第 39 行、`11.md` §11.3 → 第 52 行）；
全部文件**无一超过 50KB**，合规结论本身成立——**错的只是数字，不是结论**。

### 变更检测

本轮改动**全部是 `.md`**（`index.md` + `phase-3-design.md`）。指纹仍 `f06cc9a8…` / 885 条，
**与基线一致** → 按既定规则**不跑完整验收、不推进基线**。表格结构复核：30 行**全部 5 个管道、结构统一**。

### 下一轮

代码方向：**Backend**（Demo 已轮到尽头）；文档方向：**架构文档**（doc round 199）。

## 第 299 轮 — 2026-10-06T11:58:00+08:00

代码方向：**Backend**（一项已修 P2-73）；文档方向：**架构文档**（doc round 199，零缺陷）

### 健康预检

37777 在监听（pid 42092，`127.0.0.1:37777`），health 全 UP。指纹 `f06cc9a8…` / 885。
doc-growth 五份活动文档全 OK（findings 960 行 / 103434 字节，新上限 150000 下仍宽裕）。

### 代码方向（Backend）—— P2-73 三份配置的日志开关指向已不存在的包

角度取第 290 轮确立的那条教训：**「声明存在的配置」不等于「生效的配置」**。

**①先把「有没有死旋钮」这件事整体问一遍，再挑一个细看**

- 全仓 **83 个后端源文件全部**是 `package com.ablueforce`；`src/main/java/com/claudemem` **目录不存在**。
- 逐个查 yml 里带 `${}` 的叶子键（**真正对外的旋钮**）共 18 个，其中 9 个在代码里没有 `${}` 引用——
  **但 9 个全部有解释**：`server.port` / `server.address` / `spring.datasource.*` 走 Spring Boot 内建松散绑定，
  `app.memory.extraction.*` 走 `ExtractionConfig` 的 `@ConfigurationProperties` 类绑定。**无死旋钮。**
- 视线随即落在 `application.yml` 的一行 `logging.level.com.claudemem: INFO` 上——**它不属于上面 18 个**
  （不带占位符），所以「有没有人读」这个问题得换个问法。

**②结论确凿，且比预想严重**

不止 `application.yml`：**三份配置都指向这个不存在的包**——
`application.yml:137`（INFO）、`application-dev.yml:33`（**DEBUG**）、`application-prd.yml:18`（INFO）。
而**活体 37777 进程的 classpath 上 `com/claudemem/` 条目为 0**（fat jar 同样为 0，pom 无相关依赖）。
真正的 `com.ablueforce` 在**任何**日志配置里都没有级别。

**按 profile 分级看后果**：`prd` 与默认档是 INFO，**恰好等于 Spring Boot 默认值，所以看不出任何异常**——
这也是它能潜伏这么久的原因。**只有 dev profile 是 DEBUG**，而那一档本来就是为调试准备的：
它的另外三个 key（`org.springframework.ai` / `org.springframework.web.client` / `org.springframework.http`）
都是真实第三方包、**照常生效，唯独应用自身这一条是死的**。于是用 `--spring.profiles.active=dev` 排障的人
能拿到 Spring AI 的 HTTP 明细日志，却**一条应用 DEBUG 都看不到**，且没有任何迹象指向「配置写错了」。

**③活体双向实测（在 37790，两次只差这一个词）**

| | DEBUG 总数 | 来自 `com.ablueforce.cortexce` | 来自 `org.springframework.ai`（未改，作对照） |
|---|---|---|---|
| 修复前 | 3 | **0** | 1 |
| 修复后 | 2228 | **2225** | 1 |

**对照组两次都是 1，这正是断言成立的前提**——弱版本「有没有出现 DEBUG」在修复前那次也会通过（总数 3 ≠ 0）。

**已修**：三处各改一个词，`com.claudemem` → `com.ablueforce`。
前两处 INFO 与默认值相同、**行为零变化**；`application-dev.yml` 的 DEBUG 自此真正生效，
**这是该档配置一直在声称要做的事**。

**⚠️ 副作用已记入 P2-73 并须知悉**：**本机常驻的 37777 正是以 `--spring.profiles.active=dev` 运行的**，
下次重启它会开始输出约 2200 行量级的应用 DEBUG 日志。**若不希望 dev 档变吵，把那一行改成 `INFO` 即可**——
那是口味选择不是缺陷，留给使用方决定。

**探针错三处，全部先识别再采信**：
①第一版 YAML 栈解析器**从不出栈**，拼出 `server.app.memory.spring…` 这种荒谬路径、报「60 个旋钮全部未引用」——
**完全是解析器的错**，重写为「只抽取带占位符的叶子键 + 正确出栈」后才得到可用结论；
②`quality-threshold` 与 `recovery-interval-ms` 一开始被判为「声明却无人读」，实际前者走
**全限定** `@org.springframework.beans.factory.annotation.Value(...)`、后者走 `@Scheduled(fixedRateString=...)`，
**我的正则只认 `@Value("${`**；
③统计 DEBUG 行时按字面 `[DEBUG` 匹配，实际文本是 `[39mDEBUG[0;39m`——**ANSI 转义把那半个 `[` 吃掉了**，
于是「DEBUG 总数 0」是假的，实际有 3 行。**若照字面采信，会写成「修复后应用 DEBUG 仍为 0 行」。**

### 完整验收（指纹变化，必须跑）

指纹 `f06cc9a8…` → **`a18e34e8…`**（记录数 885 不变，改的是三份 yml 的内容）。

**新鲜度闸门第三次适用，且这次没有豁免路径**：上一轮第 294 轮那种「改动不在后端二进制内、
37777 仍然新鲜」的情况**不适用**——本轮改动**就在后端二进制里**，而 37777 早于它启动。
做法：**重新 `mvn package`，在 37790 起新 jar**，并**从 37777 的运行进程复制环境变量**
（`ps eww` 取 `KEY=VALUE`，全程不打印取值），使 `ChatModel` 正常创建、抽取能力与被验收实例一致；
开跑前先确认 jar 内的 `application-dev.yml` 已是 `com.ablueforce: DEBUG`。

**结果：回归 45 通过 / 0 失败 / 1 跳过；Phase 3 验收 25 / 0 / 0。基线推进。**

**一处自曝**：复制环境的临时文件 `/tmp/cortex-env.*`（含 API 密钥，权限 600）第一次用通配符清理时
**静默失败、文件仍在**，是随后单独 `ls` 才发现并删除的。
**凡是把密钥物化到文件的临时步骤，清理必须逐个确认、不能只发一条带通配符的删除。**

验证后 37790 已停（**只停本轮自己启动的**），37777 保持运行。

### 文档方向（架构文档，doc round 199）—— 零缺陷

**①双语结构完全对齐**：EN 与 ZH **各 13 个 H2、23 个 H3**，13 个 H2 标题**逐条对应且同序**
（`Table of Contents`↔`目录` … `Future Architecture Improvements`↔`未来架构改进`）。行数差（1314 vs 1280）
只是正文详略，**结构零漂移**。

**②技术栈的版本断言逐条落到活体**：

| 文档 | 活体实测 | 判读 |
|---|---|---|
| Java 21 | `<java.version>21</java.version>` | ✅ |
| Spring Boot 3.3.13 | pom parent `3.3.13` | ✅ |
| PostgreSQL 16 | `SHOW server_version` = **16.8** | ✅ |
| pgvector 0.8 | 已装 **0.8.1**（可用 0.8.2） | ✅ major.minor 正确，未过度精确 |

**③六处行号断言逐条命中**（本轮**未编辑**被引用文件，故行号有效）：
`OffsetPageRequest.java:105-116` 且 **109 行正是 instanceof 那行**、
`ApiRequests.java:49` 的 `record ToolUseRequest`、`SummaryGenerationService:83`（`@Async`）与 `:108`
（唯一的 Java 内拼 prompt）、`MemoryRefineService:91`（`@Async`）、`ObservationEntity.getFactsJson():282`；
`prompts/` 恰为 4 个文件（`init`/`observation`/`summary`/`continuation`）。

**④环境变量表 17 行默认值逐条对上**；其中 `SPRING_PROFILES_ACTIVE` 默认 `prd` 追到
**`docker-compose.yml:52` 的 `${SPRING_PROFILES_ACTIVE:-prd}`**——不是空口来的。

**⑤两条大注记属实**：
prd/dev 不一致那条，prd 实为 `https://api.openai.com` + `gpt-4o` + `text-embedding-3-small` + **1536**，
与注记逐字相符；
「没有 `default` 模式」那条，模式目录实测 **`code` 1 个 + `code--*` 30 个 + `email-investigation` 1 个 = 32**，
**零个意外文件**，且 **`default.json` 确实不存在**。

**本轮零改动（架构文档本身）。**

### 下一轮

代码方向：**Java SDK**（轮转回到起点）；文档方向：**运维/用户指南**（doc round 200，
补上第 197 轮跳过的这一档，五个方向走完一轮再回到 API 文档）。

## 第 300 轮 — 2026-10-06T12:30:00+08:00

代码方向：**Java SDK**（一项已修：P2-74 补注）；文档方向：**运维/用户指南**（doc round 200，零缺陷）

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `a18e34e8…` / 885。doc-growth 全 OK。

### 代码方向（Java SDK）—— 序列化契约零缺陷；重试默认极性的分歧此前无处记载

**①六个会构造 wire map 的 DTO，逐键对拍后端 `@JsonProperty`**

| SDK DTO | 发出的键 | 后端 record | 判读 |
|---|---|---|---|
| `ExperienceRequest` | task, project, count, source, requiredConcepts, userId | 同名 record | **6/6 全等** |
| `ICLPromptRequest` | task, project, maxChars, userId | 同名 record | **4/4 全等** |
| `SessionEndRequest` | session_id, cwd, last_assistant_message | 同名 record | **3/3 全等** |
| `UserPromptRequest` | session_id, prompt_text, cwd, prompt_number | 同名 record | **4/4 全等** |
| `ObservationRequest` | session_id, tool_name, **cwd**, tool_input, tool_response, prompt_number, source, **extractedData** | `ToolUseRequest` | **8/8 全等**（仅顺序不同） |

**两个「反直觉」的地方其实是对的**：`projectPath` 映成 `cwd` 而不是 `project_path`，
`extractedData` 保持**驼峰**而不是 `extracted_data`——两者都因为**后端自己的 record 就是这么声明的**
（`@JsonProperty("cwd")` / `@JsonProperty("extractedData")`）。**若按「全局 SNAKE_CASE」去「修正」SDK，反而会改坏。**

**②`SessionStartRequest` 只发 3 个、后端接受 7 个——查清那 4 个是什么**

`cwd`（后端注明是 `project_path` 的**别名**）、`is_worktree` 与 `parent_project`
（后端注明「**仅写入应用日志、不落库、不影响任何行为**」）、`projects`（多项目上下文）。
**核实 `WorktreeDetector` 确实零调用方**（除自身 logger 与那条 Javadoc 外无任何引用）——
**后端自己的断言为真**。又查 `projects`：**四家 SDK 全部为 0**，
所以 Java 并未落后，**是这项后端能力四家皆无客户端面**。**不是 SDK 缺陷。**

**③重试：三比一的默认极性分歧，此前无处记载**

四家在**状态码规则上完全一致**——都重试 429/502/503/504、都**不重试 500**。
分歧在**无法识别的异常**上：

| SDK | 未识别的异常 | 其自述 |
|---|---|---|
| Go `IsRetryable` | **false** | 「default is "not retryable": only positively identified as transient」 |
| Python `is_retryable_error` | **false** | 「matching the **fail-closed** rule」 |
| JS `isRetryable` | **false** | 末尾 `return false` |
| **Java `isRetryable`** | **true** | 「Non-HTTP errors ... are **always worth retrying**」 |

**成因是运行时、不是疏忽**：Go 有 `net.Error`、JS 有 fetch 的 `TypeError` 可匹配，fail-closed 对它们安全；
而 `RestTemplate` 把连接失败抛成 `ResourceAccessException`——**一个普通非 HTTP 异常**——
若在 Java 侧也 fail-closed，**恰恰会把它本该覆盖的那类错误静默变成不重试**。

**④一处判读纪律：先前的「失实陈述」判断被我自己撤回**

我一度认定 Java 那句 `// Matches Go SDK isTransient()` 是**失实陈述**。**逐字重读后撤回**：
该行内注释紧贴在**四个状态码**的 return 之上，Javadoc 那句紧贴在「**排除 500**」之上，
**两者各自限定的范围内都成立**。按既定规则**「遗漏 ≠ 失实」**——此处是**分歧未被记载**，不是**说错了**。

**已修**：`isRetryable` 的 Javadoc 新增一段，写明默认极性的分歧、运行时成因与移植后果；
行内注释同步改为「四个状态码与 Go 完全一致，**但下面的 fall-through 刻意不一致，原因见 Javadoc**」。
**纯注释、零行为变更**（diff 非注释行 **0**）。**行为本身不单方面改动**：把 Java 改 fail-closed 会让
网络错误**不再重试**（真实回归），把另三家改 fail-open 更差——**两边都是行为变更，记录不实施**。

**探针错两处**：①`isTransient` 一度 grep 不到、差一步就记成「Java 引用了不存在的函数」——
实际它在 `client_impl.go:443`（我只扫了 `client.go`）；
②另一个是 `findDuplicateByContentHash` 顺带查证：V8 建的 `content_hash` 去重**确实仍在使用**
（`AgentService:237-241`），**重试造重复观测的担心因此不成立**。

### 完整验收

指纹 `a18e34e8…` → **`0f0ca2c7…`**。**豁免路径适用并已核实**：本轮改动在 `cortex-mem-spring-integration`，
`backend/pom.xml` 对它**零引用**、被改的类在 fat jar 内**出现 0 次**，
**故 37777 不可能含本轮改动、二进制仍新鲜**，可直接验收。
启动后触及 `backend/src` 的三个提交非注释增删行数**各为 0**。

**回归 45 / 0 / 1；Phase 3 25 / 0 / 0。基线推进。**

### 文档方向（运维/用户指南，doc round 200）—— 零缺陷

**①脚本清单零幻影**：`TESTING.md` 提及 24 个测试脚本，**全部存在**。
反向看 `scripts/` 实有 37 个 .sh，未被提及的 13 个里绝大多数是**非测试工具**
（`code-fingerprint` / `doc-growth-check` / `start*` / `sync-resources` / `deploy-webui` 等）。

**②三处计数声明逐条对上**：`phase3-acceptance-test.sh` 称 **15 个 test 函数**（实测 **15**）；
`run-all-e2e.sh` 称 **10 个本地 E2E 套件**（实测 **10**，且逐条列出 1/10–10/10）；
「排除 Docker 套件与 `test-llm-provider.sh`」——**那 10 个里确实一个都没有**。

**③最有价值的一条：条件套件的断言做了一次活体验证**

文档称：5/10 套件 `mcp-streamable-e2e-test.sh` 是**条件执行**，
除非服务端在 `/mcp` 暴露 Streamable HTTP（**POST 一个 `initialize` 必须答 200**），
而**后端默认传输是 SSE**，故默认安装下「**跑 9 个、跳 1 个**」。

**实测**：对活体 37777 `POST /mcp` 发 `initialize` → **HTTP 404**
（`{"status":404,"error":"Not Found","path":"/mcp"}`）；`application.yml` 中 `protocol: SSE` 属实；
编排脚本自身的探针（`is_streamable_mcp`）正是同一条 POST 判 200，
其跳过信息原文即「**/mcp not HTTP 200 for initialize**」——**与文档措辞逐字对应**。
**「默认 9 跑 1 跳」这一条是被测出来的，不是读出来的。**

**④一处观察，不判缺陷**：`performance-test.sh`（性能与压测套件）未出现在 `TESTING.md` 的套件表里，
但**它在 `AGENTS.md` 中有载**，且不属于那 10 个 E2E 套件。**属编排取舍，不是文档缺口**，未改。

**本轮零改动（运维/用户指南本身）。**

### 下一轮

代码方向：**Go SDK**；文档方向：**API 文档**（doc round 201，五个方向走完一轮）。
