[← 返回索引](./index.md)

### 6.2 中期改进（需要一定工程量）

#### 6.2.1 实现 Refine Memory 机制

**新增服务**:

```java
package com.ablueforce.cortexce.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryRefineService {
    
    private static final int REFINE_BATCH_SIZE = 10;
    private static final float QUALITY_THRESHOLD = 0.3f;
    private static final int REFINED_COOLDOWN_DAYS = 7;
    
    private final ObservationRepository observationRepository;
    private final LlmService llmService;
    
    /**
     * 触发记忆精炼（可在 SessionEnd 或定时任务中调用）
     */
    @Async
    public void refineMemory(String projectPath) {
        // 1. 检索候选记忆（低质量 + 长时间未访问）
        List<ObservationEntity> candidates = findRefineCandidates(projectPath);
        
        if (candidates.isEmpty()) return;
        
        // 2. 构造元推理 prompt
        String metaPrompt = buildMetaPrompt(candidates);
        
        // 3. LLM 生成编辑计划
        String planJson = llmService.chatCompletion(
            "You are a memory management assistant. Output only valid JSON.",
            metaPrompt
        );
        RefinePlan plan = parseRefinePlan(planJson);
        
        // 4. 执行编辑计划
        executeRefinePlan(plan);
    }
    
    
    /**
     * 查找需要精炼的候选记忆
     * 
     * 策略: 多维度筛选（与3.5.4节保持一致）
     */
    private List<ObservationEntity> findRefineCandidates(String projectPath) {
        List<ObservationEntity> candidates = new ArrayList<>();
        
        // 1. 删除候选: 质量分 < 0.3
        candidates.addAll(observationRepository
            .findByProjectPathAndQualityScoreLessThan(projectPath, QUALITY_THRESHOLD));
        
        // 2. 合并候选: 同一会话内相似度高的记忆
        candidates.addAll(findMergeCandidates(projectPath));
        
        // 3. 过时候选: 30天未访问且质量分 < 0.6
        candidates.addAll(observationRepository
            .findByProjectPathAndLastAccessedBefore(projectPath, 
                OffsetDateTime.now().minusDays(30), 0.6f));
        
        // 4. 过滤已精炼且在冷却期内的记忆（7天内不重复精炼）
        return candidates.stream()
            .filter(o -> canRefine(o))
            .limit(REFINE_BATCH_SIZE)
            .collect(Collectors.toList());
    }
    
    private boolean canRefine(ObservationEntity obs) {
        if (obs.getRefinedAt() == null) return true; // 从未精炼
        // 允许 7 天后再次精炼
        return obs.getRefinedAt().isBefore(OffsetDateTime.now().minusDays(7));
    }
    
    private List<ObservationEntity> findMergeCandidates(String projectPath) {
        // 查找同一会话内嵌入向量相似度 > 0.8 的记忆对
        // 实现需要根据项目实际情况调整
        return observationRepository.findHighSimilarityPairs(projectPath, 0.8f);
    }
    
    private String buildMetaPrompt(List<ObservationEntity> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是一些记忆条目，请评估它们的质量并决定如何处理：\n\n");
        
        for (int i = 0; i < candidates.size(); i++) {
            ObservationEntity obs = candidates.get(i);
            sb.append("[%d] ID: %s\n".formatted(i, obs.getId()));
            sb.append("内容: %s\n".formatted(obs.getContent()));
            sb.append("质量分: %.2f\n".formatted(obs.getQualityScore()));
            sb.append("创建时间: %s\n\n".formatted(obs.getCreatedAt()));
        }
        
        sb.append("""
            请输出编辑计划（JSON格式）:
            {
              "to_delete": ["id1", "id2"],
              "to_merge": [["id1", "id2"], ["id3", "id4"]],
              "to_rewrite": [{"id": "id", "new_content": "..."}]
            }
            """);
        
        return sb.toString();
    }
    
    private void executeRefinePlan(RefinePlan plan) {
        // 删除
        if (!plan.getToDelete().isEmpty()) {
            observationRepository.deleteAllById(plan.getToDelete());
            log.info("Deleted {} low-quality memories", plan.getToDelete().size());
        }
        
        // 合并
        for (List<UUID> ids : plan.getToMerge()) {
            ObservationEntity merged = mergeObservations(ids);
            observationRepository.save(merged);
        }
        
        // 重写
        for (RefinePlan.RewriteItem item : plan.getToRewrite()) {
            observationRepository.updateContent(item.getId(), item.getNewContent());
        }
    }
    
    private ObservationEntity mergeObservations(List<UUID> ids) {
        List<ObservationEntity> observations = observationRepository.findAllById(ids);
        
        // 构造合并 prompt
        String mergePrompt = buildMergePrompt(observations);
        String mergedContent = llmService.chatCompletion(
            "You are a memory consolidation assistant.",
            mergePrompt
        );
        
        // 创建合并后的 observation
        ObservationEntity merged = new ObservationEntity();
        merged.setContent(mergedContent);
        merged.setQualityScore(
            (float) observations.stream()
                .mapToDouble(ObservationEntity::getQualityScore)
                .average()
                .orElse(0.5)
        );
        // ... 其他字段
        
        // 删除原始 observations
        observationRepository.deleteAll(observations);
        
        return merged;
    }
}
```

