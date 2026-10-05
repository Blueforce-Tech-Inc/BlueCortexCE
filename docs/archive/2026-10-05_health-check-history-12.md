# 健康检查巡检历史（第 255–260 轮）

> 源文件：`docs/drafts/health-check-task.md`　归档日期：2026-10-05
> 承第 250 轮确立的规则：**按轮号**匹配 `## 第 N 轮 — ` 提取（不按物理位置，
> 因历史上曾发生误插入），迁出前**逐块断言自报轮号与标题一致**（本次 6/6 通过），
> 并断言「最后一个迁出块的结束位置 == 第一个保留块的起始位置」。
> 前序归档：`2026-10-04_health-check-history-11.md`（第 1~254 轮）。

## 第 255 轮 — 2026-10-04 16:08 — Go SDK + SDK README

**方向**：代码 Go SDK · 文档 SDK README（doc round 156）

### 代码方向（Go SDK）：钳「下限」却赋「上限」

`NewClient` 配置归一化：`if cfg.Timeout < 100*time.Millisecond { cfg.Timeout = 30 * time.Second }`。
**触发条件是「太小」、赋的却是「默认最大值」** → `WithTimeout(50ms)` 实得 **30 秒**，
比要求长 **600 倍且方向相反**。`ConnectTimeout` 同形（10s）。

**两处证据把意图钉死为「地板」**：① **同一段代码的下一行** `RetryBackoff`
用**同一触发常量**、赋值 `100 * time.Millisecond`；② **Python SDK** 是
`max(0.1, timeout)`，注释写「Minimum 100ms to prevent immediate timeout」。
**四家对照**：Java 不钳制、Python 0.1s 地板、**Go 30s 天花板**。

**修复前后实测**：

| 请求值 | 修复前 | 修复后 |
|--------|--------|--------|
| 0 / 10ms / 50ms / 99ms | **30s** | **100ms** |
| 100ms | 100ms | 100ms |
| 250ms / 5s | 原样 | 原样 |
| `RetryBackoff(10ms)`（对照） | 100ms | 100ms |

**它为什么能存活**：归一化结果存在**未导出**的 `httpClient` 上，
而唯一的测试文件是**外部包** `cortexmem_test`，**根本观察不到该路径**——
**没有任何测试断言过归一化**。故 3 条新测试放入**新的内部测试文件**
`config_internal_test.go`，该事实已写进文件头注释。

**双向注入**：回退到修复前状态后**恰好 1 条失败**，
两条对照组（`RetryBackoff` 地板、默认值 30s/10s/500ms 不变）在两种状态下**均不失败**。
根模块 **299 → 302**，覆盖率 **95.2% → 95.7%**，`test-all.sh` 九模块全绿。

### 文档方向（SDK README）：三处数字 + 一个日期

实测 **302 + 13 + 8 + 12 + 27 = 362**，两份 README 仍写 359。
**按断言清扫**全部落实：亮点行、`Testing` 一节的「只覆盖根模块」、
分解行（含 **实测日期 2026-10-03 → 2026-10-04**）。双语同步。

**探针先出错**：五模块中四个报 0——它们是**独立 Go module**，
`go test ./x/...` 不跨 module 边界。README 本身已解释此点（第 250 轮也差点栽在这里），
改为逐目录 `cd` 后计数才正确。

### 变更检测与验收

指纹 `fb56d0b8…` → **`1a641fe9…`**，按门控**跑完整验收**：

| 门控 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 passed / 0 failed / 1 skipped**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 passed / 0 failed / 0 skipped**（带 P2-46 限定） |
| Go SDK `test-all.sh` | **362 passed / 0 failed**（九模块） |

**基线推进至** `4303504` / `1a641fe99de42c163ca6d02b404157b0dc3e6bc6b1a05fe7fdabffd596cba827`。

### 清理

