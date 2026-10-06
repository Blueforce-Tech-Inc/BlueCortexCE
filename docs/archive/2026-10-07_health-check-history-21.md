# Health-Check History 21 — 第 312–316 轮巡检报告（第 326 轮逐字迁出）

> 承第 306 轮确立的规则：**按轮号**匹配 `## 第 N 轮 — ` 提取（不按物理位置），
> 逐块断言**块内无嵌套轮次标题**。
> **归档文件创建后不得修改。**

> 本批共 **5 块 / 394 行**，
> 工作文件保留第 317 轮起。迁出前工作文件 **1524 行**、越过 `MAX_LINES=1500`。

<!-- 块 1 / 5：第 312 轮 -->
## 第 312 轮 — 2026-10-06T19:20:00+08:00

代码方向：**Java SDK**（零缺陷）；文档方向：**SDK README**（doc round 212，零缺陷）
**P2-78 复查 2/3：新发现问题 → 计数重置**，新增 **P2-79**。

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `06e01132…` / 885，**与基线一致**。工作区干净。
doc-growth 全 OK。（本轮开场时他人 WIP 已在 `813cf2f`/`3d415a7` 提交完毕。）

### P2-78 复查 2/3 —— 复查的价值在本轮立刻兑现

**①先验自己上轮写进注释的断言**：`PATCH /api/session/{sessionId}/user` 与
`PATCH /api/memory/observations/{id}` —— 源码 `@PatchMapping("/{sessionId}/user")`、
`@PatchMapping("/observations/{id}")` 与**活体 OpenAPI 的 `params=['sessionId']` / `params=['id']`**
逐字吻合。注释无误。

**②攻一个没试过的边界，抓到新问题**（→ P2-79）：`allowCredentials` 守卫是
`origins.length > 0 && !origins[0].equals("*")` —— **只看第 0 位**，而它自己第 48 行的注释写着
「only if not using wildcard」。**三配置活体对照**：

| `claudemem.cors.allowed-origins` | 普通请求 `GET /api/stats` | 预检 | 日志异常数 |
|---|---|---|---|
| `http://a.example,*`（**星号非首位**） | **500** | 403，无 CORS 头 | **每次请求抛异常** |
| `*,http://a.example`（星号在首位） | 200 | 200，但**无 ACAO 头** | 0 |
| `http://a.example`（无星号） | 200 | 200 + ACAO + 凭据 | 0 |

Spring 的 `validateAllowCredentials` 在「凭据开启 + origins 含 `*`」时**每个请求都抛**
`IllegalArgumentException` → **整个 API 返 500，连不带 `Origin` 的普通请求也是 500**。
**第二行也不是正确形态**：`allowedOrigins` 混用 `*` 与具体源时 Spring 不做通配匹配 → 不返回 ACAO 头。
**失效形态远重于「跨域不工作」。**

**为什么记录不修**：最直觉的修法（`*` 在任意位置就置 `allowCredentials=false`）**是安全放宽**——
Spring 会回 `Access-Control-Allow-Origin: *`，等于**给所有 origin 开口**，比运维显式列出的那个更宽。
正确修法只有 `allowedOriginPatterns` 或**启动期 fail fast**，都属安全策略决策。

### 代码方向（Java SDK）—— 零缺陷，四个假设全被自己的核对推翻

| 假设 | 核对结果 |
|---|---|
| `ChatClientRequest` 重建时**丢掉字段**（只剩 prompt+context，可能丢工具回调） | **证伪** —— Spring AI 1.1.2 的它是**只有 `prompt`/`context` 两个分量的 record**，无 `toolCallbacks` 等字段 |
| `"unknown-session"` 哨兵被写进库 | **证伪** —— 三个调用方（Advisor/MemoryTools/ToolAspect）**全部先 `isActive()` 守卫**；Advisor:185 还显式过滤该哨兵；BridgeAdvisor:45 早已记载 |
| `truncate()` 的 `s.length()` 遇 null 抛 NPE | **证伪** —— 唯一调用点 `:127` 之前 `:112` 已判 `userText != null && !isBlank()` |
| Java SDK **没有类型化错误**（Go 11 哨兵 / Python 12 类 / JS 2 子类） | **非缺陷** —— README `:506` 起有完整 Error Handling 章节，明写「A 404 and a 500 produce the same exception type」并给出走 cause 链的读法 |