**集成到 SessionEnd Hook**:

```java
@Service
public class AgentService {
    
    public void handleSessionEnd(String sessionId) {
        // ... 现有的 summary 生成逻辑
        
        // 触发记忆精炼
        String projectPath = session.getProjectPath();
        memoryRefineService.refineMemory(projectPath);
    }
}
```

#### 6.2.2 经验检索增强 (ExpRAG 风格)

**数据类定义**:

```java
package com.ablueforce.cortexce.model;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 结构化经验数据类
 */
@Data
public class Experience {
    private String taskId;
    private String task;
    private String strategy;
    private String outcome;
    private String reuseCondition;
    private float qualityScore;
    private OffsetDateTime createdAt;
}
```

**改进检索策略**:

```java
package com.ablueforce.cortexce.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpRagService {
    
    private final SearchService searchService;
    
    /**
     * 检索相关经验用于 ICL
     */
    public List<Experience> retrieveExperiences(String currentTask, String projectPath) {
        // 1. 向量相似度检索
        List<ObservationEntity> similar = searchService.searchByVector(
            currentTask, projectPath, 20
        );
        
        // 2. 过滤高质量经验
        List<ObservationEntity> highQuality = similar.stream()
            .filter(o -> o.getQualityScore() >= 0.6f)
            .limit(4)
            .toList();
        
        // 3. 转换为经验格式
        return highQuality.stream()
            .map(obs -> toExperience(obs))
            .toList();
    }
    
    /**
     * 将 ObservationEntity 转换为 Experience 对象
     * 
     * 从结构化 content 字段中提取 task/strategy/outcome 等信息
     */
    private Experience toExperience(ObservationEntity obs) {
        Experience exp = new Experience();
        exp.setTaskId(obs.getId().toString());
        exp.setQualityScore(obs.getQualityScore());
        exp.setCreatedAt(obs.getCreatedAt());
        
        // 从 content 中解析结构化内容
        String content = obs.getContent();
        exp.setTask(ExperienceTemplates.extractTag(content, "task"));
        exp.setStrategy(ExperienceTemplates.extractTag(content, "strategy"));
        exp.setOutcome(ExperienceTemplates.extractTag(content, "outcome"));
        exp.setReuseCondition(ExperienceTemplates.extractTag(content, "reuse_condition"));
        
        return exp;
    }
    
    /**
     * 构造 ICL Prompt
     */
    public String buildICLPrompt(String currentTask, List<Experience> experiences) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("以下是相关的历史经验，可以参考：\n\n");
        
        for (int i = 0; i < experiences.size(); i++) {
            Experience exp = experiences.get(i);
            sb.append("### 经验 %d\n".formatted(i + 1));
            sb.append("**任务**: %s\n".formatted(exp.getTask()));
            sb.append("**策略**: %s\n".formatted(exp.getStrategy()));
            sb.append("**结果**: %s\n".formatted(exp.getOutcome()));
            sb.append("**复用条件**: %s\n\n".formatted(exp.getReuseCondition()));
        }
        
        sb.append("---\n\n");
        sb.append("现在处理当前任务：\n%s\n".formatted(currentTask));
        
        return sb.toString();
    }
}
```

