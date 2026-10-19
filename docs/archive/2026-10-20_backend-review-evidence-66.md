# Backend Review Evidence 66 — 第 366 轮本轮新条目的整条正文（第 366 轮迁出）

> **归档规则**：承 `-62` / `-63` / `-64` / `-65` 的体例，迁出**本轮新写**的条目全文
> （写作时 findings 的字节余量只剩 3766 字节），工作文件只保留标题与 `- **Status**` 一行
> （末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 **P2-122** 一条 —— **本轮新写**的条目，写完即整体迁出，
> 工作文件留标题与 `- **Status**` 一行（含归档指针）。**正文逐字保留在本归档**。
>
> **本轮代码方向为 Java SDK，文档方向为架构文档（doc round 266）。**
> P2-122 的客户端侧部分由**代码方向**产出并已修（注释层失实陈述 + README 补行），
> 架构文档双语的说明段属**文档方向**产出；服务端鉴权本身属**对外行为契约变更**，只记录不实施。
>
> 迁出前实测 **1413 行 / 148842 字节**；本归档实测 **78 行 / 7165 字节**。

### P2-122: 四家 SDK 都能配 API 密钥，但**后端从不校验它** —— 密钥今天是**零保护**，而三份 README 把它写成「for auth」，Java SDK 的 javadoc 更直接断言「Null/blank = no auth」
- **Scope / Evidence**:
  - **静态（服务端根本不存在鉴权机制）**：
    - `backend/pom.xml` **无 `spring-boot-starter-security` 依赖**（全文件零命中）；
    - `backend/src/main/java/` 全量搜 `Authorization` → **0 命中**；
    - 搜 `SecurityFilterChain` / `WebSecurityConfigurer` / `@PreAuthorize` /
      `OncePerRequestFilter` / `HandlerInterceptor` → **只命中 `MdcAutoFilter`**，
      而它读的是 `X-Correlation-ID` / `X-Session-ID`、并把 correlationId **回写到响应头**，
      **不读** `Authorization`。
  - **客户端侧（四家都发 `Bearer <key>`，且 Java 自带测试断言这一点）**：
    - Java `CortexMemClientImpl:87-91`（`defaultHeader("Authorization", "Bearer " + apiKey)`），
      仓库自带测试 `CortexMemClientImplTest:450` `apiKey_sendsAuthorizationHeader`
      断言该头**确实发出**（客户端侧是有意为之且有测试的）；
    - Go `go-sdk/cortex-mem-go/client_impl.go:240`；
    - Python `python-sdk/cortex-mem-python/cortex_mem/client.py:110`；
    - JS `js-sdk/cortex-mem-js/src/client.ts:621`。
  - **活体实测（2026-10-20，后端 37777，**带阳性对照**）**：
    - `GET /api/stats` **不带** `Authorization` → `200`；
    - `GET /api/stats` + `Authorization: Bearer <垃圾值>` → `200`，**响应体逐字相同**；
    - `GET /api/stats` + `Authorization: Basic <垃圾值>` → `200`，**响应体逐字相同**；
    - **阳性对照**：把 `X-Correlation-ID: p366-control-9f3a` 发过去，
      `MdcAutoFilter` **把它回写进响应头** ⇒ **同一个探针确实能读出请求头带来的响应差异**，
      故上面三条「无差异」是**真观察**而非探针失灵；
    - 对照：`GET /api/definitely-not-a-real-path-xyz` → `404` ⇒ 探针能区分响应。
  - **文档侧四家的措辞**：
    - JS README `:88`：`| apiKey | — | Bearer token for auth |`；
    - Python README `:175`：`Sent as Authorization: Bearer <key> on every request`；
    - Go README `:99`：`cortexmem.WithAPIKey("my-api-key")`；
    - **Java SDK README 此前完全不提及 `api-key`**——它的 `cortex.mem` 属性表 12 行，
      `api-key` / `apikey` / `authentic` / `bearer` **零命中**，
      而 `CortexMemProperties` 里这个属性**一直存在**且有测试覆盖 ⇒ **「支持但没写」**。
  - **与既有条目的关系（不重复立条）**：findings 里「加注（不改写归档）」那条
    已记录**后端无鉴权**本身及其 compose 暴露面——那是**服务端**视角；
    本条是**客户端**视角：**四家都提供一个服务端不认的开关**。
    两者指向同一个事实的不同侧面，故本条**不重复那一条**，只在其上叠加客户端侧的失实陈述。
- **Problem**: 一个可配置的开关，在**四家**的客户端侧都被呈现成**鉴权凭据**——
  Java 的 javadoc 直接写「API key for authentication … **Null/blank = no auth**」，
  而**服务端从头到尾没有对应的校验**。后果是**具体的**：用户配了密钥 →
  请求带着 `Authorization: Bearer <secret>` 出去 → 服务端与不带密钥的请求
  **完全同等对待**（实测响应逐字相同）。这不是「遗漏一个功能」，是
  **注释与文档断言了一件不存在的事**；更实质的是它**在四家同时成立**，
  且密钥会**随每次请求发往每一个被配置的地址**——Python `client.py:99-104` 已就
  「不要把 API 密钥发给无关主机」做过防护：它把 `Authorization` 放在**每请求头**里，
  **不写进调用方共享的 `session.headers`**，原文写得很直白
  （「writing Authorization into it would send this API key to unrelated hosts」，
  头本身在 `:110`），说明这条风险**曾被想到过**，只是**没有落到「服务端根本不认它」这一层**。
  **为什么本轮只改注释与文档、不实现服务端鉴权**：给后端加鉴权 =
  改变**所有**现有部署的对外契约（今天无密钥的部署会立刻 401）=
  **对外行为契约变更**，按既定纪律不单方面实施。
- **待决策（三选一）**：①**维持现状 + 文档说真话**（**本轮已实施的部分**）；
  ②**后端实现「可选鉴权」**——`Authorization` 缺失时按现状放行、存在时校验，
  属**向后兼容的新增能力**，但需定「密钥从哪来、如何轮换、无密钥时是否放行、
  与 compose 默认部署如何共存」；③**移除客户端的 `apiKey` 选项** ——
  会**破坏**已有调用方，属破坏性变更。
  **任一选项都应先补服务端对该头的测试**——目前后端对该头的测试**为零**。
  **另记一条本轮的自查**：另三份 README 的措辞（JS 的「for auth」等）属
  **Go / Python / JS 三个轮换方向**的文档，本轮不越界改，只在本条记下。
- **Status**: ⏸ **服务端鉴权本身记录不实施**（给后端加鉴权 = 改变**所有**现有部署的对外契约 = 对外行为契约变更，按既定纪律先记后定）—— **客户端侧四处失实陈述本轮已修**（全为注释与文档，可执行行 0）：①`CortexMemProperties.apiKey` 的 javadoc（原文「Null/blank = no auth」**为假**）；②`CortexMemClientImpl` 的「Bearer token auth」措辞；③Java SDK README 属性表**补 `api-key` 行**（此前完全没有）；④架构文档双语 `### Authentication` 段**各补一段**。Java SDK `mvn clean test` **196 通过 / 0 失败**。**三选一见归档正文**。 条目全文已逐字迁入 [`…-66.md`](../archive/2026-10-20_backend-review-evidence-66.md)（第 366 轮）。
