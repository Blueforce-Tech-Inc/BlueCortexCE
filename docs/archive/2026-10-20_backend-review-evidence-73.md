# Backend Review Evidence 73 — 第 372 轮本轮新条目的整条正文（第 372 轮迁出）

> **归档规则**：承 `-62` ~ `-72` 的体例，迁出**本轮新写**的条目全文（写作时 findings 的
> 字节余量只剩 40 字节，本轮先做了**五处零信息损失替换**才腾出空间：措辞统一 **3 条规则**
> 35 + 24 + 1 处、轮次标记 **1 条** 107 处、指针**显示名**去 `.md` **1 条** 98 处，
> 逐条实测 210 + 288 + 6 + 428 + 294 = **1226 字节**；
> **指针的 target、条目标题与结构性标签逐项对拍未变**），工作文件只保留标题与
> `- **Status**` 一行（末尾追加本归档指针）。
>
> **归档文件创建后不得修改。**
>
> 头部行数/字节数**由脚本从内存中的新内容实测得出**；因数字写在文件里会反过来改变自身长度，
> 本批**迭代至不动点**后再写盘。**本文件：124 行 / 11424 字节。**
>
> 本批迁出 **P2-131 / P2-132 / P2-133** 三条新条目。
>
> **本轮代码方向为 Java SDK，文档方向为运维/用户指南（doc round 272）。**
>
> **三条里有两条是同一个根因的两副面孔**：**活体 OpenAPI 描述在撒谎**（P2-130，后端侧，
> 第 371 轮已修）与**SDK 的 README 在教用户用一条会丢字段的路径**（P2-131，Java SDK 侧，本条）；
> 另两条则是**运维文档里的默认值与实现对不上**（P2-132 / P2-133）。
> 三条的共同教训是同一句话：**「文档写着的」与「代码做的」之间，没有任何东西在核对它们。**
---

### P2-131: Java SDK README 的 V14 示例用错端点 —— `recordObservation` 的 `source`/`extractedData` 被后端丢弃
- **Problem**: Java SDK 的 README「V14 Features」有两节教用户用 `recordObservation` 设
  `source` 与 `extractedData`（两语同款），而 `recordObservation` 发往 `POST /api/ingest/tool-use`
  —— **正是后端把这两个字段连同 `prompt_number` 一起丢弃的那个端点**。README 的「Backend API
  Alignment」表还写着 `recordObservation()` | `POST /api/ingest/tool-use` | **✅ source, extractedData**。
  于是「来源归属」与「结构化数据提取」两个**标题级承诺**，在 SDK 唯一的创建路径上**无一兑现**。
  SDK 的 `ObservationRequest` 注释（「V14: source attribution」「V14: structured key-value data」）
  同样没提这件事。
- **Scope / Evidence**:
  - **活体实测（每条比较都带阳性对照）**：`POST /api/ingest/tool-use` 带 `prompt_number=42`、
    `source="p371src-tooluse"`、`extractedData={"p371":1}` ⇒ `mem_pending_messages.prompt_number`
    为 **NULL**，`mem_observations.source` 与 `mem_observations.extracted_data` 命中 **0 行**。
    **阳性对照**：`user-prompt` 带 `prompt_number=7` ⇒ `mem_user_prompts.prompt_number` 落库 **7**；
    `observation` 带 `source="p371src-obs"`、`extractedData={"p371ctrl":2}`、`prompt_number=99` ⇒
    三者**全部落库**。⇒ 是**字段级丢弃**，不是字段不存在。
  - **「能兑现的那条路」实测**：`PATCH /api/memory/observations/{id}` 带
    `{"source":"p371-patch-src","extractedData":{"p371patch":1}}` ⇒ 返回 200，数据库直读得到
    `source=p371-patch-src`、`extracted_data={"p371patch": 1}` ⇒ **PATCH 确实落库**，
    而这正是 Java SDK `updateObservation` 走的那条路（`ObservationUpdate` 确实带 `source`/`extractedData`）。
  - **Java SDK 没有 observation 端点的方法**：`/api/ingest/observation` 在整个
    `cortex-mem-spring-integration` 里**零命中** ⇒ 在本客户端里，PATCH 是唯一能落库这两个字段的路。
  - **跨家结转**：Go 的 `dto.ObservationRequest` 带 `Source`（注释「V14: source attribution」）、
    `ExtractedData`、`PromptNumber`；Python 的 `record_observation` 把 `source`/`extracted_data`/
    `prompt_number` 塞进 body；JS 的 `ObservationRequest` 同款 ⇒ **四家都发这三个字段**，
    也都存在同型注释/文档表述。**本轮只修 Java 侧**，其余三家留待各自轮次。
