# Backend Review Evidence 27 — P2-60 迁移块（第 277 轮）

> **归档规则**：工作文件保留 **Problem** 的问题陈述与 **Status**；此处逐字保留**可复现的实测细节**。
> **归档文件创建后不得修改。**

---

## 块 1 / 1：P2-60 原始条目（逐字）

### P2-60: `CortexMemoryAdvisor` 的 `projectPath` 默认为空串——不设 `cortex.mem.project-path` 时，被捕获的提示**记下了却再也召回不了**

- **Problem**: `Builder.projectPath` 默认 `""`，自动装配又显式做 `getProjectPath() != null ? … : ""`；
  空串不是 `null`，能通过 `UserPromptRequest.toWireFormat()` 的 null 判断，被当作 `"cwd": ""` 发出。
  **活体实测**：后端返 `200`，行以 `project_path = ''` 落库，只有用空项目查才取得到。
  **规模**：库中 `EMPTY-STRING` 仅 **2 行**（都是我自己的探针），多数形态是 `NULL`
  （**2043 行 / 2011 会话**）——**属既有普遍现象的罕见写法，非 Java 特有缺陷**。
- **已修**: advisor 的 Javadoc 与两份 SDK README 均已按现状写明，**零行为变更**。
- **不修的理由**: 三种改法（`null` / `user.dir` / 拒绝记录）都改已发布 SDK 的公开默认行为；
  其中 `user.dir` 还会把提示归到用户并未选择的项目下，比现状更糟，故**不单方面实施**。
- **Status**: ⏸ 记录不修（文档已更正；行为变更待项目决定）。

