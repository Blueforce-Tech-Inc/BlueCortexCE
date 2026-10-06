# Backend Review Evidence 42 — 第 317–321 轮已结案的整条条目（第 325 轮迁出）

> **归档规则**：承 `-40` / `-41` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**（标题 + Scope / Evidence + Problem + Status），工作文件只保留标题与一行
> 指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/backend-review-findings.md` 再次越过 1500 行 / 150000 字节
> **双阈值**（写入 P2-90 后实测 **1548 行 / 150073 字节**，`doc-growth-check.sh` 退出码 **2**）
> 而迁出 **4 条**：P2-83 / P2-84 / P2-85 / P2-86，分别结案于第 317 / 319 / 320 / 321 轮。
> 上一批（`-41`）只把文件压回 1500 行**整**，行数余量为 0 —— 本批因此一次多迁 4 条
> 以留出余量，而不是再做一次贴线压缩。
>
> **注意 P2-84**：该条目记录的 `TrimRight` 修复正是**第 319 轮把 Go SDK README 的
> 测试计数写过期**的同一次提交（`7a78266`）—— 见 P2-90，两件事同源。

## 块 1 / 4：P2-83 全文（第 325 轮逐字迁出）

### P2-83: `PATCH /api/session/{id}/user` —— **11 处仍用错路径变量名**，是第 157 轮那次清扫的残留
- **Scope / Evidence**: 活体 `/v3/api-docs` 权威写法
  `PATCH /api/session/{sessionId}/user`，`params=['sessionId']`；
  源码 `SessionController.java:294` 的 `@PatchMapping("/{sessionId}/user")`。
- **Problem**: 全仓 `.md` 仍有 **11 处**把该路径写成 **`/api/session/{id}/user`**，
  分布在 **10 个文件**：

  | 文件 | 处数 |
  |---|---|
  | `backend/README.md` | 1 |
  | `cortex-mem-spring-integration/README.md` / `README-zh-CN.md` | 1 + 1 |
  | `js-sdk/cortex-mem-js/README.md` / `README-zh-CN.md` | 1 + 1 |
  | `docs/DEPLOYMENT.md` / `DEPLOYMENT-zh-CN.md` | 1 + 1 |
  | `docs/api-json-naming-convention.md` | 2 |
  | `docs/go-sdk-guide.md` | 1 |
  | `docs/drafts/js-sdk-design.md` | 1 |

  **这不是新错误类**：第 157 轮已把同一处替换在**十个文件、十四处**全部更正
  （见 `patrol-rotation.md` 第 256 轮条目），**这批是当时漏掉的**。
  **危害与当年相同**：读者照抄去拼 URL 或按 `{id}` 做断言替换，会与真实路径模板不符；
  更实际的是**这类文档被生成工具消费时**，变量名错位会造成难以定位的失败。
- **⚠️ 刻意未动的同类写法**：`/api/memory/observations/{id}` 的变量名**确实是 `id`**
  （活体 `params=['id']`，`MemoryController:277`、`:427`），全库 **94 处**全部保留，
  **一个都没改**——第 157 轮已就此事立过规矩：**按断言清扫不等于按前缀清扫**。
- **Status**: ✅ **已修（第 317 轮）** —— 11 处全部改为 `{sessionId}`，
  属**失实陈述的更正**，中英双语一并处理。改后核验三项：
  **非历史文件残留 0 处**；**`/api/memory/observations/{id}` 仍为 94 处（未被误伤）**；
  `git diff --numstat` 恰为 **11 行增 / 11 行删**，无任何附带改动。
  **已刻意排除**：归档文件（不可修改）、`patrol-rotation.md`（历史记录，记的是当时做了什么）、
  health-check / doc-review / findings 三份工作文件（其中提到该字符串的是历史叙述，非断言）。

## 块 2 / 4：P2-84 全文（第 325 轮逐字迁出）

### P2-84: Go SDK 的 `base_url` 规范化**只去一个**尾斜杠，而另三家去全部 —— 同一份配置在四家里三成一败
- **Scope / Evidence**: 四家实现逐行对照 + 活体实测。
  | SDK | 位置 | 写法 | 去掉几个尾斜杠 |
  |---|---|---|---|
  | Python | `cortex_mem/client.py:86` | `base_url.rstrip("/")` | **全部** |
  | JS/TS | `src/client-options.ts:68` | `.replace(/\/+$/, '')` | **全部** |
  | **Go** | `client_impl.go`（原 `:122`） | `strings.TrimSuffix(cfg.BaseURL, "/")` | **仅一个** |
  | Java | `CortexMemClientImpl` | 交给 `RestClient.baseUrl()` 内部处理 | 不适用 |
- **Problem**: `TrimSuffix` 的语义是「删掉末尾**一个** `/`」。
  故 `WithBaseURL("http://host:37777//")` 规范化后仍是 `http://host:37777/`，
  与 `path` 拼接成 `http://host:37777//api/version`。
  **这不是理论问题——活体实测后端不折叠空路径段**：
  `GET http://127.0.0.1:37777//api/version` → **HTTP 404**，
  响应体为 Spring 的 `{"status":404,"error":"Not Found","path":"//api/version"}`。
  **即该配置下 Go SDK 的每一个请求都是 404**，而**完全相同的配置值在 Python 与 JS 里正常工作**。
  形态与第 300 轮记的「重试极性三对一」同型：**四家里三家行为一致、第四家单独不同**，
  而差异只在**多写一个斜杠**时才显形，故极难在正常使用中察觉。
  **既有测试只覆盖单个尾斜杠**：`client_test.go` 的 `TestNewClient_TrailingSlashNormalization`
  传的是 `server.URL + "/"`；**双斜杠此前无任何测试**。
  **文档侧亦无自陈**：Go SDK README 中英双语 `grep -i "trailing|尾斜杠|末尾斜杠"` **零命中**。
