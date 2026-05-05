# 上游新提交分析（2026-05-05）— Gateway 内存监控 + SessionSearch Bug Fix

**扫描区间**: `81cd67829..origin/main`  
**日期**: 2026-05-05 07:12 CST  
**上游区间内记忆相关提交**: 2 个  
**分析起点**: `81cd67829` (docs/drafts/hermes-memory 分析上限)  
**下次扫描起点**: `739b30bc0`

---

## 72.1 `6366fb9c8` — Periodic Gateway Memory Monitoring

### 提交概览

```
6366fb9c8 Port from cline/cline#10343: periodic gateway memory logging
Author: teknium1 <127238744+teknium1@users.noreply.github.com>
Date:   Wed Apr 29 17:06:53 2026 -0700
```

**背景**: Gateway 进程是长期运行的（long-lived），会累积缓存 agent 实例、session transcripts、tool schemas、memory providers、MCP 连接等。内存泄漏在单一日志行中不可见，只有随时间观察 RSS 增长才能发现。

### 新增文件

| 文件 | 行数 | 作用 |
|------|------|------|
| `gateway/memory_monitor.py` | 230 行 | 核心监控模块 |

### 设计解析

#### 核心函数

**`_get_rss_mb()`** — RSS 内存读取（双重兜底）:
```python
# 优先: resource.getrusage (stdlib, Linux/macOS)
import resource
maxrss = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
# Linux: KB; macOS: bytes（是的，没看错）
# fallback: psutil.Process().memory_info().rss
```

- Linux/macOS 用 `resource`（无额外依赖），Windows fallback 到 `psutil`
- `ru_maxrss` 是进程的高水位值（high-water mark），正是泄漏检测所需的
- 两者都失败时：发出 WARNING 并禁用监控（不崩溃 gateway）

**`log_memory_usage(prefix="")`** — 单行 grep-friendly 日志:
```python
logger.info(
    "[MEMORY] %srss=%dMB gc=%s threads=%d uptime=%ds",
    tag, rss, gc_counts, thread_count, uptime,
)
# 示例输出:
# [MEMORY] baseline rss=128MB gc=(1204,89,7) threads=12 uptime=0s
# [MEMORY] rss=135MB gc=(1405,92,8) threads=12 uptime=300s
# [MEMORY] shutdown rss=142MB gc=(1500,95,9) threads=12 uptime=3600s
```

收集三类指标:
- **RSS**: 进程驻留内存（MB）
- **GC counts**: `gc.get_count()` 返回 `(gen0, gen1, gen2)` 收集计数，衡量垃圾产生量
- **Thread count**: `threading.active_count()`，诊断线程泄漏

#### 生命周期管理

**`start_memory_monitoring(interval_seconds=300)`**:
- 全局 `_monitor_thread` + `_stop_event` + `_start_time`
- `_lock` 保护所有全局状态
- 启动前先调用一次 `log_memory_usage(prefix="baseline")`
- `daemon=True` 确保进程退出时线程自动终止

**`stop_memory_monitoring(timeout=2.0)`**:
- 写 shutdown snapshot（`log_memory_usage(prefix="shutdown")`）
- `_stop_event.set()` 通知监控循环退出
- `thread.join(timeout)` 有等待上限，不阻塞 shutdown

**`_monitor_loop(_stop_event, interval)`**:
- `threading.Event.wait(timeout)` 实现周期性唤醒
- `daemon=True` 线程，进程退出时自动消亡

### Gateway 集成

```python
# gateway/run.py — start_gateway() 中
start_memory_monitoring(interval_seconds=cfg.logging.memory_monitor.interval_seconds)

# shutdown 块中
stop_memory_monitoring()
```

### 配置项

```yaml
# hermes_cli/config.py
logging:
  memory_monitor:
    enabled: true          # 默认开启
    interval_seconds: 300   # 5 分钟
```

### 测试覆盖

10 个单元测试（`tests/gateway/test_memory_monitor.py`）:
- 格式正确性（`[MEMORY]` 前缀）
- baseline / shutdown snapshot
- double-start noop（防重复启动）
- 周期性 timer 触发
- daemon thread 不阻塞进程退出
- resource/psutil 均不可用时的 warn-and-disable 路径

### 与 Cline TypeScript 原版的差异

