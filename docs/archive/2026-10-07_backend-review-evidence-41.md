# Backend Review Evidence 41 — 已无条件结案的整条条目（第 324 轮迁出）

> **归档规则**：本批做法与 `-40` 不同。`-40` 迁出的是条目里仍需留在工作文件的
> 问题陈述之外的 **Status 段**；本批迁出的是**已完全结案、不再有待决动作**的
> 条目**全文**（标题 + Scope / Evidence + Problem + Status），工作文件只保留标题
> 与一行指针。
>
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/backend-review-findings.md` 越过 1500 行 / 150000 字节**双阈值**
> 而迁出 **5 条**：P2-24、P2-63、P2-73、P2-74、P2-81。
> 迁出前实测 **1610 行 / 157093 字节**；同批次新增的 P2-88 与 P2-89 已计入该数字。
> 迁移清单本身的缺项见同轮 P2-89 一节。

## 块 1 / 5：P2-24 全文（第 324 轮逐字迁出）

### P2-24: V17 反馈机制整体未接线 —— 实体还映射了一个不存在的列
- **Scope / Evidence**: [`…-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**：**V17 反馈机制整体未接线**，三条实证：①`ObservationFeedbackEntity` 映射了一个
  `V17` **从未创建**的 `created_at` 列（活体 `information_schema` 只有六列），任何触及该实体的
  查询都会报 `column "created_at" does not exist`；②`findByObservationIdOrderByCreatedAtDesc`
  实际按 `createdAtEpoch` 排序、**方法名描述的列不存在**且零调用方；③V17 声明的三项能力
  **全部没有写入方**，「Thompson Sampling 优化的基础」目前是**纯脚手架**。
- **Severity 说明**：①是**潜伏缺陷**而非启动即崩——该表 0 行、repository 零调用方，Spring Data
  不预校验 JPQL 引用的列，故后端仍能正常启动，会在**第一次真正使用该实体时**炸掉。
  ③是**未实现特性**而非错误行为。
- **实测证据**: 逐字迁入 [`2026-10-05_backend-review-evidence-16.md`](../archive/2026-10-05_backend-review-evidence-16.md)（第 267 轮）——含逐列清单、两个 `0 行 / 0 非空` 计数与 `grep setRelevanceCount` 零命中。
- **Verification**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Status**：①②✅ **已修复**（2026-10-03，第 219 轮）。
  ③⏸ **记录不实现** —— 接入反馈采集属**新增特性**（需要新的写入路径、信号定义与
  Thompson Sampling 算法），不是修 bug，按既定纪律留待项目决策。
  **注**：`CLAUDE.md:39` 把 V17 标为「✅ Complete」，该文件已被 gitignore，
  并入既有的 `AGENTS.md` / `CLAUDE.md` 开放项，不在本轮静默修改范围内。
