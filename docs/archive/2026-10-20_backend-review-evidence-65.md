# Backend Review Evidence 65 — 第 365 轮本轮新条目的整条正文（第 365 轮迁出）

> **归档规则**：承 `-62` / `-63` / `-64` 的体例，迁出**本轮新写**的条目全文
> （写作时 findings 的字节余量只剩 3766 字节），工作文件只保留标题与 `- **Status**` 一行
> （末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 **P2-120** / **P2-121** 两条 —— **本轮新写**的条目，写完即整体迁出，
> 工作文件各留标题与 `- **Status**` 一行（含归档指针）。**正文逐字保留在本归档**。
>
> **本轮代码方向为 Backend，文档方向为设计文档（doc round 265）。**
> P2-120 是**代码方向**的产物（已修，零行为变更）；P2-121 是**文档方向**的产物（记录不修）。
>
> 迁出前实测 **1408 行 / 146234 字节**；本归档实测 **118 行 / 12600 字节**。

### P2-120: 活体 OpenAPI 给 `POST /api/observations/batch` 声明了一个**不可达的 400**，并把 `ids` 标为必填 —— 而处理器每个分支都返回 200，非法 UUID 还被**静默丢弃**
- **Scope / Evidence**:
  - **静态（处理器无任何 400 分支）**：`ViewerController.batchGetObservations` 四条返回路径**全是 200**——
    ①`idStrings == null || idStrings.isEmpty()` → `ResponseEntity.ok(空列表, 0)`；
    ②过滤掉 `null` 与空白串后为空 → 同样 `ok`；
    ③`UUID.fromString` 抛 `IllegalArgumentException` → **`log.warn` + 置 null + `filter` 剔除**，**不报错**；
    ④剔除后 `ids` 为空 → 同样 `ok`。
  - **`ids` 的「必填」只是 OpenAPI 声明**：`ApiRequests.BatchObservationsRequest.ids` 上是
    `@Schema(requiredMode = REQUIRED)`，**不是** `jakarta.validation` 的 `@NotBlank`/`@NotNull`；
    全仓 `@NotBlank` 只出现在 `SessionEntity` 的两个字段上，**没有任何请求体 Bean Validation**。
  - **改前活体 OpenAPI**（`/v3/api-docs`）：`responses: ['200','400']`，且 400 的 schema 是
    `ErrorResponse`（`{"error": "..."}`）；`BatchObservationsRequest.required = ['ids']`。
  - **活体实测（2026-10-20，后端 37777）**，**带阳性对照**（真实 uuid 取自活体库，库中共 39,177 条观测）：

    | 请求 | 实测 |
    |------|------|
    | `{"ids":["<真实 uuid>"]}` | `200`，`count: 1` —— **阳性对照**，证明端点与该 id 都有效 |
    | `{"ids":["not-a-uuid"]}` | **`200`，`count: 0`** —— 非法 UUID 被静默丢弃 |
    | `{"ids":["<真实 uuid>","not-a-uuid"]}` | **`200`，`count: 1`** —— **部分结果，调用方无任何错误信号** |
    | `{"ids":[]}` | `200`，`count: 0` |
    | `{}`（缺 `ids`） | `200`，`count: 0` —— 声明为必填却接受缺失 |
    | `{"ids":""}`（JSON 类型错） | `400`，但 body 是 Spring 默认 `{timestamp,status,error,path}`，**不是**声明的 `{"error":...}` |

  - **三方契约互不一致**：OpenAPI 声明「400 + 必填」；`docs/API.md` 的请求表把 `ids` 标 `✅`；
    代码接受缺失与非法并返回 200。
  - **仓库自身脚本钉住了「宽容」行为**：`scripts/js-sdk-e2e-test.sh:211` 发
    `{"ids":["nonexistent-id"]}`（**不是合法 UUID**）并要求 2xx（`curl -sf` 在 4xx 上失败）；
    `scripts/java-sdk-e2e-test.sh:270` 对 demo 发 `{"ids":["e2e-test-1","e2e-test-2"]}` 同样是非法 UUID。
  - **第一方调用方都在客户端拦空 ids**：Java `CortexMemClientImpl:555`、Python `client.py:575`、
    JS `client.ts:310`、Go `client_methods.go:166`；MCP 的 `get_observations`
    （`ClaudeMemMcpTools.java:174`）也拦，且它**直查 repository、不走 HTTP**，故不受端点行为影响。
  - **后端测试对该端点零覆盖**（`backend/src/test/` 内无任何 `/api/observations/batch` 用例）。
