# 测试指南

> English version: [docs/TESTING.md](./TESTING.md)

## 概述

本文档描述 Cortex 社区版的测试方法。

## 测试类别

### 1. 端到端测试

位于 `scripts/` 目录：

| 脚本 | 说明 | 行数 |
|------|------|------|
| `regression-test.sh` | 核心功能回归测试 | 1535 |
| `thin-proxy-test.sh` | 精简代理集成测试 | 775 |
| `mcp-e2e-test.sh` | MCP 服务器端到端测试（SSE 模式） | 555 |
| `mcp-streamable-e2e-test.sh` | MCP 服务器端到端测试（Streamable HTTP 模式） | 292 |
| `docker-compose-test.sh` | Docker Compose 部署测试 | 546 |
| `docker-e2e-test.sh` | Docker 独立端到端测试 | 701 |
| `webui-integration-test.sh` | WebUI 集成测试 | 229 |

### 2. Git 子模块设置（WebUI）

本项目使用 git 子模块引入 WebUI。构建前需初始化子模块：

```bash
# 从项目根目录
git submodule update --init --recursive
```

### 3. 运行测试

#### 前置条件

- PostgreSQL 16 + pgvector 运行在 localhost:5432
- Java 21+
- `.env` 中配置必要的 API keys

#### 运行回归测试

```bash
cd scripts
./regression-test.sh
```

**选项：**

| 选项 | 说明 |
|------|------|
| `--skip-build` | 跳过 Maven 构建（假设 JAR 已存在） |
| `--cleanup` | 测试完成后清理测试数据 |
| `--parallel` | 并行运行独立测试 |
| `--verbose` | 显示详细输出 |
| `--help, -h` | 显示帮助信息 |

**示例：**

```bash
# 使用现有 JAR 运行测试
./regression-test.sh --skip-build

# 显示详细输出
./regression-test.sh --verbose

# 测试后清理数据
./regression-test.sh --cleanup
```

#### 运行 MCP 端到端测试

```bash
# SSE 模式
./scripts/mcp-e2e-test.sh

# Streamable HTTP 模式
./scripts/mcp-streamable-e2e-test.sh
```

#### 运行 Docker 部署测试

```bash
# Docker Compose 部署测试
./scripts/docker-compose-test.sh

# Docker 独立端到端测试
./scripts/docker-e2e-test.sh
```

#### 运行所有端到端测试

```bash
./scripts/run-all-e2e.sh
```

### 4. 预期结果

回归测试预期输出：

```
========================================
Cortex CE Regression Test Suite
========================================
Testing: 46/46 tests passed, 0 failed
```

### 5. 测试环境变量

确保以下环境变量已配置（参见 `.env.example`）：

```bash
SPRING_AI_OPENAI_API_KEY=your_key_here
SPRING_AI_OPENAI_EMBEDDING_API_KEY=your_embedding_key_here
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/claude_mem_dev
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

### 6. 故障排查

#### 测试全部失败

```bash
# 1. 确认服务正在运行
curl http://localhost:37777/api/health

# 2. 确认数据库可连接
psql -U postgres -d claude_mem_dev -c "SELECT 1;"

# 3. 查看详细错误输出
./regression-test.sh --verbose
```

#### Maven 构建失败

```bash
cd backend
mvn clean package -DskipTests
cd ..
```

#### 子模块问题

```bash
# 重置子模块
git submodule foreach git checkout main
git submodule update --init --recursive
```
