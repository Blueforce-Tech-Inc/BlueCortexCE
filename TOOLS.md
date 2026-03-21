# TOOLS.md - Local Notes

Skills define _how_ tools work. This file is for _your_ specifics — the stuff that's unique to your setup.

## What Goes Here

Things like:

- Camera names and locations
- SSH hosts and aliases
- Preferred voices for TTS
- Speaker/room names
- Device nicknames
- Anything environment-specific

## Examples

```markdown
### Cameras

- living-room → Main area, 180° wide angle
- front-door → Entrance, motion-triggered

### SSH

- home-server → 192.168.1.100, user: admin

### TTS

- Preferred voice: "Nova" (warm, slightly British)
- Default speaker: Kitchen HomePod
```

## Why Separate?

Skills are shared. Your setup is yours. Keeping them apart means you can update skills without losing your notes, and share skills without leaking your infrastructure.

---

Add whatever helps you do your job. This is your cheat sheet.

## Cortex CE Development Notes

### Demo Build Requirement (IMPORTANT!)

**Demo 项目必须使用 `-Plocal` profile 构建**，否则会从 JitPack 拉取旧版本：

```bash
# ❌ Wrong - pulls old version from JitPack
cd examples/cortex-mem-demo && mvn clean compile

# ✅ Correct - uses local SDK
cd examples/cortex-mem-demo && mvn clean compile -Plocal
```

**SDK 安装到本地仓库**：
```bash
cd cortex-mem-spring-integration && mvn clean install -DskipTests
```

### Service Health Check

使用 HTTP 端点检测服务状态，不要检查进程：

```bash
curl -s http://127.0.0.1:37777/api/health
# Expected: {"service":"claude-mem-java","status":"ok",...}
```

### Regression Test

```bash
bash scripts/regression-test.sh
# Expected: 39/39 tests passed
```

### Demo V14 Test

```bash
bash scripts/demo-v14-test.sh
# Expected: 4/4 tests passed
```

### Language Conventions

- 修改文档时注意文档语言版本（中文版一般带 `-zh-CN` 后缀）
- 代码注释优先使用英文
- 草稿文档和临时脚本语言不用过于严格
