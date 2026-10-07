# Backend Review Evidence 57 — 第 359 轮已结案条目的整条正文（第 359 轮迁出）

> **归档规则**：承 `-55` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**，不再手写；
> 且因数字写在文件里会反过来改变自身长度，本批**迭代至不动点**后再写盘。

> 本批迁出 `P2-107` / `P2-108` / `P2-109` 三条，**全部是 ✅ 已修**
> （注释/文档失实陈述修正，零行为变更），对本循环**没有待办动作**，故可安全迁出。
> 选它们是因为 `backend-review-findings.md` 的**字节余量仅剩 1272 字节**
> （1385 行 / 148728 字节 vs 上限 1500 / 150000），不足以容纳本轮两条新条目；
> **未结案条目一律不动**。
>
> 迁出前实测 **1385 行 / 148728 字节**；本归档实测 **40 行 / 10395 字节**。
### P2-107: `DemoParams` 的类注释称 `MemoryController` 是**唯一**在 `/demo` 前缀外的控制器 —— 活体实测有**两个**，且它自己引用的 README 就列出了第二个
- **Scope / Evidence**: `examples/cortex-mem-demo/src/main/java/com/example/cortexmem/DemoParams.java`（原 53 行）。活体实测于本轮自启的 demo（37778）：`GET /chat` 返回 500 而非 404（500 是**已知的 LLM 密钥失效**，非路由问题——`Error while extracting response`，见本文件 LLM 端点 401 那条），`GET /projects` 返回 **404**。
- **Problem**: 原句为「`MemoryController` is **the one** controller served outside the `/demo` prefix, so its paths read `/memory/...` — that is what the demo README documents」。12 个控制器里**4 个没有类级 `@RequestMapping`**（`ChatController`、`MemoryController`、`ProjectsController`、`ToolsController`），但其中 `ProjectsController` → `/demo/projects`、`ToolsController` → `/demo/tool` **都在 `/demo` 下**；真正在前缀外的是**两个**：`MemoryController`（`/memory/...`）与 `ChatController`（`/chat`）。而 demo README 第 54 行**自己就列着** `GET /chat?message=...&project=project-a`——注释用来佐证的文档反证了它自己。
- **为什么算失实而非「表述含糊」**: 该句在**局部范围**（上文那三个直接绑定数值的控制器）内成立，但它的**措辞是无限定词**的全局断言，且紧接着用「that is what the demo README documents」把读者导向一份直接反驳它的文档。按「失实陈述可修」单方面更正，**零行为变更**。
- **Status**: ✅ **已修（第 352 轮，零行为变更）** —— 改为「the only one of **those three** served outside the `/demo` prefix」，并**主动补上**被漏掉的事实：`/chat` 同样在前缀外，另注明 `ProjectsController` / `ToolsController` 无类级映射却仍在 `/demo` 下。`mvn -o clean test` **40/40** 全绿。**检测手段**: 不是靠读映射表，而是**活体探针**（`/chat` 非 404 + `/projects` 404 双向钉死），因为「有没有类级 `@RequestMapping`」与「路径在不在 `/demo` 下」是两件事，只有后者决定原句真假。
- **同段其余断言本轮全部活体复验通过**: 六个 Spring 绑定参数实测**恰好**接受 `0x10`（`/demo/experiences?count`、`/demo/iclprompt?maxChars`、`/memory/experiences?count`、`/memory/experiences/filtered?count`、`/memory/icl/truncated?maxChars`、`POST /demo/session/prompt?promptNumber`）而 `/demo/observations?limit=0x10` 被本类拒为 400；`1_0` 的五条 400 响应体（一条本类自定义 `{"error":"limit must be an integer"}`、四条 Spring 默认 `{"timestamp":…,"status":400,"error":"Bad Request"}`）逐字吻合。

### P2-108: `python-sdk-design.md` 的**两份目录树**都停在设计日 —— 漏 `tests/test_demo.py` 与 `cortex_mem/py.typed`，并列出一个**并不存在**的 `LICENSE`
- **Scope / Evidence**: `docs/drafts/python-sdk-design.md` §1 目录结构（21-34 行）与 §2 目录结构（100-115 行）。以磁盘为准实测（`python-sdk/cortex-mem-python/`）：`tests/` 实为 `conftest.py` / `test_client.py` / **`test_demo.py`** / `test_dto.py`；`cortex_mem/` 实含 `py.typed`（60 字节，非空）；顶层**无 `LICENSE`**。
- **Problem**: 该文件是**零覆盖**设计稿（此前从未审过），日期标注 2026-03-27、状态「待审批」。§2 树把 `LICENSE` 列为包内文件，而它只存在于仓库根；两份树都漏了后加的 `test_demo.py` 与 `py.typed`，§1 那份还漏 `conftest.py`。**「带日期的快照不算失实陈述」**——但这份文件在本仓库早已被当作**活文档**维护：§3.1 与 §3.2 各自带「实施后修正（2026-10-06）」注记，第 162 轮还据它改过 `client.py`。既有体例明确是**加注记**而非重写树。
- **Status**: ✅ **已修（第 352 轮）** —— 按本文件既有体例，在 §2 树后加「实施后修正（2026-10-07）」注记，**逐条列出实测到的三处漂移并声明以磁盘为准**，同时提示 §1 那份更简写的树有同样遗漏；**不重写树**，以免抹掉设计当时的记录。
- **该稿其余可验证断言本轮逐条复验通过（零差异）**: ①「26 个公开方法」——AST 枚举实得 **26**（含 `close()`），与既有注记一致；清单本身列 **25** 个 API 方法（我独立重数：`2+3+5+5+1+3+1+4+1=25`）+ 2 个 dunder，缺 `get_observation`，**与既有注记吻合**。②§4 列的 13 个 DTO 在 `dto.py` 中**全部存在**。③`dependencies = ["requests>=2.28"]` 单依赖、`version = "1.0.0"`。④§3.1 五个默认值（`timeout=30.0` / `max_retries=3` / `retry_backoff=0.5` / `api_key=None` / `session=None`）逐项相符。⑤`scripts/python-sdk-e2e-test.sh` 确实存在。⑥§3.2 的 **24 条端点路径与实现逐字一致**（`close()` 无路径故不入比对）。
- **端点比对器返工两次才可信**: 首版正则要求 docstring 以 `/` 开头，而实现写的是 `"""POST /api/session/start"""`——**比对数 0、差异 0**，属「只可能返回零的比较」；二版字符类含 `.`，把 18 条路径的句末句号一起吃进来，**造出 18 处假阳性**；三版排除 `.` 后零差异，并注入两处缺陷（`/api/searchX`、`/api/versionz`）各被抓到一次才算通过。
- **§6 异常层次只列 6 类而实现有 12 类——不记为缺陷**: 该节无「完整/全部」措辞，是**节选**而非清单，按「遗漏 ≠ 失实」不构成 finding，也不修。

