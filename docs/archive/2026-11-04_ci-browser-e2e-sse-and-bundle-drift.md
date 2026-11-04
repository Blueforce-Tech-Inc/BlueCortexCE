# 小批 D：远端 CI 实跑核对 + 浏览器 e2e 的 SSE 与构建产物漂移

- 日期：2026-11-04
- 仓库：`Blueforce-Tech-Inc/BlueCortexCE`
- 相关提交：`7bb0f3d`（浏览器 e2e 成为常驻关卡并新增 CI job）、`82c88d3`（SSE 实时用例 + bundle 漂移检查）

## 1. 远端 GitHub Actions 实跑

| 运行 | 提交 | 结论 |
|---|---|---|
| [41796724752](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41796724752) | `7bb0f3d` | **失败**：`browser-e2e` 在等待 `text=No more items to load` 时超时 20 s；backend 两个 JDK、API docs + proxy、WebUI typecheck 均通过 |
| [41798694874](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41798694874) | `82c88d3` | **成功**：四个 job 全绿（Backend JDK 25 1m51s、Backend JDK 21 2m13s、WebUI typecheck 2m7s、Browser E2E 1m27s） |
| [41800050092](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/actions/runs/41800050092) | `a3bfa08` | **成功**：收尾文档提交（本文件所记录的内容），四个 job 全绿（Browser E2E 1m37s） |

### 失败根因与修复

翻页断言依赖 `page.mouse.wheel(0, 6000)`，而滚轮事件作用于鼠标当前所在位置；
CI 上鼠标位置来自前一步点击，无法保证落在 `.feed` 内，于是页面根本没有继续加载。
本机（鼠标恰好停在列表上）不会复现，这正是「本地绿、CI 红」的差异来源。

改为直接操作滚动容器，并给足轮次与等待时间：

```js
for (let round = 0; round < 20; round += 1) {
  if (await page.locator('text=No more items to load').count() > 0) break;
  await page.evaluate(() => {
    const feed = document.querySelector('.feed');
    if (feed) feed.scrollTop = feed.scrollHeight;
    window.scrollTo(0, document.body.scrollHeight);
  });
  await page.waitForTimeout(500);
}
await page.waitForSelector('text=No more items to load', { timeout: 30000 });
```

`--with-deps` 安装、Chromium 缓存与 `psql` 客户端在两次运行中都正常，未出现
sudo 或下载问题，因此 browser job 保留在 `backend-tests.yml` 内，未拆分。

## 2. SSE 实时更新（`/stream` 的浏览器路径）

此前所有浏览器用例都靠 `page.reload()` 观察新记录，`/stream` 本身未被验证。
新增用例：页面已打开、SSE 已连上的情况下，通过 `POST /api/ingest/observation`
写入一条新 observation，断言 `SSE Connected` 控制台日志出现、新标题的卡片在
不刷新页面的前提下出现，并断言卡片数至少 +1。

```js
const sseConnected = page.waitForEvent('console', {
  predicate: message => message.text().includes('[SSE] Connected'),
  timeout: 30000,
});
await page.goto(`${base}/`, { waitUntil: 'domcontentloaded', timeout: 45000 });
await page.waitForSelector('.card-title', { timeout: 20000 });
await sseConnected;
// ... 写入后
await page.waitForSelector(`text=${liveTitle}`, { timeout: 20000 });
```

## 3. 构建产物漂移（可执行比对）

新增 `scripts/check-viewer-bundle.sh`，把 `webui/{scripts,src,package.json}` 复制到
临时目录、软链 `webui/node_modules` 后在临时副本里重跑 `scripts/build-viewer.js`，
再用 `cmp` 逐字节比对 `backend/src/main/resources/static` 中的
`viewer-bundle.js`、`index.html`、`assets/fonts/*` 与 `icon-thick-*.svg`。
退出码：0 一致、1 漂移（打印两侧 sha256 与字节数及重建步骤）、2 依赖缺失。

首次运行时发现真实漂移：`webui/plugin/ui/viewer-bundle.js` 仍是 classic JSX 构建
（`createElement`，397 179 B），而当前 `webui/scripts/build-viewer.js` 使用
`jsx: 'automatic'`，全新构建为 394 696 B。按检查器给出的重建路径，把全新构建的
产物部署到后端 static；`index.html`、字体与图标逐字节一致。随后检查器 PASS。

子模块未被修改：`webui/plugin/ui` 保持上游提交状态，检查器只在临时副本中构建。

## 4. 本地门禁

```
./scripts/verify-local.sh   # REQUIRE_BROWSER_E2E=1，前台执行
```

- 测试：537（L1 513 / L2 17 / L3 6 / L4 1），0 失败、0 跳过，四层全部执行
- 文档一致性：自检 23 例 + 中英文 81 个端点对照通过
- proxy E2E：执行且 0 跳过
- 第 9 步：bundle parity PASS + 浏览器 8 项 PASS（渲染与 SSE 连接、首页 50 条、
  `files_modified`、`hasMore` 翻页到 60 条、`{items,hasMore}` 信封、SSE 实时新增、
  刷新可见、设置开关往返）

## 5. 未覆盖

- 拖拽排序、设置项的逐项编辑（非开关）仍未在浏览器中验证。
- `check-viewer-bundle.sh` 只比对构建产物；`webui/plugin/ui` 与 `webui/src` 的漂移
  只被报告、不会被自动修复（按约定不改子模块）。
