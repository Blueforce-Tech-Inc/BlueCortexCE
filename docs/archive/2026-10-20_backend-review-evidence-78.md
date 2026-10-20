# Backend Review Evidence 78 — 第 377 轮本轮新条目的整条正文（第 377 轮迁出）

> **归档规则**：承 `-62` ~ `-77` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 **70** 字节，本轮先做了一条**新杠杆**的零信息损失压缩才腾出空间 ——
> C3e「唯一散文长词组」去标记 **162** 处，**只删 `**` 标记、一个字未改**；
> 硬证明：去掉全文所有 `*` 后字符流与压缩前**逐字相同**），
> 工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：274 行 / 22096 字节。**
>
> 本批迁出 **P2-139 / P2-140 / P2-141** 三条新条目。
>
> **本轮代码方向为 Backend，文档方向为用户指南/运维指南（doc round 277）。**
>
> 本轮的三条**都在同一类形状上**：**一个「检查配置对不对」的东西，永远回答「对」**。
> ① **P2-139**：`/api/test/llm` 在 LLM 401 失效时仍报 `status: "success"` ——
> 契约（双语 API 文档 + 活体 OpenAPI）都写明失败返 500，**是实现没兑现**；
> 仓库自带的 `scripts/test-llm-provider.sh` 读 `.status` 字段 ⇒ 打印「LLM test passed」。
> ② **P2-140**：目录重命名后 **13** 处 `java/` 前缀路径没跟上 —— 其中
> `test-llm-provider.sh --config` **永远读不到配置**（还叠了 `PROJECT_ROOT` 多退一层），
> `create-distribution.sh` **直接报错退出**。
> ③ **P2-141**：Go SDK 的 `./test-all.sh` 只测 **4/9** 个模块就宣布「All tests passed」，
> 漏掉 `examples/http-server` 的 **33** 个测试 —— 正是该 SDK README 点名要防的腐化。
>
> **共同形状**：三者都**不会报错**，都**正常退出**，都**给出一个好看的结论** ——
> 「success」「All tests passed!」「No .env file found」都能读成正常。
> 这是本轮最值得记住的一类缺陷。
---
### P2-139: `LlmService` 吐掉全部异常并返回空内容 —— 于是 `/api/test/llm` 在 LLM 完全失效时**谎报** `status: "success"`，仓库自带的巡检脚本据此打印「LLM test passed」

- **Problem**：`backend/src/main/java/com/ablueforce/cortexce/service/LlmService.java` 的
  `chatCompletionWithUsage` 把 **provider 的每一个异常**都 catch 掉，然后返回
  `LlmResponse.empty()` —— 也就是 **content = 空串、totalTokens = 0**：
  ```java
  } catch (Exception e) {
      log.error("LLM call failed: {}", e.getMessage());
      return LlmResponse.empty();
  }
  ```
  对**想要降级结果**的调用方（那 8 个调用方各自都会检查内容）这是对的；但对
  **诊断端点**是错的：**空串与「成功但没说话」不可区分**。
  于是 `TestController.testLlm()` 的 `catch (Exception e)` **永远不会被触发** ——
  它那行 `ResponseEntity.status(500)` 是死代码 —— 端点只会走成功分支。
- **实测（活体 37791，本机 LLM chat 密钥已失效）**：
  | 端点 | 修复前 | 修复后 |
  |------|--------|--------|
  | `GET /api/test/llm` | **200** `{"status":"success","message":"LLM (DeepSeek) is working!","response":""}` | **500** `{"status":"error","message":"LLM (DeepSeek) failed: 401 - {...authentication_error...}"}` |
  | `GET /api/test/embedding`（**对照组，本轮未改**） | 500 `{"status":"error","message":"Embedding failed: 401 - {...Token is invalid.}"}` | 同左 |
  | `GET /api/test/all` | 200，**内含** `llm.status = "success"` | 200，内含 `llm.status = "error"` |
  ⇒ **同一族两个端点、同一类故障，一边老实说 500、一边说「is working!」**。
  后端日志里有对应的 `ERROR ... LLM call failed: 401 - {"error":{"message":"Authentication Fails,
  Your api key: ****13fc is invalid ..."}}`（本轮实测 **6** 条），**失败是可见的，只是没被端点报告出来**。
