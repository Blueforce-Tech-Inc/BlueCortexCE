# 健康检查历史 25 — 第 329/330 轮（第 342 轮迁出）

> **归档规则**：承 `-19` ~ `-24` 的体例，迁出**较早轮次**的完整报告，
> 工作文件只保留 `## 第 N 轮 — ` 标题与一行自足指针；条目首尾的空行留在工作文件。
>
> **归档文件创建后不得修改。**

> 本批为写入第 342 轮报告后 `docs/drafts/health-check-task.md` 达 **1551 行**
> （行数越线；字节尚未越线）而迁出 **2 轮**：第 329、330 轮。
>
> 工作文件里第 312–328 轮已是 3–4 行的一行指针（更早各轮在 `-19` ~ `-24` 中），
> **第 329/330 轮是最早仍保留完整报告的两轮**，故只迁这两轮即可取回余量。

## 块 1 / 2：第 329 轮全文（第 342 轮逐字迁出）

## 第 329 轮 — 2026-10-07T07:00:00+08:00
代码方向：**Backend**（**新立 P2-91** —— 源码 `P1-N` 编号与 findings 编号冲突，
且 `P1-2` 在本仓历史上已指代过三个不同主体）；文档方向：**API 文档**（doc round 229，**零缺陷**）
本轮另把后端最后两个**从未被任何 finding 或归档提及过**的类审完

### 健康预检

37777 在监听（pid 4029），health 200。指纹 `a4525469…` / 2218 **与基线一致**，工作区干净，HEAD = `b8e7d76`。

### 代码方向（Backend）：先找真正的零覆盖面

**方法上先纠了一个自己的错**：我先按**文件名**（`XxxService.java`）grep findings，
得出「后端大半类都是零覆盖」；改按**类名**后发现不对——那些类的细节被我自己的压缩
**迁进归档**了，工作文件只剩 `…-8.md` 短名。**「工作文件零命中」≠「从未审过」**。
于是把 findings **与全部归档**合并统计，最终全后端只剩 **2 个类**真正零提及：
`entity/SummaryEntity.java`（123 行）、`config/QueueHealthIndicator.java`（54 行）。

**`SummaryEntity`：零缺陷。** 逐列与 schema 对账：V1 建的是 `memory_session_id`，
而实体映射 `content_session_id` —— 查 V13 确认它**确实**做了
`ADD COLUMN content_session_id` → 回填 → `SET NOT NULL` → `DROP CONSTRAINT ... _fkey`，
活体 `information_schema` 复核：`content_session_id` / `created_at_epoch` / `project_path`
均 NOT NULL、`memory_session_id` 已消失、`platform_source` 默认为 `'claude'`
——**与 Java 字段默认值 `= "claude"` 一致**，两侧无缝隙。

**`QueueHealthIndicator`：发现 P2-91。**
它的类 Javadoc 写着「**P1-2**: Custom health indicator for message queue monitoring」。
穷举全库 `^\\s*\\*?\\s*P[12]-\\d+[:：]`，这类标签**只有 3 处**，
且三者内部自洽（P1-1→StaleMessageRecoveryTask、P1-2→队列监控、P2-1→ContextCacheService），
显然是一份**功能优先级**编号。但本仓 findings 里 `P1-2` 指的是**「Java demo 的 `?path=` 无任何路径校验」**。

**关键证据：冲突已经真实发生过一次。** 巡检自己的归档记录了第 160 轮的
「**P1-2 导入端点把校验失败报成成功跳过**」（`health-check-history-6.md:322,409,520`、
`history-7.md:102,200,291`）；该条目如今已不在工作 findings（`grep 导入端点` 零命中），
而现存 `P1-2` 又是另一个主题。故 `P1-2` 在本仓至少指代过
**①导入端点校验、②Java demo 路径穿越、③队列健康指示器**。

**不单方面改**（判据同 P2-87）：那三处注释**本身没写错**；问题在于这套编号
**在全仓库任何地方都没有定义**（穷举 `*.md`/`*.txt`/`*.yml` 零命中，
唯一提到 stale-message recovery 的是 gitignored 的 `CLAUDE.md:357`，且只是一句功能描述）。
它们到底指上游 claude-mem issue 号、某份已废弃的计划、还是作者自己的优先级，
**只能推断** —— 写错编号的含义比标出冲突更糟。

