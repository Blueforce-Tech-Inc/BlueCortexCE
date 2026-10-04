# 健康检查巡检报告历史（第十一批）

> **Moved by**: 定时项目维护任务，第 260 轮（2026-10-04）。
> **Reason**: 以下为第 1~254 轮的巡检报告全文，源文件保留第 255~260 轮。
> 沿用既有规则：**按轮号匹配** `## 第 N 轮 — ` 提取（不按物理位置——本文件
> 曾在第 157 轮把一轮误插入另一轮正文），并**逐块断言自报轮号与标题一致**。
> 归档文件创建后不再修改。

## 第 251 轮 — 2026-10-04 09:43 — Python SDK + 设计文档

**方向**：代码 Python SDK · 文档 设计文档（doc round 152）

### 健康预检

后端 `37777` UP（db / diskSpace / messageQueue / ping 四项全 UP，pending 0）。
`37778`–`37781`、`37790` 全空闲，**未启动任何进程**（本轮无需活体改后端）。

### 代码方向（Python SDK）：修复一个我自己第 233 轮埋下的裸 TypeError

`client.py` 的 `build_icl_prompt(max_chars=None)` 抛
`TypeError: '>' not supported between instances of 'NoneType' and 'int'`——
**裸 TypeError 逃出 SDK**，而非 SDK 自己的 `ValidationError`。

根因是**第 233 轮**把守卫从 `if max_chars:` 改成 `if max_chars > 0:`，
使 `max_chars` 成为**该方法唯一不对 None 安全的参数**：`project` 与 `user_id`
都用真值判断、天然把 None 当缺失，唯独它被改成数值比较。
**Java SDK 本就是现成参照**：`ICLPromptRequest.java:83` 正是
`if (maxChars != null && maxChars > 0)`，字段注释写着「null by default — let the backend decide」。

修复三处：判定改为 `if max_chars is not None and max_chars > 0:`、
注解放宽为 `Optional[int]` 默认 0、docstring 补上缘由。

**双向注入为真**：修复后 **436 passed**；回退到 `if max_chars > 0:` 得
**恰好 1 条失败**（新增的 `test_build_icl_prompt_drops_none_max_chars`）、其余 435 全过；
恢复后 436 全过。对照用例（`max_chars=1` 必须仍上 wire、`0` 与负数必须省略）
在三种状态下**均不失败**。

**探针出错并先识别再采信**：扫 `__all__` 时以为 `ModesResponse` 未定义
（dto.py 只列 18 个、`__all__` 列 19 个），复查发现**是我的正则只扫了 dto.py**——
它定义在 `client.py` 且已正确导出。`__all__` **48 条全部存在**。

边界扫描其余结果均正常：`list_observations` / `search` 的 limit、offset 各种取值 ok；
`retrieve_experiences(count=None/0/-1)` 均返 4 条；
`get_extraction_history(limit=-1)` 正确抛 `ValidationError`。

### 文档方向（设计文档）：一条从未存在的清理 API，被当作正式步骤写进了设计

`phase-3-design/25.md:699` 让读者以为存在
`DELETE /api/memory/observations?project_path=…` 这个**按项目批量删除**的端点。

**活体证据**：该请求返 **HTTP 404**；
`/v3/api-docs` 里 `/api/memory/observations` 前缀下**只有 `/api/memory/observations/{id}`**
一条路径，**根本没有任何批量删除端点**。

已改为脚本真正使用的两步法，并**改完即实跑验证**：
在一次性项目 `/tmp/r251-docverify-*` 投一条观测、按文档片段清理，**观测数 1 → 0**。

**本轮其余部分核实为真**：7 处 `Xxx.java:行号` 引用逐条精确命中
（`17.md:23/36`、`18.md:19`、`21.md:120`、`24.6.md:232`、`7.md:15/23`）——
这一方向正是第 246 轮抓出「行号随被引源码同批改动而失效」之处，故仍逐条核对。
全大写标识符回扫后端源码与 `application.yml`，未命中项经查证均为英文强调词，非标识符。

### 清扫出的同族发现 P2-46（记录不修）——它削弱的是我自己每轮用的门控

