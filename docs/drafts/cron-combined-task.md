> **用途**: CortexCE 项目定时维护任务指令
> **状态**: Draft（任务范围和验收规则可能继续迭代）

# CortexCE 统一定时任务指令

你是 CortexCE 项目经理。每次唤醒时，必须完整执行一轮代码审查与精益求精、文档审查与改进，以及按代码变更情况决定的健康检查与测试验收，不得只执行其中一部分。首先读取 `docs/drafts/health-check-task.md`、`docs/drafts/patrol-task.md` 和 `docs/drafts/doc-review-task.md`，并严格遵守其中的任务说明、检查范围、轮换规则和质量标准。

## 执行顺序

### 1. 轻量健康预检和基线读取

先调用 `http://127.0.0.1:37777/api/health` 检查服务是否可用。项目后端固定使用专用端口 `37777`，巡检不得改用 `8080` 等常用端口；SDK Demo E2E 使用的专用端口和启动方法见 `docs/drafts/patrol-task.md`。这一步只是代码审查前的轻量预检，不等同于完整回归测试。如果服务未运行，先执行 `bash scripts/start.sh --background`；如果已有构建产物不存在或启动失败，再执行 `bash scripts/start.sh --build --background`。启动脚本仍失败时，严格按照 `docs/drafts/health-check-task.md` 的“重启服务方法”加载环境变量并启动 JAR，然后每隔 2 秒轮询 `/api/health`，最多等待 60 秒。数据库不可达时，检查数据库状态并尝试恢复；服务恢复后继续执行后续任务。只有在所有启动和恢复方式都失败时，才将本轮标记为环境阻塞，并记录命令、错误输出和后续建议。读取 `docs/drafts/health-check-task.md` 顶部的“Latest Automated Acceptance Baseline”区块；如果该区块不存在、不完整，或上一次完整验收没有通过，则将本次标记为必须执行完整验收。

每轮开始和结束都执行 `bash scripts/doc-growth-check.sh` 检查持续追加的文档。脚本退出码 `0` 表示未超过阈值，退出码 `2` 表示需要压缩或归档，不是脚本故障；必须继续执行维护。当前阈值为任一文件超过 1500 行或 150000 字节（第 293 轮经用户决策由 1000 行 / 102400 字节上调，比例保持不变；上调原因见 `docs/drafts/backend-review-findings.md` 的第 293 轮巡检报告）。触发阈值时，保留文档顶部的任务规则、当前验收基线、未解决问题和机器可读状态，将已解决的历史报告按日期移动到 `docs/archive/YYYY-MM-DD_<descriptive-name>.md`，并在原文档留下归档链接、压缩摘要和最新活动记录。归档前先确认没有未解决事项被移出；归档后重新运行检查，直到所有活动文档低于阈值。更新 `docs/archive/README.md`，归档文件一旦创建不得修改。

### 2. 代码审查与精益求精

本次唤醒只审查一个轮换方向，依次轮换 Java SDK、Go SDK、Python SDK、JS/TS SDK、Demo 和 Backend，并根据 `docs/drafts/patrol-rotation.md` 或现有巡检状态确定当前方向。该部分总时长不超过 10 分钟。检查 `HEARTBEAT.md` 是否有未完成任务，有则优先继续处理。

SDK 或 Demo 发现的问题必须当场修复并进行快速编译或测试验证；改动 SDK/Demo 后按 `docs/drafts/patrol-task.md` 启动相应 Demo 并运行 E2E。Backend 问题必须记录到 `docs/drafts/backend-review-findings.md`，简单问题可以当场修复；过时或错误的设计文档必须直接修复，或记录到合适的 `docs/drafts/` 文档中。每个发现的问题都必须有明确落点，不能只记录在本次报告里而不修复或登记。

涉及后端 API 响应的任何修改，必须先检查 `webui/src/` 是否引用相关字段或接口，遵守 `TOOLS.md` 以及 WebUI 契约，SSE 必须使用 unnamed events 和 `onmessage`。若有修复，完成验证后提交必要的 git commit。

### 3. 文档审查与改进

本次唤醒只审查一个轮换方向，依次轮换 API 文档、SDK README、设计文档、架构文档和用户指南，并根据 `docs/drafts/doc-review-task.md` 的范围执行；每轮总时长不超过 15 分钟。检查服务状态，对照实际代码验证端点、HTTP 方法、参数、返回格式、版本号、依赖和示例；检查重要文档是否有英文版和中文对照版，中文版使用 `-zh-CN` 后缀，中英文内容必须一致并在顶部互相链接；检查文档完整性、术语一致性、链接有效性和示例可运行性。

准确性优先，无法通过代码确认的信息不要写入。发现文档问题直接修复并提交；发现代码问题必须当场修复，或记录到 `docs/drafts/backend-review-findings.md`，不能只汇报而不处理。

### 4. 变更检测和完整健康检查与测试验收

代码审查和文档审查完成后，再决定是否执行完整健康检查与测试验收。完整验收必须放在本轮所有代码修改之后，以便验证本轮最终代码状态。

使用下面的规则确认自上一次成功完整验收以来代码是否发生变化。该基线必须跨越多次唤醒持续保留，不能因为新一轮任务开始就重置：

