# `extractedData.error_sig_norm` 写入提案

> **目标**：将 Evolver `normalizeErrorSignature` 思想落地为 BlueCortexCE 的错误规范化签名写入方案。  
> **背景**：Evolver `memoryGraph.js` §27 定义了完整的归一化算法（路径→`<path>`、十六进制→`<hex>`、数字→`<n>`、220截断、stableHash），实现"同类错误聚合"能力。BlueCortexCE 的 `ObservationEntity.extractedData` JSONB 已具备存储能力，`content_hash` 负责精确去重，两套机制可共存。  
> **数据来源**：`docs/drafts/evolver-memory/21-signal-taxonomy-and-gene-selection-memory.md` §2.3。  
> **前置**：先读 [`21`](./21-signal-taxonomy-and-gene-selection-memory.md) §2.3。  
> **状态**：提案（未实现）

---

## 1. 设计目标

1. **同类错误聚合**：规范化后的错误签名使得"同一类错误（路径不同/数字不同）"能被识别，用于检索时的相似错误聚合。
2. **不破坏现有 dedup**：`content_hash` 保持精确哈希去重，`error_sig_norm` 仅用于"同类聚合"检索，两者正交。
3. **最小侵入**：仅在写入路径增加逻辑，不修改已有 API 契约和字段。

---

## 2. 规范化算法（from Evolver `memoryGraph.js` §27）

```java
public static String normalizeErrorSignature(String text) {
    if (text == null || text.isBlank()) return null;
    String s = text.trim();
    return s
        .toLowerCase()
        .replaceAll("[a-z]:\\\\[^ \\n\\r\\t]+", "<path>")   // Windows path: C:\foo\bar
        .replaceAll("/[^ \\n\\r\\t]+", "<path>")            // Unix path: /foo/bar
        .replaceAll("\\b0x[0-9a-f]+\\b", "<hex>")           // hex: 0x1A2B
        .replaceAll("\\b\\d+\\b", "<n>")                   // numbers: 42
        .replaceAll("\\s+", " ")                             // collapse whitespace
        .substring(0, Math.min(s.length(), 220));           // truncate to 220 chars
}
```

**效果示例**：
```
Input:  "java.io.FileNotFoundException: C:\Users\alice\data\a.txt (No such file) at line 42"
Output: "java.io.filenotfoundexception: <path> (no such file) at line <n> at line <n>"

Input:  "Error: connection to 0x7F8B at /api/v2/users/1234 failed after 5 retries"
Output: "error: connection to <hex> at <path> failed after <n> retries"
```

**注意**：Evolver 源码在 replace 后还调用 `stableHash(norm)` 取摘要存储。BlueCortexCE 建议**直接存储归一化文本**而非 hash，理由：
- 可读性好，便于调试和人工核查
- JSONB 支持 LIKE 查询，可以做前缀/子串匹配
- 若未来需要 hash，可以单独加字段

---

## 3. JSONB 字段设计

在 `ObservationEntity.extractedData` JSONB 中，error 类观察写入：

```json
{
  "error_sig_norm": "java.io.filenotfoundexception: <path> (no such file) at line <n>"
}
```

**扩展字段（可选，作为 future work）**：

```json
{
  "error_sig_norm": "...",
  "error_count": 3,
  "first_seen_epoch": 1713000000000,
  "last_seen_epoch": 1713500000000
}
```

其中 `error_count` / `first_seen_epoch` / `last_seen_epoch` 由**同一规范化签名的多次观察聚合**填充，超出本提案范围（见 [`11-research-backlog.md`](./11-research-backlog.md) backlog item "时间半衰 / 重复失败降权"）。

---

## 4. 写入路径

### 4.1 触发条件

在 `AgentService.saveObservation` 路径中，满足以下全部条件时写入：
1. `observationType == "error"`（大小写不敏感）
2. `content` 非空
3. `content` 长度 > 10（避免无意义短文本）

### 4.2 实现位置

