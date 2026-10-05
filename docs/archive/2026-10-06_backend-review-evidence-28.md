# Backend Review Evidence 28 — P2-58 迁移块（第 277 轮）

> **归档规则**：工作文件保留 **Problem** 的问题陈述与 **Status**；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 1：P2-58 原始条目（逐字）

### P2-58: 四家 SDK 的响应 DTO **同缺**活体观测的 7 个字段——其中 3 个正是 V17 / V18 专门加的，而 Go 的 DTO 在 V17/V18 之后**还被改过**

- **Scope**: 逐字迁入 [`2026-10-04_backend-review-evidence-14.md`](../archive/2026-10-04_backend-review-evidence-14.md)（第 263 轮）。
- **Problem**: 取活体 `GET /api/observations?limit=1` 的一条真实观测（**34 个字段**），
  与四家响应 DTO 声明的字段名逐一比对，**四家同缺同样这 7 个**：

  | 字段 | 来自迁移 | 性质 |
  |------|----------|------|
  | `platform_source` | **V18** `V18__add_platform_source.sql` | **V18 专门新增**（平台来源归属） |
  | `generated_by_model` | **V17** `V17__observation_feedback.sql` | V17 反馈机制 |
  | `relevance_count` | **V17** 同上 | V17 反馈机制 |
  | `content_hash` | V8 | 内部去重列 |
  | `step_number` | V12 | 步骤效率 |
  | `discovery_tokens` | V1 | 统计列 |
  | `embedding_model_id` | V2 | 内部向量元数据 |

  Go 与 JavaScript 的 `encoding/json` / Jackson **默认忽略未知字段**，
  所以这些字段**被服务端发过来、被 SDK 静默丢弃**——不报错、不告警，
  调用方只能看到「SDK 里没这个字段」。
- **不修的理由**: ①**跨四家**，不属于任何一个方向的轮次；
  ②这 7 个里**性质不同**——`platform_source` 与 V17 两项是**面向使用方的能力**
  （V18 的存在意义就是让调用方知道一条记忆来自哪个平台），
  而 `content_hash` / `embedding_model_id` 很可能与三个向量列一样属**内部列、本就不该暴露**；
  ③**该暴露哪一部分无法由证据确定**。按既定规则**记录不单方面实施**。
- **Status**: ⏸ 记录不修（跨家 + 暴露范围待定；证据与字段来源已逐条落到迁移文件）。

