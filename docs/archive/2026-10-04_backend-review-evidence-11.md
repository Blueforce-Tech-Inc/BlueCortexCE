# Backend Review 证据归档（第十一批）

> **Moved by**: 定时项目维护任务，第 258 轮（2026-10-04）。
> **Reason**: 以下是各条发现当时的**复现与实测记录**。逐字保留于此，
> 工作文件只留一行指针；依据第 254 轮经用户决策放开的规则，
> ⏸ 条目的 Scope / Evidence 可迁入归档，**Problem 与 Status 一律留在工作文件**。
> **首版迁移脚本曾切错区间并销毁了 P2-47 整块，已从 HEAD 恢复后用单遍重建重做**——
> 原因是脚本在原始索引上算好全部区间后按升序改写同一列表，每次替换都让后续索引前移。
> 归档文件创建后不再修改。

## ### P1-1: `CortexSessionContextBridgeAdvisor.adviseStream` 依赖普通 ThreadLocal，流式下既丢捕获又泄漏会话

- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-8: 读取侧没有维度路由 —— 写入按维度分列，检索恒定比 `embedding_1024`

- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-27: Python SDK 无法清空 `extractedData` —— 与 Go 并列最弱，而它的注释把这一点说成了「对齐 Go」

- **四家能力阶梯（清空 extractedData）**: JS 两种都能发（`null` 原样透传，类型于第 246 轮修正）→ **真清空**；
  Java 只能发 `{}`（`@JsonInclude(NON_NULL)`）→ 只能落 `{}`；Go 两种都不能（`omitempty`）→ **完全不能**；
  **Python 两种都不能**（`if val is not None` / 显式 `continue`）→ **完全不能**。

  （**第 247 轮补注**：`✗` 只对 Python 的 **dataclass 调用路径**成立。
  `update_observation(id, title=None)` 这种 **kwargs 写法不经过 `to_wire()`**，
  而是逐字段原样拷贝，故 `None` 会真的发上 wire 并清空字段——探针与 demo 活体均已确认。
  即 Python **能**经 kwargs 清空、**不能**经 dataclass 清空。详见 **P2-43**。）

  注意 Go 与 Python **在 facts/concepts 上能力相反**（Go 因 `omitempty` 丢弃空切片而
  不能清空，Python 因 `[] is not None` 而能清空）——见 P2-26。

## ### P2-27: Python SDK 无法清空 `extractedData` —— 与 Go 并列最弱，而它的注释把这一点说成了「对齐 Go」

- **更正（2026-10-04 第 246 轮，本表 JS 行的判定依据当时不成立）**: 该行原以
  「原样透传给 `JSON.stringify`」为由把 JS 判为「能发 `null`」。**运行时确实透传，但类型不允许**：
  `ObservationUpdate` 当时声明为 `title?: string` 等，在本包自身的 `"strict": true` 下
  `{ title: null }` 是**编译错误**——实测 `tsc` 报 `TS2322: Type 'null' is not assignable
  to type 'string | undefined'`，八个字段全中。**本 SDK 自己的测试就是证据**：
  `client.test.ts` 里那两个名为 "should accept null fields for PATCH clear semantics" /
  "should accept all-null fields" 的用例，必须写 `null as unknown as string` 才能表达这个能力。
  故该行在第 246 轮之前实际应记作「**运行时可、类型不可达**」，Python `dto.py` 里
  「JS can send `null`」那句同样只是运行时成立。**第 246 轮已把八个字段放宽为 `T | null`**，
  现在该行按字面成立。四家的**净能力**（能否真正把字符串字段清空为 NULL）也随之明确：
  **只有 JS 能**，Java / Go / Python 三家都只能落 `{}` 或空串——上表未反映这一点。

## ### P2-28: `/api/test/all` 丢弃两个子处理器的状态码，故障时仍返回 200

- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-29: tool-use 去重键不是一次调用的身份，且未被原子强制

- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。
  对负数 **抛 `ValidationError`**，而两个最常用的检索方法静默丢弃——**同一份代码里两种
  处理，且代码与 README 都没给出任何理由**。

## ### P2-32: 两个 Dockerfile 都不设 `SERVER_ADDRESS`，默认部署下服务对外不可达；根镜像的 healthcheck 还写死了端口

- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-34: `GET /api/logs` 的 Swagger 示例漏掉 `files`，且把绝对路径写成 `/logs`

- **实测记录**: 原始 Reproduction/Evidence 已归档 → [`2026-10-04_backend-review-reproduction-5.md`](../archive/2026-10-04_backend-review-reproduction-5.md)（第 244 轮逐字迁出；Scope / Problem / Status 按 ⏸ 规则全部保留在本文件）。

## ### P2-40: 四家 SDK 都能写入 prompts 与 summaries，却没有一家读得回来

- **一处探针自身出错并先识别再采信**：初版探针想用 `dto.SummariesResponse` 去解析
  活体响应，编译失败——**这个类型根本不存在**，而这恰恰印证了「没有 summaries 方法」
  这一判断本身（同一次探针的另一个版本甚至编译不过）。改为直接统计四家 SDK 里
  非测试代码对 `summar(y|ies)` 的引用数（排除 `totalSummaries` 等统计字段），
  四家**均为 0**。