未启动任何新进程；`37777` 非本轮启动、保持运行。
临时备份 `/tmp/r255-impl.bak` 与探针文件已删除。

### 轮换推进

代码审查：Go SDK 完成，下一方向 Python SDK。
文档审查：SDK README 完成（一百五十六轮），下一方向 设计文档（一百五十七轮）。

---

## 第 256 轮 — 2026-10-04 16:30 — Python SDK + 设计文档

**方向**：代码 Python SDK · 文档 设计文档（doc round 157）

### 代码方向（Python SDK）：第 251 轮同族的两处漏网

把 client 里每个数值参数扫了一遍 `None` 行为：

| 参数 | 修复前 |
|------|--------|
| `search(limit/offset=None)` | OK |
| `list_observations(limit/offset=None)` | OK |
| `retrieve_experiences(count=None)` | **TypeError: '>' …** |
| `get_extraction_history(limit=None)` | **TypeError: '<' …** |

前四个安全是因为用**真值判断**（`if limit:`）；后两个用了**数值比较**。
**四家对照**：Java/Go 是 primitive（None 不可能）、JS 用 `!== undefined`、**只有 Python 会炸**。

**过程中被既有测试当场抓住的一次自我犯错（值得单列）**：
第一版改成 `if count:`（照搬同文件其它处的真值写法），
`test_retrieve_experiences_drops_negative_count` **立刻失败**——
**负数在 Python 里是真值**，`-1` 会被发上 wire。
**而那条测试的 docstring 早就写着**「A truthiness test is not enough:
every non-zero int is truthy in Python.」**警告一直在仓库里，我读了方法却没读它的注释。**

最终形式与第 251 轮一致：`is not None and > 0`，两处各补「真值判断在这里是错的」注释。
注解放宽为 `Optional[int]`。**+2 条测试**（439 → **441**）。
**双向注入**：回退后**恰好 2 条失败**，两条既有对照**两种状态下均不失败**。
活体复核：`count=None`/`limit=None` OK、`count=-1` 正确丢弃、**`limit=-1` 正确抛 ValidationError**。

### 文档方向（设计文档）：一个路径变量两种写法，错的那 14 处

`22.md` 写 `PATCH /api/session/{id}/user`，活体映射是 **`{sessionId}`**；
**同一文件第 118、121 行又写对了**——文档自相矛盾。
**按断言清扫**在**十个文件、十四处**全部更正；
**刻意未动** `/api/memory/observations/{id}`（那个变量名确实是 `id`）。
改后 `{sessionId}` **19 处**、`{id}` **0 处**，端点对拍**零幻影**。
7 处 `Xxx.java:行号` 引用逐条精确命中。

### 变更检测与验收

指纹 `1a641fe9…` → **`674bde42…`**，按门控**跑完整验收**：

| 门控 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 passed / 0 failed / 1 skipped**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 passed / 0 failed / 0 skipped**（带 P2-46 限定） |
| Python `pytest tests/ -q` | **441 passed** |

**基线推进至** `d556340` / `674bde42800db6c501375b5b49537b28bc603aa891c3e26fd6fe01054ea3eac4`。

### 压缩（第十九次）

第 254 轮规则要求保留 Problem 与 Status；但**无 ⏸、无待决问题的条目没有推理可供读者判断**，
故把 **P2-54 / P2-50 / P2-52 / P2-53** 四条**无条件已解决**条目的 Status 段整体迁出
（**Problem 段全部留在工作文件**），findings **1029 → 989**。

**探针两次自身出错**：①归档脚本缺 `import re`；②逐字断言比较了未 rstrip 的文本而归档端已 rstrip，
两次都在**写入工作文件之前**失败，故未造成损坏，第二次修正后校验通过。

### 清理

未启动任何新进程；`37777` 非本轮启动、保持运行；`37778`–`37781`、`37790` 全空闲。

### 轮换推进

