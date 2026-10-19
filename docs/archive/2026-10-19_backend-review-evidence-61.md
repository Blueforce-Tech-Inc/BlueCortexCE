# Backend Review Evidence 61 — 第 361 轮本轮新条目的整条正文（第 361 轮迁出）

> **归档规则**：承 `-58` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 `P2-113` / `P2-114` / `P2-115` 三条——**本轮新写**的条目，
> 写作时 findings 的字节余量已近于零，故写完即整体迁出，工作文件各留标题与
> `- **Status**` 一行（含归档指针）。**正文逐字保留在本归档**，证据与结论均可回溯。
>
> **压缩背景**：本轮为腾空间已连做四批压缩——迁出 8 条已修条目（`-59`）、
> 按第 254 轮经用户决策的规则迁出 42 条待决条目的 `- **Scope / Evidence**` 段
> （`scope-evidence-9`）、按第 292 轮「零信息损失」规则缩短 27 条已修条目的 Status 行。
> **已结案条目早已全部归档，待决条目的 Scope/Evidence 亦已迁尽**，
> 余下的是各条目的**决策推理正文**，按规则**不得迁出**——故压缩手段已到尽头，
> 根本解是 P2-115 所记的**上调阈值**（待用户决策，本轮不实施）。
>
> 迁出前实测 **1512 行 / 151398 字节**；本归档实测 **127 行 / 12245 字节**。
### P2-113: 四家 SDK 的 fire-and-forget 失败**默认可见性 2:2 分裂** —— Python 与 Java 默认会记日志，**Go 与 JS 默认完全无痕**
- **Scope / Evidence**:
  - `go-sdk/cortex-mem-go/client_impl.go:91-96` —— `nopLogger` 的四个方法**全是空实现**
  - `go-sdk/cortex-mem-go/client_impl.go:107` —— `DefaultClientConfig()` 里 `Logger: nopLogger{}`
  - `go-sdk/cortex-mem-go/client_impl.go:393-441` —— `doFireAndForget` 在**三处**失败分支上调用 `c.config.Logger.Warn(...)`
  - `js-sdk/cortex-mem-js/src/client-options.ts:86` —— 默认 `logger` 是 `{ debug() {}, info() {}, warn() {}, error() {} }`
  - `js-sdk/cortex-mem-js/src/client.ts:756-779` —— `doFireAndForget` 调用 `this.config.logger.warn(...)`
  - `python-sdk/cortex-mem-python/cortex_mem/client.py:186-211` —— `_fire_and_forget` 用**标准 `logging`** 的 `logger.warning(...)`
  - `cortex-mem-spring-integration/cortex-mem-client/.../CortexMemClientImpl.java:774-793` —— `executeWithRetrySilent` 用 SLF4J `log.warn(...)`
- **实测（本轮亲跑，非静态推断）**: 临时 Go 探针（`/tmp` 独立 module + `replace` 指向本仓库 SDK，**仓库零改动**），
  指向**关闭端口** `127.0.0.1:9` 使请求层必然失败：
  - **场景 A（默认 logger）**：`RecordObservation` 返回 **`err=<nil>`** 且**零输出** —— 重试 3 次全败，SDK 一声不吭。
  - **场景 B（同一调用，装 `slog` logger）**：**3 条 WARN** 清晰可见（`failed, retrying` ×2 + `failed after retries` ×1），
    返回值同样是 `err=<nil>`。
  - **场景 B 是阳性对照**：它证明探针**能**看见输出，故场景 A 的「无输出」为真，**不是探针坏了**。
  - **Python 对照实跑**：同一场景下输出 **2 条** WARNING（`failed, retrying` + `failed after 2 attempts`），返回 `None`。
  - 由此确定：**两家的 fire-and-forget 对调用方都是「永远成功」**（Go `err=nil` / Python `None`），
    差别**只在失败是否留痕**。
- **四家图景**:

  | SDK | 失败时的动作 | 默认可见？ |
  |---|---|---|
  | Python | `logger.warning(...)` ×2（标准 `logging`） | **✅ 可见** |
  | Java | `log.warn(...)`（SLF4J） | **✅ 可见** |
  | Go | `c.config.Logger.Warn(...)` ×3，默认 `nopLogger{}` | **❌ 无痕** |
  | JS | `this.config.logger.warn(...)`，默认空对象 | **❌ 无痕** |