同一幻影端点还写在**验收脚本** `scripts/phase3-acceptance-test.sh:48`，
但真正的问题是 **`cleanup()` 定义了却从未被调用**（全文件只出现定义处一行，
`main` 没调、无 trap，`bash -n` 通过），且 or-true 兜底把 404 吞掉，
**失败永远不会让脚本失败**。

后果可量化：

- **Test 6** 注释写「should return not_found for new project」，但它两个分支都 pass。
  清理从不生效，**第一个分支自首次成功抽取后就是死代码**。
  **本轮验收输出直接印证**：跑出来的正是 `PASS Test 6: GET latest returns status field`（兜底分支）。
- **Test 14** 名为「Re-extraction **removes** invalidated preference」，
  真正断言却只有 `pref_count >= 1`（脚本第 515 行），
  而本轮输出是 **1173** preferences，末尾 `Bonus — Xiaomi correctly removed` 分支**未触发**。
  **累积已把这条测试退化成恒真式**。

**因此第 249–251 轮的「25/0/0」须带限定**：25 条确实全通过，
但其中两条的性质如上。这**不使任何已记录的修复失效**（被修代码路径本就在别处被独立验证），
已把该限定写进基线区块本身。

**不修的理由**：属脚本方向，不在本轮（Python SDK）的代码轮换内；
且接上清理会**改变门控自身行为**（Test 6 切回 `not_found` 分支、1173 条累积数据被删），
须在自己的轮次单独做 A/B。

### 新立 P2-47（记录不修）

第 150 轮预告的 `API.md` 问题复核坐实：`SessionController.java:126-127` 读出
`is_worktree` / `parent_project` 后，**此后只出现在 142-144 的 `log.info`**；
`initializeSession(contentSessionId, projectPath, null)` 第三参传的是 `null`，**不落库**。
本该让它生效的 `WorktreeDetector` 在 `backend/src/` 内**除自身文件外零引用**。
但 `API.md` 双语都用正式字段表条目记载并写进示例 body。
按「按断言清扫」，同一断言还在 `SessionController.java:94-95` 的 `@Operation` 示例里复述了一遍，
**须与代码同批处理、不能只改文档半边**，留待 API 文档方向（第 154 轮）。

### 变更检测与验收

改了 `.py` → 指纹 `7bf3693c…` → **`0719436e…`**（882 个代码文件），按门控**跑完整验收**：

| 门控 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 passed / 0 failed / 1 skipped**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 passed / 0 failed / 0 skipped**（带 P2-46 限定） |
| Python SDK `pytest tests/ -q` | **436 passed** |

**基线推进至** `0d4f2d3` / `0719436e162d6abd381923c5f1d8971904d048061dfeb7e73ccceb1846d79f0a`。
（`accepted_commit` 是**跑验收时工作区的 HEAD**，即上一轮已提交 commit；
本轮未提交的改动由同一区块的指纹刻画。）

### 探针数据清理

一次性验证项目 `/tmp/r251-docverify-45453` 的观测已由文档片段清空（实测 1 → 0），
会话 `r251-docverify` 仍留在 `mem_sessions`，**下轮清理**。
临时备份 `/tmp/r251-client.bak` 待清理。

### 轮换推进

代码审查：Python SDK 完成，下一方向 Demo。
文档审查：设计文档完成（一百五十二轮），下一方向 架构文档（一百五十三轮）。

---

## 第 252 轮 — 2026-10-04 10:33 — Demo + 架构文档

**方向**：代码 Demo · 文档 架构文档（doc round 153）

### 健康预检

后端 `37777` UP（db / diskSpace / messageQueue / ping 四项全 UP）。
`37778`–`37781`、`37790` 开轮前全空闲。

### 代码方向（Demo）：不并排读，四个都起起来对拍

同时启动四个 demo（Java 37778、Go 37779、Python 37780、JS 37781），全部打**未改动的 37777**，
对同一参数扫同一组取值、并排记录状态码。

`limit` / `offset` / `count` **四家每个取值完全一致**；两个参数不一致：

① **Python `/extraction/history` 解析了 `limit` 却从不校验范围。**
它是该文件里唯一没有校验的数值处理器，而**该文件自己的测试套件**已为 `/search`
与 `/observations` 断言 `limit=101` → 400——规则本就在，只是漏了这一处。
活体：`101` 在 Python 返 **200**，另三家均返 **400**，且**错误文案逐字相同**
（`limit must be between 0 and 100`）。
已补校验 + **3 条新测试**（436 → **439**）。
**双向注入**：回退后**恰好 2 条失败**（两条新增范围测试），
第三条「`limit=100` 上界仍须上 wire」**两种状态都不失败**；恢复后 439 全过。

