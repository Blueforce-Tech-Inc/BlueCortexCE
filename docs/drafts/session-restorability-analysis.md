# 会话可还原度分析：记忆系统记录了什么、丢失了什么

> **Purpose**: 沉淀对记忆系统「能还原多少一次真实会话」的结构化结论，作为后续采集契约、存储结构、检索接口改进的输入。
> **Audience**: 维护采集（proxy / OpenClaw 插件）、存储（迁移与实体）、检索（`/api/search`、`/api/context/inject`）链路的开发者与产品决策者。
> **Status**: 调研结论。基于 commit `e99748a3` 的实测报告提炼，**未实施任何代码、迁移或配置改动**。

---

## 结论

**这套记忆系统是「做过什么」的可靠记录，不是「为什么这么做」的记忆。**

文件路径、代码改动前后片段、命令输出、用户提示词原文可以被还原；决策理由、失败尝试、中间推理、事件级耗时不在采集面内，任何检索入口都拿不到。

---

## 1. 采集面逐项对照

### 1.1 生命周期入口

| 生命周期 | Claude Code proxy | OpenClaw 插件 | 证据 |
|---|---|---|---|
| SessionStart | `POST /api/session/start` | `session_start` | `proxy/wrapper.js:514-560`；`openclaw-plugin/src/index.ts:392` |
| UserPromptSubmit | `POST /api/ingest/user-prompt` | **无对应 hook** | `proxy/wrapper.js:1088-1124` |
| PostToolUse | `POST /api/ingest/tool-use` | `tool_result_persist` | `proxy/wrapper.js:588-609`；`openclaw-plugin/src/index.ts:489` |
| SessionEnd | `POST /api/ingest/session-end` | `session_end` / `agent_end` | `proxy/wrapper.js:1048-1076`；`openclaw-plugin/src/index.ts:524,551` |

### 1.2 逐项对照

| 信息项 | Claude Code proxy | OpenClaw 插件 | 证据 |
|---|---|---|---|
| session_id | 原样写入 `mem_sessions.content_session_id` | 同端点，原样 | `proxy/wrapper.js:532-540` |
| 项目路径 | 优先 `project_path`，缺失回退 `cwd`，真实绝对路径 | **把 `projectName` 当 `project_path` 上报**，同名项目时间线被合并 | `SessionController.java:128-145`；`openclaw-plugin/src/index.ts:398,416` |
| platform_source | 不传，默认 `claude` | 同 | `V18__add_platform_source.sql:5` |
| 会话起止时间 | 毫秒 epoch，仅会话级 | 同 | `V1__init_schema.sql:4-16` |
| 用户提示词原文 | 保存原文（上限 100000 字符），剥离 `<private>`，整段私有则跳过 | **完全不采集** | `IngestionController.java:283-286`；`proxy/wrapper.js:1103-1112`；`Constants.java:31` |
| `mem_sessions.user_prompt`（会话首条意图） | 实测恒为 NULL（SessionStart 先建行，后续 prompt 不更新） | 同 | `SessionManagementService.java:158-169`；`SessionController.java:167` |
| 工具白名单 | **仅 `Edit/Write/Read/Bash`**，其余工具 hook 侧直接 `exit(0)` | 除 `memory_*` 外全部工具 | `proxy/wrapper.js:594-596`；`openclaw-plugin/src/index.ts:489-517` |
| 工具入参 | 完整 JSON 存 `mem_pending_messages.tool_input`（TEXT），仅剥隐私标签 | 同 | `proxy/wrapper.js:600-607`；`V1__init_schema.sql:83-84` |
| 工具输出 | 完整 JSON 入库；**进 LLM 前截断 4000 字符** | **只取第一个 `tool_result/text` 块并 `slice(0,1000)`** | `proxy/wrapper.js:601`；`AgentService.java:398-399`；`Constants.java:37`；`openclaw-plugin/src/index.ts:157,503` |
| 助手中间文本（工具调用间的说明） | 丢弃，只在 SessionEnd 读一次 transcript | 取决于插件侧实现 | `proxy/wrapper.js:434-487` |
| 助手最终文本 | 只拼接最后一条 `text` 块；`thinking`/`tool_use` 丢弃，剥 `<system-reminder>` | 由插件传入 `last_assistant_message` | `proxy/wrapper.js:465-471`；`openclaw-plugin/src/index.ts:551-570` |
| thinking / reasoning | 丢弃 | 丢弃 | `proxy/wrapper.js:465-468` |
| token 用量 / 成本 | 丢弃（`discovery_tokens` 是本地估算） | 同 | `AgentService.java`（无 provider 用量字段） |
| 工具执行耗时 / exit code / tool_use_id | 丢弃（payload 不含） | 丢弃 | `proxy/wrapper.js:603-609` |
| 图片 / 附件 / 二进制 | 丢弃 | 丢弃 | `proxy/wrapper.js:601` |
| 重复调用（同工具 + 同入参） | 同会话已存在非 `failed` 的同 hash 记录时静默丢弃 | 同 | `AgentService.java:157-161`；`PendingMessageRepository.java:129` |
| 只读探索（Read/Grep/Glob）的知识 | 抽取 prompt 明确要求输出 `<skip>` | 同 | `backend/src/main/resources/prompts/init.txt:33-44` |
| 决策理由 / 方案取舍 | 不采集；抽取 prompt 只允许「可从 tool input/output 验证的事实」 | 同 | `backend/src/main/resources/prompts/init.txt:134-138`；`AgentService.java:389-408` |