#### 6.2.3 步骤效率追踪

**新增指标**:

```sql
-- V12__step_efficiency.sql (更高编号，预留 V9-V11 给核心功能)
ALTER TABLE mem_sessions 
ADD COLUMN total_steps INT DEFAULT 0;

ALTER TABLE mem_sessions
ADD COLUMN avg_steps_per_task FLOAT;
```

**用途**:
- 跟踪任务完成所需的步骤数
- 评估记忆系统对效率的影响
- 对比不同配置下的步骤效率

#### 6.2.4 上下文预计算与缓存策略

> **关键补充**: 在旁路架构下，可以通过预计算和缓存来减少 SessionStart 时的检索延迟，同时提升上下文质量。

**为什么需要缓存策略**:

| 问题 | 影响 | 解决方案 |
|------|------|---------|
| 每次 SessionStart 重新检索 | 延迟高 (~500ms) | 预计算 + 缓存 |
| 实时检索无法利用精炼结果 | 效果打折 | SessionEnd 时预生成 |
| 多项目并发检索 | 资源竞争 | 缓存分层 |

**缓存架构设计**:

```java
package com.ablueforce.cortexce.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ContextPrecomputeService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RetrievalScoringService retrievalScoringService;
    private final SearchService searchService;

    // 缓存配置
    private static final String CACHE_KEY_PREFIX = "mem:context:";
    private static final Duration CACHE_TTL = Duration.ofHours(24); // 缓存24小时
    private static final int MAX_PRECOMPUTE_COUNT = 10; // 预计算 top-10 上下文

    /**
     * SessionEnd 时预计算并缓存下次会话上下文
     *
     * 触发时机: SessionEnd Hook
     * 缓存键: mem:context:{projectPath}
     */
    @Async
    public void precomputeContext(String projectPath) {
        try {
            log.debug("Precomputing context for project: {}", projectPath);

            // 1. 获取项目的高质量记忆
            List<ObservationEntity> highQualityMemories = searchService
                .searchWithQualityFilter(projectPath, 0.6f, 50);

            // 2. 使用综合评分检索 top-10
            List<RetrievalScoringService.ScoredObservation> topMemories =
                retrievalScoringService.searchWithScoring(projectPath, "", MAX_PRECOMPUTE_COUNT);

            // 3. 生成预计算上下文
            PrecomputedContext context = buildPrecomputedContext(topMemories);

            // 4. 序列化并缓存到 Redis
            String cacheKey = CACHE_KEY_PREFIX + projectPath.replace("/", "_");
            String json = objectMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);

            log.info("Context precomputed for project: {} ({} memories)",
                projectPath, topMemories.size());

        } catch (Exception e) {
            log.error("Failed to precompute context for project: {}", projectPath, e);
        }
    }

    /**
     * SessionStart 时快速获取预计算上下文
     *
     * @return 预计算上下文，如果缓存未命中则返回 null
     */
    public PrecomputedContext getPrecomputedContext(String projectPath) {
        try {
            String cacheKey = CACHE_KEY_PREFIX + projectPath.replace("/", "_");
            String json = redisTemplate.opsForValue().get(cacheKey);

            if (json == null) {
                log.debug("Cache miss for project: {}", projectPath);
                return null;
            }

            return objectMapper.readValue(json, PrecomputedContext.class);

        } catch (Exception e) {
            log.warn("Failed to get precomputed context for project: {}", projectPath, e);
            return null;
        }
    }

    /**
     * 强制刷新缓存（在精炼后调用）
     */
    public void invalidateCache(String projectPath) {
        String cacheKey = CACHE_KEY_PREFIX + projectPath.replace("/", "_");
        redisTemplate.delete(cacheKey);
        log.info("Cache invalidated for project: {}", projectPath);

        // 触发重新预计算
        precomputeContext(projectPath);
    }

    private PrecomputedContext buildPrecomputedContext(
            List<RetrievalScoringService.ScoredObservation> memories) {

        PrecomputedContext context = new PrecomputedContext();
        context.setComputedAt(OffsetDateTime.now());

        List<PrecomputedContext.MemoryEntry> entries = memories.stream()
            .map(scored -> {
                PrecomputedContext.MemoryEntry entry = new PrecomputedContext.MemoryEntry();
                entry.setId(scored.getObservation().getId().toString());
                entry.setTitle(scored.getObservation().getTitle());
                entry.setContent(scored.getObservation().getContent());
                entry.setScore(scored.getScore());
                entry.setQualityScore(scored.getObservation().getQualityScore());
                return entry;
            })
            .toList();

        context.setMemories(entries);

        // 生成摘要
        String summary = generateContextSummary(entries);
        context.setSummary(summary);

        return context;
    }

    private String generateContextSummary(List<PrecomputedContext.MemoryEntry> memories) {
        if (memories.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## 关键经验\n\n");

        for (int i = 0; i < Math.min(3, memories.size()); i++) {
            PrecomputedContext.MemoryEntry m = memories.get(i);
            sb.append(String.format("- %s (质量: %.2f)\n",
                m.getTitle(), m.getQualityScore()));
        }

        return sb.toString();
    }

    @Data
    public static class PrecomputedContext {
        private OffsetDateTime computedAt;
        private List<MemoryEntry> memories;
        private String summary;

        @Data
        public static class MemoryEntry {
            private String id;
            private String title;
            private String content;
            private float score;
            private float qualityScore;
        }
    }
}
```