② **JS 给 `maxChars` 加了 100000 上界，而后端根本没有这个约束。**
`MemoryController.java:151` 是
`request.maxChars() != null ? Math.max(100, request.maxChars()) : 4000`
——**只有下界、没有上界**，且后端回显它实际用的值。
活体：`maxChars=100001` 时 JS 是四家中唯一返 **400** 的，
**后端自己返 200**（回显 `"maxChars":100001`）。
已从 body 与 query 两处去掉该上界。

**修后复测**：`maxChars=100001` 四家全 200；`extraction/history limit=101`
四家全 400 且文案逐字一致。

**JS demo 无测试文件**（3 个测试文件、250 条，全在 `src/__tests__`），
故该处改动以活体 A/B 为依据；`npm test` **250 passed**、`npm run lint`（`tsc --noEmit`）干净。

### 文档方向（架构文档）：核实无误、未改

| 断言 | 实测 |
|------|------|
| 控制器层图 | **13 个类全部存在**，`@RequestMapping` 基路径逐条吻合，零遗漏零多余 |
| 服务层图（EN + ZH） | 各 **31** 个类 = 源码 `service/` **29** + `util` 的 `XmlParser` + `mcp` 的 `ClaudeMemMcpTools`；幻影 0、遗漏 0 |
| 事件类声明 | 与 `event/` 目录六个文件逐一对上 |
| `ContextController → 7 endpoints incl. /semantic` | 活体 OpenAPI **恰好 7** 条路径 |
| LLM provider 配置表三项 | 逐一核到 `application.yml:152`、`SpringAiConfig.java:82-83` |
| `backend/README.md` 迁移树 | **16 个文件名与磁盘逐字一致**；正确列 V1–V8 与 V11–V18、**正确地没有 V9/V10**（这两个版本号确实不存在）；顺序为版本序 |
| `V1__init_schema.sql # Base schema (5 tables)` | `grep -c CREATE TABLE` = **5** |

**探针三次自身出错，全部先识别再采信**：
① `grep -P` —— macOS 的 grep 不支持 `-P`，整轮输出作废，改 Python 重做；
② 服务层图用 `.*?└` 终止，撞上 AgentService 子树的 `└──` 提前截断，
**只截到 239 字符、报「服务类 2 个」而图里列了 31 个**，改锚 `└─{4,}` 后正常；
③ 比对迁移顺序用字典序 `sorted()`，把 `V11` 排到 `V1__` 之前而误报「顺序不一致」——
**是我的排序错，文档没错**。②与第 200 轮「正则把注释行当活端点」同属一类，
**已记录不等于不会再犯**。

### 新立 P2-48（记录不修）

验证 `backend/README.md` 的 walkthrough 时调了 `POST /api/ingest/session-start` → **404**，
回头才查出**幻影只存在于 gitignored 的 `CLAUDE.md`**：`backend/README.md` 本身**完全正确**
（只列 4 个真实 ingest 端点，walkthrough 用的两条都真实存在）。
把 `CLAUDE.md` 端点表逐条对拍活体：**25 条匹配 16 条，9 条幻影**，
逐条实测 404（`ingest/session-start`、`memory/save`、`context/observations`、
`quality-stats`→真实为 `quality-distribution`、`modes/active`×2、`sessions`×2、
`sessions/import`→真实为 `/api/import/sessions`）。
不修：`CLAUDE.md` 是 gitignored 本地文件，改动进不了版本控制，
「是否取消其 gitignore」仍是待用户决策。**`AGENTS.md` 已跟踪且干净**（0 幻影）。
另记：`CLAUDE.md` 写「17 controllers」，**实测 13**；「28+ services」实测 29，属「+」合法范围。

### 变更检测与验收

改了 `.py` 与 `.ts` → 指纹 `0719436e…` → **`c9900601…`**，按门控**跑完整验收**：

