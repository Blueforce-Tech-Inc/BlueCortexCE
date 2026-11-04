# 小批 K — 真实 provider 复核、完整性巡检、向量候选扩展

日期：2026-11-04
范围：K1 真实 chat provider 并发复核；K2 只读数据完整性巡检；K3 向量近邻候选扩展实验。
结论：K1 通过（MiniMax 可用，LongCat 配额耗尽）；K2 巡检上线并接入备份脚本；K3 指标零提升，**拒绝上线**。

## K1. 真实 provider 并发复核

### 凭据探测（只报状态，不回显 key）

| Provider | 端点形态 | 探测结果（2026-11-04 22:55 CST） |
|---|---|---|
| LongCat | OpenAI-compatible `/chat/completions` | **HTTP 402**，配额/余额耗尽 |
| MiniMax | Anthropic-compatible `/v1/messages` | **HTTP 200**，返回正常 message |

`backend/.env` 里同时存在两组配置。探测只发送一条 8-token 的 `reply with OK`，不打印任何
key、请求头或响应正文。启动服务后 `GET /api/test/llm` 返回 `status=success`，确认应用实际
走的是 MiniMax（Anthropic 协议）+ SiliconFlow `BAAI/bge-m3` embedding，而不是离线替身。

### 并发场景（真实 provider，真实 HTTP）

应用以打包 jar 启动（独立临时库、随机端口），场景与 J3 相同：

| 场景 | 结果 |
|---|---|
| 同 project、不同 session 并发写入相同内容 | 两条 id 不同；各自 session 列表只看到自己 1 条 |
| 并发后的检索 | `strategy=hybrid`，两条都在结果中，`content_session_id` 各自正确 |
| 项目级上下文注入 | 200，包含本次唯一 token |
| 同 session、同一 payload 并发 10 次（observation） | 返回 id 去重后 **1** 个，库里 **1** 行 |
| 同 session、同一 tool-use hook 并发 10 次（走真实 chat 抽取） | 准入 **1** 个任务，队列 `processed:1`，最终 **1** 条 observation，耗时 5.5 s |

结论：J3 的会话隔离、检索归属、注入、去重语义在真实 provider 下同样成立。K1 不再标记
“未验证”。（本轮未复用 J3 的离线测试文件；并发驱动是独立的一次性 HTTP 脚本，结果 JSON 已在
收尾时随临时库清理，仓库只保留本记录。）

## K2. 数据完整性巡检

新增 `scripts/data-integrity-audit.py`（只读、默认 dry-run），扫描 9 类：

1. 孤儿 observation（`conversation_id` 无父会话）
2. 孤儿 summary
3. project scope 的 `scope_key` 与 `project_path` 不一致
4. observation 的 `project_path` 与会话不一致
5. `embedding_state` 与实际向量不符（ready 无向量 / 有向量却非 ready）
6. 同一会话、相同 `content_hash`、30 秒窗口内重复且未被抑制
7. `processing` 超过 15 分钟
8. pending 数量 >100 或队头 >60 分钟
9. summary 输入快照引用了已不存在的 observation

默认只打印并写入 JSON 的修复 SQL；`--apply` 执行安全修复（embedding 状态、卡住队列行），
`--apply-destructive` 才执行删除孤儿/重复、改写 scope 链接。退出码 0 干净 / 1 有发现 /
2 无法巡检。

### 脏数据验证（不是只跑干净库）

`scripts/test-data-integrity-audit.py` 建临时库、启动 jar 迁移、逐类造一条脏数据，然后断言：

- 首轮 9 类**全部**非零（dry-run 前后 observation 行数不变）；
- `--apply-destructive` 后 6 类可修复项归零；
- 3 类保留：`conversation_project_mismatch`（review-only）、`summary_snapshot_missing_obs`
  （review-only）、`pending_backlog`（不自动修）。

同一脚本在一个全新迁移库上跑巡检：**0 发现、退出码 0**，无误报。