- **Status**: ✅ **已修（372 轮）** —— README 双语的「Source Attribution」「Structured Data
  Extraction」两节改用 `updateObservation`（PATCH）并给出取 id 的写法（`listObservations(...)` →
  `.items().get(0).id()`），各加一段实测说明 tool-use 路径会丢弃这三个字段；
  「Backend API Alignment」表的 `recordObservation` 行由 **✅ source, extractedData** 改为
  **⚠️ … 接受但不落库**，表后加一段 ⚠️ 注（指出 `updateObservation` 才是落库的那条路）；
  `ObservationRequest` 的两处行内注释与类 Javadoc 补上「tool-use 路径会丢弃、请用 updateObservation 落库」。
  **只改 README 与注释，零可执行行、零行为变化**；Java SDK 测试 **196 通过 / 0 失败**。
  **跨家结转**：Go / Python / JS 三家同样发这三个字段且有同型表述，本轮未动。

### P2-132: TESTING.md 第 6 节把**后端**的 `DB_USERNAME`/`DB_PASSWORD` 写成测试脚本的变量，且 `DB_NAME` 默认值错、漏了 `DB_HOST`
- **Problem**: `docs/TESTING.md` 第 6 节「Test Environment Variables」的表把**后端**的
  `DB_USERNAME` / `DB_PASSWORD` 写成了测试脚本的环境变量，**测试脚本根本不读它们**；
  `DB_NAME` 的默认值写成 docker-compose 的 `claude_mem`（而 `regression-test.sh` 的默认是
  `claude_mem_dev`），并且**漏掉了 `DB_HOST`** —— 而 `regression-test.sh:41` 至今仍在读它。
  一张标题写着「Test」的表，六个变量里三个名不副实。
- **Scope / Evidence**:
  - **脚本实读的变量**：`regression-test.sh:40-44` = `SERVER_URL`（`http://127.0.0.1:37777`）、
    `DB_HOST`（`127.0.0.1`）、`DB_NAME`（**`claude_mem_dev`**）、`DB_USER`（`postgres`）、
    `DB_PASS`（`123456`）；两个 Docker 套件分别是 `claude_mem_compose_test` / `claude_mem_test`
    与 `testpassword123`。
  - **`DB_USERNAME` / `DB_PASSWORD` 读的是谁**：它们是**后端**的变量
    （`application.yml:80-81`：`${SPRING_DATASOURCE_USERNAME:${DB_USERNAME:postgres}}`）；
    在 `scripts/` 里它们只以 `DB_USERNAME=$DB_USER` 的**本地别名**形式出现（再透传进容器），
    **不作为输入变量**。`scripts/README.md:758` 自己就写着
    「Note: the variable is DB_PASS, not DB_PASSWORD」，并给出 `DB_NAME=claude_mem_dev`。
  - **2026-05-04 那次「修复」正是把名字改反的那一次**：文档自己的 changelog 记着
    「修正 `DB_USER`→`DB_USERNAME` 和 `DB_PASS`→`DB_PASSWORD`，修正 `DB_NAME` 默认值
    `claude_mem_dev`→`claude_mem`」——对**后端**而言正确，对**测试脚本**而言全错，
    且把仍在被读的 `DB_HOST` 当作「不存在的」删掉了。
  - 其余断言核实为真：`run-all-e2e.sh` 的 10 套与 5/10 的跳过条件（`is_streamable_mcp`）、
    `phase3-acceptance-test.sh` 的 15 个测试函数、20 个脚本的存在性。
- **Status**: ✅ **已修（372 轮）** —— 第 6 节的表改成四列（`Variable` / `Read by` / `Default` /
  `Description`），逐行标明**由谁读取**与真实默认值，补回 `DB_HOST` / `DB_USER` / `DB_PASS` 三行，
  并加一段导语点明「导出 `DB_USERNAME` 不会让测试脚本用上它」。**changelog 以新增行的方式补充**
  2026-05-04 那一条（**不改写它**——它当时对后端是对的，只是没注意到这张表讲的是测试变量）。
  双语同步更新。

