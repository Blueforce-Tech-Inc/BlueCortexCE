# Backend Review Scope Evidence 11 — 第 367 轮两条待决/更正条目的 `Status` 证据尾巴（第 367 轮迁出）

> **归档规则**：承 `-9.md` / `-10.md` 的体例（第 254 轮经用户明确决策）——
> **Scope / Evidence 与实测记录可一并迁入归档，各留一行指针**；
> **决策推理所在的 `- **Problem**` 与 `- **Status**` 一律留在工作文件**，未结案条目本身不迁出。
>
> **本批搬的是什么**：两条条目的 `Status` 里，**结论句之后**的那一大段——
> 全是**文件路径、行号、代码片段、三种独立口径的计数、渲染器对照与检查器自检记录**，
> 即**可复现证据而非决策推理**。工作文件保留结论句与指针行，**追加 `-11` 引用**（不得断链）。
>
> **渲染保真**：归档里每条用同缩进的列表项包住尾巴，**相对缩进与工作文件原样一致**。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 **2 条**条目的 `Status` 证据尾巴。
> 迁出前实测 **1416 行 / 146784 字节**；本归档实测 **29 行 / 4287 字节**。

### P2-96
（工作文件原 Status 行：'- **Status**: ⚠️ **第 340 轮的修复本身是错的，已于第 346 轮更正（纯注释零行为变更）** —— 第 340 轮把 `forty` 改成 `forty-one`，**该方向改反了**：' + 指针；下面逐字是迁出的尾巴）

    第 346 轮三种独立口径（`grep -o 'catch[[:space:]]*('` 逐文件、`grep -c`、`python re` 逐包）一致得出 **12 个 `@RestController` 文件共 40 个 catch 子句**，且 `catch(Exception)` 与 `catch(any)` 完全相等（无其他类型）。`forty` 本来就是对的，**改后成了错的**。同段另有一处从未被验过的失实：**helper 归属写反了**——文档称「two in ObservationsController, one in FeedbackController」，实测 `ObservationsController` **1** 处、`FeedbackController` **2** 处（总数 3 正确、分项相反），与第 340 轮 finding 自身批评的「42−1=41 错减法」同一形状。第 346 轮两处一并更正并加注「每个 catch 子句都属该类型」以便复核；`mvn -o clean compile` EXIT=0，可执行行 `git diff -U0` 为 **0**。**双向注入验证**：回退到修复前原文，检查器报 4 FAIL；仅对调分项也报 FAIL——检查器 `/tmp/check-demo-claims.py` 在此过程中自身错了三次（单位词正则不匹配 `blocks`、剥离 `{@code}` 后抓不到类名、`\bforty\b` 把 `forty-one` 误判为 `forty`），均由阳性对照当场拦下后重写。原条目全文见 [`…-45.md`](../archive/2026-10-07_backend-review-evidence-45.md)。

### P2-106
（工作文件原 Status 行：'- **Status**: ⏸ **记录不修**（批量，逐文件核对成本高于本轮；本条按 Archived History 段的「标题紧跟 Status」体例排版以省 1 行） ' + 指针；下面逐字是迁出的尾巴）

    —— 分布：`evolver-memory/04` 35 行、`/05` 30 行、`archive/2026-09-30_backend-review-findings-history.md` **26 行（归档，按规则不可改）**、`evolver-memory-analysis/03-signals` 4 行、其余 5 行各 1（`proxy/CLAUDE-CODE-INTEGRATION-zh-CN.md:106` 的 `Edit|Write|Read\|Bash` **只转义了 3 个管道里的 1 个**（**第 345 轮已修**：三个管道全部转义）、`phase-3-design/15.md:101` 末尾多一个空单元格（**该行第 345 轮已修**））。**已修的 6 行**：`patrol-rotation.md` 4 行 + 上列 proxy zh 与 phase-3-design 各 1 行（`:243/246/248/251`），修后该文件直方图归一到 `{4: 259, 3: 119}`。**关键仪器结论**：受控样本证实 **pandoc 的 GFM 解析器不按代码 span 内的 `|` 切单元格**（386 行全报 3 单元格），而 GitHub 会切——故此类缺陷**必须数未转义管道，渲染器复核无效**，这正是 P2-99 当年改用直方图的原因。检查器 `/tmp/find-pipe-mismatch.py` 带自检（good=0 / code-span=1 / extra-col=1 / fenced=0）；其**首版公式 `pipes-1` 把无收尾竖线的行少算一个单元格**，被自检里那条无收尾行当场拦下。
