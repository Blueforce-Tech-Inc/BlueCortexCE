# Python Client SDK 设计文档

> **版本**: v1.0 DRAFT
> **日期**: 2026-03-27
> **状态**: 待审批
> **作者**: Cortex CE Team

---

## 执行摘要

### 核心决策

1. **零强制依赖** — 核心包只依赖 `requests`
2. **与 Go/Java SDK 对齐** — 覆盖全部 26 个 API 方法
3. **地道 Python 风格** — dataclass、kwargs、context manager
4. **可选集成层** — LangChain、LlamaIndex 以独立包提供（Phase 2）

### 目录结构

```
cortex-mem-python/
├── pyproject.toml           # 项目配置
├── README.md
├── cortex_mem/
│   ├── __init__.py          # 公开 API
│   ├── client.py            # CortexMemClient 实现
│   ├── dto.py               # 数据传输对象
│   ├── error.py             # 错误类型
│   └── version.py           # 版本号
└── tests/
    ├── test_client.py       # 单元测试 (httptest)
    └── test_dto.py          # DTO 测试
```

---

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| **零强制依赖** | 核心包只依赖 `requests` |
| **地道 Python 风格** | dataclass、kwargs、context manager (`with` 语句) |
| **可选集成层** | LangChain、LlamaIndex 独立包（Phase 2） |
| **与 Go SDK 等价** | 覆盖全部 26 个 API 方法 |

### 1.1 Quick Start

```python
from cortex_mem import CortexMemClient

# 1. 创建客户端
client = CortexMemClient(base_url="http://localhost:37777")

# 2. 启动会话
session = client.start_session(session_id="my-session", project_path="/path/to/project")

# 3. 记录观察（fire-and-forget）
client.record_observation(
    session_id=session.session_id,
    project_path="/path/to/project",
    tool_name="Read",
    tool_input={"file": "main.go"},
)

# 4. 检索相关经验
experiences = client.retrieve_experiences(
    task="How to handle errors in Go?",
    project="/path/to/project",
    count=3,
)

# 5. 构建 ICL Prompt
result = client.build_icl_prompt(
    task="How to handle errors in Go?",
    project="/path/to/project",
)
print(f"ICL prompt: {len(result.prompt)} chars")

# 6. 结束会话
client.record_session_end(session_id=session.session_id, project_path="/path/to/project")

# 7. 关闭客户端
client.close()
```

### 1.2 Context Manager 用法

```python
with CortexMemClient(base_url="http://localhost:37777") as client:
    session = client.start_session(session_id="s1", project_path="/p")
    # ... use client ...
# 自动调用 close()
```

---

## 2. 目录结构

```
cortex-mem-python/
├── pyproject.toml              # 项目配置 (PEP 621)
├── README.md
├── LICENSE
├── cortex_mem/
│   ├── __init__.py             # 公开 API 导出
│   ├── client.py               # CortexMemClient 类
│   ├── dto.py                  # 所有 dataclass DTO
│   ├── error.py                # 异常类型
│   └── version.py              # __version__
└── tests/
    ├── conftest.py             # pytest fixtures
    ├── test_client.py          # 单元测试
    └── test_dto.py             # DTO 序列化测试
```

---

## 3. Client API 设计

### 3.1 Client 创建

```python
client = CortexMemClient(
    base_url="http://localhost:37777",  # 默认
    timeout=30,                          # 秒，默认 30
    max_retries=3,                       # 默认 3
    retry_backoff=0.5,                   # 秒，默认 0.5
    api_key=None,                        # 可选 Bearer token
    session=None,                        # 可选 requests.Session
)
```

### 3.2 完整 API (26 个方法)

