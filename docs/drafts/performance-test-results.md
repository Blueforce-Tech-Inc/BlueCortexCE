# Performance Test Results

**Date**: 2026-03-23 09:28:21
**Server**: http://127.0.0.1:37777

## Test Results

- **Single observation creation**: 7229ms
- **Batch 10 items**: 25433ms total, ~2543ms/item (10/10 success)
- **Search query**: 1086ms (0 results)
- **Concurrent 5 requests**: 2625ms (5/5 success)
- **Health check**: 10ms
- **ICL prompt generation**: 13ms
- **Experiences API**: 14ms

## Summary

| Test | Duration | Status |
|------|----------|--------|
| Single observation | 7229ms | ✅ |
| Batch 10 items | 25433ms | ✅ |
| Search query | 1086ms | ✅ |
| Concurrent 5 | 2625ms | ✅ |
| Health check | 10ms | ✅ |
| ICL prompt | 13ms | ✅ |
| Experiences API | 14ms | ✅ |

## Performance Baseline

- **Average single request**: 7229ms
- **Average batch item**: 2543ms
- **Search response time**: 1086ms
- **Concurrent throughput**: ~1 req/sec