代码审查：Python SDK 完成，下一方向 JS/TS SDK。
文档审查：设计文档完成（一百五十七轮），下一方向 架构文档（一百五十八轮）。

## 第 257 轮 — 2026-10-04 17:19 — JS/TS SDK + 架构文档

**方向**：代码 JS/TS SDK · 文档 架构文档（doc round 158）

### 健康预检

| 项 | 状态 |
|----|------|
| 后端 37777 | UP |
| Demo 37778–37781 | 全空闲 |
| 隔离实例 37790 | 空闲（本轮未启动，也未重启非本轮启动的 37777） |
| `npm test` | **250 passed (3 files)** |
| `npm run lint`（`tsc --noEmit`） | 干净 |

### 代码方向（JS/TS SDK）：一处重复 Javadoc

**核实为真的部分**（逐项，不按文件）：

| 断言 | 结果 |
|------|------|
| `isRetryable` 状态码集合 | 429/502/503/504 + 传输层错误，与 Go `IsRetryable` **完全一致** |
| 数值参数 None 隐患 | 8 个参数**全部** `!== undefined` 守卫（234/263/290/291/478/483/886/887 行）——**四家里最干净** |
| 10MB 响应上限 | 先读 Content-Length 前置检查，读取后按 UTF-8 字节兜底，ASCII 有免费早退 |
| 响应解析层 | `parseObservationsResponse` 刻意不给缺失字段补 0 |
| query 参数名 | **8 个调用端点与活体 OpenAPI 逐条对拍，零不匹配** |

**探针自身出错一次**：首版对拍把路径变量（`p`/`q`/`t`…）也算进「后端独有」，
报出一片假差异；限定 `in == 'query'` 后重做，才得到上面这张全 OK 的表。

**真缺陷（已修）**：`client.ts:208-214` 上 `retrieveExperiences` 的 Javadoc **重复了两遍**。
它会进 `.d.ts`、进每个编辑器的悬浮提示。删除其一后 lint 干净、**250/250** 全绿。

### 文档方向（架构文档）：三处幻影断言，全在受版本控制的 `AGENTS.md`

第 153 轮已把控制器图 / 服务图 / 事件类 / 迁移树 / LLM 配置表核实为真，
故本轮换角度，扫**直接约束后续所有改动**的 WebUI 契约表。

| # | 断言 | 活体 | 处置 |
|---|------|------|------|
| 1 | `/api/context/generate` 返回 `updateFiles` | **只返回 `{context}`**（`GenerateContextResponse` 仅一个字段） | 改为 `/api/context/inject` |
| 2 | `/api/settings` 有 `observation_types/concepts` | **无此字段**（20 个 `CLAUDE_MEM_*` + `modeName`/`modeDescription`） | 换为实测的 `modeName`/`modeDescription` |
| 3 | 自查 grep 扫 `webui/src/` 找 `updateFiles` | **零命中**（消费方在 `proxy/proxy.js:293`） | 改为同时扫 `proxy/`，并注明原因 |

**危害是具体的**：一个要改名的 agent 查表，看到表里写的是**另一个端点**，
于是判定「可以改」——**表保护虚构、暴露真身**。真正产出 `updateFiles` 的是
`GET /api/context/inject`（`ContextController.java:154`），WebUI 的 6 处调用也全打它。

**同表另外两行核实为真、予以保留**：`hasMore` 在三个列表端点活体均返回且 WebUI 确有引用；
`/api/session/start` 的 `updateFiles` 活体确在；`/api/modes` 那行的理由成立——
两处调用均经**本地单例** `ModeManager.getInstance().getActiveMode()`，不从 API 读。

**架构文档其余部分核实为真**：`§Current Limits` 的具体断言逐条落到源码
（10/60s、唯一调用点 `IngestionController.java:132`、key `tool-use:{contentSessionId}`）；
MCP `save_memory` 经 `ClaudeMemMcpTools.java:238` 直连 service，**不走 HTTP**，故未受 404 影响。