### 文档方向（API 文档，doc round 229）：`docs/API.md` + `API-zh-CN.md`

P2-48 曾在 `CLAUDE.md` 里抓到 9 个活体 404 的**幽灵端点**，故本轮专门做端点存在性核对。

**先用窄口径拿到干净结论**：只取文档自己声明的 `#### METHOD \`path\`` 端点小节
（API.md:14 明写这条版式约定），逐条比对活体 `/v3/api-docs`：

| 文件 | 端点小节数 | 幽灵端点 |
|---|---|---|
| `docs/API.md` | 5 | **0** |
| `docs/API-zh-CN.md` | 66 | **0** |
| 合计 | **71** | **0** |

**顺带发现一处版式不对称**：英文版多数端点用 `### 描述名` + 代码块，
只有 5 个用 `#### METHOD \`path\``；中文版则 66 个**全部**用后者。
两者内容都完整，**版式不同不等于有缺陷**，仅记录不追改。

**本轮三处仪器错误，全部在采信前识别**（与前几轮同一模式）：
①第一版提取器把正则限定在 `/api/` 前缀，漏掉 `GET /stream`（`API.md:2391` **确有记载**）；
②第二版把 **changelog 里的历史叙述**当成端点声明，产出两个假的「幽灵端点」
（`2968`/`2972` 行的裸路径），并因跳过 changelog 逻辑过度而让中文版只剩 37 条；
③`/v3/api-docs` 被当成「文档写了但活体没有」——它其实是 OpenAPI 自身的地址。
**三次都是我的提取器不可靠，文档本身没问题**。正因如此，本轮**不拿这个提取器支撑任何 finding**，
只用可逐条复核的最窄口径下结论。

### 变更检测

本轮只新增一条 findings（P2-91），未改任何代码或既有文档。
指纹仍 `a4525469…` / 2218，**与基线一致** → **不跑完整验收、不推进基线**。
37777 保留运行。

### 下一轮

代码方向：**Java SDK**；文档方向：**SDK README**（doc round 230）。

## 块 2 / 2：第 330 轮全文（第 342 轮逐字迁出）

## 第 330 轮 — 2026-10-07T07:25:00+08:00
代码方向：**Java SDK**（**新立并修复 P2-92** —— 注释说 `defaultCount` 未被钳制，
而它上面 20 行的构造函数恰恰钳了）；文档方向：**SDK README**（doc round 230，**零缺陷**）

### 健康预检

37777 在监听（pid 4029，本会话启动并保留），health 200。指纹 `5d960e9b…` / 2218，
工作区仅 1 处未提交改动（P2-92 的注释），HEAD = `be3f74d`。

### 代码方向（Java SDK）：矛盾就在同一个类的 6 行之内

`CortexMemoryTools.java` 的注释（`:66-71`）写着：

> `defaultCount` itself is not clamped, so configuring `cortex.mem.default-experience-count`
> above 10 means a call that omits `count` asks for more than the range this parameter
> advertises. … the two branches do not agree.

而**它上面 20 行**的构造函数（`:46`）正是：

```java
this.defaultCount = Math.max(1, Math.min(defaultCount, 10));   // ← 已经钳到 [1, 10]
```

分叉本身（`:72`）是 `count == null || count <= 0 ? defaultCount : Math.min(count, 10)`。
既然 `defaultCount` 构造时已入 `[1,10]`，**两条分支实际是一致的**，注释所称的缺陷**不可能发生**。

**配置链路也确认了钳制只有这一处**：`CortexMemProperties.defaultExperienceCount`
（默认 `4`，setter **不做任何钳制**）→ `CortexMemAutoConfiguration:121-123` 原样传入 →
`CortexMemoryTools:46` 钳制。

**受控实验**（临时 Mockito 探针捕获真正上线的 `ExperienceRequest`，测完即删）：

| 配置的 defaultCount | 模型给的 count | **实际发出** |
|---|---|---|
| 20 | 省略 | **10**（不是 20） |
| 10 | 省略 | 10 |
| 3 | 省略 | 3 |
| -5 | 省略 | **1** |
| 20 | 99 | 10 |
| 20 | 0 | 10 |

**六行全部落在 `1-10`，无一行越界**，与注释所称的缺陷方向相反。

