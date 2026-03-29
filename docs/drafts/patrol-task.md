> **用途**: 代码审查巡检任务指令（高频 cron 任务专用）
> **维护者**: PM Agent
> **更新频率**: 审查范围或规则变更时更新
> **关联 Cron**: `83de38e1`（每10分钟）

# 代码审查与精益求精 — 巡检任务指令

## 执行规则

- **每次唤醒仅审查一个方向**（轮换：Java SDK → Go SDK → Python SDK → JS SDK → Demo → Backend → 文档）
- **巡检总时长 ≤ 10 分钟**
- 全量测试由"健康检查与测试验收"任务（`4b56c649`，每2小时）负责，本任务只做代码审查和修复

## 每次唤醒执行步骤

1. **检查服务状态** — `curl -s http://127.0.0.1:37777/api/health`（~5秒）
2. **检查 HEARTBEAT.md 当前任务** — 有未完成的？立即继续（~30秒）
3. **代码审查**（本次轮到的方向，~5分钟）：
   - SDK / Demo 小问题 → 直接修复 + 快速编译验证
   - Backend 问题 → **仅记录到 `docs/drafts/backend-review-findings.md`，不修复**
4. **git commit**（如有修复）
5. **飞书汇报** — 发送到 `oc_d66f3ed7488467fc7adb0460fce3ef60`，必须包含：本次审查方向、发现的问题、处理结果

## 审查范围

### 1. Java SDK 审查
- 新增 P0/P1 方法实现质量
- SearchRequest/ObservationsRequest DTO 设计
- 错误处理和边界情况
- Demo 控制器代码质量

### 2. Go SDK 审查
- Client 接口设计完整性
- DTO wire format 映射正确性
- HTTP 客户端实现健壮性
- 错误处理和重试机制
- 集成层接口适配正确性

### 3. Python SDK 审查
- CortexMemClient 实现质量
- DTO dataclass 设计
- ObservationUpdate 双模式支持
- 测试覆盖率
- Flask HTTP Server Demo 代码质量

### 4. JS/TS SDK 审查
- TypeScript 类型完整性
- CJS + ESM 双格式输出
- npm 包发布配置
- 测试覆盖率
- E2E 测试脚本

### 5. Demo 代码审查
- Java Demo: SearchController, ObservationsController, ManagementController
- Go Demo: basic, eino, genkit, langchaingo, http-server
- Python Demo: Flask http-server
- 每个 Demo 的正确性和完整性

### 6. Backend 代码审查 ⚠️ 仅记录不修复
- 每次随机抽查 1-2 个 backend 源文件（`backend/src/main/java/`）
- Controller 层: 参数验证、错误处理、HTTP 状态码
- Service 层: 业务逻辑正确性、异常处理、事务管理
- Repository 层: 查询效率、N+1 问题
- Entity 层: 字段映射、索引设计
- Config 层: 配置绑定正确性
- 对照 Phase 3 设计文档检查实现一致性
- 发现记录到 `docs/drafts/backend-review-findings.md`
- 格式：文件路径、行号、问题描述、严重级别（P0/P1/P2）
- **不修复不构建不重启**，节省时间

### 7. 文档审查
- 设计文档与实现一致性
- API 文档完整性
- **重点对照**: `docs/drafts/phase-3-design.md` 和 `docs/drafts/phase-3-design-walkthrough.md`

## 发现问题时的行动

| 问题类型 | SDK/Demo（直接修复） | Backend（仅记录） |
|----------|---------------------|------------------|
| Bug | 修复 + E2E 测试 + commit | 记录到 findings.md |
| 文档缺失 | 更新 + commit | 记录到 findings.md |
| 测试缺失 | 补测试 + 跑测试 + commit | 记录到 findings.md |
| 架构改进 | 记录到 docs/drafts/ | 记录到 findings.md |

## 已完成实施基准

- Java SDK: 24 个 API 方法
- Go SDK: 26 个 API 方法 + 8 DTO + 3 集成层 (251 tests)
- Python SDK: 24 个 API 方法 + 15 DTO + ObservationUpdate + Flask Demo (202 tests)
- JS/TS SDK: 26 个 API 方法 + CJS/ESM/DTS 输出 (130 tests)
- Demo: Java 10 控制器 + Go 5 Demo + Python 1 Demo + JS 1 Demo
- E2E 测试: 4 个严格验证脚本
