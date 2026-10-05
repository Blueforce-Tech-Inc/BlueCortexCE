> **来源**: `docs/drafts/backend-review-findings.md` 中 **P2-21** 的 **Problem 段中的实测证据正文**，
>   逐字迁出，源文件留一行指针。
> **依据**: 承第 241、244、247、253、254 轮确立的规则——**实测记录是可复现的证据**
>   （写明端口、超时与真实响应字段），⏸ 规则保护的是**决策推理**。该条的
>   `- **Status**`（为什么这么定）与压缩后的 `- **Problem**`（问题是什么）**一行未动**。
> **本文件创建后不得修改**。

---

<!-- P2-21 -->
  活体实测（真实 `CortexMemClientImpl`，指向死端口 39999，超时 500ms）：

  ```
  status  = DOWN
  details = {service=Cortex CE Memory Backend, reason=Health check returned false}
  hasErrorKey = false
  ```

  指向真实后端时 `status=UP`。即运维看到后端挂掉只能读到「Health check returned false」——
  **连接被拒 / 超时 / DNS 失败这些真正的原因全部丢失**，因为在客户端被 `log.debug` 吞掉
  （默认不输出）；「后端不可达」与「后报 degraded」两种不同情况给出**完全相同**的文案。
- **测试反而钉死了这个假象**：`CortexMemHealthIndicatorTest.health_whenClientThrows_returnsDown`
  用 **mock** 让 client 抛出并断言 `containsKey("error")`——该状态**真实 client 永远无法产生**，
  故此用例**恒真却毫无保护作用**：让人以为异常路径已覆盖，而生产中恰恰走不到。
  与第 197 轮「夹具传了后端从不下发的值」同类：测试覆盖的是**虚构状态**。
- **核实无误的部分**：`healthCheck()` 判定 `"ok"` 的大小写是对的（后端
  `HealthController.java:62` 返回 `dbReady ? "ok" : "degraded"`，**小写**；
  活体 `GET /api/health` 亦为 `{"status":"ok"}`），null body / 非 `ok` / 异常
  三种情况均正确返回 `false`，UP-DOWN 三分支本身正确——
  **缺陷只在「原因丢失」与「测试虚构」，不在判定逻辑。**
