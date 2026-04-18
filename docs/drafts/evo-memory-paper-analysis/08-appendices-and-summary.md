[← 返回索引](./index.md)

## 附录 A: 代码示例

### A.1 QualityScorer（实现以仓库为准）

> **自检（相对当前代码库）**：此前附录中的「完整实现」示例（例如依赖 `LlmService` + Lombok `@Slf4j`）**已与现网代码不一致**。下列摘要与仓库中的 [`QualityScorer.java`](../../../backend/src/main/java/com/ablueforce/cortexce/service/QualityScorer.java) 对齐；细节与常量请以源码为准。

- **依赖**：`LlmQualityScorer`（可选 LLM 路径），**不是**「仅注入 `LlmService`」的旧版构造方式。
- **规则打分**：`estimateQuality(FeedbackType, String reasoningTrace, String output, int toolUsageCount)`；另有简化重载 `estimateQuality(FeedbackType, int)`。
- **LLM 增强**：`estimateQualityWithLlm(...)`，在 `LlmQualityScorer` 不可用时回退规则路径。
- **其它公开 API**：`inferFeedbackWithLlm(...)`、`recalculateWithFeedback(...)`、`parseFeedbackType(...)`、`isLlmAvailable()`。
- **`FeedbackType`**：为 `QualityScorer` 的**内嵌枚举**（`SUCCESS` / `PARTIAL` / `FAILURE` / `UNKNOWN`）。

#### 规则路径打分逻辑（保留原附录的算法直觉，并与源码对齐）

下列与 [`QualityScorer`](../../../backend/src/main/java/com/ablueforce/cortexce/service/QualityScorer.java) 中 `getBaseScore`、`calculateEfficiencyBonus`、`calculateContentBonus` 一致；若未来调整常量，**以源码为准**。

| 组成部分 | 规则摘要 |
|----------|----------|
| **基准分** | `SUCCESS` 0.75、`PARTIAL` 0.50、`FAILURE` 0.20、`UNKNOWN` 或 `feedback == null` 时 0.50 |
| **效率加成** | 上限 0.10；工具调用数不超过 3 取满额；超出部分按每工具 0.02 递减，结果不低于 0 |
| **内容加成** | 上限 0.15；将推理与输出拼接后按长度计算：不足 100 字符无加成；不少于 500 字符取满；中间线性插值 |
| **输出** | 总和限制在 **[0, 1]** |

**相对旧附录“完整代码块”的说明**：旧稿在 `estimateQuality` 内混写 `LlmService` 自评；当前实现将 **纯规则打分** 与 **`LlmQualityScorer` 的分析入口**（如 `estimateQualityWithLlm`）分离，避免依赖关系与真实类图不一致。

### A.2 经验模板（讲解用片段）

> **自检**：下列 `ExperienceTemplates` 为**便于说明 ExpRAG / ICL 格式的示例类**，仓库中**未必存在**同名文件；若需对照真实逻辑，请优先查看 [`ExpRagService.java`](../../../backend/src/main/java/com/ablueforce/cortexce/service/ExpRagService.java) 与 `classpath:modes/`、`claudemem.mode` 相关配置。