**缓存命中时的 SessionStart 流程优化**:

```
优化前 (无缓存):
  SessionStart → 向量检索 (500ms) → 构造上下文 → 注入
                                          总计: ~500ms

优化后 (有缓存):
  SessionStart → 获取缓存 (5ms) → 注入
                                    总计: ~5ms
```

**缓存失效策略**:

| 策略 | 触发条件 | TTL |
|------|---------|-----|
| 时间失效 | 缓存创建后 24 小时 | 24h |
| 精炼失效 | MemoryRefineService 执行后 | 立即刷新 |
| 主动失效 | 用户手动触发 | 立即刷新 |
| 容量失效 | Redis 内存不足 | LRU 淘汰 |

**Redis 配置 (application.yml)**:

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

app:
  memory:
    precompute:
      enabled: true
      cache-ttl-hours: 24
      max-memories: 10
```

**实施优先级**:
- Phase 2: 先实现基础缓存（本地 Caffeine Cache）
- Phase 3: 升级为 Redis 分布式缓存（支持多实例）

**与现有 ContextCacheService 的关系**:

现有 `ContextCacheService` 是内存缓存，存储 ContextService 的结果。新的 `ContextPrecomputeService` 是 Redis 缓存，存储预计算的高质量记忆。两者可以共存：
- `ContextCacheService`: 短期缓存（会话级别）
- `ContextPrecomputeContext`: 长期缓存（项目级别，可跨会话）

### 6.3 长期改进（架构级变更）

> **注意**: 完整的 ReMem 实现需要同步干预智能体执行流程，与 Claude-Mem 的旁路架构不兼容。以下仅供参考。

#### 6.3.1 完整的 ReMem 实现（需要智能体核心修改）

**架构扩展**（需要 Claude Code 原生支持）:

```
当前架构:
Hook → Ingestion → Observation（旁路，不影响执行）

理想的 ReMem 架构（需要核心集成）:
Agent Core → Think → Act → Refine（同步循环）
                ↑_______________|
                记忆直接影响推理
```

> **⚠️ 不适用于 Claude-Mem**: 此架构需要直接修改 Claude Code 的执行循环，与旁路架构冲突。

**替代方案**: 在 Java 后端实现"伪同步"效果

```java
/**
 * 旁路架构下的"伪 ReMem"实现
 * 
 * 原理：虽然无法同步干预执行，但可以：
 * 1. 在 SessionEnd 时预测下次可能需要的上下文
 * 2. 预先生成精炼后的记忆摘要
 * 3. 在下次 SessionStart 时快速注入
 */
