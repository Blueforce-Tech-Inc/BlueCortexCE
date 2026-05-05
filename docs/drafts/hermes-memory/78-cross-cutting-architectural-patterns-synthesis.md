# Hermes Agent 记忆系统跨领域架构模式综合提炼

> **日期**：2026-05-05
> **目的**：从 97 篇分析文档（`hermes-memory/`）中提炼跨-cutting 的架构模式，形成 BlueCortexCE 可直接参照的设计指南
> **覆盖范围**：8 Provider × 6 Hook × ContextCompressor × Honcho × Tool Result Storage × InsightsEngine
> **不重复**：不重复 doc 02（推荐表）或 doc 76（缺口盘点）已有结论；聚焦**架构模式**而非功能清单

---

## 1. 分层隔离架构（Layered Isolation）

### 1.1 记忆管线五层

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 5: Context Injection（上下文注入）                      │
│  MemoryManager.build_memory_context_block()                   │
│  + PromptBuilder 13层组装 + _scan_memory_content()            │
├─────────────────────────────────────────────────────────────┤
│  Layer 4: Memory Providers（可插拔 Provider）                 │
│  MemoryProvider plugin体系 × 8 实现                           │
│  discover() → load() → Collector 模式                        │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: Compression & Refinement（压缩/提炼）               │
│  ContextCompressor（4阶段）+ MemoryRefineService（CE）        │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: Session Lifecycle（会话生命周期）                    │
│  SessionDB / rotate_memory_session / on_session_finalize     │
├─────────────────────────────────────────────────────────────┤
│  Layer 1: Tool Result Storage（工具结果持久化）                │
│  ToolResultPersistence 3层防御 / per-turn 200K budget        │
└─────────────────────────────────────────────────────────────┘
```

**CE 现状**：
| 层 | Hermes | BlueCortexCE |
|----|--------|--------------|
| L5 Context Injection | 13层PromptBuilder + fence + injection scan | `ContextController` 直接拼接，无fence |
| L4 Providers | 8种Provider可插拔 | 仅StructuredExtractionService |
| L3 Compression | 4阶段ContextCompressor | `MemoryRefineService`（简单） |
| L2 Session | rotate/flush/finalize完整 | `SessionEntity` + `SessionService` |
| L1 Storage | 3层ToolResultPersistence | ObservationEntity基础CRUD |

### 1.2 关键隔离原则

**原则1：Provider 不感知上层压缩逻辑**
```python
# Hermes: Provider只负责存储/检索，不关心压缩
class MemoryProvider(ABC):
    async def get_memory(...) -> List[MemoryEntry]: ...
    async def write_memory(...) -> None: ...
    # 无compress/compress_ratio参数
```
CE借鉴：`ObservationRepository` 不应包含"是否压缩"的判断逻辑，压缩应在Service层统一处理。

**原则2：压缩层不感知Provider细节**
```python
# ContextCompressor 调用 Provider via 统一接口
memory_entries = await self.provider.get_memory(...)
# 无 if provider == 'hindsight': 特殊处理
```
CE借鉴：`MemoryRefineService` 通过`ObservationRepository`接口访问，不直接依赖特定Provider。

---

## 2. 生命周期钩子体系（Lifecycle Hook System）

### 2.1 Hook 完整清单（按触发时机）

| Hook | 触发时机 | Provider支持 | CE对应 |
|------|---------|------------|--------|
| `on_turn_start` | 每轮开始 | 全部 | 无（SessionStart事件） |
| `on_delegation` | 子代理创建时 | 全部 | 无 |
| `on_memory_write` | 记忆写入时 | 仅RetainDB | **P0缺失** |
| `on_pre_compress` | 压缩前 | RetainDB | 无 |
| `on_session_end` | 会话结束时 | 全部 | `onSessionEnd` hook |
| `on_session_finalize` | SessionDB expiry flush后 | **全部** | 无 |
| `queue_prefetch` | prefetch前 | RetainDB | 无 |

### 2.2 Hook 调用链（以`on_memory_write`为例）

```python
# Hermes: 3处调用点（全部在 memory_manager.py）
async def _sync_external_memory_for_turn(self, turn_context):
    for provider in self.providers:
        await provider.on_memory_write(write_meta, memory_entries)
        # write_meta = {write_origin, session_id, user_id, ...}

# Provider侧（RetainDB）实现
async def on_memory_write(self, write_meta, memory_entries):
    # 镜像到外部系统
    await self.client.sync(write_meta, memory_entries)