**为什么可以单方面改**（判据：**失实陈述 + 正确值被权威确定**）：正确值就在同一文件的 `:46`，
并已由上表实测坐实；改动**只涉及注释，零行为变化**。这与 P2-87（版本钉，正确值只能推断）
不同，本条不存在需要推断的成分。

**两处探针自身错误，均在采信前识别**（本循环的既有纪律）：
①`ExperienceRequest` 是 **record**，访问器是 `count()` 而非 `getCount()`，首版编译失败；
②首版把 mock 建好却**没交给 tools** 就去 verify，Mockito 报
「Wanted but not invoked … Actually, there were zero interactions with this mock」——
读 surefire 报告确认后才改，**没有把探针报错当成产品缺陷**。

### 文档方向（SDK README，doc round 230）：零缺陷

查 `cortex.mem.default-experience-count` 在中英双语 README 的描述：
`README.md:194` / `README-zh-CN.md:201` 分别是「Max experiences per retrieval」/
「每次检索的最大经验数」——**中性且正确，未复述 P2-92 的错误断言**，
故文档侧无需改动。这正是第 316 轮立下的流程规则（「落笔前先查该模块自己的文档」）的又一次生效。

### 变更检测与完整验收

本轮改了代码（注释）+ 指纹变化 → **必须跑完整门控**。

- **新鲜度闸门（豁免路径，已核实）**：`git log 6e5890d..HEAD -- backend/` **输出为空** →
  后端源码自 37777 的 jar 构建以来零提交 → 该二进制必然含最新后端代码；
  且被改模块 `cortex-mem-spring-integration` 非后端依赖（`backend/pom.xml` 零引用）。
- `bash scripts/regression-test.sh --skip-build` → **45 通过 / 0 失败 / 1 跳过 / 46 总计**。
- `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh` → **25 通过 / 0 失败 / 0 跳过**。

两者均与上一基线逐项相同 → **基线推进**至 `5d960e9b…` / 2218，`accepted_commit` = `be3f74d`。

### 顺带：findings 文件压缩 + 一处口径精化

写入 P2-92 后 findings 达 **1528 行 / 148352 字节**，行数越线（双阈值独立）→ 迁出
**P2-64～P2-68** 五条（全 ✅，第 273/274/275/277/278 轮结案）至
[`2026-10-07_backend-review-evidence-43.md`](../archive/2026-10-07_backend-review-evidence-43.md)，
压至 **1480 行 / 143456 字节**。归档头顺带记下本批的共同主题：**注释/Javadoc 的失实陈述**，
以及 P2-68 那条值得单独记住的经验——同一份 Javadoc 的人类可读部分与 `@Operation`
机器可读部分**互相矛盾**时，**后者才是真的**，扫描器必须两处都看。

压缩脚本沿用已验证契约：单遍顺序重建、边界断言覆盖 `- **` / `### ` / `## ` 三型行首、
用删除前快照做**双向**校验。压缩后又跑了一次**独立复验**，其指针改为**按位置推导**
（不再硬编码常量，避免用同一份常量自证），因此抓到两个真问题并已修：
①我最初把指针截在原文换行处，P2-66 留下**未闭合的「」**，四条指针读起来断在半句；
②P2-68 的指针结尾是 `）` 而非另外四条的 `）。`。

**独立复验自己还犯了一个记账错误**：`introduced` 已包含归档头，我又减了一遍头与分隔行，
计数变负后反被当成「多余行」加回来，误报 24 行。**归档文件本身没问题**——
那些行本就是脚本有意生成的。是**我的验证器**有 bug，归档正确。这一点记下来：
负计数出现在「已引入行」集合里时会**反向加回**，不是无害的中间值。

**顺带把一条待决项的口径说准**：findings 里**缺 `- **Status**:` 前缀**的其实是 **13** 条，
但其中 **P2-50 / P2-52 / P2-53 / P2-54** 写了 `- 已解决条目：正文 […]` 行（已解决、只是格式不同），
**真正无任何状态交代的仍是 9 条**（P1-1、P2-8、P2-25、P2-26、P2-28、P2-29、P2-32、P2-34、P2-59）。
故已记录的待决项表述准确，未改。

### 下一轮

代码方向：**Go SDK**；文档方向：**设计文档**（doc round 231）。