```java
package com.ablueforce.cortexce.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExperienceTemplates {
    
    /**
     * ExpRAG 风格的经验格式
     */
    public static final String EXPRAG_TEMPLATE = """
        ## Previous Experience
        
        **Task**: {task}
        **Approach**: {strategy}
        **Result**: {outcome}
        **When to Reuse**: {reuse_condition}
        
        ---
        """;
    
    /**
     * ReMem Think prompt 模板
     */
    public static final String THINK_PROMPT = """
        You are solving a task. Here are some relevant experiences from memory:
        
        {memories}
        
        Current task: {query}
        
        Previous reasoning:
        {reasoning_trace}
        
        Continue your reasoning. Think about:
        1. What can you learn from the experiences above?
        2. What is the best approach for this task?
        3. What are the potential pitfalls?
        
        Output your reasoning:
        """;
    
    /**
     * Refine prompt 模板
     */
    public static final String REFINE_PROMPT = """
        You are managing a memory system. Here are some memory entries:
        
        {candidates}
        
        Evaluate these memories and decide:
        
        1. Which entries should be DELETED (low quality, outdated, or noisy)?
        2. Which entries should be MERGED (similar content, can be combined)?
        3. Which entries should be REWRITTEN (good content but poor phrasing)?
        
        Output JSON:
        {
          "to_delete": [id1, id2],
          "to_merge": [[id1, id2], [id3, id4]],
          "to_rewrite": [{"id": id, "new_content": "..."}]
        }
        """;
    
    /**
     * 经验格式化（用于 ICL）
     * 
     * 注意: ObservationEntity 的 content 字段存储结构化经验文本，
     * 包含 <task>/<strategy>/<outcome>/<key_learning> 等标签内容。
     * 此方法从 content 中解析提取这些信息。
     */
    public static String formatExperienceForICL(ObservationEntity obs) {
        String content = obs.getContent();
        
        return """
            ### Experience (ID: %s, Quality: %.2f)
            
            **Task**: %s
            **Strategy**: %s
            **Outcome**: %s
            **Reuse Condition**: %s
            
            **Key Learning**: %s
            """.formatted(
                obs.getId(),
                obs.getQualityScore(),
                extractTag(content, "task"),
                extractTag(content, "strategy"),
                extractTag(content, "outcome"),
                extractTag(content, "reuse_condition"),
                extractTag(content, "key_learning")
            );
    }
    
    /**
     * 从结构化 content 中提取指定标签的内容
     */
    private static String extractTag(String content, String tagName) {
        // 匹配 <tag>content</tag> 格式
        Pattern pattern = Pattern.compile(
            "<" + tagName + ">(.*?)</" + tagName + ">", 
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 回退: 查找 "Key Learning:" 格式
        if (tagName.equals("key_learning") && content.contains("Key Learning:")) {
            int start = content.indexOf("Key Learning:");
            String remaining = content.substring(start + "Key Learning:".length());
            return remaining.split("\n")[0].trim();
        }
        return "[未找到]";
    }
}
```

---

## 附录 B: 测试用例

> **自检（相对当前代码库）**：以下为**示意性** Spring Boot 测试；截至修订时，`backend/src/test` 下**未发现**与之同名的 `QualityScorerTest` / `MemoryRefineServiceTest` 类。落地测试时请按实际 API 与仓库测试布局新建。

### B.1 QualityScorer 测试

```java
@SpringBootTest
class QualityScorerTest {
    
    @Autowired
    private QualityScorer qualityScorer;
    
    @Test
    void testSuccessFeedback() {
        float score = qualityScorer.estimateQuality(
            FeedbackType.SUCCESS,
            "Some reasoning",
            "Good output",
            3
        );
        
        assertThat(score).isGreaterThanOrEqualTo(0.7f);
        assertThat(score).isLessThanOrEqualTo(1.0f);
    }
    
    @Test
    void testFailureFeedback() {
        float score = qualityScorer.estimateQuality(
            FeedbackType.FAILURE,
            "Some reasoning",
            "Bad output",
            10
        );
        
        assertThat(score).isLessThan(0.5f);
    }
    
    @Test
    void testEfficiencyBonus() {
        float efficientScore = qualityScorer.estimateQuality(
            FeedbackType.SUCCESS, "", "", 2
        );
        
        float inefficientScore = qualityScorer.estimateQuality(
            FeedbackType.SUCCESS, "", "", 10
        );
        
        assertThat(efficientScore).isGreaterThan(inefficientScore);
    }
}
```

### B.2 MemoryRefineService 测试

