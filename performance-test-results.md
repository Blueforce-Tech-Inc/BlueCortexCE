# Performance Test Results

**Date**: 2026-03-24 04:08 CST
**Service**: cortex-ce (port 37777)
**JVM Memory**: 452MB RSS / 3.2% CPU

---

## 1. Single Request Response Time (10 iterations)

| Metric | Value |
|--------|-------|
| First request | 22.7ms |
| Average (warm) | 12.0ms |
| Min | 11.4ms |
| Max | 22.7ms |
| Status | All 200 ✅ |

**Observation**: Cold start penalty ~10ms, then stable at ~12ms.

## 2. Batch Creation Performance

| Batch Size | Mode | Time | Per-Item |
|------------|------|------|----------|
| 10 | Concurrent | 31ms | 3.1ms |
| 50 | Serial | 335ms | 6.7ms |

**Observation**: Concurrent writes are very fast. Serial throughput ~150 items/sec.

## 3. Concurrent Stress Test

| Concurrency | Time | Status |
|-------------|------|--------|
| 5 | 29ms | ✅ |
| 10 | 22ms | ✅ |
| 20 | 29ms | ✅ |

**Observation**: No degradation under 20 concurrent writes. System handles parallelism well.

## 4. Read Endpoint Performance

| Endpoint | Time | Status |
|----------|------|--------|
| /api/health | 4.8ms | 200 ✅ |
| /api/stats | 37ms | 200 ✅ |
| /api/observations?limit=10 | 22ms | 200 ✅ |
| /api/session/start | 13ms | 200 ✅ |
| /api/search?project=default&query=test | **1497ms** | 200 ⚠️ |

## 5. Bottleneck Analysis

### ⚠️ Search Endpoint: 1.5s
- `/api/search` requires embedding generation + vector similarity search
- This is the **primary bottleneck** — ~100x slower than other endpoints
- Likely dominated by OpenAI embedding API call latency

### ✅ Write Performance: Excellent
- Ingestion endpoint: stable 11-23ms
- No memory leaks observed
- JVM stable at 452MB RSS

### ✅ Concurrency: Good
- No errors under 20 concurrent writes
- Response time linear with load

## 6. Optimization Recommendations

1. **Embedding Cache**: Cache embeddings for repeated queries to avoid redundant API calls
2. **Batch Embedding**: Use batch embedding API for multi-query scenarios
3. **Connection Pooling**: Verify HTTP client connection pool is properly configured
4. **Search Timeout**: Consider adding a timeout fallback for slow embedding calls
5. **Monitoring**: Add Micrometer metrics for p50/p95/p99 latencies

---

*Next test scheduled in 2 hours. Track trends over multiple runs.*
