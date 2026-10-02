# 文档审查与改进 — 任务指令

> **Purpose**: 定义文档准确性、完整性、双语一致性和轮换审查规则。
> **Updated by**: 定时项目维护任务。
> **Update rule**: 每轮只审查一个方向；结果写入统一健康检查报告；历史达到增长阈值时归档。

## Current Review Rotation

- **轮换顺序**: API 文档 → SDK README → 设计文档 → 架构文档 → 用户指南
- **最近完成**: SDK README（2026-10-02 六十五轮，三项修复。DOC-1（P1）Python 与 JS 的 README（EN/ZH 共 4 份）仍把 `refined_from_ids` 与四个 JSONB 列归为一类并写「后端把这些 JSONB 列序列化成字符串」——正是第 163 轮在 API 文档里更正掉的错误，在 README 里依然活着；结论（→ `list[str]`/`string[]`）是对的、归类是错的，四份均已拆出单列并写明是 `TEXT` 列、后端用 `,` 拼接、无 JSON 层。DOC-2（P2）Go 的 README 完全没提列表列的 wire 形态，而 Python/JS 都有——尽管三种形态的处理正是 `dto.StringList` 一处完成的，EN/ZH 已补。DOC-3（P2）四家 README 与 patrol-task 基准的测试数全部过时，逐个重测而非按增量推算：Go 255→297（client 194→230、dto 61→67）、JS 212→224、Python 374→389、Java 173→180（client 120→127）、基准 Java 175→180 / Go 335→343 / Python 385→389 / JS 216→224；Go 集成包「额外 33 个」经核实仍正确故未动。这批数字第 156 轮刚按实测更正过，此后每轮加测试都会再次漂移。核实无误：四家 README 中英标题层级序列仍逐位一致、围栏平衡）
- **下一方向**: 设计文档（六十六轮）
- **新增待决**: Java SDK README 没有 Wire Format 段落（第 164 轮核实发现），补齐需新增整节，超出「小而准」范围，留待专门一轮
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
