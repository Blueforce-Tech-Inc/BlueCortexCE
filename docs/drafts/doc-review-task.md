# 文档审查与改进 — 任务指令

> **Purpose**: 定义文档准确性、完整性、双语一致性和轮换审查规则。
> **Updated by**: 定时项目维护任务。
> **Update rule**: 每轮只审查一个方向；结果写入统一健康检查报告；历史达到增长阈值时归档。

## Current Review Rotation

- **轮换顺序**: API 文档 → SDK README → 设计文档 → 架构文档 → 用户指南
- **最近完成**: 架构文档（2026-10-02 六十九轮，一项。DOC-1（P2）**PostgreSQL 端口写错**（`docs/ARCHITECTURE.md:1100` 与 `docs/ARCHITECTURE-zh-CN.md:1100`）——Network Security 表格把 PostgreSQL 固定写成 `127.0.0.1:5432`，但实际取决于运行方式。三个事实依据全部实地核对后才改：`docker-compose.yml:35` 映射 `"${POSTGRES_PORT:-5433}:5432"`（Docker 路径宿主端口是 **5433**）、`README.md:308` 明写 5433 是为避开本地已占用的 5432、`backend/src/main/resources/application.yml:74` 默认 `jdbc:postgresql://127.0.0.1/claude_mem_dev` 未写端口即 5432。现改为两端口并列，并补说明：Docker 路径必须用 `SPRING_DATASOURCE_URL` 覆写指向发布出来的端口，否则后端 5432 默认值会去找一个并不存在的服务器。**写作过程中自查出一处错误**：说明段落最初插在表格的 PostgreSQL 行与 Proxy 行之间，把表格从中间截断、`| Proxy |` 行会孤立成普通文本；两种语言均已重排为「表格完整 → 段落说明」。核实无误未改：Data Privacy 的四条隐私标签（`<private>`/`<claude-mem-context>`/`<system_instruction>`/`<system-instruction>` + entirely-private skip）与实际行为逐条相符。结构校验 `/tmp/arch_parity.py` 全通过：49 个标题、层级序列一致、围栏平衡、锚点可解析）
- **最近完成**: 用户指南（2026-10-02 七十轮，一项。DOC-1（P2，已修）**PostgreSQL 端口分野未说明**，波及 `docs/TESTING.md`+`-zh-CN` 与 `docs/DEVELOPMENT.md`+`-zh-CN`（第 168 轮架构文档那处修正的同源延伸）。两份指南都假定 5432——**对原生安装和它们各自给出的 `docker run -p 5432:5432` 而言是正确的，不是错误陈述**；真正的缺口是项目自己的 `docker compose up -d` 把库发布在宿主机 **5433**（`docker-compose.yml:35`）而两处都没提，于是 compose 用户照 TESTING 排障命令会在 5432 上**再起一个空数据库**、数据却在 compose 容器里。已在 TESTING 的「前置条件」与「PostgreSQL 连接失败」、DEVELOPMENT 的 Docker 替代方案三处加注（中英双语），并在 TESTING 变更日志记一笔。**两处怀疑经核实被推翻、未改**：「10 个本地 E2E 套件」属实（`run-all-e2e.sh` 自身标注 `1/10`–`10/10`，变更日志的「12」指第 3 节脚本表格行数）；`phase3-acceptance-test.sh` 的「15 test functions」属实（套件报告的 25 是这 15 个函数下的断言数）——差点改掉两个正确的数字。校验：四个文件 U+FFFD=0、围栏平衡、中英标题层级逐位一致（TESTING 35/35、DEVELOPMENT 177/177））
- **最近完成**: API 文档（2026-10-02 七十一轮，回到轮换起点，一项。DOC-1（P2，已修）**`/api/logs` 的 `lines` 钳制行为两版均未记录**——`LogsController` 中为 `Math.min(Math.max(1, lines), 10000)`，越界值被静默钳制、**从不返回 400**，而参数表只写「Maximum lines to return / 默认 1000」。活体实测：`?lines=0` 与 `?lines=-5` 均返回 `returnedLines: 1`，`?lines=50000` 返回 `10000`，`?lines=3` 返回 `3`。同时补 `returnedLines` 永远不超过钳制后的 `lines`、`totalLines` 统计被搜索文件的全部行数（可能不止一个：优先读今天、不足才回退昨天，`files` 列出实际读取文件）。**一处自我更正**：初稿写「跨日时 `returnedLines` 可能超过 `lines`」，读控制器后不成立（`subList(size - validatedLines, size)` 已限制住），**删除而非发布**。核实无误未改：API.md 声称的「67 live endpoints」**属实**（从控制器 `@*Mapping` 重新推导正是 67）；EN/ZH 端点覆盖对称；`/api/logs` 活体返回键名 `[files, logs, path, exists, totalLines, returnedLines]` 与示例**完全一致**；第 166 轮的导入三桶语义与 `projectPath` 校验均已记录。**端点对拍脚本自身出过两次错**（第 163 轮同类）：方法级 `@RequestMapping` 是**替换**类级路径而非追加（产出 `/api/api` 幻影重复）；且我剥离了代码块——而英文版恰恰把请求行放在代码块里，EN 侧因此只提到 34 个而 ZH 侧 84 个；修正后差集里「12 个缺失」经抽查也全部证伪（正则过严，文档把动词与路径分行放置））
- **最近完成**: SDK README（2026-10-02 七十二轮，一项。DOC-1（P2，已修）**四家 README 都没有记录「空更新会被拒绝」这一契约**——而这正是本轮 DEMO-1 的知识前提：不知道它，调用方就会把客户端错误报成服务端错误。四家 SDK README（中英共 8 个文件）统一新增「Empty Updates Are Rejected / 空更新会被拒绝」小节，置于 Wire Format 之前：规则、各家异常类型、以及**为什么要拦**（不设置任何字段的 PATCH 在 wire 上是一次静默 no-op，调用方会以为写入成功）。Python 一家额外说明 `extracted_data={}` 的两种形式差异（数据类形式被 `to_wire()` 省略故属空更新，kwargs 形式会真的发出）。结构校验：8 个文件 U+FFFD=0、围栏平衡，`/tmp/readme_parity.py` 全通过、中英标题层级逐位一致（Go 20/20、Java 43/43））
- **最近完成**: 设计文档（2026-10-02 七十三轮，两项。DOC-1（P2，已修）**DLQ 的文档描述与实现完全脱节**——六份设计文档与两份面向用户的功能文档都称 DLQ 使用 `type=extraction_failed` 并有**定时重试任务**，两者都不成立：实现写的是 `type="dlq_"+templateName` + `source="dlq"`（存进专门的 `dlq:extraction` session），**不存在** `@Scheduled` 的 DLQ 作业，`findByTypeGlobal` 被声明但**无任何调用方**，条目仅供人工检查。已改 `11.md` §11.3（规范本体）、`00-quick-ref.md` 管道图、`18.md` 命名空间表、`14/15/25.md` 加指向说明，以及 `docs/structured-extraction.md` + `-zh-CN.md`。DOC-2（P2，已修）**两处既有的围栏缺陷**（HEAD 处即存在，与上一项无关）：`15.md:420` 是孤立的重复围栏，把随后的散文吞进代码块；`7.md:206` 围栏提前闭合，导致 207–209 行代码掉出代码块、210 行成孤立围栏。两处分别删除多余围栏，**全目录 31 个文件复验后无一不平衡**，链接 0 断裂、无文件超 50KB。**一处自我纠错**：改管道图时两次破坏对齐（`→` 多字节、且 `│` 被放在填充之后而非之前），已按同族行宽度重排）
- **下一方向**: 架构文档（七十四轮）
- **新增待决**: `docs/drafts/` 下 3 个文件超 50KB（`go-sdk-design.md` 195KB 等），50KB 规范原文仅约束 `phase-3-design/` 子目录，需明确适用范围或安排拆分
- **Pending 状态**: 文档问题清单已清空（0 项待处理）
- 完成本轮后必须把“最近完成”和“下一方向”更新在本节；详细历史保存在归档文件中。

