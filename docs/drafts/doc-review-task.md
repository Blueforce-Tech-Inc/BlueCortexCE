> **用途**: 文档审查与改进任务指令（30分钟 cron 任务专用）
> **维护者**: PM Agent
> **更新频率**: 审查范围或规则变更时更新

# 文档审查与改进 — 任务指令

## 执行规则

- **每次唤醒审查一个方向**（轮换：API 文档 → SDK README → 设计文档 → 架构文档 → 用户指南）
- **总时长 ≤ 15 分钟**
- **发现问题直接修复**（文档修复 + commit）
- **⚠️ 审查过程中发现的代码问题同样必须处理——要么当场修复，要么记录到 `backend-review-findings.md`，不允许只发消息了事**

## 质量标准

### 1. 双语对照

- 重要文档必须有英文版和中文版
- 中文版文件名使用 `-zh-CN` 后缀（如 `API.md` → `API-zh-CN.md`）
- 两个版本**内容必须一致**——不能英文有而中文没有，反之亦然
- 同一文档的中英文版本应在文件顶部互相链接：
  ```markdown
  > English version: [API.md](./API.md)
  > 中文版: [API-zh-CN.md](./API-zh-CN.md)
  ```

### 2. 准确性（最重要）

- **宁可不说，不可说错**——没有把握的内容不要写
- 重要信息（端点路径、参数名、返回格式）必须**通过分析代码实现来验证**
- API 文档的端点路径、HTTP 方法、参数名必须与 Controller 代码一致
- SDK 文档的方法签名、参数类型必须与源码一致
- 版本号、依赖要求必须与 `pom.xml` / `package.json` / `go.mod` 等实际配置一致

### 3. 完整性

- API 文档应覆盖所有 Controller 端点（对照 Controller 源码检查）
- SDK README 应覆盖所有公共 API 方法
- 设计文档应与实现一致（Phase 3 设计 vs 实际 Service/Entity）
- 缺失的端点/方法必须补充

### 4. 一致性

- 同一概念在不同文档中使用相同术语
- 中英文版本使用对等的结构和章节顺序
- 示例代码应可运行（不编造参数或端点）

### 5. 开源项目文档最佳实践

- README 应包含：项目简介、快速开始、API 概览、贡献指南链接
- API 文档应有清晰的分组（按功能域）
- 每个端点应有：方法+路径、参数说明、请求/响应示例
- 变更日志（CHANGELOG）应反映实际版本变更
- 链接不应失效（检查相对路径和锚点）

## 每次唤醒执行步骤

1. **检查服务状态** — `curl -s http://127.0.0.1:37777/api/health`（~5秒）
2. **选择本次审查方向**（轮换）
3. **审查**（~10分钟）：
   - 对照代码实现验证文档准确性
   - 检查中英文版本一致性
   - 修复发现的问题（文档问题直接修，代码问题修或记录到 findings.md）
   - **⚠️ 每个发现的问题必须有落点，不允许只发消息不行动**
4. **git commit**（如有修复）
5. **飞书汇报** — 发送到 `oc_d66f3ed7488467fc7adb0460fce3ef60`

## 审查范围

### 1. API 文档
- `docs/API.md`（英文）vs `docs/API-zh-CN.md`（中文）
- 对照 Controller 源码验证端点覆盖
- 对照 Swagger 注解验证参数描述

### 2. SDK README
- `java-sdk/README.md`、`go-sdk/README.md`、`python-sdk/README.md`、`js-sdk/README.md`
- 方法签名与源码一致
- 示例代码可运行

### 3. 设计文档
- `docs/drafts/phase-3-design.md` vs 实际实现
- `docs/drafts/phase-3-design-walkthrough.md` 场景覆盖

### 4. 架构文档
- `docs/ARCHITECTURE.md`（如有）
- 项目根目录 `README.md`

### 5. 运维文档
- 部署指南、配置说明、故障排查
- Docker / docker-compose 相关文档

## 汇报格式

```
📋 文档审查报告 — [日期]
🔍 本次审查方向：[方向]
📝 发现问题：[数量]
[问题列表 + 修复状态]
```

---
## Last Review Log
- **2026-03-31 04:26**: API 文档 (API.md vs API-zh-CN.md) — 修复 3 问题，commit 0e19580
- **2026-03-31 04:54**: SDK README (Go/JS/Python) — 全部 25 个方法覆盖完整，端点与 Controller 源码一致，中英文版本结构/内容一致，跨链接完整。无修复。
- **2026-03-31 05:13**: 设计文档 (structured-extraction.md + walkthrough) — 修复 2 问题，commit 8c45107
- **2026-03-31 05:45**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md) — 修复 6 问题，commit 4e5a089
- **2026-03-31 06:00**: 运维文档 (DOCKER_README + docker-compose + TESTING + CONTRIBUTING) — 修复 9 问题
- **2026-03-31 06:38**: API 文档 (API.md 新一轮) — 修复 1 问题（Viewer/Management/Mode/Health/Cursor/Logs 章节参数表和响应示例缺失），commit 3e04e6c
- **2026-03-31 07:00**: SDK README (Go/Python/JS 新一轮) — 修复 1 问题（Go README 测试数 278→268），commit 94770bb
- **2026-03-31 07:33**: 设计文档 (structured-extraction.md + walkthrough) — 修复 1 问题（walkthrough 场景4/5/决策4/评估表引用旧版 "LLM re-extraction"，实际实现为 append-only extraction），commit pending
- **2026-03-31 07:54**: 架构文档 (ARCHITECTURE.md + ARCHITECTURE-zh-CN.md + backend/README.md) — 修复 1 问题（backend/README.md 项目结构缺少 6 个包：common/dto/event/exception/logging/mcp），commit f554276
- **2026-03-31 08:20**: 运维文档 (DEPLOYMENT.md + DOCKER_README.md + CONTRIBUTING.md + TESTING.md) — 修复 2 问题（DEPLOYMENT.md 环境变量名错误/不存在变量/docker语法过旧/git URL错误/docker run示例错误；DOCKER_README.md 镜像路径与docker-compose.yml不一致），commit efba5a1
- **2026-03-31 08:42**: API 文档 (API.md + API-zh-CN.md) — 修复 2 问题（英文 Session Start 缺失响应示例；中英文 Delete Observation 响应错误 204→200 OK with body），commit 1fd42d3
- **2026-03-31 09:54**: SDK README (Go/Python/JS 新一轮) — 无修复。验证项：25 方法数（源码确认）、268 测试数（go test 确认）、端点路径与 Controller 源码一致、中英文版本结构/内容/跨链接一致、依赖声明（Go 零依赖 / Python 仅 requests / JS 零依赖）准确。
- **2026-03-31 10:54**: 设计文档 (phase-3-design.md + walkthrough 新一轮) — 修复 2 问题（Section 25 实施进度仪表盘 Steps 6-11 显示 "🔧 Pending" 但代码已实现；更新实施顺序摘要和新文件/修改文件表）; 发现 1 待修复：MemoryController ICL/experiences 端点未接入 userId（DTO 已有字段但 Controller 未使用）
- **Next direction**: API 文档 (新一轮轮换)