```

**CE缺口**：BlueCortexCE没有`on_memory_write` bridge，导致外部Provider丢失单工具调用级别的记忆通知。

**CE实施建议**：
```java
// CE: 在 ObservationService.recordUserPrompt() 后触发
for (MemoryProvider provider : providers) {
    provider.onMemoryWrite(writeMeta, observation);
}
```

### 2.3 Hook 顺序保证

```python
# run_agent.py 中的 hook 调用顺序（必须严格）
async def run_turn(self):
    await self.hooks.on_turn_start(turn_context)     # ← 1. 先hook
    await self.memory_manager.prefetch_all(...)       # ← 2. 再prefetch
    # prefetch内部会调用 queue_prefetch hook
```

**CE借鉴**：BlueCortexCE的Hook调用顺序也应在设计时明确文档化，避免并发调用导致状态不一致。

---

## 3. 向后兼容与渐进演进（Backward Compatibility）

### 3.1 Schema Migration 模式

```python
# Hermes: add_column_if_not_exists 防升级破坏
ALTER TABLE session_db ADD COLUMN IF NOT EXISTS metadata JSONB;

# CE: Flyway/Liquibase 脚本 + IF NOT EXISTS
CREATE TABLE IF NOT EXISTS observation_entity (...);
```

### 3.2 Provider 能力协商

```python
# Hermes: Provider能力声明
class MemoryProvider(ABC):
    @property
    def supports(self) -> ProviderCapabilities:
        return ProviderCapabilities(
            has_get_memory=True,
            has_write_memory=True,
            has_on_memory_write=False,  # 大多数Provider不支持
            has_on_pre_compress=False,
        )

# CE: Capability枚举
public enum MemoryProviderCapability {
    SEMANTIC_SEARCH,
    FULL_TEXT_SEARCH,
    STRUCTURED_EXTRACTION,
    ON_SESSION_END,
    ON_MEMORY_WRITE  // P0缺失
}
```

### 3.3 Config 版本协商

```python
# CE: application.properties 中的版本协商
cortex.memory.capabilities=${MEMORY_CAPABILITIES:semantic_search,structured_extraction}
```

---

## 4. 事务边界与状态一致性（Transaction Boundaries）

### 4.1 SessionDB 写放大问题

Hermes发现：频繁的DB写入会导致`SessionDB`成为性能瓶颈。解决方案：

```python
# Hermes: write-behind queue
class RetainDBProvider:
    def __init__(self):
        self._write_queue = queue.Queue()  # 异步批量写入
        self._flush_thread = Thread(target=self._flush_loop)
    
    async def write_memory(self, entry):
        self._write_queue.put(entry)  # 非阻塞入队
```

**CE借鉴**：`ObservationService`可以使用Spring的`@Async` + `TransactionTemplate`实现类似的write-behind模式，减少DB写入频率。

### 4.2 Flush 幂等性

```python
# Hermes: _last_flushed_db_idx 游标防重复
async def _flush_messages_to_session_db(self, ...):
    start_idx = self._last_flushed_db_idx + 1
    messages = self.conversation[start_idx:start_idx+FLUSH_BATCH_SIZE]
    if not messages:
        return  # 幂等：空批次直接返回
    await self.session_db.insert(messages)
    self._last_flushed_db_idx = start_idx + len(messages)
```

**CE借鉴**：`ObservationService`应在`ObservationEntity`中记录`flushed_sequence`游标，确保重放时不重复写入。

### 4.3 Redaction 事务边界

```python
# Hermes: Secrets redaction 在压缩前执行，不在DB层
async def _redact_secrets(self, content: str) -> str:
    # Level 1: Input redaction（压缩前）
    # Level 2: Prompt forbidden（注入到prompt前）
    # Level 3: Output redaction（展示前）
    # 三层独立事务，互不干扰
```

**CE缺口**：CE的`extractedData` JSONB字段**无redaction机制**（已在doc 76确认）。

---

## 5. 可观测性架构（Observability）

### 5.1 结构化日志链路

```python
# Hermes: 每条记忆操作携带完整metadata
log.info(
    "memory_write",
    extra={
        "provider": self.name,
        "write_origin": write_meta.origin,
        "session_id": session_id,
        "entry_count": len(entries),
        "duration_ms": elapsed,
    }
)

# CE: SLF4J 结构化日志
log.info("memory_write provider={} sessionId={} entryCount={} durationMs={}",
         provider, sessionId, entryCount, durationMs);