| 门控 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 passed / 0 failed / 1 skipped**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 passed / 0 failed / 0 skipped**（带 P2-46 限定） |
| Python `pytest tests/ -q` | **439 passed** |
| JS `npm test` / `npm run lint` | **250 passed** / 干净 |

**基线推进至** `5af3e8e` / `c99006018336e0dbc53f015326a15f038800fab05249424d4b027aeb2105d166`。

### 清理

四个 demo（37778–37781）**本轮启动、已全部停止**；`37777` 非本轮启动，**保持运行**。
`r252-%` 探针数据已从 `mem_sessions` / `mem_observations` / `mem_summaries` 删除（0 残留）。
临时文件与日志已清理。

### 轮换推进

代码审查：Demo 完成，下一方向 Backend。
文档审查：架构文档完成（一百五十三轮），下一方向 运维/用户指南（一百五十四轮）。

### 压缩（第十五次）

findings 写入 P2-48 后达 **1034 行**、触发 `COMPACTION_REQUIRED`（退出码 2）。

**证据层已彻底耗尽**：可迁出的 `Evidence` / `Reproduction` 段只剩 **6 行**、且全是
指向既有归档的一行指针（第 250 轮已预见到这一点）。
**「无条件已解决条目整体迁出」也不可用**：37 条条目**全部带 ⏸**，逐条核对后**没有一条**
是无条件的——第 250 轮那次之所以能用，正是因为当时还有 P2-11、P2-38 两条。

故再次启用**新先例**：`## Archived History` 小节记的是**文件自身的压缩历史**，
而各批次的完整说明**早已逐条登记在 `docs/archive/README.md`**——**两处记着同一批事件**，
与第 238 轮删掉重复的压缩日志是同一理由。该节 **17 行逐字迁入**
`2026-10-04_backend-review-compression-log.md`，源文件留 5 行指针**；
**37 条 ⏸ 条目的决策推理一行未迁**。回读校验结尾逐字相同。
另做三处行内压缩（P2-46 的 Problem/Evidence/同族事实、P2-47 的 Status、
P2-45 的 ②、Open Findings 的元评论），**回落 1000 行**。

**教训**：⏸ 规则让每个文件只能靠**行内压缩**续命，而这次的量已经很薄。
下一次 findings 再越线时，**唯一还剩下的杠杆就是那三条待用户决策的开放项之一**——
尤其「⏸ 规则是否覆盖实测证据」这一条，它决定了可迁出的量是否回到 0 附近。

---

## 第 253 轮 — 2026-10-04 11:12 — Backend + 运维/用户指南

**方向**：代码 Backend · 文档 运维/用户指南（doc round 154）

### 代码方向（Backend）：读失败变成「静默丢失全部注册项目且回报成功」

`CursorService.readRegistryUnlocked` 在**注册表文件存在但无法解析**时返回**空 Map**。
看似无害，直到注意到两个调用方 `registerProject` / `unregisterProject` 都是
**读 → 改 → 写回**——**一个空读不只是答错了问题，它成了注册表的新内容**。
同类的写路径 `writeRegistryUnlocked` 却**抛 `UncheckedIOException`**：
**读写两侧不对称，且不对称的那一侧是破坏性的**。

**决定性 A/B（同一四步序列，两个方向各跑一次）**：

| 步骤 | 未修复（37777，真实注册表，已做 sha256 备份） | 修复后（37790，本轮产物 + 隔离数据目录） |
|------|--------------------------------------------|------------------------------------------|
| 注册 3 个项目 | 200 | 200，条目数 3 |
| 截断注册表至不可解析 | 2211 → 1105 字节 | 同左 |
| 再注册一次 | **HTTP 200 `{"success":true}`** | **HTTP 500** `Failed to register project: Failed to read cursor registry` |
| 磁盘上的注册表 | **只剩 1 个条目，原有 16 个项目全部丢失** | **未被写回**（仍处于损坏状态，未被覆盖） |
| 对照 `GET /api/cursor/projects` | — | 同刻仍 **200** 并返回已缓存的 3 条 |

**未修复实例的注册表已按备份逐字还原**：sha256 与原始
`0a5c8423119b347a9766c26377bba10188217a8b841ea0d886e1073551a55d4b` 一致、16 条齐全。

