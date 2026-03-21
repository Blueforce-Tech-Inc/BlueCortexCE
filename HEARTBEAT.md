# HEARTBEAT.md

# 定期检查任务

## 服务状态检测

**正确方式**: 使用 HTTP 端点检测，不要检查进程

```bash
curl -s http://127.0.0.1:37777/api/health
```

**正确响应**:
```json
{"service":"claude-mem-java","status":"ok","timestamp":...}
```

**错误方式**: 检查进程可能会误报（进程存在但服务未就绪）

---

## 注意按照语言版本改进文档和代码注释

- 修改文档时，必须注意文档是*英文*还是*中文*版本（中文版本文件名一般带 `-zh-CN` 后缀），修改文档时注意使用**合适的语言**
- 代码注释优先使用*英文*，如果发现代码中存在中文注释，请将其翻译为英文并更新注释
- 对于草稿文档和临时脚本所使用的语言不用过于严格（只要表述准确即可）

---

## 当前巡检重点：SDK 改进实现 (V14)

**参考文档**: `docs/drafts/sdk-improvement-research.md`

### 巡检清单

- [x] **代码检查**: 确认 V14 字段正确实现
  - `ObservationEntity.source` (String)
  - `ObservationEntity.extractedData` (Map<String, Object> JSONB)
  - `PATCH/DELETE /api/memory/observations/{id}` 端点

- [x] **测试覆盖**: 确认回归测试通过
  ```bash
  bash scripts/regression-test.sh
  # 预期: 39/39 tests passed
  ```

- [x] **文档完整性**: 确认 `sdk-improvement-research.md` 已是"诚实记录"
  - 每条痛点有实现状态
  - API 参考完整
  - SDK 使用示例存在

- [x] **API 验证**: 测试关键端点
  ```bash
  # source 过滤
  curl "http://127.0.0.1:37777/api/search?project=/tmp/test&source=manual"
  
  # PATCH 更新
  curl -X PATCH http://127.0.0.1:37777/api/memory/observations/{id} -d '{"source":"test"}'
  
  # maxChars 参数
  curl -X POST http://127.0.0.1:37777/api/memory/icl-prompt -d '{"task":"test","maxChars":500}'
  ```

---

## 已完成任务

### MCP Server 协议决策 - SSE ✅

**SSE 作为默认协议**（2026-03-19）
- 提交: `44d8821`, `edc5a99`, `6146158`, `0ad44ce`
- E2E 测试: 12/12 通过 (SSE模式)

### MCP Server Phase B 验证 ✅ (2026-03-19)

**STREAMABLE 作为备选协议已验证通过**
- 提交: `9f1cfe8`, `d844125`, `f806555`
- E2E 测试: 14/14 通过 (STREAMABLE模式)
- 结论: STREAMABLE 协议可用，客户端需正确实现 session 管理
- 决策: SSE 仍为默认（客户端兼容性最佳）

### SDK 改进研究 ✅ (2026-03-20)

**docs/drafts/sdk-improvement-research.md**
- 分析 Spring AI 开发者痛点（8个关键问题）
- 提出广义实体扩展方案，避免枚举膨胀

### Session ID V13 重构 ✅ (2026-03-20)

**提交: `21ff52b`**
- 统一 session ID 到 content_session_id
- 与现有 project-based 隔离方案对齐

### Phase 1 SDK 改进实施 ✅ (2026-03-21)

**提交: `ba393f0`, `6b2b352`**
- 新增 `source` (String) 和 `extractedData` (Map JSONB) 字段
- 实现 PATCH/DELETE /api/memory/observations/{id}
- Flyway V14 迁移成功

### Phase 2 增强功能 ✅ (2026-03-21)

**提交: `3baba41`**
- Adaptive truncation: `ICLPromptRequest.maxChars` + `CortexMemoryAdvisor.maxIclChars`
- MemoryManagementTools: `updateMemory()` + `deleteMemory()`
- Source-based filtering: `ExperienceRequest.source` + `requiredConcepts`