建议在 `ObservationService.java` 或 `AgentService.java` 的 `saveObservation` 方法末尾（observation 写入 DB 之后）增加逻辑：

```java
// ObservationService.java
private void enrichErrorSignature(ObservationEntity obs) {
    if (!"error".equalsIgnoreCase(obs.getObservationType())) {
        return;
    }
    String content = obs.getContent();
    if (content == null || content.length() <= 10) {
        return;
    }
    String norm = normalizeErrorSignature(content);
    if (norm == null) return;
    
    Map<String, Object> extracted = obs.getExtractedData();
    if (extracted == null) {
        extracted = new HashMap<>();
        obs.setExtractedData(extracted);
    }
    extracted.put("error_sig_norm", norm);
}
```

### 4.3 normalizeErrorSignature 实现

```java
public static String normalizeErrorSignature(String text) {
    if (text == null || text.isBlank()) return null;
    String s = text.trim();
    String result = s
        .toLowerCase()
        .replaceAll("[a-z]:\\\\[^ \\n\\r\\t]+", "<path>")
        .replaceAll("/[^ \\n\\r\\t]+", "<path>")
        .replaceAll("\\b0x[0-9a-f]+\\b", "<hex>")
        .replaceAll("\\b\\d+\\b", "<n>")
        .replaceAll("\\s+", " ");
    return result.substring(0, Math.min(result.length(), 220));
}
```

---

## 5. 检索增强（future work）

`error_sig_norm` 的核心价值在于**同类错误聚合检索**。实现写入后，可在 SearchService 中增加：

```sql
-- 检索同一类错误的其他观察（PostgreSQL JSONB）
SELECT * FROM mem_observations
WHERE extracted_data->>'error_sig_norm' = :normSig
  AND id != :currentId
ORDER BY created_epoch DESC
LIMIT 5;
```

或作为向量检索的 pre-filter，减少语义搜索的噪音。

详细检索增强方案见 [`20-time-decay-and-fail-degradation.md`](./20-time-decay-and-fail-degradation.md) §5 及 backlog [`11-research-backlog.md`](./11-research-backlog.md) 中的"历史成功率衰减"条目。

---

## 6. 实施检查清单

| 步骤 | 动作 | 影响文件 | 状态 |
|------|------|----------|------|
| 1 | 在 `ObservationService` 增加 `normalizeErrorSignature` 方法 | `ObservationService.java` | ⬜ |
| 2 | 在 `saveObservation` 路径增加 `enrichErrorSignature` 调用 | `ObservationService.java` | ⬜ |
| 3 | 添加单元测试（`ObservationServiceTest`）覆盖规范化算法 | `ObservationServiceTest.java` | ⬜ |
| 4 | regression test 确认无回归 | `scripts/regression-test.sh` | ⬜ |
| 5 | 端到端测试验证 JSONB 写入 | — | ⬜ |

---

## 7. 与现有 backlog 的关系

| Backlog 条目 | 本提案对应 |
|-------------|-----------|
| `extractedData.error_sig_norm` 写入 | **本提案** P0 |
| 时间半衰 / 重复失败降权 | P1：`error_sig_norm` 提供同类错误识别基础，P1.1 的 `fail_count` 可用同一规范化 key 聚合 |
| `inferOutcomeEnhanced` 的 baseline vs current delta | P2：规范化签名可作为 delta 比较的分组 key |

---

## 8. 相关文档

- 总索引：[`index.md`](./index.md)
- `normalizeErrorSignature` 源码分析：[`21-signal-taxonomy-and-gene-selection-memory.md`](./21-signal-taxonomy-and-gene-selection-memory.md) §2.3
- 时间衰减专题：[`20-time-decay-and-fail-degradation.md`](./20-time-decay-and-fail-degradation.md)
- 研究 backlog：[`11-research-backlog.md`](./11-research-backlog.md) §2（可勾选）
- CE 实现锚点：[`10-aspect-bluecortex-implementation-map.md`](./10-aspect-bluecortex-implementation-map.md)