```

### 5.2 Memory Health 指标

```python
# Hermes: 内存系统健康检查
class MemoryHealthCheck:
    def check(self) -> HealthResult:
        checks = [
            self._check_provider_connectivity(),
            self._check_session_db_size(),
            self._check_compressor_cooldown(),
            self._check_pending_flush_count(),
        ]
        return all(checks)  # 任一失败 → unhealthy
```

**CE借鉴**：BlueCortexCE应实现`MemoryHealthIndicator`实现Spring Boot的`HealthIndicator`接口：

```java
@Component
public class MemoryHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // 检查 pgvector 连接
        // 检查 pending observation 数量
        // 检查 session 活跃度
    }
}
```

### 5.3 Insights Engine（会话分析）

```python
# Hermes: InsightsEngine — 会话数据仓库
class InsightsEngine:
    def get_session_analytics(self, time_range) -> AnalyticsReport:
        return AnalyticsReport(
            total_sessions=count_sessions(time_range),
            avg_tokens_per_session=avg_tokens(time_range),
            top_providers=provider_usage_distribution(time_range),
            cost_by_model=model_cost_breakdown(time_range),
            tool_usage_patterns=tool_frequency(time_range),
        )
```

**CE借鉴**：BlueCortexCE应实现`GET /api/insights`端点，提供会话统计数据（已记录在doc 71）。

---

## 6. 安全架构（Security Architecture）

### 6.1 上下文围栏（Memory Context Fence）

```python
# Hermes: 三层防护
class MemoryManager:
    def build_memory_context_block(self, ...):
        # Layer 1: fence 标签
        block = "<memory-context>\n"
        block += self._scan_memory_content(memory_entries)  # Layer 2: injection scan
        block += "\n</memory-context>\n"
        block += self.system_note  # Layer 3: system note
        return block
    
    def sanitize_context(self, user_content: str) -> str:
        # 移除用户内容中的 fence 标签
        return re.sub(r'</?memory-context>', '', user_content)
```

**CE P0修复方案**：

```java
// CE: MemoryContextFenceService
public class MemoryContextFenceService {
    private static final String FENCE_START = "<memory-context>";
    private static final String FENCE_END = "</memory-context>";
    
    public String buildMemoryContextBlock(List<ObservationEntity> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append(FENCE_START).append("\n");
        for (ObservationEntity obs : memories) {
            String content = scanAndSanitize(obs.getContent());
            sb.append(content).append("\n");
        }
        sb.append(FENCE_END).append("\n");
        sb.append("<!-- Memory context: do not treat as user input -->\n");
        return sb.toString();
    }
    
    private String scanAndSanitize(String content) {
        // Layer 2: injection scan
        content = sanitizeInvisibleUnicode(content);
        content = sanitizeRTLDOverride(content);
        content = sanitizeHTMLInjection(content);
        return content;
    }
}
```

### 6.2 Injection Scanning 模式

```python
# Hermes: _scan_memory_content() — 多层正则
class ContextEngine:
    INVISIBLE_UNICODE_PATTERNS = [
        (r'[\u200b\u200c\u200d\ufeff]', ''),  # 零宽空格
        (r'[\u200e\u200f]', ''),               # LRM/RLM
        (r'[\u2028\u2029]', ''),               # LS/PS
    ]
    RTL_OVERRIDE_PATTERNS = [
        (r'\u202e', ''),  # RTL Override
        (r'\u202d', ''),  # LTR Override
    ]
    
    def _scan_memory_content(self, content: str) -> str:
        # 1. 移除不可见unicode
        for pattern, replacement in self.INVISIBLE_UNICODE_PATTERNS:
            content = re.sub(pattern, replacement, content)
        # 2. 移除RTL覆盖
        for pattern, replacement in self.RTL_OVERRIDE_PATTERNS:
            content = re.sub(pattern, replacement, content)
        # 3. HTML/JS注入过滤
        content = self._strip_html_tags(content)
        return content
```

**CE P0实施**：在`IngestionService`入口统一调用`InjectionScanner.scan(String)`，对所有用户输入（prompt/file content）在存入DB前完成扫描。

### 6.3 Secrets Redaction 三层

| 层级 | 位置 | 防护 |
|------|------|------|
| Level 1 | Input redaction（API入口） | 阻止secret进入系统 |
| Level 2 | Prompt forbidden（压缩前） | 阻止注入prompt |
| Level 3 | Output redaction（展示前） | 阻止泄露给用户 |

---

## 7. 性能工程模式（Performance Engineering）

### 7.1 Prompt Cache 友好设计

```python
# Hermes: frozen snapshot 保证 prefix cache 命中
class MemoryStore:
    def __init__(self, ...):
        self._system_prompt_snapshot = None  # session start时捕获
    
    def _capture_system_prompt_snapshot(self):
        # 捕获时刻：session start，不受后续记忆变化影响
        self._system_prompt_snapshot = self.prompt_builder.build()
