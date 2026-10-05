# Backend Review Evidence 36 — 实测细节与已修明细迁出（第 291 轮）

> **归档规则**：⏸ 条目保留 **Problem** 的问题陈述；此处逐字保留**可复现的实测细节**与**已修条目的改动原文**。
> **归档文件创建后不得修改。**

> 本批为腾出字节：写完 P2-70 后 findings 双阈值的**字节项**越线 738 字节。
> 七块按其在工作文件中的**位置升序**编号；迁出区间为删除前快照口径。
> 另有一类**不属迁出**的压缩：P2-24 / P2-33 / P2-35 / P2-36 / P2-37 五条**逐字相同**的
> 「复核记录」行改写为紧凑形式，并**修正其中一句已被第 254 轮作废的断言**
> （原文称 Scope / Problem / Evidence / Status 全部保留在本文件，而 Scope / Evidence 已于第 254 轮迁出）。

---

## 块 1 / 7：P2-21 · 健康指示器活体实测（逐字）

> 迁出区间：工作文件第 253–256 行（删除前快照口径）。

```text
  活体实测确证：真实 client 指向死端口时 `status=DOWN`、
  `reason=Health check returned false`、`hasErrorKey=false`；指向真实后端则 `UP`。
  即**连接被拒 / 超时 / DNS 失败这些真正的原因全部丢失**（在 client 侧被 `log.debug`
  吞掉，默认不输出），「后端不可达」与「后端 degraded」给出**完全相同**的文案。
```

## 块 2 / 7：P2-21 · 判定逻辑核实为真（逐字）

> 迁出区间：工作文件第 260–262 行（删除前快照口径）。

```text
- **核实无误**：`"ok"` 的大小写判定正确（后端 `HealthController.java:62` 返回
  `dbReady ? "ok" : "degraded"`，**小写**）；null body / 非 `ok` / 异常三种情况均正确返回
  `false`，UP-DOWN 三分支本身正确——**缺陷只在「原因丢失」与「测试虚构」，不在判定逻辑。**
```

## 块 3 / 7：P2-27 · 活体行级统计（逐字）

> 迁出区间：工作文件第 339–341 行（删除前快照口径）。

```text
  活体佐证该字段真实在用、且后端自身从不写 `{}`：38,200 行中非空 20,780、NULL 17,420、**`{}` 为 0**
  ——走这条路会造出库中从未出现过的状态。逐行数据见
  [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮，含四家能力阶梯表与第 246 轮更正）。
```

## 块 4 / 7：P2-27 · 注释层已修明细（逐字）

> 迁出区间：工作文件第 344–349 行（删除前快照口径）。

```text
- **已修（注释，非行为）**: `ObservationUpdate` 类 docstring 原写「Only non-None fields are sent to the backend,
  **matching Go's pointer-field-with-omitempty pattern**」——Python 用的是 `Optional[T]` 而非指针，
  且**对切片字段两家行为恰恰相反**。已改写为逐条说明四个字段上两家的实际异同；
  `is_empty()` / `to_wire()` 里两处 `continue` 的注释也改为如实写明
  「读取时 `{}` 与 `None` 等价，但**写入时 `{}` 是本 SDK 唯一能发的清空形态**」，
  并去掉原来那句会误导的「an empty dict is semantically equivalent to None」。**行为一字未改**（428 测试全过）。
```

## 块 5 / 7：P2-59 · `DemoErrors` Javadoc 已修明细（逐字）

> 迁出区间：工作文件第 812–815 行（删除前快照口径）。

```text
- **已修**: `DemoErrors` 的类 Javadoc 原先写着「**Controllers** use `statusOf` / `messageOf`」，
  在只有 2/12 控制器这么做时读起来像全覆盖声明。已按现状改写为精确表述
  （12 个控制器 / 40 个 catch 块 / 3 个走 helper / 10 个控制器没有），
  并点名 `PATCH /demo/session/user` 作为反例。**零行为变更**，`mvn -o test` 通过。
```

## 块 6 / 7：P2-61 · 活体三端点响应与两处文档同源失实（逐字）

> 迁出区间：工作文件第 852–856 行（删除前快照口径）。

```text
  配置文件**的场景。后果是 `prd` 部署下三个**会实际消耗 LLM / 嵌入配额**的调试端点照常开放：
  活体（`--spring.profiles.active=dev`）`/api/test/llm` → 200、`/api/test/all` → 200、
  `/api/test/embedding` → 500（**已失效的嵌入密钥**，即 P2-28 记录的那一条，非本轮新缺陷）。
  文档侧同源两处：`docs/ARCHITECTURE.md:880` 的 API 分层表列出 `/api/test/*` 时**未提任何
  profile 限定**；P2-28 第 401 行「`@Profile("!prod")` 门控是**正确的**」据本条证据需要修正。
```

> **两处行号引用的有效性说明（第 291 轮核对）**：
> ① 上文 `docs/ARCHITECTURE.md:880` —— `docs/ARCHITECTURE.md` 在**同一批次**中已被第 291 轮的
> P2-70 更正编辑（Network Security 表），按既定规则**行号引用不得与被引用文件同批提交**，
> 故工作文件中的指针已改用章节名定位。
> ② 上文 `P2-28 第 401 行` —— 该行号**当时即已失效**：删除前快照里 P2-28 条目占第 353–362 行，
> 「`@Profile("!prod")` 门控是**正确的**」实际位于 P2-28 的 **Problem** 段。归档保留原文不改，
> 工作文件中的指针已改用条目名 + 段名定位。

## 块 7 / 7：P2-64 · 第 276 轮受控实验与 21 个 DTO 全量复查（逐字）

> 迁出区间：工作文件第 906–911 行（删除前快照口径）。

```text
  > **第 276 轮更正本条的一处过宽表述**：初稿写「每个访问器都按属性序列化」，**这句是错的**。
  > 受控实验（一个 record 同时带 `isEmpty()` / `getSubtitle()` / `content()` / `total()` / `hasThing()`）
  > 实测输出 `{"title":"T","narrative":"N","empty":false,"subtitle":"G"}`——**只有符合 JavaBeans 约定的
  > `isXxx()` 与 `getXxx()` 泄漏**，其余三个普通无参方法全部不可见。该轮据此**全量复查** SDK 的 21 个
  > DTO record：带 `isXxx()` 的**只有已修的那两个**，**无任何 record 带 `getXxx()`**。
  > **故 P2-64 的修复完整，无遗漏项。**
```