@Service
public class PseudoReMemService {
    
    /**
     * SessionEnd 时预测下次可能需要的上下文
     */
    @Async
    public void prepareNextSession(String sessionId, String projectPath) {
        // 1. 分析本次会话的主题和上下文
        SessionAnalysis analysis = analyzeSession(sessionId);
        
        // 2. 精炼相关记忆
        List<ObservationEntity> refined = refineMemories(
            projectPath, 
            analysis.getTopicClusters()
        );
        
        // 3. 预生成上下文摘要
        String precomputedContext = generateContextSummary(refined);
        
        // 4. 缓存到 Redis / 内存
        contextCache.put(projectPath, precomputedContext);
    }
    
    /**
     * SessionStart 时快速注入预计算上下文
     */
    public String getPrecomputedContext(String projectPath) {
        return contextCache.get(projectPath);
    }
}
```

**新增控制器**（用于外部集成场景）:

```java
@RestController
@RequestMapping("/api/remem")
public class ReMemController {
    
    @PostMapping("/think")
    public ThinkResponse think(@RequestBody ThinkRequest request) {
        // 1. 检索相关记忆
        List<MemoryEntry> memories = memoryService.retrieve(
            request.getQuery(), 
            request.getContext()
        );
        
        // 2. 构造 Think prompt
        String prompt = promptBuilder.buildThinkPrompt(
            request.getQuery(),
            memories,
            request.getReasoningTrace()
        );
        
        // 3. LLM 生成思考
        String thought = llmService.generate(prompt);
        
        return new ThinkResponse(thought, memories);
    }
    
    @PostMapping("/act")
    public ActResponse act(@RequestBody ActRequest request) {
        // 执行动作并收集反馈
        ActionResult result = actionExecutor.execute(
            request.getAction(),
            request.getEnvironment()
        );
        
        // 写回记忆
        memoryService.writeBack(
            request.getQuery(),
            request.getReasoningTrace(),
            request.getAction(),
            result.getFeedback()
        );
        
        return new ActResponse(result);
    }
    
    @PostMapping("/refine")
    public RefineResponse refine(@RequestBody RefineRequest request) {
        RefinePlan plan = memoryRefineService.generateRefinePlan(
            request.getQuery(),
            request.getCandidates()
        );
        
        memoryRefineService.executeRefinePlan(plan);
        
        return new RefineResponse(plan);
    }
}
```

#### 6.3.2 多模态记忆

**扩展 Schema**:

```sql
-- V13__multimodal_memory.sql (更高编号，预留核心功能)
ALTER TABLE mem_observations 
ADD COLUMN memory_type VARCHAR(20) DEFAULT 'text';  -- 'text', 'code', 'trajectory', 'rule'

ALTER TABLE mem_observations
ADD COLUMN modality VARCHAR(20) DEFAULT 'text';  -- 'text', 'image', 'audio', 'code'

-- 记忆关联
CREATE TABLE mem_memory_relations (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    relation_type VARCHAR(50),  -- 'derives_from', 'abstracts', 'contradicts'
    confidence FLOAT DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 6.3.3 分布式记忆演化

**场景**: 多个 Agent 共享记忆库

```java
@Service
public class DistributedMemoryService {
    
    private final KafkaTemplate<String, MemoryEvent> kafkaTemplate;
    
    /**
     * 发布记忆更新事件
     */
    public void publishMemoryUpdate(MemoryEntry entry) {
        MemoryEvent event = new MemoryEvent(
            entry.getId(),
            entry.getProjectPath(),
            MemoryEvent.Type.UPDATED,
            entry.getContent()
        );
        
        kafkaTemplate.send("memory-updates", event);
    }
    
    /**
     * 消费其他 Agent 的记忆更新
     */
    @KafkaListener(topics = "memory-updates")
    public void onMemoryUpdate(MemoryEvent event) {
        // 合并到本地记忆库
        memoryMerger.merge(event);
    }
}
```

---

