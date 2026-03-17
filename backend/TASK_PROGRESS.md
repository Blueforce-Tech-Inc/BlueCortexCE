# Code Review v5 修复进度跟踪

**开始时间**: 2026-02-12
**上次更新**:

## 问题统计

| 级别 | 数量 | 状态 |
|------|------|------|
| P0 | 4 | 待修复 |
| P1 | 13 | 待修复 |
| P2 | 9 | 待修复 |
| P3 | 3 | 待修复 |

## 详细问题列表

### P0 - Critical (4)

- [ ] `ObservationRepository.java:53-114` - SSRF Risk: Vector Deserialization 无验证
- [ ] `XmlParser.java:80-90` - ReDoS Risk: extractArray 无输入限制
- [ ] `AgentService.java:47-51` - SRP Violation: 单服务职责过多
- [ ] `ContextService.java:183-196` - validateProjectPath 返回原始路径而非规范化路径

### P1 - High (13)

- [ ] `RateLimitService.java:117-122` - 可预测的 Thread ID fallback
- [ ] `ContextService.java:206-251` - 无输入大小限制
- [ ] `SearchService.java:63-65` - pgvector 异常被静默吞掉
- [ ] `AgentService.java:299-314` - 泛型异常处理不可靠
- [ ] `ContextService.java:664-683` - N+1 查询风险
- [ ] `ContextService.java:220-224,311-313` - 多次访问相同数据
- [ ] `ContextService.java:727-733` - 负数 epoch 未处理
- [ ] `SessionController.java:85-151` - 职责过多
- [ ] `AgentService.java:255-256,541-543` - 硬编码 magic numbers
- [ ] `ContextCacheService.java:43-52` - markForRefresh 部分更新风险
- [ ] `ContextCacheService.java:58-81` - 返回 null 不一致
- [ ] `ContextService.java:431-483` - 列表无界增长
- [ ] `ObservationRepository.java` - 无查询超时

### P2 - Medium (9)

- [ ] `ContextService.java:225-227,307-308` - subList 无边界检查
- [ ] `AgentService.java` / `ContextService.java` - 重复 truncate 方法
- [ ] `ViewerController.java:172-196` - 占位符实现
- [ ] `ContextCacheService.java:50,76` - 日志级别不一致
- [ ] `ContextService.java` - SRP 违反 (Timeline/TokenEconomics)

### P3 - Low (3)

- [ ] `RateLimitService.java:93-108` - 内存增长
- [ ] `ObservationRepository.java` - 接口过大
- [ ] `SearchService.java:41` - 负数限制边界情况

## 执行步骤

1. [ ] 修复所有 P0 问题
2. [ ] 修复所有 P1 问题
3. [ ] 修复所有 P2 问题
4. [ ] 修复所有 P3 问题
5. [ ] 编译并构建: `mvn clean compile package -DskipTests`
6. [ ] 重启服务
7. [ ] 运行回归测试: `./regression-test.sh`
8. [ ] 运行 thin proxy 测试: `./thin-proxy-test.sh`
9. [ ] 提交并推送更改

## 环境变量

参考: `java/claude-mem-java/.env`