```java
@SpringBootTest
class MemoryRefineServiceTest {
    
    @Autowired
    private MemoryRefineService refineService;
    
    @Autowired
    private ObservationRepository observationRepo;
    
    @Test
    void testRefineDeletesLowQuality() {
        // 创建低质量 observation
        ObservationEntity lowQuality = new ObservationEntity();
        lowQuality.setContent("Test content");
        lowQuality.setQualityScore(0.1f);
        lowQuality = observationRepo.save(lowQuality);
        
        // 执行 refine
        refineService.refineMemory("test-project");
        
        // 验证被删除
        assertThat(observationRepo.findById(lowQuality.getId())).isEmpty();
    }
    
    @Test
    void testRefineMergesSimilar() {
        // 创建相似 observations
        ObservationEntity obs1 = createObservation("Fix bug in file A", 0.7f);
        ObservationEntity obs2 = createObservation("Fix bug in file A", 0.7f);
        
        // 执行 refine
        refineService.refineMemory("test-project");
        
        // 验证合并
        List<ObservationEntity> remaining = observationRepo.findByContentContaining("Fix bug");
        assertThat(remaining).hasSize(1);
    }
}
```

---

## 附录 C: 配置示例

### C.1 application.yml（与仓库一致的节选）

以下摘自仓库 [`application.yml`](../../../backend/src/main/resources/application.yml) 中与 Evo-Memory 特性开关、`app.memory` 相关的**真实片段**（环境变量占位保持原样）。此前附录中的杜撰键（如顶层 `exprag:`、`refine-batch-size` 等与当前文件不符）已移除。

```yaml
app:
  memory:
    refine-enabled: ${MEMORY_REFINE_ENABLED:true}
    quality-threshold: ${MEMORY_QUALITY_THRESHOLD:0.6}
    refine:
      delete-threshold: ${MEMORY_REFINE_DELETE_THRESHOLD:0.3}
      cooldown-days: ${MEMORY_REFINE_COOLDOWN_DAYS:7}
      stale-days: ${MEMORY_REFINE_STALE_DAYS:30}
    extraction:
      enabled: ${EXTRACTION_ENABLED:false}
      initial-run-max-candidates: ${EXTRACTION_MAX_CANDIDATES:100}
      max-observations-per-batch: ${EXTRACTION_BATCH_SIZE:20}
      max-batches-per-template: ${EXTRACTION_MAX_BATCHES:10}
      templates:
        - name: "user_preference"
          enabled: true
          # ...（其余模板字段略，见源码）
```

### C.2 Prompt / Mode 配置（概念示例）

> **自检**：当前主配置以 `claudemem.mode`、`modes-dir`、`classpath:modes/` 等为主（见同一 `application.yml` 后部），**不一定**存在本段所示的顶层 `prompts:` 结构。以下为**仍可用于对照论文「提示组织方式」**的 YAML 示意；与运行中实际 prompt 不一致时，以仓库内 `modes` 与代码为准。

```yaml
# 以下为概念示例，非仓库内的唯一权威结构
prompts:
  observation:
    path: classpath:prompts/observation.txt
  experience:
    path: classpath:prompts/experience.txt
```

### C.3 拆分前附录 C 全文存档（保留以便核对）

> **用途**：下列为**单文件版**文档中附录 C 的原文（含长 prompt 草稿）。其中部分键（如顶层 `exprag:`、若干 `memory` 下的细化项）**已被证实与当时/当前 `application.yml` 不一致或尚未落地**，请勿当作运行中唯一配置；**运行时请以仓库内 [`application.yml`](../../../backend/src/main/resources/application.yml) + `claudemem.*` 为准**。此处仅保证「旧稿信息不因修订而丢失」。

#### 存档：原附录 C.1（摘自拆分前正文）

```yaml
app:
  quality:
    # 是否启用 LLM 质量评估
    llm-evaluation: false

  memory:
    # 是否启用记忆精炼
    refine-enabled: true
    # Refine 触发阈值（记忆数量）
    refine-threshold: 100
    # 每次 refine 处理的最大数量
    refine-batch-size: 10
    # 质量分数阈值（低于此值会被精炼删除）
    quality-threshold: 0.3
    # 过期时间阈值（天）
    days-threshold: 30
    # 相似度阈值（用于合并检测）
    similarity-threshold: 0.85
    # 深度精炼 cron（每天凌晨4点）
    deep-refine-cron: "0 0 4 * * ?"
    # 高频精炼间隔（15分钟，快速清理积压 + 轻量级精炼）
    frequent-interval-ms: 900000

  exprag:
    # ExpRAG 检索数量
    retrieve-count: 4
    # 是否在 session-start 时注入经验
    inject-on-start: true
```

