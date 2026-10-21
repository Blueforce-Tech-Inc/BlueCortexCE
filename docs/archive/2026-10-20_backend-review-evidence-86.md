# Backend Review Evidence 86 — 第 384 轮本轮新条目的整条正文（第 385 轮迁出）

> **归档规则**：承 `-62` ~ `-85` 的体例，迁出**本轮新写**的条目全文。
> 本轮**没有新的压缩杠杆**——第 384 轮腾出的余量（161 行 / 2334 字节）足够写完本轮。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：93 行 / 6606 字节。**
>
> 本批迁出 **P2-158** 一条新条目。
>
> **本轮代码方向为 Go SDK，文档方向为 SDK README（doc round 284）。**
>
> 本轮的形状是**「校验」这件事在 Go SDK 侧的同一个缺口**：
> 客户端校验是唯一挡在「空必填字段」与「后端」之间的东西，
> 而 Go 的 **35** 个校验用例**打的是本机常驻实况后端**，全仓又**没有任何「请求数」断言**
> ⇒ 「校验在请求之前」这条顺序没人钉住；一旦校验回归，后端又不校验 `cwd`
> ⇒ 那三个捕获方法的用例会**往实况数据库真写一行**，而旧断言照样绿。

---

## P2-158 —— 全文

### P2-158: Go SDK 的 **35** 个校验用例用裸 `NewClient()`（默认打**常驻实况后端** 37777），而全仓**零「请求数」断言** ⇒ 校验的**顺序**没被钉住，且一旦校验回归就会**往实况数据库写行**

- **Scope**: → [scope-9][a6]、[scope-10][a7]
- **事实一：35 个校验用例打的是实况后端，不是测试服务器。**
  `client_test.go` 里用**裸** `cortexmem.NewClient()`（不带 `WithBaseURL`）的用例共 **35** 个，
  而 `NewClient()` 的默认 BaseURL 是 `http://127.0.0.1:37777`（`client_impl.go:109` 与 `:126`）——
  正是本机**常驻运行的**后端。其余全部用例都用 `httptest.NewServer` 或 `newTestClient(server)`。
- **事实二：全仓零「请求数」断言。** `client_test.go` 里 `requestCount` / `reqCount` /
  `callCount` 之类计数器**一个都没有**；`sync/atomic` 只在别处用过。
  ⇒ **「校验发生在请求之前」这条顺序，没有任何用例钉住**。
  这正是 **P2-147** 在 JS 侧发现并修掉的缺口（Python 侧同轮修掉），而 **Go 侧从未补**。
- **事实三：一旦某个客户端校验回归，后果不是「测试红」而是「实况库多一行」。**
  后端 `IngestionController` 对三个捕获端点**只校验 `session_id` 与 `tool_name`**，
  **`cwd` 不校验**（`handleToolUse` `:122-127`、`handleUserPrompt` `:187-188`、
  `handleSessionEnd` `:234-235`）⇒ **空 `projectPath` 会被接受并入队**。
  该结论此前已**活体实测**（归档 `2026-10-03_doc-review-history-69-81.md`：三个端点对
  「省略 `cwd`」与「发送 `"cwd": ""`」**一律返回 200**）⇒ 记录会以空项目入队、
  **不出现在任何按项目过滤的查询里、且全程无任何错误提示**。
  ⇒ 三个捕获方法里若有空 `ProjectPath` 的校验回归，**测试会往实况数据库真写一行**，
  而旧断言只检查错误消息，**照样通过**。
- **旧断言为什么钉不住顺序**：Go 的校验返回 `ValidationError` 并**直接 return**，
  若把校验挪到请求**之后**，错误消息**一字不变**（仍是 `toolName is required`），
  旧用例照样绿 —— 只有「请求确实没发出去」才区分得开。

### 修法（只改测试，实现一行未改）

- 新增 helper `newValidationClient(t)`：起一个 **hermetic** `httptest` 服务器，
  用 `atomic` 计数器记录收到的请求，**只要收到任何一个请求就让该用例失败**
  （`t.Cleanup` 里 `t.Errorf`），并把 BaseURL 指向它而不是实况后端。
- 把 **35** 个裸 `cortexmem.NewClient()` 的用例全部改用该 helper —— 一行替换，
  **不新增用例、不改断言逻辑**。
- 新增 **1** 个**阳性对照** `TestValidationClient_HermeticServerDoesReceiveRequests`：
  同一个计数器 + 一次**合法**调用 ⇒ 断言请求数**恰为 1**。
  没有它，「没有请求到达」也可能是因为「客户端压根没跟服务器说话」⇒ 守卫无牙。

### 变异测试（证明守卫有牙，且旧断言看不见）

| 变异 | 注入 | 结果 |
|------|------|------|
| **1** | `RecordObservation`：把 `toolName` 校验从「请求之前」挪到「请求之后」 | `TestRecordObservation_Validation_EmptyToolName` **失败**，且失败来自**新守卫**（`client_test.go:55: client-side validation must run before the request, but the server received 1 request(s)`）；**旧断言（错误消息）没有触发**——错误消息仍一字不差 |
| **1b** | 在变异 1 的基础上，把该用例**临时改回**变异前的写法（裸 `NewClient()`） | 该用例 **PASS**（耗时 0.25s = 一次真实的网络往返）⇒ **旧写法在同样变异下照样绿**，且请求真的打到了常驻实况后端 |

变异后 `client_methods.go` 按 **sha256**（`eb6d5d8a69f8f8cedbc05c3964c767b522de5628967bf6727208a9f1fcda9e54`）
逐字节还原。

**其余核实为真**：Go README 的「25 methods」＝ 接口 **27** 个方法减去 `Close`/`String`
（`client.go` 逐个列出核对）；「10 MiB」＝ `MaxResponseBytes = 10 << 20`（`client_impl.go:191`）；
「9 separate Go modules」＝ 仓库里确有 **9** 个 `go.mod`；测试计数按 README 的口径
（`=== RUN   Test` 顶层）实测：根模块 **307**（core **240** + dto **67**）、
eino **8** + genkit **13** + langchaingo **12** + `examples/http-server` **33**
⇒ 合计 **373**（改前 **372**）。

---

## 压缩记录

本轮 findings 的写入前余量为 **161 行 / 2334 字节**（第 384 轮 C8 腾出后），
本轮只新增 1 条条目 + 2 条结转的 Status 更新 ⇒ **本轮未再压缩**，
新增杠杆数为 **0**。

终值：**1342 行 / 148932 字节**（净 **3 行 / 1266 字节**）。

**探针自身的一处错**（记在这里，避免下次重犯）：
本轮写「校验用例打的是实况后端」这个结论时，第一版用
`grep -c "@Test"` 式的静态口径去数「哪些用例没用 httptest」，
差点把**函数体内出现 `cortexmem.NewClient()` 但同时带 `WithBaseURL`** 的用例也算进去；
改成**逐个函数体判断「有裸 `NewClient()` 且无 `WithBaseURL` / `newTestClient`」**后
才是准确的 **35** 个。⇒ **数「某写法出现了多少次」时，判定条件必须写成函数级的，
不能只 grep 一个词**。
