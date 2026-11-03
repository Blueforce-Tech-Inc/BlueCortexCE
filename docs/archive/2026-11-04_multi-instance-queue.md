# Multi-instance queue sharing and lease heartbeat

- Date: 2026-11-04 05:25:02
- Database: cortexce_load on claude-mem-test-pg:5433 (shared by both instances)
- Instances: http://127.0.0.1:37778 and http://127.0.0.1:37779
- Config: stale threshold 1 min, recovery every 5 s, heartbeat every 5 s, scheduler poll every 1 s, provider stub chat delay 0 / 70 s

## Claim path

```text
   instance A                        shared DB                        instance B
  scheduler poll  ─┐                                                  ┌─ scheduler poll
                   ├─►  UPDATE ... SET status='processing',             │
                   │      started_processing_at_epoch=now              │
                   │      WHERE id=:id AND status='pending'            │
                   │           └── row-count 1: claimed                │
                   │           └── row-count 0: someone else won ──────┘
                   ▼
  heartbeat every 5 s ──► UPDATE started_processing_at_epoch=now
                          WHERE id IN (this instance's in-flight) AND status='processing'
```

## 1. Shared queue fan-out

| metric | value |
|---|---:|
| jobs inserted directly into the queue | 60 |
| queue drained | True |
| observations persisted | 60 |
| observations lost | 0 |
| duplicate content hashes | 0 |
| rows left in processing | 0 |
| rows left pending | 0 |
| processed per instance (log) | instance 37778: 50, instance 37779: 10 |

## 2. Lease heartbeat vs stale recovery

- Provider delay: 70 s, stale threshold: 60 s, heartbeat: 5 s.
- Lease advanced during processing: **True**.
- Final row status: **processed**; queue drained: True; duplicate hashes: 0.
- Stale-recovery log lines during the run: {37778: 0, 37779: 0} (zero means no instance reclaimed the live lease).

| elapsed s | status | lease moved past claim |
|---:|---|---:|
| 0.0 | processing | +0.0 |
| 5.1 | processing | +2.0 |
| 10.2 | processing | +7.0 |
| 15.3 | processing | +12.0 |
| 20.3 | processing | +17.0 |
| 25.4 | processing | +22.0 |
| 30.5 | processing | +27.0 |
| 35.5 | processing | +32.0 |
| 40.6 | processing | +37.0 |
| 45.7 | processing | +42.0 |
| 50.7 | processing | +47.0 |
| 55.8 | processing | +52.0 |
| 60.9 | processing | +57.0 |
| 65.9 | processing | +62.0 |
| 71.0 | processed | ended |

Verdict: PASS. Pass requires every queued job persisted exactly once, no row stuck in processing, and a lease that keeps advancing past the stale threshold while the provider call is still running.

Elapsed: 74.3 s.