**顺带把 README 的五条回退断言逐条验真**（既然读了就不能只读一半）：
`search`→`:486` 加 `"fell_back":true` ✓；`getVersion`→`:602` 返 `"unknown"` ✓；
`getStats`→`:642/645` 加 `"fell_back":true` ✓；`getSettings`→`:686` 加 `"error"` ✓；
`getModes`→ 加 `"observation_types"` + **`"observation_concepts"`** + `"error"` ✓
——**连那个不寻常的蛇形键名都对上了**，说明该 README 是照着代码写的。

### 文档方向（SDK README，doc round 212）—— 零缺陷

**Python SDK README 双语**：
- 公开方法 **26** 个，README 方法表**恰好 26** 行，**逐名比对零缺失、零幻影**（不是只对计数）。
- 19 条 `/api/**` 路径对**活体 OpenAPI（62 paths）零幻影**；43 条活体未覆盖属**策展子集**，
  README **从未声称全覆盖** → 按「遗漏 ≠ 失实」不判缺陷。
- 四条配置断言逐条命中：默认 `base_url=http://127.0.0.1:37777` ✓、
  去尾斜杠 ✓（`rstrip("/")`）、`timeout=30.0` 且**地板 0.1** ✓、User-Agent ✓。

### 变更检测

本轮**未改任何代码**（只新增 findings 条目）。指纹仍 `06e01132…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。三次 37790 启动（我自己起的）已全部停止，**37777 未受影响**。

### 下一轮

代码方向：**Go SDK**；文档方向：**设计文档**（doc round 213）。
**P2-78 复查计数已重置为 1/3**（P2-79 在同一段代码里，需连查 3 轮）。
<!-- 块 2 / 5：第 313 轮 -->
## 第 313 轮 — 2026-10-06T19:58:00+08:00

代码方向：**Go SDK**（零缺陷）→ 引出 **P2-81**；文档方向：**设计文档**（doc round 213，零缺陷）
**P2-79 复查 1/3：本段代码里又发现新问题 → P2-80（已修），P2-79 维持 ⏸**

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `06e01132…` / 885 与基线一致，工作区干净。

### P2-79 复查 1/3 —— 攻没试过的解析角度，抓到 P2-80

`allowedOrigins.split(",")` **不 trim**。而 `docs/drafts/spring-ai-integration-plan.md:138`
教的正是「多个域名**用逗号分隔**」——**人在逗号后打一个空格是极自然的写法**。

**活体实测（37790，配置 `http://a.example, http://b.example`）**：

| `Origin` | 实测 |
|---|---|
| `http://a.example` | **200** + `ACAO: http://a.example` + `ACAC: true` |
| `http://b.example` | **403 "Invalid CORS request"**，无任何 CORS 头 |

`IllegalArgumentException` **0 次**——**与 P2-79 是两种机制**（那条每请求抛异常致全站 500，
这条什么都不抛，只是第二个及以后的域名**静默失配**）。
**第 2 个及之后的元素带前导空格**，与永不带空格的 `Origin` 头**永不相等**。

**⚠️ 一处探针错，先质疑探针**：初版用 `curl -o 文件` 后 grep 响应头，读到的永远是空
（`-o` 存**响应体**、`-D` 才是响应头），a.example 一度显示「200 但无 ACAO」。
改用 `-D` 倒原始响应头后 `Access-Control-Allow-Origin: http://a.example` **确实存在**——
**数据没错，是探针错了**；改正后结论反而更硬。

**P2-80 已修**：新增 `parseOrigins()` = `split` → `trim` → **丢弃空元素**。
**归类为「已损坏行为」而非安全放宽**——trim 只让**运维自己写进列表的域名**真正生效，
**不可能放行列表之外的任何 origin**。修后用**刻意写脏的配置**
`'http://a.example, http://b.example , ,http://c.example'` 验证：a/b/c **各 200 且各自回正确 ACAO**，
d 与 evil **仍 403**，PATCH 仍在允许列表，普通请求 200，异常 0。**进入 3 轮复查（1/3）。**

