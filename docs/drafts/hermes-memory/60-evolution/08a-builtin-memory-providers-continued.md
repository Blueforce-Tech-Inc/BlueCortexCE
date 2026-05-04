## 62. BlueCortexCE Observation 现状确认（v5.1 更新）

> **本节为 v5.1 更新**，确认 BlueCortexCE 的 Observation Entity 字段现状，更新"待确认"列表。

### 62.1 BlueCortexCE Category 字段现状

**文件**: `backend/src/main/resources/prompts/init.txt` + `backend/.../entity/ObservationEntity.java`

**Summary Prompt（init.txt）定义的 `type` 字段**：
```xml
<type>[ bugfix | feature | refactor | change | discovery | decision ]</type>
```
- **bugfix**: something was broken, now fixed
- **feature**: new capability or functionality added
- **refactor**: code restructured, behavior unchanged
- **change**: generic modification (docs, config, misc)
- **discovery**: learning about existing system
- **decision**: architectural/design choice with rationale

**ObservationEntity.java 中的 `type` 字段**：
```java
@Column(name = "type", nullable = false)
@JsonProperty("type")
private String type;
```
- **现状**：`type` 是 `String`，没有 enum 验证
- **问题**：LLM 可能输出任意字符串，后端不校验

**结论**：✅ Category 系统在 **prompt 层**已实现（6 个类型），但 **entity 层**没有 enum 约束。LLM 可能输出非标准类型。

### 62.2 BlueCortexCE Entity Extraction 字段现状

**ObservationEntity 中没有 `entities` 字段**。现有相关字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `facts` | `List<String>` (JSONB) | 事实列表 |
| `concepts` | `List<String>` (JSONB) | 知识类别标签 |
| `source` | String | 来源：tool_result / user_statement / llm_inference / manual |
| `extractedData` | `Map<String, Object>` (JSONB) | 结构化提取数据 |

**没有 dedicated entity extraction**。如果需要提取"实体"（如人名、地点、技术名词），目前只能放在 `facts` 列表中。

**结论**：❌ 尚未实现 dedicated entity extraction（`entities` 字段）。Phase 3 设计中也没有包含 entity extraction。

### 62.3 建议优先级调整

基于确认的现状，更新建议优先级：

| 优先级 | 建议 | 说明 |
|--------|------|------|
| **高** | Observation 增加 `category` enum 约束 | 后端增加 enum 校验，而非依赖 prompt 约束 |
| **高** | 确认 `type` 字段是否被 API 消费者使用 | 如果 WebUI 不显示 type，添加 enum 价值有限 |
| **中** | 考虑增加 `entities` 字段 | 如果需要从文本中提取命名实体 |
| **低** | source 字段在 Summary prompt 中提取 | 目前 source 由调用方在 API 层设置 |

---

## 53. 待进一步确认（v5.1 更新）

### 53.1 本轮已确认项目

1. ✅ ~~ContextCompressor Phase 1 tool result pre-pass~~ — **已详细分析**：规则型 1-line 摘要，非通用 placeholder
2. ✅ ~~SessionDB v6 reasoning chain columns~~ — **已验证**：reasoning/reasoning_details/codex_reasoning_items 三列
3. ✅ ~~Honcho write_frequency mechanism~~ — **已验证**：async/turn/session/int 四种模式 + daemon thread 实现
4. ✅ ~~Honcho sync_turn threading model~~ — **已验证**：daemon thread + 5s 前序 join 防堆积
5. ✅ ~~RetainDB SQLite write-behind queue~~ — **v4.8 已详细分析**：pending 表 + crash replay + thread-local connections
6. ✅ ~~RetainDB memory_type enum~~ — **v4.8 已验证**：factual/preference/goal/instruction/event/opinion + importance 0-1
7. ✅ ~~Supermemory entity_context~~ — **v4.8 已验证**：negative 指令 + "When in doubt, store less" + trivial filter
8. ✅ ~~Hermes 内置 memory 生命周期机制~~ — **v4.9 已详细分析**：有界精选（硬字符限制）+ 冻结快照 + Agent 显式删除 + 注入扫描
9. ✅ ~~OpenViking Provider~~ — **v5.0 已详细分析**：分层上下文（L0/L1/L2）+ 6 类提取 + filesystem URI + atexit 安全网
10. ✅ ~~ByteRover Provider~~ — **v5.0 已详细分析**：CLI wrapper + on_pre_compress 策展 + 3 tools
11. ✅ ~~Built-in Memory 双系统架构~~ — **v5.1 已澄清**：Built-in Memory (MemoryStore) 不属于 MemoryManager 插件系统，run_agent.py 直接管理
12. ✅ ~~Honcho 动态推理级别~~ — **v5.1 已分析**：Query-Length 驱动 (<120/low, 120-400/mid, >400/high) + cap at high
13. ✅ ~~Honcho ai_observe_others 观察模式~~ — **v5.1 已分析**：交叉观察 (AI→User) vs 自我观察 (User→Self) 的双路由
14. ✅ ~~Honcho on_memory_write → create_conclusion 语义~~ — **v5.1 已确认**：写入 USER profile 事实，与 session 关联但结论跨 session
15. ✅ ~~BlueCortexCE Observation Category 现状~~ — **v5.1 已确认**：type 字段在 prompt 层已实现 6 类（bugfix/feature/refactor/change/discovery/decision），entity 层无 enum 约束
16. ✅ ~~BlueCortexCE Observation Entity Extraction~~ — **v5.1 已确认**：无 dedicated entities 字段，只有 facts/concepts

### 53.2 仍待确认项目

1. **Hindsight local mode** — 启动 embedded daemon 的具体实现和协议（云端 API）
2. **Mem0 Provider** — 云端 API，具体 LLM prompt 策略未知
3. **Hermes Agent Self-Model** — RetainDB 的 `Agent Self-Model` 具体如何影响 behavior？（需要查看 agent/ 相关代码）
4. **Honcho Dialectic 完整行为** — Peer Q&A + Observation 模式的具体实现（需要云端测试）
5. **Honcho Dialectic 完整 prompt** — 云端 API，本地无 LLM prompt 模板（无法验证）

---

### 上游同步备注（2026-04-24）

**`420d2709`**：`_file_lock` 跨平台硬化 — `fcntl` 仅 Unix，`msvcrt` 仅 Windows；两者均不可用时（部分容器环境）跳过文件锁。CE 的 `SessionDao` / 文件锁逻辑可参照此 defensive import 模式。

---

