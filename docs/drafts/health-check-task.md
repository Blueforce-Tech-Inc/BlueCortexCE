> **用途**: 健康检查与测试验收任务指令（每2小时 cron 任务专用）
> **维护者**: PM Agent
> **更新频率**: 测试规则或验收标准变更时更新
> **关联 Cron**: `4b56c649`（每2小时）

# CortexCE 健康检查与测试验收 — 任务指令

## 执行规则

- 专注于验证系统健康状态
- 代码审查和修复由巡检任务（`83de38e1`）负责，本任务只做测试、汇报、以及 Backend 审查问题的修复

## 每次执行步骤

1. **服务健康检查**
   ```bash
   curl -s http://127.0.0.1:37777/api/health
   ```

2. **回归测试**
   ```bash
   bash scripts/regression-test.sh --skip-build
   ```

3. **EXTRACTION_ENABLED=true 验收测试**
   ```bash
   EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh
   ```

4. **Backend 审查问题修复**
   - 读取 `docs/drafts/backend-review-findings.md`
   - 检查是否有新增的待修复问题（级别列未标记"已修复"或"已跳过"的）
   - 如有，按下面的修复流程执行

## ⚠️ 代码修改后 Review 规则（必须遵守）

每次修改代码后，必须执行**连续 3 轮迭代检查**：
- 每次检查都应该视必要对代码库的已有代码/文档进行深入探索
- 如有问题，马上修改代码、使得编译通过，然后**重置计数器，重新检查**
- 直到连续检查三次没有发现任何问题、没有任何改动为止
- 只要你修改了代码，那么迭代次数重置为 0，重新开始迭代检查

## Backend 问题修复流程

1. 按照 `docs/drafts/patrol-task.md` 中 Backend 审查规则执行修复
2. 构建验证：`cd backend && mvn clean compile -DskipTests`
3. 执行上述"代码修改后 Review 规则"（连续 3 轮无问题）
4. 重启服务 + 回归测试（46/46）
5. 将修复细节记录到 `docs/drafts/backend-fix-progress.md`
6. 更新 `docs/drafts/backend-review-findings.md` 标记对应问题为已修复
7. git commit

## ⚠️ WebUI 契约（不可破坏）

修改后端 API 响应前，必须检查 `webui/src/` 是否引用。SSE 必须用 unnamed events（`onmessage`）。详见 `TOOLS.md`。

## 汇报要求

- ✅ 全部通过且无新修复 → 简短汇报"所有测试通过"
- ✅ 全部通过且有新修复 → 汇报"所有测试通过" + 本次修复的问题列表
- ❌ 有失败 → 立即修复，修复后重新跑测试确认通过，然后汇报：失败项 + 修复措施 + 最终结果

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`