**记录不修的能力缺口**：四家 SDK **无一暴露 `platformSource`**，
而后端 `/api/observations` 接受它、WebUI 也确实向 `/api/context/inject` 传；
且**没有任何一家 SDK 调用 `/api/context/inject`**。四家一致缺失 = 产品能力缺口。

### 变更检测与验收

```
指纹 674bde42800db6c501375b5b49537b28bc603aa891c3e26fd6fe01054ea3eac4
  →  0b8d9c7174beb19d21870f65985877fad6f940b0e21245255b7fe95d1b7d37a1
```
改了 `.ts` → 按门控**跑完整验收**（跑在不含本轮修复的 37777 上）：

| 套件 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 / 0 / 1**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 / 0 / 0**（带 P2-46 限定） |
| JS SDK `npm test` | **250 / 250** |

基线已更新：`accepted_commit: 4acc0f3`、指纹 `0b8d9c71…`。
验收 Test 15 再次确认后端对负 `limit` 是**钳制**而非拒绝——
这也正是 JS SDK 在客户端先抛 `ValidationError` 的原因（与第 252 轮 Python 侧同一事实）。

## 第 258 轮 — 2026-10-04 17:59 — Demo + 运维/用户指南

**方向**：代码 Demo · 文档 运维/用户指南（doc round 159）

### 健康预检

| 项 | 状态 |
|----|------|
| 后端 37777 | UP（全程未重启） |
| Demo 37778–37781、隔离实例 37790 | 轮初全空闲 |

### 代码方向（Demo）：两处记录、一处零行为变化的描述更正

**P2-55（新）——四个 demo 写下了同一份整数文法，却 2:2 分裂。**
逐条**直接实测各自的判定逻辑**（不启服务）：

| 输入 | Java `DemoParams` | Python `_INT_RE` | Go `strconv.Atoi` | JS `/^[+-]?\d+$/` |
|------|-------------------|------------------|-----------|-------------------|
| `１２３`（全角） | **123** | **123** | REJECT | REJECT |
| `٥`（天城文） | **5** | — | — | — |
| `1_0` / `0x10` / `10abc` | REJECT | REJECT | REJECT | REJECT |

分家原因是**运行时而非决定**：`Character::isDigit` 与 Python 的 `\d` 是 Unicode 宽的，
`strconv.Atoi` 与 **JS 的 `\d`（规范即 `[0-9]`）**是纯 ASCII。
**决定性的一测在活体后端**：`limit=５` → 200/5 条、`limit=٠١٢` → 200/12 条、
`limit=1_0` → 400、`limit=0x10` → 200/16 条。
**故按两个 demo 自己写下的判准（「与后端一致」），偏离的是 Go 和 JS**——
收紧 Java/Python 反而会让它们**远离**所要对齐的后端。**记录不修**（修哪边都是收窄对外契约，
方向需一次产品决定：整数文法是否仅限 ASCII）。

**P2-56（新）——`ExperiencesController` 是四个控制器里唯一不调 `boundedInt` 的。**
它把 `count` / `maxChars` 直接绑成 `Integer` 再手写范围检查，
**而 `DemoParams` 的 Javadoc 原本宣称「controllers 取原始 String，这条规则是唯一能决定取值含义的东西」——
这句话对它是假的**。同一进程实测（37778，本轮启动；探针项目 `/tmp/r258-demo-probe`）：

| 输入 | `/demo/observations?limit=`（走 `boundedInt`） | `/demo/experiences?count=`（直接绑定） |
|------|--------------------------------------------|--------------------------------------|
| `1_0` | 400 `{"error":"limit must be an integer"}` | 400 `{"timestamp":…,"error":"Bad Request","path":…}` |
| `0x10` | 400 | **200** |
| `１０` | 200 | 200 |

