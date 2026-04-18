[← 返回索引](./index.md)

## 5. 当前 Claude-Mem Java 版本的差距分析

### 5.1 当前架构回顾

```
Claude-Mem Java 架构:
┌─────────────────────────────────────────────────────────────┐
│  Hook Events (SessionStart, UserPrompt, PostToolUse, etc.) │
│                           ↓                                  │
│  Thin Proxy (wrapper.js) → HTTP POST                        │
│                           ↓                                  │
│  Java Backend (Spring Boot)                                  │
│    ├── IngestionController (接收事件)                        │
│    ├── AgentService (LLM → XML解析 → 嵌入 → 存储)            │
│    ├── SearchService (向量 + 文本搜索)                        │
│    └── PostgreSQL + pgvector (存储)                          │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 与 Evo-Memory 框架的对比

| 维度 | Evo-Memory 框架 | 当前 Claude-Mem Java | 差距 |
|------|----------------|---------------------|------|
| **记忆抽象** | (F, U, R, C) 四元组 | 隐式存在，无显式抽象 | ⚠️ 缺乏统一形式化 |
| **更新模块 U** | 支持追加/压缩/重写/合并 | 仅支持追加 (Upsert) | ❌ 无记忆演化机制 |
| **检索模块 R** | 多策略检索 + 相似度过滤 | 向量相似度 + 项目过滤 | ✅ 基本满足 |
| **上下文构造 C** | 模板化 + 动态选择 | 静态模板 | ⚠️ 缺乏动态性 |
| **任务流** | 显式序列化 + 依赖建模 | 独立事件处理 | ❌ 无任务流视角 |
| **Refine 操作** | 主动记忆管理 | 无 | ❌ 关键缺失 |
| **经验复用** | 检索历史轨迹作为 ICL 样本 | 检索事实性内容 | ⚠️ 检索目标不同 |
| **质量评估** | quality_score + 反馈 | 无 | ❌ 缺乏质量机制 |

### 5.3 具体差距分析

#### 差距 1: 记忆是静态的，无演化能力

**当前行为**:
```java
// ObservationEntity 只是存储，永远不会被修改或合并
public class ObservationEntity {
    private String content;
    private float[] embedding;
    // 一旦写入，永不更新
}
```

**论文建议**: 记忆应该能被:
- **删除**: 过时/噪音/低质量
- **合并**: 多个具体案例 → 抽象规则
- **重写**: 更精确的表述

#### 差距 2: 无任务流视角

**当前行为**: 每个事件独立处理
```
event_1 → observation_1
event_2 → observation_2
event_3 → observation_3
```

**论文建议**: 任务应该有序列依赖
```
task_1 → experience_1 → M_2
task_2 (基于 M_2) → experience_2 → M_3
task_3 (基于 M_3) → experience_3 → M_4
```

#### 差距 3: 检索的是事实，不是策略

**当前 prompt 模板**:
```
检索到的内容:
- "用户修改了 .gitignore 文件"
- "添加了 V6 数据库迁移"
```

**论文建议的经验格式**:
```
检索到的策略:
- "当处理 Git 忽略规则时，应该检查是否会影响构建输出"
- "数据库迁移添加新列时，记得同时创建索引以优化查询"
```

#### 差距 4: 无质量评估机制

**当前**: 所有观察同等对待

**论文**: 每个记忆条目有 `quality_score`
- 成功任务的经验得分高
- 失败任务的经验可选择性保留
- 低质量记忆在 Refine 时被剪枝

---

