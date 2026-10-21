# 后端审查证据归档 -100（2026-10-22）

> 第 399 轮（JS/TS SDK + SDK README，doc round 298）的证据正文。
> 工作文件：`docs/drafts/backend-review-findings.md`（本文档的 findings 终值：**1321 行 / 149063 字节**）。
> 本文档体量：**182 行 / 13132 字节**。

> ⭐ **归档文件创建后不得修改**：任何发现归档头数字失实，只能删除重建并迭代至不动点。

## 本轮新写的两条（正文逐字保留在本归档）

### P2-196: JS SDK 的出站 wire 字段名：十个带 body 的端点，**请求体的键集合**无一被断言 —— 而 README 把这些名字当契约发布

- **Scope**: → [scope-9][a6]
- **Status**: ✅ **已修（399 轮，JS SDK 侧，只加测试 + 改文档，SDK 主源码零改动）** —— 新增
  `src/__tests__/wire-format.test.ts`（**15** 例），逐端点钉住实际 JSON 键集合，并把 README 的
  「Wire Format / Wire 格式」段与实测键集合**双向**绑定。

### P2-197: `content` 与 `narrative` 是同一个后端字段的两个名字，而 **JS 是四家里唯一不检测这个冲突的 SDK** —— 它的双语 README **全篇 0 次**提及这两个字段

- **Scope**: → [scope-10][a7]
- **Status**: ✅ **已修（399 轮，文档方向，两语各一段）+ 钉进测试**；Go / Java 两家 README 的同一处
  遗漏**只记录不实施**（属它们各自轮次的文档方向）。

## 出站 wire 字段名：实测与对拍

JS SDK 的请求对象是**原样交给 `JSON.stringify`** 的 ⇒ wire 上的字段名就是**调用方写下的属性名**，
而记录这些名字的 TypeScript 接口在运行时**不存在**。所以「接口改名」＝**静默的 wire 变更**。

实测方式：用间谍 `fetch` 录下每个请求体解析后的 JSON 键集合（不是读接口推断），再逐端点与后端
`ApiRequests.java` 里的 `@JsonProperty` 对拍：

| 端点 | JS 实测键 | 后端 `@JsonProperty` | 对拍 |
|------|-----------|----------------------|------|
| `POST /api/session/start` | `session_id`, `project_path`, `user_id` | 同（+ `cwd`/`projects`/`is_worktree`/`parent_project`/`platform_source` 为后端多出来的） | ✅ |
| `PATCH /api/session/{id}/user` | `user_id` | `user_id` | ✅ |
| `POST /api/ingest/tool-use` | `session_id`, `cwd`, `tool_name`, `tool_input`, `tool_response`, `prompt_number`, `source`, `extractedData` | 同（+ `platform_source`） | ✅ |
| `POST /api/ingest/session-end` | `session_id`, `cwd`, `last_assistant_message` | 同 | ✅ |
| `POST /api/ingest/user-prompt` | `session_id`, `prompt_text`, `cwd`, `prompt_number` | 同（+ `platform_source`） | ✅ |
| `POST /api/memory/experiences` | `task`, `project`, `count`, `source`, `requiredConcepts`, `userId` | 同 | ✅ |
| `POST /api/memory/icl-prompt` | `task`, `project`, `maxChars`, `userId` | 同 | ✅ |
| `POST /api/memory/feedback` | `observationId`, `feedbackType`, `comment` | 同 | ✅ |
| `PATCH /api/memory/observations/{id}` | `title`, `subtitle`, `content`, `narrative`, `facts`, `concepts`, `source`, `extractedData` | 服务层读 `title`/`content`/`narrative`/`subtitle`/`source`/`facts`/`concepts`/`extractedData` | ✅ |
| `POST /api/observations/batch` | `ids` | 同（+ `project`/`orderBy`/`limit`） | ✅ |

⇒ **wire 格式本身没有错**。本轮要修的是**它没有被钉住**。

## 钉住缺口是怎么量出来的

底数取 `git show HEAD:` 的**未修改**套件（`client.test.ts`、`required-arguments.test.ts`、
`truncated-body.test.ts`），逐处归到所属端点：

| 端点 | HEAD 的 body 断言 | 断言了什么 | 断言过**键集合**？ |
|------|------------------|-----------|-------------------|
| `POST /api/session/start` | 有（`JSON.parse`） | `session_id`、`project_path` | ❌ |
| `PATCH /api/session/{id}/user` | 有（`JSON.parse`） | `user_id` | ❌ |
| `POST /api/ingest/tool-use` | **无** | ——（只断言 URL 与 method） | ❌ |
| `POST /api/ingest/session-end` | **无** | ——（只断言 URL） | ❌ |
| `POST /api/ingest/user-prompt` | 有（`JSON.parse`） | `prompt_text` | ❌ |
| `POST /api/memory/experiences` | 部分（`String(opts.body)`） | `count` 的取舍与取值 | ❌ |
| `POST /api/memory/icl-prompt` | 部分（`String(opts.body)`） | `maxChars` 的取舍 | ❌ |
| `POST /api/memory/feedback` | 有（`JSON.parse`） | `observationId`、`feedbackType`、`comment` | ❌ |
| `PATCH /api/memory/observations/{id}` | 有（`JSON.parse`） | `title`、`content`、`narrative`、`source`、`extractedData` | ⚠️ 只对两种**局部更新**形状断言过（八字段全 null；只给 `title`），**满字段形状从未断言** |
| `POST /api/observations/batch` | 有（`JSON.parse`） | `ids` | ❌ |

