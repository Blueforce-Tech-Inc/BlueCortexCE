# Performance Test Results

**Date**: 2026-10-08 01:26:38
**Server**: http://127.0.0.1:37777

## Test Results

- **Single observation creation**: 151ms
- **Batch 10 items**: 634ms total, ~63ms/item (10/10 success)
- **Search query**: 36ms (0 results)
- **Concurrent 5 requests**: 128ms (5/5 success)
- **Health check**: 21ms
- **ICL prompt generation**: 30ms
- **Experiences API**: 20ms

## Summary

| Test | Duration | Status |
|------|----------|--------|
| Single observation | 151ms | ✅ |
| Batch 10 items | 634ms | ✅ |
| Search query | 36ms | ✅ |
| Concurrent 5 | 128ms | ✅ |
| Health check | 21ms | ✅ |
| ICL prompt | 30ms | ✅ |
| Experiences API | 20ms | ✅ |

## Performance Baseline

- **Average single request**: 151ms
- **Average batch item**: 63ms
- **Search response time**: 36ms
- **Concurrent throughput**: ~39 req/sec

