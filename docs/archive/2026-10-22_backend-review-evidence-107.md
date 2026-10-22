# 后端审查证据归档 -107（2026-10-22）

> 第 406 轮（Java demo 代码方向 + API 文档方向，doc round 305）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1330 行 / 148686 字节**）。
> 本文档体量：**195 行 / 14384 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的两条（正文逐字保留在本归档）

### P2-212: Java demo 的 **44** 个端点绑定的 query 参数名**无一被钉住** —— 端点面钉住了，绑定名没钉住

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **Problem**: Java demo（`examples/cortex-mem-demo`）的 **44** 个端点，每一个都把
  query 参数绑到方法形参上，而**绑定的那一串名字从头到尾没人钉过**：
  - `DemoEndpointSurfaceTest` 钉的是**端点面** —— 44 条路径与 HTTP 方法在不在、拼写对不对；
  - `DemoParamsTest` 钉的是**整数解析规则**（`?limit=10` / `?limit=abc` 的取值行为）。
  ⇒ 两者都**不碰「这个端点到底认哪几个 query 参数名」**。
  ⇒ 而参数名写错**不会报错**：Spring 只绑定它声明过的名字，多的名字**连一个字都不吭**
    ⇒ 症状是「那个参数**没生效**」，不是「请求失败」。
  ⇒ demo 与后端**是同一套绑定**（demo 控制器直接透传后端客户端），
    所以这条缺口的后果与第 405 轮的 P2-209（JS SDK 出站 query 参数名）**同族**。
- **实测（修复前）：9 个控制器各改一个绑定名，demo 套件仍 53 / 53 全绿。**
  变异手法取**最小作用域** —— 只给注解加 `name = "xZZ"`（`@RequestParam(name = "xZZ")`），
  **不动方法体**（仍可编译），一次构建覆盖 9 个控制器各一个参数槽。
  ⇒ **44 个端点的绑定名，一个都没被钉住。**
- **活体 A / B（demo 实例 37778）**：
  - 变异版：`GET /demo/tool?path=marker.txt` → **200**，但读回来的是默认的 `hello.txt`
    ⇒ **读者传的值被静默丢弃**；
  - 对照版（还原源码并**重新编译**后）：同一请求 → **200**，且读到 `MARKER-CONTENT-42`
    ⇒ 值被采纳。
  - `/chat` 在**两侧都 500**（本机 DeepSeek 凭据失效）⇒ **不可用作探针**。
- **修法（只加测试，demo 主源码零改动）**：新增
  `examples/cortex-mem-demo/src/test/java/com/example/cortexmem/DemoQueryParamsTest.java`
  （**3** 例）：
  1. **44 端点的完整 query 键集合**按集合相等比对（手写期望值；**24** 个端点有参数、共 **68** 个槽）；
  2. README 里**文档化**的 query 参数**必须被绑定**（文档 ↔ 代码双向绑定）；
  3. **可证伪自检**（证明这套断言确实能红）。
  ⇒ demo 套件 **53 → 56 / 56**。
- **负控（9 个控制器各改一个绑定名）**：新测试 **2 / 3 红**，其余 **53** 个仍绿
  ⇒ 新断言**有牙**，且**没有**误伤既有断言。
- **定性**：**覆盖缺口**，不是行为缺陷 —— demo 现有绑定与后端 `@RequestParam` 一致，
  缺的只是「有人钉住它」这件事。
- **探针自身的四处错（全部在写成发现之前被拦下）**：
  1. 源解析器把**注解的参数**（`defaultValue` / `false`）当成了参数名；
  2. 阳性对照 **5 / 6 不符** ⇒ 该解析器的结论**整体作废**（没有阳性对照就不该采信它）；
  3. 漏了**类级** `@RequestMapping` 前缀 ⇒ 端点数从 44 变成 **41**；
  4. 无 path 值的 `@GetMapping(produces = …)` 未处理 ⇒ 又变回 **44**。
  另有一处工具侧陷阱：`shutil.copy2` **保留 mtime** ⇒ Maven **跳过重编** ⇒
  「对照组」实际跑的是变异产物 ⇒ 必须 `touch` 之后才作数。
- **Status**: ✅ **已修（406 轮，Demo 侧，只加测试；demo 主源码零改动）** —— 新增
  `DemoQueryParamsTest`（**3** 例）钉死 44 个端点的**完整 query 键集合**（**68** 槽）
  并核对 README 文档化的参数确实被绑定；负控：9 个控制器各改一个绑定名 ⇒
  新测试 **2 / 3 红**、其余 **53** 个仍绿；正文与证据逐字见归档 [`…-107`][a65]。

### P2-173 的结转证据：第 406 轮实施归档 `-90` 列出的**候选修法 ①**

- **原条目**：`P2-173`（`/api/context/preview` 的 `platformSource` 参数在活体 OpenAPI 与代码里
  **都真实存在**，但中英双语文档**全篇 0 次提及**）—— 第 396 轮记、归档 `-90` 存正文，
  当时的处置是「⏸ 只记录不实施 —— 文档完整性问题，等作者裁定」，
  并在归档正文里列出**三条候选修法**。