**状态码相同、body 形状完全不同**——因为 `InvalidParamAdvice` 只匹配自己的异常类型，
**无人处理 Spring 的 `MethodArgumentTypeMismatchException`**（`DemoErrors` 只管后端异常）。
**记录不修**（两条修法都动对外契约，且方向取决于 P2-55）。
**已修的是其中零行为变化的那一半**：Javadoc 改为**如实描述现状**。

**本轮启动的 37778 已停止**；轮末 37778–37781 与 37790 全部空闲、37777 完好。

### 文档方向（运维/用户指南）：核实无误，**两个自信假设被实测推翻**

新角度 = 环境变量端到端对拍。**两个我相当有把握的推断都是错的**：

1. **「限流三变量绑不上」** → 隔离实例 37790 实测：
   `CLAUDEMEM_RATE_LIMIT_MAX_REQUESTS=3` → **第 4 次 429**；
   `CLAUDEMEM_RATELIMIT_MAXREQUESTS=3` → **同样第 4 次 429**。**两种都生效，文档正确。**
2. **「`.env.dev` 的 embedding base URL 少了 `/v1/embeddings`」** → 全仓计数后**结论相反**：
   后端自身的 `application-dev.yml:15`、`backend/.env.example:30`、两个 docker 脚本
   与 12 份文档**全部用短形式**，长形式只出现在 gitignored 的 `CLAUDE.md:210`。**文档正确。**

**若不先测，这两处都会把正确文档改成错误文档。**
**其余核实为真**：§2.1 快启动 `.env` 的 12 个变量**全部**被 compose 真实引用（零幻影）；
§5 覆盖 compose 实际读取的 11 个变量中的 10 个，缺的 `IMAGE_NAME`
在 `DOCKER_README.md:68` 有专表、`DEPLOYMENT.md:139` 内联展示默认值。
**首版探针自身出错**：把 Spring **属性名**（`claudemem.mode` 等）当成环境变量名，
刷出 33 条假「遗漏」，该栏整体作废重做。

### 变更检测与验收

```
指纹 0b8d9c7174beb19d21870f65985877fad6f940b0e21245255b7fe95d1b7d37a1
  →  6b856f89f2863a87fb502e12ba95b9a8b1ed9d64585137e1d77dfbb5ca20cae9
```
改的是 `.java` 里的一段 Javadoc（描述文本，**无行为变化**）→ 按门控**跑完整验收**
（跑在不含本轮改动的 37777 上）：

| 套件 | 结果 |
|------|------|
| `bash scripts/regression-test.sh --skip-build` | **45 / 0 / 1**（共 46） |
| `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` | **25 / 0 / 0**（带 P2-46 限定） |
| Java demo `mvn -o -DskipTests package` | BUILD SUCCESS（37778 上实测过改后行为） |

基线已更新：`accepted_commit: e2c8877`、指纹 `6b856f89…`。

### 压缩事故：脚本销毁了 P2-47 整块，已从 HEAD 恢复并单遍重做

findings 写入两条新发现后达 **1081 行 / 103814 字节**，越过 `MAX_LINES=1000` 与
`MAX_BYTES=102400` 双限，`doc-growth-check.sh` 返回 **`COMPACTION_REQUIRED` 退出码 2**
（该非零退出会打断 `&&` 链、令 commit 被静默跳过）。
先按文件自身声明的规则**整体移除逐轮摘要表**（第 219~256 轮全部行，与
`patrol-rotation.md` / `doc-review-task.md` 逐字重复），再把 14 段实测类小节迁入归档。

**迁移脚本切错段，且这次是销毁而非错迁。** 根因有二，**两条都是第 253 轮教训的复现**：

1. **在原始索引上算出全部切割区间后，按升序逐个改写同一个列表** ——
   每次替换都让后续区间整体前移，第 4 个区间起全部错位。
2. **边界断言只判 `- **` 与 `### `，漏了 `## `** —— 最后一个 `Evidence` 块因此一路吞到文件末尾。