### 代码方向（Go SDK）—— 零缺陷，并牵出 P2-81

`client_methods.go`（381 行）是历史覆盖最少的生产文件（仅 2 次提及）。

| 检查项 | 结果 |
|---|---|
| 5 处 `fmt.Sprintf` 拼路径 | **全部**用 `url.PathEscape` ✓ |
| 查询参数编码 | `url.Values.Encode()` ✓ 正确百分号编码 |
| 空值参数 | `if v != ""` 跳过 + 调用方已守卫（双保险）✓ |
| 校验一致性 | 每个方法都有 `TrimSpace` + `ValidationError`，含 `ids[i]` 逐项下标 ✓ |
| `go test ./count=1 ./...` | 全绿（2 包 / 302 RUN）✓ |

**牵出 P2-81**：查「Go 单测是否被脚本跑到」时发现三个适配器
（`eino`/`genkit`/`langchaingo`）是**独立 `go.mod`**，故从 `cortex-mem-go` 跑 `go test ./...`
**根本不会执行它们**；再查编排脚本，发现 `run-all-e2e.sh` 的头注释写着
「Run **all** local E2E test scripts」并**逐条列出 3 个排除项**——
**实际漏掉 13 个未声明的测试脚本**，其中 **7 个只需前置「后端跑在 37777」，与该脚本自身前置完全相同**
（java/python/js SDK 三套 + phase3-acceptance + go-sdk-unit + demo-v15-extraction + performance）。
**危害是「静默的假完整」**：照头注释理解，跑完即全部验收，实际三家 SDK 套件与 Phase 3 **一次都没跑**。
CI 不构成补偿（唯一 workflow 只构建镜像）。**头注释已按「失实陈述」修正为如实描述**，
**未把脚本接进编排**（那会改变命令实际执行什么，需拍板）。

**⚠️ 本条第一版把 16 写成 13**：口径是用正则从全文抓 `.sh` 名，结果**连注释里那 3 个
「已声明排除」也算成了"已调用"**。重算后正确表述是
**16 个测试脚本未被调用 = 3 个已声明 + 13 个未声明**。

### 文档方向（设计文档，doc round 213）—— `go-sdk-design.md` 零缺陷

**①「15 个核心方法」不是失实**。它**明确限定在 Phase 1**，
且 §9 变更日志**逐迭代记录**后续新增（如「✅ 新增 `TriggerExtraction` 方法」）——
是分阶段设计的**历史数字**，不是现状陈述。**不判缺陷。**

**②附录 A「API 端点映射」17 行逐条对拍代码**：
**17/17 全部一致，零幻影、零错配**。
未列出的 7 个方法（`Search` / `ListObservations` / `GetObservationsByIds` /
`GetProjects` / `GetStats` / `GetModes` / `GetSettings`）属**遗漏非失实** → 不制造修改。

**⚠️ 又一处探针错**：首版把 5 行判成「不一致」，实为**我的比较脚本没处理 `%s` 占位符**
（`fmt.Sprintf` 的 `%s` 就是路径参数）。按占位符归一后 **17/17 全对**。
**两次教训同源：探针的解析能力不足，不能当成数据错。**

### 变更检测与完整验收

改了 `WebConfig.java`（P2-80）与 `scripts/run-all-e2e.sh`（**纯注释**），两者均在指纹范围内
→ 指纹 `06e01132…` → **`2eadb49a…`**，**触发完整验收**。
**归因核对**：`run-all-e2e.sh` 的 diff 中**非注释、非空增删行 = 0**（已用 `-U0` 逐行验）。

**新鲜度处理**：37777 非本轮启动且其 jar 不含本轮改动 → **不重启不复用**；
另起 **37790** 跑新 jar，用 `SERVER_URL` / `BACKEND_URL` 覆盖指向它：

| 套件 | 结果 |
|---|---|
| `regression-test.sh --skip-build` | **45 通过 / 0 失败 / 1 跳过 / 46** |
| `phase3-acceptance-test.sh`（`EXTRACTION_ENABLED=true`） | **25 通过 / 0 失败 / 0 跳过** |