### 与备份闭环集成

`backup-restore-drill.sh` 新增 `INTEGRITY_AUDIT=1`：恢复临时库后直接跑巡检，报告写到
`<REPORT_DIR>/integrity-drill-<时间戳>.json`，有发现时该阶段**退出码 10**。用 100 万行
`cortexce_load` 实跑：

- dump / 校验 / 恢复 / 行数 / 检索全部通过；
- 巡检发现 **20,000** 行“有向量但 `embedding_state='complete'`”，退出码 10，报告与 SQL 落盘；
- `complete` 是早期 eval 装载脚本写入的值，不是 `ready`——巡检在百万行规模上抓到了一个
  真实的数据状态偏差（装载脚本已改为 `ready`，避免继续制造）。

运维结论已并入 `docs/DEPLOYMENT.md` / `docs/DEPLOYMENT-zh-CN.md` 的备份章节：每日在线只读
巡检、每周随备份恢复副本巡检、报告位置、30 天清理、发现后的处置顺序。没有新建散文档。

## K3. 向量近邻扩大候选实验

改动：`scripts/eval-retrieval-quality.py` 增加 `semantic_limit` 参数与 `--vector-depths`。
只放大**向量候选列表**（100/200/400），全文候选与融合后截断都保持生产深度 40。语料仍是
90 题 / 188 条 observation，7 天半衰期重排与生产一致。

### 数据

同一基线（生产 hybrid + 7 天衰减）：**hit@1 0.833 / hit@5 0.911 / MRR 0.866**。

| 向量候选深度 | 融合 hit@1 | 融合 hit@5 | 融合 MRR | +7d hit@1 | +7d hit@5 | +7d MRR | 平均耗时 | p95 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 40（基线） | 0.778 | 0.900 | 0.821 | 0.833 | 0.911 | 0.866 | 74.0 ms | 114.1 ms |
| 100 | 0.778 | 0.900 | 0.821 | 0.833 | 0.911 | 0.866 | 73.2 ms | 113.0 ms |
| 200 | 0.778 | 0.900 | 0.821 | 0.833 | 0.911 | 0.866 | 77.2 ms | 119.7 ms |
| 400 | 0.778 | 0.900 | 0.821 | 0.833 | 0.911 | 0.866 | 75.7 ms | 115.7 ms |

诊断：

- 生产口径（hit@5）未命中 **8** 条；其中 **6** 条至少有一条相关行在融合池之外，13 条查询
  共有 22 条相关行不在池内（与上一轮“15 条未命中/5 条不在池”的口径不同，这里按当前 90 题
  基线重新计数）。
- 纯向量列表在深度 100 时已覆盖 186/188 条相关行，深度 200 时 **188/188**：相关行并非
  “向量搜不到”。
- 但把向量候选放大到 100/200/400 后，融合指标**一位小数都不动**；MRR 甚至从 0.8657 降到同值
  （融合 0.8213→0.8212）。原因是这些行以很深的向量名次进入候选，RRF 得分 `1/(60+rank)` 远低于
  全文候选（权重 2.0）的 `2/(60+rank)`，仍然进不了融合 top-40。**候选池不是瓶颈，融合权重才是。**
- 延迟：三档均值 73.2–77.2 ms，基线 74.0 ms；客户端每次调用都含固定的 psql 进程开销，
  差异 <4 ms，在噪声内。数据量 188 行也不足以体现 HNSW `ef_search` 的差异。

### 判定

**拒绝上线**（未加生产开关、未改 `SearchService`）。理由：hit@1/hit@5/MRR 全部在单题噪声
（0.011/题，阈值 0.02）内，零收益；虽然延迟无可测代价，但没有理由为无效改动增加一个配置项。
下一步若继续追这 6 条，应做“降低全文权重/调整 RRF 权重”的对照实验，而不是继续加深向量池。

实验产物：`docs/archive/2026-11-04_retrieval-quality-candidate-expansion.md` 与同名 `.json`。
