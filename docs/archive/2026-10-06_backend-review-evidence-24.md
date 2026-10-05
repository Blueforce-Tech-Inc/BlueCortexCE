# Backend Review Evidence 24 — P2-27 迁移块（第 275 轮）

> **归档规则**：工作文件 `docs/drafts/backend-review-findings.md` 保留各条目的
> **Problem**（问题是什么）与 **Status**（为什么这么定）；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 1：P2-27 原始条目（逐字，第 246 轮建立）

### P2-27: Python SDK 无法清空 `extractedData` —— 与 Go 并列最弱，而它的注释把这一点说成了「对齐 Go」

- **Scope / Evidence**: 已逐字迁入 [`2026-10-04_backend-review-scope-evidence-8.md`](../archive/2026-10-04_backend-review-scope-evidence-8.md)（第 254 轮）。
- **Problem**: 后端 `PATCH` 两种清空写法都能落库（实测 `null` → NULL、`{}` → `{}`），
  而 Python **两种都发不出**：`None` 被 `if val is not None` 跳过、`{}` 被上面那句
  `continue` 跳过；探针确认二者的 `to_wire()` **都是 `{}`**、`is_empty()` **都是 True**，
  故**一条已有 extractedData 的观测无法通过 Python SDK 清空它**。
  活体佐证该字段真实在用：38,200 行中非空 **20,780**、NULL **17,420**、**`{}` 为 0**——
  后端自身从不写 `{}`，走这条路会造出库中从未出现过的状态。
- **四家能力阶梯（清空 extractedData）**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
- **更正（2026-10-04 第 246 轮，本表 JS 行的判定依据当时不成立）**: 逐字迁入 [`2026-10-04_backend-review-evidence-11.md`](../archive/2026-10-04_backend-review-evidence-11.md)（第 258 轮）。
- **该缺陷为何能存活**: `js-sdk/cortex-mem-js/tsconfig.json` 的 `exclude` 含
  `"**/*.test.ts"`，而 `npm run lint` 就是 `tsc --noEmit`——**测试文件根本不参与类型检查**，
  于是类型层与断言层之间的裂缝没有任何自动关卡。已独立立为 **P2-42**。
- **已修（注释，非行为）**: `ObservationUpdate` 类 docstring 原写
  「Only non-None fields are sent to the backend, **matching Go's
  pointer-field-with-omitempty pattern**」。这句有两处不准：Python 用的是
  `Optional[T]` 而非指针；且**对切片字段两家行为恰恰相反**。已改写为逐条说明四个字段
  上两家的实际异同。`is_empty()` 与 `to_wire()` 里那两处 `continue` 的注释也改为
  如实写明「读取时 `{}` 与 `None` 等价，但**写入时 `{}` 是本 SDK 唯一能发的清空形态**，
  所以这里跳过是真实的能力缺口」，并去掉原来那句会误导的
  「an empty dict is semantically equivalent to None (backend stores nothing in JSONB)」。
  **行为一字未改**：428 测试全过，探针输出与改动前逐字相同。
- **Status**: ⏸ **行为记录不修** —— 改行为只有两条路：让 `{}` 发上 wire
  （**改变现有调用方的可观测行为**，`extracted_data={}` 从「不变」变成「落 `{}`」），
  或新增显式清空入口（**新增公开 API**）。按既定纪律留待项目决策。
  **注释层已先行更正**。
- **复核记录**（原文见 [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md)，逐轮全文另见 `patrol-rotation.md`）