### 1.3 进入存储前已被降维的部分

| 原始信息 | 降维结果 | 证据 |
|---|---|---|
| 工具入参/输出 | 压成 1–5 条 facts + 1 句 subtitle（≤24 words）+ 可选 1 句 narrative | `prompts/init.txt:136,224,236` |
| `Read` 读到的整段文件内容 | 通常不进入 observation | `prompts/init.txt:33-44` |
| 事件与 observation 的对应关系 | 无法反查是哪次 tool 事件、哪个模型、哪条 prompt | `mem_observations` 溯源字段实测为 NULL（见 2.3） |

---

## 2. 存储面：成品层与原料层

### 2.1 两层存储

| 层 | 表 | 内容 | 是否有 API | 保留策略 |
|---|---|---|---|---|
| 成品层 | `mem_sessions`、`mem_user_prompts`、`mem_observations`、`mem_summaries` | 抽取/概括后的结构化记录 | 有（`/api/sessions`、`/api/prompts`、`/api/observations`、`/api/summaries`） | 覆盖（`RetentionService.java:76-80`） |
| 原料层 | `mem_pending_messages` | `tool_input`/`tool_response` 原始 JSON（TEXT） | **无** | **不覆盖**；随会话级联删除 |

### 2.2 原料层的「意外留存」

| 事实 | 证据 |
|---|---|
| 处理完不删除：`processed` 与 `failed` 行都继续留在表中 | 实测：无 LLM 会话 4 条 `failed`、有 LLM 会话 `processed(retry=2)` 仍存在 |
| 保留策略只清 prompts / observations / summaries | `RetentionService.java:76-80` |
| 没有任何 API 暴露原始载荷 | `docs/API.md` 无对应端点 |
| 会话删除时级联清空，长期不可依赖 | 表结构外键与实测清理行为 |

因此原料层不是产品承诺的存储能力，而是队列实现细节的副作用。

### 2.3 成品层的实测保真度

| 检查项 | 实测结果 | 证据 |
|---|---|---|
| observation 溯源字段 | `source`、`extracted_data`、`generated_by_model`、`prompt_number`、`step_number` 全为 NULL | `V14__add_observation_source.sql:8,12`；实测 SQL |
| `files_read` | 实测为空，即使 trace 中确实读过文件 | 实测 observation 行 |
| `files_modified` | 仅一次被模型填上；属模型可选输出而非结构化必然结果 | 实测 observation 行 |
| 压缩比 | 2 个工具事件只产出 1 条 observation（含一次 `<skip>`） | `prompts/init.txt:33-44`；实测 |
| summary 输入 | 只有 observation digest + 最后一条 assistant 文本，**看不到用户原话** | `SummaryGenerationService.java:281-297,333-345` |
| summary `files_read`/`files_edited` | 恒为 NULL；`prompt_number=0`、`created_at` 为 NULL | `SummaryGenerationService`（从不填这两个字段）；实测 |
| 验证失败的证据 | 已发生的测试命令可能被判定「只查看输出」而 skip，只在 summary 剩一句概括 | `prompts/init.txt:41`；实测 `restore-llm2` |

---

## 3. 还原度评估

### 3.1 口径

样本为一次受控会话，把 agent 侧本可观测的信息切成 **30 个原子信息单位**（一个单位 = 一条可独立核对的事实/字段），按信息价值人工加权。

| 组 | 单位数 | 内容 | 权重 |
|---|---|---|---|
| A 会话外壳 | 5 | session id、项目、平台、开始时间、结束时间 | 0.10 |
| B 用户回合 | 6 | 两条提示词 ×（原文、编号、时间） | 0.25 |
| C 助手回合 | 2 | 中间说明、最终回复 | 0.05 |
| D 工具事件 | 8 | Edit/Bash 各（工具名、入参、输出、时间） | 0.10 |
| E 代码内容 | 4 | 文件路径、old、new、diff/原文件 | 0.25 |
| F 派生知识 | 5 | 根因理由、测试结果、失败尝试、后续步骤、改动文件清单 | 0.25 |