**均与上次基线一致 → 基线推进**。验证后停掉 37790（自己启动的），**37777 未受影响**。

**⚠️ 一处被异常信号抓住的错误**：启动新实例后 `UP after ~3s`（Spring Boot 通常 ~25s），
查进程才发现**监听的是本轮早先那个带脏 CORS 配置的验证实例**，
**我新启的实例已因 `Port 37790 was already in use` 启动失败**（日志命中 2 处）。
虽已用活体预检证实旧实例**含 P2-80 修复**（带空格的 b.example 仍 200+正确 ACAO），
仍**重启了干净实例**（pid 83834，端口冲突命中 0）再跑验收。
**若只看"健康检查通过"就往下走，这一轮验收会跑在来源不明的进程上。**

### 下一轮

代码方向：**Python SDK**；文档方向：**架构文档**（doc round 214）。
**P2-80 复查 1/3**，第 314 轮须复查第 2 遍。
<!-- 块 3 / 5：第 314 轮 -->
## 第 314 轮 — 2026-10-06T20:35:00+08:00

代码方向：**Python SDK**（零缺陷）；文档方向：**架构文档**（doc round 214，零缺陷）
**P2-80 复查 2/3：在自己的修复里发现一条加宽边 → 计数重置 1/3**；
**并更正 P2-79 一条由坏探针测出的断言（更正方向是加重）**

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `2eadb49a…` / 885 与基线一致，工作区干净。

### 先做一件必须的事：复核 P2-79 里用坏探针测出的断言

第 312 轮那批 `grep access-control-allow-origin` 用的是 `curl -o`（**响应体**），
与第 313 轮我已当场认出的同一个探针错。**当时改了当轮的新结论，却没回头更正已归档的旧条目。**
**用 `-D` 重测（37790，配置 `*,http://a.example`）**：

| `Origin` | 实测 |
|---|---|
| `http://a.example`（列出） | **200** + `Access-Control-Allow-Origin: *`，`ACAC` 头 **0** 次 |
| `http://zzz-not-listed.example`（**未列出**） | **200** + `Access-Control-Allow-Origin: *` |

**记录里写的是「200 但无 ACAO 头 → 浏览器照样拦截，两种错法都错」——结论相反，且更严重。**
真相是：星号在首位时 `allowCredentials` 被算成 false，Spring **直接回显通配**，
**未列出的 origin 同样拿到 `ACAO: *`**。
**所以 P2-79 不是「两种错法都错」，而是「一种把整个 API 打挂、一种把它对全网敞开」**。
**已更正记录**（并补回被挤掉的「今天不可触发」限定语）。
第 1 行的 500 与第 3 行的 ACAO **不受影响**：前者取自 `-w` 的状态码本身，后者出自第 311 轮用 `-D` 的测量。

### P2-80 复查 2/3 —— 在自己的修复里找到一条加宽边，计数重置

实测（37790，配置 `' *'`，星号前一个空格）：
**任意 origin（含完全无关的 `https://totally-unlisted.example`）都拿到 `ACAO: *`，`ACAC` 0 次。**

**对照修复前**：`origins[0]` 会是字面量 `" *"`，既不等于 `"*"`（凭据被算成开启），
列表里又没有字面 `"*"`（故 Spring 不抛异常）——**结果是没有任何 origin 能匹配，对所有来源一律失效**。
**trim 之后**它变成干净的 `["*"]`，走通配分支 → **对全网回显 `ACAO: *`**。

**即：本修复把一个「什么都不匹配」的输入，变成了一个「什么都匹配」的输入。**
这与 P2-79 里那条「直觉修法」落到同一结果，**故本条不能独立结案**——
trim 对多源列表是纯好处，但它与「通配 + 凭据」这条策略问题**在边界上交汇**，
**必须与 P2-79 一并决策**才能收口。**今天的实际风险为 0**（该配置项全仓从未被设置）。
**按纪律，改动里发现新问题 → 复查计数由 2/3 重置为 1/3。**

### 代码方向（Python SDK）—— 零缺陷

