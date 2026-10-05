> **来源**: `docs/drafts/backend-review-findings.md` 中 **P1-2** 的 **实测记录**段，逐字迁出，
>   源文件留一行指针。
> **依据**: 承第 241、244、247、253、254 轮确立的规则——**实测记录是可复现的证据**
>   （每条都写明端口、命令与真实响应）。该条的 `- **Problem**`（问题是什么）、
>   `- **Severity 说明**` 与 `- **Status**`（为什么这么定）**一行未动**。
> **本文件创建后不得修改**。

---

<!-- P1-2 -->
- **实测记录（第 269 轮，2026-10-05，demo 起在 37778）**：
  探针文件 `printf 'CANARY-R269-ARBITRARY-READ-PROBE\n' > /tmp/r269-canary.txt`（`chmod 600`），
  进程为本轮自己启动的 `java -jar target/cortex-mem-demo-1.0.0.jar`，**验证后已停止、探针已删除**。

  | # | 请求 | 实际响应 |
  |---|------|----------|
  | ① | `GET /demo/tool?path=/tmp/r269-canary.txt` | `200`，`result` 为 `CANARY-R269-ARBITRARY-READ-PROBE\n` —— **逐字回显** |
  | ② | `GET /demo/tool?path=/etc/passwd` | `200`，`result` 为该文件内容（`## / # User Database / …`） |
  | ③ | `GET /demo/tool?path=/tmp/../etc/hosts` | `200`，`result` 为 `/etc/hosts` 内容（`127.0.0.1 localhost` 等）—— **穿越有效** |
  | ④ | `GET /demo/session/tool?sessionId=…&projectPath=/tmp&path=/tmp/r269-canary.txt` | `200`，`result` 为探针内容 —— **第二个入口同样成立** |
  | ⑤ | `POST /demo/session/lifecycle?…&toolPath=/tmp/r269-canary.txt` | `200`，响应体含 `'tool_result': 'CANARY-R269-ARBITRARY-READ-PROBE\n'` —— **第三个入口同样成立** |

  **绑定实测**：

  ```
  $ lsof -nP -iTCP:37778 -sTCP:LISTEN
  java    50101 yangjiefeng   15u  IPv6 ... TCP *:37778 (LISTEN)      # demo：全网卡

  $ lsof -nP -iTCP:37777 -sTCP:LISTEN
  java    42092 yangjiefeng   28u  IPv6 ... TCP 127.0.0.1:37777 (LISTEN) # 后端：仅回环
  ```

  **影响面已核实而非假设**：另三家 demo（`go-sdk/cortex-mem-go/examples/`）
  `grep -niE 'readfile|os\.ReadFile|ioutil\.ReadFile|read_file|readFileSync'`
  **零命中** —— 该能力为 **Java demo 独有**，不是四家共同的设计。
  后端已有 `PathValidationUtil`（`backend/.../util/PathValidationUtil.java`，
  注释明写 `Normalize and resolve the path to prevent path traversal`），
  **demo 一处都没有引用**。demo 自带 `e2e/run-e2e.sh` 只读 `/tmp` 下的文件
  （`?path=/tmp/e2e-test-*.txt`、`toolPath=/tmp/demo-project-a/readme.txt`），
  **从无越界用例**。

  **一次探针自身出错、先识别再采信**：初次探测第二个入口时按字面猜成
  `GET /demo/tool/session`（把方法级 `@GetMapping("/tool")` 当成了类级路径），
  实际返回 **404**；查 `SessionLifecycleController` 的类级映射
  （`@RequestMapping("/demo/session")`）后改用 `GET /demo/session/tool` 才复测成功。
  **404 没有被当成「该入口不存在」写进结论**。

  **未清理的残留（如实说明）**：本轮三次探针在 `mem_observations` 中**未留下**
  `r269-probe%` 行（`SELECT … LIKE 'r269-probe%'` 返回 0，异步捕获未成），
  库中既有的 `demo-*` 测试数据来自**历轮**共用，**故未删除**——避免动到其他轮次的记录。