- **影响面（诚实界定，不夸大）**: 只在**请求层失败**时才有影响（连接被拒、非可重试 4xx 等）。
  **不是**「后端收下了却没落库」——那种情况**四家都看不见**：本轮实测后端
  `POST /api/ingest/tool-use` 返回 `200 {"status":"accepted"}` 后异步加工失败、库里 0 条，
  而 `go-sdk` 的 `examples/basic` 照样打印「Observation recorded successfully」并以 **0** 退出；
  那是**已知的环境限制**（LLM 密钥失效），与本条无关。
  真正的后果是：**请求根本没发出去、或被 4xx 拒绝时，Go 与 JS 的用户默认得不到任何线索**。
- **文档状态**: Go README `:116` **双语都记着** `WithLogger` 默认 *(nop)* / （空操作）——
  故「默认静默」本身是**有记载的自觉设计**，本条**不是失实陈述**。
  但**后果未记载**：README 未说明「捕获失败因此默认不可见」，也未说明「想看到失败必须自己装 logger」；
  而 **Python README 明确告诉用户**「the SDK **logs** it and returns `None`」——
  **四家里只有 Go/JS 默认无痕，这一差异在 Go README 里从未被提及**。
- **为什么不单方面改**: 改 Go/JS 的**默认 logger**（如换成 `slog.Default()`）会让**原本安静的库突然产生输出**，
  属对外可见的行为变更；补文档则落在 **SDK README** 方向，不属本轮（代码）方向的顺手修改。
- **建议修法（待确认，二选一）**:
  1. **改默认**：Go 用 `slog.Default()`、JS 用 `console`，与 Python/Java 对齐 —— 代价是新增默认输出。
  2. **补文档**：在 Go/JS README 的 fire-and-forget 节写明「默认不输出，失败请自行 `WithLogger`」，
     并在 Python README 补一句「默认走标准 logging，可被静默」。
- **测试侧同缺**: 四家的 fire-and-forget 测试均只断言「不抛错」，**没有一条断言失败会被记录**。
- **Severity**: 中（静默丢数据 + 与 Python/Java 行为相反，但需请求层失败才触发，不涉及安全）。
- **Status**: ⏸ **记录不修** —— 修法形状需作者或用户确认，见上。

### P2-114: 根 README 的架构图把薄代理标为 **(Express)**，而同一仓库的 `ARCHITECTURE.md` 与中文版**早在 2026-10-01 就已改正** —— 根 README 是那次**漏改**的那一份
- **Scope / Evidence**: `README.md:191-192`（架构图）与 `README.md:205`（Core Components）。
  图中 `│  (Express)      │────────▶│  (Spring Boot)   │`。
  **对照事实**：`proxy/package.json` 的 `dependencies` **只有 `axios`**，`proxy.js` 用 Node 内置
  `http.createServer(handleRequest)`（`proxy/proxy.js:328`），**全文 `express` 出现 0 次**。
  **活体验证**（本轮亲启 `node proxy.js`）：启动横幅自报 `Listening: http://127.0.0.1:37778` /
  `Java API: http://127.0.0.1:37777`，横幅与日志里 `express` **0 次**；验毕已停，37778 恢复空闲。
- **这不是新问题，而是同族问题的一次漏改**: 归档
  [`2026-10-01_health-check-history.md`](../archive/2026-10-01_health-check-history.md):154 逐字记载
  「**ARCHITECTURE.md/ZH 两处称代理层为 "Express"**，实际 `proxy/package.json` 仅依赖 axios，
  `proxy.js` 用 Node 内置 `http` —— 已将架构图与技术栈表改为 "Node.js/axios"（**中英四处同步**）」。
  磁盘现状与该记载一致：`docs/ARCHITECTURE.md:86-87` 已作 `(Node.js)`、`:282` 记
  `package.json # Dependencies (axios)`；`docs/ARCHITECTURE-zh-CN.md:85` 同为 `(Node.js)`、
  `:941` 技术栈表为 `Node.js/axios`。**唯独根 README 未在同批更正** ——
  即「**修掉一个说法 ≠ 修掉复述它的每个说法**」的又一次复发。
- **顺带修正的第二处（同一张图）**: 原图把 `wrapper.js` 与 Thin Proxy 画成**串联两框**，
  而实际二者是**同一层里的两个组件**：`wrapper.js` 是 hook 调用的 CLI 入口，**直连后端 37777**
  （`proxy/wrapper.js:84` 的 `javaUrl` 默认即 `http://127.0.0.1:37777`），
  `proxy.js` 是**可选**的本地聚合 HTTP 服务（37778）。
  **阳性对照**：`wrapper.js` 里 `37777` 命中 **12** 次，而 `37778` 与 `proxy.js` 各命中 **0** 次
  ——同一 grep 看得见前者，故两个 0 是读数而非探针失灵。
  `docs/ARCHITECTURE.md:166-171` 的画法（`wrapper.js (CLI entry)` 与 `proxy.js (HTTP server optional)`
  并列于同一个 Thin Proxy 框内）是**正确体例**，本轮按其对齐。