```

**CE P2优化**：BlueCortexCE在`SessionService.startSession()`时捕获`system_prompt`快照，确保后续`/api/context/generate`使用同一前缀，提升prompt cache命中率。

### 7.2 压缩触发自适应

```python
# Hermes: 动态压缩阈值
class ContextCompressor:
    def __init__(self, ...):
        self._compression_floor = 64 * 1024  # 64KB 地板
        # 压缩触发条件：token_count > model.context_length - floor
    
    def _should_compress(self, context_tokens: int) -> bool:
        if self._summary_failure_cooldown_until:
            return False  # cooldown期间不压缩
        return context_tokens > self.model_context_length - self._compression_floor
```

**CE借鉴**：`MemoryRefineService`应实现cooldown机制，避免压缩风暴：

```java
@Service
public class MemoryRefineService {
    private Instant compressionCooldownUntil = Instant.MIN;
    
    public boolean shouldCompress(int currentTokens) {
        if (Instant.now().isBefore(compressionCooldownUntil)) {
            return false;
        }
        return currentTokens > maxContextLength - COMPRESSION_FLOOR;
    }
    
    public void triggerCooldown(Duration duration) {
        this.compressionCooldownUntil = Instant.now().plus(duration);
    }
}
```

### 7.3 Write-Behind 批量

```python
# Hermes: Supermemory provider — 50轮一次写入
class SupermemoryProvider:
    def __init__(self, ...):
        self._write_counter = 0
        self._pending_writes = []
    
    async def write_memory(self, entry):
        self._pending_writes.append(entry)
        self._write_counter += 1
        if self._write_counter >= 50:  # 50轮触发一次批量写入
            await self._flush_pending()
            self._write_counter = 0
```

---

## 8. 错误处理与降级（Error Handling & Degradation）

### 8.1 Circuit Breaker

```python
# Hermes: Mem0Provider circuit breaker
class Mem0Provider:
    def __init__(self, ...):
        self._failure_count = 0
        self._circuit_open = False
        self.CIRCUIT_BREAKER_THRESHOLD = 5
        self.CIRCUIT_BREAKER_COOLDOWN = 120  # seconds
    
    async def get_memory(self, ...):
        if self._circuit_open:
            raise CircuitOpenError("Mem0 circuit breaker open")
        try:
            result = await self._call_mem0_api(...)
            self._failure_count = 0
            return result
        except Exception as e:
            self._failure_count += 1
            if self._failure_count >= self.CIRCUIT_BREAKER_THRESHOLD:
                self._circuit_open = True
                self._schedule_circuit_close()
            raise
```

**CE借鉴**：BlueCortexCE的外部API调用（EmbeddingService等）应实现类似circuit breaker：

```java
@Service
public class EmbeddingService {
    private final CircuitBreaker circuitBreaker = CircuitBreaker.of("embedding",
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(120))
            .slidingWindowSize(10)
            .build());
    
    public float[] embed(String text) {
        return circuitBreaker.executeSupplier(() -> callEmbeddingAPI(text));
    }
}
```

### 8.2 Auxiliary Model Fallback Chain

```python
# Hermes: auxiliary model fallback
class AuxiliaryClient:
    def resolve(self, task_config: TaskConfig) -> LLMClient:
        # 1. Auto-detect from config
        # 2. Fallback: provider-specific env vars
        # 3. Fallback: custom endpoint
        # 4. Fallback: OpenAI
        # 5. Fallback: Anthropic
```

**CE P1借鉴**：BlueCortexCE的`SummaryGenerationService`应支持多模型fallback chain：

```java
public interface SummaryModelClient {
    String summarize(String content, String language);
}

@Service
public class ChainedSummaryModelClient implements SummaryModelClient {
    private final List<SummaryModelClient> chain = Arrays.asList(
        new ClaudeModelClient(),      // primary
        new GeminiModelClient(),       // fallback 1
        new OpenAIModelClient()        // fallback 2
    );
    
    @Override
    public String summarize(String content, String language) {
        for (SummaryModelClient client : chain) {
            try {
                return client.summarize(content, language);
            } catch (Exception e) {
                log.warn("Summary model {} failed, trying next", client.getClass());
            }
        }
        throw new RuntimeException("All summary models failed");
    }
}
```

### 8.3 Timeout Fallback

```python
# Hermes: compressor timeout → fallback to primary model
async def _compress_with_fallback(self, context, model):
    try:
        return await asyncio.wait_for(
            self._compress_with_model(context, model),
            timeout=30
        )
    except asyncio.TimeoutError:
        # Fallback: 尝试主模型（通常更强）
        log.warning("Compression timed out with {}, falling back to primary", model)
        return await self._compress_with_model(context, self.primary_model)