#### 存档：原附录 C.2（摘自拆分前正文）

```yaml
prompts:
  observation:
    path: classpath:prompts/observation.txt
    
  # 新增：经验提取 prompt
  experience:
    path: classpath:prompts/experience.txt
    template: |
      从以下工具使用事件中提取结构化经验：
      
      事件: {event}
      
      请输出：
      <experience>
      <task>任务描述</task>
      <strategy>采用的策略</strategy>
      <outcome>结果</outcome>
      <reuse_condition>什么情况下可以复用这个策略</reuse_condition>
      <key_learning>关键学习点</key_learning>
      </experience>
  
  # 新增：Refine prompt
  refine:
    path: classpath:prompts/refine.txt
    template: |
      评估以下记忆条目并决定如何处理...
```

## 附录 D: 数据模型与 Migration（对照仓库）

> **自检**：本节区分「Flyway 中已存在」与「仍为设想」。字段级真相来源为 [`ObservationEntity.java`](../../../backend/src/main/java/com/ablueforce/cortexce/entity/ObservationEntity.java)；迁移脚本目录为 [`backend/src/main/resources/db/migration`](../../../backend/src/main/resources/db/migration)。

### D.1 Migration 索引（节选）

| 文件 | 描述 | 状态 |
|------|------|------|
| `V8__add_observation_content_hash.sql` | 内容哈希去重 | ✅ 已在仓库 |
| `V11__observation_quality.sql` | 观察质量、访问与精炼相关列 | ✅ 已在仓库 |
| `V12__step_efficiency.sql` | 步骤效率（如 `step_number`） | ✅ 已在仓库 |
| `V9__session_metrics.sql` | 会话级聚合指标（文档曾设想） | 📋 **当前** migration 目录中**无**此文件 |
| `V10__task_streams.sql` | 任务流表（文档曾设想） | 📋 **当前** migration 目录中**无**此文件 |

### D.2 V9: Session 指标字段（仍为设想）

**Migration 文件**: `V9__session_metrics.sql`（若未来引入，需单独编写并与 `SessionEntity` 对齐）

以下为本文早期规划列举的字段示例，**不代表当前数据库已具备**：

- `total_observations`、`avg_observation_quality`、`refine_completed`、`refined_at`、`tool_call_count` 等。

### D.3 V11: Observation 质量字段（已在仓库落地）

**Migration 文件**: [`V11__observation_quality.sql`](../../../backend/src/main/resources/db/migration/V11__observation_quality.sql)

> **开关**：精炼可由 `app.memory.refine-enabled`（及 `MemoryRefineService`）控制；质量相关列可为空或与规则/LLM 打分逐步填充。

与 **V11** 对应的主要列（与实体一致，语义摘自源码注释）：

- `quality_score`、`feedback_type`、`last_accessed_at`、`access_count`、`refined_at`、`refined_from_ids`、`user_comment`、`feedback_updated_at`

说明：`refined_from_ids` 在实体注释中为**逗号分隔 ID** 串（溯源合并来源），请勿想当然写成「必须是 JSON 数组」除非迁移脚本另有约定。

### D.4 V10: Task Streams（仍为设想）

**Migration 文件**: `V10__task_streams.sql`（待创建；用于任务依赖与顺序时可再引入）

### D.5 Java 实体（以 ObservationEntity 为准）

**权威来源**：[`ObservationEntity`](../../../backend/src/main/java/com/ablueforce/cortexce/entity/ObservationEntity.java)（含 V11 质量块、V12 `step_number`、V14 `source`/`extracted_data`、V18 `platform_source` 等）。

下列仅为 **V11 片段节选**，完整映射与 getter/setter 请看源码：