body 断言总数 **20** 处 = **14** 处 `JSON.parse(opts.body)` + **6** 处 `String(opts.body)`
（`required-arguments.test.ts` 与 `truncated-body.test.ts` 各 **0** 处）。

⇒ **十个端点，无一断言过请求体的键集合。**

⇒ README 的「Wire Format」段为其中三个端点发布了 **6** 个名字：
`session_id`、`cwd`、`tool_name`、`extractedData`（采集组）、
`requiredConcepts`、`userId`（Experience 和 ICL 组）
—— 这六个名字在**这三个端点的请求体上从未被断言过**。改一个接口属性名，套件不会响。

⇒ ⭐ **另有一条遗漏（不是失实）**：README 的「Wire Format」段**对 session-end 一个字段名都没发布**
（`session_id` / `cwd` / `last_assistant_message` 全篇 **0** 次）。
按「遗漏 ≠ 失实」，本轮**不补**，只记录。

## 别名冲突：活体实测

`content` 与 `narrative` 在后端是**同一个字段**。JS SDK **不检测**这个组合，两个都发；
后端**保留 `content`、丢掉 `narrative`**。2026-10-22 对活体后端（37777）实测，
用 JS SDK 的 `updateObservation` 发 PATCH，读回库里的那一列：

| 用例 | 发出去的 | 库里那一列 |
|------|----------|-----------|
| 改前 | — | `seed-value` |
| ① | `content='AAA'`, `narrative='BBB'` | **`AAA`** ⇒ narrative 被丢弃 |
| ② | `content=null`, `narrative='CCC'` | **NULL** ⇒ narrative 又被丢弃（用 `content IS NULL` 查库确认，不是靠 SDK 的空串兜底） |
| ③ | 只有 `narrative='DDD'` | **`DDD`** ⇒ content 缺席时 narrative 会被用上 |

⇒ **冲突的后果是静默的**：PATCH 返回 200，调用方以为写成功了。
而另三家 **直接拒绝**：Go 抛 `content|narrative` 校验错误（`dto/observation.go:167`）、
Java 在 `ObservationUpdate.Builder` 抛 `IllegalArgumentException`（`:85`）、
Python 抛 `ValidationError`（`field="content|narrative"`）。
⇒ **从那三家移植过来的调用方预期会报错，实际拿到的是一次只写了一半的写入。**

⇒ **JS README 双语对 `narrative` 的提及次数：0。**（`grep -c narrative README.md` ⇒ 0，
中文版同样 0；`content` 一词在中文版里也是 0 次。）
「每个字段有三种状态」那一节说 `ObservationUpdate` 的**每个**字段都有跳过/设置/清空三态，
却从头到尾没提有两个字段其实是同一个后端字段。

## 新增测试：`src/__tests__/wire-format.test.ts`

**15** 例：

- 逐端点钉住实际 JSON 键集合（**10** 例，期望值是**实测**出来的，不是照着接口抄的）；
- 一条「目录本身没漂」的清单用例；
- README「Wire Format / Wire 格式」段 ↔ 实测键集合的**双向**绑定（**2** 例，两语各一）：
  既要求 README 发布的每个名字都真的上了 wire，也要求这一组**恰好**发布那些名字
  （少了名字同样会红）；
- `content`/`narrative` 同时存在时**两个键都上 wire、且不报错**（**1** 例，钉住现状）；
- README 必须**点名**「其中一个会被静默丢弃」（**1** 例，删掉那段就红）。

## 变异复验：新测试自己也过了变异检验

| 变异 | 内容 | 期望 | 实测 |
|------|------|------|------|
| M0 | 只加一行注释（阴性对照） | 0 红 | **0 红**（388 passed） |
| M1 | `submitFeedback` 改发 `observation_id` | 红 | **2 红** |
| M2 | `buildICLPrompt` 改发 `max_chars` | 红 | **1 红** |
| M3 | README（英）删掉 `extractedData` 那一行 | 红 | **1 红** |
| M4 | README（英）删掉 `content`/`narrative` 那一段 | 红 | **1 红** |
| M5 | `updateObservation` 改成拒绝这个组合 | 红 | **3 红** |

## 本轮的自查更正：归档初稿里的三处失实数字