- **Severity**: 低——`http://host//` 属配置笔误，正常输入（无尾斜杠、单个尾斜杠）本就正确。
  但**失败形态是最坏的一种**：不是报错而是**静默的全量 404**，且**只在一家里发生**。
- **Status**: ✅ **已修（第 319 轮）** —— `TrimSuffix` → `TrimRight`。
  属**纯加宽 / 向后兼容修正**：**当前能工作的任何输入行为都不变**，
  受影响的只有那些**本来就 100% 失败**的输入，故可单方面实施。
  **补测一条**（`TestNewClient_DoubledTrailingSlashNormalization`）覆盖此前完全无覆盖的双斜杠。
  **双向注入验证**：保留修复后全绿 → 回退为 `TrimSuffix` 后**恰好**新测试失败、
  既有单斜杠测试仍通过 → 恢复后全绿。**是数据与断言互相印证，不是只跑通就算数。**

## 块 3 / 4：P2-85 全文（第 325 轮逐字迁出）

### P2-85: Python demo 注释称「四家与后端一致」，但后端**接受十六进制**而四家全部拒绝 —— 实测断言
- **Scope / Evidence**: `python-sdk/cortex-mem-python/examples/http-server/app.py` 的
  `_parse_int_param` 注释（`:141-146`）原文：
  > A regex pins the grammar to "optional sign, then digits" … **so all four demos match the backend**,
  > which rejects "1_0" with 400 (round 211 recheck).
- **Problem**: 该等价断言在**十六进制输入上不成立**。**活体实测**（对一个含 100 条观测的 project，
  后端为本轮自起的 37790 实例）：

  | 查询参数 | 后端实际行为 |
  |---|---|
  | `limit=10` | 返回 **10** 条 |
  | `limit=0x10` | 返回 **16** 条 ← **十六进制** |
  | `limit=0x5` | 返回 **5** 条 |
  | `limit=010` | 返回 **10** 条 ← **十进制**，不是八进制 |
  | `limit=1_0` | **400**（注释所举之例，确为真） |

  Spring 的 `NumberUtils` 把 `0x` 前缀当十六进制，故 `0x10` = 16 ——
  **`0x10`→16 与 `10`→10 不可混淆，属决定性证据**。
  而**四个 demo 全部拒绝** `0x10`：Go demo 自己在 `main.go:65` 写明
  「it still rejects "0x10" and "10abc"」，Python/JS/Java 三家则由 `[+-]?\d+` 这条文法排除。
  **故「四家一致」为真，「四家与后端一致」为假。**
