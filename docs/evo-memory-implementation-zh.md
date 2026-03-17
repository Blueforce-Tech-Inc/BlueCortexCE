# Evo-Memory 实现文档

> **日期**: 2026-03-17
> **项目**: Claude-Mem Java (Cortex Community Edition)
> **参考论文**: [Evo-Memory Paper](https://arxiv.org/abs/2511.20857)

---

## 目录

1. [概述](#概述)
2. [架构](#架构)
3. [数据库 schema](#数据库-schema)
4. [核心服务](#核心服务)
5. [API 端点](#api-端点)
6. [质量评分系统](#质量评分系统)
7. [记忆精炼](#记忆精炼)
8. [经验检索 (ExpRAG)](#经验检索-exprag)
9. [特性开关](#特性开关)
10. [测试](#测试)
11. [配置](#配置)

---

## 概述

本文档描述了基于 Google DeepMind 发布的 [Evo-Memory 论文](https://arxiv.org/abs/2511.20857) 实现的智能记忆管理功能。该实现为 Claude-Mem Java 系统增添了智能记忆管理能力。

### 核心改进

| 功能 | 描述 |
|------|------|
| **质量评分** | 基于反馈自动评估观察质量 |
| **记忆精炼** | 自动清理和整合低质量记忆 |
| **ExpRAG** | 基于经验的检索，用于上下文学习 |
| **特性开关** | 可配置的安全部署选项 |

---

## 架构

### 系统上下文

```
┌─────────────────────────────────────────────────────────────────┐
│                        Claude Code / Agent                       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Claude-Mem Java Backend                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   Session   │  │Observation  │  │      Summary           │  │
│  │  Controller │  │ Controller  │  │      Controller       │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
│         │                │                      │                │
│         └────────────────┼──────────────────────┘                │
│                          ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    AgentService                              ││
│  │  (Session lifecycle, observation persistence)              ││
│  └─────────────────────────────────────────────────────────────┘│
│                          │                                       │
│         ┌────────────────┼────────────────┐                       │
│         ▼                ▼                ▼                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │QualityScorer│  │MemoryRefine │  │  ExpRag    │             │
│  │             │  │  Service    │  │  Service    │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
│                          │                                       │
└──────────────────────────┼───────────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   PostgreSQL + pgvector                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   sessions   │  │ observations │  │  summaries   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### 数据流程

```
会话开始
     │
     ▼
┌────────────────┐
│ 创建会话        │
└────────────────┘
     │
     ▼
┌─────────────────────┐
│ Agent 工作         │
│ (工具调用等)       │
└─────────────────────┘
     │
     ▼
┌─────────────────────┐     ┌──────────────────┐
│ 摄入观察           │────▶│  QualityScorer  │
└─────────────────────┘     │  (估算质量)      │
                           └──────────────────┘
     │                            │
     │                            ▼
     │                    ┌──────────────────┐
     │                    │ 质量分数         │
     │                    │ (存储到数据库)   │
     │                    └──────────────────┘
     │
     ▼
┌─────────────────────┐
│ 会话完成            │
└─────────────────────┘
     │
     ▼
┌─────────────────────┐     ┌──────────────────┐
│ 反馈推断           │────▶│  QualityScorer  │
└─────────────────────┘     │  (更新分数)      │
                           └──────────────────┘
                                    │
                                    ▼
                           ┌──────────────────┐
                           │ MemoryRefine     │
                           │ (异步清理)       │
                           └──────────────────┘
```

---

## 数据库 Schema

### V11: 质量评分字段

添加到 `mem_observations` 表:

```sql
-- 质量评分字段
quality_score FLOAT,           -- 0.0-1.0 质量分数
feedback_type VARCHAR(20),    -- SUCCESS/PARTIAL/FAILURE/UNKNOWN
last_accessed_at TIMESTAMP,   -- 上次检索时间
access_count INT DEFAULT 0,  -- 检索次数

-- 精炼追踪
refined_at TIMESTAMP,         -- 上次精炼时间
refined_from_ids JSONB,       -- 合并/前体观察的 ID
user_comment TEXT,            -- 用户反馈评论
feedback_updated_at TIMESTAMP -- 上次反馈更新时间
```

### V12: 步骤效率字段

添加到 `mem_sessions` 表:

```sql
-- 步骤效率追踪
total_steps INT DEFAULT 0,
avg_steps_per_task FLOAT
```

添加到 `mem_observations`:

```sql
-- 会话内步骤编号
step_number INT
```

---

## LLM 集成流程

会话结束时，LLM 执行以下操作:

```
AgentService.onSessionEnd()
    │
    ├─► 1. inferFeedbackWithLlm()
    │       → LLM 分析会话摘要 → 返回 SUCCESS/PARTIAL/FAILURE
    │
    ├─► 2. estimateQualityWithLlm()  
    │       → LLM 分析每个 observation → 返回 0.0-1.0 质量分数
    │
    └─► 3. MemoryRefineService.refineMemory()
            │
            ├─► mergeObservations()
            │       → LLM 将多个 observation 合并为一个
            │
            └─► rewriteObservation()
                    → LLM 改进 observation 内容提高可读性
```

**关键方法**:
- `QualityScorer.inferFeedbackWithLlm()` - 反馈推断
- `QualityScorer.estimateQualityWithLlm()` - 质量评分
- `MemoryRefineService.mergeObservations()` - 记忆合并
- `MemoryRefineService.rewriteObservation()` - 记忆重写

---

## 核心服务

### QualityScorer

**位置**: `backend/src/main/java/com/ablueforce/cortexce/service/QualityScorer.java`

基于以下因素评估观察质量:

1. **反馈类型** (基础分数):
   - SUCCESS: 0.75
   - PARTIAL: 0.50
   - FAILURE: 0.20
   - UNKNOWN: 0.50

2. **效率加成**: 工具使用次数越少，分数越高

3. **内容加成**: 内容越长、越详细，获得加成

```java
public float estimateQuality(FeedbackType feedback, String content, 
                           List<String> facts, int toolUseCount) {
    float baseScore = feedback.baseScore;
    float efficiencyBonus = Math.max(0, (20 - toolUseCount) * 0.01f);
    float contentBonus = content != null ? Math.min(0.1f, content.length() / 10000f) : 0;
    
    return Math.min(1.0f, baseScore + efficiencyBonus + contentBonus);
}
```

### LlmQualityScorer

**位置**: `backend/src/main/java/com/ablueforce/cortexce/service/LlmQualityScorer.java`

基于 LLM 的质量分析服务。使用现有的 LlmService (Spring AI ChatClient) 进行质量评估:

1. **质量分析**: 使用 LLM 分析观察内容并提供质量分数
2. **反馈推断**: 使用 LLM 从会话上下文推断反馈类型
3. **回退**: 如果 LLM 不可用，回退到基于规则的 QualityScorer

使用系统现有的 LlmService，支持 OpenAI 兼容 API (DeepSeek 等) 和 Anthropic 兼容 API (Claude, GLM 等)。

### MemoryRefineService

**位置**: `backend/src/main/java/com/ablueforce/cortexce/service/MemoryRefineService.java`

管理记忆生命周期:

1. **删除**: 移除低质量观察 (< 0.3 阈值)
2. **精炼**: 使用 LLM 重写陈旧观察
3. **冷却期**: 7 天后才可再次精炼

```java
@Async
public void refineMemory(String projectPath) {
    if (!refineEnabled) return;
    
    List<ObservationEntity> candidates = findRefineCandidates(projectPath);
    
    // 分类: 删除 vs 精炼
    List<ObservationEntity> toDelete = candidates.stream()
        .filter(o -> o.getQualityScore() < deleteThreshold)
        .toList();
    
    // 删除低质量
    deleteLowQualityObservations(toDelete);
    
    // 精炼候选 (LLM 重写)
    for (ObservationEntity obs : toRefine) {
        if (canRefine(obs)) {
            refineObservation(obs);
        }
    }
}
```

### ExpRagService

**位置**: `backend/src/main/java/com/ablueforce/cortexce/service/ExpRagService.java`

检索用于上下文学习的经验:

```java
public List<Experience> retrieveExperiences(String currentTask, 
                                            String projectPath, 
                                            int count) {
    // 获取高质量观察
    List<ObservationEntity> highQuality = observationRepository
        .findHighQualityObservations(projectPath, MIN_QUALITY_THRESHOLD, count * 3);
    
    return highQuality.stream()
        .map(this::toExperience)
        .toList();
}

public String buildICLPrompt(String currentTask, List<Experience> experiences) {
    // 构建包含历史经验的提示词
}
```

---

## API 端点

### MemoryController

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/memory/refine` | POST | 触发记忆精炼 |
| `/api/memory/experiences` | POST | 检索用于 ICL 的经验 |
| `/api/memory/icl-prompt` | POST | 构建 ICL 提示词 |
| `/api/memory/quality-distribution` | GET | 获取质量分布 |

### 使用示例

```bash
# 触发精炼
curl -X POST "http://localhost:37777/api/memory/refine?project=/path/to/project"

# 检索经验
curl -X POST "http://localhost:37777/api/memory/experiences" \
  -H 'Content-Type: application/json' \
  -d '{"task": "implement auth", "project": "/path", "count": 4}'

# 获取质量分布
curl "http://localhost:37777/api/memory/quality-distribution?project=/path"
```

---

## 质量评分系统

### 反馈推断

会话完成时，系统从完成消息推断反馈:

```java
private QualityScorer.FeedbackType inferFeedback(String lastAssistantMessage, 
                                                 int observationCount,
                                                 long sessionDurationMs) {
    // 检查成功/失败关键词
    if (lastAssistantMessage.contains("完成") || 
        lastAssistantMessage.contains("completed") ||
        lastAssistantMessage.contains("solved")) {
        return FeedbackType.SUCCESS;
    }
    
    if (lastAssistantMessage.contains("失败") || 
        lastAssistantMessage.contains("failed")) {
        return FeedbackType.FAILURE;
    }
    
    // 基于观察数和时长的启发式判断
    if (observationCount < 3 || sessionDurationMs < 5000) {
        return FeedbackType.FAILURE;
    }
    
    return FeedbackType.PARTIAL;
}
```

### 质量分布

系统将观察分类到不同质量层级:

- **高**: quality_score >= 0.6
- **中**: 0.4 <= quality_score < 0.6
- **低**: quality_score < 0.4
- **未知**: quality_score 为 NULL

---

## 记忆精炼

### 候选选择

记忆精炼针对以下观察:

1. **低质量**: quality_score < 0.3
2. **陈旧**: 30 天未更新
3. **逾期**: 7 天未精炼

### 冷却机制

精炼后，观察有 7 天冷却期才能再次精炼:

```java
private boolean canRefine(ObservationEntity obs) {
    if (obs.getRefinedAt() == null) return true;
    return obs.getRefinedAt().isBefore(OffsetDateTime.now().minusDays(cooldownDays));
}
```

---

## 经验检索 (ExpRAG)

### 检索流程

```
新任务请求
       │
       ▼
┌──────────────────┐
│ 查找高质量       │
│ 观察             │
│ (quality >= 0.6) │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ 转换为           │
│ 经验格式         │
└──────────────────┘
       │
       ▼
┌──────────────────┐
│ 构建 ICL 提示词 │
│ (含上下文)      │
└──────────────────┘
```

### 经验格式

```java
public record Experience(
    String id,
    String task,           // 任务是什么
    String strategy,       // 如何解决
    String outcome,        // 结果如何
    String reuseCondition, // 何时复用
    float qualityScore,    // 质量评分
    OffsetDateTime createdAt
) {}
```

---

## 特性开关

所有 Evo-Memory 功能可通过配置切换:

```yaml
app:
  memory:
    # 启用/禁用记忆精炼
    refine-enabled: true
    
    # 检索质量阈值
    quality-threshold: 0.6
    
    refine:
      # 删除阈值
      delete-threshold: 0.3
      # 冷却天数
      cooldown-days: 7
      # 陈旧天数
      stale-days: 30
```

### 环境变量

| 变量 | 默认值 | 描述 |
|------|--------|------|
| `MEMORY_REFINE_ENABLED` | true | 启用精炼 |
| `MEMORY_QUALITY_THRESHOLD` | 0.6 | 检索过滤 |
| `MEMORY_REFINE_DELETE_THRESHOLD` | 0.3 | 删除阈值 |
| `MEMORY_REFINE_COOLDOWN_DAYS` | 7 | 冷却期 |
| `MEMORY_REFINE_STALE_DAYS` | 30 | 陈旧阈值 |

---

## 测试

### 测试脚本

| 脚本 | 用途 |
|------|------|
| `scripts/regression-test.sh` | 核心 API 回归测试 |
| `scripts/evo-memory-e2e-test.sh` | Evo-Memory 端到端工作流 |
| `scripts/evo-memory-value-test.sh` | 业务价值验证 |

### 运行测试

```bash
# 回归测试 (24 个测试)
bash scripts/regression-test.sh

# Evo-Memory E2E 测试 (16 个测试)
bash scripts/evo-memory-e2e-test.sh

# 业务价值测试 (7 个测试)
bash scripts/evo-memory-value-test.sh
```

### 测试结果

- **回归测试**: 23/24 通过 (Test 10 因清理预期失败)
- **E2E 测试**: 16/16 通过
- **价值测试**: 7/7 通过

---

## 配置

### 应用属性

位置: `backend/src/main/resources/application.yml`

```yaml
server:
  port: 37777

app:
  memory:
    refine-enabled: ${MEMORY_REFINE_ENABLED:true}
    quality-threshold: ${MEMORY_QUALITY_THRESHOLD:0.6}
    refine:
      delete-threshold: ${MEMORY_REFINE_DELETE_THRESHOLD:0.3}
      cooldown-days: ${MEMORY_REFINE_COOLDOWN_DAYS:7}
      stale-days: ${MEMORY_REFINE_STALE_DAYS:30}
```

### 启动服务

```bash
cd backend
source .env
java -jar target/cortex-ce-0.1.0-beta.jar --spring.profiles.active=dev
```

---

## 局限性与未来工作

### 当前状态 (全部实现 ✅)

Evo-Memory 核心功能已全部实现:

| 功能 | 实现 |
|------|------|
| **质量评分** | ✅ 规则评分 + LLM评分 (LlmQualityScorer) |
| **反馈推断** | ✅ 规则推断 + LLM推断 |
| **记忆合并** | ✅ LLM驱动 mergeObservations() |
| **记忆重写** | ✅ LLM驱动 rewriteObservation() |

### 当前局限性

1. **异步精炼**: 精炼异步发生;结果不会立即可见
2. **非多模态**: 目前仅支持文本

### 未来改进

1. **学习质量**: 基于标注数据训练模型
2. **多模态**: 支持图像、代码等
3. **分布式**: 多节点扩展

---

## 参考资料

- [Evo-Memory 论文](https://arxiv.org/abs/2511.20857)
- [Claude-Mem Java 后端](./backend/README.md)
- [原始实现](./proxy/README.md)

---

## 文档变更日志

| 日期 | 提交 | 描述 |
|------|------|------|
| 2026-03-17 | f06452b | 阶段1: 质量评分实现 |
| 2026-03-17 | 1599f08 | 阶段2: MemoryRefineService |
| 2026-03-17 | b841b0e | 阶段2: ExpRAG + SessionEnd |
| 2026-03-17 | 7a04284 | 阶段3: ReMem API + V12 |
| 2026-03-17 | 606fafc | 特性开关 |
| 2026-03-17 | 4592ecf | E2E 测试 |
| 2026-03-17 | d68510b | 业务价值测试 |

---

> **英文版本**: [evo-memory-implementation.md](./evo-memory-implementation.md)
