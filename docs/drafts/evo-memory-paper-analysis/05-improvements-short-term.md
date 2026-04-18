[← 返回索引](./index.md)

## 6. 改进方向与实施建议

> **⚠️ 架构约束**: Claude-Mem 是旁路观察者架构，无法同步干预 Claude Code 的执行。所有改进都应遵循"**异步处理 + 延迟生效**"原则。详见 [第8节：旁路架构的适配策略](./07-roadmap-and-bypass-adaptation.md#8-旁路架构的适配策略)。

### 6.1 短期改进（可快速实施）

#### 6.1.1 添加记忆质量评分

> **⚠️ 重要**: 本功能由开关 `app.memory.refine-enabled` 控制。关闭时不影响现有功能，所有新增字段均可为空。

**数据库 Schema 扩展**:

```sql
-- V11__observation_quality.sql
-- 注意: 所有字段均可为空，不影响已有功能
-- 开关: app.memory.refine-enabled = false 时不执行精炼逻辑
ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS quality_score FLOAT;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS feedback_type VARCHAR(20);

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMP;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS access_count INT;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS refined_at TIMESTAMP;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS refined_from_ids TEXT;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS user_comment TEXT;

ALTER TABLE mem_observations
ADD COLUMN IF NOT EXISTS feedback_updated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_quality_score ON mem_observations(quality_score DESC);
CREATE INDEX IF NOT EXISTS idx_last_accessed ON mem_observations(last_accessed_at);
CREATE INDEX IF NOT EXISTS idx_refined_at ON mem_observations(refined_at);
```

**Java 实现**:

```java
public enum FeedbackType {
    SUCCESS,    // 任务成功完成
    PARTIAL,    // 部分成功
    FAILURE,    // 任务失败
    UNKNOWN     // 无反馈信息
}

public class QualityScorer {
    
    public float estimateQuality(FeedbackType feedback, 
                                  String reasoningTrace,
                                  String output,
                                  int toolUsageCount) {
        // 基础分数来自反馈类型
        float baseScore = switch (feedback) {
            case SUCCESS -> 0.75f;
            case PARTIAL -> 0.50f;
            case FAILURE -> 0.20f;
            case UNKNOWN -> 0.50f;
        };
        
        // 效率调整（工具使用次数）
        float efficiencyBonus = Math.max(0, 0.1f - (toolUsageCount - 3) * 0.02f);
        
        // 内容质量调整（基于长度和结构）
        float contentBonus = evaluateContentQuality(reasoningTrace, output);
        
        float finalScore = baseScore + efficiencyBonus + contentBonus;
        
        // LLM 自评（可选）
        if (enableLLMEvaluation && feedback == FeedbackType.SUCCESS) {
            float llmScore = llmEvaluate(output);
            finalScore = (finalScore * 0.7f) + (llmScore * 0.3f);
        }
        
        return Math.min(1.0f, Math.max(0.0f, finalScore));
    }
    
    // 辅助方法定义见附录 A.1
    private float evaluateContentQuality(String reasoningTrace, String output) { /* 见附录 A.1 */ }
    private float llmEvaluate(String output) { /* 见附录 A.1 */ }
}
```

**检索时考虑质量**:

```java
@Repository
public interface ObservationRepository extends JpaRepository<ObservationEntity, Long> {
    
    @Query("""
        SELECT o FROM ObservationEntity o 
        WHERE o.projectPath = :project 
        AND o.qualityScore >= :minQuality
        ORDER BY cosine_similarity(o.embedding, :query) DESC
        LIMIT :limit
        """)
    List<ObservationEntity> searchWithQuality(
        @Param("project") String project,
        @Param("query") float[] query,
        @Param("minQuality") float minQuality,
        @Param("limit") int limit
    );
}
```

**检索评分公式（含时间衰减）**:

> **关键补充**: 简单的质量过滤不足以优化检索，应该使用加权评分综合考虑多个因素。

```java
package com.ablueforce.cortexce.service;

import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RetrievalScoringService {

    private static final float QUALITY_WEIGHT = 0.5f;      // 质量分权重
    private static final float RECENCY_WEIGHT = 0.3f;      // 新鲜度权重
    private static final float ACCESS_WEIGHT = 0.2f;       // 访问频率权重

    private static final double TIME_DECAY_HALF_LIFE_DAYS = 30.0; // 30天后新鲜度衰减一半

    /**
     * 计算记忆条目的综合检索评分
     *
     * 公式: score = quality * Wq + recency * Wr + access * Wa
     *
     * @param qualityScore 质量分数 [0, 1]
     * @param createdAt    创建时间
     * @param accessCount  访问次数
     * @return 综合评分 [0, 1]
     */
    public float calculateRetrievalScore(float qualityScore,
                                        OffsetDateTime createdAt,
                                        int accessCount) {
        // 1. 质量分归一化
        float normalizedQuality = Math.max(0, Math.min(1, qualityScore));

        // 2. 时间衰减计算 (指数衰减)
        long daysSinceCreated = ChronoUnit.DAYS.between(createdAt, OffsetDateTime.now());
        double decayFactor = Math.pow(0.5, daysSinceCreated / TIME_DECAY_HALF_LIFE_DAYS);
        float recencyScore = (float) decayFactor;

        // 3. 访问次数归一化 (使用 log 避免极端值)
        float accessScore = (float) (Math.log(1 + accessCount) / Math.log(1 + 100)); // 假设100次为上限

        // 4. 加权计算
        float finalScore = normalizedQuality * QUALITY_WEIGHT
                         + recencyScore * RECENCY_WEIGHT
                         + accessScore * ACCESS_WEIGHT;

        return Math.max(0, Math.min(1, finalScore));
    }

    /**
     * 带评分的检索方法
     */
    public List<ScoredObservation> searchWithScoring(String projectPath,
                                                     String query,
                                                     int limit) {
        // 1. 向量检索获取候选
        List<ObservationEntity> candidates = searchService.searchByVector(
            query, projectPath, limit * 3 // 获取更多候选用于重排序
        );

        // 2. 计算每个候选的综合评分
        List<ScoredObservation> scored = candidates.stream()
            .map(obs -> {
                float score = calculateRetrievalScore(
                    obs.getQualityScore() != null ? obs.getQualityScore() : 0.5f,
                    obs.getCreatedAt(),
                    obs.getAccessCount() != null ? obs.getAccessCount() : 0
                );
                return new ScoredObservation(obs, score);
            })
            .sorted(Comparator.comparingDouble(ScoredObservation::getScore).reversed())
            .limit(limit)
            .toList();

        // 3. 更新访问次数
        updateAccessCounts(scored);

        return scored;
    }

    private void updateAccessCounts(List<ScoredObservation> scored) {
        for (ScoredObservation so : scored) {
            ObservationEntity obs = so.getObservation();
            if (obs.getAccessCount() == null) {
                obs.setAccessCount(1);
            } else {
                obs.setAccessCount(obs.getAccessCount() + 1);
            }
            obs.setLastAccessedAt(OffsetDateTime.now());
        }
        observationRepository.saveAll(
            scored.stream()
                .map(ScoredObservation::getObservation)
                .toList()
        );
    }

    @Data
    public static class ScoredObservation {
        private final ObservationEntity observation;
        private final float score;
    }
}
```

**评分公式可视化**:

```
评分随时间变化示例 (quality=0.8, accessCount=5):

时间(天)  新鲜度分  综合分
0         1.00      0.80
7         0.85      0.70
30        0.50      0.50
60        0.25      0.38
90        0.12      0.30
```

**检索策略对比**:

| 策略 | 公式 | 适用场景 |
|------|------|---------|
| 纯向量 | cosine_similarity | 语义相似度优先 |
| 质量过滤 | quality >= 0.6 | 过滤噪音，保留高质量 |
| 时间衰减 | quality * 0.7 + recency * 0.3 | 平衡质量与新鲜度 |
| 综合评分 | 质量 + 新鲜度 + 访问 | 最优平衡 |

**实施建议**:
- Phase 1: 先使用简单的质量过滤
- Phase 2: 升级为综合评分检索

#### 6.1.2 反馈获取机制（关键补充）

> **⚠️ 重要说明**: 质量评分需要反馈信息作为输入。在旁路架构下，有两种反馈来源：
> 1. **自动推断**：SessionEnd 时从可用信息推断
> 2. **人工审核**：通过 WebUI 用户事后审核给出

**旁路架构下反馈来源分析**:

| 来源 | 可用性 | 可靠性 | 说明 |
|------|--------|--------|------|
| **WebUI 人工审核** | ✅ 可用 | ⭐⭐⭐ 高 | 用户事后审核记忆，直接给出反馈（最高优先级） |
| last_assistant_message | ✅ 可用 | ⭐⭐ 中 | Claude 的最后回复，可能包含完成状态 |
| observation 数量 | ✅ 可用 | ⭐ 低 | 过少可能表示失败 |
| 会话持续时间 | ✅ 可用 | ⭐ 低 | 过短可能表示失败 |

**WebUI 用户反馈机制**（新增）:

Claude-Mem 自带 WebUI，用户可以事后审核记忆记录并给出反馈：

```java
package com.ablueforce.cortexce.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.time.OffsetDateTime;

// WebUI 反馈 API
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    /**
     * 用户对记忆进行事后审核反馈
     *
     * @param observationId 记忆 ID
     * @param feedbackType 反馈类型: success / partial / failure / unknown
     * @param comment 用户评论（可选）
     */
    @PostMapping("/observation/{id}")
    public ResponseEntity<Void> submitObservationFeedback(
            @PathVariable("id") Long observationId,
            @RequestParam FeedbackType feedbackType,
            @RequestParam(required = false) String comment) {

        observationRepository.findById(observationId).ifPresent(obs -> {
            obs.setFeedbackType(feedbackType.name().toLowerCase());
            obs.setUserComment(comment);
            obs.setFeedbackUpdatedAt(OffsetDateTime.now());
            observationRepository.save(obs);

            // 重新计算质量分数
            float newQuality = qualityScorer.recalculateWithFeedback(obs);
            obs.setQualityScore(newQuality);
            observationRepository.save(obs);

            log.info("User feedback applied to observation {}: {}",
                observationId, feedbackType);
        });

        return ResponseEntity.ok().build();
    }
}
```

**反馈优先级**:
1. **WebUI 人工反馈** > 自动推断（用户审核更准确）
2. 自动推断仅在无人工反馈时使用

**推荐反馈推断策略**:

```java
package com.ablueforce.cortexce.service;

import org.springframework.stereotype.Service;

@Service
public class FeedbackInferenceService {

    /**
     * 从 SessionEnd 可用信息推断反馈类型
     *
     * @param lastAssistantMessage Claude 的最后回复
     * @param observationCount     Observation 数量
     * @param sessionDurationMs    会话持续时间（毫秒）
     * @return 推断的反馈类型
     */
    public FeedbackType inferFeedback(String lastAssistantMessage,
                                      int observationCount,
                                      long sessionDurationMs) {
        // 1. 从最后回复中解析成功/失败信号
        if (lastAssistantMessage != null) {
            String lowerMsg = lastAssistantMessage.toLowerCase();

            // 成功信号
            if (containsSuccessSignal(lowerMsg)) {
                return FeedbackType.SUCCESS;
            }

            // 失败/未完成信号
            if (containsFailureSignal(lowerMsg)) {
                return FeedbackType.FAILURE;
            }
        }

        // 2. 基于观察数量的启发式判断
        if (observationCount == 0) {
            return FeedbackType.FAILURE; // 无任何有效操作
        }
        if (observationCount < 3) {
            return FeedbackType.FAILURE; // 工具使用过少，可能受阻
        }

        // 3. 基于会话时长（过短可能表示失败）
        if (sessionDurationMs < 5000 && observationCount < 5) {
            return FeedbackType.FAILURE;
        }

        // 4. 默认返回 PARTIAL（有进展但可能未完成）
        return FeedbackType.PARTIAL;
    }

    private boolean containsSuccessSignal(String msg) {
        return msg.contains("完成") || msg.contains("解决")
            || msg.contains("completed") || msg.contains("finished")
            || msg.contains("done") || msg.contains("solved")
            || msg.contains("已解决") || msg.contains("成功了");
    }

    private boolean containsFailureSignal(String msg) {
        return msg.contains("无法") || msg.contains("失败")
            || msg.contains("failed") || msg.contains("cannot")
            || msg.contains("unable") || msg.contains("错误")
            || msg.contains("error") || msg.contains("无法完成");
    }
}
```

**集成到 SessionEnd**:

```java
// 在 AgentService.completeSessionAsync() 中添加
@Autowired
private FeedbackInferenceService feedbackInferenceService;

public void completeSessionAsync(String contentSessionId, String lastAssistantMessage) {
    // ... 现有逻辑 ...

    // 1. 计算会话持续时间
    long sessionDurationMs = session.getCompletedAtEpoch() - session.getCreatedAtEpoch();

    // 2. 推断反馈类型
    FeedbackType feedback = feedbackInferenceService.inferFeedback(
        lastAssistantMessage,
        observations.size(),
        sessionDurationMs
    );

    // 3. 为每个 observation 分配反馈类型和质量分
    for (ObservationEntity obs : observations) {
        obs.setFeedbackType(feedback.name().toLowerCase());
        float quality = qualityScorer.estimateQuality(
            feedback,
            obs.getContent(),
            obs.getFacts(),
            observations.size()
        );
        obs.setQualityScore(quality);
    }
    observationRepository.saveAll(observations);

    // ... 继续生成 summary ...
}
```

**实施要点**:
- 反馈推断是"尽力而为"，不可能 100% 准确
- 建议在运行一段时间后，根据实际效果调整启发式规则
- UNKNOWN 类型应作为 fallback，主要用于冷启动阶段

#### 6.1.3 经验结构化模板

**当前**: 自由文本 observation

**改进**: 结构化经验模板

```java
public class ExperienceTemplate {
    
    public String buildExperienceText(
            String taskInput,
            String reasoningTrace,
            String action,
            String outcome,
            List<String> keyLearnings) {
        
        return """
            ## Task
            %s
            
            ## Reasoning Process
            %s
            
            ## Action Taken
            %s
            
            ## Outcome
            %s
            
            ## Key Learnings
            %s
            
            ## When to Reuse
            This experience is useful when: [auto-generated condition]
            """.formatted(
                taskInput,
                reasoningTrace,
                action,
                outcome,
                String.join("\n- ", keyLearnings)
            );
    }
}
```

**LLM Prompt 增强**:

```
从以下工具使用事件中提取经验，重点关注:
1. 采取了什么策略
2. 为什么这个策略有效/无效
3. 什么情况下可以复用这个策略

格式要求:
<observation>
<task>任务描述</task>
<strategy>采用的策略</strategy>
<outcome>结果</outcome>
<reuse_condition>复用条件</reuse_condition>
<key_learning>关键学习点</key_learning>
</observation>
```

#### 6.1.4 任务流追踪

**新增表**:

```sql
-- V10__task_streams.sql (V9 分配给 session_metrics)
CREATE TABLE mem_task_streams (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    project_path VARCHAR(512),
    task_sequence INT NOT NULL,  -- 任务在流中的序号
    task_input TEXT,
    task_output TEXT,
    parent_task_id BIGINT,       -- 依赖的前置任务
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_task_id) REFERENCES mem_task_streams(id)
);

CREATE INDEX idx_task_stream_session ON mem_task_streams(session_id, task_sequence);
```

**用途**:
- 跟踪同一会话中的任务序列
- 识别任务依赖关系
- 支持基于任务流的上下文检索

#### 6.1.5 精炼时机设计

> **核心设计**: 精炼触发分为两大类：**事件触发**（最实时）和**定时触发**（分高频和深度）

---

##### 一、事件触发（最实时）

| 事件 | 触发条件 | 精炼逻辑 |
|------|---------|---------|
| **SessionEnd** | 用户结束会话 | 处理当前会话内新产生的记忆：评估质量分、删除低质量、合并会话内相似记忆 |

```java
// AgentService.completeSessionAsync() 中
public void completeSessionAsync(String contentSessionId, String lastAssistantMessage) {
    // 1. 现有逻辑：生成 Summary

    // 2. 触发即时精炼（当前会话，最实时）
    memoryRefineService.refineCurrentSession(session);
}
```

---

##### 二、定时触发（分高频和深度）

**为什么需要定时触发**:
- 事件触发只处理当前会话的新记忆
- 跨会话积累的低质量记忆需要定时清理
- 合并跨会话的相似记忆需要全局视角

| 层级 | 间隔 | 触发条件 | 精炼逻辑 | 资源 |
|------|------|---------|---------|------|
| **高频** | 15分钟 | 检测到积压 | 快速清理少量低质量记忆 + 轻量级精炼 | 低 |
| **深度** | 每天凌晨 | 定时 | 跨会话大规模合并 | 高 |

---

**精炼时机总结**:

| 触发方式 | 时机 | 间隔 | 实时性 |
|---------|------|------|-------|
| **事件触发** | SessionEnd | 会话结束 | ⭐⭐⭐ 最实时 |
| **定时-高频** | 检测到积压 | 15分钟 | ⭐⭐ 接近实时 |
| **定时-深度** | 每天 | 24小时 | 深度优化 |

---

#### 6.1.6 定时增量精炼任务

**两级精炼检测策略**（优化 LLM 调用效率）:

> **核心优化**: 不是每次定时任务都调用 LLM 精炼，而是先做快速检测，只在需要时才调用 LLM。

```java
package com.ablueforce.cortexce.service;

/**
 * 两级精炼检测策略
 *
 * 第一级（快速检测）: 不调用 LLM，通过编码的启发式规则判断
 *   - 质量分低于阈值？
 *   - 超过一定时间未访问？
 *   - 与其他记忆相似度过高（可合并）？
 *
 * 第二级（深度检测）: 如果第一级通过，调用 LLM 进行精炼
 *   - LLM 判断具体如何合并/重写/删除
 *   - 成本较高，但更智能
 */
@Service
public class RefineDetectionService {

    @Value("${app.memory.refine.quality-threshold:0.3}")
    private float qualityThreshold;

    @Value("${app.memory.refine.days-threshold:30}")
    private int daysThreshold;

    @Value("${app.memory.refine.similarity-threshold:0.85}")
    private float similarityThreshold;

    /**
     * 第一级检测：快速启发式检测（无需 LLM）
     *
     * @return true 表示需要进一步精炼处理
     */
    public boolean needsRefinement(String projectPath) {
        // 1. 检查是否有低质量记忆
        long lowQualityCount = observationRepository
            .countByProjectPathAndQualityScoreLessThan(projectPath, qualityThreshold);

        if (lowQualityCount > 10) {
            log.info("Project {} has {} low-quality observations, needs refinement",
                projectPath, lowQualityCount);
            return true;
        }

        // 2. 检查是否有超过阈值未访问的记忆
        long staleCount = observationRepository
            .countByProjectPathAndLastAccessedBefore(
                projectPath, OffsetDateTime.now().minusDays(daysThreshold));

        if (staleCount > 20) {
            log.info("Project {} has {} stale observations, needs refinement",
                projectPath, staleCount);
            return true;
        }

        // 3. 检查是否有可合并的相似记忆
        // (通过向量检索找出相似记忆对)
        long mergeableCount = estimateMergeableCount(projectPath);

        if (mergeableCount > 5) {
            log.info("Project {} has {} potentially mergeable observations",
                projectPath, mergeableCount);
            return true;
        }

        // 4. 检查是否有待精炼的记忆超过冷却期
        long overdueCount = observationRepository
            .countOverdueForRefine(projectPath, OffsetDateTime.now().minusDays(7));

        if (overdueCount > 15) {
            return true;
        }

        return false; // 不需要精炼
    }

    private long estimateMergeableCount(String projectPath) {
        // 简化实现：统计相似记忆对数量
        // 实际实现可通过采样向量计算
        return observationRepository
            .countSimilarPairs(projectPath, similarityThreshold);
    }
}
```

**定时任务设计**:

```java
package com.ablueforce.cortexce.scheduled;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ablueforce.cortexce.service.MemoryRefineService;
import com.ablueforce.cortexce.service.RefineDetectionService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryRefineScheduledTask {

    private final MemoryRefineService memoryRefineService;
    private final RefineDetectionService refineDetectionService;

    /**
     * 高频增量精炼 - 每 15 分钟执行一次
     *
     * 目标: 尽可能接近 ReMem 的"持续演化"效果
     *
     * 策略:
     * 1. 第一级检测：快速启发式检查（无需 LLM）
     * 2. 如果需要，触发第二级：LLM 精炼（轻量级，每次最多5条）
     * 3. 不做跨会话深度合并
     *
     * 配合 SessionEnd 精炼:
     * - SessionEnd: 处理当前会话（最实时）
     * - 每15分钟: 清理积压（接近实时）
     * - 每天: 深度合并和规则提取
     */
    @Scheduled(fixedRateString = "${app.memory.refine.frequent-interval-ms:900000}")
    public void frequentRefine() {
        log.debug("Starting frequent memory refine (15min interval)");

        try {
            List<String> projects = projectService.getAllProjects();

            for (String project : projects) {
                // 只处理积压的低优先级记忆
                memoryRefineService.quickRefine(project, 5); // 最多5条
            }

        } catch (Exception e) {
            log.error("Frequent refine task failed", e);
        }
    }

    /**
     * 每天凌晨 4:00 执行深度精炼（相比普通精炼更激进地合并）
     * - 包括跨会话相似记忆合并
     * - 规则提取和抽象
     *
     * 设计理由: 深度精炼比普通精炼更消耗资源，
     * 但为了接近 ReMem 的持续演化效果，需要每天执行
     */
    @Scheduled(cron = "${app.memory.deep-refine-cron:0 0 4 * * ?}")
    public void scheduledDeepRefine() {
        log.info("Starting scheduled deep memory refine");

        try {
            List<String> projects = projectService.getAllProjects();

            for (String project : projects) {
                try {
                    memoryRefineService.deepRefineProjectMemories(project);
                } catch (Exception e) {
                    log.error("Failed to deep refine project: {}", project, e);
                }
            }

            log.info("Scheduled deep refine completed for {} projects", projects.size());

        } catch (Exception e) {
            log.error("Scheduled deep refine task failed", e);
        }
    }
}
```

**Spring Boot 启用定时任务配置**:

```java
// 在主应用类上添加 @EnableScheduling
@SpringBootApplication
@EnableScheduling
public class ClaudeMemApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaudeMemApplication.class, args);
    }
}
```

**application.yml 配置**:

```yaml
app:
  memory:
    # 深度精炼 cron（每天凌晨4点）
    deep-refine-cron: "0 0 4 * * ?"
    # 高频精炼间隔（15分钟，快速清理积压 + 轻量级精炼）
    frequent-interval-ms: 900000
```

**精炼任务分层设计**（更接近 ReMem 实时效果）:

| 层级 | 触发时机 | 间隔 | 范围 | LLM调用 | 适用场景 |
|------|---------|------|------|---------|---------|
| **L1: SessionEnd** | 会话结束 | 实时 | 当前会话 | ✅ 是 | 每次会话后立即处理 |
| **L2: 高频** | 定时 | 15分钟 | 少量积压 + 轻量级精炼 | ✅ 是 | 接近实时清理 |
| **L3: 深度** | 定时 | 每天 | 全量+跨会话 | ✅ 是 | 规则提取和抽象 |

**为什么需要多层设计**:
- 旁路系统无法修改 Agent 循环，但可以尽可能接近实时效果
- L1 (SessionEnd) 是最实时的，每次会话结束立即处理
- L2 (高频) 每15分钟处理积压，进一步缩短延迟
- 多层设计避免单次任务过重，同时保证持续演化

**实施优先级**:
- Phase 1: 先实现 SessionEnd 精炼
- Phase 2: 添加定时精炼任务