- **复核记录**: [`…-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md)（第 241 轮；Problem / Status 留本文件）。

## 块 2 / 5：P2-63 全文（第 324 轮逐字迁出）

### P2-63: 一次压缩把 P2-30 **整块销毁且未进归档**——正是 P2-47 事故的复发，而现行校验规则本该拦住它
- **Scope / Evidence**: `git log -S'### P2-30:' -- docs/drafts/backend-review-findings.md`
  仅两条命中（`1491f5b` 建立 / **`adf4366` 销毁**）；该提交对工作文件 **+41 / −143**，
  diff 中**无任何含 P2-30 的新增行**，其新建归档 `…-reproduction-5.md` 自述内容为
  P1-1 / P2-8 / P2-28 / P2-29 / P2-32 / P2-34，**不含 P2-30**。
- **Problem**: 压缩日志第 141 轮明写迁出 P2-30 的 Reproduction 时「Scope / Problem /
  Evidence / **Status** stayed put」——**故其主体本应留在工作文件中，不是有意归档**。
  `Impact` / `Status` / `Reproduction` / `复核记录` 幸存于两个归档，**`Problem` 彻底丢失**。
  后果：工作文件第 265 行到 P2-32 之间**没有 P2-30**（`sed -n '413,420p'` 可见），
  而 `python-sdk/cortex-mem-python/cortex_mem/client.py:403` 的 docstring 明写邻近站点
  「is recorded as **P2-30**」——**读者被指向一个查不到的条目**。这与 `Processing Rules`
  记录的 **P2-47 事故（第 258 轮）是同一失效模式**，且那次已立为常设断言。
- **Status**: ✅ **已恢复**（第 271 轮）—— 条目骨架已回到工作文件，`client.py:403` 的引用
  重新可解析；`Problem` 标为**重建**而非逐字，`Impact`/`Status`/`Reproduction` 保持指向
  原始归档。**现行断言为何没拦住**：那几条校验针对的是「边界断言覆盖三种行首」与
  「指针数 = 归档块数」，**没有一条检查「工作文件里曾经存在的条目是否还在」**——
  补这一条属规则变更，需项目决策，未自行添加。

## 块 3 / 5：P2-73 全文（第 324 轮逐字迁出）

### P2-73: 三份配置的 `logging.level.com.claudemem` 全部指向**已经不存在的包**——dev profile 的应用调试日志开关**从未生效**
- **Scope**: `backend/src/main/resources/` 三处——`application.yml:137`（INFO）、`application-dev.yml:33`（**DEBUG**）、`application-prd.yml:18`（INFO）。
- **Problem**: 第 294 轮给 demo 绑回环时顺带发现 `application.yml` 仍在配置 `com.claudemem` 的日志级别，遂全仓追查。
  **该包已不存在**：`backend` 等 **9 个模块中声明 `package com.claudemem` 的文件数为 0**，83 个后端源文件**全部**是 `package com.ablueforce`；
  打包产物 `cortex-ce-0.1.0-beta.jar` 内 `com/claudemem/` 条目 **0**，**活体 37777 进程的 classpath 上也是 0**。
  而真正的 `com.ablueforce` 在**任何**日志配置里都没有级别。**后果按 profile 分级**：
  `prd` 与默认档是 INFO、恰好等于 Spring Boot 默认值，故**看不出任何异常**；**只有 dev profile 是 DEBUG——
  那一档本来就是为调试准备的，它的另外三个 key（`org.springframework.ai` / `org.springframework.web.client` / `org.springframework.http`）
  都是真实第三方包、照常生效，唯独应用自身这一条是死的**。于是用 `--spring.profiles.active=dev` 排障的人能拿到
  Spring AI 的 HTTP 明细日志，却**一条应用 DEBUG 都看不到**，且没有任何迹象指向「配置写错了」。
  **活体双向实测**（37790，两次仅差 `com.claudemem`→`com.ablueforce` 这一处）：

  | | DEBUG 总数 | 来自 `com.ablueforce.cortexce` | 来自 `org.springframework.ai`（未改，作对照） |
  |---|---|---|---|
  | 修复前 | 3 | **0** | 1 |
  | 修复后 | 2228 | **2225** | 1 |

  对照组两次都是 1，**正是它让这个断言有意义**——弱版本「有没有出现 DEBUG」在修复前那次也会通过（总数 3 ≠ 0）。
- **Status**: ✅ **已修**（三处各改一个词）——`com.claudemem` → `com.ablueforce`。
  `application.yml` / `application-prd.yml` 两处 INFO **与默认值相同，行为零变化**；
  `application-dev.yml` 的 DEBUG **自此真正生效**（新增应用 DEBUG 输出），这是该档配置**一直在声称要做的事**。
  **⚠️ 需知悉的副作用**：**本机常驻的 37777 实例正是以 `--spring.profiles.active=dev` 运行的**，
  下次重启它会开始输出应用 DEBUG 日志（约 2200 行量级）。
  **若不希望 dev 档变吵，把 `application-dev.yml` 那一行改成 `INFO` 即可**——那是口味选择，不是缺陷，留给使用方决定。
  验证：完整验收在**含本轮改动的构建**上跑过（见 health-check 报告的新鲜度论证），回归 45/0/1、Phase 3 25/0/0，基线推进。

## 块 4 / 5：P2-74 全文（第 324 轮逐字迁出）

### P2-74: 四家 SDK 的重试**默认极性三比一不同**——Go / Python / JS 是 fail-closed，**只有 Java 是 fail-open**；而这一分歧此前无处记载
- **Scope**: Java `CortexMemClientImpl.isRetryable`（`:811`）、Go `error.go:201 IsRetryable`、
  Python `error.py is_retryable`、JS `errors.ts:129 isRetryable`。
- **Problem**: 四家在**状态码规则上完全一致**——都重试 **429/502/503/504**，都**不重试 500**
  （Go 侧由 `isTransient` 委派给共享的 `IsRetryable`，见 `client_impl.go:443`）。
  分歧在**无法识别的异常**上：

  | SDK | 未识别的异常 | 措辞 |
  |---|---|---|
  | Go `IsRetryable` | **false** | 「The default is "not retryable": an error is only retryable when it is positively identified as transient」 |
  | Python `is_retryable_error` | **false** | 「Passing anything else returns `False`, matching the **fail-closed** rule」 |
  | JS `isRetryable` | **false** | 末尾 `return false` |
  | **Java `isRetryable`** | **true** | 「Non-HTTP errors (network failures, timeouts) are **always worth retrying**」 |

  **差异由运行时决定、不是疏忽**：Go 有 `net.Error`、JS 有 fetch 的 `TypeError` 可供匹配，
  fail-closed 对它们是安全的；而 `RestTemplate` 把连接失败抛成 `ResourceAccessException`
  ——一个**普通的非 HTTP 异常**——若在 Java 侧也 fail-closed，**恰恰会把它本该覆盖的那类错误静默变成不重试**。
  **跨 SDK 移植重试逻辑时的实际后果**：来自 Java 客户端的一个意外异常会被带退避重试到 `maxRetries` 次才浮现，
  同样的异常在其余三家**首次尝试即失败**。
- **⚠️ 判读纪律**：原先怀疑 Java 那句 `// Matches Go SDK isTransient() for consistent behavior across SDKs`
  是**失实陈述**，**逐字重读后撤回该判断**——该注释紧贴在四个状态码的 return 之上、
  Javadoc 那句紧贴在「排除 500」之上，**两者各自限定的范围内都成立**。
  按既定规则**「遗漏 ≠ 失实」**：此处是**分歧未被记载**，不是**说错了**，故不按失实陈述处理。
- **Status**: ✅ **已按准确描述补注**（`isRetryable` 的 Javadoc 新增一段，写明默认极性的分歧、
  运行时成因与移植后果；行内注释同步改为「四个状态码与 Go 完全一致，**但下面的 fall-through 刻意不一致，原因见 Javadoc**」）。
  **纯注释、零行为变更**（diff 非注释行 **0**）。**行为本身不单方面改动**：
  把 Java 改成 fail-closed 会让网络错误**不再重试**（真实回归），把另三家改成 fail-open 则更差——
  两边都是行为变更，按既定规则**记录不实施**。**若要统一，需要先决定哪种极性为准。**

## 块 5 / 5：P2-81 全文（第 324 轮逐字迁出）

### P2-81: `run-all-e2e.sh` 声称跑「全部」E2E 脚本并逐条列出 3 个排除项——**实际漏掉 13 个**，其中 7 个的前置与它自己完全相同
- **Scope / Evidence**: `scripts/run-all-e2e.sh:1-3` 与 `:18-20`（修复前的头注释）；
  `:125-138`（实际调用的 10 个套件）。
- **Problem**: 头注释原文是「Run **all local E2E test scripts** in one pass (excluding Docker
  suites and test-llm-provider.sh)」，并另起一节「**Excluded by design (per project convention)**」
  **逐条列出**三个：`docker-e2e-test.sh`、`docker-compose-test.sh`、`test-llm-provider.sh`。
  **两处都不成立**。`scripts/` 下共 **37** 个 `.sh`，其中测试类 **26** 个；
  **从未被它调用的是 16 个** = **头注释已声明的 3 个排除项** + **未声明的 13 个遗漏**：

  | 未声明的遗漏（13） | 声明的前置 | 本质 |
  |---|---|---|
  | `java-sdk-e2e-test.sh` / `python-sdk-e2e-test.sh` / `js-sdk-e2e-test.sh` | **仅「Backend service running (port 37777)」** | **与本脚本自身前置完全相同** |
  | `phase3-acceptance-test.sh` | **仅「Backend running on port 37777」** | 同上 |
  | `go-sdk-unit-test.sh` / `demo-v15-extraction-test.sh` / `performance-test.sh` | 无额外服务迹象 | 同上 |
  | `demo-v14-test.sh` / `demo-v15-test.sh` | 需 Java demo @ 37778 | 额外服务，**排除本身合理** |
  | `go-sdk-e2e-test.sh` | 需 Go demo @ 37779 | 额外服务，**排除本身合理** |
  | `js-demo-e2e-test.sh` / `python-demo-e2e-test.sh` / `codex-watcher-test.sh` | 需各自 demo / npm | 额外服务，**排除本身合理** |

  **⚠️ 本条第一版把 16 写成 13**：口径是用正则从 `run-all-e2e.sh` 全文里抓 `.sh` 名字，
  结果**连「Excluded by design」注释里提到的 3 个也算成了"已调用"**。
  重算后拆成「16 个未调用 = 3 已声明 + 13 未声明」才准确。**下表 13 行与「7 + 6」的拆分自洽。**

  **危害是「静默的假完整」**：照头注释理解，跑完这一条就等于跑完全部验收，
  实际上**三家的 SDK 套件与 Phase 3 验收一次都没执行**且**没有任何提示**。
  **附带的结构事实**：三个适配器（`eino`/`genkit`/`langchaingo`）是**独立 go.mod**，
  故从 `cortex-mem-go` 跑 `go test ./...` **根本不会执行它们**（实测只出 2 个包）。
  **CI 不构成补偿**：唯一 workflow `docker.yml` 只构建推送镜像，不跑任何测试。
- **Status**: ✅ **头注释已修（第 313 轮，零行为变更）** —— 按「失实陈述的修正可修」，
  头注释改为如实写明「跑的是哪 10 个、另外 13 个是什么、为何排除、单独怎么跑」。
  **未把脚本接进编排**：那会改变一条命令实际执行什么（且这些脚本有副作用、
  无法在本轮逐一验证自动运行安全），属需拍板的变更。
  **接不接、接哪些，留待用户决策。**