- **Status**: ✅ **已修（第 361 轮，零行为变更，纯文档）** —— 架构图标注由 `(Express)` 改为 `(Node.js)`；
  Core Components 的 Thin Proxy 条目补上「本层是 Node.js + axios、`proxy.js` 用 Node 内置 `http`
  而非 Express」与「`wrapper.js` 直连 37777、`proxy.js` 为可选的 37778 聚合服务、**两者不串联**」。
  **未改 ASCII 框体**：重画会让相邻框的对齐全部位移，收益不抵排版风险，故以文字补足事实。
  本条**只改 `.md`**，代码指纹 `903928c8…` / 2218 未变 → **未触发完整验收**（符合规则）。
- **本轮一并复验为零缺陷的相邻断言**: 根 README 的 hook 配置（第 131~134 行）与
  `wrapper.js --help` 输出**逐条吻合**（`commandToHookMap`，`proxy/wrapper.js:1489-1494`）；
  `/stream` 端点**确实存在**（`StreamController` 的 `@RequestMapping("/stream")`，活体 spec 亦有）；
  badge 的 Spring Boot 3.3 与 pom 的 3.3.13 相符；「Java 21」与 pom 的 `release 21` 相符。
  **两处曾被我怀疑、实为正确**: ①`session-start` 等四个命令在 `wrapper.js` 里的 `case` 出现在
  `case 'cursor':` 之后，一度像是 cursor 子命令——实为「无 stdin 事件」的兜底分支，
  真正主路径是 `commandToHookMap`；②`proxy.js` 的 `case 'session-init'` 一组**才是** cursor 子命令，
  与 Claude Code 的四个 hook 命令**分属两套体系**，README 只展示后者并无遗漏。

### P2-115: `backend-review-findings.md` 的字节上限**已不可能靠压缩解决** —— 建议把阈值按第 293 轮同比例上调至 **2000 行 / 200000 字节**
- **现状（本轮实测）**: 1493 行 / 149395 字节，**距上限仅 605 字节**。本轮为腾空间已连做**四批**压缩：
  ①迁出 8 条已修条目（`-59`）；②按第 254 轮**经用户决策**的规则迁出 42 条待决条目的
  `- **Scope / Evidence**` 段（`scope-evidence-9`）；③按第 292 轮「零信息损失、只缩短显示名」
  规则缩短 27 条已修条目的 Status 行（**归档链接与轮号逐条不变，已断言**）。
  **已结案条目早已全部归档**（工作文件里剩下的全是两行指针），**待决条目的 Scope/Evidence 亦已迁尽**，
  余下 **904 行**是各条目的**决策推理正文**——按规则**不得迁出**。故压缩手段已到尽头。
- **建议（待用户决策，本轮不实施）**: `MAX_LINES=2000` / `MAX_BYTES=200000`。
  **行/字节比例仍为 100**，与第 293 轮（1000 行 / 102400 字节 → 1500 行 / 150000 字节）**保持一致**。
  **实现无需改脚本**：`scripts/doc-growth-check.sh` 的两个阈值**本就是环境变量**
  （`readonly MAX_LINES="${MAX_LINES:-1500}"` / `readonly MAX_BYTES="${MAX_BYTES:-150000}"`），
  只需把 `docs/drafts/cron-combined-task.md` 正文中「当前阈值为任一文件超过 1500 行或 150000 字节」
  那一句同步为新值即可。
- **为什么是 200000 而非更大**: 按现阈值，findings 每行约 **100 字节**，而 `doc-review-task.md`
  每行约 **393 字节**、`health-check-task.md` 每行约 **54 字节**——**同一阈值对不同文档松紧相差七倍**。
  上调到 200000 后 findings 约有 5 万字节余量（约 8 轮），`doc-review-task.md` 另余 8.6 万字节，
  两者都不再需要靠每轮压缩腾挪。
- **Severity**: 低（不涉代码正确性），但**每轮都要消耗一次压缩动作**，且压缩本身有**历史风险**
  ——第 258 轮曾因切割区间复用而整块销毁 P2-47。
- **Status**: ⏸ **记录不修** —— 属流程契约变更，需用户决策。
