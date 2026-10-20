# Backend Review Evidence 75 — 第 374 轮本轮新条目的整条正文（第 374 轮迁出）

> **归档规则**：承 `-62` ~ `-74` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 **21** 字节，本轮先做了**三处零信息损失压缩**才腾出空间 ——
> C3a「CJK 短词组·频次 ≥ 2」粗体去标记 **86** 处、C3b「CJK ≤ 4 字且唯一」粗体去标记
> **127** 处，**只删 `**` 标记、一个字未改**；C4「归档指针显示名去 `.md`」**5** 处，
> **链接 target 一字不动**（第 293 轮既有体例）；硬证明：显示名归一化后去掉全文所有 `*`，
> 字符流与压缩前**逐字相同**），工作文件只保留标题与 `- **Status**` 一行
> （末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：92 行 / 7531 字节。**
>
> 本批迁出 **P2-136** 一条新条目，并记录 **P2-131** 的跨家结转进度（Go / Python 已修，JS 待结转）。
>
> **本轮代码方向为 Python SDK，文档方向为 SDK README（doc round 274）。**
>
> **两条落在同一个根因上：tool-use 端点「接受但不落库」的三个字段。**
> 本轮把这件事在**第二个表面**（demo 的 HTTP 端点）上认了出来，并补齐了它在**第三个表面**
> （Python SDK 自身的 docstring 与 README）上的说明。
> 贯穿本轮的方法学纪律仍是两条：**探针自身出错必须先识别再采信**
> （本轮第一次的 ICL 三态探针把「省略 project」拼成了非法 JSON，四个 400 全是探针自己的错；
> 改用 Python 构造请求体后，README 的断言才被真正验证），以及
> **每个「0 行」结论都要配阳性对照**（tool-use 那次 0 行，配了 PATCH 与直接创建两条对照，
> 并同时给出静态签名的定案）。
---

### P2-136: 四家 demo 的 create 端点把 `source`/`extractedData` 送进 tool-use 路径 ⇒ **从不落库**

- **Problem**: 四家的 demo 都有一个 create 端点，接收 `source` 与 `extractedData`
  （Python / Go / JS 还收 `prompt_number`），并把它们交给 SDK 的
  `record_observation` / `RecordObservation` / `recordObservation`
  —— 那条路发往 `POST /api/ingest/tool-use`，而**后端在这条路径上只读六个字段**
  （`session_id`、`cwd`、`tool_name`、`tool_input`、`tool_response`、`platform_source`），
  这三个字段被**接受后丢弃**。demo 却一律返回 `{"status": "recorded"}`，
  调用方据此认为「来源归属 + 结构化数据」已经入库。
  - Python demo：`python-sdk/cortex-mem-python/examples/http-server/app.py:451`
    （`POST /observations/create`）—— 它**为 `extractedData` 的类型校验写了 12 行注释**
    （区分「显式 null」与「不是对象」），说明作者**是打算把它入库的**；
  - Go demo：`go-sdk/cortex-mem-go/examples/http-server/main.go:823`（`POST /create-observation`）；
  - JS demo：`js-sdk/cortex-mem-js/examples/http-server/app.ts:327`；
  - Java demo：`examples/cortex-mem-demo/src/main/java/com/example/cortexmem/ObservationsController.java:185`
    （`POST /demo/observations/create`，同样带 `extractedData` 的类型校验）。
  ⇒ **四家 demo 的 create 端点都做不到它们自己接受的那两个字段。**
  这与 P2-131（SDK 侧的 DTO/README 教用户设这三个字段）、P2-130（活体 OpenAPI 的 schema
  不说自己被丢弃）**同根**：tool-use 这条路径的「接受但不落库」在**四个表面**各留了一个坑。
- **实测记录**（本轮 Python 侧，**37790 新鲜实例**，jar 与 HEAD 同源）：
  - **被测路径**（SDK 的 `record_observation` → tool-use）：
    `source='p374src-tooluse'` 在 `mem_observations` 命中 **0 行**，
    该会话里 `prompt_number=42` 命中 **0 行**；
  - **阳性对照 B**（同一个 SDK 的 `update_observation` → `PATCH /api/memory/observations/{id}`）：
    `source='p374src-patch'` 命中 **1 行**；
  - **阳性对照 C**（`POST /api/ingest/observation` 直接创建）：
    `source='p374src-direct'` 命中 **1 行**、同会话 `prompt_number=42` 命中 **1 行**；
  - **探针自身的诚实说明（这一条必须记，否则读数会被误读）**：被测会话里
    **总行数也是 0** —— 本机 LLM chat 端点密钥失效（401），异步抽取根本没产出 observation。
    **所以单看行数，分不清「字段被丢」与「压根没成行」**；真正定案的是**静态签名**：
    把 `handleToolUse` 的方法体整段抽出来 grep `source|extractedData|promptNumber|prompt_number`，
    **零命中**（脚本以退出码 1 结束，即「无匹配」）；而
    `AgentService.processToolUseAsync` 的签名里**根本没有 source / extractedData 两个参数**，
    prompt number 位被**硬编码 `null`** ⇒ 那三个字段**在类型层面就无处可传**，
    与 LLM 是否可用无关。两条阳性对照则证明「列存在、查询可用、写路径可用」
    ⇒ 0 行是**字段级丢弃**，不是探针测不出。
  - **四家 SDK 都没有暴露 `POST /api/ingest/observation`**（实测：四家源码里该路径**零命中**，
    只在注释与测试里出现）⇒ demo 想真正落库，**没有 SDK 方法可用**。
- **为什么不单方面改**：三条修法**全是对外契约变更**：
  ① 让 SDK 暴露 `POST /api/ingest/observation` —— **新增公开 API**（四家都没有）；
  ② demo 的 create 端点改走直接创建路径 —— **demo 契约变更**（返回体形状、时序语义都变，
  且不再有 tool-use 的异步抽取行为）；
  ③ demo 明确拒绝这两个字段 —— **收窄契约**，破坏既有调用方。
  按既定纪律（对外契约变更不单方面实施）**记录不修**。
- **本轮已做的部分（注释级，零可执行行）**：Python 侧把「这三个字段在这条路上被丢弃」
  写进了四处 —— SDK 客户端 `record_observation` 的 docstring、demo create 端点的注释、
  demo README 的端点表 + 表下说明、SDK README（EN / ZH）的 Wire Format 段。
  **没有改动任何可执行行**，**没有改动 demo 的请求/响应契约**。
  Go / JS / Java 三家的 demo 侧属**跨家结转**，留待对应轮次。

### P2-131 的跨家结转进度（Java → Go → Python，JS 仍待）

- P2-131 的根因是**后端 tool-use 路径接受却不落库 `source` / `extractedData` /
  `prompt_number`**；第 372 轮修了 Java 侧（README 双语 + DTO 注释，零可执行行），
  第 373 轮修了 Go 侧（`dto/observation.go` 的类型注释 + 三个字段的行内注释，零可执行行），
  **第 374 轮修完 Python 侧**（`cortex_mem/client.py` 的 `record_observation` docstring
  + SDK README EN/ZH 的 Wire Format 段 + demo 注释与 README，零可执行行）。
- 三家定稿措辞一致：**指出后端处理器只读哪六个字段、这三个字段被接受后丢弃、
  并指向真正落库的 `updateObservation` / `UpdateObservation` / `update_observation`（PATCH）**。
- **JS 侧仍待结转**（`js-sdk/cortex-mem-js`）—— 该家的 `recordObservation` 同样是
  tool-use 路径。
- 本轮的活体证据与探针纪律见 P2-136 的「实测记录」段。
