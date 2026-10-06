# Backend Review Evidence 40 — 已解决条目的 Status 段（第 323 轮迁出）

> **归档规则**：⏸ 条目保留 **Problem** 的问题陈述；此处逐字保留**已解决条目的 Status 全文**。
> **归档文件创建后不得修改。**

> 本批为 `docs/drafts/backend-review-findings.md` 越过 150000 字节阈值（**150817 字节**）而迁出 **3 条**已无条件解决条目的 Status 段。
> **Problem 段全部留在工作文件**，只把 Status 换成一行指针。

## 块 1 / 3：P2-80 的 Status 段（第 323 轮逐字迁出）

### P2-80: CORS 的 origin 列表**不 trim**——按文档教的「逗号分隔」写，**只有第一个域名生效**

- **Status**: ✅ **已修（第 313 轮）** —— 新增 `private static String[] parseOrigins(String)`：
  `split(",")` → `trim()` → **丢弃空元素**。
  **按既定规则归类为「已损坏行为」而单方面实施**：它不是安全放宽——
  trim 只让**运维自己写进列表里**的那个域名真正生效，**不可能放行列表之外的任何 origin**
  （`d.example` 与 `evil.example` 实测仍 403）；原行为则是**静默忽略配置的一部分**。
  **修后实测**（重启 37790 载入新 jar，`unzip -p … WebConfig.class | strings` 确认含 `parseOrigins`），
  配置**刻意写成脏的** `'http://a.example, http://b.example , ,http://c.example'`：

  | `Origin` | 实测 |
  |---|---|
  | `http://a.example` / `http://b.example` / `http://c.example` | **200**，**各自回正确的 ACAO** |
  | `http://d.example`（未列出） | **403**，无 ACAO |
  | `http://evil.example`（未列出） | **403**，无 ACAO |
  | `ACRM=PATCH` | **200**，`ACAM=GET,POST,PUT,DELETE,PATCH,OPTIONS`（P2-78 未回退） |
  | 普通请求 `GET /api/stats` | **200** |

  异常数 **0**。`mvn -o compile` 与 `package -DskipTests` 均 EXIT=0。

## 块 2 / 3：P1-2 的 Status 段（第 323 轮逐字迁出）

### P1-2: Java demo 的 `?path=` **无任何路径校验**，且服务绑 `*:37778` —— 同网段可读走本机任意文件

- **Status**: ✅ **已修（第 294 轮，经用户明确授权）** —— 此前多轮 ⏸ 的理由是「加路径约束属收窄已发布端点语义、是对外契约变更」，而这正是需要用户拍板的那一类，**已取得授权**。两步：①`application.yml` 补 `server.address: ${SERVER_ADDRESS:127.0.0.1}`（仍可用环境变量覆盖）；②`FileReadTool` 改为**解析到根目录内**——相对路径按根解析，绝对路径仅当已在根内才接受，逃逸则在**读取之前**拒绝且**不回显内容**，并同时做**词法 `..` 归一化与 `toRealPath()` 符号链接检查**（前者不跟链接、后者对不存在的路径会抛，两者不可互相替代）。三个控制器的 `?path=` 默认值由绝对路径 `/tmp/hello.txt` 改为相对路径 `hello.txt`（沙箱化后原默认值必被拦）。**活体验证**：`hello.txt` 正常返回；`/etc/passwd`、`/Users/<me>/.ssh/id_rsa`、`../pom.xml`、`/etc/../etc/passwd` 四种逃逸**全部在读取前被拒**且响应不含内容；`lsof` 实况 demo 由 `*:37778` 变为 **`127.0.0.1:37778`**，从本机非回环地址 `10.166.1.125:37778` **连接失败**。**+8 条测试**（`FileReadToolTest`，含符号链接逃逸），demo 套件 **25 → 33 全绿**。demo README 端点表下的警告改为如实描述现状。
  **本轮未改任何 Java 代码**，实测用的 demo 进程与探针文件已清理。

## 块 3 / 3：P2-78 的 Status 段（第 323 轮逐字迁出）

### P2-78: CORS 的 `allowedMethods` **漏了 PATCH**——按文档开启 CORS 后，两个 PATCH 端点对浏览器静默失效

- **Status**: ✅ **已修（第 311 轮）** —— `allowedMethods` 补入 `"PATCH"`。
  **按既定规则归类为「纯加宽」而单方面实施**：它只对**运维已显式配置的 origin** 生效，
  不改变任何现有客户端的行为（原先被拒的调用变通，早先能通的调用不受影响），
  且不引入有意义的攻击面（能跨域调 GET/POST/PUT/DELETE 的 origin 本就比 PATCH 权限更高）。
  **修后实测**（重启 37790 载入新 jar，`unzip -p ... WebConfig.class` 内确认含 `PATCH`）：
  六个方法**全部 200** 且 `Allow-Methods: GET,POST,PUT,DELETE,PATCH,OPTIONS`；
  另一条 PATCH 端点路径同样 200；**回归检查**：未授权 origin 仍 **403 且 ACAO 头出现 0 次**。
  `mvn -o compile` 与 `package -DskipTests` 均 EXIT=0。
  **按纪律进入连续 3 轮复查计数（第 311 轮为 1/3，第 312、313 轮各复查一遍）。**