- **最坏后果：仓库自己的巡检脚本被骗**。`scripts/test-llm-provider.sh` 读的是
  **`.status` 字段**（不是状态码）：
  ```bash
  if [ "$HTTP_STATUS" = "200" ]; then
      STATUS=$(echo "$BODY" | jq -r '.status' 2>/dev/null)
      if [ "$STATUS" = "success" ]; then
          print_success "LLM test passed"
  ```
  ⇒ **LLM 密钥 401 完全失效时，脚本打印 `[PASS] LLM test passed` 并 `return 0`**。
  这是**假阳性**：端点说「成功」，脚本说「通过」，而 LLM 一句话也答不出来。
  （修复前实测输出：`[PASS] LLM test passed`；修复后同一脚本逻辑对同一故障输出
  `[FAIL] HTTP request failed with status: 500`。）
- **与 P2-28 的区别（不是重复立条）**：P2-28 记的是 `/api/test/all` **丢弃子处理器的状态码**、
  恒返回 200；那条的实测记录里 **LLM 密钥当时是有效的**（只有嵌入密钥失效），所以
  `/api/test/llm` 当时报 success **是真的**。本条是**根因不同、层面不同**：
  `/api/test/llm` **自己**把失败说成成功，根在 `LlmService` 吞异常，与状态码传播无关。
- **契约方向**：双语 `docs/API.md` / `docs/API-zh-CN.md` 都明写该端点**失败时返回 500 +
  `{"status":"error","message":"LLM (DeepSeek) failed: ..."}`**，活体 OpenAPI 也声明了 500
  ⇒ **契约是对的，是实现没兑现** ⇒ 属「已损坏行为」，可修（不属对外契约变更）。
- **修复**（改动面最小，既有 8 个调用方**行为完全不变**）：
  1. `LlmService` **纯新增** `chatCompletionReportingFailure(String, String)` ——
     同样的调用，但**不吞异常**，把 provider 的原始异常抛给调用方；
     `chatCompletion` 与 `chatCompletionWithUsage` **一字未改**。
  2. `TestController.testLlm()` 改用新方法，并对**空响应**显式判失败
     （`response == null || response.isBlank()`）。
  3. 新增 `TestControllerLlmFailureTest`（**3** 个测试）：
     ① provider 抛异常 ⇒ 500 + `status:"error"` + 消息含 401；② provider 成功 ⇒ 200 +
     `status:"success"`（**这条防止「改成永远抛」也能过**）；③ `chatCompletion` 仍降级为
     空串（**钉住修复没有泄漏到那 8 个调用方**）。
- **阳性对照（证明测试钉得住）**：把 `TestController` 还原成修复前的调用后重跑，
  `providerFailure_isReportedAsErrorWith500` **确实失败**（`Tests run: 3, Failures: 1`，
  `BUILD FAILURE`）⇒ 这条性质**会失败**，不是恒不响的空测试。
- **验证**：后端全量 `mvn -o test` **170/0/0** 全绿（原 **167** + 新增 **3**）；
  活体（新鲜实例，含本轮改动）`/api/test/llm` → **500 + 真实 401 原因**；
  `scripts/test-llm-provider.sh` 的判定逻辑对坏掉的 LLM 从 `[PASS]` 变成 `[FAIL]`。
  **注意**：`scripts/test-llm-provider.sh` 的 `BASE_URL` 是**硬编码**的
  （`BASE_URL="http://127.0.0.1:37777"`），传环境变量**不生效** ——
  首次复测时我据此得出了「修复没生效」的错误结论，改写端口后才是真读数（**探针自身错 1 处，先识别再采信**）。

### P2-140: 四处 `java/` 前缀的路径在目录重命名后没跟上 —— 而其中一处让 `--config` **永远读不到配置**

