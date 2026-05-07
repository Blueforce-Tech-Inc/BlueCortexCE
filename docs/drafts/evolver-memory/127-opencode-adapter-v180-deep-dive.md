# `127` opencode.js Adapter — v1.80.0 新平台适配器深度分析

**文件**: `docs/drafts/evolver-memory/127-opencode-adapter-v180-deep-dive.md`
**目标**: 分析 v1.80.0 新增的 opencode 平台适配器
**数据来源**: `src/adapters/opencode.js` (234L) + `test/adapters.opencode.test.js` (224L)
**版本**: v1.80.0 (ab9d68e)

---

## 1. 平台定位

opencode 是继 Claude Code / Codex / Cursor / Kiro 之后的第 5 个 evolver 适配平台，通过 opencode 插件系统 (`~/.opencode/plugins/`) 集成。与其他适配器类似，opencode 通过 stdin/stdout JSON 过滤脚本与 evolver 通信，无直接 Node 模块共享。

**opencode 插件系统特点**:
- opencode 在启动时同步加载 `.js` 文件
- 调用 default-exported async factory 函数，传入 context 对象
- 工厂返回包含事件处理器的对象

---

## 2. 事件映射

| opencode 事件 | evolver hook 脚本 | 超时 | 用途 |
|---------------|-------------------|------|------|
| `session.created` | `evolver-session-start.js` | 3000ms | 注入记忆 |
| `tool.execute.after` (write/edit only) | `evolver-signal-detect.js` | 2000ms | 检测信号 |
| `session.idle` | `evolver-session-end.js` | 8000ms | 记录结果 |

与 Claude Code / Cursor 等其他平台不同，opencode 使用 **`session.idle`** 而非 `session.end` 事件来触发 session-end 逻辑。

---

## 3. install / uninstall 实现

### install 流程

```
写入 ~/.opencode/plugins/evolver.js   (buildPluginSource)
复制 hook scripts → ~/.opencode/hooks/
追加 evolver section → AGENTS.md
```

**buildPluginSource** 动态生成插件源码，核心是 `runHook()` 函数：
```javascript
function runHook(scriptName, payload, timeoutMs) {
  const result = spawnSync('node', [path.join(HOOKS_DIR, scriptName)], {
    input: JSON.stringify(payload || {}),
    encoding: 'utf8',
    timeout: timeoutMs,
  });
  if (!result || !result.stdout) return {};
  try { return JSON.parse(result.stdout); } catch { return {}; }
}
```

使用 `spawnSync` 而非 `spawn` — 同步执行避免 opencode 事件循环阻塞。

**session.created 事件**:
```javascript
session_id: event.properties.info.id   // opencode session ID 从嵌套 properties 提取
```

**tool.execute.after 事件**:
```javascript
input.tool !== 'write' && input.tool !== 'edit' → 跳过
tool_input:  output.args         // 工具输入参数
tool_response: output.output || output.result  // 工具输出（兼容两种格式）
```

**AGENTS.md 注入**: 通过 `EVOLVER_MARKER = '<!-- evolver-evolution-memory -->'` 标识已注入的 section，支持幂等追加。

### uninstall 流程

1. 删除 `~/.opencode/plugins/evolver.js`（仅当 `isEvolverManagedPluginFile` 为 true）
2. 删除 `~/.opencode/hooks/` 下的 hook 脚本
3. 从 `AGENTS.md` 移除 evolver section（通过 `EVOLVER_MARKER` 定位）

---

## 4. 安全设计

**插件来源验证**:
```javascript
function isEvolverManagedPluginFile(filePath) {
  return raw.includes('_evolver_managed: true');
}
```
只有包含 `// _evolver_managed: true` 的文件才会被 uninstall 删除，防止误删用户自定义插件。

**原子写入**:
```javascript
fs.writeFileSync(tmp, source, 'utf8');
fs.renameSync(tmp, dest);  // rename 是原子操作
```

---

## 5. 与其他 Adapter 的对比

| 维度 | Claude Code | Cursor | Kiro | Opencode |
|------|-------------|--------|------|----------|
| 事件类型 | `session.end` | `session.end` | `session.end` | `session.idle` |
| 信号检测 | tool 输出分析 | tool 输出分析 | tool 输出分析 | tool.execute.after |
| 通信方式 | spawnSync | spawnSync | spawnSync | spawnSync |
| 插件管理 | hook 脚本复制 | hook 脚本复制 | hook 脚本复制 | 插件文件生成 |
| AGENTS.md | ✅ | ✅ | ✅ | ✅ |

---

## 6. CE 借鉴意义 (P3)

**P3 - 值得参考**:
- opencode 的 `session.idle` 触发 session-end 模式可以参考：如果 BlueCortexCE 支持"空闲触发"而非仅"显式结束"，可以实现更及时的记忆固化
- `spawnSync` 同步执行 + 超时控制模式对 CLI hook 通用
- `isEvolverManagedPluginFile` 的幂等安装/卸载模式

---

## 7. 测试覆盖

`test/adapters.opencode.test.js` (224L) 覆盖：
- install 成功写入 plugin 文件
- uninstall 仅删除 evolver 管理的文件
- `session.created` 提取 session_id
- `tool.execute.after` 仅处理 write/edit
- AGENTS.md marker 幂等追加
