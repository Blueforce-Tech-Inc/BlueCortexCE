# Backend Review Evidence 62 — 第 362 轮本轮新条目的整条正文（第 362 轮迁出）

> **归档规则**：承 `-61` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的字节余量已近于零），
> 工作文件只保留标题与 `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来
> 改变自身长度，本批**迭代至不动点**后再写盘。
>
> 本批迁出 `P2-116` 一条——**本轮新写**的条目（Python SDK 两种 `update_observation`
> 调用风格不等价），写完即整体迁出，工作文件留标题与 `- **Status**` 一行（含归档指针）。
> **正文逐字保留在本归档**，证据与结论均可回溯。
>
> 迁出前实测 **1412 行 / 141891 字节**；本归档实测 **114 行 / 10512 字节**。

### P2-116: Python SDK 的两种 `update_observation` 调用风格**不等价** —— kwargs 路径真能清空 `narrative` / `extractedData`，而 SDK 自己的注释与 P2-27 都断言「不能清空」

- **Scope / Evidence**:
  - `python-sdk/cortex-mem-python/cortex_mem/client.py:662-664` —— kwargs 路径的赋值循环
    `for kwarg, wire_key in ObservationUpdate._WIRE_FIELDS.items(): if kwarg in kwargs: body[wire_key] = kwargs[kwarg]`
    **既不查 `None`、也不查空 dict**（这是本条的全部根因）
  - `python-sdk/cortex-mem-python/cortex_mem/dto.py:515-523` —— dataclass 路径的 `to_wire()`
    **两处过滤**：`if val is not None` 跳过 `None`；`if attr == "extracted_data" and isinstance(val, dict) and not val: continue` 跳过空 dict
  - `python-sdk/cortex-mem-python/cortex_mem/client.py:665-670` —— `if not body:` 抛
    `ValidationError("at least one field must be provided for update")`，**一个请求都不发**
  - `backend/.../controller/MemoryController.java:311-395` —— 后端 PATCH 语义：
    **显式 null ⇒ 清除该字段，缺省 ⇒ 不变**（`:312-321` title、`:322-331` content/narrative、
    `:384-395` extractedData 三处同型）
  - `python-sdk/cortex-mem-python/examples/http-server/app.py:489` —— **Python demo 的 PATCH 走的正是 kwargs 路径**
    （`client.update_observation(obs_id, **kwargs)`），故 demo 今天就能清空
  - `python-sdk/cortex-mem-python/README.md:258-260` / `README-zh-CN.md:246` —— **README 已经写对了**：
    「以关键字参数传入 `extracted_data={}` 即可发送一个显式空的 JSONB 值——数据类形式
    `ObservationUpdate(extracted_data={})` 会把它从 wire 中省略，因此仍属空更新」
  - `python-sdk/cortex-mem-python/README.md:114` / `README-zh-CN.md:114` —— 同一份 README 的
    **绝对化表述**「只有非 None 字段会被发送到后端（PATCH 语义）」，**与它自己下面第 258 行矛盾**
  - `docs/archive/2026-10-04_backend-review-evidence-11.md:21-23` —— 归档里的**四家能力阶梯表**
    把 Python 判为「**两种都不能**（`if val is not None` / 显式 `continue`）→ **完全不能**」
    ——那两条机制**只存在于 dataclass 路径的 `to_wire()`**
  - `docs/drafts/backend-review-findings.md` P2-26 条目末段 —— **已写明**「这对 dataclass 路径成立，
    **对 kwargs 路径不成立**……那行只覆盖了一半的调用面，**应当标注**」，并称
    「**P2-27 的能力表应当立即标注 kwargs 路径**……**已在下方补注**」
- **Problem**: `CortexMemClient.update_observation` 的 docstring 写明「Supports two calling styles」
  并给出两个并列示例，**形如可互换**。**实测两者在「空值」上不等价**：
  - **dataclass 路径**：`to_wire()` 过滤掉 `None` 与空 `extracted_data` ⇒
    `ObservationUpdate(content=None)` 与 `ObservationUpdate(extracted_data={})` 产出**空更新**，
    被 `if not body` 拦下抛 `ValidationError`，**一个请求都不发**；
  - **kwargs 路径**：**既不查 `None` 也不查空 dict** ⇒ `content=None` 真的把 `{"narrative": null}`
    送上 wire，`extracted_data={}` 真的把 `{"extractedData": {}}` 送上 wire。

  后端 PATCH 语义是「**null 清除该字段，缺省则不变**」，故 kwargs 路径**确实清空了这两列**。
  **而 SDK 自己的注释与既有结论都断言「不能清空」**：`dto.py` 的类 docstring 写
  "Only non-None fields are sent to the backend"；`is_empty()` 写 "this SDK sends neither."；
  `to_wire()` 写 "this SDK can send neither ``{}`` nor ``null``, so ``extracted_data``
  **cannot be cleared through this client at all**"；归档里的四家能力阶梯表把 Python 判为「完全不能」。
  **同一发布包内自相矛盾**：README 第 258 行说 kwargs 路径能发 `{}`（对），
  docstring 说「不能清空」（错），README 第 114 行又说「只有非 None 字段会被发送」（与其自身第 258 行矛盾）。
- **实测证据（本轮亲跑，桩 session 截获 + 活体查库，双对照）**:
  1. **桩探针**（`/tmp` 独立脚本，**仓库零改动**）截获实际 wire body：
     - A) `ObservationUpdate(extracted_data={})` → `ValidationError`，**无请求**
     - B) `extracted_data={}` → `{"extractedData": {}}`
     - C) `ObservationUpdate(content=None)` → `ValidationError`，**无请求**
     - D) `content=None` → `{"narrative": null}`
     - E/F) `title="T"`（两种风格）→ 均为 `{"title": "T"}` ✓
     - G/H) `facts=[]`（两种风格）→ 均为 `{"facts": []}` ✓
     - **阳性对照 4 项全部按预期失败**（未知字段 / content+narrative 冲突〔kwargs 与 dataclass 各一〕/
       空更新）⇒ 探针**有能力**失败，故 A/C 的「不发请求」为真、不是探针失灵
  2. **活体端到端**（`POST /api/ingest/observation` 建观测 → SDK kwargs 路径 PATCH →
     `psql` 查 `mem_observations`）：
     - 初始：`content='P362-NARRATIVE-SENTINEL'`，`extracted_data={"n": 7, "probe": "keep-me"}`
     - dataclass `content=None` → `ValidationError`，**库两列均不变**（对照）
     - kwargs `content=None` → **`content` 变 NULL**
     - kwargs `extracted_data={}` → **`extracted_data` 变 `{}`**
     - dataclass `extracted_data={}` → `ValidationError`，**库不变**（对照）
     - 清理：探针行**残留 0**
  3. **后端侧独立确证**（curl 直连 PATCH 同一 id）：`{"narrative": null}` → `content` 变 NULL、
     `{"extractedData": {}}` → `extracted_data` 变 `{}`，均返 `200 {"status":"updated"}`
     ⇒ **后端语义与实现一致，问题不在后端**
- **与 P2-26 / P2-27 的关系**: P2-26 条目末段**已写明** P2-27 的能力表「对 kwargs 路径不成立」
  并称补注「**已在下方补注**」——**但 P2-27 条目（工作文件第 179–194 行）根本没有那句补注**，
  逐行核对确认只有 Problem / 已修（注释层）/ Status 三段，**无任何提及 kwargs 路径的字样**。
  **承诺过的补注从未落盘**。而 P2-27 的 Problem 仍写「Python **两种都发不出**」，
  其 Status 仍把「让 `{}` 发上 wire」列为**将来**的选项（称其会「从「不变」变成「落 `{}`」）——
  **而它今天经 kwargs 已经在落 `{}`**，故该 Status 的事实前提亦不成立。
- **处置**:
  - **注释层已修（零行为变更）**：`dto.py` 三处 docstring（类 docstring / `is_empty()` / `to_wire()`）
    与 `client.py` 的 `update_observation` docstring 改为如实描述**两条路径的差异**
    （并写明「**要清空字段请用关键字参数形式**」）；`README.md` / `README-zh-CN.md` 第 114 行的
    绝对化表述改为限定于**数据类形式**并指明关键字参数形式的例外，**双语同步**。
    **零行为变更由 AST 机器证明**：剥离 docstring 后两文件 AST **逐字相同**
    （dto.py 97357 字符 / client.py 69004 字符），字符串字面量数 358 → 358 / 262 → 262，
    pytest **453 passed**。
  - **行为不修**：两种收法都改变现有调用方的可观测行为 —— ①让 kwargs 路径也过滤 `None`/`{}`
    （**等于让今天已经能用的清空能力失效**，而 Python demo 的 PATCH 走的正是 kwargs 路径）；
    ②新增显式清空入口（**新增公开 API**）。真正的修法要先决定**哪种写法才是 SDK 想支持的清空方式**，
    属 API 契约决策。**与 P2-26 的判断一致，不重复立条**。
  - **P2-27 的更正**已在其条目内以**标注**形式补上（追加式审计链不可重写，更正只能用标注），
    归档里的能力阶梯表**不动**（归档文件创建后不得修改），改由工作文件的标注指向。
- **顺带核实（不另立条）**：`/api/projects` 活体返回 `projects` / `sources` / `projectsBySource`
  三个键，而四家 SDK（含 Go `dto/management.go:14-16`、JS `src/dto/misc.ts:24-26`）的
  `ProjectsResponse` **都只有 `projects`** ⇒ 平台来源分组四家一致缺失，与 P2-58 同族。
  `projects` 本身是全量（后端 `findAllProjects()`），丢的只是**分组**、不丢项目，故本轮不另立条。
- **本轮仪器错误（两处，均由验证而非通读发现）**:
  ①**把两次运行的观测搞混** —— 首次运行因 `psql` 路径写错，在**第一行** DB 读取就崩掉，
     建出来的观测**从未被 PATCH 过**；我却拿它去证明「SDK 的 PATCH 没生效」，
     差点把「**样本从未被处理**」误当成「后端没清」。改用正确的列名
     （表里是 `content`，**没有 `narrative` 列**）并重跑才拿到真读数。
  ②**路径存在性探针的解析基准太naive** —— 首版只试「仓库根」与「文档所在目录」，
     把 `scripts/regression-test.sh`、`V11__observation_quality.sql`、`pom.xml` 等
     **100 余个真实引用**全报成缺失；改成仓库级索引（按 basename/后缀在 `git ls-files` 里找）后，
     只剩 7 个 token 未命中 —— 其中 6 个是「**要你自己创建**的 IDE 配置文件」
     （`.vscode/launch.json` 等，`.gitignore:34,41` 确实忽略它们，文档写的是 "Create `.vscode/launch.json`"），
     1 个 `plugin/modes/` 经查是 `ModeService.resolveModesDir():167,178` 的**真实回退路径**
     （用户自建目录，仓库内本就不存在）——**零缺陷**。