- **Problem**：本仓库的后端目录是 **`backend/`**（含 `mvnw`），**没有 `java/` 这一层**
  （`ls -d java` → 不存在），但仓库里仍有 4 个文件按旧布局写路径：
  | 文件 | 位置 | 内容 |
  |------|------|------|
  | `RELEASE.md` | 21 / 27 / 38 / 42 | `cd java/backend`、`-f java/Dockerfile`、`./java/scripts/regression-test.sh`、`./java/scripts/webui-integration-test.sh` |
  | `proxy/README.md` | 130 / 137 / 143 / 149 / 159 | `/path/to/claude-mem/java/proxy/wrapper.js` |
  | `proxy/CLAUDE-CODE-INTEGRATION-zh-CN.md` | 38 / 353 | `cd java/scripts` |
  | `scripts/prebuild-webui.sh` | 104 / 107 | `-f java/Dockerfile`、`-f java/docker-compose.yml` |
  | `scripts/sync-resources.sh` | 418 | `cd java/backend && mvn clean package` |
  | `backend/.../TemplateService.java` | 53 | 注释：`java/scripts/sync-prompts.sh`（**该脚本根本不存在**） |
  | `scripts/seed-diverse-data.sh` | 172 | 演示数据里的 `files_read` 值 `java/proxy/wrapper.js`（**该文件也不存在**，真实位置是 `proxy/wrapper.js`） |
- **最实的一处**：`scripts/test-llm-provider.sh` 的 `show_current_provider()` 读的是
  `"$PROJECT_ROOT/java/backend/.env"`，而 `--config` 的实测输出是
  `No .env file found at .../BlueCortexCE/java/backend/.env`
  ⇒ **配置总览永远读不到**。**而且这里还叠了第二个独立缺陷**：
  ```bash
  PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"   # 多退了一层
  ```
  `SCRIPT_DIR` 已是 `<repo>/scripts`，再退两层 ⇒ 指向 **仓库的父目录**
  ⇒ 即便路径名写对，`backend/.env` 仍找不到（实测：
  `No .env file found at .../BlueCortexCE/backend/.env`，注意少了 `java/` 但**仍不是仓库根**）。
  仓库里其余脚本一律用 `$(cd "$SCRIPT_DIR/.." && pwd)`（**11 个脚本中的 9 个**），
  只有 `test-llm-provider.sh` 与 `create-distribution.sh` 用了多退一层的写法。
- **另一处是硬失败**：`scripts/create-distribution.sh` 整条路径假设都是 `java/` 前缀，
  实测**直接报错退出**：
  `cp: .../BlueCortexCE/java/docker-compose.yml: No such file or directory`。
- **按断言清扫**：`grep -rn 'java/backend\|java/Dockerfile\|java/scripts\|java/proxy\|java/README\|java/docker-compose'`
  全仓命中 **13** 处（含上述）⇒ **范围就是这些，没有别的同族**。
  `docs/drafts/` 与 `webui/docs/drafts/` 里的命中是**历史报告正文**与**带日期的快照**
  （`webui/docs/drafts/2026-10-06-java-viewer-alignment.md` 引用的
  `java/scripts/webui-contract-test.mjs` 在本仓库**根本不存在**，那是当时在**另一个 WebUI 检出**里跑的）
  ⇒ 按「带日期的快照不算失实陈述」**不改**。
- **修复**：
  - `scripts/test-llm-provider.sh`：`PROJECT_ROOT` 改为 `$(cd "$SCRIPT_DIR/.." && pwd)`；
    `.env` 路径改为 `$PROJECT_ROOT/backend/.env`；启动提示里的 `cd` 同步改为 `backend`。
  - `scripts/create-distribution.sh`：`PROJECT_ROOT` 同样修正，三处 `java/...` 改为
    `docker-compose.yml` / `proxy/<file>` / `README.md`。
  - `scripts/prebuild-webui.sh`、`scripts/sync-resources.sh`：`java/` 前缀去掉。
  - `RELEASE.md` 四处、`proxy/README.md` 五处、`proxy/CLAUDE-CODE-INTEGRATION-zh-CN.md` 两处：
    路径改为真实布局（`/path/to/BlueCortexCE/...`）。
    **注意 `proxy/README.md` 同文件第 112 行本来就写着 `cd /path/to/BlueCortexCE/backend`**
    ⇒ **同文件内自相矛盾**，本轮统一到新布局。
  - `TemplateService.java` 的注释：指向 `scripts/sync-resources.sh`（**核实该脚本确有 `--prompts`
    开关且真的写 `backend/src/main/resources/prompts/`，而 `scripts/sync-prompts.sh` 不存在**）。
  - `scripts/seed-diverse-data.sh`：演示数据的 `files_read` 值改为真实存在的 `proxy/wrapper.js`。