**结果**：`### P2-47` 的标题与 22 行正文**既不在工作文件、也不在归档里**（真销毁）。
发现方式不是靠肉眼看，而是**拿条目清单与 `git show HEAD` 对拍**：
HEAD 43 条 + 新增 2 条应为 45，实际只有 44。

**处置**：`git checkout HEAD -- <file>` 恢复 → **删掉已生成的归档文件**
（归档创建后不得修改，只能重建，不能在错误的归档上打补丁）→
**单遍顺序重建**（顺序遍历输出、不复用已变更索引）→ 补齐两条新发现与表格移除。

**压缩后逐条验证（四项全过才提交）**：

| 验证项 | 结果 |
|--------|------|
| HEAD 43 条无一缺失、新增恰为 P2-55/P2-56 | ✅ 45 条 |
| **每条 HEAD 条目未迁出的正文逐字仍在** | ✅ **不匹配 0 处** |
| 指点数与归档块数相等 | ✅ 14 = 14 |
| 全部归档指针指向的文件存在 | ✅ 8/8 |

findings 1081 → **984 行**、103814 → **92314 字节**。
**两条硬约束已写进 `## Processing Rules`**（倒序或单遍处理切割区间；
边界断言须覆盖三种行首），并登记于 `docs/archive/README.md`。

## 第 259 轮 — 2026-10-04 18:43 — Java SDK + API 文档

**方向**：代码 Java SDK · 文档 API 文档（doc round 160）

### 健康预检

| 项 | 状态 |
|----|------|
| 后端 37777 | UP（全程未重启） |
| Demo 37778–37781、隔离实例 37790 | 全空闲（上一轮启动的 37778 已停） |
| 工作区 | 干净，HEAD `49cd42a` |

### 代码方向（Java SDK）：wire 映射层设计可靠；立 P2-57（记录不修）

**核实为真**：

| 断言 | 结果 |
|------|------|
| 请求体序列化 | **6 个请求 record 全部有手写 `toWireFormat()`**，发的是 `Map` 而非 record → **完全不依赖调用方的 ObjectMapper 命名策略** |
| 响应反序列化 | `ObservationResponse` 22 个、`Experience` 4 个、`ExtractionResponse` 7 个显式 `@JsonProperty`，多词字段全部覆盖 |
| query 参数名 | **23 个调用端点与活体 OpenAPI 逐条对拍、零不匹配** |
| 路径变量名 | SDK 用 `{template}`、后端用 `{templateName}` —— **仅模板名不同**，运行时替换的是值 |
| 重试逻辑 | 次数 = maxAttempts、抖动退避、429/502/503/504、**500 不重试**，与另三家一致 |

**后端在 `application.yml:104` 配了 `property-naming-strategy: SNAKE_CASE`（反序列化同样按 snake）**，
而 SDK 模块内**没有任何命名策略配置** —— 若请求体依赖 Jackson 序列化，全部字段会被静默丢弃。
**正因为请求侧走 `toWireFormat()`、响应侧走 `@JsonProperty()`，这个雷被绕开了。**

**P2-57（新，记录不修）——四家里只有 Java 的默认 base URL 用主机名**：

| SDK | 默认 base URL | 位置 |
|-----|---------------|------|
| **Java** | **`http://localhost:37777`** | `CortexMemProperties.java:12` |
| Python / Go / JS | `http://127.0.0.1:37777` | `client.py:72` / `client_impl.go:102` / `client-options.ts:68` |

**决定性对照（JDK 自带 HttpClient，同一后端）**：

| JVM 参数 | `localhost:37777` | `127.0.0.1:37777` |
|----------|------------------|-------------------|
| 默认 | HTTP 200 | HTTP 200 |
| `-Djava.net.preferIPv6Addresses=true` | **ConnectException** | **HTTP 200** |