**修复**：`readRegistryUnlocked` 在「文件存在但无法解析」时改为抛 `UncheckedIOException`，
与写路径对称；**「文件不存在 = 空的」保持不变**（那才是真正的空）。
方法 Javadoc 写明为什么这里不能返回空：返回空会被写回。
调用方本就 `catch (Exception)` 并返回 500，无需改动。`mvn -o compile` 通过。

### 记录不修的两条

**P2-49**：`scripts/start.sh:55` 钉死 `cortex-ce-0.1.0-beta.jar`，且 `start.sh:97` 在找不到时
**直接拒绝启动**；而 `docs/TESTING.md:212` 把 `scripts/start.sh` 列为**推荐**启动方式。
共 6 处（另三个脚本 + 两份 `evo-memory-implementation*.md`）。
**与第 248 轮修的「artifactId 写错」不是同一类**：那批名字从来不可能产出，
这批**名字是对的、只把版本钉死了**。当前 jar 存在故今天不可复现，属**潜伏缺陷**。

**数据目录有两个互不相干的键**：`CursorService` 用 `@Value("${claudemem.data-dir:…}")`，
`AppSettings` 用 `CLAUDE_MEM_DATA_DIR`。设后者只会挪走 `settings.json`，
`cursor-projects.json` 仍落在 `~/.claude-mem/`——**本轮第一次起隔离实例就是把
4 个探针写进了真实注册表**（原始 16 条未丢，已清理）；正确写法是 `-Dclaudemem.data-dir=...`。

### 文档方向（运维/用户指南）：脚本报缺失 7 个，**全部是探针假象**

`ode.js` 来自 `Node.js`、`settings.js` / `launch.js` 来自 `.vscode/`、`package.js` 来自
`package.json`、`install.sh` 是 nvm 的 URL、`backup.sh` 是文档让读者自己写的示例脚本、
`test-all.sh` 确实存在于 `go-sdk/cortex-mem-go/` 且上下文正确。**逐一核对后无一为真幻影。**

**真缺陷一处**：`docs/DEVELOPMENT.md` 四处钉死 `target/cortex-ce-0.1.0-beta.jar`，
而**同一节的中文版本就是通配** `cortex-ce-*.jar`——EN 侧会在版本变更后失效。
已全部改为通配，两版现已一致。

### 探针错误与一次作废重做

**压缩切错了段**：第五批证据归档的行号是在我插入汇总表**之前**算的，
插入使其下所有条目整体偏移一位，**切出的归档里混进了 P2-47 的标题和 P2-48 的正文片段**。
这正是第 246 轮「行号引用因被引用文件同批改动而失效」的同一形态。
处置：`git checkout HEAD` 回到第 252 轮末状态 → 在**同一次操作**里插入 P2-49/P2-50
与汇总表 → **最后才在最终文本上重算行号** → 切割前逐段打印首尾并断言下一行是条目边界 →
迁出后回读逐段校验原文仍在。四段全部逐字一致。

### 变更检测与验收

改了 `.java` → 指纹 `c9900601…` → **`2b7f3f9e…`**，按门控**跑完整验收**：

| 门控 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 passed / 0 failed / 1 skipped**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 passed / 0 failed / 0 skipped**（带 P2-46 限定） |

**基线推进至** `06c50b1` / `2b7f3f9e9189a611ddba4811a7bfe38da6383f64cb8e43f281768702f5ab2abf`。

### 压缩（第十六次）

findings 写入两条新条目后达 **1046 行**触发 `COMPACTION_REQUIRED`。
P2-41 / P2-46 / P2-48 / P2-50 的 **Evidence** 段共 27 行逐字迁入
`2026-10-04_backend-review-evidence-5.md`（**Scope / Problem / Status 一行未动**），
另做六处行内压缩（P2-49、P2-50、P2-27、P2-21 及 Open Findings 的元评论），
**回落 999 行**。**上一轮报告里那句「唯一剩下的杠杆就是待决项之一」已应验**：
证据层再次耗尽后，只能靠行内压缩续命。

### 清理

37790 **本轮启动、已停止**；`37777` 非本轮启动，保持运行。
真实注册表已还原（sha256 一致）；`/tmp/r253-data` 与临时文件已清理。

### 轮换推进

代码审查：Backend 完成，下一方向 Java SDK。
文档审查：运维/用户指南完成（一百五十四轮），下一方向 API 文档（一百五十五轮）。

