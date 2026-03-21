# USER.md - About Your Human

_Learn about the person you're helping. Update this as you go._

- **Name:**
- **What to call them:**
- **Pronouns:** _(optional)_
- **Timezone:**
- **Notes:**

## Context

### Demo 构建注意事项 (重要!)

**Demo 必须使用 `-Plocal` profile 构建**：
```bash
# ✅ 正确 - 使用本地 SDK
cd examples/cortex-mem-demo && mvn clean compile -Plocal

# ❌ 错误 - 会拉取 JitPack 旧版本
cd examples/cortex-mem-demo && mvn clean compile
```

**先安装 SDK 到本地仓库**：
```bash
cd cortex-mem-spring-integration && mvn clean install -DskipTests
```

---

构建注意事项：

```bash
# cd backend
# 构建时，可以先清理，避免因为缓存问题没有及时发现编译错误
mvn clean compile package -DskipTests
```

如果当前没有测试可以覆盖修改的内容，那么就应该先改进测试。应该优先使用**端到端**的测试脚本来进行测试。

**部分**测试/回归测试脚本见目录：
- `scripts/regression-test.sh`
- `scripts/thin-proxy-test.sh`
- `scripts/webui-integration-test.sh`
- `scripts/mcp-e2e-test.sh`

你可以使用 `cat backend/.env` 命令来查看环境变量（包含 key）以及如何启动/重启服务的信息。
注意，请仔细阅读 .env 中的注释（说明）：你**仅应该**杀死服务端进程，**不**要误杀连接服务端的客户端进程。 

---

开发过程中的代码注释 / 公开文档应该优先使用英文编写，而非中文。

每次取得关键的开发进展后，应该做回归测试进行确认。然后可以总结和 `git commit`。

因为你可能需要多次操作才能完成，所以你需要一个稳妥的方式避免在多次操作的过程中失忆（丢失上下文）。

你可以考虑在 `docs/drafts` 目录中创建一个文件用来跟踪任务的执行进度。每次取得关键性的进展，先更新进度文件，然后再执行下一步。

---

完成某个阶段性任务后**不要**停下来问我。你应该做到“作为一个旁路记忆系统”，已知的规划任务全部完成、**没有可改进的地方了为止**。

为此，你应该适当修改各个文档，包括但是不限于 HEARTBEAT.md 文档，保证项目24小时不间断推进。

---

The more you know, the better you can help. But remember — you're learning about a person, not building a dossier. Respect the difference.
