# 上游新提交分析（d35efb989 → 8fabef9d3，22 commits，2026-05-05 新增）

**下次扫描起点**：`origin/main` `8fabef9d3`

## 概述

`d35efb989..origin/main` 共 22 个新提交，**0 个记忆相关** — 覆盖 docs/cron/tui/gateway/models/kanban 等功能修复与新特性。

## 提交清单

| Commit | 类型 | 描述 |
|--------|------|------|
| `8fabef9d3` | fix(docs) | register cron-script-only guide in sidebar (#19893) |
| `81cd67829` | fix(google-workspace) | restore required_credential_files in SKILL.md (#16452) |
| `60b143e9d` | fix(tui_gateway) | guard sys.path against local package shadowing (#15989) |
| `645a2f482` | fix(cli) | fix shortcut config conflict in hermes_cli |
| `a919269eb` | docs(skills/email) | document himalaya v1.2.0 folder.aliases syntax |
| `9cda237bb` | docs(cron) | lead with agent-driven setup for no-agent mode (#19871) |
| `eadf34633` | fix(models) | strip :cloud/-cloud suffix from Ollama Cloud model IDs |
| `c050ee657` | fix(file_ops) | resolve search_files path/line collision for hyphenated filenames |
| `fbc477df7` | fix(run_agent) | acquire lock in IterationBudget.used property |
| `64ad7dec0` | fix(file-ops) | allow file search in hidden roots |
| `9e2628ee7` | test(discord) | annotate make_attachment content_type as Optional[str] |
| `1c7f47a58` | fix(cron) | add concurrency regression test for parallel job state writes |
| `687547191` | fix(tts) | update MiniMax API endpoint to v1/text_to_speech |
| `75bce317a` | fix(cron) | expand \${VAR} refs in config.yaml during job execution (#15890) |
| `fd9c32c0f` | fix(email) | drop non-allowlisted senders before dispatch |
| `20edca75e` | fix(update) | sync bundled skills to all profiles including active (#16176) |
| `103f51ad3` | fix(doctor) | check gh auth status when GITHUB_TOKEN absent |
| `8ab9f61dc` | fix(gateway) | preserve WSL interop PATH in systemd units |
| `d90f73bce` | fix(gateway) | use git HEAD SHA, not file mtimes, for stale-code check (#19740) |
| `a21f364ad` | chore(release) | AUTHOR_MAP entries for Tier 1g salvage batch |
| `1c7c7c3c5` | feat(kanban-dashboard) | per-platform home-channel notification toggles (#19864) |
| `3db6b9cc8` | feat(cron) | add no_agent mode for script-only cron jobs (watchdog pattern) (#19709) |

## 记忆系统相关文件 Diff 检查

对以下记忆系统核心路径执行 diff 无任何变更：

- `agent/memory_manager.py`
- `agent/context_compressor.py`
- `agent/trajectory_compressor.py`
- `plugins/memory/holographic/`
- `plugins/memory/honcho/`
- `plugins/memory/hindsight/`
- `plugins/memory/retaindb/`
- `plugins/memory/supermemory/`
- `plugins/memory/mem0/`
- `tools/memory_tool.py`
- `tools/session_search_tool.py`

## 分析结论

记忆系统文档保持最新。值得注意的是 `fbc477df7` 在 `run_agent` 中为 `IterationBudget.used` 属性添加了锁，这是一个小的线程安全修复，但不影响记忆系统核心逻辑。