- **按断言清扫的结果**：全库只有**这一处**写了「与后端一致」的等价断言。
  Go demo 那句**本身准确**（它只陈述自己拒绝什么，未声称与后端等价），故**未动**。
- **Severity**: 低——`?limit=0x10` 这类输入现实中几乎不出现，
  且 demo 比后端**更严格**本身不是危害。**但它是本循环迄今第一次在源码注释里发现的失实陈述**，
  而本项目的长期主题正是「宁可少说，不要说错」，故仍予更正。
- **Status**: ✅ **已修（第 320 轮）** —— 改为**限定范围**的准确表述：
  四家在**十进制文法上**互相一致、且与后端一致（并补上实测的 `010`→十进制 10），
  另起一段说明**十六进制是四家共享的刻意分歧**（后端接受、四家拒绝）。
  **纯注释改动**：13 增 / 2 删，经 `git diff -U0` 逐行核验**每一行都以 `#` 开头或为空行**，
  **无任何可执行行变更**；Python **453/453** 全绿。

## 块 4 / 4：P2-86 全文（第 325 轮逐字迁出）

### P2-86: API 文档把两条 DELETE 路由的路径变量写成 `{uuid}`，活体是 `{id}` —— 第 157/317 轮同类
- **Scope / Evidence**: **活体 `/v3/api-docs` 权威写法**（37777 实例）：
  ```
  DELETE  /api/summary/{id}      params=[('id', 'path')]
  DELETE  /api/observation/{id}  params=[('id', 'path')]
  ```
  源码 `ViewerSessionController.java:43,49` 亦为 `@DeleteMapping("/observation/{id}")` / `("/summary/{id}")`。
- **Problem**: `docs/API.md:1816-1817` 与 `docs/API-zh-CN.md:1812-1813` 把这两条写成
  **`/api/observation/{uuid}` 与 `/api/summary/{uuid}`**，**共 4 处**。
  这批文档由另一进程在 `6e5890d` 中新增，正落在本轮文档轮换方向内。
  **这不是新错误类**：第 157 轮修过 14 处、第 317 轮修过 11 处同一类（`{id}` → `{sessionId}`）。
  **成因可解释但仍为失实**：`deleteObservation(@PathVariable UUID id)` 的**参数类型**是 `UUID`，
  作者据类型写了 `{uuid}`，而**路由变量名**是 `id` —— **类型与变量名是两回事**。
- **危害与当年相同**：读者照抄 `{uuid}` 去拼 URL 或据其做模板断言替换，会与真实路径模板不符；
  文档若被生成工具消费，这类错位会造成难以定位的失败。
- **按断言清扫的结果**：全库（排除归档与 `node_modules` 第三方内容）**恰好这 4 处**，
  分布在 **2 个文件**。**该文件自身即自相矛盾**：`API.md` 全文路径变量普查为
  `{typeId}`×6、`{templateName}`×4、`{projectName}`×4、**`{id}`×3**、`{uuid}`×2、`{sessionId}`×2、
  `{platformSource}`×1、`{contentSessionId}`×1 —— 同一份文档里 `{id}` 与 `{uuid}` 并存。
- **Status**: ✅ **已修（第 321 轮）** —— 4 处改为 `{id}`，中英双语一并处理，属**失实陈述的更正**。
  改后核验：**残留 0**；`git diff --numstat` 恰为 **每文件 2 增 / 2 删**，
  且逐行核验**只有那 4 行路径行变动**、无任何附带改动。
- **⚠️ 刻意未验证的部分**：该节其余断言（SSE `item_deleted`/`session_deleted`、`afterCommit` 时机、
  409 的四个状态、`limit` 钳制、`offset` 下限）**逐条核到源码为真**
  （`ViewerSessionService.java:55,60,106,123,136,149,158`），
  但 **DELETE 是破坏性端点，本轮一律未调用**；404/409 的**运行时**行为**未实测**，仅代码可证。

