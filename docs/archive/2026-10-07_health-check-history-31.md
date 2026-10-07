# 健康检查历史 27 — 第 336 轮（第 349 轮迁出）

> **归档规则**：承 `-19` ~ `-26` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数取自 `doc-growth-check.sh` 的实测值，不手写。

> 本批为写入第 349 轮报告后 `docs/drafts/health-check-task.md` 达 **1501 行**
> （行数越线；字节尚未越线）而迁出 **1 轮**：第 336 轮。
>
> 工作文件里第 312~331 轮已是一行指针（更早各轮在 `-19` ~ `-26` 中），
> **第 336 轮是最早仍保留完整报告的一轮**，故只迁这一轮即可取回余量。
> 轮次边界按**轮号**匹配 `## 第 N 轮 — ` 定位，**不按行号相邻**——第 342 轮教训。

## 块 1 / 1：第 336 轮全文（第 349 轮逐字迁出）

## 第 336 轮 — 2026-10-07T09:05:00+08:00

代码方向：**Java SDK**（**零缺陷**，含一次差点造成**假 finding** 的惊险）；
文档方向：**设计文档**（doc round 236，`docs/drafts/mcp-server-status-and-roadmap.md` —— **零缺陷**）

### 健康预检

37777 在监听（pid 4029，保留），health 200。指纹 `5d960e9b…` / 2218 **与基线一致**，
工作区干净，HEAD = `712afda`。

### 代码方向（Java SDK）：把第 335 轮的回归审计延伸到 Java 侧

**JAVA-3（第 173 轮）的修复完好，且它那两条跨 SDK 断言经核实属实**：

- `isRetryable` 确实只重试 **429/502/503/504** 并显式排除 500（`CortexMemClientImpl.java:834`）；
- 注释称「The four status codes match **Go SDK `isTransient()`** exactly」——
  Go 的 `isTransient` **确实存在**（`client_impl.go:452`），且它 `return IsRetryable(err)` 委派，
  四个码与 Java **逐个一致**；
- `jitteredBackoff` 的「base = backoff × attempt、抖动 ±25%、最小 1ms」与 Go 的
  `jitteredBackoff` 注释一致（`base ± 25%`，配置里有 100ms 下限）。

**本轮最惊险的一次 —— 差点写出一条假 finding 并「修」一份正确的 README。**

README 双语都写着「**196 unit tests**（143 client + 46 spring-ai + 7 starter）」。
我先跑 `mvn -o test`（**没带 `clean`**），再 glob `*/target/surefire-reports/TEST-*.xml` 汇总，
得到 **143 / 47 / 10 = 200**，与 README 差 4 —— 看起来是一条标准的 P2-90 型计数过期。

**但 `mvn -o clean test` 的权威汇总行是 `Tests run: 143 / 46 / 7`，合计正好 196**，
**与 README 逐位吻合**。clean 后重新聚合 XML 也是 3 文件/143、8 文件/46、2 文件/7，与 Maven 自身一致。
**成因：不 clean 时 `target/surefire-reports/` 里残留着已不存在测试类的旧 XML**，
被我的 glob 一并计入（client 模块无残留所以对得上，spring-ai +1、starter +3）。

**若照第一版读数行事，我会「修」一份完全正确的 README，并留下一条不存在的 finding。**
权威来源是 **Maven 自己打印的每模块 `Tests run:` 汇总行**，不是 `target/` 里的文件聚合。
已把这条写进本轮报告的纪律段。

### 文档方向（设计文档，doc round 236）：`mcp-server-status-and-roadmap.md`

零引用的设计文档有 10+ 份；选这份是因为它描述的是**已交付功能**（MCP server），
断言可逐条对着代码与活体核。

| 文档断言 | 核验 | 结果 |
|---|---|---|
| `Spring Boot 3.3.13` / `Spring AI 1.1.2` | `backend/pom.xml` `<spring-ai.version>1.1.2</spring-ai.version>` | ✅ |
| **已接入 5 个 MCP tools** | `@McpTool` 计数 = **5**，名字 `search` / `timeline` / `get_observations` / `save_memory` / `recent`，与 `AGENTS.md` 记载**逐字一致** | ✅ |
| 当前采用 **SSE**、单协议、STREAMABLE 仅作备选 | `application.yml` `protocol: SSE`，`sse-endpoint: /sse`，`streamable-http.mcp-endpoint: /mcp` 作为**配置项存在但非激活** | ✅ |
| 「可工作」的远程 MCP Server | 活体 `GET /sse`（`Accept: text/event-stream`）→ **200** | ✅ |
| `ServerProtocol` 枚举含 `SSE`/`STREAMABLE`/`STATELESS` | 从 `spring-ai-autoconfigure-mcp-server-common-1.1.2.jar` 解出该 class，`javap` 显示**恰好这三个常量** | ✅ |

最后一条比文档自己声明的「已从官方 Javadoc 确认」**更硬** —— 我验的是**实际字节码**。

**关于 STATELESS 404 那条断言**：文档把「实测得 404」写成自己的实验结论，
把根因写成 **「更可能是 Spring AI 1.1.2 的 AutoConfiguration Bug」** 并引 PR #4179 ——
**推测部分有明确对冲**，没有把假设写成事实。切换协议需重启后端，
而本轮只允许停止自己启动的进程，故**未实测复核该 404**，仅记录其表述方式得当。

### 变更检测

本轮**未改任何代码或既有文档**，未新增 finding。指纹 `5d960e9b…` / 2218 **与基线一致**
→ **不跑完整验收、不推进基线**。37777 保留运行。

### 本轮踩到的仪器错误（三处）

①**同一型错误第二次发生**：Java 注释引用 Go 的 `isTransient`，我 grep 后紧跟一句
**无条件 `echo` 的「(empty = no such function)」**——grep 其实返回了 **3 条真实命中**，
是我自己那句提示语会让人读成「不存在」。**这个坑 `health-check-history-18.md:617` 已经记过一次**
（「`isTransient` 一度 grep 不到、差一步就记成『Java 引用了不存在的函数』」），
本轮**差点重蹈**。教训：`echo` 的说明文字必须与命令的退出状态绑定，不能无条件打印。
②**surefire XML 未 clean 即汇总**，虚报测试数 200（详见上），最严重的一次。
③解压 class 文件时把 `rm` 串在 `cd /tmp` 之后的相对路径上，被安全策略拦下 ——
改用绝对路径与全新目录名后正常。

### 下一轮

代码方向：**Go SDK**；文档方向：**架构文档**（doc round 237）。