| 方面 | Cline (TS) | Hermes (Python) |
|------|-----------|-----------------|
| 定时机制 | `setInterval` + `unref()` | `threading.Event.wait()` |
| 内存指标 | `process.memoryUsage()` ext/arrayBuffers | `resource.getrusage` + GC counts |
| Python 特有 | N/A | `gc.get_count()` + `threading.active_count()` |
| 泄漏检测 | V8 heap snapshot near OOM | `tracemalloc` 可选（开销大未启用）|

### CE 借鉴

**可执行借鉴** — BlueCortexCE 后端服务长期运行场景（Gateway / MCP Server）:

1. **periodic memory logging**: 
   - 直接复用 `gateway/memory_monitor.py` 设计思想
   - 集成到 Spring Boot actuator `/actuator/metrics` 或自定义 `/api/admin/memory-stats`
   - 5 分钟间隔，daemon thread，logback 输出 grep-friendly 行

2. **GC stats 作为泄漏信号**:
   - JVM GC 日志（`GCgarbageCollectorMXBean`）代替 Python `gc.get_count()`
   - 可通过 JMX endpoint 暴露

3. **关键差异**: 
   - Python `resource.getrusage` 在 JVM 中不可用，需要用 `ManagementFactory.getMemoryMXBean()`
   - Java 没有原生 daemon thread 概念，用 `ExecutorService.awaitTermination()` 模拟

4. **BlueCortexCE 具体建议**:
   ```java
   // 每 5 分钟记录一次内存快照到日志
   // MemoryMXBean + GC beans
   // 格式: [MEMORY] heap_used=XMB heap_max=YMB gc=(gen0=X,gen1=Y) threads=N uptime=Zs
   ```
   长期运行后，可通过日志时间序列发现内存增长趋势。

---

## 72.2 `319141a0d` — SessionSearch TOOL Row Truncation Bug Fix

### 提交概览

```
319141a0d fix(session_search): truncate TOOL rows with None tool_name
Author: Teknium <127238744+teknium1@users.noreply.github.com>
Date:   Thu Apr 30 20:25:58 2026 -0700
Co-authored-by: toaiclaw-a11y <264816063+toaiclaw-a11y@users.noreply.github.com>
```

### 问题描述

`_format_conversation()` 中的截断逻辑是:

```python
# 修复前
if role == "TOOL" and tool_name:   # ← tool_name 为 None 时条件为 False
    if len(content) > 500:
        content = content[:250] + "\n...[truncated]...\n" + content[-250:]
    parts.append(f"[TOOL:{tool_name}]: {content}")
```

当 `tool_name is None` 时，该分支被跳过，content 走默认格式化路径，导致 **tool output 全文（可能很大）直接拼入摘要上下文**，淹没实际对话内容。

### 修复方案

```python
# 修复后
if role == "TOOL":
    # 所有 TOOL rows 都截断，不管 tool_name 是否存在
    if len(content) > 500:
        content = content[:250] + "\n...[truncated]...\n" + content[-250:]
    header = f"[TOOL:{tool_name}]" if tool_name else "[TOOL]"
    parts.append(f"{header}: {content}")
```

关键变更:
- 去掉 `and tool_name` 条件，所有 TOOL rows 都经过截断保护
- `tool_name` 为 None 时渲染为 `[TOOL]`（无后缀）
- 兼容存储中偶尔出现的 `tool_name=None` 历史记录

### 发现来源

@toaiclaw-a11y 在 PR #2579（已关闭，其他部分已被 `_truncate_around_matches` 替代）中首次发现此问题。

### CE 借鉴

BlueCortexCE 的 SessionSummaryService 在将 tool results 写入 observation 时应:

```java
// 处理任何 content 类型的统一截断逻辑
if (content != null && content.length() > TRUNCATION_LIMIT) {
    String truncated = content.substring(0, 250) + "\n...[truncated]...\n" 
                     + content.substring(content.length() - 250);
    // 不要依赖 tool_name 存在来判断是否截断
}
```

**防御性设计**: 无论 tool_name 是否存在，**一律截断**超长 tool content。这是防止未知数据格式破坏上下文预算的正确方式。

---

## 汇总: CE 行动计划

| 优先级 | 任务 | 来源 |
|--------|------|------|
| P2 | 为 Spring Boot 后端服务添加 periodic memory logging (GC stats + heap) | `72.1` |
| P2 | SessionSummaryService 中统一截断所有 tool results，不依赖 tool_name 存在 | `72.2` |
| P3 | 研究 BlueCortexCE 的 long-lived 进程（MCP Server / Gateway）是否有内存监控 | `72.1` |

---

## 下次扫描计划

下次 cron 巡检从 `origin/main` `739b30bc0` 继续扫描记忆相关提交。