`pytest -q`：**453 passed**（1.47s）。
- `build/lib/` 下有一份**陈旧源码副本**（client 682 行 vs 现 836）——
  查证为 `python-sdk/cortex-mem-python/.gitignore:5` 忽略、**0 个跟踪文件** → 本地构建产物，**非缺陷**。
- 状态码 → 异常映射（`error.py:106-132`）**完整正确**：
  400/401/403/404/405/409/422/429/≥500 各自成类，兜底 `APIError`。
- `_extract_error_message` 对**五种**响应体实测全部正确：
  应用错误 `observationId is required` ✓、**Spring 默认体 `Bad Request`** ✓、
  404 `Observation not found: 0000` ✓、空体 `(empty response body)` ✓、非 JSON ✓。
- `_request_json` 的 docstring 记着「有体但不可解析会抛」这条**已被修过的行为及其原因**
  （HTML 错误页配 200 曾把 `start_session` 变成空 session_id），措辞与实现一致。

### 文档方向（架构文档，doc round 214）—— 零缺陷

- **Network Security 表双语同步且准确**：两条 `ports:` 无主机 IP、
  Compose 设 `SERVER_ADDRESS: 0.0.0.0`、鉴权关闭 → 推荐的 Docker 部署全网可达（P2-70 的更正仍在位）。
- **Authentication 段**「Currently no authentication (local development)」—— 与本会话第 311 轮
  实测的「后端 `HttpStatus.UNAUTHORIZED` 零命中、从不返 401」**一致**。
- **Data Privacy 段**声称剥离在 `proxy/tag-stripping.js` —— **文件确实存在**，
  且它实际处理的**四个标签逐字对上**
  （`<claude-mem-context>` / `<private>` / `<system_instruction>` / `<system-instruction>`），
  `replace(/…[\s\S]*?…/g, '')` 也证实「整个移除」而非截断。**零幻影。**

### 变更检测

本轮**未改任何代码**（只更正/补充 findings 条目）。指纹仍 `2eadb49a…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。两次 37790（自己启动）已停止，**37777 未受影响**。

### 下一轮

代码方向：**JS/TS SDK**；文档方向：**运维/用户指南**（doc round 215）。
**P2-80 复查计数 1/3**；**P2-79 与 P2-80 需一并决策**（`allowedOriginPatterns` vs 启动期 fail fast）。
<!-- 块 4 / 5：第 315 轮 -->
## 第 315 轮 — 2026-10-06T21:10:00+08:00

代码方向：**JS/TS SDK**（零缺陷）→ 四家对拍牵出 **P2-82**；文档方向：**运维/用户指南**（doc round 215，零缺陷）
**P2-80 复查 2/3：干净，计数 2/3**

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `2eadb49a…` / 885 与基线一致，工作区干净。

### P2-80 复查 2/3 —— 一次跑掉多个边界，无新问题

把**制表符、前后空白、通配、空元素**合成一份脏配置
`'\t*\t, \thttp://a.example\t, \t , http://b.example'`（37790）：

| `Origin` | 实测 |
|---|---|
| `http://a.example` / `http://b.example` / `http://c.example` / `https://anything-at-all.example` | **200**，`ACAO: *`，`ACAC=0` |

`parseOrigins` 对**制表符**（`trim` 一并处理）、**空元素**（丢弃）均正确；
`ACRM=PATCH` 仍 200 且 `ACAM=GET,POST,PUT,DELETE,PATCH,OPTIONS`（**P2-78 未回退**）；
普通请求 200；`IllegalArgumentException` **0 次**。
**除已记录的通配交互（P2-79）外无新问题 → 复查计数 2/3。**

### 代码方向（JS/TS SDK）—— 零缺陷，四家对拍牵出 P2-82

`npx vitest run`：**259 passed**（3 文件 / 1.45s）。

JS SDK 本身：`errors.ts` 有 **14 个谓词**且注释写明「500 不重试（代码 bug）、4xx 不重试」；
重试**只用在 `doFireAndForget`**（与 Go 一致）；`doFetch` 里 10 MB 上限有**三道检查**，
且注释把每道防线的**由来**都记着（含「多字节字符使 `text.length` 低估体积达上限 1.5 倍」那次实测修补）。
**零缺陷。**

