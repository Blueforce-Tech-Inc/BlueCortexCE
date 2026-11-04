# 小批 E：bundle parity 进 CI + 查看器的鉴权与停服行为

- 日期：2026-11-04
- 仓库：`Blueforce-Tech-Inc/BlueCortexCE`
- 相关提交：`710a6bc`（parity 进 Quality Gates、token/停服浏览器场景）、`7e42a6f`（固定 parity 工具链）

## 1. 远端 GitHub Actions

| 运行 | 提交 | 结论 |
|---|---|---|
| [41803938155](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41803938155) | `710a6bc` | **失败**：`WebUI typecheck` job 里新增的 parity 步骤报 `DRIFT: viewer-bundle.js`。`built 395732 bytes` vs `shipped 394696 bytes`，源码未变 |
| [41805175336](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41805175336) | `7e42a6f` | **成功**：四个 job 全绿——Backend JDK 21/25、WebUI typecheck（含 parity）、Browser E2E |

### 漂移根因

子模块 `webui` 没有锁文件（上游刻意 gitignore 了 `package-lock.json`），
`npm install` 在不同时间会解析到不同补丁版本：本机 `react 19.3.0`，CI 拉到
`react 19.3.1`。同一份 `webui/src` 因此产出不同的压缩字节，逐字节比对必然红。

修法：新增 `scripts/viewer-build/`（`package.json` + `package-lock.json`，
固定 react/react-dom 19.3.0、esbuild 0.28.2、ansi-to-html、dompurify），
parity 构建改用这份固定依赖，CI 先 `npm ci` 再比对。子模块仍保持只读。

## 2. parity 的失败路径验证（不污染仓库）

| 触发方式 | 结果 |
|---|---|
| 临时静态目录里翻转 `viewer-bundle.js` 一个字节（`VIEWER_STATIC_DIR`） | 退出 1，打印 built/shipped 的 sha256、字节数与重建命令 |
| 临时 webui 目录缺依赖（`VIEWER_WEBUI_DIR`） | 退出 2，`SKIP: the pinned viewer toolchain is not installed; run (cd scripts/viewer-build && npm ci)` |
| `REQUIRE_BROWSER_E2E=1 verify-local.sh` + 上面两种 | 第 9 步分别以 `STEP FAILED: viewer bundle drift` 与 `STEP FAILED: viewer bundle parity` 退出 |
| `PLAYWRIGHT_BROWSERS_PATH` 指向空目录 + `REQUIRE_BROWSER_E2E=1` | 第 9 步以浏览器缺失失败，不冒充通过 |

`check-viewer-bundle.sh` 现在支持 `VIEWER_STATIC_DIR` / `VIEWER_WEBUI_DIR` /
`VIEWER_DEPS_DIR`，失败路径验证全程使用临时副本。

## 3. 查看器在鉴权与故障下的真实行为

三条场景都进了 `scripts/webui-browser-e2e.sh`，常驻执行（第 9 步现在共 20 个
`PASS`）。

### A. 后端要求 token，浏览器不带 token

- 读取接口（`/api/observations`、`/api/settings`、`/stream`）**按设计保持开放**：
  EventSource 无法附加自定义头，锁死读接口会让查看器整体不可用。
- 受保护接口的行为：`GET /api/logs` 返回 `401 {"error":{"code":"unauthorized",
  "message":"Missing bearer token"}}`，不是 `200 {logs: []}`；日志抽屉显示
  `⚠ Failed to fetch logs:`；设置保存显示 `✗ Error: Missing bearer token`。
- 页面正常渲染、无 JS 异常、无白屏。
- **已知不足**：Tomcat 的 401 状态行是 `HTTP/1.1 401 `（无 reason phrase），
  所以日志抽屉里冒号后为空。Servlet 6.0 已删除 `setStatus(int, String)`，
  后端无法补 reason phrase；修复需要改子模块查看器，按约定未动。

### B. 页面打开时后端进程被直接停掉

- 真实 `SIGTERM` 杀掉进程后，滚动触发分页请求失败，信息流显示带 Retry 的
  `role="alert"`（文案为浏览器给出的 `Failed to fetch`），没有残留 spinner，
  页面仍完整渲染。
- 冷启动（后端已经停了再打开页面）属于浏览器自身的网络错误页，查看器无法接管。

### C. 后端重启后的恢复

- 重启后点 Retry，下一页正常返回（50 → 62 张卡片），错误条消失；
  `[SSE] Connected` 再次出现，说明 `/stream` 自动重连。
- 刷新页面同样可恢复。

## 4. 本地门禁

```
env -u CI -u GITHUB_ACTIONS REQUIRE_BROWSER_E2E=1 ./scripts/verify-local.sh
```

- 537 测试（L1 513 / L2 17 / L3 6 / L4 1），0 失败、0 跳过
- 文档自检 + 中英文 81 端点对照、proxy E2E 0 跳过
- 第 9 步：bundle parity PASS + 浏览器 20 项 PASS

## 5. 未覆盖

- 日志抽屉的 401 文案无法给出原因（见 A 的已知不足）。
- 拖拽排序、设置项的逐项编辑仍未在浏览器中验证。
- 后端冷启动不可用时的页面行为由浏览器决定，无法在应用层断言。
