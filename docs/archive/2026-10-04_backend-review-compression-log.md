# CortexCE backend-review-findings 压缩历史归档 — 第 1~4 批

> **归档日期**: 2026-10-04（第 252 轮）
> **来源**: `docs/drafts/backend-review-findings.md` 的 `## Archived History` 小节，**逐字迁出**。
> **触发原因**: 该文件写入第 252 轮的 P2-48 后达 1034 行，越过 `MAX_LINES=1000`
>   （`doc-growth-check.sh` 返回 `COMPACTION_REQUIRED`、退出码 2）。
> **为何可迁出**: 这些段落记的是**已完成的压缩批次本身**，属于历史；
>   每批的完整说明（筛选规则、逐字校验方式、为何留下 / 迁走哪些段）
>   **已逐条记在 `docs/archive/README.md`**，两处原本记着同一批事件。
> **注意**: 本文件记录的是**文件自身的维护历史**，与 37 条 ⏸ 条目的
>   决策推理无关——后者仍完整留在工作文件中，未迁出任何一行。
> **本文件创建后不得修改**。

---

## Archived History

The complete historical review log through 2026-05-07 is preserved in [`2026-09-30_backend-review-findings-history.md`](../archive/2026-09-30_backend-review-findings-history.md). Do not modify that archive; future resolved history should use a new dated archive when this file reaches the growth threshold again.

Ten entries whose status is unconditionally resolved — P1-2, P2-1, P2-2, P2-3, P2-4, P2-5, P2-6, P2-7, P2-9 and P2-12 — were moved verbatim on 2026-10-03 (round 225) into [`2026-10-03_backend-review-history-resolved.md`](../archive/2026-10-03_backend-review-history-resolved.md), when this file reached 1008 lines against the `MAX_LINES=1000` threshold. That archive records the selection rule and must not be modified.

A second batch — **P1-3 and P1-4, 79 lines moved verbatim** — went into [`2026-10-03_backend-review-history-resolved-2.md`](../archive/2026-10-03_backend-review-history-resolved-2.md) on 2026-10-03 (round 232), when this file stood at 980 lines and adding P2-30 would have crossed the threshold. **P2-24 was deliberately left behind**: it carries a ⏸ remainder even though its first two parts are ✅ fixed, so it still holds live reasoning rather than history. Verbatim equality of both batches was verified by diffing the extracted block against `git show HEAD` before the source lines were removed.

**Provenance note.** On 2026-10-04 (round 237) the `- **复核记录**:` sections of P2-22 through P2-27 were moved verbatim into [`2026-10-03_backend-review-provenance.md`](../archive/2026-10-03_backend-review-provenance.md), each replaced by a one-line pointer. The file is structurally saturated — 27 entries, 25 of them ⏸ — and the ⏸ rule below protects the **decision reasoning** (Scope / Problem / Status), which stayed. `复核记录` is provenance: which round found it and how the evidence was gathered, and the same text is stored verbatim per round in `patrol-rotation.md` and `doc-review-task.md`. **This is the first move of this kind**; if the ⏸ rule is later read to cover provenance too, the sections can be restored from the archive without loss.

**Provenance note, batch 2.** On 2026-10-04 (round 238) the same treatment was applied to **P2-28 through P2-31**, moved verbatim into [`2026-10-04_backend-review-provenance-2.md`](../archive/2026-10-04_backend-review-provenance-2.md) when P2-36 pushed this file to 1035 lines. A **separate** file was used because batch 1 declares itself immutable. Verbatim equality against `git show HEAD` was verified before any source line was removed, and the working file dropped to 993. Round 238 also removed the duplicated compression log from `Current Status`, which duplicated this section's history and had to be updated twice per compression.

Entries carrying a `⏸` "recorded, not fixing" status stay here on purpose: they hold the reasoning behind each decision and are the live record, not history. P2-11 also stays, because its backend half is still undecided even though the documentation and annotation layers were fixed.

**Provenance note, batch 3.** On 2026-10-04 (round 241) the same treatment was applied to **P2-24 and P2-32 through P2-37** — seven sections, 40 lines — moved verbatim into [`2026-10-04_backend-review-provenance-3.md`](../archive/2026-10-04_backend-review-provenance-3.md) when P2-37 pushed the file to 1033 lines, the ninth compression it has needed. Verbatim equality against `git show HEAD` was verified first.

**Reproduction note, batch 4 — the first move of a section other than `复核记录`.** The first three batches had exhausted every `复核记录` section, yet the file still stood at 1029. A **measured transcript** — a captured wire body, a live curl result, a table of row counts — is *reproducible evidence*, not the reasoning behind a decision, so the **Reproduction** sections of **P2-25, P2-26, P2-30 and P2-31** (89 lines) moved verbatim into [`2026-10-04_backend-review-reproduction-4.md`](../archive/2026-10-04_backend-review-reproduction-4.md), each replaced by a one-line pointer. **Scope / Problem / Evidence / Status stayed put.** Every transcript names its date, endpoint and technique, so it is reproducible on demand. This brought the file to **944 lines** — the first compression in four rounds that left real headroom. **This extends the rule rather than merely applying it, so it is flagged for project decision**: if the ⏸ rule is meant to protect the evidence too, the four sections are restorable from the archive without loss.