- **验证**：`bash scripts/test-llm-provider.sh --config` 实测**读到**
  `Reading from: .../BlueCortexCE/backend/.env` 并列出 `OPENAI_API_KEY=***`（**密钥仍被打码，
  安全属性未被破坏**）；`bash scripts/create-distribution.sh <dir>` 实测**成功**并产出
  `README.md` + `docker-compose.yml` + `proxy/`（**7** 个文件）。
  五个改过的 shell 脚本 `bash -n` **全部 OK**。

### P2-141: Go SDK 的 `./test-all.sh` 只测 **4** 个模块就宣布「All tests passed」—— 漏掉了 `examples/http-server` 的 **33** 个测试

- **Problem**：`go-sdk/cortex-mem-go/test-all.sh` 的模块列表是**手写**的：
  ```bash
  MODULES=("." "genkit" "eino" "langchaingo")
  ```
  而该 SDK 实际有 **9** 个 `go.mod`（`find . -name go.mod` 实测）：
  `go.mod`、`dto`（属根模块）、`genkit`、`eino`、`langchaingo`、
  `examples/basic`、`examples/eino`、`examples/genkit`、`examples/http-server`、
  `examples/langchaingo`。
  ⇒ **脚本漏掉了 `examples/http-server` 的 33 个测试**，却仍然打印
  `Passed: 4/4` / `✅ All tests passed!`。脚本头注释自称「This script tests everything」
  ⇒ **注释与行为不符**。
- **为什么它该测**：Go SDK README 明写「This SDK is **9 separate Go modules**, not one …
  a bare `go test ./...` run from this directory only reaches the root module — **306 of
  the 372 tests**」⇒ **漏掉 33 个正是 README 点名要防的那种腐化**。
- **数字核实**（本轮实测，`=== RUN` 口径含子测试，与 README 的口径一致）：
  | 模块 | 测试数 |
  |------|--------|
  | 根模块（core 239 + dto 67） | **306** |
  | `genkit` | 13 |
  | `eino` | 8 |
  | `langchaingo` | 12 |
  | `examples/http-server` | **33** |
  | 其余 4 个 `examples/` 模块 | 0（`[no test files]`，实测确认） |
  | **合计** | **372** |
  ⇒ 与 README 的 **372** 分解**逐项精确相符**。
- **顺带查出的一处陈旧数字**：`docs/DEVELOPMENT.md` 与 `docs/DEVELOPMENT-zh-CN.md` 写着
  `./test-all.sh # … (this is what **335** counts)` 与 `go test ./... # … (**302**)`，
  而实测是 **372** 与 **306**。
  **查清了来历，不算本轮新造**：**335** 是第 **164** 轮（2026-10-04）实测值并当轮订正过
  （`docs/drafts/doc-review-task.md` 记着「`./test-all.sh` 计 359 个 Go 测试——实测是 335，少 24」），
  **362 → 363** 是第 **319** 轮订正（`docs/drafts/backend-review-findings.md` 记着
  「**core** 235 应为 236，故根模块 `302 → 303`、总数 `362 → 363`」），
  **372** 是 **2026-10-20**（Go SDK README 自报，并注明「measured 2026-10-20」）——
  ⇒ **335/302 是被后续更新取代的旧值**，本轮实测 372 与 README 完全一致 ⇒ 订正到 **372 / 306**。
- **修复**：`test-all.sh` 改为**从 `go.mod` 自动发现模块**（不再手写列表 ⇒ 新增模块不会再漏）：
  ```bash
  MODULES=()
  while IFS= read -r mod; do
      [ -n "$mod" ] && MODULES+=("$mod")
  done < <(cd "$SDK_DIR" && find . -name go.mod -not -path '*/node_modules/*' \
      | xargs -n1 dirname | sed 's|^\./||' | sort)
  ```
  **探针自身错 1 处，先识别再采信**：第一版用了 `mapfile` ——
  **macOS 自带的是 bash 3.2，没有 `mapfile`**（`bash --version` → `GNU bash, version 3.2.57`）
  ⇒ 脚本直接 `mapfile: command not found` 退出；改用 `while read` 循环（bash 3.2 兼容）后正常。
  另一处：初版 `sed 's|^\./|.|'` 把 `./x` 变成了 `.x`（**少一个斜杠**）⇒ 改为 `sed 's|^\./||'`。
