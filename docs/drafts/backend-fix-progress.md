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