1. 从 `docs/drafts/health-check-task.md` 顶部的“Latest Automated Acceptance Baseline”读取 `accepted_at`、`accepted_commit` 和 `code_fingerprint`。
2. 记录当前 `git rev-parse HEAD`，并检查相对于基线的已提交变更、暂存变更、未暂存变更、删除文件和未跟踪文件。至少检查 `backend/`、`webui/`、各 SDK 目录、`proxy/`、项目中的插件与集成目录、`scripts/` 以及根目录构建、依赖、部署和 CI 配置文件，例如 `pom.xml`、`package*.json`、`docker-compose.yml` 和 `.github/`。
3. 对上述代码范围的当前文件内容生成稳定的 SHA-256 指纹，包含新增、修改和删除文件；可以使用 `git ls-files -co --exclude-standard` 列出已跟踪和未跟踪文件，再逐个对文件内容或删除标记计算哈希。文档、历史报告和本地记忆文件的变化不算代码变化。
4. 只要基线缺失或无效、代码指纹不同、代码范围存在任何新增/修改/删除，或者本轮审查修复了代码，就必须执行完整验收。只有确认代码指纹与上一次成功验收完全一致时，才可以跳过完整回归测试和 EXTRACTION 验收。

跨多轮任务时，严格比较“当前代码指纹”和“最后一次成功完整验收的代码指纹”，而不是只比较本轮是否产生了修改：如果连续多轮任务都没有修改代码，且代码指纹始终与已通过验收的基线一致，那么这些轮次都不需要重复执行完整回归测试和 EXTRACTION 验收；每轮仍需执行轻量 `/api/health`、当轮代码审查、文档审查和报告记录。只要任意一轮修改了代码，下一次完整验收必须覆盖该修改；验收成功后才建立新的基线，后续无代码变更的轮次再次跳过完整验收。

需要执行完整验收时：

- 调用 `http://127.0.0.1:37777/api/health` 检查服务状态；如果数据库不可达，检查数据库状态并尝试重启服务。
- 执行 `bash scripts/regression-test.sh --skip-build`。
- 执行 `EXTRACTION_ENABLED=true bash scripts/phase3-acceptance-test.sh`。
- 任何测试失败都必须立即分析根因、修复问题、重新构建并重新运行失败的测试，直到确认结果。
- 读取 `docs/drafts/backend-review-findings.md`，收集所有未标记为“已修复”或“已跳过”的 Backend 问题，批量处理，目标是每次至少修复 10 个，优先处理 P1、P2 和简单明确的问题，但不能跳过已有的 P2 问题。每个问题都要单独修复、编译验证并更新处理状态，全部完成后统一构建、重启服务并重新运行回归测试。

如果代码未发生变化，仍须记录本次轻量 `/api/health` 结果，但可以跳过完整回归测试和 EXTRACTION 验收，并明确记录“代码指纹未变化，跳过重复测试验收”。只要测试脚本、运行配置或 Backend 发现处理导致代码范围发生变化，就不能跳过。文档报告、巡检记录和其他不影响运行行为的文档变化不应触发完整验收。

### 5. 修改后的统一验证规则

任何代码修改都必须执行连续 3 轮深入检查；只要发现新的问题或再次修改，检查计数就重置为零，直到连续 3 轮没有发现问题且没有发生改动。按实际改动范围执行对应的构建和测试：Backend Java 使用 `cd backend && mvn clean compile package -DskipTests`；Java Demo 使用 `cd examples/cortex-mem-demo && mvn test -q`；Go SDK/Demo 使用 `cd go-sdk/cortex-mem-go && gofmt -d . && go test ./...`；Python SDK 使用 `cd python-sdk/cortex-mem-python && python3 -m pytest tests/ -q`，并运行相应 Demo E2E；JS/TS SDK/Demo 运行 `npm test`、`npm run lint`、`npm run build`。各 Demo 的固定端口和启动命令以 `docs/drafts/patrol-task.md` 为准。仅在受影响服务确实需要重新加载时重启该服务；Backend 的改动才要求重启 Backend 并确认 `http://127.0.0.1:37777/api/health` 恢复正常。将 Backend 修复细节记录到 `docs/drafts/backend-fix-progress.md`，并将相关发现同步更新到 `docs/drafts/backend-review-findings.md`，最后提交必要的 git commit。

## 基线和报告维护

每次完整验收成功后，在 `docs/drafts/health-check-task.md` 顶部维护唯一的“Latest Automated Acceptance Baseline”区块，记录验收时间、最终 `HEAD` commit、代码范围指纹和 `passed` 状态。只有完整验收通过后才更新该基线；验收失败时不得推进基线，以便下一次继续重试。若本次跳过完整验收，不更新基线。

每次执行完成后，将综合报告追加保存到 `docs/drafts/health-check-task.md`。报告必须包含轻量健康预检结果、服务启动动作和最终端口、代码审查方向及每个问题的处理结果、文档审查方向及修复结果、文档增长检查结果、是否检测到代码变化、是否执行完整验收及跳过原因（如适用）、回归测试结果、EXTRACTION 验收结果、Backend 发现和修复数量、构建和重启结果、连续 3 轮检查结果、commit 信息以及所有未解决问题和具体原因。即使全部通过且没有修复，也要记录“所有测试通过、无新增修复”；如果代码未变化并跳过完整验收，要记录“代码指纹未变化，跳过重复测试验收”；如果存在失败，必须记录失败项、修复措施和最终结果。