- **验证**：修后 `bash test-all.sh` 实测 **9/9 模块全绿**（修前 4/4），
  `examples/http-server` 的 33 个测试**现在会被跑到**。
- **文档侧同批订正**：`docs/DEVELOPMENT.md` 与 `docs/DEVELOPMENT-zh-CN.md` 的
  **335 → 372**、**302 → 306**（双语同步）；注释同时改为「all 9 modules」。
  **按断言清扫**：`grep -rn '\b335\b|\b302\b'` 在活文档里除这两处外**零命中**
  （其余命中全在 `node_modules`、归档与 drafts 的历史正文里）。

### 附：P2-116 的「第 363 轮的更正」段（第 377 轮从工作文件迁入）

> 第 254 轮规则：待决条目的 `- **Scope / Evidence**` 段可迁入归档，`- **Problem**` 与
> `- **Status**` 一律留在工作文件。本段是**第 363 轮追加的更正**（晚于 `-62` 归档的创建，
> 故 `-62` 里没有它）—— **逐字迁入本归档**，工作文件只保留 P2-116 的标题与 `- **Status**` 一行。

- 第 363 轮的更正（本条是 P2-43 的重复立条，缺陷计数不因本条增加）: **P2-43 早在第 247 轮
  就立了同一发现**（标题同义：「Python SDK 的两种调用风格对 `None` 的含义相反——dataclass 路径丢弃它、
  kwargs 路径原样发上 wire」），其 Problem 的机制描述与 Status 的两条收法与本条逐条相同。
  第 362 轮立本条时未先查 P2-43，是一次重复立条——按本文件「写之前先按主题搜全文」的纪律，
  这一步本该先做。保留本条而非删除，是因为它带来三项 P2-43 没有的东西:
  ①**用 SDK 本身**（而非 demo）的活体端到端复验，含 `extracted_data={}` 这一格；
  ②查出 P2-43 与 P2-26 都承诺过、却从未落盘的那句补注；
  ③注释层的失实陈述已修（P2-43 只要求「给能力表加注」，而 Python SDK 自己的 docstring
  仍在说「cannot be cleared through this client at all」）。
  本条与 P2-43 记的是同一件事，以 P2-43 为准；本条的证据与正文见 [`…-62`](../archive/2026-10-20_backend-review-evidence-62.md)（362 轮）。

### 本轮文档方向：用户指南/运维指南（doc round 277）—— 除上述外逐条核实为真

- **`docs/DEPLOYMENT.md` 与 `docs/DEVELOPMENT-zh-CN.md` 的路径与命令逐条核实**：
  `DEVELOPMENT.md` 里 6 个 `cd` 目标（`proxy`、`backend`、`cortex-mem-spring-integration`、
  `go-sdk/cortex-mem-go`、`python-sdk/cortex-mem-python`、`js-sdk/cortex-mem-js`）
  **全部真实存在**；`DEPLOYMENT.md` 的端口（**37777**）、`SERVER_PORT` 默认值、
  `CLAUDEMEM_RATE_LIMIT_*` 默认值（**10 / 60 / 300**）与实现**逐项相符**
  （`RateLimitService` 的 `@Value("${claudemem.rate-limit.max-requests:10}")` 等三处）。
  **限流段的「固定窗口」表述是第 373 轮已订正的**（当时只改了文档侧）——
  本轮查出**代码侧漏了**：`RateLimitService` 的类 Javadoc 与内层类注释仍写着
  「**sliding window** algorithm」，而实现是**固定窗口**
  （`if (now - windowStart >= windowSeconds) { count.set(0); windowStart = now; }`
  —— **整窗重置，不滑动**）⇒ **失实陈述**，本轮已订正注释（并注明内层类名 `SlidingWindow`
  是历史遗留、其行为是固定窗口）。这是第 373 轮那条的**同族漏改**。