## Pending Doc Issues

- ~~**DOCKER_README 中英文结构漂移**~~（2026-09-30 用户指南轮发现，**同日已解决**）：两文件已统一为相同的 14 个 H2 结构且顺序一一对应（H3/H4 数量一致）。变更：EN Commands 合并了 ZH 健康检查/日志查看/停止服务的命令组并新增 3 个故障排查小节（服务启动失败/数据库连接问题/端口冲突，译自 ZH）；ZH 删除了与「使用 Dockerfile 构建」重叠的「构建本地镜像」节（本地镜像构建命令并入前者），测试覆盖从 4 行表格改为与脚本实际验证项一致的 11 项清单（`docker-e2e-test.sh` 中无 MCP 测试项，原表格的 "MCP 服务测试" 声明不实）；生产注意事项统一为 4 条（ZH 原 6 条中 2 条与安全建议重复）。更正：先前记录称 "EN 缺本地开发节" 不准确 —— EN `## Development` 即其对应节。
- ~~**API 文档中英标题风格分歧**~~（2026-09-30 发现，**2026-10-02 关闭**）：EN 用描述式 H3 + 请求行代码块，ZH 用路径式 H4。**已决策为有意差异，不再统一**：路径式标题在中文语境下更自然，两版对每个端点都给出相同方法与路径，2026-10-02 用脚本核验 67 个生效端点 EN/ZH 集合完全相等。已在 `API.md` 与 `API-zh-CN.md` 顶部各加一段结构说明（含“不要单方面对齐”的告诫），避免后续轮次反复重开此项。