本归档的初稿（提交前）带着三处**在测量上就不成立**的说法，是提交前的一次自检把它们抓出来的。
三处都已按 `git show HEAD:` 重新实测并改准；**记录在此，是因为它们都属于同一族错误**：
**先下结论、再找证据**。

| 初稿的说法 | 实测 | 错的成因 |
|-----------|------|---------|
| 「四个端点零钉住」（tool-use / session-end / experiences / icl-prompt） | tool-use 与 session-end 确实**一条 body 断言都没有**；但 experiences 有 **4** 条 `count` 取舍断言、icl-prompt 有 **2** 条 `maxChars` 取舍断言 | 把「**没有键集合断言**」当成了「**没有任何 body 断言**」—— 前者才是真缺口 |
| 「README 发布的六个名字 = `extractedData` / `cwd` / `last_assistant_message` / `requiredConcepts` / `userId` / `maxChars`」 | README 发布的是 `session_id` / `cwd` / `tool_name` / `extractedData` / `requiredConcepts` / `userId`；`last_assistant_message` 与 `maxChars` 在两份 README 里**各 0 次** | 读了**实测的 wire 键**当成了**README 发布的名单**——两者只在一部分重合 |
| 「既有 **16** 处 body 断言」 | **20** 处 = **14** 处 `JSON.parse(opts.body)` + **6** 处 `String(opts.body)` | 只数了一种解析形态 |

⇒ 更正后的结论**比初稿更强**、而不是更弱：真正的缺口是
「**十个端点的请求体键集合，无一被断言**」，而 README 对 session-end 的**零发布**是**遗漏**不是失实。

## 判据脚本自身的错（这轮最值钱的一条）

第一版的变异判据把 vitest 输出的**最后一行**当汇总行 ⇒ 读到的是 `Duration …` 那一行 ⇒
每轮都解析成 `-1/-1` ⇒ **六个变异全部被判成 MISMATCH**，而实际上它们**全部按预期转红**
（`exit=1`、命中的测试文件都对）。⇒ **「没有输出」不是「通过」的近亲：判据读到空值时，
它把「读不到」当成了「读到了空」**。改成**在全文里找 `^\s*Tests\s+` 那一行**之后，
六个变异才各自对上期望值。

⇒ 与「校验器正常返回空/绿灯是最贵的一种失败」同族：**校验器读不到东西时，
必须让它说「读不到」，而不是让它说「空」**。

## 环境注记（不是产品缺陷，但值得记）

本轮探针用 `POST /api/ingest/tool-use`（工具使用路径）创建观察时，**接口返回 200 `accepted`
但库里 40 秒内没有行**，也没有进 `mem_pending_messages` 队列。查后端日志定位到根因：
抽取器调 LLM 时 **401** —— `Your api key: ****13fc is invalid`（DeepSeek 的错误格式）。

**用另一条通路复验**（不靠日志自证）：直接 `curl` `https://api.deepseek.com/chat/completions`
并带上同一把 key ⇒ 同样 **HTTP 401**、同样的报文 ⇒ 确认是**本机环境里的 DeepSeek 凭据失效**，
**不是**产品缺陷、**不是** SDK 缺陷，也不是「工具坏了」。

⇒ 工具使用路径依赖 LLM 抽取器，抽取器一失败，**fire-and-forget 的语义就变成静默丢数据**
（调用方拿到 200）。这是四家 SDK 都写明了的「尽力而为的捕获」语义，**不是新缺陷**。
本轮的别名实测因此**改走回归套件用的直写路径**（`POST /api/ingest/observation`）创建观察，
再用 JS SDK 的 PATCH 改、用 `content IS NULL` 查库读回 ⇒ **实测结论不受该凭据问题影响**。
回归与 Phase3 验收**不依赖**抽取器（它们走直写路径），所以本轮的验收结论依然成立。

## 验收

- JS SDK **388/388**（255 client + 97 必填参数 + **15** wire 格式 + 5 截断响应体 +
  8 http-server 示例 + 5 http-server 错误契约 + 3 http-server 暴露面）；
- 回归 **46/0/1**、Phase3 **25/0/0**，均 exit 0，跑在**新鲜实例 37790**（pid **66382**；
  `find backend/src -newer <jar>` 为空 ⇒ jar 比全部后端源码都新）；
- 指纹 `4a4d8ca9…` → `5af43c08…`，记录数 2238 → **2239**。

## 压缩复查

本轮 findings 写入前余 **3589** 字节。复查压缩杠杆：按 C10 口径，**已结案**条目里可压缩的
非标题内容只剩本轮前两条（P2-193 / P2-194，合计 **1454** 字节，压了也就省几百字节且立刻
再失真）；其余 **44** 条全是 **⏸ 待决**条目，按 C8 口径它们的 `Scope` / `Evidence` **已经是
一行指针**，C8 对 ⏸ 条目**早已耗尽**。⇒ **本轮未迁出任何条目**，**C8 / C10 同时枯竭**
（这是 **C10 的第四次复测**、**C8 的第五次**）。
