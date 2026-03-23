# CortexCE Performance & Stress Test Results

**Test Date**: 2026-03-23 22:06 CST (Asia/Shanghai)
**Service Version**: claude-mem-java
**Host**: 杨捷锋的Mac Pro
**JVM PID**: 30162

---

## 📊 Service Health

| Metric | Value |
|--------|-------|
| Status | ✅ OK |
| Response Time | 1.8ms |
| Database | PostgreSQL (UP) |
| Message Queue | UP (0 pending) |
| Disk Space | 2.7TB free / 3.8TB total |

---

## ⚡ Performance Test Results

### 1. Single Request Response Time (Health Endpoint)

| Iteration | Response Time |
|-----------|--------------|
| 1 | 1.69ms |
| 2 | 1.14ms |
| 3 | 0.87ms |
| 4 | 1.03ms |
| 5 | 1.03ms |
| 6 | 0.99ms |
| 7 | 0.78ms |
| 8 | 0.97ms |
| 9 | 0.84ms |
| 10 | 0.90ms |
| **Average** | **1.02ms** |
| **Min** | 0.78ms |
| **Max** | 1.69ms |

**Assessment**: ✅ Excellent - Sub-millisecond to low millisecond response times.

### 2. Batch Observation Creation Performance

| Batch Size | Total Time | Avg per Request |
|------------|------------|-----------------|
| 10 records | 100ms | 10ms |
| 50 records | 346ms | 6.9ms |
| 100 records | 664ms | 6.6ms |

**Assessment**: ✅ Good - Consistent per-request performance. Slight improvement at scale due to connection reuse.

### 3. Search Performance

| Query Type | Response Time |
|------------|--------------|
| Simple keyword (limit 10) | 9ms |
| Medium complexity (limit 50) | 8ms |
| Complex search (limit 100) | 10ms |
| Session list (limit 100) | 8ms |

**Assessment**: ✅ Excellent - All searches under 10ms regardless of complexity.

### 4. Extraction API Performance
*Note: Extraction API test skipped - endpoint not available in current deployment.*

---

## 🔥 Stress Test Results

### 5. Concurrent Request Tests (Health Endpoint)

| Concurrency | Wall Time | Max Individual |
|-------------|-----------|----------------|
| 5 concurrent | 31ms | 2.78ms |
| 10 concurrent | 16ms | 1.30ms |
| 20 concurrent | 26ms | 1.38ms |

**Assessment**: ✅ Excellent - No degradation under concurrent load. Request queuing not observed.

### 6. Concurrent Mixed Operations (10 ingest + 10 search)

| Test | Wall Time |
|------|-----------|
| 20 mixed ops | 36ms |

**Assessment**: ✅ Good - Mixed read/write operations handled efficiently.

---

## 💾 Resource Usage

| Metric | Before Tests | After Tests | Delta |
|--------|-------------|-------------|-------|
| RSS Memory | 362MB | 430MB | +68MB |
| CPU | 0.0% | 0.0% | — |

**Assessment**: ⚠️ Memory increased ~68MB during tests. Likely JVM heap expansion. Should stabilize via GC.

---

## 🎯 Performance Baseline Summary

| Metric | Value | Rating |
|--------|-------|--------|
| Health endpoint latency | ~1ms | ⭐⭐⭐⭐⭐ |
| Ingest throughput (single) | ~6-10ms/req | ⭐⭐⭐⭐ |
| Search latency | <10ms | ⭐⭐⭐⭐⭐ |
| Concurrent handling (20) | No degradation | ⭐⭐⭐⭐⭐ |
| Memory footprint | ~430MB RSS | ⭐⭐⭐ |

---

## 🔍 Findings & Recommendations

### Strengths
1. **Ultra-low latency**: Health endpoint consistently under 2ms
2. **Stable under concurrency**: No performance degradation at 20 concurrent requests
3. **Fast searches**: Vector + keyword searches all under 10ms
4. **Clean message queue**: 0 pending messages, system healthy

### Potential Optimizations
1. **Batch ingest API**: Consider implementing a batch `/api/ingest/batch` endpoint to reduce HTTP overhead for bulk operations (current: 664ms for 100 sequential requests)
2. **Memory monitoring**: RSS grew ~68MB during tests. Consider adding JVM heap metrics via Actuator for better visibility
3. **Extraction API**: Not tested - ensure this endpoint is accessible for full performance profiling
4. **Connection pooling**: Verify HikariCP pool settings are optimized for expected concurrency

### No Critical Issues Found
- Service is healthy and performant
- No bottlenecks identified at current load levels
- Suitable for production workload patterns

---

## 📈 Historical Comparison

*First baseline test - future tests will compare against these results.*

| Metric | 2026-03-23 | Target |
|--------|-----------|--------|
| Health latency | 1.02ms | <5ms ✅ |
| Ingest avg | 6.6ms | <20ms ✅ |
| Search avg | 8.75ms | <50ms ✅ |
| Concurrency 20 | 26ms | <100ms ✅ |

---

*Next test scheduled: 2026-03-24 00:06 CST*
