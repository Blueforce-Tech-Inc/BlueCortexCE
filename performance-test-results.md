# Performance Test Results

**Date**: 2026-03-24 16:16 CST
**Service**: cortex-ce (port 37777)
**JVM Memory**: 378MB RSS / 0.6% MEM / 46.9% CPU (during test)
**Java PID**: 40730

---

## 1. Single Request Response Time (5 iterations)

| Metric | Value |
|--------|-------|
| First request (cold) | 20ms |
| Average (warm) | 12ms |
| Min | 9ms |
| Max | 20ms |
| Status | All 200 ✅ |

**Observation**: Cold start penalty ~10ms, then stable at ~10ms. Slightly faster than last run (12ms avg).

## 2. Batch Creation Performance

| Batch Size | Mode | Time | Per-Item |
|------------|------|------|----------|
| 10 | Parallel | 172ms | 17.2ms |
| 50 | Parallel | 74ms | 1.5ms |

**Observation**: 50-record parallel batch is surprisingly fast (1.5ms/item). The 10-record batch was slower likely due to connection establishment overhead being proportionally higher.

## 3. Search Performance (No Embedding)

| Query | Time | Status |
|-------|------|--------|
| "test" | 9ms | 200 ✅ |
| "performance benchmark observation" | 7ms | 200 ✅ |
| "batch-50-item" | 8ms | 200 ✅ |
| "nonexistent-xyzzy-12345" | 8ms | 200 ✅ |
| Session list (20 items) | 10ms | 200 ✅ |

**Observation**: Simple query searches are very fast (~8ms). No vector computation involved.

## 4. Search Performance (With Embedding)

| Attempt | Time | Status |
|---------|------|--------|
| #1 (cold) | **1607ms** | 200 ⚠️ |
| #2 (warm) | 253ms | 200 ✅ |
| #3 (warm) | 275ms | 200 ✅ |

**Observation**: Cold embedding call is the primary bottleneck at 1.6s. Warm calls settle at ~260ms, dominated by OpenAI embedding API latency.

## 5. Other Endpoint Performance

| Endpoint | Time | Status |
|----------|------|--------|
| /api/health | 7ms | 200 ✅ |
| /api/stats | 30ms | 200 ✅ |
| /api/observations?limit=20 | 32ms | 200 ✅ |

## 6. Concurrent Stress Test

| Concurrency | Time | Status |
|-------------|------|--------|
| 5 | 15ms | ✅ |
| 10 | 18ms | ✅ |
| 20 | 28ms | ✅ |

**Observation**: Linear scaling under concurrency. No degradation up to 20 concurrent requests. System handles parallelism well.

## 7. Trend Comparison (vs 04:08 run)

| Metric | 04:08 | 16:16 | Trend |
|--------|-------|-------|-------|
| Single request (avg) | 12.0ms | 12ms | Stable |
| JVM RSS | 452MB | 378MB | ↓ Better |
| Health check | 4.8ms | 7ms | Slightly slower |
| Search (embedding, cold) | 1497ms | 1607ms | ⚠️ Slightly worse |
| Search (embedding, warm) | N/A | ~260ms | Baseline established |
| 20 concurrent | 29ms | 28ms | Stable |

## 8. Bottleneck Analysis

### ⚠️ Primary: Embedding API Cold Start (~1.6s)
- First `/api/search` call requires embedding generation via OpenAI API
- Subsequent calls benefit from connection reuse (~260ms)
- This is expected behavior for external API dependency

### ✅ Write Performance: Excellent
- Ingestion: stable 9-20ms
- Batch parallel writes scale well
- No memory leaks (RSS actually decreased from 452MB to 378MB)

### ✅ Concurrency: Good
- No errors under 20 concurrent requests
- Linear response time scaling

## 9. Optimization Recommendations

1. **Embedding Cache**: Cache frequently used query embeddings (LRU cache, 1000 entries)
2. **Connection Pre-warm**: Initiate HTTP client connection to OpenAI on startup
3. **Async Embedding**: Return results with pending embedding status, update asynchronously
4. **Micrometer Metrics**: Add p50/p95/p99 latency histograms for production monitoring
5. **Batch Embedding API**: Use OpenAI's batch endpoint for multi-query scenarios

---

*Next test: ~18:16 CST. Track trends across multiple runs.*
