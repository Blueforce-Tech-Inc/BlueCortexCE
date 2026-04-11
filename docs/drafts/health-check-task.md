> **用途**: 健康检查与测试验收任务指令（每2小时 cron 任务专用）
> **维护者**: PM Agent
> **更新频率**: 测试规则或验收标准变更时更新
> **关联 Cron**: `4b56c649`（每2小时，超时 1800s）

# CortexCE 健康检查与测试验收 — 任务指令

## 执行规则

- 验证系统健康状态，发现问题**立即修复**
- 本任务是每 2 小时执行一次的重任务，有充足时间进行修复
- 与巡检任务（`83de38e1`）互补：巡检做快速审查，本任务做深度验证与修复

## 每次执行步骤

1. **服务健康检查**
   ```bash
   curl -s http://127.0.0.1:37777/api/health
   ```
   - DB 不可达 → 检查数据库状态，尝试重启服务

2. **回归测试**
   ```bash
   bash scripts/regression-test.sh --skip-build
   ```
   - 失败 → 立即分析失败原因，修复代码，重新构建测试

3. **EXTRACTION_ENABLED=true 验收测试**
   ```bash
   EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh
   ```
   - 失败 → 立即分析失败原因，修复代码，重新构建测试

4. **Backend 审查问题批量修复**
   - 读取 `docs/drafts/backend-review-findings.md`
   - 收集所有未修复问题（级别列未标记"已修复"或"已跳过"的）
   - **批量修复**：一次处理尽可能多的问题（目标：每次至少修复 10 个）
   - 优先级：P1 > P2 > P2(低)（但**不要跳过 P2**——有就修）
   - 每个问题单独修复 → 编译验证 → 标记"✅已修复"
   - 全部修复后统一构建、重启、跑回归测试
   - 如时间不够，优先修复简单问题（null 检查、返回类型、dead code）

## ⚠️ 代码修改后 Review 规则（必须遵守）

每次修改代码后（无论是测试失败修复还是 Backend 问题修复），必须执行**连续 3 轮迭代检查**：
- 每次检查都应该视必要对代码库的已有代码/文档进行深入探索
- 如有问题，马上修改代码、使得编译通过，然后**重置计数器，重新检查**
- 直到连续检查三次没有发现任何问题、没有任何改动为止
- 只要你修改了代码，那么迭代次数重置为 0，重新开始迭代检查

## 修复流程（适用于所有修复场景）

1. 定位问题根因
2. 修改代码
3. 构建验证：`cd backend && mvn clean compile package -DskipTests`
4. 执行上述"代码修改后 Review 规则"（连续 3 轮无问题）
5. 重启服务（仅杀服务端进程，不要误杀客户端）
6. 重新跑失败的测试，确认通过
7. 将修复细节记录到 `docs/drafts/backend-fix-progress.md`
8. 如涉及 Backend 审查发现的问题，同步更新 `docs/drafts/backend-review-findings.md`
9. git commit

### 重启服务方法

```bash
# 仅杀服务端进程
pkill -f "java.*cortex-ce" 2>/dev/null; sleep 2
# 加载环境变量并启动
export $(cat backend/.env | grep -v '^#' | grep -v '^$' | xargs) 2>/dev/null
java -jar backend/target/cortex-ce-0.1.0-beta.jar --spring.profiles.active=dev &
# 等待启动
curl -s http://127.0.0.1:37777/api/health
```

## ⚠️ WebUI 契约（不可破坏）

修改后端 API 响应前，必须检查 `webui/src/` 是否引用。SSE 必须用 unnamed events（`onmessage`）。详见 `TOOLS.md`。

## 汇报要求

- ✅ 全部通过且无新修复 → 简短汇报"所有测试通过"
- ✅ 全部通过且有新修复 → 汇报"所有测试通过" + 本次修复的问题列表
- ❌ 有失败 → 立即修复，修复后重新跑测试确认通过，然后汇报：失败项 + 修复措施 + 最终结果

---

## 巡检历史

### 2026-04-11 17:42 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-11 16:19 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-11 14:21 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-10 03:11 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-09 12:20 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-09 05:15 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-09 04:22 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-08 11:22 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-06 23:04 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

### 2026-04-06 20:12 | 健康检查

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

**Backend Review #45**（2026-04-09 21:14）— 健康检查 + Backend Review：

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Backend 服务健康 | ✅ OK | `{"service":"claude-mem-java","status":"ok"}` |
| 回归测试 | ✅ 46/47 | regression-test.sh（1 skipped） |
| EXTRACTION 验收 | ✅ 25/25 | phase3-acceptance-test.sh |
| Backend Review | ✅ 0 P0/0 P1/0 P2 | 全部已修复，无待处理问题 |

**Backend Review #44**（2026-04-07 07:08）— Backend 审查问题批量修复：

全部通过，无待处理问题（0 P0/0 P1/0 P2）。

**Backend Review #43**（2026-04-06 20:12）— ImportService + TokenService + ImportController 抽样审查：

发送消息到 `oc_d66f3ed7488467fc7adb0460fce3ef60`