```python
class CortexMemClient:
    # ==================== Session ====================

    def start_session(self, session_id: str, project_path: str, user_id: str | None = None) -> SessionStartResponse:
        """POST /api/session/start"""

    def update_session_user_id(self, session_id: str, user_id: str) -> dict:
        """PATCH /api/session/{session_id}/user"""

    # ==================== Capture (fire-and-forget) ====================

    def record_observation(self, session_id: str, project_path: str, tool_name: str, **kwargs) -> None:
        """POST /api/ingest/tool-use (fire-and-forget)"""

    def record_session_end(self, session_id: str, project_path: str, last_assistant_message: str | None = None) -> None:
        """POST /api/ingest/session-end (fire-and-forget)"""

    def record_user_prompt(self, session_id: str, prompt_text: str, project_path: str = "", prompt_number: int = 0) -> None:
        """POST /api/ingest/user-prompt (fire-and-forget)"""

    # ==================== Retrieval ====================

    def retrieve_experiences(self, task: str, project: str = "", **kwargs) -> list[Experience]:
        """POST /api/memory/experiences"""

    def build_icl_prompt(self, task: str, project: str = "", **kwargs) -> ICLPromptResult:
        """POST /api/memory/icl-prompt"""

    def search(self, project: str, **kwargs) -> SearchResult:
        """GET /api/search"""

    def list_observations(self, project: str = "", **kwargs) -> ObservationsResponse:
        """GET /api/observations"""

    def get_observations_by_ids(self, ids: list[str]) -> BatchObservationsResponse:
        """POST /api/observations/batch"""

    # ==================== Management ====================

    def trigger_refinement(self, project_path: str) -> None:
        """POST /api/memory/refine?project=..."""

    def submit_feedback(self, observation_id: str, feedback_type: str, comment: str = "") -> None:
        """POST /api/memory/feedback"""

    def update_observation(self, observation_id: str, **kwargs) -> None:
        """PATCH /api/memory/observations/{id}"""

    def delete_observation(self, observation_id: str) -> None:
        """DELETE /api/memory/observations/{id}"""

    def get_quality_distribution(self, project_path: str) -> QualityDistribution:
        """GET /api/memory/quality-distribution?project=..."""

    # ==================== Health ====================

    def health_check(self) -> None:
        """GET /api/health — raises APIError on failure"""

    # ==================== Extraction ====================

    def trigger_extraction(self, project_path: str) -> None:
        """POST /api/extraction/run"""

    def get_latest_extraction(self, project_path: str, template_name: str, user_id: str = "") -> ExtractionResult:
        """GET /api/extraction/{template}/latest"""

    def get_extraction_history(self, project_path: str, template_name: str, user_id: str = "", limit: int = 0) -> list[ExtractionResult]:
        """GET /api/extraction/{template}/history"""

    # ==================== Version ====================

    def get_version(self) -> VersionResponse:
        """GET /api/version"""

    # ==================== P1 Management ====================

    def get_projects(self) -> ProjectsResponse:
        """GET /api/projects"""

    def get_stats(self, project_path: str = "") -> StatsResponse:
        """GET /api/stats"""

    def get_modes(self) -> ModesResponse:
        """GET /api/modes"""

    def get_settings(self) -> dict:
        """GET /api/settings"""

    # ==================== Lifecycle ====================

    def close(self) -> None:
        """Close the underlying HTTP session."""

    def __enter__(self) -> "CortexMemClient": ...
    def __exit__(self, *args) -> None: ...
```

---

## 4. DTO 设计

所有 DTO 使用 Python `dataclasses`，用 `field()` 控制序列化行为。

### 核心 DTO

| DTO | 用途 |
|-----|------|
| `SessionStartResponse` | 会话启动响应 |
| `Experience` | 检索到的经验 |
| `ICLPromptResult` | ICL prompt 结果 |
| `Observation` | 观察记录 |
| `SearchResult` | 搜索结果 |
| `ObservationsResponse` | 分页观察列表 |
| `BatchObservationsResponse` | 批量观察响应 |
| `QualityDistribution` | 质量分布 |
| `ExtractionResult` | 提取结果 |
| `VersionResponse` | 版本信息 |
| `ProjectsResponse` | 项目列表 |
| `StatsResponse` | 统计信息 |
| `ModesResponse` | 模式配置 |

---

## 5. Wire Format 映射

### 关键规则（从 Go SDK 验证）

| 场景 | Go JSON tag | Python 序列化 key |
|------|-------------|-------------------|
| SessionStart.project_path | `json:"project_path"` | `"project_path"` |
| SessionEnd.project_path | `json:"cwd"` | `"cwd"` |
| Observation.project_path | `json:"cwd"` | `"cwd"` |
| UserPrompt.project_path | `json:"cwd"` | `"cwd"` |
| ExperienceRequest.requiredConcepts | `json:"requiredConcepts"` | `"requiredConcepts"` (camelCase) |
| ExperienceRequest.userId | `json:"userId"` | `"userId"` (camelCase) |
| ICLPromptRequest.maxChars | `json:"maxChars"` | `"maxChars"` (camelCase) |
| ObservationRequest.extractedData | `json:"extractedData"` | `"extractedData"` (camelCase) |
| FeedbackRequest.observationId | `json:"observationId"` | `"observationId"` (camelCase) |

---

## 6. 错误处理

```python
class CortexError(Exception):
    """Base exception for Cortex CE SDK."""

class APIError(CortexError):
    """API error with status code."""
    def __init__(self, status_code: int, message: str): ...

class NotFoundError(APIError): ...      # 404
class ConflictError(APIError): ...      # 409
class RateLimitError(APIError): ...     # 429
class ServerError(APIError): ...        # 5xx
```

---

## 7. 测试策略

| 类型 | 工具 | 说明 |
|------|------|------|
| 单元测试 | `pytest` + `responses` | mock HTTP |
| E2E 测试 | `scripts/python-sdk-e2e-test.sh` | bash + curl |

---

## 8. 版本策略

| 包 | 版本 | 说明 |
|---|------|------|
| `cortex-mem-python` | 1.0.0 | 初始发布 |

**语义化版本**：主版本号变更 = 破坏性 API 变更。