---

## 第 254 轮 — 2026-10-04 15:46 — Java SDK + API 文档

**方向**：代码 Java SDK · 文档 API 文档（doc round 155）

### 文档方向（API）：P2-47 到期，按断言清扫发现**比记录还多三处**

五处一并更正，全部是描述文本，**字段名/类型/状态码/行为未变**：

| 位置 | 更正前（活体核对） |
|------|--------------------|
| `ApiRequests` 的 `@Schema` × 3 | **活体 `/v3/api-docs` 就在输出** "Flag indicating worktree mode (rare, internal use)" 等 |
| `SessionController` `@Operation` 描述 | `projects` 被写成 "for worktree support"（实为多项目上下文） |
| `SessionController` 请求示例 Javadoc | 同上三处字段无任何限制说明 |
| `API.md` + `API-zh-CN.md` 字段表 | 无任何限制说明 |

**新立 P2-51（记录不修）**：`projects` 的分支判定是 `projectsParam.contains(",")`，
**单个值被静默忽略、与不传逐字等价**。文档侧已写明，实现侧不改（属语义变更）。

**证据两次返工**：首版三组对照全部返回相同内容、几乎得出「`projects` 不生效」——
**上下文缓存按 `project_path` 命中**。改用每组不同 `project_path` 强制未命中后才分出高下
（`openclaw` 单值 → "no memories yet"；`openclaw,/tmp/phase3-acceptance-test` → 25 observations / 6,109 tokens）。

### 代码方向（Java SDK）：SDK 本身无误，但测试运行在掩盖一次真实失败

SDK 空安全核实为真（`maxChars != null && maxChars > 0`、primitive `limit` 不可能为 null）——
**第 251 轮 Python 的隐患在 Java 侧不存在**。

但 `target/` 里有 **10 个源码已删的测试类**，Maven 照跑目录内已有 class。
其中 `NestingProbeTest` 是第 249 轮 P2-44 的探针，**断言外层作用域应当存活**——
那正是 P2-44 记录的**未修缺陷**，故**必然失败**。后果：源码全绿的工作区 `mvn test` **退出非零**，
计数显示 **211** 而非 196。`mvn -o clean test` → 退出 **0**、`BUILD SUCCESS`、**143+46+7 = 196**，与 README 吻合。
记为 **P2-52**，`clean` 即解决，未改任何源码或脚本。

**更该记的是我自己**：先前那次运行把 mvn 管道给 tail 再读 `$?`，读到的是 **tail 的退出码、恒为 0**，
一次真实失败被完美掩盖。管道吞掉上游退出码是 shell 基本事实。

### 压缩杠杆：经用户决策后打开

findings 卡在 **1024 行**，不动内容的手段已用尽（实测段仅剩 4 行指针、
逐轮摘要表已按文件自身声明移除至第 219 轮、排版重排触及 0 个块、40 条全带 ⏸）。
**用户决定**：允许把 `- **Scope**` 与 `- **Evidence**` 一并迁入归档，**只保留 Problem 与 Status**。
**52 段逐字迁出、净省 69 行、1024 → 955**；规则变更已写入文件的 `## Processing Rules`。
这是本文件建立以来 ⏸ 规则**第一次由用户拍板放宽**（此前只允许迁出实测记录）。

### 变更检测与验收

指纹 `2b7f3f9e…` → **`fb56d0b8…`**，按门控**跑完整验收**：

| 门控 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 passed / 0 failed / 1 skipped**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 passed / 0 failed / 0 skipped**（带 P2-46 限定） |
| Java SDK `mvn -o clean test` | **196 passed / 0 failed**（143+46+7，与 README 吻合） |

**基线推进至** `c858f42` / `fb56d0b8f870e3340c979d83fc64f13c86c9316dfb7744fb40a49a372fea6718`。

### 清理

未启动任何新进程；`37777` 非本轮启动、保持运行；`37778`–`37781`、`37790` 全空闲。
`r254-%` 探针数据已删除。`/tmp/r254-javatest.log` 已清理。

### 轮换推进

代码审查：Java SDK 完成，下一方向 Go SDK。
文档审查：API 文档完成（一百五十五轮），下一方向 SDK README（一百五十六轮）。

---