**四家对拍时牵出 P2-82——同一道安全阀，只有 Python 没有**：

| SDK | 响应体上限 | 形态 |
|---|---|---|
| Go | **10 MB** | `MaxResponseBytes = 10 << 20`，**导出具名常量**；`LimitReader(Max+1)` 读超一字节显式报错 |
| JS | **10 MB** | `const maxSize = 10*1024*1024`，读前查 `Content-Length`、读后查长度、外加 UTF-8 字节感知第三次 |
| **Python** | **无** | `_request` 拿到 `Response` 后**直接 `resp.content`**，全路径零体积判断 |

`grep -nE 'MAX_RESPONSE|max_response|10 * 1024 * 1024|10485760' python-sdk/cortex-mem-python/cortex_mem/*.py`
**零命中**。`requests` 默认把整个响应体缓冲进内存，故超大响应（或拦截它的代理）
**在 Python 侧被完整读入**，Go/JS 则中止并给可诊断错误——**失效形态不同，不只是「少个常量」**。
→ **P2-82（⏸ 记录不修）**：给 Python 加上限会让原本成功的超大响应变成抛错，属**收窄已发布 SDK 的接受范围**。
**未证实的部分不写**：本轮**未实测**「真的 OOM」，那是**代码路径层面的断言**，不是已复现的事故。

### 文档方向（运维/用户指南，doc round 215）—— `TESTING.md` 零缺陷

顺着 P2-81 查「跑哪些套件」的交代是否说过头：
- **§1/§2/§3 的三张表把各套件列全**（含四家 SDK E2E、`go-sdk-unit-test.sh`、`phase3-acceptance-test.sh`），
  **对 `run-all-e2e.sh` 的描述反而比脚本旧头注释更准**：
  「runs the **10** local E2E suites defined here …（excludes Docker suites and test-llm-provider.sh）」——
  它**没有**声称跑遍所有表列脚本。**P2-81 的失实在脚本侧，不在文档侧。**
- **选项表逐项核实**：`--skip-build` / `--parallel` / `--verbose` / `--cleanup` / `--help|-h`
  **全部真实存在于 `regression-test.sh:65-83` 的解析分支**。✓
- **PostgreSQL 端口提示准确**：`docker compose` 发布在 **5433**、原生 5432，
  与 `docker-compose.yml` 的 `"${POSTGRES_PORT:-5433}:5432"` 一致。✓
- **一条很具体的断言逐字命中**：`go-sdk-unit-test.sh` 声称覆盖
  「root + dto + eino + genkit + langchaingo」——脚本 `:59-62` **确实**跑这四个目标
  （root 一个目标已覆盖 client + dto），**且它自己的头注释 `:4-5` 恰好记录了
  「各集成层独立 `go.mod`，`go test ./...` 只覆盖 root + dto」**——正是 P2-81 暴露的那个陷阱。✓

### 变更检测

