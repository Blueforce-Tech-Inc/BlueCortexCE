# Conversation 与 Memory Scope 身份边界重构实施蓝图

> **状态**：可实施规划草案；实施前只需复核实时仓库/数据库基线，不需要重新讨论本文件已给出的默认设计决策。
> **编写/复核代码基线**：2026-10-22；父仓库 `3c5aa4bce1a2fba58a2dbf9fb6fc270d4ebbe9ea`；`webui/` gitlink `72e7804b13f89b03177f746867e0dd341fb12c8a`。这是本次复核的调查快照，不是实施时应盲目使用的版本号。
> **目的**：给后续实施者一份可从中断处直接继续的完整路线，安全拆分“一次对话的身份”与“长期记忆的归属范围”，并兼容现有 API、SDK、Viewer 和数据。
> **本轮范围**：只规划，不改服务代码、数据库、WebUI 子模块指针或远程仓库。

## 0. 接手说明

本文件是主实施入口。接手者应先读本节和第 1 节的冻结决策，再按第 8 节执行；不需要先阅读上游仓库或逐层追踪其他文档。链接到代码的引用用于核验具体实现，不是理解方案的前置条件。

开始实施前：

1. 读取仓库 `AGENTS.md`，检查 `git status`；保留已有改动，尤其不要覆盖既存 SDK、部署文档或子模块 WIP。
2. 重新确认父仓库 HEAD、`git submodule status webui`、迁移列表最高版本、部署数据库 Flyway 版本及部署中的后端版本。本文中的 `V19`、父仓库 HEAD 和 WebUI SHA 都是 2026-10-22 的调查基线，不可代替实时核验。
3. 检查目标运行环境及其数据库备份/恢复链路。本文不声称已经访问过生产数据库，也不声称 V13–V19 已在任何特定环境成功运行。
4. 按第 10 节先执行只读 preflight；输出审阅通过后，方可准备 migration。任何冲突须停在 preflight，不得边迁移边猜归属。
5. 严格保留 `webui/` 与后端配套的发布不变量：gitlink 必须指向经契约验证的版本，不得单独追最新 `main`。

### 0.1 术语

| 术语 | 本规划中的唯一含义 |
|---|---|
| **Conversation**（对话） | 某个来源平台的一次对话/会话；拥有内部 UUID，并有来源平台与外部会话 ID。 |
| **Memory Scope**（记忆范围） | Observation 被长期保留、检索和归组的逻辑范围；首发按现有项目路径语义确定，不是 Conversation。 |
| **外部会话引用** | `(platform_source, content_session_id)`；兼容现有 API 中的会话字段。 |
| **内部 Conversation ID** | `mem_sessions.id` 的 UUID；已存在，重构后成为子记录关系的规范内部键。 |
| **Scope key** | 用于唯一确定一个 Scope 的完整字符串；首发项目 Scope 使用存储中的精确 `project_path`，不做路径再编码。 |
| **平台来源** | 对话或记录所属的宿主平台标签（`platform_source`）；用于来源展示与来源过滤，不是 Memory Scope 隔离维度。记录类型/录入渠道另由 Observation 的 `source` 表达。 |

### 0.2 Java 后端与 WebUI 子模块的边界

本项目的 Java 后端不运行参考 WebUI 仓库中的 Worker、SQLite/Chroma 存储、Cloud Sync、MCP server 或 CLI。父仓库只把 `webui/plugin/ui/` 下的静态 Viewer 产物复制到 Spring Boot 的 `backend/src/main/resources/static/`：

- `scripts/deploy-webui.sh` 从 `plugin/ui` 部署 `viewer.html`、`viewer-bundle.js`、字体、图标和图片。
- `scripts/prebuild-webui.sh` 为 Docker 构建执行同样的静态资源 staging。
- `webui/scripts/build-viewer.js` 从 Viewer entrypoint 构建 `plugin/ui/viewer-bundle.js` 和 HTML；它不是 Java Worker 的构建步骤。

当前两个 staging 脚本只复制 Viewer HTML/bundle、字体、图标和图片；`plugin/ui/tv.html` 及其它未被脚本复制的 WebUI 产物不属于 Java 发布包，除非另有单独的部署变更。

因此本规划中的“WebUI 变更”专指与 Java API 配套的静态 Viewer、其 TypeScript 类型/适配层和 Viewer 单元/浏览器测试。WebUI 仓库其余运行时（Worker、SQLite、Chroma/Cloud Sync、MCP/CLI、独立服务端路由）不属于本次迁移对象；它们的全量测试最多作为额外回归，不能替代 Java 数据迁移、API、SSE 或数据库验收。任何需要修改这些运行时的提议必须另立范围和兼容性评估。

## 1. 冻结的设计决策

这些决策由当前代码语义、WebUI 契约和低风险迁移要求得出。实施时不应把它们重新留成开放问题；若新数据证据与决策冲突，先停止并补充 ADR，而不是自行扩大/缩小语义。

| 主题 | 冻结决策 | 理由与边界 |
|---|---|---|
| Conversation 内部主键 | 使用现有 `mem_sessions.id UUID` 作为规范关系键；不另造第二种内部 Conversation ID。 | `id` 已是实体主键，`mem_pending_messages.session_db_id` 已引用它。新内部关联复用该 UUID。 |
| 内部 UUID 的边界 | `conversation_id` 是此数据库部署内的关系键，不是跨库全局身份、授权凭据或导入去重键。 | 导出可保留它作来源诊断信息，但 Import 必须按规范化 `(platform_source, content_session_id)` 重新解析目标 UUID；不得把来源 UUID直接写进目标外键。Scope UUID 不导出为可复用身份，Scope 由类型及原始 project path 在目标库重新解析。 |
| 外部 Conversation 唯一性 | 按 `(platform_source, content_session_id)` 唯一。 | WebUI 已用这对值做 SessionRef；裸 ID 全局唯一与多平台身份语义不一致。项目路径不进入 Conversation 唯一键，因为一次会话可在过程中切换工作目录，项目是记录归属而不是来源 ID 的 namespace。 |
| 外部 session ID 处理 | `content_session_id` 是平台提供的 opaque、大小写敏感字符串；验证非空、无 PostgreSQL 不支持的 U+0000、Unicode code point 数不超过现有 `VARCHAR(255)` 字符上限后，原样存储/比较。不得 trim、lowercase、Unicode-normalize 或从 project path 推导。 | 长度按 code point 而非 UTF-8 字节或 Java UTF-16 code unit 计算，和 PostgreSQL `varchar(255)` 一致。规范化只针对 `platform_source`，不能误改宿主 ID。不同 ID 只有逐字符相同且平台相同才冲突。 |
| 外部 ID 的 HTTP 路由传输 | 含 opaque `content_session_id` 的 API 不得把该值插入 URL path segment；Viewer Session 删除的规范入口使用 query 参数或 request body，WebUI 用 `URLSearchParams` 等标准编码后发送，服务端不得 trim/解码两次。 | `/`、`%`、`?`、`#`、`+`、Unicode 和首尾空格都可能是合法 ID 字符；编码斜杠在反向代理/Servlet 容器中可能被拒绝或改写。现有 `DELETE /api/sessions/{platformSource}/{contentSessionId}` 只作为安全字符 ID 的兼容入口，不作为完整 opaque ID 的唯一入口；WebUI 新版本改用规范入口。 |
| 平台规范化 | 持久化身份只接受规范小写 ASCII slug：`^[a-z0-9][a-z0-9._-]{0,49}$`。API 边界仅剥离首尾 ASCII 空格 `U+0020`、将 ASCII 大写字母折为小写；缺失或剥离后为空按兼容默认 `claude`，其余不合规则值返回 400。适配器负责先把宿主别名映射为稳定 slug。 | SQL 与 Java 必须使用同一规则，不能让 locale、Unicode 空白或宽松 `trim()` 产生不同身份。现有 WebUI 别名归一行为是适配器兼容层，实施需以黄金用例覆盖并确保发往后端的是规范 slug；已有可规范化旧值可迁移，但任何折叠冲突或不能无损规范化的历史值必须由 preflight 阻断并显式处理。平台集合保持开放，不硬编码封闭枚举。 |
| Memory Scope 首发身份 | `project` Scope 唯一键是数据库中完整、原样的非空 `project_path` 字符串；不含 `platform_source`，不含未经认证的 `user_id`。 | 现有 MCP/搜索主要按 project 查询，未按平台隔离；平台是来源标签。路径大小写、分隔符、符号链接、父子 worktree 继续遵循当前调用方传入及精确相等行为，首发不引入路径 canonicalization。 |
| 跨平台记忆可见性 | 同一精确项目路径下的记忆继续跨平台共享；Viewer 仍可按平台筛选展示。 | 保持既有项目检索语义；平台过滤不能意外变成搜索隔离。若未来要隔离，必须是独立产品变更，不属于本次迁移。 |
| `user_id` | 保留为现有会话/抽取元数据，不把它提升为 Memory Scope 隔离键或认证边界。 | 目前服务查询没有一致的 user authorization/filtering；引入它会改变检索和隐私语义。本文不宣称当前系统具有多租户隔离。 |
| Worktree | 每个现有精确 project path 独立形成 project Scope；父项目/工作树的聚合规则不变。 | 不把 `WorktreeDetector` 的展示/多项目列表自动转成 Scope 合并；避免扩大记忆可见范围。 |
| MCP `save_memory` 有项目 | 按传入的精确 `project` 归入对应 project Scope，不创建 Conversation；即使显式传入字符串 `manual-memories`，也按 project Scope 处理。 | 手动写入属于 Scope，不应伪装成一次 SDK 对话。只有缺省 project 才使用 `manual_default`；这样新请求不会把“用户明确选择的路径”误当成保留 sentinel。 |
| MCP `save_memory` 无项目 | 归入唯一 `manual_default` Scope；保留旧兼容 `project_path = "manual-memories"`，但不再创建/挂靠该伪 Session。 | 维持旧的默认过滤/导出表现，同时取消伪对话的整会话删除边界。新写入的 `source` 固定为 `manual`；`platform_source` 表示发起该调用的宿主平台，缺失时兼容默认为 `claude`，不把平台字段当作录入类型。 |
| 手动记录与结构化提取 | `save_memory` 产生的 `standalone_kind='manual'` 默认不进入结构化提取候选；模板若确实需要处理手动记忆，必须通过显式的 `include-standalone-manual: true`（对应 Java 属性 `includeStandaloneManual`，模板级配置）选择加入。 | 当前 `save_memory` 没有写入 `source`，即使默认模板声明 `sourceFilter=["user_statement", "manual"]` 也不会把这些记录当候选。迁移后补写 `source='manual'` 若不设边界会静默扩大 LLM 输入、改变费用和结果；默认排除保留现有有效行为，显式 opt-in 才允许变化。 |
| 手动记录与通用 refinement | `standalone_kind='manual'` 的 Observation 默认永不进入通用 refinement 的低质量删除、stale/overdue 候选、LLM 重写或合并；不提供本次迁移内的 opt-in 开关。迁移前，凡符合 sentinel + `type='discovery'` + `subtitle='Manual memory'` 的 manual-shaped 行也必须 fail-closed 排除，即使其 `source` 非法并等待冲突处理；同一待判定技术桶中的所有子行也必须暂时排除，直到整组身份冲突解决。 | 手动记忆是用户明确保存的内容，`source='manual'` 不能被解释为允许 destructive refinement。当前查询已排除 extraction 类型和 DLQ source，却没有排除 manual；迁移后必须以 `standalone_kind` 硬排除，兼容阶段必须以完整 manual marker 或“未决技术桶整组保护”排除。未来若要整理手动记忆，另行设计显式、可预览、按记录授权的流程，不能靠放宽通用查询实现。 |
| 非对话 Observation 的分类 | 新增 nullable `standalone_kind`，允许 `manual`、`extraction`、`dlq`；`conversation_id IS NULL` 当且仅当它属于这三类之一。Conversation-owned 行必须有 UUID 且 `standalone_kind IS NULL`。 | 当前 MCP manual save、结构化抽取结果与 DLQ 都用合成 Session 承载 Observation；它们不是用户对话，不能因为有旧 Session 外键就继续伪装成 Conversation。 |
| standalone wire 字段 | 保留旧 `content_session_id/session_id` string；增加 `conversation_id` 与 nullable `standalone_kind`。Manual 继续使用 `manual-memories` sentinel，extraction/DLQ 保留现有合成 key 作为兼容/相关性值。 | 新 WebUI 以 `conversation_id:null` 不建立 SessionRef；`standalone_kind` 用于类型化过滤与 export/import round-trip。 |
| Scope UUID 的 wire 边界 | `memory_scope_id` 是数据库内部关系键，首发不加入公共 API、SDK 或导出；既有 `project`/`project_path` 仍是面向调用方的 Scope 选择值。 | 避免泄漏不稳定的本地 UUID、增加客户端持久化耦合；若未来需要 Scope 管理 API，另行版本化，不影响本次 schema。 |
| 删除语义 | Conversation 删除只删除该 Conversation 的 prompts、summaries、Observations；不会删除 Scope 或其他无 Conversation 的记忆。首发不提供批量删除 Scope 的 API。 | Scope 是长期归属而非会话生命周期。手动记忆继续使用单条记录删除。 |
| 兼容默认 | 旧 wire 请求缺 `platform_source` 时按 `claude` 处理；读取/删除旧路径没有平台参数时只解析 `claude`，绝不跨平台猜测。 | 维持 Claude 集成兼容，避免旧请求在新增同名 ID 后变成不确定查找。新增/支持多平台的端点须传平台来源。 |
| 外部 wire | 始终保留 `session_id`、`content_session_id`、`platform_source`、`hasMore`、`updateFiles` 等现有公开字段及其类型/大小写。仅在 `conversation_identity_v2` cutover 后，Observation-facing API/export 稳定输出 `conversation_id`（真实 UUID 字符串或 JSON `null`）和 nullable `standalone_kind`；二者只表达关系与记录类别，不替代旧字段。 | 通过已知 WebUI 与 Proxy 硬契约；新增字段只增不删。 |

## 2. 执行结论

**值得实施，且应先做删除止损，再做身份模型改造。** 这是聚焦式领域建模和数据关系迁移，不是全仓改写。当前问题已在代码路径中存在：固定伪会话混放手动记忆；数据库唯一约束仍是裸 ID；部分服务在已有 Session 上修改来源平台；导入与多个查询/删除仍以裸 ID 运作；Cursor 与其他适配器存在不稳定回退 ID。

核心实施目标：

1. Conversation 用内部 UUID 关联，外部引用以平台+来源 ID 表示。
2. 每条 Observation 有且仅有一个 Memory Scope；manual、extraction、DLQ standalone Observation 可以没有 Conversation，且有明确 kind。
3. Scope 延续当前项目路径检索边界，并跨平台共享；平台来源单独作为 provenance/display filter。
4. 在数据库、服务、导入导出、SDK/适配器、Viewer、SSE、脚本和文档中贯穿同一身份语义。
5. 以只读盘点、可审计回填、兼容发布、明确回滚锚点防止数据丢失或错误隔离。

**重要限制**：本规划没有连接生产数据库，也没有核验实际行数、部署 Flyway 版本、备份质量、迁移耗时或受影响记录。任何这些数字都必须由第 10 节的执行证据生成。

## 3. 已验证事实与缺陷边界

### 3.1 代码与契约事实

