# 健康检查历史 24 — 第 328 轮（第 341 轮迁出）

> **归档规则**：承 `-19` ~ `-23` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为写入第 341 轮报告后 `docs/drafts/health-check-task.md` 达 **1531 行**
> （行数越线；字节尚未越线）而迁出 **1 轮**：第 328 轮。
>
> 工作文件里第 312–327 轮本就已是 3–4 行的一行指针（更早各轮在 `-19` ~ `-23` 中），
> **第 328 轮是最早仍保留完整报告的一轮**，故只迁它一轮即可取回余量。

## 块 1 / 1：第 328 轮全文（第 341 轮逐字迁出）

## 第 328 轮 — 2026-10-07T06:40:00+08:00
代码方向：**Demo**（**零缺陷**，`mvn test` 退出码 0）；文档方向：**运维/用户指南**
（doc round 228，`docs/TESTING.md` 双语，**零缺陷**）
**连续第三轮只读**

### 健康预检

37777 在监听（pid 4029，本会话早前启动并保留），health 200；其余验证端口空闲。
指纹 `a4525469…` / 2218 **与基线一致**。工作区干净，HEAD = `a9a1964`。

### 代码方向（Demo）：`DemoErrors` 的自陈计数逐条实测

`DemoErrors.java`（108 行）的类 Javadoc 自陈了三处计数与一条跨 demo 断言，全部核对：

| 自陈 | 实测 |
|---|---|
| 「**twelve** controllers」 | **12**（`^@RestController` 精确匹配；`@RestControllerAdvice` 会被子串匹配骗到，见下） |
| 「**forty** `catch (Exception e)` blocks」 | **40** |
| 「only **three** of those reach these helpers」 | **3**（`FeedbackController` 1 + `ObservationsController` 2） |
| 「The **ten** other controllers answer a flat 500」 | 未走助手的 37 个 catch 恰好分布在 **10** 个控制器 ✓ |
| 「Go demo maps NotFound to 404，Python 与 JS 直通后端状态」 | Go `main.go:432,450` `errors.Is(err, cortexmem.ErrNotFound)` → 404；Python `app.py:52` `status = exc.status_code if 400 <= exc.status_code < 600 else 502`；JS `app.ts:531-532` `else if (err instanceof APIError) errorJson(res, err.statusCode, err.message)` ✓ |

**两处探针错误，都在采信前识别**：
①`grep -c "catch (Exception e)"` 得 **42**，比 docstring 多 2 —— 因为 **docstring 自己那句
「forty {@code catch (Exception e)} blocks」也被 grep 数进去了**（另加一处注释）。改用剥掉块注释
与行注释后按**代码**重数，得 **40**，与自陈一致。
②`"@RestController" in code` 把 `DemoParams.java:120` 的 **`@RestControllerAdvice`** 误判为控制器
（子串匹配），得 13。改用 `^@RestController\b` 行首锚定后得 **12**。
另有一处括号配对写错（depth 从 0 起算却跳过了 `{`），修正后助手 catch 块数才可信。
**三处都是仪器错，docstring 是对的。**

### 文档方向（运维/用户指南，doc round 228）：`docs/TESTING.md`

该文档以**计数声明**为主，正是 P2-90 那类会随提交漂移的数字，故逐一实测：

| 声明 | 实测 |
|---|---|
| `phase3-acceptance-test.sh`「**15 test functions**」 | **15** 个 `test_*()`（全文件 24 个函数 = 15 测试 + 9 助手），函数名逐条可列 ✓ |
| `run-all-e2e.sh`「runs the **10** local E2E suites」 | `run_suite` 调用编号 **1/10 … 10/10**，恰好 10 个 ✓ |
| 「Suite **5/10** `mcp-streamable-e2e-test.sh` 是条件执行的；默认安装下实际运行 **9** 个、报告 1 个跳过」 | 第 5 个的 `run_suite` 调用确实**缩进在 `if` 内**（`run-all-e2e.sh:145`）✓ —— 这条非平凡的跳过计数**成立** |
| 表中列出的 29 个脚本 | **全部存在，零幽灵条目**；反向差集为 8 个（`code-fingerprint` / `doc-growth-check` / `create-distribution` / `deploy-webui` / `prebuild-webui` / `sync-resources` / `export-memories` / `performance-test`） |

**那 8 个并非文档缺口**：`scripts/README.md`（**781 行**的脚本目录）**逐一记载了全部 8 个**。
两者用途不同 —— `TESTING.md` 是**测试套件目录**，`scripts/README.md` 是**脚本全集目录**。

**一处强证据**：`docs/TESTING-zh-CN.md:249` 的 **2026-10-02** 变更记录写着「已核实
`run-all-e2e.sh` 确实运行 10 个本地套件、`phase3-acceptance-test.sh` 确实定义 15 个测试函数，
两个计数均保持不变」——**上一轮已独立核过一遍，本轮的独立复核与之完全一致**。
同文件 `:253` 还记着该表曾漏列 `python-sdk-e2e-test.sh`，说明这张表的完整性历史上被盯过。

### 变更检测

本轮**未改任何代码或既有文档**。两方向均零缺陷，按既定纪律**不制造修改**。
指纹仍 `a4525469…` / 2218，**与基线一致** → **不跑完整验收、不推进基线**。
本轮无新增 findings，findings 文件无需压缩（1441 行 / 141923 字节）。
37777 保留运行。

### 下一轮

代码方向：**Backend**；文档方向：**API 文档**（doc round 229）。