### P2-133: DEPLOYMENT.md 的 `SPRING_PROFILES_ACTIVE` 默认 `prd` 只在 compose 下成立 —— 原生运行无 profile，表格默认值全部不成立
- **Problem**: `docs/DEPLOYMENT.md` 第 5.1 节把 `SPRING_PROFILES_ACTIVE` 的默认值写成 `prd`，
  而 `prd` **只是 `docker-compose.yml:52` 的默认**；原生 JAR 运行不设该变量时**没有任何激活的
  profile**（Spring 打印 `No active profile set, falling back to 1 default profile: "default"`）。
  此时第 5.3 / 5.4 两张表列出的「默认」**全部不成立** —— 真正生效的是 `SpringAiConfig` 的
  `@Value` 兜底值。更糟的是：**在这种状态下，那几个「可调」变量本身也失效**。
- **Scope / Evidence**:
  - **无 profile 的实测（启动 JAR，只给两个 key）**：日志 `No active profile set, falling back to
    1 default profile: "default"`；随后 `Creating OpenAI-compatible ChatModel:
    baseUrl=https://api.deepseek.com, model=deepseek-chat`、
    `Creating EmbeddingModel: baseUrl=https://api.siliconflow.cn, model=BAAI/bge-m3,
    dimensions=1024` ⇒ 生效值是 **dev 那一列**，而**不是**表里写的 prd 值。
  - **正反对照（每个比较都跑了对照组）**：
    ① 只设短别名 `OPENAI_BASE_URL=https://alias-only.invalid` ⇒ 日志仍是 `https://api.deepseek.com`
    ⇒ **短别名在无 profile 下失效**（别名只在 `application-prd.yml` / `application-dev.yml` 里定义）；
    ② 设完整名 `SPRING_AI_OPENAI_BASE_URL=https://full-name.invalid` ⇒ 日志变成
    `https://full-name.invalid` ⇒ **完整名的 base URL 生效**；但同一批设的
    `SPRING_AI_OPENAI_CHAT_MODEL=full-name-model` ⇒ 日志仍是 `deepseek-chat` ⇒ **模型名被忽略**；
    ③ 设 `SPRING_AI_OPENAI_EMBEDDING_BASE_URL=https://emb-full.invalid` ⇒ 生效
    （`baseUrl=https://emb-full.invalid`），而同批的 `SPRING_AI_OPENAI_EMBEDDING_MODEL` 与
    `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS=768` ⇒ 仍是 `BAAI/bge-m3` 与 `1024` ⇒ **被忽略**。
  - **根因（属性名对不上）**：`SpringAiConfig` 读模型与维度用的是
    `${spring.ai.openai.chat.options.model:...}`、`${spring.ai.openai.embedding.options.model:...}`、
    `${spring.ai.openai.embedding.options.dimensions:...}`，而 Spring 的宽松绑定把
    `SPRING_AI_OPENAI_CHAT_MODEL` 映到 `spring.ai.openai.chat.model`、
    `SPRING_AI_OPENAI_EMBEDDING_DIMENSIONS` 映到 `spring.ai.openai.embedding.dimensions`
    —— **各差一段**，值到不了字段。两个 base URL 读的是 `spring.ai.openai.base-url` /
    `spring.ai.openai.embedding.base-url`，**恰好对得上**，所以它们仍然生效。
    在 `prd` / `dev` 下这个问题不出现，因为那两个配置文件**自己**把变量名写了出来
    （`model: ${SPRING_AI_OPENAI_CHAT_MODEL:...}`）—— **映射变量名的是 profile，不是代码**。
  - 其余断言核实为真：`application-prd.yml` 与 `application-dev.yml` 的五组默认值与文档表格
    **逐项吻合**；短别名只定义在 prd、dev 识别 `OPENAI_*` 但没有 `EMBEDDING_*` 别名 —— 与文档一致；
    compose 的 `DB_NAME`/`DB_USERNAME`/`POSTGRES_PORT` 默认值与第 5.2 节一致。
- **Status**: ✅ **已修（372 轮）** —— 第 5.1 节的 `SPRING_PROFILES_ACTIVE` 行注明
  「`prd` **仅 Docker Compose 下**」并指向第 5.4 节的第三种状态；第 5.4 节新增一段与一张表，
  写明**无 profile** 时实际生效的值（即 dev 那一列）、并记录**哪些变量在无 profile 下会被静默忽略**
  及其属性名根因，结论是「**请显式设置 `SPRING_PROFILES_ACTIVE`**」。双语同步更新。
  **未改行为**：后端代码一行未动；本条只更正文档里的失实陈述与缺失的第三态。