本机 `localhost` 解析顺序实测 **`::1` 在前**；后端只绑 IPv4（`[::1]:37777` 直连失败）。
**改一行即可，但改的是已发布 SDK 的公开默认端点 → 记录不单方面实施。**

**探针自身出错一次（较有价值）**：首轮 grep 命中 `client.py:47` 的 **Javadoc 示例**而非 `:72` 的真实默认值，
一度得出「2:2 分裂」的错误结论；改用**排除注释行**的探针后才看清真实 **3:1**。

### 文档方向（API 文档）：核实无误，三处幻影全是探针假阳性

- 首版抓到的 3 条幻影逐条查原文后**全部证伪**：两条来自 changelog 里描述历史修改的散文，
  正文真正生效的两条（`API.md:564`/`:599`）路径变量是正确的 `{id}`；
  `PUT /api/modes` **全文搜不到**，是正则跨行假阳性。
- **排除 changelog 后重扫：双语各 66 条端点引用、幻影 0、活体端点无一缺席。**
- **字段级对拍**：真实观测 34 个字段，API.md 仅缺 `embedding_768/1024/1536` ——
  **公共 API 参考不该暴露 pgvector 列，这是正确做法，不算缺陷**。
- **主动放弃了一个诱人但无证据的结论**：三个向量列确实出现在响应里，
  本想记「每条观测序列化三份向量」，但**实测本行三者皆为 `null`**，
  `psql` 本机不可用、无法统计全表非空率 —— **证据不足，不写**。

### 变更检测与验收

```
指纹 6b856f89f2863a87fb502e12ba95b9a8b1ed9d64585137e1d77dfbb5ca20cae9
  →  6b856f89f2863a87fb502e12ba95b9a8b1ed9d64585137e1d77dfbb5ca20cae9（未变）
```
**本轮未改任何代码**（仅记录一条 finding、文档方向零改动），
按门控**不触发完整验收**，基线保持不变。

### 压缩（第 259 轮）：两条硬约束首次实测生效

写入 P2-57 后 findings 达 **1030 行**、再次越过 `MAX_LINES=1000`。
按第 258 轮写进 `## Processing Rules` 的两条硬约束执行：
①**单遍顺序重建**（不在原始索引上算好区间后按升序改写同一列表）；
②边界断言**同时覆盖 `- **`、`### ` 与 `## `** 三种行首**——上一批漏掉的正是第三种。
迁出 22 段证据类小节共 53 行（`Scope` 5 / `Evidence` 4 / `实测记录` 6 / `Reproduction` 4 /
`Verification` 1 / `量化证据` 1 / `精确边界` 1），**Problem 与 Status 一行未动**。

**验证**：上一提交 45 条无一缺失、新增恰为 P2-57、**每条未迁出正文逐字仍在**、
22 个指针对应归档中 22 个块、全部归档链接可解析。1030 → **999 行**。

**我的验证脚本自己错了一次**：按 `^## ` 匹配后又 `[1:]`，
把一个**真实块头**当成文件标题丢掉（归档标题是单 `#`），一度报出「22 指针 vs 21 块」的假警报——
**数据本身完整**，改正匹配后才确认一致。**探针出错与数据出错必须分开判定**，
这已是本会话第无数次同一教训。

**遗留**：999 行距 1000 只差 1 行，**下一轮几乎必然再次触发**；
根因是该文件 46 条 ⏸ 条目的决策推理本身已接近单文件承载上限，
下轮需考虑把**已解决条目的 Problem** 也整体迁出（仅第 250 轮对 2 条用过此先例）。

## 第 260 轮 — 2026-10-04 18:55 — Go SDK + SDK README

**方向**：代码 Go SDK · 文档 SDK README（doc round 161）

### 健康预检

| 项 | 状态 |
|----|------|
| 后端 37777 | UP（全程未重启） |
| 37778–37781 / 37790 | 全空闲 |
| Go SDK 五模块 | 全部 `ok`（根 302 / dto 67 / eino 8 / genkit 13 / langchaingo 12） |