- **本轮实施的是候选修法 ①**：「EN / ZH 两张 preview 表各补一行（与机器可读契约对齐）」。
  ⇒ 原条目的**定性**（遗漏，不是失实）**不变**；变的是它现在**已经被修掉**。
  ⇒ 按追加式审计链的纪律，**原条目正文一字未改**，只在 `- **Status**` 上**标注**。
- **活体契约复核（37790，**本轮新起**的实例，jar 比全部后端源码都新）**：
  `GET /api/context/preview` 的 OpenAPI 参数表实测 **10** 个 query 参数，
  末位是 `platformSource`（`in: query`, `required: false`, 无 default）。
- **代码路径复核**：`ContextController.java:343` 读入 → `:409` 传入
  `generateContextWithFilters` → `ContextService.java:371-383` **按它是否为空切换两条仓储查询**
  （`findByTypeAndConceptsWithSessionLimit` ⇄ `…ForPlatformSource`；
  摘要侧同理 `findByProjectPathOrderByCreatedAtEpochDesc` ⇄ `…AndPlatformSource…`）。
- **活体 A / B（证明它**真的生效**，不是装饰性参数）**，项目
  `/Users/yangjiefeng/Documents/Blueforce-Tech-Inc/BlueCortexCE`：

  | `platformSource` | HTTP | 响应字节 | md5 |
  |------------------|------|----------|-----|
  | 不传 | 200 | 4661 | `55977bd1e93c3bd9d6771f77abb21249` |
  | `claude` | 200 | 4661 | `55977bd1e93c3bd9d6771f77abb21249` |
  | `cursor` | 200 | 73 | `f32513a91e540b625410338d6a8b77a1` |
  | `__nonexistent__` | 200 | 73 | `f32513a91e540b625410338d6a8b77a1` |

  ⇒ 不匹配时返回**空状态**（73 字节），匹配时返回**完整预览**（4661 字节）⇒ 参数**真实生效**。
  ⇒ **第一版对照无效**：最初选的探针项目 `/tmp/phase3-acceptance-test` 的 observation
  `type` 全部不在 `safeTypes` 内（`extracted_user_preference` / `user_statement` / `test`），
  三态响应**逐字节相同**（83 字节空状态）⇒ 那是**探针选错**，不是参数无效。
- **全量反向对拍（不只查这一个端点）**：把活体 spec 的**每一个** query 参数名
  与两份文档的**每端点参数表**逐一对拍，解析出 **68** 个端点。
  ⇒ 除本条外**零遗漏**；`/api/summaries` 与 `/api/prompts` 报出的 5 个「缺失」经读上下文判定为
  **引用式写法**（EN 写「Query parameters and response format are the same as List Observations」，
  ZH 写「**查询参数**: 同 `/api/observations`」）⇒ **不是遗漏**。
- **对拍探针自身的四处错**（全部在采信其结论之前被拦下）：
  1. `method.lower() in ("GET", …)` **恒为假** ⇒ spec 侧解析出 **0** 个操作；
  2. 围栏代码块的「首行」索引记错 ⇒ EN 文档只解析出 **5** 个端点（实为 68）；
  3. 参数表表头正则要求第二列是 `Type` ⇒ EN 的 `/api/context/inject`（**3** 列表）**假阳性**；
  4. 与 2 叠加 ⇒ 首版**全绿**而实际上一个端点都没比。
  ⇒ 修好后加了**阳性对照**（往 spec 注入一个假参数 `zzzSelfTestParam`，
  要求脚本必须报 `MISSING`）与**解析量下限**（每份文档至少 40 个端点）⇒ 自检 **PASS** 才采信。
- **补充的覆盖缺口（非失实）**：API 文档的**端点集合**已有机器校验
  （`scripts/check-api-doc-surface.py`，回归里跑，带 `--self-test`），
  但**每端点的参数名 / 请求体字段**此前**无任何机器校验**。
  本轮实测二者均与活体 spec 吻合 ⇒ 记为**覆盖缺口**，本轮不入仓新脚本（守住「一轮一方向」）。
- **Status**: ✅ **已修（406 轮，文档方向，实施归档 `-90` 的候选修法 ①）** ——
  EN / ZH 两份 API 文档的 `GET /api/context/preview` 参数表**各补一行** `platformSource`；
  活体 A / B 证明该参数真实生效；全量反向对拍确认**这是唯一一处遗漏**；
  原条目的「⏸ 只记录不实施」为**历史状态**，本行予以**标注**（原文一字未改）；
  正文与证据逐字见归档 [`…-90`][a48] 与本归档 [`…-107`][a65]。

## 按 C8 从工作文件迁入的证据段（逐字保留在本归档）

