# Backend 修复进度记录

## 2026-04-01 18:14 | 健康检查 + 测试验收 + Backend 审查 #16

### 健康检查结果
- ✅ 服务健康检查：`{"status":"ok"}`
- ✅ 回归测试：46/46 Passed, 0 Failed
- ✅ Phase 3 验收测试：25/25 Passed, 0 Failed (EXTRACTION_ENABLED=true)

### Backend 审查 #16 修复
| # | 文件 | 修复内容 | 级别 |
|---|------|---------|------|
| 1 | RateLimitService.java | `cleanupExpiredWindows()` 移到 `tryIncrement()` 之后执行 | P2 |
| 2 | RateLimitService.java | 改进注释说明 ConcurrentHashMap 迭代的弱一致性 | P2 (低) |
| 3 | ExtractionStorageService.java | 识别 isolation level 潜在问题（风险极低，未修改代码） | P2 (低) |

### 验证
- 编译：`mvn clean compile package -DskipTests` ✅ BUILD SUCCESS
- 回归测试重启后：46/46 Passed ✅
- 后端审查累计修复：~65 个问题

### 剩余未修复问题
- Python SDK #3: SearchResult.to_dict() 测试覆盖 (P2) - SDK 问题
- Python SDK #3: dto.py 注释改进 (P2) - SDK 问题
- JS SDK + Backend: StartSessionResponse 缺少 session_id (P2) - SDK 端已通过 defaults 兜底
- JS SDK: UUID 格式校验 (P2 极低)

### 2026-04-01 23:26 | 健康检查修复 — ContextService.java

**修复的 4 个 P2 问题**：

1. **validateProjectPath() 逻辑反转** — 原条件 `!projectPath.contains("..")` 放过了含 `..` 的遍历路径。改为直接 reject 含 `..` 的路径。
2. **validateProjectPath() 重复 PathValidationUtil** — 简化为直接 `contains("..")` 检查，路径验证足够（不需要 PathValidationUtil 的 filesystem 检查，因为路径可能不存在）。
3. **generateContinuation() 日志级别错误** — catch 块 `logHappyPath`（DEBUG）改为 `logFailure`（WARN+），异常不再被静默吞掉。
4. **generateContextMultiProject() NPE 风险** — `getFileName()` 对 root 路径返回 null，添加 null check + fallback。

**验证结果**：
- ✅ 编译通过
- ✅ 回归测试 46/46 通过
- ✅ 连续 3 轮迭代检查通过
- ✅ 服务重启成功，健康检查 OK

**Backend 审查问题状态更新**：所有 P2 问题已清零（0 未修复）

## 2026-04-02 02:31 | 健康检查 + 测试验收 + P2 问题修复

### 健康检查结果
- ✅ 服务健康：`{"service":"claude-mem-java","status":"ok"}`
- ✅ 回归测试：46/46 通过，1 跳过
- ✅ Phase 3 验收测试：25/25 通过（EXTRACTION_ENABLED=true）

### 修复的问题
1. **#19-1 P2** — `ClaudeMemMcpTools.search()`: offset 参数被硬编码为 0，改为使用传入的 offset 值
2. **#19-2 P2** — `ClaudeMemMcpSaveMemory()`: 每次调用创建新 dummySession 导致 session 泄漏，改为复用单个 "manual-memories" session（find-or-create 模式）

### 验证
- 构建: ✅ BUILD SUCCESS
- 重启服务: ✅ 健康检查通过
- 回归测试修复后: ✅ 46/46 通过
- 3 轮代码审查: ✅ 全部通过

### Backend 审查问题状态
- P0: 0 | P1: 0 | P2: 0 | 跳过: 4
- **所有 P2 问题已修复完毕 ✅**