### 代码方向（Go SDK）：核实干净；立 P2-58（记录不修）

**核实为真**：重试状态集与另三家一致；10MB 上限**多读 1 字节**以显式报错而非让解析器报困惑的
JSON 错误；`Close()` 对**调用方自带**的 `*http.Client` 不关连接（注释写明理由）；
**全部调用端点的 query 参数名与活体 OpenAPI 吻合**（唯一差异 `platformSource` 即已记录的跨家缺口）。

**P2-58（新）——四家响应 DTO 同缺活体观测的 7 个字段**：

| 字段 | 迁移 | 性质 |
|------|------|------|
| `platform_source` | **V18** | V18 专门新增 |
| `generated_by_model` / `relevance_count` | **V17** | 反馈机制 |
| `content_hash` / `step_number` / `discovery_tokens` / `embedding_model_id` | V8 / V12 / V1 / V2 | 部分疑为内部列 |

**决定性时间证据**：V17、V18 迁移均提交于 **2026-04-16**，
而 `go-sdk/cortex-mem-go/dto/observation.go` **最后改动 2026-10-02** ——
**DTO 在迁移之后被改过，却仍未补上这两批列**。Go / JS 默认忽略未知字段 → **静默丢弃**。

**我的探针两次出错，且两次都指向不存在的缺陷**：
①数「走重试的方法」得 11（文档称 10）—— 实为**文件最后一个方法**的切片延伸到文件尾，
把内部辅助函数 `executeWithRetry` 的**定义**算了进去；截断到 Internal 段前得 **10，与文档一致**；
②早前只搜 `client_impl.go` 便断言「`Close()` 不存在」，实为该方法在 `client_methods.go:366`。

### 文档方向（SDK README）：Python 测试数 435 → 441（双语已修）

`pytest tests/ -q` → **441 passed**；逐文件 **214 + 140 + 87**。
README 写 **435（211 + 140 + 84）**，算术自洽但整体陈旧 ——
第 251 / 252 / 256 轮三次改测试**都没回头改这个数**。中英双语已同步。

**同区域两条断言核过之后决定不动**：
「25 methods」—— AST 枚举 26 个公开方法，第 26 个是 `close()`，
而**下一条 bullet 就写着 context manager**，故 25 指 API 方法数，**准确**；
「Java SDK 重试 25 个中的 10 个」—— 实测 **10 / 25 完全一致**。

### 变更检测与验收

```
指纹 6b856f89f2863a87fb502e12ba95b9a8b1ed9d64585137e1d77dfbb5ca20cae9（未变）
```
**本轮未改任何代码**（一条 finding + 两行 README），**不触发完整验收**，基线不变。

### 压缩：两轮三次边界/控制流错误，本轮全部靠 diff 发现

第 259 轮压到 999 行、距 1000 只差 1 行，本轮写入 P2-58 后立即再次越线。
故改用**结构性杠杆**（第 250 轮先例）：把 4 条**无条件已解决**条目的正文整体迁出。

**连犯两次错误**：
①最后一条候选的结束点取成「下一个 `## ` 行」，**吞掉其后 5 个条目**，文件反而变长；
②改用 `for` 循环时**只加指针、没跳过正文**（此前用 `while i=e`），
`diff` 显示 **0 删除、48 纯新增** —— 正文根本没被移走。

**两次都是靠 `diff` 对拍上一提交发现的，不是靠肉眼**；第二次还额外用
「HEAD 中已不在工作文件的行是否逐字存在于归档」做了零丢失校验。

**终验**：46 行删除**逐字全部在归档**、真正丢失 **0** 行、条目 **47**、归档含 4 条标题、
全部归档链接可解析。findings 1043 → **990 行 / 91813 字节**，`doc-growth-check.sh` 退出 0。