- **Problem**: 机器可读契约（OpenAPI）**承诺了一件事，而实现从不兑现**：它声明了一个
  **没有任何代码路径能产生**的 400（连 body 形状都不对——真 400 是 Spring 默认体），
  又把 `ids` 标为必填，而处理器对缺失/空/非法一律 200。**更实质的危害是「静默的错误答案」**：
  调用方发 10 个 id、其中 3 个格式非法，会拿到 7 条观测且**不报任何错**——
  与「那 3 条观测恰好不存在」**完全不可区分**。这不是「遗漏」，是**断言为真而实际为假**。
  **为什么本轮只更正契约、不改行为**：仓库自己的两个 E2E 脚本**依赖**当前的宽容行为
  （见 Scope 第 4 条），把它们改成严格语义需要**同时改后端与脚本**，且属**对外行为契约变更**，
  按既定纪律不单方面实施；**本轮做的是「让契约说真话」**——删掉不可达的 400 声明、
  取消 `ids` 的必填标注、把实测行为写进双语 API 文档。
- **待决策（三选一）**：①维持宽容 + 文档说明（**本轮已实施的部分**）；
  ②改为「`ids` 缺失/为空/含非法 UUID 即 400」，**须同步改 `scripts/js-sdk-e2e-test.sh` 与
  `scripts/java-sdk-e2e-test.sh`**（用格式合法但不存在的 UUID 保住两脚本的**原意**）；
  ③折中：只对**缺失/为空**报 400，非法 UUID 仍宽容（但这样 OpenAPI 的
  「invalid ids」半句仍需改写）。
  **任一选项都应先补后端对该端点的测试**——目前是零覆盖。
- **Status**: ✅ **已修（第 365 轮，零行为变更）** —— **机器可读契约的失实陈述更正**：①`ViewerController` 删掉那条**不可达**的 400 `@ApiResponse`（处理器**没有任何 400 分支**；唯一的 400 来自 Jackson 拒收 `ids` 类型，body 是 Spring 默认体，**不是**声明的 `ErrorResponse`）；②`ApiRequests.BatchObservationsRequest.ids` 去掉 `requiredMode = REQUIRED`（该注解**只影响 OpenAPI 文档**、不构成校验，后端自始至终接受 `ids` 缺失）；③`docs/API.md` 与 `docs/API-zh-CN.md` 的 `ids` 必填标注 ✅ → ❌，并各补一段**实测行为说明**（承 P2-10 体例）。**改动全是注解与文档，可执行行 0**；改前改后**整份 OpenAPI 逐字对拍**证明爆炸半径恰为「1 个端点 + 1 个 schema」，其余 65 条路径与全部其它 schema **逐字未变**；**行为一字未变**（五种探针的码与 count 与改前完全相同）。**未单方面收紧行为**的原因：仓库自身脚本**钉住了宽容行为**——`scripts/js-sdk-e2e-test.sh:211` 发 `{"ids":["nonexistent-id"]}`（**非法 UUID**）并要求 2xx（`curl -sf`），`scripts/java-sdk-e2e-test.sh:270` 对 demo 发非法 UUID 亦然；**是否改为「缺失或非法即 400」属对外行为契约变更，留待用户决策**（见归档正文的三选一）。 条目全文已逐字迁入 [`…-65.md`](../archive/2026-10-20_backend-review-evidence-65.md)（第 365 轮）。

