# Phase 3 Design — 文档索引

> **架构规范**：本目录内每个文档 **≤50KB**，禁止堆积长文。原根文件 `phase-3-design.md`（271KB）已拆分至此。

---

## 文档概览

| 文件 | 大小 | 内容 |
|------|------|------|
| `00-quick-ref.md` | 1KB | TL;DR 快速参考 + 核心 Pipeline 图示 |
| `00-overview.md` | 1KB | 设计纠错 / 泛化思路 / Bug Fix 索引 / 架构分析 |
| `0.1.md` | 5KB | v7 关键 Bug 修复（findBySource List 参数、BeanOutputConverter Class） |
| `0.2.md` | 3KB | v10 关键设计缺口（Schema-to-Class bridge、Array handling、missing impls） |
| `0.3.md` | 1KB | Refine vs Extraction 概念澄清 |
| `2.md` | 29KB | **核心**：通用 Structured Extraction 设计（模板/管道/DLQ/mergeAppendOnly） |
| `3.md` | 3KB | Memory Conflict Detection 设计 |
| `03-deferred-roadmap-principles.md` | 1KB | UserProfile defer / Roadmap / Key Principles |
| `7.md` | 8KB | Additional Considerations 深度探讨 |
| `8.md` | 2KB | Open Questions 状态（10/10 已解决） |
| `9.md` | 3KB | Implementation Feasibility Check |
| `10.md` | 7KB | Critical Implementation Considerations |
| `11.md` | 6KB | Error Handling & Recovery |
| `12.md` | 4KB | Template Lifecycle Management |
| `13.md` | 4KB | Extraction Result Usage |
| `14.md` | 2KB | Testing Strategy |
| `15.md` | 21KB | **Implementation Bootstrap Checklist**（可执行步骤） |
| `16.md` | 2KB | Architecture Decision Records (ADRs) |
| `17.md` | 2KB | Extraction Idempotency（v14） |
| `18.md` | 2KB | Observation Type Namespace Reservation（v14） |
| `19.md` | 8KB | Implementation Readiness & Practical Gaps（v15） |
| `20.md` | 17KB | Walkthrough Findings（v16） |
| `21.md` | 11KB | Implementation Inspection Findings（v19） |
| `22.md` | 5KB | SDK API Walkthrough Findings（v21） |
| `23.md` | 8KB | Token Cost Analysis（v23） |
| `24.md` | 12KB | LLM Re-Extraction Edge Cases（v24） |
| `24.6.md` | 10KB | **Prior Truncation Silent Data Loss Fix**（v28，append-only solution） |
| `25.md` | 42KB | **Implementation Plan Phase 3.1**（完整执行计划） |
| `26.md` | 15KB | Acceptance Test Plan（Test-First） |
| `99-changelog.md` | 15KB | 版本历史 Changelog |

---

## 建议阅读顺序

### 入门路径（1-2h 快速理解）
1. `00-quick-ref.md` — TL;DR + Pipeline 图示
2. `00-overview.md` — 设计理念 + 架构分析
3. `2.md` — 核心设计（29KB，泛化提取架构）
4. `15.md` — Implementation Bootstrap Checklist（可直接执行）
5. `25.md` — 完整 Implementation Plan

### 深度理解路径（半天）
1. 完成入门路径
2. `23.md` — Token Cost 分析（理解为什么 append-only 是最优解）
3. `24.6.md` — Prior Truncation 问题（v28 修复，append-only 设计核心）
4. `20.md` / `21.md` / `22.md` — 各轮审查发现
5. `17.md` — Idempotency 保证
6. `26.md` — Acceptance Test Plan

### 实现参考路径
1. `15.md` — Bootstrap Checklist
2. `25.md` — 完整 Implementation Plan
3. `11.md` — Error Handling
4. `14.md` — Testing Strategy

---

## 核心概念速查

| 概念 | 文档 |
|------|------|
| append-only extraction | `24.6.md` |
| BeanOutputConverter + Java Class | `0.1.md` §Bug1 |
| mergeAppendOnly() | `2.md` §24.6 |
| DLQ (Dead Letter Queue) | `2.md` §2.5 |
| Template lifecycle | `12.md` |
| Token budget (~$0.0004/次) | `23.md` |
| Idempotency | `17.md` |
| Observation type namespace | `18.md` |

---

**体量合规**：30 个文件，最大 42KB，均 ≤50KB ✅