### P2-109: `TimelineService` 的地板注释断言了一个**真实执行顺序到不了**的异常——`subList` 永不先抛，`PageRequest` 先抛
- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/service/TimelineService.java`（原 90~100 行）。受控实验 `subList(1,0)` → `IllegalArgumentException: fromIndex(1) > toIndex(0)`（与注释逐字吻合）；`PageRequest.of(0,-3)` → `IllegalArgumentException: Page size must not be less than one!`；**按方法真实顺序复原**（`windowSize=(before+after)*2+1=-3` → `Math.min(-3,500)=-3` → `PageRequest.of(0,-3)`）→ **先抛 `Page size must not be less than one!`**，`extractWindow` 根本没被调用。
- **Problem**: 注释原写「A negative depth inverts that range: with anchorIndex = 0, before = -1 and after = -1 the indices become fromIndex(1) > toIndex(0) and the JDK throws IllegalArgumentException: fromIndex(1) > toIndex(0)」。**这个名字与消息在真实代码里不可达**：能反转 `subList` 区间的 (before, after) 必然满足 before+after<0，于是 `windowSize<1`、`maxObs<1`，`PageRequest.of(0, maxObs)` 在**更早的一行**就抛了。结论（负数深度是未处理的 500）没错，**依据的机制是错的**。
- **同一段的第二处失实**: 「before=0/after=0 yields subList(0, 1), i.e. the anchor observation alone, **which is exactly what the endpoints return today for depth 0**」。实测**只在锚点恰为该项目最新观测时成立**——因为抓取宽度是 `(0+0)*2+1=1` 行，锚点不在候选里时 `findAnchorIndex` 返回 -1，走**提前返回空列表**那条路。同一项目（1362 条观测）取四个位置的锚点活体实测 `depth_before=0&depth_after=0`：**最新→1 条、第 2 新→0、第 6 新→0、第 11 新→0**。补测 `1/1` 同样如此（2 新→3 条，6 新与 11 新→0）。
- **为什么仍按「地板取 0 而非 1」修**: 该**结论依然正确**——地板取 1 会把上表三个 0 变成 3 条窗口，那才是行为变更。错的只是支撑它的两条事实，故只改注释、**零行为变更**。注释已改为：写明 `windowSize` 同时决定抓取宽度、真实首抛来自 `PageRequest`、并说明 `subList` 的异常真实存在但**在本方法里不可达**；再按四个锚点位置的实测值说明 depth 0 的真实返回。
- **Status**: ✅ **已修（第 353 轮，零行为变更）** —— 改的是注释，`Math.max(0, …)` 一行未动。**新鲜度闸门**：改的是后端源码，豁免路径**不适用**，已 `mvn -o clean package -DskipTests`（BUILD SUCCESS）并重启 37777 后跑完整验收。
- **本轮一并复验为零缺陷的相邻断言**: `ContextController` 350~360 行四条全部成立——`maxObservations` 确实进原生 `LIMIT :limit`（`ObservationRepository` 多处）、`maxSummaries` 确实走 `ContextService:392` 的 `stream().limit(...)`、端点是 `@GetMapping(produces = TEXT_PLAIN_VALUE)` 返回 `String` 故 catch-all 以 **200** 返回纯文本（结构上成立）、`0` 的行为实测正是渲染为 “**no memories yet**”。它引用的「`Stream.limit(-1)` 的消息字面就是 `-1`」也**实测为真**（`IllegalArgumentException: -1`）。
- **一句「遗漏」不记为缺陷**: 注释说「Both timeline entry points (GET /api/context/timeline and GET /api/timeline) funnel through this method」。两者确实都经 `getTimelineByAnchor`（`TimelineService:60`）汇聚到 `getTimelineMap`，**该句字面为真**；但 `ClaudeMemMcpTools:146` 是**第三个**调用方（MCP `timeline` 工具直接调用）。按「遗漏 ≠ 失实」不记 finding——补记也只是正文加一句。