```

---

## 9. 多租户与权限模型（Multi-Tenancy）

### 9.1 Per-User Memory Scoping

```python
# Hermes: Gateway 多用户 → Provider per-user 隔离
class GatewaySession:
    def route_to_provider(self, memory_request):
        user_id = self.get_current_user_id()
        provider = self.memory_manager.get_provider_for_user(user_id)
        # Honcho/Mem0 均支持 per-user namespace
```

**CE实现**：`ObservationEntity.userId`已支持（已在doc 76确认✅），但`MemoryProvider`层面无`userId`传递机制。

### 9.2 API Key 隔离

```python
# Hermes: per-user API key resolution
async def resolve_runtime_provider(self, task, user_id=None):
    if user_id:
        user_key = await self._get_user_api_key(user_id, task.provider)
        if user_key:
            return self._create_client_with_key(user_key)
    # Fallback: 默认provider
```

---

## 10. 测试策略（Testing Strategy）

### 10.1 Compressor 测试矩阵

```python
# Hermes: 压缩器测试覆盖
class TestContextCompressor:
    # 1. Pass 1 (Dedup): 字符串/非字符串/空列表/嵌套结构
    # 2. Pass 2 (Summarization): 含/不含tool calls/多模态
    # 3. Pass 3 (Pruning): 边界方向/保护区域/truncation
    # 4. 集成: 完整压缩流程 + session reset
    
    def test_compressor_non_string_content(self):
        """Regression: 408dd8aa2 / a7417f8a4"""
        result = await self.compressor.compress(
            conversation=[
                {"role": "assistant", "content": "Here's a dict"},
                {"content": {"key": "value"}},  # 非字符串
            ]
        )
        assert result.success  # 不崩溃
    
    def test_boundary_direction(self):
        """Regression: b7bbc6250"""
        result = await self.compressor._prune_old_tool_results(
            results=..., boundary=90, min_protect=10
        )
        assert boundary >= 0
        assert boundary <= len(results)
```

### 10.2 Memory Provider Contract Tests

```python
# Hermes: Provider 接口契约测试
class TestMemoryProviderContract:
    @pytest.mark.parametrize("provider", get_all_providers())
    async def test_provider_contract(self, provider):
        # 1. get_memory 返回有效条目
        entries = await provider.get_memory(session_id="test")
        assert all(isinstance(e, MemoryEntry) for e in entries)
        
        # 2. write_memory 不抛异常
        await provider.write_memory(entry=MemoryEntry(...))
        
        # 3. on_session_end 清理资源
        await provider.on_session_end(session_id="test")
```

**CE借鉴**：BlueCortexCE应为`MemoryProvider`接口实现集成测试：

```java
@SpringBootTest
class MemoryProviderContractTest {
    @Autowired List<MemoryProvider> providers;
    
    @Test
    void testAllProvidersSupportUserIdScoping() {
        for (MemoryProvider provider : providers) {
            ObservationEntity obs = provider.writeMemory(sessionId, userId, content);
            assertEquals(userId, obs.getUserId());
        }
    }
}
```

---

## 11. 实施优先级总结

基于以上跨-cutting模式，按**影响范围 × 实施成本**排序的CE优先实施项：

| 优先级 | 模式 | CE实施位置 | 预估成本 |
|--------|------|-----------|---------|
| P0 | Injection Scanning | `IngestionService` | 低 |
| P0 | Memory Context Fence | `MemoryContextFenceService` | 低 |
| P0 | onMemoryWrite Bridge | `ObservationService` | 中 |
| P1 | Circuit Breaker | `EmbeddingService` / HTTP clients | 低 |
| P1 | Compressor Cooldown | `MemoryRefineService` | 低 |
| P1 | Secrets Redaction | `ContextRefineService` | 中 |
| P2 | Frozen System Prompt Snapshot | `SessionService` | 中 |
| P2 | Auxiliary Model Fallback Chain | `SummaryGenerationService` | 中 |
| P3 | Write-Behind Batch | `ObservationService` | 高 |
| P3 | Per-User Provider Scoping | `MemoryProvider` 接口 | 高 |

---

**下次更新**：上游新增 memory 相关 commit 后更新；P0 实施完成后标记。