1. `mem_sessions.id` 已是 UUID 主键；`SessionEntity.contentSessionId` 当前仍声明全局唯一。Observation、Summary 的 V13 关系改为引用 `content_session_id`，而不是 UUID。[SessionEntity](../../backend/src/main/java/com/ablueforce/cortexce/entity/SessionEntity.java#L21) · [V13](../../backend/src/main/resources/db/migration/V13__unify_session_id_on_content_session.sql)
2. V18 在 sessions、observations、summaries、prompts 上添加 `platform_source`，但只有默认值和普通索引；没有把平台加入 Session 唯一键或所有关系键，也没有设置 NOT NULL。[V18](../../backend/src/main/resources/db/migration/V18__add_platform_source.sql)
3. 当前 Session start 先通过裸 `content_session_id` 查找/创建，再调用 `applyPlatformSource`。已有值为默认 `claude` 的会话收到其他来源请求时可以被改成另一个平台；在未来允许相同外部 ID 跨平台并存后，所有裸 ID 查询都会产生歧义。[SessionController](../../backend/src/main/java/com/ablueforce/cortexce/controller/SessionController.java#L124) · [SessionManagementService](../../backend/src/main/java/com/ablueforce/cortexce/service/SessionManagementService.java#L50)
4. Viewer 会话目录按 `content_session_id` 对 prompts/observations/summaries 计数；删除入口先按平台和外部 ID 找 Session，但随后删除子记录仅按裸外部 ID。它目前依赖全局唯一约束才能不误删。[ViewerSessionService](../../backend/src/main/java/com/ablueforce/cortexce/service/ViewerSessionService.java#L54) · [删除](../../backend/src/main/java/com/ablueforce/cortexce/service/ViewerSessionService.java#L96)
5. MCP `save_memory` 固定查找/创建 `manual-memories` Session；同一伪 Session 可挂接不同 `project_path` 的手动 Observation。当前手动 Observation 没显式设置 `source`，因此 V14 列通常为 NULL；Viewer 删除这个 Session 时会批量删除按该 ID 找到的记录。[ClaudeMemMcpTools](../../backend/src/main/java/com/ablueforce/cortexce/mcp/ClaudeMemMcpTools.java#L238)
6. Cursor `normalizeCursorInput` 在事件无会话 ID 时用 `cursor-${Date.now()}`；before-submit、工具事件、stop 分别调用该归一化函数。缺失来源 ID 时，各事件可能为同一实际对话生成不同外部 ID。[Cursor normalizer](../../proxy/wrapper.js#L232) · [before-submit](../../proxy/wrapper.js#L1136) · [stop](../../proxy/wrapper.js#L1271)
7. Codex watcher 在尚未看到 session metadata 时可造带当前时间的回退 ID；OpenClaw plugin 的 ID 映射保存在进程内，并在创建值时附当前时间。必须核验它们遇到重启、补发和会话恢复时的稳定性，不能只修 Cursor。[Codex watcher](../../codex-watcher/src/watcher.ts#L150) · [OpenClaw plugin](../../openclaw-plugin/src/index.ts#L343)
8. `ImportService.SessionImportData` 未含平台来源，session 去重调用 `findByContentSessionId`；观察、摘要和 prompt 导入去重也有按 session 字符串查询的实现。[ImportService records](../../backend/src/main/java/com/ablueforce/cortexce/service/ImportService.java#L135) · [Session import](../../backend/src/main/java/com/ablueforce/cortexce/service/ImportService.java#L230)
9. WebUI 已定义会话身份为 `(platform_source, content_session_id)`，Session key、列表、详情和删除事件均按复合引用工作；feed row 可没有会话引用。[WebUI sessions.ts](../../webui/src/ui/viewer/utils/sessions.ts#L4)
10. WebUI 在适配器边界对宿主来源做别名归一：`transcript`/含 `codex` → `codex`，含 `cursor` → `cursor`，含 `claude` → `claude`，含 `kimi` → `kimi`，`agy`/`antigravity`/`antigravity-*` → `antigravity-cli`；其他值经 trim、小写及空白转连字符后保留，例如 `opencode`。[WebUI platform-source](../../webui/src/shared/platform-source.ts)
11. MCP Search 目前按 `project` 调用 SearchService，不传 platform filter；说明当前长期记忆检索是项目范围，而 Viewer 的平台过滤是来源展示维度。[MCP search](../../backend/src/main/java/com/ablueforce/cortexce/mcp/ClaudeMemMcpTools.java#L75)
12. `user_id` 是可空的会话字段，注释说明它用于 SDK/结构化抽取；目前没有足够证据证明它是全站统一认证租户边界。[V15](../../backend/src/main/resources/db/migration/V15__add_user_id_to_sessions.sql) · [SessionEntity userId](../../backend/src/main/java/com/ablueforce/cortexce/entity/SessionEntity.java#L42)
13. V13 对找不到父 Session 的 observation/summary 执行了删除。本文只将其列为迁移审计重点；没有证据表明任何特定环境因此丢过多少数据。[V13 orphan cleanup](../../backend/src/main/resources/db/migration/V13__unify_session_id_on_content_session.sql)
14. `ExtractionStorageService` 还创建 `status='extraction'` 的合成 Session 来承载生成结果；key 默认是确定性 `ext:<project-hash>:<user>`，但模板可通过 `sessionIdPattern` 覆盖。它也创建固定 key 为 `dlq:extraction` 的 `status='dlq'` Session 来承载失败记录；Observation 自身保存 project path。它们不是平台对话，DLQ Session 还可能承载多个项目的 Observation。key 仅作兼容 correlation value，不能作为分类条件。[ExtractionStorageService](../../backend/src/main/java/com/ablueforce/cortexce/service/ExtractionStorageService.java#L49) · [StructuredExtractionService](../../backend/src/main/java/com/ablueforce/cortexce/service/StructuredExtractionService.java#L135) · [ExtractionConfig](../../backend/src/main/java/com/ablueforce/cortexce/config/ExtractionConfig.java#L65)
15. `observation_feedback.session_db_id` 是可空 UUID 并有普通索引，但 V17 未为它建立指向 Session 的外键；它是删除伪 Session 时必须盘点的软关联，不能因无 FK 而忽略。[V17](../../backend/src/main/resources/db/migration/V17__observation_feedback.sql) · [ObservationFeedbackEntity](../../backend/src/main/java/com/ablueforce/cortexce/entity/ObservationFeedbackEntity.java#L46)
16. `MemoryRefineService` 的 low-quality、stale、overdue 候选查询目前排除 `type` 为 extraction 的行和 `source='dlq'` 的行，但没有排除 manual marker；`quickRefine` 可删除低质量行，普通 refinement 可对候选做 LLM 重写/合并，且合并仍按裸 `content_session_id` 分组。迁移把可识别历史 manual 行的 `source` 补为 `manual`，不会自动阻止这些 destructive 路径，必须在止损阶段先补 guard；即使 manual-shaped 行因非法 source 进入 conflict，也必须先受保护，不能在等待人工处理期间被 refinement 改写。若同一 sentinel/correlation-key 父组存在 marker 混合、父状态/元数据不符或其它分类冲突，guard 必须保护该组的所有 Observation，而不仅是 marker 行，直到冲突解决。[ObservationRepository refinement queries](../../backend/src/main/java/com/ablueforce/cortexce/repository/ObservationRepository.java#L568) · [MemoryRefineService candidates](../../backend/src/main/java/com/ablueforce/cortexce/service/MemoryRefineService.java#L298)

### 3.2 不能从源代码单独断言的事项

- 任一部署数据库实际处于 V19 或其他版本；迁移运行过几次、是否失败、是否有人工 DDL。
- 生产行数、重复、跨平台碰撞、空来源、孤儿、不同来源子记录、手动伪会话内容和真实影响用户数。
- Cursor/Codex/OpenClaw 的真实版本事件是否总带稳定外部会话 ID，或其进程恢复语义。
- 任一历史数据库备份可恢复、迁移可在维护窗口完成或预计锁时长。

这些都由实施前 preflight、受控回放和恢复演练验证，不把调查者推测写成事实。

## 4. 目标模型与字段契约

### 4.1 关系图

```text
ExternalSessionRef = (platform_source, content_session_id)
                  └── resolves uniquely to mem_sessions.id (UUID)

mem_sessions / Conversation
  id UUID PK
  platform_source + content_session_id UNIQUE
  project_path (session/catalog metadata; not the identity key)
  user_id (existing optional metadata; not a Scope/auth key in this change)
       │
       ├── mem_user_prompts.conversation_id (required)
       ├── mem_summaries.conversation_id (required)
       ├── mem_observations.conversation_id (NULL only for explicitly typed standalone observations)
       └── mem_pending_messages.session_db_id (already references id)

mem_observations.memory_scope_id (required) ──> mem_memory_scopes.id
  project scope: unique by exact stored project_path, shared across platforms
  manual default scope: one explicit scope for save_memory without project

Standalone Observation (`standalone_kind = manual | extraction | dlq`)
  memory_scope_id required; conversation_id NULL; legacy content_session_id retained as wire/correlation value
```

### 4.2 目标表/列

本节是实现应满足的逻辑 schema；migration 编号、列名以实施分支实时 schema 再核验，但不得改变这些语义。

**`mem_sessions`（继续作为 Conversation 表，不为改名迁表）**

- 保留现有 `id UUID PRIMARY KEY`。
- `platform_source VARCHAR(50) NOT NULL`，并以数据库 CHECK 强制小写 ASCII slug `^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$`（表达式使用 `COLLATE "C"`）；新写入先在 API 规范化/校验。
- 保留 `content_session_id VARCHAR(255) NOT NULL`，并把“单列唯一”收敛为唯一 `(platform_source, content_session_id)`。
- `SessionEntity.contentSessionId` 的 JPA 映射不得继续声明 `unique = true`；单列唯一约束只由既有数据库 migration 暂时保留到阶段 F，不能依赖 ORM 自动建表/更新来管理本次唯一性切换。
- 保留现有 `project_path`、status、时间字段、`user_id` 与其他上下文字段。
- 若子表使用三列一致性 FK，增加父键唯一 `(id, platform_source, content_session_id)`；内部 id 仍是规范主键。

**`mem_memory_scopes`（新表）**

- `id UUID PRIMARY KEY`，由应用生成，与当前 `SessionEntity` UUID 生成方式一致；不依赖新增数据库扩展。
- `scope_type VARCHAR(32) COLLATE "C" NOT NULL CHECK (scope_type IN ('project', 'manual_default'))`，首发只用这两类。
- `scope_key TEXT COLLATE "C" NOT NULL`：`project` 类型存完整原样路径；`manual_default` 使用固定内部键 `manual-default-v1`。`"C"` collation 使唯一性和查找按确定性精确字符串比较，不受部署库 locale 或 nondeterministic collation 影响；preflight 必须确认数据库为 UTF-8。
- `display_name` 可用作 UI 友好展示，不参与身份判断；`created_at` 只作审计。
- 增加 CHECK：仅允许 `project` 搭配非空 `scope_key`，或 `manual_default` 搭配 `manual-default-v1`；服务层另外拒绝全空白项目路径，但不得 trim 非空路径。
- 保存 `scope_key_sha256 BYTEA NOT NULL CHECK (octet_length(scope_key_sha256) = 32)`（由 Java `MessageDigest("SHA-256")` 对 `scope_key` 的 UTF-8 原始字节计算），唯一索引 `(scope_type, scope_key_sha256)` 避免把可能很长的完整路径塞进 B-tree 唯一键。**哈希不是最终身份判断**：Scope resolver 在摘要命中后必须用 `scope_key COLLATE "C" = :exactKey COLLATE "C"` 比较完整原文；upsert 命中同 digest 但原文不等时返回显式 `SCOPE_KEY_DIGEST_COLLISION` 错误、不得复用或覆盖该行，并记录无敏感路径的冲突指标。该极低概率事件 fail-closed，不能合并两种路径。
- Scope resolver 的创建必须是并发安全的：先按 `(scope_type, scope_key_sha256)` 执行 `INSERT ... ON CONFLICT DO NOTHING`（或等价数据库原子操作），再在同一事务中按 digest 候选读取并用 `"C"` 原文比较确认；不能采用“先查再插”。同一精确 key 的并发调用必须返回同一个 Scope UUID；同 digest 不同原文必须全部 fail-closed 为 `SCOPE_KEY_DIGEST_COLLISION`，不能把唯一索引冲突转换成另一个 Scope 或覆盖现有行。
- 同一规则适用于 manual_default 固定 key。digest 唯一约束不允许同一 scope type 下保存真实 SHA-256 碰撞的两个 key，因此碰撞只影响该类型下后续 Scope 创建能力，不破坏已存在记录归属。测试通过可注入摘要器构造碰撞，证明绝不错误复用 Scope。
- 本版本不加 `platform_source`、`user_id` 维度；不提供 Scope 批量删除 API。

**`mem_observations`**

- 新增 `memory_scope_id UUID NOT NULL`，FK 至 Scope。
- 新增 `standalone_kind VARCHAR(32) NULL`，允许值仅 `manual`、`extraction`、`dlq`；新增 `conversation_id UUID NULL` 作为关系真值。CHECK 要求二者互斥：真实 Conversation Observation 必须 UUID 非空且 standalone_kind 为 NULL；standalone 必须 conversation_id 为 NULL 且 kind 属于 allowlist。Manual 必须匹配 `source='manual'`、sentinel/type/subtitle marker；历史 classifier 只可把 NULL/空白 source 审计修复为 `manual`，已有其它非空 source 必须进入 conflict，不得被 marker 静默覆盖。Extraction 必须匹配 `source='llm_extraction'` 与字面 type 前缀 `extracted_`；dlq 必须匹配 `source='dlq'` 与字面 type 前缀 `dlq_`。新列在 V20 可空，V21 回填审计完成后加约束。所有记录继续有 `memory_scope_id`。
- 继续保留 `content_session_id VARCHAR(255) NOT NULL` 与 `platform_source VARCHAR(50) NOT NULL`，保持旧 wire key/type；平台字段同样有小写 ASCII slug CHECK。Conversation-owned 行的两个外部字段从父 Session 派生。Manual 兼容值固定为 `manual-memories`，新写入的记录 `source='manual'`；其 `platform_source` 表示发起调用的宿主平台，缺失时兼容默认为 `claude`，可显式传入其它合法平台。Extraction/DLQ 保留已有合成 key（例如 `ext:<project-hash>:<user>` 与 `dlq:extraction`）及当前 platform_source 行为，但 key 只是兼容/相关性数据，不是 Conversation 身份；新写入不创建/查找合成 Session。
- 移除 Observation `content_session_id` 指向 Session 外部 ID 的旧 FK；Conversation-owned 行通过 `(conversation_id, platform_source, content_session_id)` 复合 FK 校验一致。复合 FK 使用 MATCH SIMPLE，因此 standalone 行不引用伪父 Session。旧手动/extraction/DLQ 行只有在下述整组证据通过时才可置空 Conversation；其余冲突在 preflight 阻断，不得靠 type 前缀单列猜测。
- V1/V13 的 legacy 外部 ID 外键还存在于 `mem_summaries` 和 `mem_user_prompts`。V20 期间保留这些旧 FK；V21 先建立并验证新 UUID/复合一致性 FK，再删除 Observation、Summary、Prompt 上所有指向 `mem_sessions.content_session_id` 的旧 FK。`mem_sessions.content_session_id` 单列唯一约束仍保留到 V22。迁移实现须从实时 `pg_constraint` 盘点准确约束名，不能假定生产名称与历史脚本一致。
- `project_path` 保持现有非空约束/JSON API；手动无 project 时继续值为 `manual-memories`，但它只兼容展示/过滤，不再决定 relation 或 Session 身份。

**Wire serialization boundary**

当前 Controller、SSE 和 Viewer 路径有直接返回实体的代码，因此不能只依赖“内部字段不应被使用”的约定。`memory_scope_id` 必须使用 DTO 隔离或实体 `@JsonIgnore`，任何 API、SSE、SDK、导出响应都不得序列化它；`conversation_id` 与 `standalone_kind` 只在已冻结的 Observation-facing v2 wire 中显式输出，并且通过 `@JsonInclude(ALWAYS)` 或 DTO 保证 Conversation-owned 行的 `standalone_kind` 和 standalone 行的 `conversation_id` 都是明确 JSON `null`/值。Summary/Prompt 的内部 `conversation_id`、Summary 的 `summary_job_key`、Pending 的 job key、输入快照、结果引用与删除 tombstone 均必须使用 DTO 隔离或实体 `@JsonIgnore`，不得因新增列泄露到现有 Summary/Prompt/队列 wire；它们现有的 `session_id`/`content_session_id` surface 不变。

**`mem_summaries` 与 `mem_user_prompts`**

- 新增 `conversation_id UUID NOT NULL`，指向内部 Session UUID；保留公开的 `content_session_id`/`session_id` 字段及非空、规范 slug `platform_source`。
- `mem_summaries` 另新增 nullable 的内部 `summary_job_key`；由 SessionEnd durable job 写入固定值 `session-summary-v1`，并建立部分唯一索引 `(conversation_id, summary_job_key) WHERE summary_job_key IS NOT NULL`，它只保证同一 durable job 最多产生一条 Summary，不限制一个 Conversation 的历史 Summary 数量。该键不序列化到 wire。V20 初始允许历史 Summary 为 NULL；历史行默认继续为 NULL，只有存在可审计的精确 durable-job/result 关联证据时才可赋键。行数、内容相似度或时间接近都不能推断 job lineage。
- 使用内部关系查找、计数、导入去重和级联删除；字段副本由父 Conversation 解析，避免裸外部 ID join。
- 迁移后可用复合 FK `(conversation_id, platform_source, content_session_id)` 保证副本一致。若某一实际 ORM/DB 限制不允许该 FK，则以内部 UUID FK + 写入服务严格取父值 + 数据库一致性校验触发器作为等价方案，须测试，不可仅靠约定。

**`mem_pending_messages`**

- `session_db_id` 已引用 Session UUID，继续作为队列关联键；事件去重、session processing/deletion guard、处理状态查询及重试归属均必须按此 UUID 查询。`content_session_id` 只保留为兼容/诊断副本，不能单独作为队列身份。
- 必须改造当前以 `content_session_id` 查重的 `PendingMessageRepository` 方法，以及 `AgentService.isSessionProcessing` 的 pending/processing 检查。否则两个平台共享同一外部 ID 时，一边的 tool event 会误抑制另一边的事件，或一边的 pending work 错误阻止另一边删除。可新增 `platform_source` 供可观测性/重放，但不得取代 `session_db_id`。
- 保留已有 `session_db_id` FK；不要在有 pending/processing 工作时切换或删父行。根据目标库队列规模与 `EXPLAIN` 结果，为 UUID、tool/hash、状态过滤设计所需索引，不盲目沿用裸外部 ID 索引作为身份保障。
- V1 最初只允许 `pending`、`processing`、`processed`、`failed`；V3 已把 `skipped` 加入现行 CHECK，且 Observation worker 用它持久化 LLM 跳过结果。V20 必须通过 `pg_constraint` 识别并替换实际 CHECK（不假定约束名），按 `message_type` 限定状态：`observation` 保持现行五值；`summarize` 允许这五值及 `result_persisted`。摘要状态转移固定为 `pending -> processing -> result_persisted -> processed`；`result_persisted` 表示结果已提交但 durable finalization 尚未完成，是非终态且必须阻止删除；无 Observation 时转为持久 `skipped` 终态，解除删除保护但保留 job tombstone。`skipped` 在两种消息类型中都合法，语义分别是 Observation 被 LLM 跳过、Summary 输入为空。本文不引入独立 `finalizing` 状态值，相关表述统一指 `result_persisted`。摘要运维重试复用原 job/payload 并审计后将状态重置为 `pending`；`failed` 只有在重试耗尽或显式终结后才允许删除。V20 部署前 bridge 不得写入新增状态；新状态只可在 CHECK 与 bridge worker 同步就绪后使用。
- 为 SessionEnd 摘要 job 增加 nullable `job_key`（V20 expand 先加列，旧 Observation job 可为 NULL）；本方案定义 cutover 后每个 Conversation 至多一个 canonical SessionEnd job，固定键为 `session-summary-v1`。这是新 job 的幂等规则，不代表一个 Conversation 至多有一条 Summary，也不改变历史多条 Summary 的保留语义。job 的 terminal row 必须长期保留；若通用队列需要清理 terminal rows，必须先把该键和终态原子归档到永久幂等账本。该 key 只用于 durable job 幂等，不改变 tool-use 去重语义；重复 job key 进入 conflict ledger。
- 摘要 job 增加 nullable `summary_input_snapshot JSONB`、`summary_input_sha256 BYTEA`、`result_summary_id UUID` 与 `result_deleted_at`（或等价、可验证的 durable snapshot/result tombstone）；snapshot hash 固定为 32 字节 SHA-256。V20 先扩列，V21 建立并验证复合结果 FK：Summary 对 `(id, conversation_id, summary_job_key)` 有唯一候选键，job 以 `(result_summary_id, session_db_id, job_key)` 引用它，并用 CHECK 保证非空结果指针只出现在 `summarize` job 且 key 非空。该约束必须防止 job 指向另一 Conversation 或另一 job key 的 Summary；删除 Summary 时由同一事务先写 tombstone、清空 pointer，再删除 Summary，FK 不做跨列级联置空。snapshot 在排空已准入工作后、LLM 调用前持久化精确渲染后的 system/user prompt、prompt/template 版本及 provider/model/请求参数；重试只从该 snapshot 发起相同请求，并先校验 hash。Snapshot 与 Observation 正文同级敏感：不得进入日志、审计导出、API/SSE/export；preflight 测算其空间，按现有数据库权限/备份保护。非终态及可运维重试的 failed job 保留 snapshot；只有显式终结/丢弃失败 job 时才清空正文 snapshot，保留 hash 和永久 job tombstone。摘要结果本身也必须幂等：`mem_summaries.summary_job_key` 的部分唯一索引保证一个 durable job 至多产生一条 Summary。worker 在父锁内按 job 的 `result_summary_id` 与 `summary_job_key` 查找结果；写 Summary、填写结果引用并推进到持久 `result_persisted/finalizing` 状态必须在同一事务提交。崩溃恢复命中该引用后继续 finalize，不再次调用生成 LLM 或插入结果。若用户删除已完成 job 的 Summary，同一事务记录 tombstone 并保留 job 终态；SessionEnd 重放只返回幂等成功，不得因结果行缺失重新生成。历史未关联 Summary 的单条删除不影响 job。不得靠删除重复 Summary 或仅依赖内存标记修复。
- 历史结果与新 job 的边界：所有 `summary_job_key IS NULL` 的历史 Summary（无论每个 Conversation 有零条、一条或多条）均保留原样且默认保持 NULL；不能仅凭数量、内容、时间或 `prompt_number` 给历史行绑定 `session-summary-v1`，也不能仅因多条历史行阻断首次新 job。Repository 已明确记录一个本地数据实例中 896 个 session id 有 212 个存在多条 Summary、最多 33 条，故“每个会话至多一条历史 Summary”不是有效前提。只有同一个已持久 job 的可审计关联证据才能恢复 keyed result；重复 keyed result、keyed result 找不到唯一 job、或 job 指向不一致结果时，才按身份/幂等冲突入账并对该 Conversation fail-closed。所有历史行原样保留，不删除或静默合并。
- 现有 `message_type='summarize'` 且 `job_key IS NULL` 的 pending 不能被新索引忽略：bridge 在同一父行锁内按 `session_db_id` 审计。只有恰好一条、`status='pending'`、`retry_count=0`、处理/完成/失败时间戳均为空且持久 payload 可完整复用的未 claim job，才可原子升级为 `session-summary-v1`；零条才创建新 job。历史未 keyed Summary 的数量不参与该判断，也不代表此 pending 已生成结果。多条 pending、非 pending 状态、非零重试、时间戳或 payload 不完整/不一致，以及无法证明是否已有副作用的行，均进入 conflict ledger 并保留原样；不得自动重试或靠插入新 job 掩盖歧义。当前版本把非 `observation` pending 类型标记为 failed，因此 preflight 必须显式列出这些行，且 bridge worker 接管前不能让旧消费者继续改变其状态。
- 已有 `summary_job_key` 的结果必须与唯一 durable job 和 `result_summary_id` 逐项核对；符合显式 lineage 的结果由 bridge 复用，不得新插入。没有 lineage 的 unkeyed 历史 Summary 不参与该核对。任何 keyed 冲突在人工决议前保持删除保护并阻止该 Conversation 的 SessionEnd，不得以重复生成结果绕过账本。
- 当前 `PendingMessageEntity` 的 JPA `@UniqueConstraint(uk_session_tool_input)` 不能视为已存在的数据库护栏：V6 只创建了普通 `(content_session_id, tool_name, tool_input_hash)` 索引，而且应用层先查后插在并发下可重复入队。目标实现必须移除这份仍以裸外部 ID 为键的 ORM 唯一声明，改由明确的数据库索引/约束和应用协议管理；不能让未来启用 schema generation 时重新创建错误的全局约束。入队必须让同一 UUID 父行锁覆盖“去重读取 + 插入”，或使用等价的原子 insert/conflict 方案，并将键改为内部 `session_db_id`、`tool_name`、完整 `tool_input_hash`。若增加数据库唯一约束/索引，必须明确 `failed` 行的重试语义（优先使用与现有“失败可重试、非 failed 去重”一致的部分唯一规则，或保留父锁下的状态-aware 检查）；不得使用会阻断合法失败重试的无条件唯一约束。并发相同事件、不同响应、不同平台同名 ID、失败后重试都必须有测试。

#### 4.2.1 删除与会话写入的共同串行化协议

会话删除与 pending 入队/完成之间不能只依赖“先检查再删除”。`mem_pending_messages.session_db_id` 在 V1 已是 `ON DELETE CASCADE`；若删除事务检查 pending 后，另一个事务才插入 pending，数据库会允许该 pending 随父行被级联删除，造成在途工作无声丢失。因此所有会改变 Conversation 生命周期或写入其 session-owned 数据的路径必须遵守同一协议：

1. 先解析到内部 Conversation UUID，再在同一数据库事务中对 `mem_sessions.id` 执行 `SELECT ... FOR UPDATE`。锁必须以 UUID 父行作为共同锚点；不能用裸 `content_session_id`、仅应用内 synchronized、或只锁 pending 行替代。get-or-create 的新 Session 先用复合外部唯一键原子创建/读取，拿到 UUID 后再遵守本协议；两平台同名 ID 不得共享锁键。
2. Viewer/服务层删除在该父行锁内重新读取状态、所有 session-owned 非终态 pending/processing 行和需要审计的 feedback 软引用；发现 active、queued、processing、summarizing 或其它定义为在途的工作即返回 409，不删除任何父子行。检查和删除必须在同一事务中完成，不能把“允许删除”的布尔结果带出事务后再执行删除。整 Conversation 删除还必须遵守子表 FK 顺序：确认没有非终态工作后，先按该 UUID 删除其 terminal pending/job 行（此处整个 Conversation 随即删除，因此无需保留其幂等 tombstone），再删 Summary/Prompt/Observation，最后删除 Session；或在同一事务先清除全部 keyed job 的 `result_summary_id` 再删 Summary。不得先删 Summary 再指望稍后的 Session `ON DELETE CASCADE` 清掉仍引用它的 job。
3. pending 入队、claim、retry、完成/失败转态，以及 Conversation-owned Observation、Summary、Prompt 的创建、更新和单条删除，均须先取得同一父行锁，再在该事务内重查父状态并完成关联写入/删除。SessionEnd 将父行置为 `summarizing` 后，外部新 ingest/prompt、导入、Viewer 单条删除、普通 feedback/quality mutation 与 refinement 对该 Conversation 返回 409 或 defer；只有 SessionEnd 关闭准入前已存在且可识别的 pending observation 工作可完成写入，以及摘要 job 自身的 finalization/质量副作用可 mutation。此例外按 pending UUID 和 job key 精确授权，不能仅凭调用方声称“此前已准入”。摘要 job terminal 后才恢复普通子项操作；异步 refinement 必须在该状态后重新取父锁并确认父行仍存在，否则跳过并由已验证的 scheduled catch-up 路径补做。Viewer 单条 Observation/Summary 删除、质量评分/反馈更新、refinement 合并或删除、导入与补偿任务都属于该规则；不能只保护整会话删除而留下并发子项写入。任何要把 `observation_feedback.session_db_id` 写成非空 UUID 的创建/更新路径也必须先取得该父行锁并确认父行仍存在；feedback 只关联 Observation 并不使它免于这项身份生命周期检查。入队、子项 mutation 或 feedback 写入发现父行已被删除或处于删除保护状态时必须失败并让调用方重解析/重试；不能依赖 FK 异常作为正常并发控制。Standalone Observation 不拥有 Conversation 父锁；其写入/更新/删除须使用 Scope/Observation 自身的等价行锁，并保证不会被 Conversation 删除路径选中。所有 repository 原生 SQL、异步消费者、反馈采集、导入任务和补偿任务都纳入清单，不能只改 Viewer 路径。
4. LLM/embedding/网络调用期间不得持有父行锁。调用前先在锁内写入并提交可识别的 pending/processing 持久状态；调用完成后重新取得父行锁，在同一事务中写最终子行并把 pending 转为终态或删除。若删除先取得锁，它会看到该非终态 pending 并拒绝；若完成事务先取得锁，删除随后重新检查并只在安全时继续。进程崩溃由既有 pending/recovery 规则处理，不能用释放锁掩盖在途工作。
5. V20 兼容期创建/查找 manual、extraction、DLQ 技术父行时，对兼容父行使用其精确兼容身份的数据库唯一键/等价行锁；V21 cleanup 必须在写入屏障下运行，或对同一父行采用同样的锁协议。迁移 runner、直接 SQL 和运维脚本不得绕过该屏障直接删除 Session-owned 行。

同一事务若需要触碰多个 Conversation（例如批量导入、批量重放或跨会话 cleanup），必须先按 UUID 的确定性升序取得全部父行锁，再进行任何子项 mutation；锁未全部取得前不得写入，取得后不得调用网络/LLM。不能按请求到达顺序或外部 session ID 顺序加锁，以免引入跨会话死锁。分批任务应把每批限制在可控数量，并在批次边界提交。

摘要 SessionEnd 在上述规则下还必须先按本节的旧 `summarize` job 兼容规则查找/升级既有 pending，再幂等创建或标记 durable job；若 Observation 集合为空，job 也必须转为明确的 `skipped` 终态并解除 Session 删除保护，不能只从进程内集合移除。该兼容处理与首次 payload 冻结规则必须在实现和测试中固定。

摘要生成是该协议的明确实例：SessionEnd 解析 Conversation UUID 后，必须在父行锁内关闭新 Observation/prompt 入队准入，先按本节规则审计/升级既有 `summarize` pending，再创建或复用唯一的 `mem_pending_messages.message_type='summarize'` durable job，并把父 Session 置为 `summarizing`/其它冻结的在途状态后提交；不能只把外部 ID 放进进程内 `summarizingSessions`。首次成功准入的 SessionEnd 请求冻结该 job 的请求 payload（包括 `last_assistant_message` 原值）；并发或后续重放只复用已冻结 payload，不覆盖、不重新排队。已准入的 Observation/prompt 工作排空后，worker 在关闭准入的同一生命周期内构造精确 LLM request，并把 request、prompt/template 版本与模型参数序列化至 `summary_input_snapshot`、计算 hash 后提交；重试/崩溃恢复必须从该不可变 snapshot 发起请求并先校验 hash，不能重新读取可变模板或数据后拼出不同输入。Snapshot 内的 Observation UUID 列表和输入内容因父状态锁协议在 job terminal 前保持稳定；任何维护/写入破坏 hash 时 fail-closed，不用新输入重跑。payload 不同的重复请求仍返回兼容的幂等成功响应，但记录不含正文的 mismatch 指标/hash；不得把正文写日志。未来如需用户显式重生成，必须另立 API/设计并使用不同 job key，不属于本次 cutover。

已经入队的 Observation/prompt 工作仍可完成，但摘要 worker 只有在该 Conversation 的其它非终态工作排空后才可取得一致输入快照；在途工作完成时，仍须允许其已准入的写入按 UUID 落库。为使幂等可由数据库强制，在 pending 表增加 nullable `job_key`、`result_summary_id` 和 `result_deleted_at`，摘要固定写入 `session-summary-v1`，并建立唯一部分索引 `(session_db_id, message_type, job_key) WHERE job_key IS NOT NULL AND message_type = 'summarize'`；等价存储可用，但必须提供相同的持久唯一性、结果指针和删除 tombstone。首次 Summary 创建时，在一个事务内写入 `summary_job_key`、job 的结果引用和 `result_persisted/finalizing` 状态；已存在结果时只做幂等 finalize，不再次调用生成 LLM 或插入第二条结果。Viewer 删除 keyed Summary 时，若对应 job 尚未 terminal，按共同 Conversation 锁返回 409 且不删除；job terminal 后才允许删除，并在同一锁/事务内写 `result_deleted_at`、清空结果引用且保持 terminal。普通 SessionEnd replay 永不重建已删除结果。重试耗尽的 job 保留在可删除状态并记录失败原因；显式运维重试只可用同一 job、同一冻结 payload 并增加审计代数。观察为空时转为持久 `skipped` 终态。

LLM/embedding 调用期间释放父锁；完成、失败、跳过和重试都重新取得同一父行锁，在同一事务中更新结果/job 状态。摘要生成后的质量/反馈 LLM 与 Conversation-owned Observation 更新，也必须在 durable job 转终态前完成；延后异步更新须另有持久 job 或按同锁协议重新准入。只有所有会话拥有的摘要副作用完成后父 Session 才从 `summarizing` 转为 `completed`。进程内集合最多作诊断/去重提示，不能作为删除安全阀。`new_summary` SSE 是 best-effort 通知，不是持久结果真值：job replay 不重复发送；WebUI 在 SSE 重连/初始同步时必须通过 Summary REST feed 对账，使“Summary 已提交、广播前进程崩溃”最终可见。不得宣称 SSE exactly-once。

该协议是阶段 A 删除止损、阶段 C identity bridge、阶段 D backfill/catch-up、V21 cleanup 和最终 Viewer cutover 的共同不变量。实施时必须审查所有 `mem_sessions` 子表写入口并记录“已纳入锁协议/明确 standalone 不适用”的结论；只在删除服务加锁而不改入队、最终写入或 cleanup 不算完成。

**`observation_feedback` 的 Session 软引用**

- `observation_feedback.session_db_id` 已是可空 UUID，但当前没有指向 `mem_sessions.id` 的外键；新增约束前先盘点所有非空/悬空引用。
- 在 V21 constraints 阶段增加 `session_db_id REFERENCES mem_sessions(id) ON DELETE SET NULL` 外键并验证。既有悬空 UUID 没有可恢复的 Session 目标：默认先把 feedback row ID 与原 UUID 写入受限 migration audit，再将该列置 NULL；随后验证 FK。不得丢 feedback 行或跳过验证。
- `observation_feedback.session_db_id` 表示产生该使用/反馈事件的可选触发 Conversation，不是 Observation 的所属关系；它可以合法地指向另一条 Conversation，也可以为 NULL（例如无会话的信号）。V21 只校验非空值指向存在的 `mem_sessions.id`，不能要求它等于 Observation 的 `conversation_id`，也不能因为 Observation 是 standalone 就清空合法的跨会话反馈。悬空 UUID 仍按上一条的审计后置 NULL 规则处理。
- 删除 Conversation 时，Observation 自身的 feedback 仍按现有 Observation FK 随 Observation 删除；指向该 Conversation、但关联到仍保留 Observation 的 feedback 行必须保留，并将 `session_db_id` 置 NULL。**在 V21 外键上线前，Viewer/其它会话删除路径也必须先以 UUID 更新并审计受影响 feedback 行，再删除父 Session**，不能依赖尚不存在的数据库约束。伪 Session cleanup 同理：先审计受影响 feedback row IDs 与原 session UUID，再通过该 FK 的 `SET NULL` 保留 feedback 行。不得产生 dangling UUID 或无审计地丢弃 feedback。

### 4.3 Scope 解析和兼容语义

1. 正常 Observation：取该事件实际持久化的 `project_path` 原值，必须非 null/非空白；按 `(scope_type='project', exactProjectPath)` upsert Scope。写入和回填均不调用 `realpath`、不统一大小写/斜杠、不去掉末尾分隔符，也不把不同的已存路径合并。
2. **读查询的路径兼容边界（重要）**：调查发现现有读路径并非统一使用同一种 project-path 预处理。为避免本次身份拆分暗中改变查询结果，调用方必须先保留其原有预处理，再把“旧代码最终传给 project-path SQL 谓词的那个字符串”交给 Scope resolver。Resolver 本身只按精确字符串查找，不做 `Path.normalize()`、`realpath`、大小写/分隔符转换、trim 或 basename 推导；只读查询不得因 Scope 不存在而 upsert 空 Scope。

   | 已核实的读路径 | 现有 SQL 查询使用的路径 | 重构后 Scope 查询键 |
   |---|---|---|
   | `ContextService.generateContext(projectPath, ...)`，含默认 context、Session start 和 Cursor context 调用 | 方法虽调用 `validateProjectPath()` 作校验、显示名和长度检查，但 Observation/Summary repository 查询仍传入原始 `projectPath`；prior-session message 仍按项目路径选择，当前会话排除条件改用当前 Conversation UUID | Scope 查询使用原始 `projectPath`；prior-session message 保持现有项目/排序语义，只排除当前 UUID，不因另一平台有相同外部 ID 而排除它 |
   | `/api/context/preview` → `generateContextWithFilters(...)` | `validateProjectPath()` 返回的 `validatedPath`；该方法以 `Paths.get(...).normalize()` 处理路径，并拒绝输入中任何 `..` 子串 | `validatedPath`，且保留现有校验/拒绝行为 |
   | `generateContextMultiProject(...)` | 每个输入路径原样交给 Observation/Summary repository | 每个原始路径分别解析，再按现有规则合并结果 |
   | `SearchService`（Viewer search、`/api/context/semantic`、MCP `search`） | `SearchRequest.project()` 原值 | 原始 `project` |
   | Viewer `/api/observations`、project-filtered timeline/anchor 查询及其它经 `rg` 盘点确认的 Observation repository 读路径 | 现有 controller/service 传给 repository 的 project 值；当前这些路径不调用 `ContextService.validateProjectPath()` | 同一原值；实施时为每个命中记录调用点与回归测试，不允许漏改后退回 `project_path` 比较 |
   | Viewer `/api/stats?project=` 的 Observation 计数、`/api/memory/quality-distribution`、`/api/search/by-file` | controller 原样传入 `project`；by-file 另对 folder query 保留现有尾斜杠补齐和文件前缀匹配 | project 解析为 Scope UUID；保留聚合、文件谓词、分页和排序。Stats 中 Summary/Session 计数仍按其既有 project 规则，不改成 Scope 计数 |
   | `ExpRagService.retrieveExperiences` 非 user-scoped 分支 | `findBySource`、`findHighQualityObservations` 及 fallback 使用原始 `projectPath` | 原始 path 解析为 Scope；保留 quality/source/fallback 次序。`userId` 分支是不同语义：Session 先按 `(user_id, 原始 session project_path)` 选出，再按 Conversation UUID 取其 Observation；不能改成该 project 的全 Scope 检索，也不能把 `user_id` 提升成 Scope/auth 边界 |
   | `MemoryRefineService` 的 `quickRefine`/project candidate queries 与 `findDistinctProjects` 调度入口 | 按原始 `projectPath` 查询 low-quality/stale/overdue 行；调度器按 Observation `project_path` 枚举去重项目 | project 条件解析为 Scope；枚举 exact project Scope，并把 `manual_default` 通过兼容键 `manual-memories` 只调度一次，该键按 §4.3 同时解析 default 与同名 project Scope；Conversation-owned 候选保持原有阈值、排序、限额和删除/重写行为，所有 standalone kind（尤其 manual）均硬排除，不能仅依赖 `source` 或旧 type marker |
   | `AgentService.saveObservation` 的短时间重复检测 | `findDuplicateByContentHash` 按原始 `projectPath` 精确限定重复范围 | 用解析出的 Scope UUID 限定同一 30 秒窗口；同一 hash 在不同 Scope 不得互相抑制 |
   | `StructuredExtractionService.runTemplateExtraction` 的候选 Observation 读取 | `findBySourceIn` 按原始 `projectPath`、source 列表过滤，按 `created_at_epoch DESC` 和现有 limit 取候选 | 将原始 project path 解析为 Scope（`manual-memories` 兼容键按本节规则同时解析 `manual_default` 与同名 project Scope）；保持 source 过滤、排序、limit 与 per-user batch cap。默认排除 `standalone_kind='manual'`，仅模板显式配置 `include-standalone-manual: true` 时加入；Conversation-owned 候选按 `conversation_id` 查询 user；允许进入的 standalone 候选没有 Conversation/user，归入 `__unknown__` 组，不按 legacy correlation key 查 Session。 |

   例如 `/repo/./app` 在 `generateContextWithFilters` 路径会先变为 `/repo/app`，而 `generateContext`、SearchService 和其它直接过滤仍使用 `/repo/./app`。这项差异是当前可观察行为，不在本重构中顺手统一；Scope key 对两种**存储值**仍分别精确建模。未来若要统一路径语义，必须单独定义版本化迁移、冲突审计与回滚方案。
3. Worktree 事件：使用现有写入端传给 Observation 的路径；不通过 `parent_project`、`projects` 多值字段合并 Scope。worktree 关系仍是上下文查询的独立规则；多项目查询按上表逐个解析各自的有效查询键。
4. `save_memory(project=...)`：使用正常 project Scope。传入非空字符串原样保留，空白按“未提供”处理，与现有行为一致；即使该字符串恰为 `manual-memories` 也按显式 project Scope 处理。写入 `standalone_kind='manual'`。
5. `save_memory` 无 project：`manual_default`；`project_path` 与旧 `content_session_id` 兼容值仍为 `manual-memories`。迁移将此字面值视为保留的旧无项目标记；任何精确 project=`manual-memories` 的旧查询，为保持结果同时检索同名 `project` Scope 与 `manual_default` Scope；其他精确项目查询不自动纳入 default Scope。没有 project filter 的全局 Viewer Observation 列表/时间线按现有全局列表语义包含两者；`/api/search` 与 MCP `search` 仍要求显式 project，不扩展为无 project 的全库语义搜索。
6. Extraction/DLQ 输出属于 project Scope 下的 standalone Observation，分别标记 `standalone_kind='extraction'`/`'dlq'`；旧合成 session key 保持在 wire 字段中供兼容与相关性查询，但不再建立 `mem_sessions` 行。Extraction 的 prior/latest/history lookup 改用 `(memory_scope_id, standalone_kind, legacy content_session_id, type)` 等效复合过滤，不按 Conversation 解析合成 key；稳定 hash/key 生成规则首发保持不变。**冻结项目边界优先于历史模板的 key 分组**：若自定义 `sessionIdPattern` 未含 `{project}`，旧实现可能把不同 project path 的 extraction prior/history 混在一起；新实现按 Scope 隔离，选择性地停止这种跨项目共享，属于有意的行为变化，不迁移或删除历史记录。阶段 B 必须盘点全部启用模板并影子比较受影响 key；在新读 flag 打开前可回退旧 lookup，放量后按部署回滚窗处理。若确需跨项目抽取，应另行设计显式、可审计的共享 Scope，不把省略 `{project}` 当作隐式授权。DLQ 按 Observation 自身 project path 落入 Scope。
7. Summary/Session 等非 Observation 读路径继续按其既有关系和 `project_path` 规则过滤；本方案不得仅因新增 Scope 就把 Conversation-owned Summary 改成 Scope-owned 数据。任何需要从 Scope 读取 Observation 的入口按上表/全仓调用点清单使用 UUID；没有 project filter 的全局列表仍保持全局语义。
8. Scope 是归属和查询 partition，不是授权边界。鉴权/多租户隔离必须另行设计，不能用本次 Scope 表替代。

## 5. 外部兼容规则

### 5.1 旧请求与新请求

- 所有现有 `session_id` 请求别名继续有效；响应中现有 `content_session_id`/`session_id` 别名行为逐端点保留。
- `platform_source` 缺失时只在**兼容入口**解析为 `claude`；新的一方适配器必须显式传其平台。来源解析为统一函数，禁止在 Controller/Service 中散落不同 default。
- 后端来源解析只剥离首尾 ASCII 空格 `U+0020`，再折叠 ASCII `A`–`Z`；空值使用兼容默认 `claude`。其余输入必须匹配 `^[a-z0-9][a-z0-9._-]{0,49}$`，否则返回 400，不能静默改写或降级。WebUI/适配器可以识别宿主别名，但必须先输出稳定 slug；保留现有别名行为并用共享黄金用例测试，避免 Java 与 WebUI 对同一宿主产生不同 SessionRef。未知平台只要符合 slug 规则即可，不以当前来源清单拒绝扩展。
- 旧的 `GET /api/session/{sessionId}`、`PATCH /api/session/{sessionId}/user` 与批量 SDK session 查询缺少 source；path 形式只作为安全字符 ID 的 Claude 兼容别名，不承诺支持 `%2F` 等容器可能拒绝/改写的 opaque 值。新增规范入口：`GET /api/session?contentSessionId=...&platformSource=...`、`PATCH /api/session/user?contentSessionId=...&platformSource=...`（user ID 仍放 request body）。批量查询保留旧 `contentSessionIds`（整体默认 Claude），另增 `sessionRefs: [{contentSessionId, platformSource}]`，以支持一个请求混合多个来源；不得用两个平行数组表达身份，也不得继续调用可能返回多行的全局 `findByContentSessionId`。
- API 只要以外部 ID 解析 Conversation，就必须通过 JSON body 或 query 参数承载它，不能依赖 path-segment 编码来传输 opaque ID。Viewer 新客户端使用规范 query 入口（`platformSource` + `contentSessionId`）；Viewer delete 与旧 Session path 形式仅保留为安全字符 ID 的兼容别名。查询值按收到的单次解码结果原样匹配。
- Session start、直接 observation ingest、user prompt ingest、session end、summary/refine、批量 import/export、Viewer detail/delete 均携带或推导同一 `ExternalSessionRef`。已存在 Session 不得因新请求的来源不同而“改平台”；必须创建/解析另一个 Conversation 或返回身份冲突。SessionEnd 是仅解析的终态事件：只可解析已存在的复合外部引用；找不到父行时返回 not-found/conflict，不得调用 `ensureSession`/get-or-create 或创建 durable summary job，以免重放复活已删除的会话。

- Viewer 删除的规范入口固定为 `DELETE /api/sessions?platformSource=...&contentSessionId=...`（也可用等价 JSON body，但 query 版本是 WebUI 默认）；现有 `DELETE /api/sessions/{platformSource}/{contentSessionId}` 仅作为安全字符 ID 的 Claude/平台兼容别名。两者最终都必须通过同一 `ExternalSessionRef` resolver 得到 UUID，不能由 path 入口拥有不同删除语义。

### 5.2 平台来源与检索边界

- `platform_source` 继续出现在 Viewer row 和 catalog，Viewer 的可选来源筛选继续工作。
- MCP Search、项目上下文注入、经验检索等记忆检索不新增平台隔离，Memory Scope 共享跨平台 observations。
- Viewer 删除某平台下某会话，只按已解析的内部 Conversation UUID 删除该对话的关联子记录。其它平台即使外部 ID 相同也不受影响。

### 5.3 手动记忆与 Viewer

- MCP `save_memory` 增加可选 `platform_source`，表示发起调用的宿主平台；未提供时按兼容规则设为 `claude`。新 Observation 的 `source` 固定为 `manual`，用于表达记录类型；`platform_source` 不创建 Conversation，也不改变 project Scope 的跨平台共享检索语义。
- 历史 manual marker 行若 `source` 为 NULL/空白，回填为 `manual` 是有意的数据语义修复，不是纯内部改名：此后 `source=manual` 的显式过滤可能返回这些行。默认的结构化提取路径仍按 `standalone_kind` 排除它们；Search/ExpRag/导出及 SDK source filter 必须在迁移前后用对照 fixture 验证并在文档中标明该变化。非空且不是 `manual` 的历史 source 不静默改写，按冲突规则处理。
- `conversation_identity_v2` cutover 后，所有 Observation-facing API/export 响应继续包含原有 string 类型的 `content_session_id/session_id`；同时始终输出 `conversation_id` 为真实 UUID 字符串或 JSON `null`，并输出 `standalone_kind`（Conversation-owned 为 null）。Manual 行保留 `manual-memories` sentinel；extraction/DLQ 保留现有合成 correlation key；真实会话 Observation 输出父 Conversation UUID。Standalone 行不再依赖 Session 存在。此前的 V20/backfill 阶段不得把尚未完成回填的 NULL 关系列直接暴露成 `conversation_id:null`。
- WebUI feed 在 `conversation_id` 明确为 null 时按没有 SessionRef 的 Observation 渲染，不添加到任何 Session detail；不得仅凭旧 sentinel 或 extraction key 构造 SessionRef。manual/extraction/DLQ 的类型由 `standalone_kind` 表达，目录不出现这些伪会话。
- Viewer 单条 Observation 删除保持；整会话删除继续只接受真实 Conversation。数据库和服务层都不允许由 Session 删除级联触及 Scope 行或任何 `conversation_id IS NULL` 的 standalone Observation。
- 兼容 `/api/observations?contentSessionId=manual-memories` 的旧客户端：将其固定解释为 `conversation_id IS NULL AND standalone_kind='manual' AND content_session_id = 'manual-memories'` 的手动项查询，绝不按 sentinel 推断数据库行身份，也不把真实会话混入手动结果。由于该旧请求缺少可区分两者的身份信息，若真实 Conversation 的外部 ID 恰为 `manual-memories`，旧 sentinel 查询不会返回它；这是该 deprecated 查询的明确兼容边界，不得用自动合并结果“修复”。Observation-facing 新接口中，显式 `conversation_id`（UUID）是读取某个真实 Conversation 子项的最高优先级；只有先通过 `(platform_source, content_session_id)` 解析到父 Conversation、再以内 UUID 过滤子项，才允许用复合外部引用读取真实会话。不能在子表上把外部 tuple 与 standalone sentinel 直接 OR 匹配，也不能把该请求解析成删除 Scope。API 文档标明 deprecated，并保留 sentinel 碰撞和真实会话分别读取的测试。
- `manual-memories` 的 marker、父状态和空子表只能证明“形状像兼容技术桶”，不能证明它从未被真实会话使用。若旧数据没有额外 provenance 能排除真实 Conversation，classifier 必须写入 `LEGACY_SENTINEL_IDENTITY_AMBIGUITY` 并保留父行/子行原状，不能把它当作已确认的 standalone 或静默套用 `manual_default`；只有受控 rehearsal/目标环境的显式映射经过复核后才可继续。无 project 与显式 `project=manual-memories` 的旧手动写入之所以可默认归 `manual_default`，仅限父行已经被独立证据确认是兼容技术桶，不能覆盖这类身份冲突。
- Observation/summary/prompt 列表的请求参数也固定身份优先级：`GET /api/observations`、`/api/summaries`、`/api/prompts` 新增可选 camelCase 查询参数 `conversationId`（UUID）和 `standaloneKind`。有 `conversationId` 时只按该内部 UUID 查询 Conversation-owned 行，不能返回任何 standalone；若同时给出 `contentSessionId`/`platformSource`，后端必须解析并确认同一 UUID，不一致返回 400。没有 `conversationId` 时，完整 `(platformSource, contentSessionId)` 先解析父 Conversation 再按 UUID 查询；旧请求缺 source 只默认 Claude；`standaloneKind` 只与 `conversationId` 缺失的 standalone 查询组合使用。旧 `contentSessionId=manual-memories` 入口仍固定为 manual standalone 兼容查询，不能靠子表上裸 `OR` 混合两种身份。query 参数使用 camelCase 是为了与现有 Viewer/API contract 一致，响应字段仍使用已冻结的 snake_case `conversation_id`/`standalone_kind`。
- Extraction/DLQ historical correlation keys 也可能与真实平台 ID 相同；任何 Session lookup/detail/delete 必须以完整外部引用解析 Conversation，standalone 历史查询需带 `standalone_kind`（及兼容 key），不能把合成 key 重新当作 SessionRef。

## 6. 全仓影响面与实施所有权

实施者必须审查并改造下列范围；文件名是 2026-10-22 已发现入口，不保证穷尽。每项均应通过代码搜索补齐实际调用链。

| 范围 | 已知关键位置 | 必须做到 |
|---|---|---|
| DB schema/migrations | `backend/src/main/resources/db/migration/V1__init_schema.sql` 至 V19；本地最高为 V19 | 仅新增迁移；加 scope/FK/复合唯一，回填和 guard；不改写历史 migration。盘点所有 Session 关系，包括没有 FK 的 `observation_feedback.session_db_id`，并在技术父行清理前保护其软引用。 |
| Entity/repository | `SessionEntity`、`ObservationEntity`、`SummaryEntity`、`UserPromptEntity`、`PendingMessageEntity`；对应 Repository | 引入 UUID 关系和 Scope；去掉 `SessionEntity.contentSessionId` 的 JPA `unique=true` 声明（数据库旧唯一护栏仍由 migration 保留至阶段 F）；淘汰所有可能跨平台的裸 ID 查询/删除方法；确保分页、重复检查和批量读取用内部 UUID 或完整外部复合身份。 |
| Ingestion/session state | `SessionController`、`IngestionController`、`SessionManagementService`、`AgentService`、`SummaryGenerationService`、`RateLimitService` | 统一身份解析；并发 get-or-create 原子；来源不再被已有 Session 的默认值覆盖。SessionEnd DTO 必须新增可选 `platform_source`（旧请求缺失时仍默认 Claude），在异步摘要入队前解析 Conversation UUID，并用 `mem_pending_messages` 的 `summarize` durable job、`job_key` 幂等约束、Session 状态和删除保护共同以 UUID 为锚；进程内 `summarizingSessions` 不能是安全依据。Context 的 `getPriorSessionMessages` 继续按原 project 规则选先前 Session，但当前 Session 排除按 UUID。工具事件限流目前在 Session 解析前按裸 `contentSessionId` 建键；改为完整规范化 `ExternalSessionRef`（或解析后 UUID），同一平台同 ID 共享额度、不同平台同 ID 隔离，获取额度与 `retry_after` 查询必须使用同一个键。 |
| Structured extraction | `StructuredExtractionService`、`ExtractionStorageService`、相关 Repository 和 tests | 候选 Observation 按 project Scope 查询，保持 source filter、created-time 顺序、limit 与 per-user batch cap；默认排除 `standalone_kind='manual'`，模板必须显式 `include-standalone-manual: true` 才能扩大输入。re-extraction/session 输入和 Conversation-owned 候选的 user lookup/grouping 按 Conversation UUID；允许进入的 standalone 候选不查合成 Session，按无 user 的 `__unknown__` 分组（与旧技术父行的空 user_id 语义一致）。Generated extraction 与 DLQ observation 写到 project Scope、设 standalone kind，不创建合成 Session；prior/latest/history 用 Scope + kind + 兼容 correlation key + type 查找；保持现有 key/hash、模板限额和追加语义。 |
| Memory search/context/refinement | `SearchService`、`ContextService`、`ExpRagService`、`MemoryRefineService`、MCP 搜索/上下文工具、`ObservationRepository` native queries | 所有 project-scoped Observation 查询/聚合/删除候选按 `memory_scope_id` 解析并维持现有谓词、排序、分页和 fallback；跨平台共享；`manual_default` 只按 §4.3 的兼容规则加入结果。保留 ExpRag 的 userId→Session→Conversation UUID 限定分支。通用 refinement 只处理 `standalone_kind IS NULL` 的 Conversation-owned 行，并按 `conversation_id` 分组；manual/extraction/DLQ 均不进入低质量删除、stale/overdue 候选、LLM 重写或合并。绝不能再用裸 `content_session_id` 把两个平台或不同 Scope 合并；`refined_from_ids` 仍只记录 Observation UUID，并在源行删除/合并时保留可追溯性。复核向量检索 hydration、定时 refine 项目枚举和 cache key，不能只改 Repository 方法名。 |
| Viewer/SSE | `ViewerSessionService`、`ViewerController`、各 Repository、`ObservationFeedbackRepository`、`SSEBroadcaster` | 目录计数、详情、分页、删除、SSE 去重/广播都按 UUID 或复合引用；任何 standalone Observation 均不属于 Session。V21 前的 Conversation 删除也必须按 UUID 清空并审计 feedback 的可选 Session 引用；V21 后由 `ON DELETE SET NULL` 提供数据库兜底。 |
| MCP | `ClaudeMemMcpTools`、相关 tests | `save_memory` 写入 Scope、`conversation_id=NULL`、`standalone_kind='manual'`；搜索的项目语义不变；保护/删除行为回归。 |
| Other Observation consumers | `MemoryController` quality distribution、`ViewerController` project stats/timeline/by-file、`AgentService` content-hash duplicate check、`MemoryRefineService` scheduled project enumeration、所有 ObservationRepository project predicates | 分别保留 project 过滤、文件前缀、聚合、30 秒去重、定时 refine 候选/限额语义并解析 Scope；Summary/Session stats 不改成 Scope；全仓调用点搜索逐项列出并分类，不留未审查的 `project_path` SQL。 |
| Import/export | `ImportService`、`ImportController`、API DTO、`scripts/export-memories.sh`、`scripts/export-test.sh` 与文档 | 新格式保留平台、opaque 外部 session ID、project path 和 `standalone_kind`；Conversation UUID 仅作为来源库诊断字段，不导出 Scope UUID；导入始终重新映射本地 UUID/Scope。旧备份默认 Claude；旧手动/extraction/DLQ 伪父行按严格证据归类；跨平台相同 ID 不再重复合并；每条 Observation 按 project resolver 分配 Scope。V20 旧外部 FK 尚存时，bridge 可按 §8.4 使用已确认的兼容技术父行承载 legacy key，但不得把它当 Conversation 或导出为 SessionRef；V21 cleanup 后的目标路径不得为 standalone 创建父行。导出脚本只从 `conversation_id IS NOT NULL` 的真实 Conversation Observation 生成去重后的 `(platform_source, content_session_id)` `sessionRefs`，standalone 行保留在 observations 中但其 sentinel/correlation key 绝不生成 SessionRef；不再只按裸 ID 汇总。旧 batch payload 仅用于明确的 Claude-only 兼容测试。 |
| Proxy adapters | `proxy/wrapper.js`、Cursor/Claude integration、`proxy/package.json` | 固定发送来源；Cursor 缺稳定 ID 时不制造按事件变化的 ID；转换/透传 body 的契约测试。当前 `npm test` 脚本将 `node:test` 当作文件入口，在 Node v24.6.0 实测以 `MODULE_NOT_FOUND` 失败；必须先修复 Node test-runner 命令并加入真实身份/来源用例，门 C/E 不得把当前脚本结果当覆盖。 |
| Other adapters | `codex-watcher/`、`openclaw-plugin/` | 检验并修复进程重启、恢复、重复事件下外部会话 ID 稳定性；显式传递平台来源；可靠会话 key 缺失时不能伪造一个假装稳定的 ID。 |
| Platform aliases | `webui/src/shared/platform-source.ts`、各 CLI/plugin adapter 与 Java `PlatformSource` 边界 | 保留已存在 alias 输出契约，新增共享黄金用例覆盖 transcript/Codex/Cursor/Claude/Kimi/Antigravity/OpenCode/未知合法 slug；后端只接规范 slug，不重复猜宿主别名。 |
| SDKs | `js-sdk/`、`python-sdk/`、`go-sdk/`、`cortex-mem-spring-integration/` | 为通用客户端提供可选 `platform_source` 配置/请求字段；保留已有 Observation `source` 字段语义；默认保持 Claude 兼容；更新 DTO、wire subset 测试和示例。 |
| Docs/release | `docs/API*.md`、`docs/ARCHITECTURE*.md`、`backend/README.md`、各集成指南、部署文档 | 同步中英文；记录默认值、NULL 的手动记录语义、删除语义、迁移要求和回滚限制。 |
| WebUI 静态 Viewer | `webui/` 子模块的 `src/ui/viewer/`、共享平台身份适配、Viewer tests/browser tests 及 `plugin/ui` 构建产物 | 支持同 ID 多平台独立会话和 `standalone_kind` 类型化的无 SessionRef Observation；只改并验证配套静态 Viewer 版本，父仓库提交 gitlink。Worker、SQLite、Chroma/Cloud Sync、MCP/CLI 不在本次范围内。 |
| Test harness/API contract | `BackendQueryParamContractTest`、API/OpenAPI contract tests，以及 `scripts/` 中 regression/MCP/WebUI/DB 脚本 | 新增/扩展 `platformSource` query 参数时同步更新固定参数名契约、API 文档和 SDK request tests；显式设置并断言目标 URL、数据库 identity、构建产物；禁止环境变量被位置参数默认值覆盖后静默打到姊妹服务。 |

**缓存边界**：`ContextCacheService` 当前以 project path 作为缓存和失效键，不是数据库关系键。重构后保持各入口原有的 raw/validated path 预处理并继续使用同一缓存键；不要把 `memory_scope_id` 直接替换进去，除非同时完成版本化失效和影子读验证。任何 Scope 归属变化、manual default rehome 或新 path 解析差异都必须让对应缓存失效，避免数据库结果已切换而 context 仍返回旧缓存。

实现时可用以下搜索作为补全检查，不得以搜索结果为真理边界：

```bash
rg -n 'findByContentSessionId|findByContentSessionIdIn|deleteByContentSessionId|content_session_id|session_id' backend/src/main/java
rg -n 'platform_source|platformSource|session_id|contentSessionId' proxy codex-watcher openclaw-plugin js-sdk python-sdk go-sdk cortex-mem-spring-integration
git -C webui grep -n -E 'content_session_id|platform_source|session_deleted|sessionKey|sessionRefOf' HEAD -- src/ui/viewer
```

任何命中裸 ID 的路径要被归类为：(a) 已带 Claude 默认约束的兼容入口；(b) 已解析内部 UUID；(c) 需要改造；或 (d) 非 Conversation 身份（如只是展示）。每个命中都要有结论。

## 7. API/数据行为切换表

| 操作 | 现在/兼容入口 | 重构后内部处理 | 兼容性要求 |
|---|---|---|---|
| Session start | `session_id` + 可选 `platform_source` | 解析/原子创建 `(platform, external id)`，返回既有响应字段；内部传 UUID | 缺 source = Claude；冲突来源不改写现有行。旧全局唯一约束移除前，同一外部 ID 的跨平台创建返回明确冲突，不误报成功或改写来源。 |
| Session detail/user update | 旧 `/api/session/{sessionId}` 与 `/api/session/{sessionId}/user` path 入口 | 新 GET/PATCH query 入口用 `(platformSource, contentSessionId)` 解析 Conversation UUID 后读/更新；user ID 仍在 PATCH body | 旧 path 仅兼容安全字符 ID 并默认 Claude；新入口支持 opaque ID，不做 trim 或重复解码。 |
| Observation ingest | 外部 session ID + project | 解析 Conversation UUID + project Scope UUID，保存双关系 | 旧别名保留；缺 source = Claude；新 adapter 显式发送。 |
| User prompt/summary | 外部 session ID | Conversation UUID 关系 | wire 继续回传旧 ID/source 字段。 |
| MCP manual save | `text/title/project`，可选 `platform_source` | Scope upsert；Conversation NULL；`source='manual'` provenance | 老参数仍可用；新增平台来源参数缺失时默认 Claude；project 路径精确保留。 |
| Extraction/DLQ artifact | 当前合成 `ext:<hash>:<user>` / `dlq:extraction` session key | Scope upsert；Conversation NULL；standalone kind + legacy correlation key | 不创建 Conversation；prior/latest/history 按 Scope、kind、correlation key、type 查找；旧 wire key 保持。结构化提取候选默认排除手动 standalone，模板显式 opt-in 才改变。 |
| Project observation search/context | 每个读入口按既有规则得到的有效 project path | 将该精确值解析为一个或多个 Scope UUID；不增加平台过滤 | 保留 route-specific path preprocessing；读不创建 Scope；跨平台共享不变；manual default 特例见 §4.3。Summary/Session 查询仍按其既有 Conversation/project 规则。 |
| Project observation analytics/refinement | stats、quality distribution、search-by-file、ExpRag、scheduled refinement、ingest duplicate check | project predicate 或 duplicate partition 使用 Scope UUID；无 project 的全局聚合维持全局结果 | 保留各自过滤/聚合/排序/fallback；ExpRag userId 分支继续先选 user+project Sessions 再按 Conversation UUID 取 observations；manual-memories 与定时 refine 规则见 §4.3。 |
| Session catalog/detail | Viewer SessionRef `(platform,id)` | 用复合外部引用解析 UUID；子项按 UUID 计数 | 保持 Viewer 字段与分页 wire。 |
| Session delete | Viewer SessionRef | 规范 DELETE query/body 携带完整复合外部身份，解析 UUID 后校验状态并只删该 UUID 的 conversation 子行 | manual rows/Scopes 永不受影响；Observation 删除按既有 FK 处理其 feedback；其他 feedback 保留并由 `ON DELETE SET NULL` 清除被删会话引用；SSE 回传平台+外部 ID。旧 path endpoint 仅兼容可安全路由的 ID。 |
| Import | 旧 snapshot 可能只有裸 ID | 先补平台默认，再生成 Conversation UUID mapping，按 project 解析 Scope | Import report 给出缺省/冲突/隔离计数；不静默吞记录。 |
| Export | 现有兼容字段与 `/api/sdk-sessions/batch` | 保留旧 wire `content_session_id/session_id` 字符串（manual 继续输出 sentinel `manual-memories`，extraction/DLQ 继续输出各自 correlation key），新增稳定 `platform_source`、来源库 `conversation_id` 诊断字段和非 UUID 的 `memory_scope_type`；所有 standalone 行的 `conversation_id` 为 null，并带 `standalone_kind`。批量 Session metadata 使用真实 Conversation 的 `sessionRefs` 复合对象；standalone 行不请求 Session metadata。旧 `contentSessionIds` 请求明确限定 Claude | 旧导出消费者继续可读；schema version 增加且旧数据可读。导入不得复用来源库 Conversation UUID 或 Scope UUID；Scope 由 `memory_scope_type` 与 project path 在目标库重建映射。跨平台同 ID 的两个 Session 必须在导出和 round-trip 后仍是两个 Conversation。 |

## 8. 执行阶段与停止门

整个实施按下列顺序交付。每阶段必须留存命令、commit/build SHA、实际目标和结果；失败时停在对应门，不“继续看看”。

### 8.1 阶段 A：止损补丁（先于结构迁移发布）

1. Viewer 后端在兼容期拒绝删除可识别的旧 manual/extraction/DLQ 技术桶。Manual 按 `content_session_id='manual-memories'`、project path 与完整 marker 证据识别；extraction/DLQ 按父 status、子 source/type marker、非空 Observation 集合，以及无 prompts/summaries/pending 的整组证据识别。保护判断**不得要求父行 `platform_source='claude'`**：旧代码可改写默认来源。返回明确 409/受保护错误；不得仅按裸外部 ID 拦截没有技术桶证据的真实会话。证据互相矛盾时删除 fail-closed 并交给 preflight，而不是猜身份。Session-start 若命中技术父行，必须返回身份冲突，不得重标记该行。即使 UI 隐藏按钮，服务也必须拒绝。对允许删除的真实 Conversation，兼容期删除事务必须先按内部 UUID 锁定父行，在锁内重查状态/pending 并按内部 UUID 将 `observation_feedback.session_db_id` 置 NULL，再完成删除；受影响 feedback row IDs/原 UUID 写入本地受限审计。该步骤在 V21 FK 上线前是应用层责任，不能遗留悬空软引用；入队、最终写入和 cleanup 必须同时遵守 §4.2.1 的共同串行化协议。与此同时，所有 refinement 候选查询和删除/重写/合并入口必须在兼容阶段按 manual-shaped marker 排除手动记录（即使其 `source` 非法并进入 conflict ledger）；若同一 sentinel/correlation-key 父组的 marker、状态、元数据或关联关系存在冲突，必须排除该组所有 Observation，不能只排除 marker 行；不能等 `standalone_kind` 列上线后才保护它们。
2. MCP/Session-start/refinement 端写测试：删除所有已识别技术桶均被拒绝；不同来源 session-start 不得重标记技术父行；同 key 的真实会话只有在身份可判定时可正常删除；单条 Observation 删除只删选中行；active/queued/processing/summarizing 保护不回归；低质量、stale、overdue 和 scheduled quick-refine 均不会删除、重写或合并 manual-shaped 行，包括 source 非法而待冲突处理的行；marker 混合或父状态/关联冲突的技术组中，非 marker 子行也不会被 destructive refinement 触碰。
3. 在阶段 B 上线前禁止任何工具或脚本手工删除已识别或待核验的 manual/extraction/DLQ 技术父行（包括 sentinel 与 correlation-key 父行）；发布说明提醒操作人员。

**门 A**：以上测试在本仓库后端构建产物和目标数据库上通过；调用目标 identity 已打印并验证。阶段 A 可独立发布并可单独回滚，不触发任何 schema 变化。

### 8.2 阶段 B：只读基线、契约与数据画像

按照第 10 节运行脚本，封存结果。逐条确认每个读取/写入/删除端点、SDK/adapter 的身份输入及默认；导出所有启用 extraction template 的有效 `sessionIdPattern`（不记录密钥/正文），列出其跨精确 project path 的 resolved-key 碰撞；确认旧客户端缺平台时的 Claude 默认，不得误把数据库默认值当成请求端契约。

**门 B**：所有生产/目标环境都有已验证备份及恢复证明；所有 SQL 盘点与目标数据库一致；冲突表已归类；后端与 WebUI 兼容版本明确；不存在未解释的数据行或身份碰撞。否则只提交代码修复，不运行迁移。

### 8.3 阶段 C：应用层 identity 与双兼容代码

1. 引入统一 `PlatformSource` 解析和 `ExternalSessionRef` 值对象；单一模块严格实现 §1 的规范：只去首尾 ASCII 空格、仅折叠 ASCII 大写、验证 slug、再应用缺省值；适配器别名归一单独在边界处理。所有 Conversation 写入、pending 生命周期操作和删除遵守 §4.2.1 的 UUID 父行锁协议；不在 LLM/网络调用期间持锁。
2. 移除 `SessionEntity.contentSessionId` 的 JPA `unique = true` 元数据，避免 ORM 映射继续宣称单列全局唯一；当前 `ddl-auto: none` 下这不会删除数据库约束。复合唯一约束尚未上线时，数据库仍保留旧全局 `content_session_id` 唯一约束作为更强的临时并发护栏；期间禁止开放同 ID 跨平台创建，来源不匹配返回明确冲突。复合唯一约束上线后，get-or-create 使用 `(platform_source, content_session_id)` 唯一键上的原子 `INSERT ... ON CONFLICT`（或等价明确并发方案），冲突后读取胜出 UUID。禁止“先查再插”作为唯一竞态保护。
3. Controllers 在边界把旧请求默认成 Claude；服务层只接收已解析身份/UUID。已有会话不能由不同平台 request 改来源。
4. 先实现 Scope resolver 的纯应用层逻辑并保持 feature flag 关闭；阶段 C 制品不得包含对 V20 尚未创建的表/列的 ORM 映射、查询或写入，也不得在启动/健康检查路径隐式访问它们。该制品必须能在当前 pre-V20 schema 上启动，并完成兼容性测试。只有阶段 D 的 V20 expand 已在目标库成功执行、且部署的 bridge 制品包含新映射并能同时兼容新旧列后，才启用 Scope shadow-write；启用前仍不改变既有读结果。
5. 阶段 A 的保护继续保留，直到伪 Session 清理且回滚窗口关闭。

**门 C**：service/Repository 单测、并发竞争集成测试、旧客户端 wire tests 全过；阶段 C 制品可部署于并通过真实 pre-V20 schema 的启动、读写冒烟验证；仍保留旧全局唯一约束且跨平台重复创建关闭；无查询仍假定外部 ID 全局唯一（搜索复核清单签名）。此门只放行不依赖 V20 新表/列的身份兼容代码，不放行 Scope/UUID 关系字段的 ORM 映射或访问。

### 8.4 阶段 D：Expand migration 与可审计回填

1. 仅追加新版本迁移（基线候选为 V20 expand、V21 constraints、V22 仅在阶段 F 删除旧全局唯一；实施时重新检查版本序列并保持这三个语义阶段）。**发布必须分成有门控的独立制品**：第一制品只包含 V20、兼容 bridge 代码与 backfill runner；该制品及部署包内不能含 V21 migration resource。只有全部 writer/queue worker 已切换到 bridge、backfill/catch-up 完成且冲突账本通过门 D 后，才在后续独立变更中加入 V21 并发布/执行；V22 只能在门 F 通过后的另一次发布执行。若发布流程必须提前把 V21 放入同一制品，必须显式设置 Flyway target=20，并通过目标环境启动测试证明 V21 未执行；默认方案不依赖该例外。大批回填不得放在默认单事务 Flyway SQL migration 中：V20/V21 只承担 schema expand/最终 validate-constraint，另提供独立 Java backfill runner，以有界 JDBC 事务逐批提交并持久化 run id/checkpoint；每个步骤有 dry-run、apply、resume 和审计输出。所有 migration 及 runner 先在隔离副本验证。
2. V20 expand：建立 Scope 表和唯一键；给 Observation 加 nullable `memory_scope_id`/`conversation_id`/`standalone_kind`，给 Summary/Prompt 加 nullable `conversation_id`，给 `mem_summaries` 加 nullable 内部 `summary_job_key`，给 pending 加 nullable `job_key`、`summary_input_snapshot`、`summary_input_sha256`、`result_summary_id`、`result_deleted_at`；建立必要索引（包括 pending 摘要 job 与 Summary 结果的部分唯一索引）和迁移审计映射表。先不删旧 unique/FK/列，不先 set NOT NULL。摘要 job/结果冲突清点与处理门、pending 去重所需的部分唯一索引/原子入队策略必须随 V20 rehearsed。特别注意：V13 留下的 `content_session_id -> mem_sessions.content_session_id` 外键在 V20 期间仍然存在，因此 bridge 在 V20 到 V21 的过渡期必须为已分类的历史 standalone 行和新 standalone 写入继续创建/查找 manual/extraction/DLQ 技术父行，作为**兼容载体**以满足旧 FK。已经由 runner 回填或 bridge 写入的 standalone Observation，其新关系字段为 `conversation_id = NULL`、`standalone_kind` 有值、`memory_scope_id` 已设置，同时旧 `content_session_id` 仍暂时引用技术父行；尚未回填的存量行可暂时保持新列 NULL，不能因此提前执行 V21。技术父行不是目标 Conversation，Stage A 始终阻止整会话删除；在 V20/backfill 与旧 WebUI 并存期间，继续提供旧 wire/catalog/detail 兼容行为，不输出尚未可信的 `conversation_id:null`，也不提前隐藏技术父行。完成门 D/V21、cleanup 且配套 WebUI 已部署后，才在受控 cutover 同时启用新 wire、隐藏技术父行、停止创建/查找兼容载体并开放 standalone 写入。V20 上线后先确认 DDL 完成，再滚动部署 bridge 代码；旧实例在扩展期可暂时继续使用旧字段，但必须在最终回填前全部排空。只有新列/表已存在且 bridge 版本已接管所有写入者后，才启用影子/双写。
3. 按第 9.3 节固定分类规则按精确 `project_path` 去重创建 project Scope，并创建唯一 `manual_default` Scope；只有已由完整 classifier 判定为兼容技术桶、且已排除真实会话/显式 project 身份的 manual 行才进入 `manual_default`，不能仅凭 marker 跳过同名 project Scope。回填观察的 Scope，以 observation 自身 project_path 为准，而非 Session.project_path（尤其 DLQ 伪父行可跨项目）。
4. 先执行第 5 项 classifier 的只读判定并生成候选/冲突清单；随后仅对明确不属于任何 standalone marker 候选组的普通会话子行，使用当前仍全局唯一的 `content_session_id` 找父 Session UUID并比较子记录平台和父平台。二者冲突、找不到父行、同一子项候选多父项时记入 conflict 表并停止收紧约束，不要静默选择；不能先给技术 marker 组写入 Conversation UUID 再尝试改回 standalone。
5. 按第 9.3 节冻结 classifier 对 manual、extraction、DLQ 三类技术桶进行整组分类。Manual 仅在完整 marker（`type=discovery`、`subtitle=Manual memory`）、父行 `project_path='manual-memories'`/`status='completed'`、`user_prompt` 与 `last_assistant_message` 均按 Java `String.isBlank()` 判为空白、且无非 marker observation、summary、prompt、pending 时归类；不以父平台来源单独判定。上述条件是兼容技术桶的形状证据，不是历史身份 provenance；无法排除该 key 曾代表真实 Conversation 时，必须按 `LEGACY_SENTINEL_IDENTITY_AMBIGUITY` 整组阻断，不能自动归类。Extraction 仅在父 status 为 `extraction`、Observation 集合非空且每行 `source='llm_extraction'`、type 有字面前缀 `extracted_`、无 prompt/summary/pending 时归类。DLQ 仅在父 status 为 `dlq`、Observation 集合非空且每行 `source='dlq'`、type 有字面前缀 `dlq_`、无 prompt/summary/pending 时归类。三类都要求没有混合的 conversation-owned child；分类后的 Observation 设 `conversation_id=NULL` 和对应 `standalone_kind`，Scope 按自身 project path/固定 manual-default 规则分配，旧 correlation/content_session_id 与 platform_source 保留。Manual 新写入的 `source` 固定为 `manual`；历史 marker 行的 `source` 缺失时可按审计规则补为 `manual`，但不能改写其 `platform_source`，后者继续表示宿主平台，缺失时兼容默认为 `claude`。显式 `manual-memories` 项目歧义按预定 ambiguity ledger 映射。空集合、标记/状态/关系不符、来源非法或关联混合均整组写入 conflict ledger，不得部分改写；详细 ambiguity 与 source repair 规则见 §9.3。
   Manual 的 source 也是完整 marker 的一部分：只有 `source` 为 NULL/空白或已经是 `manual` 才能自动分类；非空且不是 `manual` 的值即使其它 marker 全部匹配，也必须进入 conflict ledger，不能由 classifier 静默覆盖。NULL/空白修复为 `manual` 必须逐行审计。进入 conflict ledger 的 manual-shaped 行以及同一未决技术父组的其它子行，在冲突解决前仍按 manual 保护规则排除所有 destructive refinement，不能因为尚未获得 `standalone_kind` 就落入旧候选查询。
   Extraction/DLQ 类型检查必须使用字面 prefix（例如 `left(type, 10) = 'extracted_'` / `left(type, 4) = 'dlq_'`），不能用未 escape 的 SQL `LIKE 'extracted_%'` 或 `LIKE 'dlq_%'`，因为 `_` 在 LIKE pattern 中是任意单字符。
6. 不能仅凭 sentinel/key/status 删除技术父 Session。只有父 Session 满足对应完整技术桶轮廓，其所有 Observation 已按 kind 完成重归属审计，且没有 Summary、Prompt、pending 或 Conversation-owned child 时，才将父行完整快照写入受限审计归档并标记为 **V21 后清理候选**；V20 期间不得实际删除，因为旧 Observation/Summary/Prompt 外部 ID 外键仍可能引用它。V21 保持所有 writer 为 bridge 版本，先在门 D 已通过的前提下建立并验证新 UUID/复合身份约束（包括 `observation_feedback.session_db_id` 的 `ON DELETE SET NULL` 外键），再删除 Observation、Summary、Prompt 上所有旧外部 ID 外键。随后由 bridge 停止创建/查找技术父 Session；独立、可重入的 cleanup 仅在 `mem_sessions.id` 无 pending 引用、无 Summary/Prompt 外部 ID 引用、无 `conversation_id IS NOT NULL` 的 Observation 外部 ID 引用时，才归档并删除已分类的 manual/extraction/DLQ 父行。删除前将受影响的 feedback row IDs 与原 UUID 写入迁移审计；`ON DELETE SET NULL` 保留 feedback 行并清除伪 Session 引用。Standalone Observation 保留兼容 content_session_id，不再受 FK 约束，不能因 wire key 阻止 cleanup 或被删除。cleanup 失败必须阻断打开对应 standalone 新写路径/完成门，不得级联删除。父行来源可为旧代码改写后的任意合法 slug，不用于单独确认分类；实际值写入审计。任何其它轮廓不匹配或来源值不合法都保留父行并停止删除步骤。各被改动 Observation 的旧外部 ID/source、行 UUID、新 Scope UUID、standalone kind 和迁移规则可追溯；归档数据不能被普通 Viewer 查询。
7. 分批回填 child conversation UUID、standalone kind 与 scope UUID；每批按稳定主键顺序、可重入、可观察进度。不得在 V21 SQL 中偷偷承担大批数据清理。在线写入期间完成首轮 backfill 后必须重复 catch-up，直到新旧写路径产生的增量全部纳入；应用最终写入屏障（维护暂停，或确认所有 writer/消费者均为 bridge 且其写路径原子填充新旧字段）后，再执行最终 sweep。V21 尚未执行时继续保留旧外部 ID FK 所需的技术父行；此时不得 cleanup 或开放不依赖伪父行的 standalone 写入。回填/cleanup 与 live session-owned 写入不得并发绕过 §4.2.1 的父行锁协议；若无法证明所有 writer/消费者均已切换，必须保持写入屏障。失败可从 last completed key 重启；不在事务外维持半写入的“成功”状态。
8. 在最终写入屏障内验证逐表总行数、Scope 数、standalone kind 分布、Conversation/Scope 关系、父子映射数、platform mismatch、FK 反查、feedback 软引用及 embedding/hash 等非目标列未变。保存 before/after 聚合清单和审计行数。通过门 D 仅表示已授权进入 V21 阶段，不代表 V21 已执行或 cleanup 已完成；V21 前解除写入屏障时，bridge 新写仍必须原子填充新旧字段。
9. **门 D 通过后才发布并执行 V21**。constraints migration 先建立父表复合唯一键，再建立并验证 Observation/Summary/Prompt 的 UUID/复合一致性 FK、durable summary job 到其结果的 `(result_summary_id, session_db_id, job_key)` 复合 FK、`observation_feedback.session_db_id` 的 `ON DELETE SET NULL` FK，以及 NOT NULL/CHECK/复合唯一约束；全部验证后再删除 Observation、Summary、Prompt 上指向 `mem_sessions.content_session_id` 的旧 FK。旧单列唯一约束保留到 V22；V21 SQL 不承担大批 cleanup。保持所有 writer 为 bridge 版本。先部署配套 WebUI/API 制品但保持 `conversation_identity_v2` 关闭；然后在受控维护/cutover 窗口运行第 6 步的可重入 cleanup，并在同一流量切换中启用新 wire/catalog 行为。cleanup 前先把受影响 feedback row IDs 与原 UUID 写入同一事务审计，再删除已分类且无 pending、Summary、Prompt 或 Conversation-owned Observation 依赖的技术父行。cleanup 成功后才停止创建/查找伪父 Session 并开放 manual/extraction/DLQ standalone 写入；wire/correlation key 保留但不再创建伪父行。cleanup 或约束验证失败时关闭对应写路径并停止发布，不得级联删除或跳过验证；不得让旧 WebUI 在已清理的父行上继续提供 SessionRef 操作。

**伪会话正向证据**：manual、extraction、DLQ 自动分类和 V21 cleanup 都要求至少一条 Observation，且每一行都满足该 kind 的完整 marker；空观察集合不满足任何 kind 的自动归类。单靠 sentinel/correlation key、路径或 status 不足以证明父 Session 可归档。

**门 D**：未解决冲突计数必须为 0；所有历史歧义须依已记录、可逆且经复核的映射在原表中明确修复，并满足最终约束。不得把留在原表、仍缺 Scope/Conversation 关系的“隔离项”当作放行例外。所有 Observation `memory_scope_id` 非空；Conversation-owned 行有正确 UUID，standalone 行有正确 kind；没有一条合法行被丢弃；审计能反向解释所有改写。通过后才加 NOT NULL/FK/CHECK 与复合唯一。若无法安全解决冲突，则保持 additive nullable schema、暂停后续切换，不进入 constraints 阶段。

#### V20 兼容父行冲突的 fail-closed 规则

在 V20/V21 过渡期，任何 standalone 新写入都必须先检查其 legacy key（`manual-memories`、解析后的 `ext:*` 或 `dlq:extraction`）对应的 `mem_sessions` 父行是否已经被 classifier 证明是兼容技术父行。若该 key 已被真实 Conversation 占用、父行轮廓混合/不明、来源或状态冲突，bridge 必须拒绝本次 standalone 写入并返回可观测的 `STANDALONE_COMPAT_PARENT_CONFLICT`，不得把 Observation 挂到真实 Conversation、不得静默改写 legacy key、不得用时间戳伪造另一个 key。失败计数不含正文并进入门 D 冲突/运行期指标；V21 移除旧外部 ID FK 且完成 cleanup 后，standalone 写入才恢复为不创建父行的目标路径。该短暂可用性退化是数据安全边界，需在发布说明和重试策略中明确。

### 8.5 阶段 E：读写切换与完整功能验证

1. 新写入 dual-write：Conversation-owned 关系以 UUID 为真值、旧外部字段从已解析父 Session 投影；manual/extraction/DLQ standalone 行只写 Scope，不造 Conversation，并保留各自兼容 key 与 kind。
2. 影子读比较旧项目检索与新 Scope 检索的有序 ID 集合/分页边界；允许差异仅限已批准并量化的 manual default rehome，以及自定义 extraction pattern 省略 `{project}` 后改为按 Scope 隔离的结果变化。每项必须有稳定 row/key 级审计解释；任何其它差异未归因前禁止切换。
3. Conversation detail、Catalog count、timeline feed、summary/prompt 关系、重复导入与 delete 均切换到 UUID；SessionEnd/摘要生成的 durable job、pending `job_key` 与 Summary `summary_job_key` 幂等约束、状态转换和删除保护也切换到 UUID，不能只依赖进程内 `summarizingSessions`。摘要 worker 在恢复时必须能区分“无结果、已有结果但未 finalize、已完成”，并对已有 `summary_job_key` 结果执行幂等 finalize，不重复插入 Summary 或广播 SSE。Extraction 输入会话与 user metadata 查询按 Conversation UUID，生成结果的 prior/latest/history 按 Scope + kind + correlation key + type，不能将 standalone output 查询改成 UUID 查询。
4. WebUI 增加 standalone manual/extraction/DLQ Observation 呈现与类型化筛选测试，保留 SessionRef 的 `(platform,id)`，并用目标后端完成目录、详情、单条删除、会话删除、SSE 验证。
5. Proxy/SDK/adapters 显式传 source，验证所有 hook 事件共享稳定外部 SessionRef。没有稳定源 ID 时，优先使用 adapter 的持久化映射（keyed by documented source conversation key）；若稳定源 key 与持久映射均不可得，默认拒绝该次 session-owned ingest，不写入伪造/standalone observation，向调用方给出不含对话正文的 `SESSION_ID_UNAVAILABLE` 诊断并记录计数。只有后续事件获得稳定 ID 后才恢复写入；不自动猜测或回填此前事件。
6. Import/export v2 支持 platform、standalone `conversation_id: null` 与 `standalone_kind`；manual/extraction/DLQ 各自的 legacy key 均按普通兼容值保留。V21 cleanup 后的目标路径中，standalone 导入不创建 Conversation 或伪父 Session；V20 旧外部 FK 过渡期只能按 §8.4 的兼容父行规则暂存 legacy key，且该父行不得出现在新关系、SessionRef 或普通目录中。Conversation-owned 行才按规范外部复合身份解析/创建本地 UUID。旧文件缺 platform 默认 Claude 并统计；新导入跨平台同 ID 不互相去重。导入端将来源库 `conversation_id` 仅作为审计字段，重建 Scope，绝不直接信任或写入来源 UUID/Scope UUID。

**门 E**：第 11 节测试矩阵全绿；线上只读观测窗口内未批准的差异归零，已批准的 manual rehome 与 extraction Scope 隔离差异符合预期且稳定；API doc checker 与 WebUI gitlink 检查通过。未达到时保持旧读路径/feature flag 关闭。

### 8.6 阶段 F：移除裸 ID 全局约束（不可逆兼容门）

1. 在新写入、查询、导入、删除都按完整身份/UUID 运行且稳定一个回滚观察窗后，复核阶段 D 已创建且验证过的唯一 `(platform_source, content_session_id)`，并确认所有 children 已使用 conversation UUID。
2. 在单独的最终 migration 删除 `mem_sessions.content_session_id` 的单列唯一约束/索引，保留复合唯一；清理旧外部 ID FK。
3. **旧二进制不再是安全回滚目标**：一旦产生两个平台相同 `content_session_id`，旧版裸 ID finder 可能非唯一或串数据。可回滚目标只能是已理解新 schema 的兼容桥接版本；要退回更旧版本必须先恢复迁移前数据库快照，并按审计记录/写入日志处理迁移后新写数据，不能仅回滚 jar。
4. 首次允许跨平台相同外部 ID 前，完成受控重复 ID 测试、部署 API 明确默认平台、所有 query scripts identity assertions 通过。

**门 F**：双平台同外部 ID 的创建、查询、导入、Viewer 删除验证全部通过；无调用会触发 `NonUniqueResultException`；回滚桥接版本已真实演练。否则暂不删除全局唯一约束；保持复合 identity 代码兼容，但不开放重复 ID 能力。

### 8.7 阶段 G：清理与稳定文档

- 回滚窗关闭且所有环境确认后，移除双写/旧外部关系列（若确有必要）；此项可另发版本，默认不与首次发布捆绑。
- 保留 migration audit 及其访问限制；不要把审计删掉当作“清洁 schema”。
- 更新中英文 API/架构/部署和集成文档；将本草案标注为已实现版本、迁移证据与回滚锚点，或按文档生命周期归档。

## 9. 数据迁移实施设计

### 9.1 迁移原则

- 目标数据是不可替代资产；**迁移不得用删除坏行来满足新约束**。未知/冲突行进入 conflict ledger，阻断约束收紧，保持行原状。
- 不修改任何已发布 `V1`–`V19` migration。实施前检查 `flyway_schema_history` checksum 与 success 状态；如环境有手工 DDL，要记录实际 schema 与版本表差异。
- 所有改写均以稳定行主键为依据，可重入、分批、可观察。并行部署时先确保旧写路径仍合法，再收紧列约束。
- `CREATE INDEX CONCURRENTLY` 不能放在默认事务 migration 中；若数据量要求并发建索引，明确配置 Flyway non-transactional migration、失败后清理 invalid index 的流程。小库也必须测锁与阻塞。
- 单实例执行 migration；应用副本不得同时抢跑长 backfill。设置锁超时/statement timeout 前先测定，不通过无限等待锁死服务。

### 9.2 只读 SQL 盘点清单

以下 SQL 是检查意图的可执行模板，默认输出只含聚合计数和非敏感状态，不输出项目路径、外部会话 ID、原始平台值或行 UUID。只读执行，不要把包含敏感项目路径/会话 ID 的完整结果放进 Git；确需定位冲突时，只通过第 10 节要求的显式敏感明细导出，将脱敏行 UUID/最少必要映射保存到带访问控制的本地附件。

```sql
-- 目标实例身份（先确认不是姊妹服务/错误数据库）
SELECT current_database(), current_user, current_setting('server_version'),
       current_setting('server_encoding'), inet_server_addr(), inet_server_port();

SELECT datcollate, datctype
FROM pg_database WHERE datname = current_database();

-- 相关文本列的实际 collation。Scope 使用 "C"；旧项目过滤列与外部身份列
-- 必须为 deterministic collation，避免新旧相等语义在迁移后发生静默变化。
SELECT t.relname AS table_name, a.attname AS column_name,
       c.collname, c.collprovider, c.collisdeterministic
FROM pg_attribute a
JOIN pg_class t ON t.oid = a.attrelid
JOIN pg_namespace n ON n.oid = t.relnamespace
JOIN pg_collation c ON c.oid = a.attcollation
WHERE n.nspname = 'public'
  AND a.attnum > 0 AND NOT a.attisdropped
  AND (t.relname, a.attname) IN (
      ('mem_sessions', 'project_path'),
      ('mem_sessions', 'platform_source'),
      ('mem_sessions', 'content_session_id'),
      ('mem_observations', 'project_path'),
      ('mem_observations', 'platform_source'),
      ('mem_observations', 'content_session_id'),
      ('mem_summaries', 'project_path'),
      ('mem_summaries', 'platform_source'),
      ('mem_summaries', 'content_session_id'),
      ('mem_user_prompts', 'project_path'),
      ('mem_user_prompts', 'platform_source'),
      ('mem_user_prompts', 'content_session_id')
  )
ORDER BY t.relname, a.attname;

-- 迁移状态；对照实际 schema，不只看版本号
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank;

-- 旧全局唯一是否仍成立，以及来源规范化后的潜在复合冲突
SELECT count(*) AS duplicate_external_id_groups,
       COALESCE(sum(row_count), 0) AS affected_sessions
FROM (
    SELECT count(*) AS row_count
    FROM mem_sessions
    GROUP BY content_session_id COLLATE "C"
    HAVING count(*) > 1
) duplicates;

-- 外部 ID 必须是非空 opaque 字符串；ASCII 空白先查，JVM preflight runner 再按 String.isBlank() 全量校验
SELECT count(*) AS blank_or_ascii_space_session_ids
FROM mem_sessions
WHERE content_session_id IS NULL OR btrim(content_session_id, ' ') = '';

SELECT count(*) AS normalized_composite_collision_groups,
       COALESCE(sum(row_count), 0) AS affected_sessions
FROM (
    SELECT count(*) AS row_count
    FROM mem_sessions
    GROUP BY COALESCE(NULLIF(translate(btrim(platform_source, ' '), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ''), 'claude') COLLATE "C",
             content_session_id COLLATE "C"
    HAVING count(*) > 1
) collisions;

-- 四张表中规范化后不满足小写 ASCII slug 的历史来源必须进入 conflict ledger；不可自行删除或猜测映射。
-- translate 只折叠 ASCII 大写，避免 PostgreSQL locale 对 Unicode lower() 产生不同结果。
SELECT table_name, count(*) AS invalid_source_rows
FROM (
    SELECT 'sessions' AS table_name
    FROM mem_sessions
    WHERE (COALESCE(NULLIF(translate(btrim(platform_source, ' '), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ''), 'claude') COLLATE "C") !~ '^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$'
    UNION ALL
    SELECT 'observations' FROM mem_observations
    WHERE (COALESCE(NULLIF(translate(btrim(platform_source, ' '), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ''), 'claude') COLLATE "C") !~ '^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$'
    UNION ALL
    SELECT 'summaries' FROM mem_summaries
    WHERE (COALESCE(NULLIF(translate(btrim(platform_source, ' '), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ''), 'claude') COLLATE "C") !~ '^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$'
    UNION ALL
    SELECT 'user_prompts' FROM mem_user_prompts
    WHERE (COALESCE(NULLIF(translate(btrim(platform_source, ' '), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ''), 'claude') COLLATE "C") !~ '^[abcdefghijklmnopqrstuvwxyz0123456789][abcdefghijklmnopqrstuvwxyz0123456789._-]{0,49}$'
) invalid_sources
GROUP BY table_name;

-- 需要迁移的来源空值/空白概况；不输出原始来源值
SELECT 'sessions' AS table_name,
       count(*) FILTER (WHERE platform_source IS NULL) AS null_rows,
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source, ' ') = '') AS blank_rows,
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source, ' ') <> '') AS nonblank_rows
FROM mem_sessions
UNION ALL
SELECT 'observations', count(*) FILTER (WHERE platform_source IS NULL),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source, ' ') = ''),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source, ' ') <> '')
FROM mem_observations
UNION ALL
SELECT 'summaries', count(*) FILTER (WHERE platform_source IS NULL),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source, ' ') = ''),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source, ' ') <> '')
FROM mem_summaries
UNION ALL
SELECT 'user_prompts', count(*) FILTER (WHERE platform_source IS NULL),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source, ' ') = ''),
       count(*) FILTER (WHERE platform_source IS NOT NULL AND btrim(platform_source, ' ') <> '')
FROM mem_user_prompts;

-- Observation 当前父行缺失/平台值与父行不一致（实际执行前确认列及旧约束）
SELECT count(*) AS orphan_count
FROM mem_observations o LEFT JOIN mem_sessions s
  ON s.content_session_id COLLATE "C" = o.content_session_id COLLATE "C"
WHERE s.id IS NULL;

SELECT count(*) AS raw_platform_source_mismatch_count
FROM mem_observations o JOIN mem_sessions s
  ON s.content_session_id COLLATE "C" = o.content_session_id COLLATE "C"
WHERE COALESCE(NULLIF(translate(btrim(o.platform_source, ' '), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ''), 'claude')
       COLLATE "C"
   <> COALESCE(NULLIF(translate(btrim(s.platform_source, ' '), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), ''), 'claude') COLLATE "C";

-- 手动 marker 与 sentinel 的默认分布；路径只分为保留值/其它值，不输出明文
SELECT (type COLLATE "C" = 'discovery' AND subtitle COLLATE "C" = 'Manual memory') AS has_manual_marker,
       (project_path COLLATE "C" = 'manual-memories') AS has_reserved_project_path,
       count(*)
FROM mem_observations
WHERE content_session_id COLLATE "C" = 'manual-memories'
GROUP BY 1, 2;

-- sentinel 下任何不符合完整 manual marker 的 observation 都是迁移阻断项；只输出计数
SELECT count(*) AS sentinel_non_manual_marker_rows
FROM mem_observations
WHERE content_session_id COLLATE "C" = 'manual-memories'
  AND NOT COALESCE((type COLLATE "C" = 'discovery'
                    AND subtitle COLLATE "C" = 'Manual memory'), false);

SELECT s.status,
       (s.project_path COLLATE "C" = 'manual-memories') AS reserved_project_path,
       (NULLIF(btrim(s.user_prompt, ' '), '') IS NULL) AS no_user_prompt,
       (NULLIF(btrim(s.last_assistant_message, ' '), '') IS NULL) AS no_assistant_message,
       count(DISTINCT o.id) AS observations,
       count(DISTINCT o.id) FILTER (WHERE o.type COLLATE "C" = 'discovery' AND o.subtitle COLLATE "C" = 'Manual memory') AS manual_marker_observations,
       count(DISTINCT o.id) FILTER (WHERE NOT COALESCE((o.type COLLATE "C" = 'discovery' AND o.subtitle COLLATE "C" = 'Manual memory'), false)) AS non_marker_observations,
       count(DISTINCT sm.id) AS summaries, count(DISTINCT p.id) AS prompts,
       count(DISTINCT pm.id) AS pending_rows
FROM mem_sessions s
LEFT JOIN mem_observations o ON o.content_session_id = s.content_session_id
LEFT JOIN mem_summaries sm ON sm.content_session_id = s.content_session_id
LEFT JOIN mem_user_prompts p ON p.content_session_id = s.content_session_id
LEFT JOIN mem_pending_messages pm ON pm.session_db_id = s.id
WHERE s.content_session_id COLLATE "C" = 'manual-memories'
GROUP BY s.status, s.project_path, s.user_prompt, s.last_assistant_message;

-- Extraction/DLQ 技术桶画像。结果仅按父 status 与 marker 形状聚合，不输出合成 key。
-- marker 数与 observation 总数相同才表示该组子记录全部符合候选类型；仍须核对来源 slug、
-- 空/混合子表和第 9.3 节的完整 classifier，不能仅凭 status 或单行 marker 自动归类。
WITH observation_profile AS (
    SELECT content_session_id,
           count(*) AS observations,
           count(*) FILTER (WHERE source COLLATE "C" = 'llm_extraction'
                              OR left(type COLLATE "C", 10) = 'extracted_') AS extraction_signal_rows,
           count(*) FILTER (WHERE source COLLATE "C" = 'dlq'
                              OR left(type COLLATE "C", 4) = 'dlq_') AS dlq_signal_rows,
           count(*) FILTER (WHERE source COLLATE "C" = 'llm_extraction'
                              AND left(type COLLATE "C", 10) = 'extracted_') AS extraction_markers,
           count(*) FILTER (WHERE source COLLATE "C" = 'dlq'
                              AND left(type COLLATE "C", 4) = 'dlq_') AS dlq_markers
    FROM mem_observations
    GROUP BY content_session_id
), summary_profile AS (
    SELECT content_session_id, count(*) AS summaries
    FROM mem_summaries GROUP BY content_session_id
), prompt_profile AS (
    SELECT content_session_id, count(*) AS prompts
    FROM mem_user_prompts GROUP BY content_session_id
), pending_profile AS (
    SELECT session_db_id, count(*) AS pending_rows
    FROM mem_pending_messages GROUP BY session_db_id
)
SELECT s.status,
       count(*) AS parent_sessions,
       count(*) FILTER (WHERE COALESCE(o.observations, 0) = 0) AS empty_observation_groups,
       count(*) FILTER (WHERE s.status = 'extraction'
                          AND COALESCE(o.observations, 0) > 0
                          AND o.extraction_markers = o.observations) AS all_extraction_marker_groups,
       count(*) FILTER (WHERE s.status = 'dlq'
                          AND COALESCE(o.observations, 0) > 0
                          AND o.dlq_markers = o.observations) AS all_dlq_marker_groups,
       count(*) FILTER (WHERE (COALESCE(o.extraction_signal_rows, 0) > 0
                               AND s.status <> 'extraction')
                          OR (COALESCE(o.dlq_signal_rows, 0) > 0
                               AND s.status <> 'dlq')
                          OR (COALESCE(o.extraction_signal_rows, 0) > 0
                               AND COALESCE(o.dlq_signal_rows, 0) > 0)) AS marker_status_or_kind_mismatch_groups,
       count(*) FILTER (WHERE COALESCE(o.observations, 0) > 0
                          AND o.extraction_markers + o.dlq_markers < o.observations) AS mixed_or_unmarked_groups,
       count(*) FILTER (WHERE COALESCE(sm.summaries, 0) > 0) AS groups_with_summaries,
       count(*) FILTER (WHERE COALESCE(p.prompts, 0) > 0) AS groups_with_prompts,
       count(*) FILTER (WHERE COALESCE(pm.pending_rows, 0) > 0) AS groups_with_pending
FROM mem_sessions s
LEFT JOIN observation_profile o ON o.content_session_id = s.content_session_id
LEFT JOIN summary_profile sm ON sm.content_session_id = s.content_session_id
LEFT JOIN prompt_profile p ON p.content_session_id = s.content_session_id
LEFT JOIN pending_profile pm ON pm.session_db_id = s.id
WHERE s.status IN ('extraction', 'dlq')
   OR COALESCE(o.extraction_signal_rows, 0) > 0
   OR COALESCE(o.dlq_signal_rows, 0) > 0
GROUP BY s.status;

-- feedback.session_db_id 是无 FK 的可选软引用；计数悬空值，不输出 UUID 或 metadata。
SELECT count(*) FILTER (WHERE f.session_db_id IS NOT NULL) AS feedback_session_refs,
       count(*) FILTER (WHERE f.session_db_id IS NOT NULL AND s.id IS NULL) AS dangling_session_refs
FROM observation_feedback f
LEFT JOIN mem_sessions s ON s.id = f.session_db_id;

-- Scope 回填规模：不 select 内容正文。手动无项目标记单列统计，不作为 project Scope 候选。
SELECT count(*) AS observation_rows,
       count(DISTINCT project_path COLLATE "C") FILTER (
           WHERE (content_session_id COLLATE "C" <> 'manual-memories'
                  OR (type COLLATE "C" = 'discovery'
                      AND subtitle COLLATE "C" = 'Manual memory'
                      AND project_path COLLATE "C" <> 'manual-memories'))
             AND project_path IS NOT NULL
             AND btrim(project_path, ' ') <> ''
       ) AS exact_project_scope_candidates,
       count(*) FILTER (
           WHERE content_session_id COLLATE "C" = 'manual-memories'
             AND type COLLATE "C" = 'discovery'
             AND subtitle COLLATE "C" = 'Manual memory'
             AND project_path COLLATE "C" = 'manual-memories'
       ) AS manual_default_candidates,
       count(*) FILTER (WHERE project_path IS NULL OR btrim(project_path) = '') AS ascii_blank_or_null_path_rows
FROM mem_observations;
```

Conversation 外部身份按大小写敏感的精确字符串比较。Preflight 必须核实 `mem_sessions.platform_source/content_session_id` 及所有对应 child 列、以及四张现有表的 `project_path` 列都使用确定性 collation；上面的目录查询结果必须逐列覆盖这些列，不能把缺行当成通过。确定性 collation 会在 locale 比较结果相同时以字节序区分文本；在 `server_encoding=UTF8` 前提下，旧列等值比较与新 Scope 的 `"C"` 原文比较对合法存储字符串保持相同的相等关系。若目标库任一相关列使用 nondeterministic collation、编码不是 UTF-8，或隔离副本的等值探针显示旧列与 `"C"` 不一致，必须停止迁移并提交兼容性决策；不得自动改用 nondeterministic Scope collation，也不得静默改变现有项目过滤语义。当前只读核验的本机 `claude_mem_dev` 是 `en_US.utf8` 数据库，四张 `project_path` 列均继承 deterministic `default` collation，大小写、组合字符与尾随空格探针对比 `"C"` 均一致；这只是本机开发库观察，不能替代任何部署目标的 preflight。Scope 的 `scope_key` 固定使用 `"C"` collation，resolver 必须以原文比较；`scope_key_sha256` 只用于固定宽度索引候选，不可取代原文比较。不能用默认 locale 下的分组/唯一性统计替代精确路径计数。

`raw_platform_source_mismatch_count` 是所有 Observation 与其旧外部 ID 父行的原始 `platform_source` mismatch 画像，不可单独作为分类结论：只有完整符合 §8.4/§9.3 整组条件的 standalone manual/extraction/DLQ 可不要求继承父 Conversation 的平台字段；其余 mismatch 都是阻断冲突。上面的 Extraction/DLQ profile 只用于定位候选组，不能替代 classifier；必须另外检查父来源 slug 合法性、混合 Conversation child，以及每一条 Observation 的 `platform_source` 值是否合法。对已分类的 extraction/DLQ，Observation 自身 `platform_source` 原值保留，不从合成父行复制。另需检查目标运行配置中的每个启用模板及其 resolved `sessionIdPattern`；仅列出 pattern 和跨 project key 碰撞计数，不输出凭据或项目路径。默认 lookup 按 Scope 隔离，即使旧 pattern 省略 `{project}`；受影响模板必须在阶段 B 记录新旧结果差异及接受依据。

路径以及 manual 父行 `user_prompt`/`last_assistant_message` 是否空白必须与 Java `String.isBlank()` 一致。上面 SQL 对这些文本仅统计 NULL、空字符串和 ASCII 空格组成的常见空值；preflight runner 必须再用同一 JVM 规则扫描所有相关值，只输出行 UUID/计数，不输出敏感路径/正文，并以 runner 结果作为是否可分类/回填的权威判定。

上面 union 查询若遇到各表字段状态不同（例如旧 schema 尚无 `platform_source`），先以实时 schema 生成等价分表查询，不要为了跑脚本手工 ALTER 目标库。还要逐表检查 `mem_summaries`、`mem_user_prompts` 的孤儿、重复 prompt key、pending 非终态数量、`observation_feedback.session_db_id` 指向候选技术父行的引用数、子记录 project/session project 不同的分布，以及重复 Scope path 的精确比较语义。对已经悬空的 feedback UUID，按 §4.2 的默认审计后置 NULL 规则处理。

### 9.3 Backfill 与审计表行为

实施代码应创建 migration audit/conflict 结构，并创建数据库内 durable runner-state/checkpoint 记录（以 run id 和迁移步骤/目标表为键），至少保存 source schema version、运行状态、每表最后提交的稳定游标、批次/行计数和更新时间。逐行 audit 至少记录 migration run id、source row UUID、旧 Conversation UUID/外部 ID/source/project、目标 Conversation UUID/Scope UUID/`platform_source`/`standalone_kind`、resolution code、审阅人/时间（如需人工处理）及迁移批次。数据改写、audit/conflict 行和 runner-state checkpoint 按第 8 步原子提交。不得记录 observation 正文或 embedding。

**V20 queue status expansion:** after checking all existing values, replace the actual `mem_pending_messages.status` CHECK so `observation` jobs retain the current V3 allowlist (including `skipped`) and `summarize` jobs additionally allow `result_persisted`. `skipped` remains legal for both message types with the semantics defined in §4.2. Keep `result_persisted` non-terminal; the CHECK and bridge worker must ship/activate together. Rehearse permitted and rejected message-type/status combinations before deployment, including preservation of the existing Observation LLM-skip path.

推荐执行顺序：

1. 取一致性快照/备份并验证可恢复；确认所有写入者（HTTP 实例、后台任务、导入任务、pending-message 消费者）均为 bridge 版本。否则在维护窗口冻结写入；在线模式必须保证每条 live write 原子填充新旧字段，并安排最终 catch-up 与写入屏障。
2. Scope upsert：先创建唯一 `(manual_default, manual-default-v1)`；对非 default standalone 和 Conversation-owned Observation 的每个精确 distinct 有效路径创建 `(project, path)` scope。完整 manual marker 且 `project_path = 'manual-memories'` 只构成候选，不足以决定 Scope；仅当完整 classifier 已确认父行是兼容技术桶、并有独立证据排除真实 Conversation 及显式 project 身份时，才映射到 `manual_default`，且不创建同名 project Scope。若身份来源不可区分，先记 `LEGACY_SENTINEL_IDENTITY_AMBIGUITY` 并阻断该组，不得先分配 Scope；相同路径的普通 Observation、extraction/DLQ 或有可靠显式 project 证据的手动行仍创建 project Scope。缺失/blank 路径不造假 scope，写入 conflict 并停。
3. 逐行回填 observation scope id。以 observation 自己的 project path 为准；session.project_path 只作审计对比，不覆盖 observation 的来源上下文。
4. 仅对未被 standalone classifier 识别的普通 conversation-owned children，从外部复合身份映射至 session UUID，并比较 platform source 的规范化结果。无唯一父项或来源冲突则冲突表记录，迁移停止，不重新推断 ID；status=`extraction`/`dlq` 的组先按 §8.4 第 5 步处理，不得在普通关联路径先赋 Conversation UUID。
5. Manual、extraction、DLQ 均只按 §8.4 阶段 D 第 5 步的**整组 marker + 父 Session 轮廓 + 无冲突关联行**规则分类；backfill runner 不得另写更宽松判断。每类 Scope 取 Observation 自身 project path；设置 `conversation_id=NULL`、准确 `standalone_kind`，保留其兼容 correlation key。V20 到 V21 期间旧外部 ID FK 仍要求保留技术父行作兼容载体。Manual 旧数据中“未提供 project”与显式 `manual-memories` 不可区分；仅在父行已被独立证据确认是兼容技术桶后，默认映射 `manual_default`，逐行输出 ambiguity 并保留可逆映射。若无法排除该 key 曾代表真实 Conversation，必须使用 `LEGACY_SENTINEL_IDENTITY_AMBIGUITY` fail-closed，不得以 default 映射代替身份决策。Manual 的 `source` 字段修复、Extraction/DLQ 字面 type/source markers 和空组冲突条件均按 §8.4 执行；不得把该修复误用于 `platform_source`。rehearsal 的可信显式覆盖须冻结并原样复用于正式迁移。分类未决的技术父组在解决前整体保留并整体排除 destructive refinement，不能只保护已识别 marker 行。
   其中 Manual 只允许把 NULL/空白 `source` 修复为 `manual`；已有其它非空 source 必须保留原行并进入冲突账本，不得为了满足新 CHECK 静默改写。此类 manual-shaped 冲突行在解决前继续被 refinement guard 排除，不能被低质量删除、重写或合并。
6. V20 不删除任何技术父行。V21 在所有数据约束通过后移除旧外部 ID FK，之后才按 §8.4 阶段 D 第 6 步严格条件归档并删除 manual/extraction/DLQ 伪父行；删除前验证无 pending UUID 引用、无 Summary/Prompt 引用及无 conversation-owned Observation 引用。将伪父行的 feedback 引用 row IDs 与原 UUID 写入审计，依赖已验证的 `ON DELETE SET NULL` 保留 feedback 行并清除软引用。所有 standalone Observation 保留各自兼容 key，即使 `conversation_id IS NULL` 也不能当成 FK 阻塞或 cascade 删除。来源非法、父行轮廓不符或任何关联不明时保留父行并阻断 cleanup；父来源不要求 `claude`，实际值写入审计。
7. 完成 Summary/Prompt 的 conversation UUID；所有未 keyed 历史 Summary 按行保留并保持 `summary_job_key=NULL`，不按每会话数量判冲突、不自动绑定到新 job；仅对已有 keyed 结果与 durable job/result pointer 做精确一致性核对。旧 `summarize` pending 按本节单独的状态/载荷判定升级或进入 conflict ledger。运行 orphan、source consistency、count 和 hash checks；V21 先建立父表 `(id, platform_source, content_session_id)` 唯一键和新的 UUID/复合一致性 FK，验证后再删除旧子表外部 ID FK，最后通过独立 cleanup 处理符合条件的技术父行。V21 后所有 standalone kind 均不依赖伪 Session。
8. 每批提交后记录游标和计数。Scope upsert/关系字段改写、对应 audit/conflict ledger 记录及数据库内 checkpoint 必须在同一有界数据库事务中提交；事务失败时三者全部回滚，不能留下“已迁移但无审计”或“游标已前进但行未完成”的状态。若另写本地进度文件，它仅作诊断，不得作为恢复真值；resume 以数据库内最后一个已提交 checkpoint 和行级状态为准。中断后从已提交批次继续；重跑不得复制 Scope、重复审计或改变已验证记录。

**空集合处理**：Backfill runner 必须按父 Session UUID 计算每种候选 kind 的 Observation 数量大于 0 且 marker count 等于该组总数，才可自动分类；0/0 不满足任何 kind，冲突账本记录相应 `*_without_marked_observations` 并保留父行。该检查与 §8.4 共用同一 classifier/测试向量，不得 SQL 与 Java 各自采用不同判据。

### 9.4 迁移验证不变量

- 除经批准的 manual/extraction/DLQ 技术父 Session metadata 归档，原 Observation/Summary/Prompt 主键、记录数、内容正文、embedding 向量、hash、created 时间、project 路径不变。
- 每个 Observation 有且仅有一个有效 Scope FK；每个 Conversation-owned Observation/Summary/Prompt 有且仅有一个正确 Conversation UUID。
- Manual observation：`conversation_id IS NULL`、`standalone_kind='manual'`、旧 sentinel 与 Scope 正确；Observation 的 `source` 为 `manual`，`platform_source` 缺失时兼容默认为 `claude`，只有已记录的显式平台来源保留其它合法 slug。未解决的 manual-shaped source 冲突及其同组子行不得进入约束阶段或 destructive refinement。Extraction/DLQ observation 分别有正确 kind、literal type/source marker 与 project Scope；三类均不出现在任何 Conversation 的 count/detail/delete 结果中。
- 每个 `conversation_id IS NOT NULL` 的 child，其 `platform_source` 与父 Conversation 规范化来源一致；每个 `conversation_id IS NULL` 的行都有合法 standalone kind 并满足该 kind 的约束，不要求存在父 Conversation。冲突已清零或明确留在隔离旧行，不可加约束假装解决。
- `observation_feedback.session_db_id` 无悬空 UUID；V21 前允许删除的 Conversation 已由应用层按 UUID 清空并审计该引用，V21 后已验证的 `ON DELETE SET NULL` 外键继续提供数据库保障；Observation 删除仍只级联删除该 Observation 自己的 feedback。
- 新增 composite external uniqueness 生效；验证 PostgreSQL 唯一约束/索引确实以预期列和 collation 工作；四张来源列均有有效 NOT NULL 与小写 ASCII slug CHECK。
- 迁移前后逐表总数、distinct 主键数、Conversation/standalone kind 分布、platform 分布、Scope 分布、project 分布、manual marker 数、extraction/DLQ marker 组数与 orphan 数可复核；任何差值必须有一一对应 audit row。

## 10. Preflight 交付件与操作命令

实施分支应新增并测试：

1. 只读 SQL 或脚本，如 `scripts/db/conversation-scope-preflight.sql`，输出数据库实例标识、Flyway/schema 状态、行数/分布/冲突计数。默认只打印聚合信息且绝不包含原始项目路径/外部 ID/正文；需行明细时要求显式 `--include-sensitive-details --output <protected-local-path>`，只导出定位所需的 UUID 与最少映射字段，拒绝 stdout/仓库内默认输出，并提示该文件属于敏感本地数据。
2. migration conflict ledger 的导出工具，列出冲突原因和稳定 row UUID，不自动修复。
3. migration rehearsal 脚本，使用隔离数据库副本及目标版本 Flyway 执行，保存开始/结束时间、锁/批次数、row counts、checksums、应用 build SHA。
4. 回滚验证脚本/步骤，真实启动兼容 bridge build 读取新 schema，并验证不读取/删除错误 Conversation。

发布前操作人应记录（不要把密码或 URL credentials 写入文档/日志）：

```text
target label:
target DB identity: current_database / server version / schema owner / sanitized host label
running backend image or jar SHA-256:
parent git commit:
webui gitlink and paired commit:
Flyway current version + history checksum result:
backup artifact SHA-256 + isolated restore result:
preflight report path (access controlled):
maintenance/traffic mode:
operator + reviewer:
```

实际构建与测试命令按实时 package scripts 运行。调查基线可参考：

```bash
cd backend && ./mvnw test
cd proxy && npm test
cd js-sdk/cortex-mem-js && npm test && npm run build
cd codex-watcher && npm run build
cd openclaw-plugin && npm run build
cd go-sdk/cortex-mem-go && go test ./...
cd python-sdk/cortex-mem-python && python -m pytest
cd webui && bun run typecheck && bun test tests
```

实际仓库的测试命令可能演进；运行前读对应 package manifest，并把具体命令和退出码写入验收记录。不能因为 `openclaw-plugin` 当前 `test` 脚本是占位成功就把它当作行为覆盖；需新增真实 ID/平台测试。

调查时的额外已知阻塞：当前 `proxy/package.json` 的 `test` 脚本在 Node `v24.6.0` 下运行 `npm test` 会因把 `node:test` 解析为本地文件而报 `MODULE_NOT_FOUND`。实施时先修正到该 Node 版本支持的 test-runner 调用方式，再确认测试发现范围覆盖新增用例；在修复前这项验证状态为 **failed**，不是“环境缺依赖”或“无测试”。

## 11. 验收测试矩阵

### 11.1 身份/并发单测与 DB 集成

- 同平台、同 ID、同重复请求只对应一条 Session UUID；并发首次创建无重复、无 500；重放幂等。
- 两个平台同外部 ID 各自创建两条 Session，彼此拥有不同 UUID，project path 可相同或不同，不覆盖来源字段。
- 同 ID 的不同平台工具事件使用独立限流窗口；相同 `(platform_source, content_session_id)` 继续共享窗口，429 的 `retry_after` 与准入检查读取同一身份键。
- SessionEnd 显式来源解析到正确 Conversation UUID；旧请求缺来源仍只解析 Claude。相同外部 ID 的不同平台可并行生成摘要，`summarizing` 状态与删除保护只阻止对应 UUID，不串扰另一平台。
- SessionEnd 对不存在的 `(platform_source, content_session_id)` 不创建 Session；删除后重放返回 not-found/conflict，且不创建 Session、pending 或 Summary。
- Context prior-session 查找在同项目、跨平台存在相同外部 ID 时只排除当前 Conversation UUID；另一平台的同名 ID 仍按既有项目/时间排序语义作为独立候选。
- 两个平台同外部 ID、同 tool/input 的 pending 事件互不去重；一个 Conversation 有 pending/processing work 时，不能误阻止另一个同 ID Conversation 的删除，反向也不得漏掉本 Conversation 的保护。
- 删除与 pending 入队并发：删除与入队/claim/retry/完成必须对同一 `mem_sessions.id` 使用共同 `FOR UPDATE` 协议；删除在锁内重查非终态 pending，入队不能在删除检查之后无锁插入；LLM 调用期间不持锁，最终 Observation 与 pending 转态在同一锁内事务完成。
- 删除与最终 Observation/Summary/feedback 写入并发：无论删除或完成事务先取得父行锁，后取得者必须重新读取状态、子队列和 feedback 软引用并得到安全结果；不得出现“删除返回成功但随后写入孤儿/被级联丢失”的路径。迁移 cleanup、导入、反馈采集和补偿任务同样必须遵守该协议或运行在已验证的写入屏障内。
- 删除与 SessionEnd/摘要 LLM 并发：SessionEnd 必须先提交 UUID 绑定的 `summarize` durable pending/在途状态；删除看到它时返回 409，摘要完成事务必须在同一父行锁内写 Summary 并结束 job。进程重启后只依赖持久 job 恢复，不得因内存标记消失而允许删除。
- 整 Conversation 删除 FK 顺序：已完成且有 keyed Summary 的 fixture 在事务内按规定先移除 terminal job/pointer，再删 Summary 和父 Session，满足复合结果 FK；若 job 非 terminal 必须 409 且全部行保留。单条 Summary 删除则不同，必须保留 terminal job 与删除 tombstone。
- 摘要运行期子项 mutation：摘要 job 非 terminal 时，Viewer 删除 Observation、普通导入/feedback 更新和 refinement 不得与 worker 交错；必须 409/defer。预先入队的 Observation pending 可在摘要输入冻结前排空；冻结后不再有普通写入者。job terminal 后恢复的 refinement 若父 Conversation 已删除须安全跳过，不能重建子行。
- 摘要 job 兼容/恢复：只有唯一、未 claim、零重试、时间戳空且 payload 完整的旧 `summarize` pending 可审计升级；零条才创建，多条或任何已有处理/结果歧义均 fail-closed 且原行保留。历史 unkeyed Summary 数量不参与旧 pending 判断。并发 SessionEnd 与相同 payload 重放复用唯一 job；不同 payload 不覆盖首次快照；运维重试使用同一 job/key 与 payload，不产生第二个 job。
- 摘要结果恢复：在不可变 `summary_input_snapshot` 与其 SHA-256 已提交后、LLM 调用前模拟崩溃，重启必须向 stub LLM 发送字节等价的 request；再在 Summary row、job result pointer 与 `result_persisted` 状态同一事务提交后模拟崩溃，重启按 Conversation UUID + `summary_job_key`/结果引用从该非终态完成 finalize，不重复调用生成 LLM 或插入行。Snapshot hash 不符时必须 fail-closed。另测空 Observation 输入被持久标记为 `skipped`、不生成 Summary 且解除删除保护；验证 Observation `skipped` 既有路径仍有效，并验证 Observation pending 拒绝 `result_persisted`。SSE 发送前崩溃时 WebUI reconnect REST reconcile 后可见；job replay 不重发事件，SSE 不宣称 exactly-once。
- 摘要结果关系约束：尝试把 Conversation A 的 job 指向 Conversation B 的 Summary、或指向不同 `summary_job_key` 的 Summary，必须由 V21 复合 FK/等价触发器拒绝；Viewer 删除先清 pointer 并写 tombstone 后才可删结果。
- 历史摘要兼容：每个 Conversation 有零条、一条或多条 `summary_job_key IS NULL` 的旧 Summary 时，全部保留且不因此阻断创建唯一的新 canonical SessionEnd job；原行始终 unkeyed。只有重复 keyed rows、无对应唯一 job/result pointer 或 keyed 关系不一致时才进入 conflict ledger。
- 摘要删除/重放：对应 job 尚未 terminal 时删除 keyed Summary 返回 409；terminal 后删除须原子写 `result_deleted_at` tombstone、清空 job 的结果引用并保留 terminal job；重复 SessionEnd 不重建结果。删除 unkeyed 历史 Summary 不修改新 job。并发删除、SessionEnd replay、worker finalize 均由同一 Conversation 行锁串行化。
- 老请求不带 source 解析 Claude；已存在 Codex 行不能被无 source 请求或 Claude 请求改写。
- 子表建立 relation 时，Conversation UUID、external ID、platform 三者不匹配被拒绝；Viewer 删除只影响给定 UUID。
- 外部 ID 去重包含平台；导入相同平台重复为 duplicate，不同平台同 ID 为 independent insert。
- `/api/sdk-sessions/batch` 的新 `sessionRefs` 能在一个请求中返回两个平台相同外部 ID 的两条 Session；旧 `contentSessionIds` 只返回 Claude 记录。`export-memories.sh` 不会因裸 ID 去重而丢掉其中一条，也不会为 manual/extraction/DLQ standalone 行请求或生成伪 Session metadata。
- 查询按 project Scope 跨平台共享；Viewer source filter 仍隔离展示。user_id 不被误当作安全边界。
- 内容 hash 在同一 exact Scope 与 30 秒窗口内仍去重，不同 Scope 的相同 hash 不互相抑制；project 变体的测试覆盖 ingest 路径。
- Refinement 边界：通用 refinement 只处理 `standalone_kind IS NULL` 的 Conversation-owned 行；同外部 ID 的不同平台 Conversation 不互相合并，并按 `conversation_id` 分组。manual/extraction/DLQ standalone 行均不得进入低质量删除、stale/overdue 候选、LLM 重写或合并；未来若要处理手动记录，必须另立可预览、按记录授权的流程。合并后的 `refined_from_ids` 只含真实 Observation UUID，源行删除后仍可从保留行和审计追溯。

### 11.2 手动记忆与 Viewer

- `save_memory(project=A)` 与普通 A 项目 observation 共用 project Scope，手动项 conversation UUID 为空。
- `save_memory(project=B)` Scope 与 A 不同；修改/删除一条不会改其它项目。
- 无 project 写入 manual_default Scope、wire sentinel/string type 不变；Observation 的 `source` 默认/固定为 `manual`，而 `platform_source` 缺省仍按兼容规则为 `claude`，显式平台来源只改变宿主平台标记，不改变 Scope 或 Observation 的 `source`。
- Extraction 结果和 DLQ 记录分别落入 Observation 自身 project Scope，不创建 Conversation；类型/source marker 完整的历史组可迁移为 standalone，空组、混合子项、非法来源或 marker 不完整必须进入 conflict 并阻断 cleanup。
- Extraction prior/latest/history 查询用 Scope、kind、correlation key 与 type 联合限定；相同合成 key/type 在不同 Scope 不串读，DLQ 仍按其自身项目过滤。针对未含 `{project}` 的自定义 `sessionIdPattern`，验证 legacy 与新 scoped lookup 的差异，并确认该预期行为变化可由新读 flag 回退。
- Extraction 候选扫描按 exact Scope 保持 source filter、`created_at_epoch DESC`、候选 limit 与每用户 batch cap；默认不扫描 `standalone_kind='manual'`，模板显式 `include-standalone-manual: true` 时才纳入。跨平台同外部 ID 的 Conversation 按 UUID 获取 user 分组，允许进入的 standalone 候选归入 `__unknown__`，不通过 sentinel 查询 Session。
- V20 期间 bridge 仍保留 sentinel 父行为旧外部 ID FK 提供兼容；V21 删除旧 FK 后 cleanup 只归档/删除伪父行，不删除任何 standalone manual Observation。cleanup 可中断重跑，重复执行不改动已归档数据。
- 同一可重入 cleanup 对 manual/extraction/DLQ 三类父行逐类验证；合法父行只归档/删除载体，不删除 standalone Observations；任一类不符合证据时只阻止该组并保留原始行。
- 伪父行被 cleanup 时，引用它的 `observation_feedback` 行保留、`session_db_id` 变为 NULL，原 row IDs/UUID 可从迁移审计追溯；删除普通 Conversation 时同样不能留下指向已删除 UUID 的 feedback。
- manual-default observation 不计入任何会话目录、不出现在会话详情、不因 Session delete 被删除；普通时间线可展示它。
- 旧 WebUI/旧 API 对 content session string 的预期保持；新 WebUI 依据 `conversation_id:null` 不构造 session ref。
- `manual-memories` 字面值也可能是真实外部会话 ID；不得只凭该字符串判定伪 Session。测试和 preflight 必须同时核对 manual marker、父行元数据轮廓、非 manual 子项与 pending，并在证据不符时阻止删除、保留原行。
- 同时验证 legacy sentinel 查询仅返回 `conversation_id IS NULL` 的手动行；若存在外部 ID 同为 `manual-memories` 的真实 Conversation，按其 UUID/完整外部复合身份仍可单独读取，二者不混合。

- `save_memory` 并发请求在 V20 bridge 和 V21 目标路径都不会丢 Observation、重复创建技术父行或把不同 project/platform 的记录串到一起；旧技术父行的 get-or-create 使用唯一键/锁或等价原子方案，遇到真实 Conversation key 冲突按 `STANDALONE_COMPAT_PARENT_CONFLICT` fail-closed。

### 11.3 Adapter 稳定性

- Cursor prompt/tool/stop 同一个有 ID 对话始终映射同一 `(cursor,id)`；ID 缺失按真实 Cursor payload/文档走确定策略，不用 handler 时间戳。
- Codex watcher 重启、历史 JSONL 重读、session metadata 延迟、文件轮转场景稳定，不因 watcher 内存 Map 清空创建第二会话。
- OpenClaw session_start、compaction、restart、同一 session key 恢复与并发事件使用同一 ID；不以每次插件进程启动时的新时间戳分叉。
- Claude、SDK、Spring 集成显式或兼容默认来源正确；多个 SDK 在 request wire format 中实际发送配置值。
- WebUI alias 黄金用例与 Java slug validator 一致；当前归一到 `codex`、`cursor`、`claude`、`kimi`、`antigravity-cli` 的适配器别名及 `opencode` 规范 slug均有请求/SessionRef round-trip 测试，未知 ASCII slug 可通过，空值/default 和非法 Unicode/空白边界符合 §1。
- 缺少可靠来源 key 时，功能明示无法关联的结果，不落入 `Date.now()`、随机值或项目路径猜测。

### 11.4 API/WebUI/E2E/迁移

- 用同一组 `/repo/app` 与 `/repo/./app` 存量 Observation 建立迁移前后对照：`generateContext`、SearchService、Viewer 直接 project filter 保持 raw-string 命中；`/api/context/preview` 保持 `validateProjectPath()` 后的命中。验证 Scope resolver 自身不规范化、不合并路径；无匹配 Scope 的读不创建空行；多项目查询逐路径保留结果集与排序。
- 对 project-filtered `/api/stats`、quality distribution、search-by-file、ExpRag quality/source/fallback、low-quality/stale/overdue refinement、scheduled project enumeration 做迁移前后结果集对照；refinement 还要断言 manual/extraction/DLQ 行在低质量删除、stale/overdue、scheduled quick-refine、LLM 重写和合并路径中均为零命中。`manual_default` 和同名 project Scope 在兼容键 `manual-memories` 下各只计入一次。ExpRag 的 userId 分支验证仍只返回所选 Conversation UUID 的 observations，不因 Scope 共用而扩大结果；Session/Summary stats 保持原语义。
- 对 `AgentService` 内容 hash 去重验证同 Scope 重复仍被抑制、不同 Scope 相同 hash 不被误合并；全仓 `ObservationRepository` project predicates 搜索结果逐项记录为 Scope query、刻意保留的非-Scope 条件、或无调用死代码。
- Migration fixtures 覆盖 manual/extraction/DLQ 正常整组、空 Observation、部分 marker、manual 非空非 `manual` source、混合 marker、marker 与父 status 不符、manual 父行 `user_prompt`/`last_assistant_message` 任一非空白（含 Unicode 空白边界）、关联 prompt/summary/pending、合法/非法 platform source；所有不完整组均进入冲突账本且不能被误赋 conversation UUID 或 cleanup。Feedback fixture 覆盖有效、跨 Conversation 触发、悬空 `session_db_id`；悬空 UUID 默认写入审计并置 NULL 后才能验证 FK；删除作为触发 Conversation 的父行时，对仍保留的 feedback 行将其触发引用设 NULL，删除 Observation 时 feedback 按其 Observation FK 级联，以及伪 Session cleanup 后 feedback 行保留且原 UUID 可从审计追溯。不得把 feedback 的触发 Conversation 与 Observation 所属 Conversation 混为一谈。
- Viewer catalog item count 精确对应 conversation UUID；detail query 与分页不串同 ID 的其他平台。
- Viewer DELETE 对 `content_session_id` 含 `/`、`%`、`?`、`#`、`+`、Unicode、首尾空格时能按原值完整 round-trip；query/body 入口定位正确 UUID，另测 path 兼容入口不被误当作 opaque 通用传输。
- Session detail 与 user PATCH 对上述 opaque ID 使用规范 query 入口并解析正确 UUID；旧 path 别名只覆盖安全字符 ID、缺 source 时仅查 Claude；query-param 合同测试锁定参数名与默认值。
- 整会话删除：请求平台与 ID 解析 UUID，非终态仍 409；成功后只删 UUID 子项并在 commit 后发出包含 platform + external ID 的 SSE。
- V21 前后都验证真实 Conversation 删除不会留下 feedback 悬空 UUID：删除前应用层更新/审计删除父行作为触发引用的 feedback，删除后外键 `ON DELETE SET NULL` 再次核对；Observation 自身删除仍按 Observation FK 级联其 feedback，伪 Session 受保护或按同一审计规则 cleanup。跨 Conversation 触发的合法 feedback 不因其 Observation 所属关系不同而被误清空。
- SSE 新 observation row 有 `conversation_id`（UUID/null）与 external identity；Observation 在服务端归入正确 Scope，但 wire 不发送 `memory_scope_id`/Scope UUID。任何 standalone kind 都不触发错误 Session catalog patch。
- API 文档 field-surface 检查对比 OpenAPI/非 OpenAPI/MCP/actuator 等既有口径；确认硬契约 `hasMore`、`updateFiles` 没有变化。
- API、SSE、SDK 与 export DTO 均不得意外暴露内部 `memory_scope_id`；对所有 standalone kind 均断言 `conversation_id` 是 JSON null、`standalone_kind` 精确且旧 key/type 保持。
- 新增/修改 controller query 参数时更新 `BackendQueryParamContractTest` 的 `VERB /path` 预期表，并验证 live OpenAPI 与文档一致；测试不仅要新增参数通过，也要能在参数名意外改动时失败。
- WebUI 配套 gitlink 中静态 Viewer 的 TypeScript 类型、Viewer 单元测试和浏览器测试覆盖 cross-platform duplicate、manual/extraction/DLQ standalone 类型与无 SessionRef 呈现、分页、删除、SSE。最低命令为 `bun run typecheck:viewer`、`bun test tests/viewer tests/ui/viewer` 和 `node scripts/build-viewer.js`；WebUI Worker/SQLite/Chroma/Cloud Sync/MCP/CLI 的全量测试只能作为额外回归，不能替代本矩阵的 Java 后端、数据库迁移或 API 验收。
- Import/export v2 使用明确的 `conversation_id: null` 与 `standalone_kind` 表达 manual/extraction/DLQ 无 Conversation；旧 `content_session_id/session_id` 字符串继续保留各自 sentinel/correlation key。
- 导出脚本使用真实 Conversation 的复合 `sessionRefs`；fixture 覆盖同外部 ID 的 Claude/Codex 两条 Session 都被导出、导入为独立 Conversation，standalone 行保留 `conversation_id:null` 且不生成 SessionRef，旧 Claude-only batch 请求仍保持兼容。
- 导入 round-trip 覆盖 standalone 三类不创建 Conversation/伪父 Session；跨库 UUID 和 Scope UUID 不被复用，目标 Scope 按记录 project path 重建。
- V20 回滚演练区分 bridge 从未写入 V20-only 数据的 additive-schema 情况与已创建 durable summarize job/standalone 新写入的情况；后一情形必须拒绝 pre-V20 二进制，只允许 V20-aware bridge，退回更早版本必须实测快照恢复与审计重放。
- 迁移在从实际基线创建的隔离副本执行，演练正常路径、冲突中止、重跑、中断恢复、分别匹配 V20/V21/V22 的回滚兼容 build、备份恢复。
- 所有 HTTP/E2E 测试打印并 assert `SERVER_URL`、数据库 identity、jar/image SHA 和 WebUI gitlink；在姊妹服务/端口上即失败，不允许“健康检查 200”单独证明目标正确。

## 12. 发布顺序与不可逆门

| 顺序 | 产物/操作 | 放行条件 |
|---|---|---|
| 1 | 阶段 A 止损版本，保护 manual/extraction/DLQ 技术桶免于整会话删除 | 三类删除保护、Session-start 冲突保护测试通过并部署；不改 schema。 |
| 2 | 只读 preflight、目标身份核验、备份与隔离恢复演练、conflict review | 尚未执行任何结构迁移；逐表 count/FK/platform/path 冲突均有解释，目标数据库/制品身份明确，执行人与复核人签字（门 B）。 |
| 3 | 阶段 C identity 兼容代码制品（pre-V20 schema） | 门 B 通过；制品不含 V20 新表/列映射或访问；在当前 schema 启动及旧 wire/读写测试通过；旧全局唯一仍作为并发护栏，跨平台重复 ID 仍关闭。 |
| 4 | V20 expand + bridge/preflight 制品（不含 V21/V22） | 步骤 3 已部署并稳定；CI 与目标基线核验完成；expand 不删列/约束，Flyway 最高只到 V20。 |
| 5 | 单实例 backfill + 应用 bridge 双写 | runner 账目及门 D 通过；行数/FK 不变量通过；V21 尚未自动执行；其他副本未并行跑 runner。 |
| 6 | 发布含 V21 的 constraints 制品 | 仅在门 D 的 backfill/冲突/审计证据获复核后应用；不通过时不得发布/执行 V21。 |
| 7 | 部署 paired WebUI/API 与 adapter/SDK 契约制品，`conversation_identity_v2` 保持关闭 | 新 UI 已验证可消费目标 backend；旧 wire/catalog 兼容仍可用；WebUI gitlink 与后端配套。 |
| 8 | 开启新读路径 shadow compare | 未批准差异归零；仅保留事前量化、审计且稳定的 manual default rehome 与 extraction Scope 隔离差异；对外 wire 与 catalog 仍保持 legacy 兼容。 |
| 9 | 受控 cutover：cleanup 技术父行、启用新 wire/catalog、开放 standalone 写路径 | V21 约束与 cleanup rehearsal 通过；维护/流量切换已安排；新旧 API/UI 不会在清理边界并行服务；feedback 和父行审计可恢复。 |
| 10 | 关闭旧裸 ID 查询并观察回滚窗 | 全面请求/日志无裸 ID ambiguity；bridge build 已演练。 |
| 11 | 发布/执行 V22，移除 Session 外部 ID 全局唯一约束 | 门 F 全部通过；明确旧二进制不得直接回滚。 |
| 12 | 后续移除旧列/双写（可选单独发布） | 回滚窗关闭、所有部署环境完成升级、备份与审计策略批准。 |

如停机维护比双写更安全，可以在阶段 4 采用写入暂停，但仍需逐行审计、恢复演练和同样的验收门。无论采用不停机或停机，不能把 DDL 成功当作语义成功。

## 13. 回滚与故障处理

### 13.1 按 V20/V21/V22 的 schema 阶段回滚

- **V20 已执行、V21 尚未执行**：若 bridge 从未启用且确认没有任何 V20-only 新列写入、standalone 新语义写入或 durable `summarize` job，扩展 schema 仍为 additive，可继续使用 pre-V20 应用。bridge 一旦写入新关系/Scope 数据或创建 durable summary job，回滚目标只能是理解 V20 列、`result_persisted`/`skipped` 状态及 job 恢复语义、并仍维护旧字段兼容的 V20-aware bridge；仅确认旧外部列完整并不足以让旧二进制安全，因为旧 worker 不识别 durable summarize job，可能将其误标 failed、漏做 finalize 或在 SessionEnd 重放时重复生成。若必须退回更早版本，先停止写入，恢复迁移前快照并按审计/写入日志重放；不得仅降级 jar。新列和 backfill 结果可留在库中，不执行 `flyway clean` 或手工删列。任何旧版写入都会使既有门 D 证明失效，之后执行 V21 前必须重新部署 bridge、catch-up 全部增量、重跑最终 sweep/不变量核验并重新通过门 D。
- **V21 已执行、V22 尚未执行**：虽然旧全局唯一仍阻止跨平台重复外部 ID，但新增 Conversation/Scope 约束已经要求写入新列；只能回到经过 V21 schema 验证、继续写新旧字段的 bridge 版本。不能回退到 V20 前的旧二进制，也不能仅关闭读 feature flag 就认为其写入兼容。若必须回到旧应用，先停止写入，恢复迁移前快照并按审计/写入日志重放，或通过单独审核、演练过的恢复迁移解除新约束；不得手工删约束。
- **新写路径须能由内部 UUID 重建旧字段**；切回旧读路径前先确认审计和差异。backfill 在扩展列中产生的结果可保留；如需重跑，使用 run id/checkpoint 和幂等键。

### 13.2 开放跨平台重复 ID 之后

- 只能回滚到理解复合 identity 和 UUID relations 的 bridge build。
- 不可直接回滚旧二进制：裸 `findByContentSessionId` 可能返回多行，旧唯一插入会失败，按裸 ID 的删除可能跨平台误删。
- 如必须退回更老代码：停止写入，做新快照，审计并处理重复外部 ID，再恢复迁移前兼容数据库快照；迁移后新增 observations/summaries/prompts 必须用审计/导出重放，不能丢弃。
- 若迁移遇冲突，先保留原表与 conflict ledger，关闭 feature flag，回到兼容读写；不强行给冲突行分配 Scope、不删除“多余”行。

### 13.3 服务安全阀

- `conversation_identity_v2` 与 `memory_scope_read_v2` 应分别有可观测 feature flag。身份 flag 在 V20 nullable 回填阶段保持关闭，不输出未经回填的 NULL 关系；只有 V21/cleanup 完成且 paired WebUI/API 已就绪后才 cutover。flag 关闭后的旧语义只在保留兼容父行/约束的数据库状态启用，cleanup 后不得回退到会把 standalone sentinel 当作 Conversation 的旧 UI/二进制。
- 日志/指标记录无法解析 identity、platform mismatch、Scope not found、shadow diff、migration batch counts、pending 队列数量；禁止记录 prompt/observation 正文或 embedding。
- 任一影子查询差异、session deletion row count 超出目标、summary/prompt 跨平台命中或 DB conflict 非零，都关闭切换 flag 并暂停 destructive step。

## 14. 非阻断运行期未知项与默认处理

架构决策已由 §1 固定。以下是必须测量的环境事实，不是让后续实施者重新设计模型：

| 运行期未知 | 默认处理 | 可逆边界 |
|---|---|---|
| 实际数据量/索引构建时间 | preflight 在目标副本测量；超时/锁风险高则分批或安排维护窗口 | expand 可保留；drop constraint 之前可回旧版。 |
| 某行 `platform_source` 为空/空白 | 按本规划冻结的兼容规则归 `claude`；审计 `platform_source` repair 数量。若同时修补历史 manual 行的 `source`，另记 `source='manual'` 的修复数量 | audit 保留旧值；未通过审计不得 set NOT NULL。 |
| 普通 Conversation Observation 的 `platform_source` 与父 Session 不同 | 默认父子身份冲突，停 migration；不选 parent 或 child 静默覆盖。只有完整 classifier 证明属于 manual/extraction/DLQ standalone 的组不要求继承父平台字段，按各自 frozen repair/preserve 规则处理；Observation 的 `source` 是记录类型字段，不参与这项父子身份一致性判断 | 保持该行原状，逐行显式决议后重跑。 |
| manual/extraction/DLQ 技术桶出现非 marker 子项、空 Observation 组、非空 manual 父提示文本或 pending/summary/prompt 引用 | 停止该组自动分类和删除；导出 row IDs 与最少状态，先查明真实来源 | 阶段 A 后整桶删除已保护；结构迁移未清理旧会话前可暂缓。 |
| 已启用 extraction `sessionIdPattern` 未含 `{project}`，导致旧 key 覆盖多个精确 project path | 默认按 Observation 自身 project Scope 隔离新 prior/latest/history；记录受影响 template/key 碰撞数与影子差异，放量前可回退旧 lookup flag | 不重写或删除历史 Observation；若未来需要跨项目 extraction，另行设计显式共享 Scope。 |
| project path 含大小写/斜杠/点路径/符号链接差异 | 按精确存储字符串建立独立 Scope，不合并；查询前只沿用各入口现有预处理（例如 context preview 的 `Path.normalize()`），Scope resolver 不再规范化 | 后续可单独引入可版本化 canonicalization/显式 alias，不改本次既有 key；不同入口现存的规范化差异在此之前保留并测试。 |
| Cursor/Codex/OpenClaw 无可靠外部会话 ID | 先检查平台 payload/历史文件并实现稳定 adapter 映射；若仍不可得，拒绝 session-owned 写入并给出 `SESSION_ID_UNAVAILABLE` 诊断，不伪造 ID、不把普通 observation 悄悄丢到其他 Scope | payload/映射具备后，可从平台重放；不回填没有可验证身份的事件。 |
| user_id 多值/缺值 | 不改 Scope 分区，不声称租户隔离；按现有单服务行为继续 | 将来的 auth/tenant 项目可建立 Scope v2 与数据授权迁移。 |
| export consumer 对新增字段敏感 | v2 schema additive；旧字段/类型不变；由真实 SDK 消费测试确认 | 保留旧 export version，按调用方升级。 |

仅当 preflight 证明某个事实无法被表结构或业务数据判别，且会造成数据丢失/串租户时，才需升级为产品阻断决策；届时冻结 destructive migration，保留原数据，不替用户猜。

## 15. 文档与配套上游注意事项

- 更新 `docs/API-zh-CN.md` 与英文对应段落：平台+外部 ID、缺省 Claude、Conversation UUID relation、manual sentinel 与 extraction/DLQ correlation value、所有 standalone kind 的 `conversation_id:null`、Scope 项目过滤/跨平台共享、删除语义、Import/Export 版本。
- 更新 `docs/ARCHITECTURE-zh-CN.md` 及英文版：从“Session ID 是唯一关系键”更新为内部 UUID relation + 外部复合引用 + Project Scope。
- 更新 Viewer、Cursor、Codex、OpenClaw、SDK 和部署迁移指南，明确缺稳定外部会话 key 时的行为。
- API 兼容检查时同时查 WebUI 与 Proxy：`hasMore`/`updateFiles` 是硬契约，不要只 grep WebUI。
- `webui/` 的选择由 Java 后端能力和静态 Viewer 契约决定。记下 WebUI 源仓库提交 SHA、父仓库 gitlink SHA、backend commit，以及实际部署资源（`viewer.html`、`viewer-bundle.js`、`assets/`、被部署的 `icon-*.svg` 和 `*.webp`）的 SHA-256 manifest。只有这一组合已通过 Viewer typecheck/unit/browser tests、静态 bundle 构建及目标 Java 后端上的 catalog/feed/delete/SSE/API contract tests 才算配套。没有兼容提交就保留当前 gitlink，不猜测。Worker/SQLite/Chroma/Cloud Sync/MCP/CLI 的测试结果不构成 Java 迁移或配套证据。
- 参考仓库 `claude-mem-java` 是**实验性移植，不保证生产可用**。只借鉴分离概念的启发；不要复制它的 migration、代码、测试通过数字或生产结论。上游本机 checkout 的私有路径不得写入 Git 文档。
- 新增 `.md` 按 `docs/drafts/` 的 draft 生命周期管理；实现稳定后更新/晋升或按 archive 规则归档，遵循 [doc-management skill](../../.agents/skills/doc-management/SKILL.md)。

## 16. 完成定义与执行记录

### 16.1 最终完成定义

只有全部满足下列条件才关闭此项：

1. Manual、extraction、DLQ Observation 均无 Conversation、归入正确 Scope、可按类型检索/管理，且无法被 Session 删除误伤。
2. 外部会话在平台内唯一，数据库内部关系用 UUID；不同平台相同外部 ID 独立工作。
3. 所有 session-owned 表、查询、去重、目录计数、SSE、导入导出与删除均使用 UUID 或完整复合外部引用；全仓裸 ID 命中逐项有结论。
4. project Scope 保持每个现有读入口最终 project-path 谓词的查询语义并跨平台共享；Scope resolver 与存量映射均不新增 canonicalization、user/worktree 隔离或 path alias 合并。
5. Adapter 重启/重复事件下稳定；不再用每事件或每进程时间戳伪造会话身份。
6. 迁移账目逐表一致、无静默丢行、无未解释 orphan/冲突、恢复副本验证成功。
7. API wire 保持既有 key/type/兼容默认；WebUI 与 backend gitlink 配套；SDK、MCP、Proxy、E2E 目标身份都得到验证。
8. rollback bridge、安全阀、迁移审计和中英文文档齐全；任何生产执行都有明确审批、操作者和恢复点。

### 16.2 实施时追加的执行记录

本表在规划阶段留空；实际实施者逐项填写，不得把预期当作结果。

| 项目 | 实际值/证据位置 |
|---|---|
| 实施 commit 范围 | `325c817..f7b865e`（9 个提交，见文末清单） |
| 基线父仓库/WebUI SHA | 父仓库 `325c817bbf580c0c1451212aa0ca472d7afa7089`；WebUI gitlink `72e7804b13f89b03177f746867e0dd341fb12c8a` |
| 目标环境/DB identity | 仅隔离副本：pgvector/pg16 容器，`server_encoding=UTF8`、`en_US.utf8`。**未接触任何生产或业务数据库** |
| Flyway before/after | before: V19（最高版本）；after: V20（一次性迁移，schema+回填+约束收紧同一事务） |
| 备份 SHA / restore proof | 不适用：用户明确决定迁移模型为「可接受失败、手动介入、长时间停机，不考虑回退」。迁移失败即整体回滚，库回到迁移前状态 |
| 各表 before/after counts | 干净 fixture：13 条观测 → 9 conversation + 2 manual + 1 extraction + 1 dlq，13/13 有 scope，0 孤儿 |
| Conflict ledger | 冲突不再入长期账本：任何不可证明行 `RAISE EXCEPTION` 中止整个迁移并列出冲突行，人工处理后重跑。rehearse 验证了 6 类冲突全部中止且完整回滚 |
| 单元/集成/SDK/WebUI/E2E 命令与结果 | 见 §16.3 |
| Shadow diff/window | 不适用：无在线切换，无影子读 |
| bridge rollback proof | 不适用：不考虑回退 |
| 操作人与复核人 | 待真实部署时由操作人执行；本轮回填仅在隔离副本执行 |

### 16.3 实施验证证据

| 验证项 | 命令/方式 | 结果 |
|---|---|---|
| 后端单元/集成测试 | `cd backend && ./mvnw test` | **282 通过 / 0 失败 / 13 跳过**（跳过的 13 个是需真实数据库的 `MemoryScopeServiceDatabaseTest`） |
| 真实数据库测试 | `MEMSCOPE_TEST_DB_URL=... ./mvnw test -Dtest=MemoryScopeServiceDatabaseTest` | **13 通过 / 0 失败**（含 `ON CONFLICT` 并发、`COLLATE "C"` 精确性、digest 碰撞 fail-closed） |
| 构建产物 | `./mvnw -DskipTests package` | **成功**，`cortex-ce-0.1.0-beta.jar` |
| 迁移重放（空库） | `scripts/db/rehearse-conversation-scope.sh` | **21 通过 / 0 失败**：V1–V19 重放、V20 提交+不变量、跨平台重复 ID 合法、二次执行被基线守卫拒绝、冲突 fixture 中止且完整回滚 |
| 应用启动（V20 schema） | 隔离库从空库迁移到 V20 后启动打包 jar | **成功启动**；并修正了仅在运行期暴露的 4 个缺陷（JPQL `COLLATE`、陈旧 `@Query`、双构造器、裸 ID 去重残留） |
| 真实 API 端到端 | 对运行中实例发 HTTP | 同 `session_id` 跨平台产生**两个不同 UUID**；两侧目录各自计数互不串扰；删除 Claude 侧后 Codex 侧完整保留、无孤儿；直连观测落到 conversation + project scope |
| WebUI 类型检查 | `bun run typecheck:viewer` | **通过** |
| WebUI 单元测试 | `bun test tests/viewer` | **86 通过 / 0 失败**（含 3 个新增 standalone 语义用例） |
| WebUI 浏览器测试 | `bun test tests/ui/viewer --concurrency 1` | 见 §16.4 基线对比 |
| Viewer 构建 | `node scripts/build-viewer.js` | **成功**；产物已部署到 `backend/src/main/resources/static`，SHA-256 manifest 记于提交 `f7b865e` |

### 16.4 基线与新增问题区分

浏览器套件在**未改动的基线**上即为 **105 通过 / 6 失败**，6 个失败全部是既有问题：
- `session-keyboard-actions` 的 Enter/Space 两个用例（headless Chrome 经 CDP 派发按键超时）
- 4 个依赖 `Bun.serve` 流式响应的用例（`Bun.serve` 默认 10s `idleTimeout` 导致的超时）

本次改动**未新增浏览器失败**；键盘用例已加显式 opt-out（`CLAUDE_MEM_NATIVE_KEY_TESTS=0`），默认仍启用。

### 16.5 实施提交清单

```
325c817  docs: add conversation and memory scope refactor plan        (基线)
46e467b  db: add V20 additive expansion and read-only preflight
02cca55  feat(identity): add canonical session identity and memory scope primitives
0c7bd8d  feat(entity): expose conversation identity on the wire, keep scope ids internal
cd6b972  feat(summary): add durable, resumable SessionEnd summary job
d33efbd  feat(migration): add fail-closed classifier and checkpointed backfill runner
63b5502  db: make V20 a single atomic migration with fail-closed conflicts
8985fa3  refactor(session): resolve conversations by composite identity and internal UUID
9a0e90b  refactor(scope): route observation reads through memory scope predicates
a8cc175  fix: make the refactor start and serve on the real V20 schema
f7b865e  build: pair WebUI gitlink and static viewer with the v2 identity wire   (HEAD)
```

**执行模型偏离说明**：本规划原定「在线零停机 + checkpoint 续跑 + 影子读 + 回退演练 + V21/V22 分段」。实施中用户明确裁定该部署为单实例自托管、可接受迁移失败与长时间停机、**不考虑回退**，因此改为「单事务一次性迁移 + 冲突即中止 + 跨平台一次放开」。回填 runner 与 conflict ledger 的长期形态因此被移除；fail-closed 判定保留为 `RAISE EXCEPTION`。`ConversationScopeBackfillRunner`/`BackfillCliMain`/`SummaryJobService` 仍保留在代码库中（前者已被 V20 的 SQL 内联取代，后两者为 durable summary job 的服务层），但迁移不再依赖 runner 续跑。


### 16.6 Scope 轴残留 site 分类（每处有结论）

ObservationRepository 中所有 project-scoped Observation 查询均以 `memory_scope_id` 解析 Scope，不再以 Observation 的展示用 `project_path` 决定归属；单项目和多项目谓词对 project key 使用 `COLLATE "C"` 精确比较。唯一兼容例外是请求路径精确等于 `manual-memories` 时，同时纳入字面同名 project Scope 和固定 `manual_default` Scope。Conversation-only 查询仍限制在非空 `conversation_id`，不会因这个兼容例外读入 standalone 行。

这里不再固定写查询条数：共享 SQL 谓词和派生查询会随实现调整，精确数量不是验收依据。以下残留**刻意保留**原始 `project_path` 语义，因为它们属于 Summary/Session（conversation-owned）读路径，规划 §4.3 第 7 条明确此类不改成 Scope 计数：

| Site | 读者 | 结论 |
|---|---|---|
| `SummaryRepository:20` `findAllPaged` | Viewer summaries 列表 | conversation-owned；保持 summary.project_path 既有规则 |
| `SummaryRepository:45` `findByProjectPathAndPlatformSource` | ContextService summaries | 同上 |
| `SummaryRepository:56` `findByProjectLimited` | MCP `recent` | 同上 |
| `SessionRepository:74` `findByProjectPathAndStatus` | context cache owner | Session 元数据；既有规则 |
| `SessionRepository:87` `findLastCompletedSessionWithMessage` | prior-session message | Session 元数据；当前会话排除已改用 Conversation UUID |
| `SessionRepository:100` `findConversationIdsByUserIdAndProject` | ExpRag userId 分支 | Session 选 UUID，观测按 UUID 取（Scope 轴在下游已应用） |
| `UserPromptRepository:28` | Viewer prompts 列表 | conversation-owned；既有规则 |

会话轴残留为零：`findByContentSessionId`/`deleteByContentSessionId`/`findByContentSessionIdIn` 均已删除；仅存的两处合法用途是 `findStandaloneByCorrelationKeyAndType`（standalone 兼容 key + kind，非会话身份）与 `findByPlatformSourceAndContentSessionId`（复合身份）。

**最终实施结论**：本次重构不能缩减为“V20 加一个 Scope 表”或“把唯一索引改成复合索引”。实际采用用户裁定的单事务 V20 模型：迁移前只读盘点，迁移内完成 fail-closed 判定与回填；遇到不能证明安全的数据形状即整体失败，允许停机和人工介入，不设计回滚、checkpoint、影子读或分阶段发布。实现仍须保护伪会话删除、固定 Scope 语义、以已有 UUID 作为关系真值、补齐所有身份路径并保持 wire 兼容。WebUI gitlink 可从干净 clone 获取且指向配套版本仍是独立发布门。