### P2-121: `docs/` 下 13 条相对链接的目标**根本不存在** —— 上游自动拆分后未更新，且「哪一篇承接了被引章节」需要内容判断，不宜机械改
- **Scope / Evidence**:
  - **扫描口径**：`docs/` 下 **506 个** `.md`，相对链接 **1,702 条**（排除 `http(s)`/`#`/`mailto:`，
    并**剔除代码围栏内**的内容），断链 **18 条**，分三类：
    1. **本批已修 15 条**（目标存在且能机械判定为同一篇）：
       - `docs/drafts/evolver-memory-analysis.md` 曾被拆成 `docs/drafts/evolver-memory/` 目录
         （commit `fb4262c` 删旧文件、`8ef2d53` 拆目录），**3 条短链**因此断掉
         （`memory-research-hub.md:11`、`evolver-memory/AGENT.md:11`、`evolver-memory/index-nav.md:228`）
         ——而 `evolver-memory/AGENT.md` 的约束 1 与约束 5 **明文要求该路径保持为短入口**
         （并把 `wc -c docs/drafts/evolver-memory-analysis.md` 列为例行自检），
         故本批**按 Hermes 同型体例补回短入口**（15 行指针文档），3 条短链**自愈且未改动任何既有正文**；
       - `evolver-memory/11-research-backlog.md:4` 目录前缀重复（`./evolver-memory/11-research-backlog.md`）；
       - `docs/evo-memory-implementation.md` 与 `-zh.md` 各 2 处用 `./` 指向**仓库根**的
         `backend/README.md` / `proxy/README.md`（应为 `../`）；
       - `hermes-memory/` 下 8 处**缺目录前缀**（`60-evolution/…`、`AGENT.md` 等）；
       - `hermes-memory/60-evolution/83-…:156` 引用 `70-compression-…`——**该编号从未存在过**
         （`git log --diff-filter=A` 零命中），实存的是 `82-compression-…`。
    2. **不可改 3 条**（在 `docs/archive/` 内，**归档文件创建后不得修改**）：
       `2026-03-13_v0.1.0-beta-release-draft.md:101`、`2026-10-02_health-check-history-4.md:24`、
       `2026-10-03_doc-review-history-69-81.md:19`。
    3. **探针误报 2 条**：`screenpulse-implementation-progress.md:56,88` 的 `[text](url)`
       是**散文里描述链接渲染**的写法（表格式说明与行内说明），不是 markdown 链接。
  - **本条记的是余下 13 条**，逐条如下（目标在仓库内**无任何候选**）：

    | 断链位置 | 目标 |
    |----------|------|
    | `hermes-memory/AGENT.md:98`、`hermes-memory/11-research-backlog.md:147`、`hermes-memory/index.md:41` | `60-evolution/105-shell-hooks-extensibility-architecture.md` |
    | `hermes-memory/60-evolution/82-compression-eval-harness-and-scrubber-pipeline.md:6,144` | `78-cross-cutting-architectural-patterns-synthesis.md` |
    | `hermes-memory/60-evolution/76-ce-gap-inventory-and-p0-unsafe-utf8-analysis.md:4` | `../02-bluecortexce-recommendations.md` |
    | `evolver-memory/09-aspect-bluecortex-bridge.md:53,79,108,109,110` 与 `evolver-memory/18-evolver-local-source-memory-architecture-snapshot.md:68` | `./01-intro-toc-memory-through-curriculum.md` |
    | `evolver-memory/09-aspect-bluecortex-bridge.md:116` | `./07-idle-through-skillpublisher-v14.md` |

  - **上游自动拆分的落点已查清**：`evolver-memory/01b-…-curriculum.md` 的首行注释写着
    「auto-split from **01-intro-toc-memory-through-curriculum.md** (sections 6-11)」，
    而 `01-architecture-overview.md` 承接 sections 1-5 —— 故被引的「§8」等**具体章节**
    分别落在**不同分片**里，**逐条判断归属是内容工作，不是链接工作**。
- **Problem**: 断链的三类里，前两类本批已处置，**余下 13 条是真正的「目标不存在」**：
  `105-shell-hooks-extensibility-architecture.md` 与 `78-cross-cutting-architectural-patterns-synthesis.md`
  在仓库里**从未以该路径存在**（`hermes-memory/60-evolution/` 下最接近的是
  `75-shell-hooks-external-script-bridge.md` 与 `82-compression-…`，**标题均不同**）；
  `02-bluecortexce-recommendations.md` 同样无候选；
  `01-intro-toc-memory-through-curriculum.md` 与 `07-idle-through-skillpublisher-v14.md`
  已被上游**自动拆成多篇**，被引的章节分散在不同分片。
  **这 13 条不能靠「找个存在的文件链上去」来修**——那样做会把读者送到**不承接该章节**的文档，
  比断链更糟（断链至少会立刻暴露）。**正确处置是逐条确认「哪一篇承接了被引内容」，
  属于内容判断，需要作者或用户确认**，故**记录不修**。
  **另记一条本轮的自查**：`docs/` 下 1,702 条相对链接的扫描**必须剔除代码围栏**，
  否则 Swift/Rust 源码里的 `"[\(title)](\(url))"` 会被当成 markdown 链接而误报——
  本轮据此把 2 条误报排除在缺陷之外。
- **Status**: ⏸ **记录不修** —— 13 条断链的目标**在仓库里不存在任何候选**（`105-shell-hooks-extensibility-architecture.md`、`78-cross-cutting-architectural-patterns-synthesis.md`、`02-bluecortexce-recommendations.md` 从未在此路径存在；`01-intro-toc-memory-through-curriculum.md` 与 `07-idle-through-skillpublisher-v14.md` 已被上游自动拆成多篇，**被引的章节归属需逐条内容判断**）。**机械改到任一现存文件都可能指错章节**，故**只记录不猜**。**同批已修的 15 条另计**（见归档正文），二者不可混为一谈。 条目全文已逐字迁入 [`…-65.md`](../archive/2026-10-20_backend-review-evidence-65.md)（第 365 轮）。