三档口径：**DB 直读**（可 SQL 读队列表）、**仅 HTTP API**（sessions/prompts/observations/summaries/search/context-inject）、**仅 context inject**（SessionStart 实际注入 agent 的文本）。

### 3.2 结果

| 组 | DB 直读（有 LLM） | HTTP API（有 LLM） | context inject（有 LLM） | DB 直读（无 LLM） | HTTP API（无 LLM） |
|---|---|---|---|---|---|
| A 会话外壳 | 5/5 | 4/5（缺结束时间） | 3/5 | 5/5 | 4/5 |
| B 用户回合 | 6/6 | 6/6 | 0/6 | 6/6 | 6/6 |
| C 助手回合 | 1/2 | 0/2 | 0/2 | 1/2 | 0/2 |
| D 工具事件 | 7/8（缺耗时/退出码） | 0/8 | 0/8 | 7/8 | 0/8 |
| E 代码内容 | 4/4 | 1/4 | 1/4 | 4/4 | 0/4 |
| F 派生知识 | 3/5 | 2/5 | 2/5 | 0/5 | 0/5 |
| **未加权** | 26/30 = 86.7% | 13/30 = 43.3% | 6/30 = 20.0% | 23/30 = 76.7% | 10/30 = 33.3% |
| **加权** | **86.3%** | **49.3%** | **22.3%** | **71.3%** | **33.0%** |

### 3.3 口径局限

1. **分母是人工加权**：30 个单位来自一次 2 工具会话。真实长会话中 D 组随工具数线性增长，observation 数量不随之线性增长，86% 只是「单事件粒度完整」时的上限，不是通用常数。
2. **86% 依赖意外留存**：`mem_pending_messages` 无保留策略、无 API、随会话级联删除。若只按产品承诺的存储（observations + summaries + prompts）计算，DB 直读与 API 口径基本重合（约 43–49%）。
3. **有 LLM 时的观测质量与模型相关**：`files_modified` 仅一次被填、`files_read` 为空、content 出现 `**Title:**` 包装漂移，E/F 组的命中是运气而非契约保证。
4. **无 LLM 的 71% 是「临时可读」**：会话一旦被删除，原料层随会话消失，长期还原度降为 0（此时不存在 observation/summary）。

---

## 4. 检索可用性实测

### 4.1 查询命中情况

| 查询 | 结果 | 原因 |
|---|---|---|
| `缓存 TTL 单位 秒` | 命中，top-1 = `Fixed build cache TTL resolution in BuildService` | 语义 + 全文均可用 |
| `cache TTL seconds regression test` | 命中，top-1 = `Added BuildServiceCacheTtlTest regression coverage` | 英文 query 正常 |
| `BUILD SUCCESS` | `count=2` | 命令输出中的关键结果被 facts 保留 |
| `BuildServiceCacheTtlTest` | `count=2` | 测试类名进入 facts |
| `别再引入分钟的中间变量`（只存在于用户提示词） | `count=0` | `/api/search` 只检索 observations，不检索 `mem_user_prompts` |
| `为什么用秒不用分钟`（理由类） | `count=0` | 理由从未进入任何可检索字段 |

`/api/search` 只加载 `ObservationEntity`（`SearchService`；`ViewerController.java:363-380`）。

### 4.2 各入口能给出什么

| 入口 | 实际返回 | 缺什么 |
|---|---|---|
| `POST /api/session/start` → `context` | 项目级 timeline：observation 的 title/subtitle/content/facts、文件分组头、最后一条 summary 的 request/completed/learned/next_steps | **用户原话、diff、工具输出、失败尝试、中间推理、事件级时间** |
| `GET /api/context/inject` | 同上；只渲染 title/subtitle/content/facts 与 summary 的四个字段 | 同上（`ContextService.java:748-775,653-656`） |
| `GET /api/search` | 只能命中 observation 文本/facts | 提示词、原始输出、理由 |
| `GET /api/sessions` | session id、项目、平台、开始时间、条目数 | 结束时间、状态、逐条内容 |
| `GET /api/prompts?contentSessionId=...` | 提示词原文、编号、时间 | 不参与 search / context，需调用方主动取 |
| `GET /api/observations?contentSessionId=...` | 结构化 observation（facts/concepts/files_modified） | 工具名、原始输入输出、diff、来源/模型 |
| `GET /api/summaries` | request/investigated/learned/completed/next_steps/notes | `files_read`/`files_edited` 恒 NULL；无用户原话 |

**结论**：凭记忆系统重建新会话上下文，得到的是「一份由模型二次概括的项目活动摘要」。要恢复可执行上下文，必须回到 `/api/prompts` 与原料层；前者不参与检索与注入，后者没有 API。

---

## 5. 无法还原的信息类型