## ### P2-41: `platform_source` 等四个字段后端每条观测都在返回、WebUI 也在按它过滤——而四家 SDK 既不暴露、也不接受过滤

- **一处探针自身出错并先识别再采信**：首版探针把「DTO 解析后的对象」当 dict 处理
  （Python DTO 是 dataclass），于是**每个键都被报成丢弃**、看起来像一片灾难；
  抽查区反而暴露了真问题（`content_hash` 属性不存在），促使改用
  `dataclasses.fields()` 内省。修正后 15 个「丢弃」里**大部分只是改名**
  （`content_session_id`→`session_id`、`project`→`project_path`、`hasMore`→`has_more`、
  `springBoot`→`spring_boot`、`extractedData`→`extracted_data`），
  真正缺失的才是上面那几个——**若不复核就会写成一条夸大的假发现**。

## ### P2-45: 会话启动的 `projects` 字段能生成多项目上下文、API.md 也写了——而四家 SDK 一律发不出去

- **同区域另两处事实（均记录，留待各自轮次处理）**:
  ①**`is_worktree` / `parent_project` 只进日志**——`SessionController` 读了两者后
  **仅用于一条 `log.info`**，不落库、不参与 `initializeSession`；而本该让 worktree 真正生效的
  `WorktreeDetector` 服务**在 `backend/src/` 内零调用者**（除自身文件外无任何引用）。
  活体佐证：带 `is_worktree:true` + `parent_project` 的请求与不带时的响应**完全相同**。
  **但 `API.md` 把两者作为正式字段记载并写进了示例 body**（「Whether this is a worktree」、
  「Parent project name (worktree mode)」），**文档描述的是尚未实现的能力**。
  属 API 文档方向的问题，留待下一轮 API 文档审查更正。
  ②**`CLAUDE.md` 的 Go 测试数与 Go README 互相矛盾**：CLAUDE.md 写「372 unit tests
  (278 core + 61 dto + …)」，Go README 写 359 并给出**实测吻合的分解**（根模块 299 =
  core 232 + dto 67，另加 eino 8 + genkit 13 + langchaingo 12 + `examples/http-server` 27）；
  两者 core/dto 拆分矛盾且 CLAUDE.md 漏了 27 条。**以 Go README 为准**（分解经复核成立），
  更正留待项目决策。①②已分别独立立为 **P2-47** 与 P2-48（`CLAUDE.md` 的端点表幻影）。

## ### P2-53: Go SDK 的 `WithTimeout` 把「太小的值」重置成**默认最大值**——请求 50ms 实际得到 30s

- **Evidence**: 修复前实测（探针直接读归一化后的 `httpClient.config`）：
  请求 `0 / 10ms / 50ms` → 实际 `30s / 30s / 30s`；请求 `100ms` → `100ms`；
  请求 `5s` → `5s`；**同段对照** `RetryBackoff(10ms)` → `100ms`。
  修复后 `0 / 10 / 50 / 99 / 100ms` → 全部 `100ms`，`250ms` 与 `5s` 原样透传。

## ### P2-55: 四个 demo 为同一件事立了同一份文法契约，却 2:2 分裂——而且**与后端一致的那两家是「碰巧」一致的**

- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
  | `１２３`（全角） | **123** | **123** | REJECT | REJECT |
  | `٠١٢`（阿拉伯-印度） | **12** | **12** | REJECT | REJECT |
  | `５`（全角 5） | **5** | **5** | REJECT | REJECT |
  | `1_0` / `0x10` / `10abc` | REJECT | REJECT | REJECT | REJECT |

  Java 侧另有直接探针：`Integer.parseInt("１２３")` 返回 **123**、`"٠١٢"` 返回 **12**、
  `"५"`（天城文）与 `"۵"` 返回 **5** —— 因为 `Integer.parseInt` 内部走 `Character.digit`。
  **决定性的一测在活体后端**（`GET /api/observations?limit=…`）：

  | `limit=` | 后端 |
  |----------|------|
  | `５` | **200，5 条** |
  | `１２３` | **200，100 条**（被 `MAX_PAGE_SIZE` 钳到上限） |
  | `٠١٢` | **200，12 条** |
  | `1_0` | 400 |
  | `0x10` | 200，16 条（`Integer.decode` 十六进制，与 `DemoParams` 的 Javadoc 一致） |

  **所以「谁对」的答案与直觉相反**：按两个 demo 自己写下的判准（「与后端一致」），
  **偏离的是 Go 和 JS**，而放行的 Java 与 Python 只是**各自运行时的副作用**——
  代码里能看出作者本意是拒绝非普通数字（显式挡掉了 `0x10`/`1e3`/`1.5`/`10abc`），
  却没料到 `Character.isDigit` 与 Python `\d` 是 Unicode 宽的。
  **根因不在四家 demo，而在没人规定过「非 ASCII 数字算不算整数」**。

## ### P2-56: Java demo 里四个控制器有三个用了共享校验类，第四个把两个数值参数整个绕过去了——**而那个类的 Javadoc 宣称自己覆盖了所有控制器**

- **Evidence**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
  而不是继续宣称一个假的统一性——**那条假断言正是这个缺陷能长期存在的原因**。