### Phase 4 source过滤 + Test 10修复 ✅ (2026-03-21)

**提交: `372a70d`**
- `/api/search` 端点支持 source 参数过滤
- SearchService.filterSearch() 支持 source 过滤
- 修复 Test 10: 用 API 替代 psql 查询

### V14 功能测试覆盖 ✅ (2026-03-21)

**提交: `ac9072a`**
- Test 22: ICL prompt maxChars 参数
- Test 23: Observation source 和 extractedData 字段
- Test 24: PATCH observation 端点
- Test 25: Search API source 过滤
- Test 26: Experiences API source/requiredConcepts 过滤
- 回归测试 39/39 通过

### 文档更新 ✅ (2026-03-21)

**提交: `799cd62`**
- `sdk-improvement-research.md` 更新为"诚实记录"
- 每条痛点有实现状态
- API 参考和 SDK 使用示例

### SDK + Demo 验证 ✅ (2026-03-21)

**验证步骤**:
1. SDK 模块构建成功 (`mvn clean install -DskipTests`)
2. Demo 项目编译成功 (`mvn clean compile -Plocal`)
3. Demo V14 功能测试通过 (4/4 tests passed)
4. 回归测试通过 (43/43 tests passed)

**Demo V14 测试结果**:
```
✅ /memory/experiences (basic)
✅ /memory/icl/truncated (V14 maxChars)
✅ /memory/experiences/filtered (V14 source filtering)
✅ /memory/health
```

**关键验证点**:
- `cortex-mem-spring-integration` 安装到本地 Maven 仓库
- `examples/cortex-mem-demo` 必须使用 `-Plocal` profile
- Demo 演示了所有 V14 特性端点

### 测试健壮性增强 ✅ (2026-03-21)

**提交: `3c93018`**
- 问题: 原 V14 测试只检查 API 响应包含字段，未验证数据真正持久化
- 解决: 添加后端数据库验证，验证真实状态
- 改进内容:
  - Test 23: 创建后验证 observation 实际存在于后端
  - Test 24: PATCH 后验证修改真正持久化
  - Test 25: 验证搜索结果中所有 observation 的 source 正确
  - Test 26: 验证 experiences API 能找到 PATCH 后的 observation
- 测试结果: 43/43 通过 (原 39 + 4 个后端验证步骤)

---

## Cron 任务

| 任务 | 状态 | 说明 |
|------|------|------|
| MCP Server 巡检 (每小时) | ✅ | 检查 MCP 服务状态 |
| 项目巡检 (每15分钟) | ✅ | **重点**: SDK 改进实现巡检 |

### 巡检任务说明

**SDK 改进实现巡检** (每15分钟):
1. 服务健康检测
2. 运行回归测试确认 39/39 通过
3. 检查关键 API 端点
4. 确认文档与实现一致

---

## 实现状态总览

| 功能 | 状态 | 相关端点 |
|------|------|----------|
| source 字段 | ✅ 已实现 | POST /api/ingest/observation, GET /api/search |
| extractedData JSONB | ✅ 已实现 | POST /api/ingest/observation |
| PATCH observation | ✅ 已实现 | PATCH /api/memory/observations/{id} |
| DELETE observation | ✅ 已实现 | DELETE /api/memory/observations/{id} |
| maxChars truncation | ✅ 已实现 | POST /api/memory/icl-prompt |
| source 过滤 (search) | ✅ 已实现 | GET /api/search?source=... |
| source 过滤 (experiences) | ✅ 已实现 | POST /api/memory/experiences |
| requiredConcepts 过滤 | ✅ 已实现 | POST /api/memory/experiences |
| updateMemory tool | ✅ 已实现 | CortexMemoryTools |
| deleteMemory tool | ✅ 已实现 | CortexMemoryTools |
| UserProfile entity | ⏳ 延期 | - |
| Preference history | ⏳ 延期 | - |
| Memory conflict detection | ⏳ 延期 | - |