本轮**未改任何代码**（只新增 findings 条目）。指纹仍 `2eadb49a…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。37790（自己启动）已停止，**37777 未受影响**。

### 下一轮

代码方向：**Demo**；文档方向：**API 文档**（doc round 216）。
**P2-80 复查 2/3**；**P2-79 与 P2-80 仍需一并决策**（`allowedOriginPatterns` vs 启动期 fail fast）。
<!-- 块 5 / 5：第 316 轮 -->
## 第 316 轮 — 2026-10-06T21:55:00+08:00

代码方向：**Demo**（零缺陷）；文档方向：**API 文档**（doc round 216，零缺陷）
**P2-80 复查 3/3 → 技术侧结案**；**P2-82 定性被本轮推翻并下调**；**新增一条流程规则**

### 健康预检

37777 在监听（pid 42092），health 全 UP。指纹 `2eadb49a…` / 885 与基线一致，工作区干净。

### P2-80 复查 3/3 —— 结案

补测前两轮**未覆盖**的两个边界：

| 边界 | 配置 | 实测 |
|---|---|---|
| **CRLF**（多行环境变量） | `'http://a.example,\r\nhttp://b.example'` | 两个源**各自精确放行**并回**各自的** ACAO；未列出的 **403**；异常 **0** |
| **退化输入**（只有分隔符与空白） | `' , '` | 丢弃空元素后得**空数组** → **CORS 退回全关的安全默认**，普通请求仍 **200** |

**三轮合计覆盖**：逗号后空格（313）、制表符+前后空白+空元素+通配（315）、CRLF+退化输入（316）。
**`parseOrigins` 在所有非通配输入上行为正确 → 代码侧结案**。
仍然开放的不是技术问题，而是**通配 + 凭据的策略决策**（与 P2-79 合并待决）。

### 代码方向（Demo）—— 零缺陷，一个假设被实验推翻

第 310 轮已覆盖多数 controller，本轮读此前未看的 `ToolsController` 与 `ChatController`。
**假设**：两个 controller 对**无法解析的 project selector** 兜底不同
（`ToolsController` 用原始 selector，`SessionLifecycleController` 用 `user.dir`），
故同一 selector 会在不同端点产生不同记忆命名空间。
**实验推翻**：起 demo（37778）用同一个未知 selector 打两个端点——
`POST /demo/session/start?project=totally-unknown-key-316` 返回
`project_path='totally-unknown-key-316'`（**原始 selector**），查库确认 session 也落在该命名空间。
**机制**：`DemoProperties.resolveProjectPath:55-61` 对非空 key **从不返回 null**（miss 时返回 key 本身），
故**所有** demo 端点最终都得到原始 selector，**无不一致**。
**顺带发现（非缺陷）**：`SessionLifecycleController` 两处 `if (projectPath == null)`
因 `project` 有 `defaultValue="default"` 而**永不可达**，属防御性死代码。
**这加强了第 304 轮那条「已查清的非问题」记录。**

### 文档方向（API 文档，doc round 216）—— 顺 P2-82 查文档，结果推翻了我上一轮的定性

查各文档对**响应体上限**的记载，发现**三份 SDK README 各自准确、双语同步**：
- **Go README `:227-235`**：常量名 `MaxResponseBytes`、`10 << 20`、读 `Max+1` 字节、错误原文；
- **JS README `:171-179`**：报错文案，且**主动澄清**「文案写 `10MB`，实际上限是 10 MiB = 10,485,760 字节」；
- **Python README `:229-238`（中英双语同节）**：明写「There is **no** response size cap in this SDK」，
  并给出**原因**「`requests` gives **no portable hook** for a streaming size check」、
  **后果**「bounded only by the memory available to your process」、
  **建议**「Keep `limit` modest」。

**故 P2-82 不是「无人知晓的缺口」，而是已双语记载的、有理由的有意取舍**——
上一轮把它记成缺陷**定性错了**。**技术事实不变**（Python 无上限、Go/JS 有 10 MiB），
**变的是定性**：从「缺口」降为「已记载的取舍」，剩下的问题只是**要不要改**，
属**产品决策**而非补缺口。**已更正记录**（保留事实、只改定性并降级）。
`docs/API.md` 本身本轮**零改动**（它不含响应体上限的相关断言）。

### 一条必须写进流程的教训

这是**连续第三轮**同一模式：先凭代码判成「缺口/缺陷」，下一轮才发现**它是被双文档明确记载的有意设计**
（312 Java 无类型化错误 / 314 `build/lib` 陈旧副本 / 316 Python 无上限）。
**已在 Processing Rules 立规**：把某处记成 finding **之前**，先 grep 该模块的
README（**中英双语**）看它**是否已自陈**。**「代码里没有」不等于「没人知道」**——
本项目在多处主动记载权衡，**这本身是好实践，不该被当成缺陷反复重提**。

### 变更检测

本轮**未改任何代码**。指纹仍 `2eadb49a…` / 885，**与基线一致**
→ **不跑完整验收、不推进基线**。自起的 demo（37778）与两次 37790 均已停止，**37777 未受影响**。

### 下一轮

代码方向：**Backend**（轮换回到起点）；文档方向：**SDK README**（doc round 217）。