## 执行规则

- 每次唤醒只审查一个方向，总时长不超过 15 分钟。
- 发现文档问题直接修复并提交；发现代码问题必须当场修复，或记录到 `docs/drafts/backend-review-findings.md`。
- 每个发现的问题必须有落点，不能只写在报告中。
- 开始时检查 `http://127.0.0.1:37777/api/health`；服务未运行时遵循 `docs/drafts/cron-combined-task.md` 的启动流程，后端固定使用专用端口 `37777`。
- 本轮方向、发现、修复、验证和 commit 写入 `docs/drafts/health-check-task.md` 的综合报告。

## 质量标准

### 双语对照

- 重要文档必须有英文版和中文版。
- 中文版使用 `-zh-CN` 后缀，例如 `API-zh-CN.md`。
- 中英文内容、章节顺序和示例必须一致，并在顶部互相链接。

### 准确性和完整性

- 端点路径、HTTP 方法、参数、返回格式、版本和依赖必须通过实际代码或构建配置验证。
- API 文档覆盖所有 Controller 端点；SDK README 覆盖所有公共 API 方法；设计文档与实际 Service/Entity 一致。
- 示例必须可运行，链接和锚点必须有效；无法确认的信息不要写入。

## 审查范围

1. **API 文档**：`docs/API.md` 与 `docs/API-zh-CN.md`，对照 Controller 和 OpenAPI 注解。
2. **SDK README**：Java、Go、Python、JS/TS SDK README，核对源码签名和示例。
3. **设计文档**：`docs/drafts/phase-3-design/`（拆分后的子文档，入口 `index.md`；根 `phase-3-design.md` 仅为指针文件）与 `phase-3-design-walkthrough.md`，对照实际实现和测试脚本。引用具体章节时应指向对应子文档（如 §2.2 → `2.md`），不要只指向指针文件。
4. **架构文档**：`docs/ARCHITECTURE.md`、中文版本、`backend/README.md` 和根 README。
5. **运维/用户指南**：部署、配置、测试、故障排查和 Docker 文档。

## 活动文档维护

每轮开始和结束执行 `bash scripts/doc-growth-check.sh`。任一活动文档超过 1000 行或 102400 字节时，保留规则、状态和未解决项，将已解决历史归档到 `docs/archive/YYYY-MM-DD_<descriptive-name>.md`，更新 `docs/archive/README.md`，并重新检查。归档文件创建后不得修改。

## Archived History

完整审查历史截至 2026-05-05 保存在 [`2026-09-30_doc-review-history.md`](../archive/2026-09-30_doc-review-history.md)。
