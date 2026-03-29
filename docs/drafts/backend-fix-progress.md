# Backend 审查问题修复进度

**开始时间**: 2026-03-29 11:46
**目标**: 修复 `backend-review-findings.md` 中所有 4 个 P2 问题 + 3 轮迭代检查 + 回归测试

## 问题清单

### 审查 #1 — 代码问题

| # | 文件 | 问题 | 级别 | 修复状态 |
|---|------|------|------|---------|
| 1 | VectorValidator.java | `countDimensions()` 对 `[]` 返回 1 而非 0 | P2 | ⏳ |
| 2 | IngestionController.java | `handleSessionEnd()` debug 变量死代码 | P2 | ⏳ |
| 3 | IngestionController.java | `toolInput`/`toolResponse` 类型转换可简化 | P2 | ⏳ |
| 4 | PendingMessageEntity.java | 无 Lombok，手写 getter/setter | P2 | ⏳ |

### 审查 #2 — 代码问题

| # | 文件 | 问题 | 级别 | 修复状态 |
|---|------|------|------|---------|
| 5 | SSEBroadcaster.java | `broadcast()` 的 eventName 参数被忽略 | P2 | ⏳ |
| 6 | HealthController.java | `/api/health` 无实际健康检查 | P2 | ⏳ |
| 7 | HealthController.java | `/api/version` 在 IDE 下返回 null | P2 | ⏳ |

### 审查 #3 — API.md 文档问题

| # | 问题 | 级别 | 修复状态 |
|---|------|------|---------|
| 8 | Session API 路径全部错误 (`/api/sessions` → `/api/session/*`) | P1 | ⏳ |
| 9 | Session API 虚构端点 (列表、DELETE) | P1 | ⏳ |
| 10 | Messages API 路径错误 | P1 | ⏳ |
| 11 | Extraction 路径缺少 {templateName} | P1 | ⏳ |
| 12 | 缺失 PATCH user 端点文档 | P2 | ⏳ |
| 13 | Ingest/Viewer/Mode/Logs 端点未文档化 | P2 | ⏳ |

## 进度记录

### Phase 1: 修复问题
- [x] 读取 findings
- [x] 修复 #1: VectorValidator.countDimensions — 提取内容后再计算维度
- [x] 修复 #2: IngestionController debug 死代码 — 移除
- [x] 修复 #3: IngestionController 类型转换简化 — 使用 pattern matching
- [x] 跳过 #4: PendingMessageEntity — 代码库风格一致（所有 Entity 均手动 getter/setter）
- [x] 修复 #5: SSEBroadcaster — 使用 .name(eventName) 替代忽略参数
- [x] 修复 #6: HealthController /api/health — 添加 DB 连接检查
- [x] 修复 #7: HealthController /api/version — 多源回退 (JAR → build-info → dev-SNAPSHOT)
- [x] 修复 #8-14: API.md 全面重写 — 修正 Session/Extraction/Ingest 路径，补充 Viewer/Mode/Logs/Import
- [x] 构建验证 — BUILD SUCCESS
- [x] 回归测试 — 46/46 passed

### Phase 2: 迭代检查 (连续3轮无问题)
- [x] 第1轮检查 — 所有修改文件审查 + API.md 端点验证 + 健康端点验证
- [x] 第2轮检查 — 边界情况 + SSEBroadcaster + debug 残留 + API.md 路径一致性
- [x] 第3轮检查 — git diff 全量审查 + 最终构建验证

### Phase 3: 回归测试
- [x] 构建 — BUILD SUCCESS
- [x] 重启服务
- [x] 回归测试 — 46/46 passed
- [ ] git commit
- [ ] 飞书汇报