| 信息类型 | 状态 | 原因 |
|---|---|---|
| 用户的语气、追问的上下文关系 | 部分丢失 | 提示词逐条保存，但与工具轮次无结构化绑定；observation 中 `prompt_number` 为 NULL |
| 关键决策与理由 | 丢失 | 理由只存在于 assistant 文本或 thinking，两者均不采集 |
| 失败尝试 / 死路 | 丢失 | 失败命令若被判定「只查看输出」会 skip，且无结构化失败记录 |
| 中间推理过程 | 丢失 | transcript 只在 SessionEnd 读一次，只取最后一条 `text` |
| 代码最终态 | 只能还原被编辑片段 | 仅限 4 种工具命中过的文件片段；后续其他工具或人工改动不可见 |
| 命令输出细节 | 有条件保留 | proxy 原文在原料层；OpenClaw 只留 1000 字符；observation 只留模型选中片段 |
| 真实执行耗时 / 退出码 / 重试 | 丢失 | payload 不含，只有入队时间 |
| 其他 agent 事件（Grep/Glob/Task/MCP/子代理） | 丢失 | hook 白名单直接退出 |
| 模型推理与 token 成本 | 丢失 | 采集面不含 |

---

## 6. 改进建议（全部未实施）

价值排序依据：B/E/F 三组权重各 0.25，缺口最大。

| # | 建议 | 预计收益 | 代价 | 风险 | 状态 |
|---|---|---|---|---|---|
| ① | 抽取与摘要接入用户原话与助手叙述：observation 抽取带最近 1–3 条用户提示词；summary 用完整 assistant 文本；修复 `mem_sessions.user_prompt` 恒 NULL | B 组注入口径 0/6 → 可用；加权注入 22% → 35% 量级 | 每次抽取多几百 token | 提示词注入面扩大；需沿用 `<private>` 规则；成本上升 | 未实施 |
| ② | 原始工具载荷升为一等存储并开放按会话导出 API：新增 `mem_tool_events`（含 exit_code、起止时间、patch），补保留期与压缩 | API 口径 E 组 1/4 → 4/4、D 组 0/8 → 7/8；加权 API 49% → 约 80% | 存储显著增长（Bash 输出可达 MB 级） | 明文密钥/大文件入库；需迁移与保留策略；WebUI 契约不受影响（新增端点） | 未实施 |
| ③ | observation 补溯源字段、`files_read`/`files_modified` 从 trace 机械推导 | 文件清单稳定可控；可回答「这条记忆来自哪次调用」 | 小（写字段 + 解析 trace 路径） | 历史数据仍为空；`extracted_data` 需脱敏 | 未实施 |
| ④ | 捕获中间 assistant 文本 + 显式 `POST /api/ingest/decision` 通道 | F 组理由项 0 → 1；是重建「为什么」的唯一可靠路径 | hook 改动 + 存储增长；需 agent 侧约定 | 与「只记录可验证事实」的现有抽取契约冲突，需新契约而非放宽校验 | 未实施 |
| ⑤ | 修 OpenClaw 三处硬缺口：新增 user-prompt 采集、`project_path` 改传 `workspaceDir`、输出截断 1000 提升或按工具分级 | OpenClaw B 组 0/6 → 6/6；输出细节不再被截断 | 小（插件升级 + 配置） | 长输出放大存储/隐私风险，需与②的保留策略一起上线 | 未实施 |

**暂不建议**：把 Grep/Glob/Task 等全部工具纳入采集（噪声与抽取成本高，现有契约会大量 skip）；把时间戳精度提到事件级（payload 本身不含，需改 hook 协议，收益仅 1 个单位）。

---

## 7. 证据成立的边界

| 边界 | 说明 |
|---|---|
| 样本规模 | 一次受控会话（2 个工具事件）+ 两次有 LLM 会话（各 2 个工具事件）；不是真实长会话统计 |
| 无模型情形 | 无 LLM 组只有原料层数据，71% 属「临时可读」，会话删除后归零 |
| 原料层依赖 | 86% 与 71% 均包含「可读 `mem_pending_messages`」这一非产品承诺能力 |
| 未做全量对照 | 未与真实历史会话做逐条比对；30 个单位与权重为人工定义 |
| 模型相关性 | 有 LLM 的抽取质量受模型与 prompt 影响，`files_read` 为空、格式漂移已在实测中出现 |
| 时效性 | 结论对应 commit `e99748a3`；采集白名单、截断长度、SQL 路径若已变更需重新核对 |

**调研元信息**：素材为仓库外实测报告（基于 commit `e99748a3`），临时 pgvector 容器与临时后端实例均已清理；本文件仅做提炼与结构化，不代表新的实测。数组件路径、接口清单见 [API.md](../API.md)；文档放置规则见 [doc-management](../../.agents/skills/doc-management/SKILL.md)。
