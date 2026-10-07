# Backend Review Evidence 45 — 第 340 轮已结案条目的整条正文（第 340 轮迁出）

> **归档规则**：承 `-40` ~ `-44` 的体例，迁出**已完全结案、不再有待决动作**的条目
> **全文**，工作文件只保留标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为写入 P2-97 后文件**行数越线**而迁出 **2 条**：P2-78（第 323 轮结案，正文残余）、
> P2-96（本轮第 340 轮结案）。
>
> **两条都不是「首次笔误」，而是复发**：
> P2-78 的 CORS `allowedMethods` 漏 `PATCH` 是第 312 轮复查时才发现的（第 311 轮只查到 2/3），
> 而 P2-97 本轮抓到的 `project` / `projectPath` 参数名，**2026-04-01 已经修过一次**——
> 那次只改了 `getLatestExtraction` / `getExtractionHistory` 两行，同表另外 3 行原样留下。
> **同一次审查只改到一半，比不改更糟**：它让下一位读者以为这一列已经核过。

## 块 1 / 2：P2-78 全文（第 340 轮逐字迁出）

### P2-78: CORS 的 `allowedMethods` **漏了 PATCH**——按文档开启 CORS 后，两个 PATCH 端点对浏览器静默失效
- **Scope / Evidence**: `backend/src/main/java/com/ablueforce/cortexce/config/WebConfig.java:52-57`
  （`/api/**` 映射的 `allowedMethods`）；配置项 `claudemem.cors.allowed-origins` 定义于同文件 `:18`。
- **Problem**: 允许方法列表是 `GET, POST, PUT, DELETE, OPTIONS`——**没有 `PATCH`**，
  而活体 `/v3/api-docs` 明确有**两个 PATCH 端点**：
  `PATCH /api/session/{sessionId}/user` 与 `PATCH /api/memory/observations/{id}`（活体方法分布
  `GET 37 / POST 25 / PUT 1 / PATCH 2 / DELETE 2`）。**跨域预检按允许清单判定**，
  清单里没有的方法**直接 403、不带任何 CORS 头**。
  **实测对照（第 311 轮，37790 开启 CORS / 37777 未开启）**：

  | `Access-Control-Request-Method` | 37790（已开启 CORS） | 37777（未开启） |
  |---|---|---|
  | `GET` / `POST` / `PUT` / `DELETE` / `OPTIONS` | **200** + `Allow-Methods` 头 | **403** |
  | **`PATCH`** | **403，无任何 CORS 头** | **403** |

  **对照组是关键**：37777 上 GET 与 PATCH **同为 403**，说明 37790 上 PATCH 的 403
  **不是「CORS 没开」那个基线**，而是**被允许清单单独拒绝**——同一实例上 GET 200 / PATCH 403
  就是判别实验本身。
  **今天不可触发**：`claudemem.cors.allowed-origins` **全仓从未被设置**
  （只在 `@Value` 默认值与 `docs/drafts/spring-ai-integration-plan.md:138` 出现；
  `application*.yml` / `docker-compose.yml` / `.env.example` / 脚本全部零命中），
  默认空值 → `allowedOrigins` 空数组 → **所有预检一律 403，CORS 默认关闭（安全默认成立）**。
  但那份 draft **明确指导浏览器前端用户去配置它**，照做之后恰好丢掉这两个端点。
- **Status**: ✅ **已修** —— 逐字迁入 [`…-40.md`](../archive/2026-10-07_backend-review-evidence-40.md)（第 323 轮）。
- **第 312 轮复查 2/3 的结果：新发现问题，计数重置** —— 见 P2-79。

## 块 2 / 2：P2-96 全文（第 340 轮逐字迁出）

### P2-96: `DemoErrors` 自陈的 catch 块数写错了 1 —— 而第 328 轮「核实四项计数全对」那次核实本身是错的
- **Scope / Evidence**: `examples/cortex-mem-demo/src/main/java/com/example/cortexmem/DemoErrors.java:20`
  —— 类 Javadoc 自陈「the twelve controllers hold **forty** `catch (Exception e)` blocks
  between them, and only three of those reach these helpers」。
- **实测**（`mvn -o clean test` 之外的独立计数，两种方法互证）：

  | 方法 | 结果 |
  |---|---|
  | 裸 `grep -c "catch (Exception e)"` 逐文件相加 | **42**（含 Javadoc 自己那 1 处引用） |
  | 剥掉块注释与行注释后按 `catch (Exception e)` 计 | **41** |
  | 放宽为 `catch (Exception <任意变量名>)` | 同样 **41**（**没有**变量名不同的块） |

  并按 `git show <rev>:` 在 `461099b` / `cb0ac07` / `cb0ac07~1` / `b180fa4` / `3a6922d`
  五个提交上复算，**一律 41** —— 源码里**从未**是 40。
- **另外三个计数经核实全对**（这一步是必要的，因为下面那条教训正是「只修一个」的风险）：
  **12 个控制器** ✓、**3 个块到达助手** ✓（`ObservationsController` 2 个 +
  `FeedbackController` 1 个）、**10 个控制器答平铺 500** ✓（12 − 2，与「3 个块」自洽）。
  `{@link #statusOf}` / `{@link #messageOf}` 指向的方法也都还在
  （该类实有 `statusOf`/`isNotFound`/`clientStatus`/`messageOf` 四个）。
- **第 328 轮那次核实的错在哪**：它记录「`grep -c` 得 **42**——docstring 自己那句也被数进去；
  剥注释后得 **40**」。前半句与我的实测一致，**后半句的减法错了**：42 − 1 = **41**，不是 40。
  即**一次「已核实」的结论里藏着一个未复核的算术步骤**，而后续多轮都直接引用了它。
- **为什么可以单方面改**（判据：**失实陈述 + 正确值被权威确定**）：
  正确值 41 可由两种独立方法复现，且改动**只涉及注释**（`git diff`：1 增 1 删，
  非注释行 **0**）；`mvn -o clean test` → **40 tests / 0 failures / BUILD SUCCESS**。
- **本轮我自己也差点犯同一个错**：第一版脚本按 `statusOf|messageOf|isNotFound` 找
  「到达助手的块」，得 **2** 个，于是差点把 Javadoc 的「three」也当成错的。
  实际上控制器调的是 **`DemoErrors.clientStatus(e)`**，不在我的模式里 ——
  **是我的正则漏了一个方法名，不是文档写错了。** 补上 `clientStatus` 后得 3，与文档一致。
- **Severity**: 低（纯注释失实，行为零影响；且它描述的是这个 demo 最核心的已知缺陷——
  「一个 demo 既丢又造后端状态码」——的数字依据，数字错了会削弱那条警告的分量）。
- **Status**: ✅ **已修（第 340 轮，纯注释零行为变更）** —— `forty` 改为 `forty-one`。
  `mvn -o clean test` 全绿（40/0/0，exit 0）。

