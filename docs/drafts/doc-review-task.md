# 文档审查与改进 — 任务指令

> **Purpose**: 定义文档准确性、完整性、双语一致性和轮换审查规则。
> **Updated by**: 定时项目维护任务。
> **Update rule**: 每轮只审查一个方向；结果写入统一健康检查报告；历史达到增长阈值时归档。

## Current Review Rotation

- **轮换顺序**: API 文档 → SDK README → 设计文档 → 架构文档 → 用户指南
- **最近完成**: 架构文档（2026-09-30）
- **下一方向**: 用户指南
- 完成本轮后必须把“最近完成”和“下一方向”更新在本节；详细历史保存在归档文件中。

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
3. **设计文档**：`docs/drafts/phase-3-design.md` 与 walkthrough，对照实际实现和测试脚本。
4. **架构文档**：`docs/ARCHITECTURE.md`、中文版本、`backend/README.md` 和根 README。
5. **运维/用户指南**：部署、配置、测试、故障排查和 Docker 文档。

## 活动文档维护

每轮开始和结束执行 `bash scripts/doc-growth-check.sh`。任一活动文档超过 1000 行或 102400 字节时，保留规则、状态和未解决项，将已解决历史归档到 `docs/archive/YYYY-MM-DD_<descriptive-name>.md`，更新 `docs/archive/README.md`，并重新检查。归档文件创建后不得修改。

## Archived History

完整审查历史截至 2026-05-05 保存在 [`2026-09-30_doc-review-history.md`](../archive/2026-09-30_doc-review-history.md)。