> 迁出的是 **⏸ 待决条目**的**证据 bullets**（第 254 / 384 轮规则）；
> `- **Problem**` / `- **Status**` / `- **Severity**` / `- **⚠️ 为什么不单方面改**` /
> `- **建议修法**` 一律**留在工作文件**。

### P2-91: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - **冲突是实打实的，不是理论风险**：本仓库 `docs/drafts/backend-review-findings.md` 里
    `P1-1` / `P1-2` 指的是另外的缺陷（P1-2 = 「Java demo 的 `?path=` **无任何路径校验**」）。
    于是在本仓 `grep P1-2` 得到的是**互不相关**的主体。

### P2-93: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - **危害：完全静默。两次都返回 HTTP 200 `{"status":"ok","observationId":"…"}`**，
    调用方**无从得知评论没被清空。与 P2-26 相比这里连半信号都没有**
    （P2-26 至少在只设 `Facts` 时会抛「at least one field must be provided」）。
  - **与 P2-26 / P2-27 的关系（全文见归档）**: 同属「空值被省略 → 无法表达清空」这一族，但**不同端点、不同范围**（P2-26 是 Go 的 PATCH observations、P2-27 是 Python 的 `extractedData`）。

### P2-27: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - ⚠️ **第 362 轮已推翻本条 Problem 的一半**（追加式审计链只可标注，**原文不改写**）：
    本条**只对 dataclass 路径成立** —— `client.update_observation` 的 **kwargs 路径既不查 `None` 也不查空 dict**，
    故 `content=None` / `extracted_data={}` **真的上了 wire**，而后端 PATCH 语义正是「显式 null ⇒ 清除该字段」
    ⇒ **Python 其实能清空这两列，只是只有 kwargs 路径能**（活体端到端实测：`content` 列 NULL、`extracted_data` 列 `{}`）。
    ⇒ Problem 里「Python 两种都发不出」「**一条已有 extractedData 的观测无法通过 Python SDK 清空它**」
    对 kwargs 路径**为失实**，Status 里「让 `{}` 发上 wire……从「不变」变成「落 `{}`」」这一表述的
    **事实前提亦不成立**（今天经 kwargs **已经在落 `{}`**）。归档 `-11` 的四家能力阶梯表同样只覆盖 dataclass 路径，
    **归档文件创建后不得修改，故更正只落在本处**。新证据与正文见 **P2-116**。

### P2-43: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - **第 363 轮的补注（本条目在第 247 轮写下的「已在下方补注」，直到本轮才真正落地；全文见归档）**:
    那句补注从未出现在本条目里 —— 第 247 轮与 P2-26 都写着「P2-27 的能力表应当立即标注 kwargs 路径……
    已在下方补注」，而本条目从那时起只有 Problem / Status 两段。本轮已就地补上（见 **P2-27** 的第 362 轮更正）。
    本条的证伪也从未被同步到 JS SDK 的复述点（`js-sdk/cortex-mem-js/src/dto/observation.ts` 与其**双语 README**
    仍写着「四家里只有 JS 能把字符串字段清空为 NULL、Python 跳过 `None`」，而那句话在第 247 轮就已被本条目证伪），
    本轮已更正（**P2-117**）；另，本条的证伪在第 362 轮被**用 SDK 本身**（而非 demo）做了活体端到端复验
    —— kwargs `content=None` 之后 `mem_observations.content` 列**实为 NULL**（`psql` 直查），见 **P2-116**。

### P2-79: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - 对 P2-78 复查计数的影响：第 312 轮为 P2-78 的第 2/3 次复查，
    但在同一段代码里发现本条新问题 → 按既定纪律计数重置，从第 312 轮重新记 1/3。

### P2-82: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - **未证实的部分，不写**：本轮**未实测**「真的让 Python 侧 OOM」——
    上面是**代码路径层面的断言**（无上限检查、requests 会缓冲），
    **不是**「已复现的内存事故」。

### P2-20: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - 危害：`startEpoch` / `endEpoch` 是时间戳，`0x` 前缀会把它们解释成一个
    1970 年附近的 epoch 毫秒值，静默返回空时间窗而无任何报错。`lines` 会被读成
    一个行数，`maxObservations` 会被读成一个条数。都没有校验、没有警告。
  - 对照：布尔参数不受影响（`?includeObservations=0x1` → 400）；四家 SDK 把这些
    参数声明为数字类型，根本无法把十六进制字面量放到线上，故只影响直接调用
    HTTP API 的代码。

### P2-37: 的证据段

> 原条目保留在 `docs/drafts/backend-review-findings.md`，此处为它的证据正文（逐字）。

  - **分歧是「已知且被写下」的**：`ChatController` 自己的 Javadoc 明写
    「The Go, Python and JS demos all answer `POST /chat` with a JSON object」，
    **紧接着就改用 `@GetMapping`**——写下了差异却没有解决。


## 本轮链接定义

```
[a65]: ../archive/2026-10-22_backend-review-evidence-107.md
```
