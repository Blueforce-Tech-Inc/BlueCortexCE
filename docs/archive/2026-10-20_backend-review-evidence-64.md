# Backend Review Evidence 64 — 第 364 轮本轮新条目的整条正文（第 364 轮迁出）

> **归档规则**：承 `-62` / `-63` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的字节余量不足），
> 工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 **P2-119** 一条 —— **本轮新写**的条目，写完即整体迁出，
> 工作文件留标题与 `- **Status**` 一行（含归档指针）。**正文逐字保留在本归档**。
>
> **本轮代码方向为 Demo，文档方向为 SDK README。** 本条是**代码方向**的产物；
> 文档方向经四个角度核查**零缺陷**：Java README 的 Backend API Alignment 表 25 行端点
> 逐条对活体全部存在且 V14/Phase 3 标注逐条为真；八份双语 SDK README 的 `/api/` 字面量
> 幻影 0；四对中英数字经**段落级**比对全部对等；安装坐标逐条对清单吻合；
> 四家 demo README 的端点表 26/26 准确。
>
> 迁出前实测 **1405 行 / 145125 字节**；本归档实测 **63 行 / 6937 字节**。

### P2-119: 四家 demo 的监听地址 **1:3 分裂** —— Java demo 随 P1-2 硬化为只绑回环，**Go / Python / JS 三家至今绑所有网卡、零鉴权**，并把「只绑回环的后端」变成了**同网段可达**的 API
- **Scope / Evidence**:
  - **静态（四份源码，行号为 2026-10-20 现状）**：
    - `go-sdk/cortex-mem-go/examples/http-server/main.go:894` —— `addr := ":" + port`（**通配地址，无 host**）
    - `python-sdk/cortex-mem-python/examples/http-server/app.py:766` —— `app.run(host="0.0.0.0", port=PORT, debug=False)`（**显式 0.0.0.0**）
    - `js-sdk/cortex-mem-js/examples/http-server/app.ts:548` —— `app.listen(PORT, ...)`（**不传 host，Node 默认监听 `::` 双栈**）
    - `examples/cortex-mem-demo/src/main/resources/application.yml:8` —— `address: ${SERVER_ADDRESS:127.0.0.1}`（**回环，唯一一家**）
  - **活体实测（2026-10-20，四家 demo 与后端同机同起，同一条 `lsof` 命令）**：
    - Java demo（37778）：`IPv6 TCP 127.0.0.1:37778 (LISTEN)` —— **回环**
    - Go demo（37779）：`IPv6 TCP *:37779 (LISTEN)` —— **所有网卡**
    - Python demo（37780）：`IPv4 TCP *:37780 (LISTEN)` —— **所有网卡**
    - JS demo（37781）：`IPv6 TCP *:37781 (LISTEN)` —— **所有网卡**
    - 后端（37777）：`IPv6 TCP 127.0.0.1:37777 (LISTEN)` —— **回环（阳性对照：同一工具在同一台机上能区分绑定地址，故上列四行不是探针失效）**
  - **Python demo 自己的启动横幅即自证**：`Running on all addresses (0.0.0.0)`，并直接列出 `http://10.166.1.125:37780`（本机 LAN IP）。
  - **LAN 连通性实测**（`curl --noproxy '*'` 绕开本机透明代理；**判据看退出码与异常类型，不用端到端计时** —— 第 190 轮教训）：
    - demo → LAN IP `10.166.1.125:37780`：**exit 28（超时）**、`time_connect=0.0016s` → **SYN 被丢**
    - 后端 → LAN IP `10.166.1.125:37777`：**exit 7（连接被拒）** → **无监听**
    - demo → `127.0.0.1:37780`：**HTTP 200**
    - `socketfilterfw --getglobalstate` → `Firewall is enabled. (State = 1)`
    - **结论：今天挡住 LAN 访问的是本机 macOS 应用防火墙，不是应用本身。**
  - **鉴权**：三份 demo 源码对 `authorization|apikey|api_key|bearer|token|auth` 的 grep **各命中 0 处**。
  - **demo 确实代访后端**（否则「代理放大」只是推断）：经 Python demo 的 `GET /stats?project=/` 返回**后端真实库计数**
    `{"database":{"totalObservations":0,"totalProjects":1,"totalSessions":1,"totalSummaries":0},"worker":{"isProcessing":false,"queueDepth":0}}`；
    四家 demo 的 `/version` 均转发后端的 `{"version":"0.1.0-beta","service":"claude-mem-java",...}`。
- **Problem**: 四家 demo 面向同一件事（把 SDK 暴露成 HTTP），**在「谁能连上它」这一层上 1:3 分裂**：
  1. **Java demo 是唯一绑回环的一家，而且是专门硬化过的** —— `examples/cortex-mem-demo/src/main/resources/application.yml:5-7`
     的注释写着「Loopback only. Without this the server binds *:37778 ... (P1-2)」，
     其 README 第 64–77 行把「只绑回环」写成**显式安全属性**并说明理由。
  2. **另外三家绑所有网卡、零鉴权，且无法改成回环** —— Go 的 `addr` 写死通配、Python 写死 `0.0.0.0`、
     JS 不传 host；Java 那家至少有 `SERVER_ADDRESS` 逃生口。
  3. **危害被 demo 放大了** —— 后端自己**只绑回环**（`backend/src/main/resources/application.yml:3`），
     这正是它在共享网络上的第一道防线；而 demo 跑在**用户本机**、**代为访问** `127.0.0.1:37777`，
     于是**回环这道防线被旁路**：同网段任何能连到 demo 的人，就等于直连后端那套 API。
     读全部记忆（`GET /observations`、`/search`、`/stats`、`/experiences`、`/iclprompt`）、
     改与删（`PATCH /observations/{id}`、`DELETE /observations/{id}`）、
     写（`POST /observations/create`、`POST /ingest/prompt`、`POST /ingest/session-end`、`POST /session/start`）、
     触发 LLM 作业（`POST /refine`、`POST /extraction/run`）。
  4. **三份 README 都没把这件事告诉读者** —— Go README 第 27 行只平铺直叙「It binds `:37779` by default」，
     **不点明它意味着同网段可达**；Python 与 JS 的 README **对绑定地址只字未提**（`bind` 命中数各 0）。
  5. **先例就在本仓库** —— Java demo 当年正是因为 P1-2（第 294 轮**经用户授权**）才硬化成回环绑定。
     即**本仓库自己已认定「demo 应当只绑回环」**，另外三家从未跟进。
- **Status**: ⏸ **记录不修** —— 把默认改成回环 = **收窄**可观测面（远程可达性消失）= **对外行为契约变更**；且 P1-2 那次同类硬化是**经用户授权**才实施的（第 294 轮）。按既定纪律（对外契约变更不单方面实施）先记后定。**建议修法（待决策）**：①三家默认改绑回环，并给 Go/Python/JS 各加一个与 Java `SERVER_ADDRESS` 对齐的 host 覆盖口（**纯加宽、默认行为不变**）；②保留默认，但在三份 README 写明暴露面与「经 demo 即等于经后端」的放大关系；③维持现状，仅记录。**本轮未实施任何代码改动**（零代码变更 → 指纹未变 → 按规则跳过完整验收、不推进基线）。 条目全文已逐字迁入 [`…-64.md`](../archive/2026-10-20_backend-review-evidence-64.md)（第 364 轮）。