```java
// === Quality Score Fields (V11) — 节选 ===

@Column(name = "quality_score")
private Float qualityScore;

@Column(name = "feedback_type")
private String feedbackType;

@Column(name = "last_accessed_at")
private OffsetDateTime lastAccessedAt;

@Column(name = "access_count")
private Integer accessCount = 0;

@Column(name = "refined_at")
private OffsetDateTime refinedAt;

@Column(name = "refined_from_ids")
private String refinedFromIds;

@Column(name = "user_comment")
private String userComment;

@Column(name = "feedback_updated_at")
private OffsetDateTime feedbackUpdatedAt;
```

**Session 侧**：若文档中曾列举的 `total_observations` 等会话聚合字段尚未有对应 migration，则 **SessionEntity 仍以仓库当前字段为准**，勿假设已与下表一致。

### D.6 相关 Java 类（对照仓库）

| 类 / 枚举 | 路径 | 说明 |
|-----------|------|------|
| **QualityScorer** + 内嵌 **FeedbackType** | [`service/QualityScorer.java`](../../../backend/src/main/java/com/ablueforce/cortexce/service/QualityScorer.java) | 反馈类型枚举定义在 **QualityScorer 内部**，并非独立的 `common/FeedbackType.java` |
| **ExpRagService** | [`service/ExpRagService.java`](../../../backend/src/main/java/com/ablueforce/cortexce/service/ExpRagService.java) | ExpRAG 风格检索增强 |
| **MemoryRefineService** | [`service/MemoryRefineService.java`](../../../backend/src/main/java/com/ablueforce/cortexce/service/MemoryRefineService.java) | 异步精炼 |
| **结构化经验模型 `Experience`** | `model/Experience.java` | **当前仓库未发现**同名类；经验内容多体现在 `ObservationEntity.content` 及提取逻辑中 |

---

## 总结

Evo-Memory 论文为 LLM 记忆系统提供了重要的理论基础和实践指导：

### 核心启示

1. **记忆应该演化，而不是静态存储** - 通过 Refine 机制持续优化记忆质量
2. **经验复用比事实回忆更重要** - 检索策略而非仅仅检索事实
3. **质量评估是关键** - 并非所有经验都值得保留
4. **任务流视角** - 将独立事件组织为有意义的序列

### 旁路架构的关键约束

> **Claude-Mem 是旁路观察者，无法同步干预 Claude Code 的执行。**

这意味着：
- ✅ **可以做**: 异步质量评估、后台记忆精炼、下次会话注入优化上下文
- ❌ **不能做**: 同步 Refine、实时修改推理过程、当前会话即时改进

**核心策略**: **异步演化 + 延迟生效** — 当前会话的精炼效果在下次会话体现。

### 对 Claude-Mem 的建议优先级（适配旁路架构）

| 优先级 | 改进方向 | 旁路实现方式 | 预期收益 |
|--------|---------|-------------|---------|
| **P0** | 添加 quality_score | SessionEnd 异步评估 | 提升检索质量 |
| **P1** | 实现 Refine 机制 | SessionEnd 触发 + 下次 SessionStart 生效 | 减少噪音 |
| **P1** | ExpRAG 检索增强 | SessionStart 注入高质量经验 | 提升经验复用 |
| **P2** | 经验结构化模板 | LLM prompt 优化 | 提升可读性 |
| **P3** | ReMem API | 仅供外部集成 | 扩展性 |

### 预期效果

基于论文实验结果，实施这些改进后预期可以：
- **多轮任务成功率**: 提升 20-40%（从 0.5 → 0.7+）
- **步骤效率**: 减少 30-50% 步骤数
- **记忆质量**: 低质量记忆比例从 ~30% 降至 ~10%

### 架构对比总结

| 维度 | ReMem（论文） | Claude-Mem（旁路适配） |
|------|-------------|----------------------|
| Refine 时机 | 任务执行中同步 | SessionEnd 异步 |
| 效果生效 | 立即 | 下次会话 |
| 实现复杂度 | 需要核心集成 | 仅需后端改动 |
| 适用场景 | 可控 Agent | 旁路观察系统 |

---

*文档作者: Claude (Claude-Mem)*
