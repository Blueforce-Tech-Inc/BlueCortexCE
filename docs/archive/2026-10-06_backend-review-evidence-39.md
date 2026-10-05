# Backend Review Evidence 39 — JS SDK 响应体解析矩阵（第 293 轮）

> **归档规则**：⏸ 条目保留 **Problem** 的问题陈述；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

> P2-71（Python）的同族问题在 JS SDK 上的实测。方法：一次性本地 HTTP server 精确控制
> 2xx 响应体，14 个公开读方法 × 6 种载荷 = **84 次调用**，逐次记录抛出的异常类型与是否静默。

## 块 1 / 2：84 次调用矩阵（2xx 状态码，仅改响应体）

| 2xx 响应体 | 结果 |
|---|---|
| HTML 错误页 | **14/14 抛裸 `Error`**：`cortex-ce: failed to parse {path} response` |
| `null` | **9/14 抛裸 `TypeError`**（`Cannot read properties of null (reading '<字段>')`），**5/14 静默返回默认值** |
| `[]` / `"oops"` / `42` / `false` | **56/56 静默返回良构的默认结果** |

**抛 `TypeError` 的 9 个**：`listObservations`、`getObservationsByIds`、`search`、`buildICLPrompt`、
`getQualityDistribution`、`getVersion`、`getStats`、`getModes`、`getLatestExtraction`。
**静默的 5 个**：`startSession`、`updateSessionUserId`、`retrieveExperiences`、`getProjects`、
`getExtractionHistory`。

**汇总：84 次调用，抛非 SDK 异常 23 次（14 + 9），静默 61 次。**

**根因**：`client.ts` 中 13 处 `as Record<string, unknown>` —— **TypeScript 类型断言在运行时是 no-op**。
其中 **9 处**是 `const r = raw as Record<string, unknown>` 之后立即取 `r.<字段>`（`raw` 为 `null` 即抛）；
另 **2 处**（234、486 行）用的是 `Array.isArray(raw)` —— **那是真正的运行时检查，对 `null` 安全**，
这正是 5 个方法静默而非抛错的原因。类型断言与运行时检查在同一个文件里并存。

**命名约定被违反**：SDK 每一处自有错误消息都带 `cortex-ce:` 前缀——`ValidationError`（`errors.ts:14`）、
`APIError`（`errors.ts:38`）、以及 `client.ts` 的 567 / 688 / 697 / 718 行。**唯独这一处 `TypeError`
没有前缀**，消息是 V8 引擎原文，把内部属性名（`'items'` / `'observations'` / `'version'` …）直接漏给调用方；
按 `cortex-ce:` 过滤日志的调用方会**完全看不到它**。

**跨家对照**（第 292 轮已测，本轮补齐 JS 的完整 6 载荷）：

| 2xx 体 | Go | Python（P2-71） | JS（本条） |
|---|---|---|---|
| HTML | 抛类型化 | 抛 `CortexError` | 抛裸 `Error` |
| `[]` | 抛类型化 | 静默 | **静默** |
| `null` | **静默零值** | 静默 | **抛裸 `TypeError`** |
| 标量 | 抛类型化 | 抛裸 `AttributeError` | **静默** |

**Go 是唯一一家对全部非对象输入都给类型化错误的**；JS 与 Python 各有一半输入落到「静默」，
另一半落到「非 SDK 异常」。

## 块 2 / 2：本轮核实为真、**不记为缺陷**的部分

1. **10 MB 响应体上限实现正确且已完整文档化**。`client.ts:655` 起：`Content-Length` **读之前**先查
   （超限直接抛，body 根本不被缓冲），读之后 `text.length * 3 > maxSize && utf8ByteLength(text) > maxSize`
   作兜底。`utf8ByteLength` 正确处理代理对（surrogate pair 记 4 字节并 `i++`）。
   **README「Response Size Limit」段把这两道守卫、裸 `Error` 的选择及其理由
   （"since the failure is local rather than an HTTP status"）、以及此前用 `String.length`
   导致 15 MB 体被误放行的历史都写清了** —— 准确，非缺陷。
2. **`clearTimeout(timer)` 在 `finally` 里**（`client.ts:700-702`），成功与失败两条路径都清理，
   定时器无泄漏。
3. **HTML 解析失败抛裸 `Error` 属 README 已确立的约定**（「HTTP 状态用 `APIError`、本地失败用普通
   `Error`」），**不是**未文档化的意外。JS SDK 只导出 `ValidationError` 与 `APIError` 两个类，
   `errors.ts` 里也确实只有这两个 `extends Error`（P2-16 所记的「JS 有 16 个」是 2 个类 + 14 个谓词函数）。

**探针自身出错两处，先识别再采信**（否则会记成两个不存在的缺陷）：
①第一版按位置传参调用 `startSession('s1','/tmp/p')` 等 4 个方法，而它们收的是**单个对象**
（`startSession({session_id, project_path})`），于是四种载荷一律抛 `ValidationError` ——
**那是请求侧校验失败，根本没走到响应解码**；②用 `e.constructor.name` 判定「是否 SDK 异常」，
而 `dist` 产物里类名被混淆成 `_ValidationError`，于是**把 SDK 自己的异常误判成非 SDK 异常**。
改为 `instanceof APIError / ValidationError` 后两者才归位。
