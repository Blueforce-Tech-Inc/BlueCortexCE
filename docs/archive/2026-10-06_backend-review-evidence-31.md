# Backend Review Evidence 31 — P2-56 范围更正与 P2-69 新立（第 282 轮）

> **归档规则**：工作文件保留 **Problem** 的问题陈述与 **Status**；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 2：P2-56 第 259 轮原始条目（逐字留存，供对照）

### P2-56: Java demo 里四个控制器有三个用了共享校验类，第四个把两个数值参数整个绕过去了——**而那个类的 Javadoc 宣称自己覆盖了所有控制器**

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-12.md`](../archive/2026-10-04_backend-review-evidence-12.md)（第 259 轮）。
- **Problem**: `DemoParams` 的 Javadoc 原本写着「Controllers take the raw `String` and call
  `boundedInt` rather than declaring an `Integer` parameter, **so this rule is the only thing
  that can decide what a value means**」。**这句话对 `ExperiencesController` 是假的**：
  `boundedInt` 的调用点只落在 `SearchController` / `ObservationsController` / `ExtractionController`
  三个文件里，而 `ExperiencesController` 把 `count` 与 `maxChars` **直接绑成 `Integer`**，
  再在方法体里手写 `count < 0 || count > 100` / `maxChars < 0`。
  后果是**同一个进程内部出现两种 400**：`InvalidParamAdvice` 只匹配 `InvalidParam`，
  **无人处理 Spring 的 `MethodArgumentTypeMismatchException`**（`DemoErrors` 只管后端异常），
  故两种 400 的**状态码相同、body 形状完全不同**（前者 `{"error":"…"}`、后者 Spring 默认体）。
- **Status**: ⏸ 记录不修（Javadoc 已按现状更正；实现待 P2-55 的文法决定）。

**第 282 轮判定：以上 Problem 的范围陈述不完整，故原条目整体迁入本归档，工作文件改写为更正后的版本。**
不完整之处有两处，均在标题与 Problem 的**枚举**上，而失效形态（两种 400、十六进制被接受）本身描述正确：

1. 标题写「**四个**控制器有三个用了共享校验类，**第四个**把**两个**数值参数整个绕过去了」，
   读起来是一个闭合的三比一划分。
2. Problem 写「`boundedInt` 的调用点只落在 `SearchController` / `ObservationsController` /
   `ExtractionController` 三个文件里，而 `ExperiencesController` 把 `count` 与 `maxChars` 直接绑成 `Integer`」——
   这一句把 `ExperiencesController` 表述成唯一的例外。

实测：**用方三个、绕过的是三个、绕过参数六个。**

---

## 块 2 / 2：P2-56 第 282 轮完整实测（Demo 方向，活体 37778）

### 探测环境

- demo jar 由本轮从当前源码重建（`mvn clean package -DskipTests`，退出码 0，jar 35078568 字节，03:05），
  启动于 `37778`，java pid 54973，`lsof` 显示 `TCP *:37778`（再次印证 P1-2：demo 绑全网卡）。
- 后端 `37777` 为本会话之外的既有进程，只读使用，未重启。
- 每个数值参数的畸形输入都同时测了 `1_0`（下划线）与 `0x10`（十六进制）两种。

### 静态普查：`boundedInt` 的全部调用点

`grep -rn "boundedInt" src/main/java/ | grep -v DemoParams.java` → **5 处、3 个文件**：

| 文件 | 行 | 调用 |
|---|---|---|
| `SearchController` | 48 | `boundedInt(limit, 0, 0, 100, "limit")` |
| `SearchController` | 49 | `boundedInt(offset, 0, 0, Integer.MAX_VALUE, "offset")` |
| `ObservationsController` | 53 | `boundedInt(limit, 0, 0, 100, "limit")` |
| `ObservationsController` | 54 | `boundedInt(offset, 0, 0, Integer.MAX_VALUE, "offset")` |
| `ExtractionController` | 89 | `boundedInt(limit, 0, 0, 100, "limit")` |

### 静态普查：全部非 String 的 `@RequestParam`

`grep -rn "@RequestParam" src/main/java/ | grep -vE "String [a-zA-Z]"` → **8 处**，其中 6 处是绕过 `boundedInt` 的数值参数：

| 控制器 | 参数 | 声明类型 | 行 | 方法体内范围检查 |
|---|---|---|---|---|
| `ExperiencesController` | `count` | `Integer` | 41 | 有（0–100） |
| `ExperiencesController` | `maxChars` | `Integer` | 101 | 有（≥0） |
| `MemoryController` | `count` | 原生 `int` | 59 | 有（0–100） |
| `MemoryController` | `maxChars` | 原生 `int` | 126 | 有（1–100000） |
| `MemoryController` | `count` | 原生 `int` | 161 | 有（0–100） |
| `SessionLifecycleController` | `promptNumber` | 原生 `int` | 82 | **无** |
| `ChatController` | `useTools` | `boolean` | 66 | 不适用（布尔，非整数文法） |
| `DemoParams` 自身 | — | — | 15 | Javadoc 内的举例，非真实参数 |

### 活体对拍（全部实测，非读码推断）

**A 组 · 使用 `boundedInt` 的端点（对照组）**

| 请求 | HTTP | body |
|---|---|---|
| `/demo/observations?limit=1_0` | 400 | `{"error":"limit must be an integer"}` |
| `/demo/search?q=x&project=P&limit=1_0` | 400 | `{"error":"limit must be an integer"}` |
| `/demo/search?q=x&project=P&limit=0x10` | 400 | `{"error":"limit must be an integer"}` |

**B 组 · `ExperiencesController`（`Integer` 绑定）**

| 请求 | HTTP | body |
|---|---|---|
| `/demo/experiences?task=t&project=P&count=1_0` | 400 | `{"timestamp":…,"status":400,"error":"Bad Request","path":"/demo/experiences"}` |
| `/demo/experiences?task=t&project=P&count=0x10` | **200** | `{"count":10,"experiences":[…]}` |
| `/demo/iclprompt?task=t&project=P&maxChars=1_0` | 400 | Spring 默认体 |
| `/demo/iclprompt?task=t&project=P&maxChars=0x10` | **200** | `{"maxChars":100,"prompt":"…","experienceCount":4}` |

**C 组 · `MemoryController`（原生 `int` 绑定）**

| 请求 | HTTP | body |
|---|---|---|
| `/memory/experiences?task=t&project=P&count=1_0` | 400 | Spring 默认体 |
| `/memory/experiences?task=t&project=P&count=0x10` | **200** | `[{…}]` |
| `/memory/experiences/filtered?task=t&project=P&count=0x10` | **200** | `[{…}]` |
| `/memory/icl/truncated?task=t&project=P&maxChars=1_0` | 400 | Spring 默认体 |
| `/memory/icl/truncated?task=t&project=P&maxChars=0x10` | **200** | `{"prompt":"…","experienceCount":4,"maxChars":100}` |

**D 组 · `SessionLifecycleController.promptNumber`（原生 `int`，无范围检查）**

| 请求 | HTTP | body |
|---|---|---|
| `POST /demo/session/prompt?…&promptNumber=1_0` | 400 | Spring 默认体 |
| `POST /demo/session/prompt?…&promptNumber=0x10` | **200** | `{"status":"prompt recorded","sessionId":"probe-282-1791227481"}` |
| `POST /demo/session/prompt?…&promptNumber=-1` | **200** | `{"status":"prompt recorded","sessionId":"probe-282-1791227481"}` |

### 负数与十六进制确认落库

`SELECT content_session_id, prompt_number, left(prompt_text,20) FROM mem_user_prompts WHERE content_session_id LIKE 'probe-282-%'`：

```
probe-282-1791227481|-1|hi
probe-282-1791227481|16|hi
```

即 `0x10` 以**十进制 16** 入库、`-1` 原样入库——文法违例不是只在响应层被放过，而是**真的写进了表**。

### 结论

失效形态与第 259 轮描述的**完全一致**（两种 400 体、十六进制被接受），**但适用面是六处而非两处、三个控制器而非一个**。
`DemoParams` 的类 Javadoc 在第 259 轮「按现状更正」之后**仍然只点名 `ExperiencesController`**，
故它与本条目一样低估了范围；第 282 轮已按实测把该 Javadoc 改写为完整枚举（零行为变化）。

---

## P2-69（新立，第 282 轮）：`promptNumber` 是 demo 里唯一没有范围检查的数值参数

### 取证

`SessionLifecycleController.recordPrompt` 把 `@RequestParam(defaultValue = "1") int promptNumber`
**直接**流入 `UserPromptRequest.builder().promptNumber(promptNumber)`，方法体里**没有任何范围检查**。
对照：同一 demo 的其余五个数值参数（`count` ×3、`maxChars` ×2）全部在方法体里写了范围检查。

实测：`promptNumber=-1` → HTTP 200「prompt recorded」，且 `mem_user_prompts.prompt_number` 真实落库为 `-1`。

### 不修的理由

给已发布的端点补范围检查 = **收窄它接受的值域**，属对外契约变更，按既定规则记录不单方面实施。
且这与 P2-55 / P2-56 是同一条文法线上的决定，应等那次产品决定一并处理。

### Status

⏸ 记录不修。