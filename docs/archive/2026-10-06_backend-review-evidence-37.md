# Backend Review Evidence 37 — 「2xx 但响应体不是 JSON 对象」的四家实测对照（第 292 轮）

> **归档规则**：⏸ 条目保留 **Problem** 的问题陈述；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

> 起因：Python SDK 的 12 个读方法一律写成 `X.from_wire(data or {})`（两处把 `or {}` 写在
> `_request_json(...)` 那一侧），而 `_request_json` **不做类型校验**，`resp.json()` 可以返回
> 列表、字符串、数字或 `null`。于是「响应体存在、能解析、但不是对象」这一类输入的处理
> 在四家之间**各不相同**。
>
> **方法**：一次性本地 HTTP server 精确控制 2xx 响应体，四个 SDK 各跑 5 种载荷。
> Go 用 `go run`，JS 用已构建的 `dist/index.js`，Python 用 `python3.11`，均直连本机临时端口。

## 块 1 / 2：四家实测对照表（2xx 状态码，仅改响应体）

| 2xx 响应体 | Go | Python | JS |
|---|---|---|---|
| `<html>…502 Bad Gateway…</html>` | 抛 `cortex-ce: failed to parse /api/observations response: invalid character` | 抛 `CortexError: failed to parse /api/observations response: Expecting value…` | 抛 `Error: cortex-ce: failed to parse /api/observations response` |
| `[]` | 抛 `… cannot unmarshal array into Go value of type` | **静默返回空结果** | **静默返回空结果** |
| `null` | **无错，返回零值** | **静默返回空结果** | **抛裸 `TypeError: Cannot read properties of null (reading 'items')`** |
| `"oops"` | 抛 `… cannot unmarshal string …` | **抛裸 `AttributeError: 'str' object has no attribute 'get'`** | **静默返回空结果** |
| `42` | 抛 `… cannot unmarshal number …` | **抛裸 `AttributeError: 'int' object has no attribute 'get'`** | **静默返回空结果** |

**三处要点**：

1. **Python 是三家里唯一对标量响应体抛「非 SDK 异常」的**——`AttributeError` 不在
   `CortexError` 层次内，调用方的 `except CortexError:` **接不住**。JS 对 `null` 抛的
   `TypeError` 同理。Go 两侧抛的都是 `fmt.Errorf` 包装过的 `encoding/json` 错误，**始终在 SDK 内**。
2. **`null` 是 Go 与 Python 共同的静默空洞**：Go 的 `json.Unmarshal([]byte("null"), &struct)`
   是**合法 no-op**（实测：零值、无错），Python 则经 `or {}` 变成 `{}`。两者都不会告诉调用方
   「后端回的不是对象」。
3. **Go 与 JS 在这 5 种输入里有 3 种行为相反**。二者只在 HTML 那一行一致。

## 块 2 / 2：Python DTO 层的对照（说明问题**不在**字段级）

对 `SearchResult` 逐字段污染（`id=1`、`session_id=['a']`、`title=3.14`、`facts='not-a-list'`、
`concepts=7`、`files={'a':1}`、`createdAt='not-a-date'`、`quality_score='high'`、`score='nope'`
共 11 个字段逐个替换）：**11/11 全部解析成功，零逃逸**。`dto.py` 通篇用 `data.get(...)`
（41 处）与 `_to_str` / `_to_str_list` / `_to_dict` 三个防御性转换函数，与 JS 的
`safeStringArray` / `safeRecord` 同源设计——**字段级类型错是被真正防住的**。

同一轮对**全部 16 个 `from_wire`** 施加 9 种恶意载荷（空字典 / 全 null / 键全 null / 类型全错 /
嵌套垃圾 / 极端值 / 字符串当对象 / 列表当对象 / 数字当对象）共 **144 次调用**：逃逸 **59 次**，
**全部**是 `AttributeError: 'X' object has no attribute 'get'`，且**全部**只由四种**顶层非 dict**
载荷（`None` / `str` / `list` / `int`）触发。**没有一个逃逸来自字段级污染。**

> **探针自身出错两处，先识别再采信**（否则会记成两个不存在的缺陷）：
> ①第一版把 `retrieve_experiences` 的参数写成 `limit=`，实际签名是 `count=`，
> 于是四种载荷一律报 `TypeError: got an unexpected keyword argument`——那是**调用错误**，不是响应问题；
> ②第一版调用了 `list_projects()`，该方法实际叫 `get_projects()`，四种载荷一律报
> `AttributeError: 'CortexMemClient' object has no attribute 'list_projects'`——**同样是我的调用错误**。
> 另有一处断言过严：要求 200+HTML 体的异常消息里出现 "JSON" 字样，而实际消息是
> `failed to parse /api/observations response: Expecting value…`——**行为正确，是断言写错**。
