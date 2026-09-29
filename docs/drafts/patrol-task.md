# 代码审查与精益求精 — 巡检任务指令

> **Purpose**: 定义单轮代码审查、问题落点、轮换方向和修复规则。
> **Updated by**: 定时项目维护任务。
> **Update rule**: 保留当前审查规则；每轮具体结果记录到统一健康检查报告，旧历史按压缩规则归档。

## 执行规则

- **每次唤醒仅审查一个方向**（轮换：Java SDK → Go SDK → Python SDK → JS SDK → Demo → Backend）
- **巡检总时长 ≤ 10 分钟**
- **⚠️ 修改后端 API 响应前，必须检查 `webui/src/` 是否引用**（WebUI 子模块已拉取到 `webui/`）
- 完整验收由统一维护任务在本轮代码审查和文档审查之后，依据代码指纹基线决定是否执行
- SDK/Demo 问题当场修复，Backend 问题记录到 findings.md 或当场修复（小问题不拖延）

## 每次唤醒执行步骤

1. **检查服务状态** — `curl -s http://127.0.0.1:37777/api/health`（~5秒）
2. **检查 HEARTBEAT.md 当前任务** — 有未完成的？立即继续（~30秒）
3. **代码审查**（本次轮到的方向，~5分钟）：
   - SDK / Demo 问题 → **直接修复** + 快速编译验证
   - Backend 问题 → **记录到 `docs/drafts/backend-review-findings.md`**（小问题可当场修复）
   - 设计文档过时 → **直接修复**或记录到 findings.md
   - **⚠️ 每个发现的问题必须有落点：修复 或 记录到文档。不允许只发消息不行动。**
4. **git commit**（如有修复）
5. **记录结果** — 将本次审查方向、发现的问题和处理结果写入 `docs/drafts/health-check-task.md` 的本轮报告

### ⚠️ 核心原则

- **发现的每个问题必须有落点——要么修复，要么记录到文档，绝不允许只发消息了事**
- **SDK/Demo 问题必须当场修复** — 不允许写"无需修复"
- **Backend 问题记录到 findings.md** — 随后由统一维护任务的完整验收阶段集中修复
- **⚠️ 绝不允许"发现 → 发消息 → 忘记"的模式** — 如果一个问题不值得当场修，就必须记到 findings.md，否则它会消失在聊天记录里
- findings.md 积累过多问题时，手动触发 Backend 修复任务消化

### ⚠️ 代码修改后 Review 规则（所有修改任务必须遵守）

每次修改代码后，必须执行**连续 3 轮迭代检查**：
- 每次检查都应该视必要对代码库的已有代码/文档进行深入探索
- 如有问题，马上修改代码、使得编译通过，然后**重置计数器，重新检查**
- 直到连续检查三次没有发现任何问题、没有任何改动为止
- 只要你修改了代码，那么迭代次数重置为 0，重新开始迭代检查

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

### 6. Backend 代码审查 ⚠️ 默认记录，随后集中修复
- 每次随机抽查 1-2 个 backend 源文件（`backend/src/main/java/`）
- Controller 层: 参数验证、错误处理、HTTP 状态码
- Service 层: 业务逻辑正确性、异常处理、事务管理
- Repository 层: 查询效率、N+1 问题
- Entity 层: 字段映射、索引设计
- Config 层: 配置绑定正确性
- 对照 Phase 3 设计文档检查实现一致性
- 发现记录到 `docs/drafts/backend-review-findings.md`
- 格式：文件路径、行号、问题描述、严重级别（P0/P1/P2）
- 默认只记录、不构建、不重启；简单且安全的问题可以当场修复，其他问题由本轮后续完整验收阶段集中修复

## 发现问题时的行动（每个问题必须有落点）

| 问题类型 | SDK/Demo（直接修复） | Backend（记录到 findings.md 或直接修复） |
|----------|---------------------|------------------------------|
| Bug | 修复 + 编译/测试验证 + commit | **必须记录到 findings.md**（或小问题当场修复） |
| 文档缺失/过时 | 更新 + commit | **必须记录到 findings.md** |
| 测试缺失 | 补测试 + 跑测试 + commit | **必须记录到 findings.md** |
| 架构改进 | 记录到 docs/drafts/ | **必须记录到 docs/drafts/** |
| 设计文档过时 | 修复 + commit | **必须记录或直接修复** |

**⚠️ 所有审查类型适用同一条规则：发消息汇报 ≠ 处理。每个问题必须落到上述表格对应的行动里。**

## 已完成实施基准

- Java SDK: 25 个 API 方法 (120 tests: DtoTest 34 + CortexMemClientImplTest 86)
- Go SDK: 25 个 API 方法 + 8 DTO + 3 集成层 (288 tests: client 194 + dto 61 + genkit 13 + langchaingo 12 + eino 8)
- Python SDK: 25 个 API 方法 + 15 DTO + ObservationUpdate + Flask Demo (374 tests: dto 118 + client 179 + demo 77)
- JS/TS SDK: 25 个 API 方法 + CJS/ESM/DTS 输出 (212 tests)
- Demo: Java 10 控制器 + Go 5 Demo + Python 1 Demo + JS 1 Demo
- E2E 测试: 4 个严格验证脚本