- **运维指南漏了唯一能自检 LLM 的那一步**：`DEPLOYMENT.md` §7.1.4「LLM API Call Failed」
  的排障步骤是「查环境变量 → curl 提供方 → 看日志」，**没有一步能确认后端自己通不通**，
  而仓库明明带 `scripts/test-llm-provider.sh`。按「**遗漏 ≠ 失实**」的纪律，
  **本轮不补这一节**（补它是新增内容而非修正失实陈述）；但 §7.1.4 与本轮 P2-139 的
  关系已记录在此处：**在 P2-139 修复之前，那条排障路径会让运维人员以为 LLM 正常**
  （因为 `/api/test/llm` 报 success），修复后才恢复可信。

### 本轮其余部分

- **代码方向（Backend）**：P2-139 已修（`LlmService` 纯新增一个方法 + `TestController`
  一处调用替换 + **3** 个新测试）。**既有 8 个调用方（`SummaryGenerationService`、
  `LlmQualityScorer`、`MemoryRefineService`、`AgentService`、`StructuredExtractionService`
  等）行为完全不变** —— 逐个核实它们**各自都已处理「空响应」**：
  `MemoryRefineService` 有 `!mergedContent.isEmpty()` 保护、`LlmQualityScorer` 的
  `parseAnalysisResponse` 有 `!response.isBlank()` 保护、`StructuredExtractionService` 的
  `parseJsonResponse` 对空串**抛异常**、`AgentService` 的 `XmlParser.parseObservation("")`
  **抛 `DataValidationException`** ⇒ 吞异常在那些路径上是**被下游接住的**，
  只有诊断端点无人接住 ⇒ 这也解释了**为什么选「新增方法」而不是「改 `LlmService` 语义」**：
  改语义会连带改变 `AgentService.isRetryableException` 的判定输入（它按**异常消息文本**
  匹配 `API_KEY_INVALID` / `PERMISSION_DENIED` 等），而 401 的消息
  `Your api key: **** is invalid` **一条都匹配不上** ⇒ 会被判成**可重试**
  ⇒ **在失效密钥上无限重试**。本轮**不改**那条路径。
- **探针自身错 3 处，全部先识别再采信**：
  ①**源码解析式对拍**（用正则从 Java 源码里提取方法体与状态码）连续两版都错：
  第一版把**路径模板里的 `{`** 当成方法体起点、第二版把**文件内第一个** `@RequestMapping`
  当基路径、第三版 `skip_annotations` 把方法映射后的普通代码也吞了 ⇒ **解析出 12 个路由**
  ⇒ **放弃源码解析路线，改用活体多形态穷举**（每端点打 3 种路径变体 × 3 种请求体）。
  ②活体穷举第一版**类型错**（拿 `int` 状态码与 `str` 声明集合比）⇒ 30 个操作全部误报。
  ③`test-llm-provider.sh` 的 `BASE_URL` **硬编码**，传环境变量不生效 ⇒ 首次复测得出了
  「修复没生效」的错误结论（**差点据此改回代码**）。
- **本轮验证**：后端全量 `mvn -o test` **170/0/0**（原 167 + 新增 3）；
  `go test ./...` 全绿；`bash test-all.sh` **9/9**；
  回归 **45/0/1** + EXTRACTION 验收 **25/0/0**（均 exit 0，跑在含本轮改动的**新鲜实例** 37790 上）。
- **容量**：写入前 findings 只剩 **70** 字节，本轮做了一条**新杠杆**的零信息损失压缩 ——
  **C3e「唯一散文长词组」**（含汉字、无反引号、无 snake_case/camelCase 标识符、无路径、
  无代码符号、长度 **13~40**、**全篇只出现一次**、且**不含换行**）去标记 **162** 处，
  **只删 `**` 标记、一个字未改**；硬证明：去掉全文所有 `*` 后字符流与压缩前**逐字相同**
  （脚本内三重断言：字符流逐字相同、标记减少量 == 2×候选数、非标记字符长度不变）；
  **跨行片段必须排除**（去掉 `**` 会破坏 Markdown 缩进/列表结构，实测 64 处跨行候选已排除），
  **一个字的正文都没动**。共省 **648** 字节。
