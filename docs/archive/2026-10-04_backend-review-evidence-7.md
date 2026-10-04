# CortexCE backend-review-findings 实测记录归档 — 第 7 批

> **归档日期**: 2026-10-04（第 254 轮）
> **来源**: P2-52 的 **Evidence** 段（clean 前后测试计数与退出码对比、十个陈旧类清单），逐字迁出。
> **依据**: 承第 241/244/247/253/254 轮同规则；Scope / Problem / Status 一行未动。
> **本文件创建后不得修改**。

---

- **Evidence**: 实测 `mvn -o clean test` 退出码 **0**、`BUILD SUCCESS`，
  计数 **143 + 46 + 7 = 196**，与 `README.md:26` 声明的 196 逐字吻合；
  未 clean 时同一命令报 **211 条、失败 1**，差值 15 恰为十个陈旧类之和。
  十个陈旧类：`CortexMemErrorMessageTest`、`ProbeRound241Test`、`TmpStatusLossProbeTest`、
  `ZzLiveProbeTest`、`ZzProbeBaseUrlTest`、`NestingProbeTest`、
  `ScratchStreamThreadProbeTest`、`ScratchEnvProbeTest`、`ScratchHealthProbeTest`、`ScratchProbeTest`
  （逐个以「`src/test/java/**/<类名>.java` 是否存在」核对，均已不存在）。
