# Backend Review Evidence 71 — 第 370 轮本轮新条目的整条正文（第 370 轮迁出）

> **归档规则**：承 `-62` ~ `-70` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 219 字节），工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：88 行 / 7470 字节。**
>
> 本批迁出 **P2-129** 一条新条目。
>
> **本轮代码方向为 Demo，文档方向为设计文档（doc round 270）。**
>
> **本条与前几条不同**：它不是「文档比实现更不完整」，而是**四份实现彼此分裂**，
> 而**分裂点恰好被两份文档各写了一半**（Go README 说三家对齐，Python 的 docstring 说 JS 是 404）——
> **两份文档互相矛盾，且都不是当前代码的行为**。修复只落在「与已声明契约一致」的那两家。
---

### P2-129: 四家 demo 的 404 / 405 响应**分裂成三种形态** —— JS 对方法不匹配答 **404 + HTML**、Go 对未知路径答 **text/plain**，而 Go demo 自己的 README 写着「Every error is `{"error": "<message>"}`」
- **Problem**: 四家 demo 都在自家 README 或注释里确立了同一条错误响应契约，
  但**活体实测下三家各不相同**：
  | 场景 | Go | Python | JS | Java |
  |---|---|---|---|---|
  | 方法不匹配（对存在的路径发错动词） | 405 + `application/json` | 405 + `application/json` | **404 + `text/html`** | 405 + `application/json` |
  | 不存在的路径 | **`text/plain`**（`404 page not found`） | 404 + `application/json` | **404 + `text/html`** | 404 + `application/json` |
  也就是说，**同一个客户端错误，四家给出三种形态**：Go 的未知路径是**纯文本**，
  JS 的两个场景是 **HTML 页面**（`Cannot GET /x` / `Cannot POST /x`），
  只有 Python 与 Java 两家全程是 JSON。
  **这不是「框架默认如此所以无妨」**：Go demo 的 README 明文写着
  「**Every error is `{"error": "<message>"}`**」并把「Python demo 与 JS demo follow the same rule」
  作为**跨家断言**；而 **Python demo 的注释（`app.py` 的 `handle_http_exception` docstring）**
  恰恰写着「a wrong method answers 405 (Java, Go) or 404 (JS)」——
  **两处文档互相矛盾，且都不是当前代码的行为**（JS 早已不是 404，Python 那句在第 370 轮修完 Go/JS 后也过期了）。
- **Scope**:
  - **Go demo**：`go-sdk/cortex-mem-go/examples/http-server/main.go` —— `http.ServeMux` 的默认 404
    （`text/plain`，`404 page not found`）；修复 = 新增 `registerFallback(mux)`（`main.go:141-160`），
    注册 `"/"` 作为兜底路径，返回 `{"error": "not found: <path>"}`。
  - **JS demo**：`js-sdk/cortex-mem-js/examples/http-server/app.ts` —— Express 的默认 404 处理器
    返回 HTML，且 Express **根本不答 405**（错动词直接落到同一个 HTML 404）；
    修复 = 末尾新增终止处理器（`app.ts:504-575`），按 `app.router.stack` 取出路由表
    判定「路径存在但动词不对」→ **405 + `Allow` 头 + JSON**，否则 → **404 + JSON**。
  - **Python demo**：`python-sdk/cortex-mem-python/examples/http-server/app.py` —— `@app.errorhandler(HTTPException)`
    早已把 404/405 转成 JSON，**行为本就合规**；本轮只更正它 docstring 里已过期的
    「or 404 (JS)」那句。
  - **Java demo**：`examples/cortex-mem-demo` —— 404/405 走 Spring 默认错误体
    （`{"timestamp","status","error","path"}`），**JSON 但不是 `{"error": "<message>"}` 形状**。
- **Evidence**:
  **（一）活体实测（2026-10-20，四家 demo 同时在跑：Java 37778 / Go 37779 / Python 37780 / JS 37781）**：
  - 方法不匹配：`POST /health`（Java 用 `POST /demo/manage/health`）
    → Go **405** `{"error":"method not allowed: expected GET"}`、
    Python **405** `{"error":"The method is not allowed for the requested URL."}`、
    JS **404** `text/html` `<!DOCTYPE html>…Cannot POST /health`、
    Java **405** `{"timestamp":…,"status":405,"error":"Method Not Allowed","path":"/demo/manage/health"}`。
  - 不存在的路径：`GET /definitely-not-a-real-endpoint-xyz`
    → Go **404** `text/plain` `404 page not found`、
    Python **404** `application/json`、
    JS **404** `text/html` `<!DOCTYPE html>…Cannot GET /definitely-not-a-real-endpoint-xyz`、
    Java **404** `application/json`（Spring 默认形状）。
  - **阳性对照**（先证明探针看得见差异）：同一家的 `GET /health` → 200 vs `POST /health` → 405/404，
    **两读数不同** ⇒ 探针能分辨方法差异，不是「探针失灵所以读不出」。
  **（二）修后复测：四家状态码与 Content-Type 完全一致** ——
  方法不匹配 **四家全部 405**，未知路径 **四家全部 404**，
  未知路径的 Content-Type **四家全部 `application/json`**。
  **（三）测试**：新增 Go `examples/http-server/fallback_test.go`（2 个测试 / 5 个子用例）与
  JS `examples/http-server/error-contract.test.ts`（5 个测试），后者断言
  **响应体不含 `<!DOCTYPE html>`** 且 `Allow` 头列出可行方法。
  JS SDK 自带套件 **264 通过 / 0 失败**（原 259 + 本轮 5）；Go SDK 根模块与示例模块全绿。
  **（四）旁证（不是本条缺陷，但同源）**：`scripts/mcp-e2e-test.sh` 开头注释写着
  「SSE mode: /sse returns 200, /mcp returns 404」——**独立于四家 demo 的第二来源**，
  说明「SSE 模式下 `/mcp` 应当 404」是本仓库的既定判据。
- **Status**: ⏸ **Java 的 404 / 405 错误体要不要也拉齐成 `{"error": "<message>"}`，待决**（对外契约变更）——
  **本轮已实施 Go 与 JS 两家（零行为变更风险的部分）**：
  ① Go 新增 `registerFallback` 兜底，未知路径从 `text/plain` 变成 `{"error": "not found: <path>"}`；
  ② JS 新增终止处理器，方法不匹配从 `404 + HTML` 变成 `405 + Allow + JSON`，
  未知路径从 `404 + HTML` 变成 `404 + JSON`；
  ③ 两处改动都**只影响错误路径**，且**方向与已声明的契约一致**（不是发明新契约）；
  ④ Go demo README 的错误响应表补一行「Unknown path → 404」，JS demo README 新增
  「Error responses」段（此前只有 Go 的 README 有这段），Python 的过期注释已更正。
  **三选一**：
  ① **只做已做的（记录）**：Java 保持 Spring 默认错误体，文档里注明四家在此处的差异；
  ② **把 Java 也拉齐**（**对外契约变更**）：新增 `@RestControllerAdvice` 接管
     `NoHandlerFoundException` / `HttpRequestMethodNotSupportedException`，
     需同时确认 `spring.mvc.throw-exception-if-no-handler-found` 与
     `spring.web.resources.add-mappings` 的取值，否则静态资源处理器会先吞掉 404；
  ③ **改文档口径**：把 Go README 的「Every error is `{"error": "<message>"}`」限定为
     「本 demo 的错误路径」，并把 Java 的 Spring 默认体也写进契约里。
  **本轮只实施 ①②④（与已声明契约一致的部分），Java 的错误体属对外契约变更，未单方面实施。**
