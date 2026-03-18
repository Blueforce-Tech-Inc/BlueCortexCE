# Spring AI 集成实施进度

> **创建时间**: 2026-03-18
> **最后更新**: 2026-03-18 17:50

## 总体进度

| Phase | 模块 | 状态 | 说明 |
|-------|------|------|------|
| Phase 1 | cortex-mem-client | ✅ 完成 | DTO + CortexMemClient 接口 + REST 实现 |
| Phase 2 | cortex-mem-spring-ai | ✅ 完成 | Advisor + Capture + Retrieval + AOP |
| Phase 3 | cortex-mem-starter | ✅ 完成 | AutoConfig + @EnableCortexMem + HealthIndicator |
| Phase 4 | 构建验证 | ✅ 完成 | 3 模块 BUILD SUCCESS |
| Phase 5 | DTO 对齐后端 API | ✅ 完成 | Experience/QualityDist/Feedback 格式已修复 |
| Phase 6 | mvn install 到本地仓库 | ✅ 完成 | ~/.m2/repository/com/ablueforce/cortexce/ |
| Phase 7 | 更新规划文档状态 | ✅ 完成 | |
| Phase 8 | 单元测试 | ✅ 完成 | 51 tests, 3 modules |

## 后端 API 对齐验证

已验证 `MemoryController.java` 中 5 个端点全部存在并对齐：

| 客户端方法 | 后端端点 | 状态 |
|-----------|---------|------|
| `recordObservation()` | `POST /api/ingest/tool-use` | ✅ |
| `recordSessionEnd()` | `POST /api/ingest/session-end` | ✅ |
| `recordUserPrompt()` | `POST /api/ingest/user-prompt` | ✅ |
| `retrieveExperiences()` | `POST /api/memory/experiences` | ✅ |
| `buildICLPrompt()` | `POST /api/memory/icl-prompt` | ✅ |
| `triggerRefinement()` | `POST /api/memory/refine` | ✅ |
| `submitFeedback()` | `POST /api/memory/feedback` | ✅ |
| `getQualityDistribution()` | `GET /api/memory/quality-distribution` | ✅ |
| `startSession()` | `POST /api/session/start` | ✅ |
| `healthCheck()` | `GET /actuator/health` | ✅ |

## DTO 修复记录

| 修复项 | 修改前 | 修改后 |
|--------|--------|--------|
| Experience 字段 | 缺少 id/reuseCondition | 7 字段完整对齐后端 |
| Experience.createdAt | Instant timestamp | OffsetDateTime createdAt |
| ICLPromptResult.experienceCount | int | String + experienceCountAsInt() |
| QualityDistribution | 嵌套 {distribution, total} | 扁平 {project, high, medium, low, unknown} |
| submitFeedback | (experienceId, rating) | (observationId, feedbackType) |

## Maven 坐标

```xml
<!-- 只需要 Starter (包含 client + spring-ai) -->
<dependency>
    <groupId>com.ablueforce.cortexce</groupId>
    <artifactId>cortex-mem-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- 或者只用客户端 -->
<dependency>
    <groupId>com.ablueforce.cortexce</groupId>
    <artifactId>cortex-mem-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 文件清单 (20 源文件)

### cortex-mem-client (11 文件)
- `pom.xml`
- `CortexMemClient.java` — 统一接口
- `CortexMemClientImpl.java` — REST 实现 (RestClient + 重试)
- `config/CortexMemProperties.java` — 配置属性
- `dto/ObservationRequest.java`
- `dto/SessionEndRequest.java`
- `dto/UserPromptRequest.java`
- `dto/ExperienceRequest.java`
- `dto/ICLPromptRequest.java`
- `dto/Experience.java`
- `dto/ICLPromptResult.java`
- `dto/QualityDistribution.java`

### cortex-mem-spring-ai (9 文件)
- `pom.xml`
- `advisor/CortexMemoryAdvisor.java` — Spring AI 1.1 CallAdvisor + StreamAdvisor
- `advisor/CortexSessionContextBridgeAdvisor.java` — 当 CONVERSATION_ID 存在时自动 begin/end CortexSessionContext (2026-03-18)
- `aspect/CortexToolAspect.java` — @Tool AOP 自动捕获
- `context/CortexSessionContext.java` — ThreadLocal 会话上下文
- `observation/ObservationCaptureService.java` — 接口
- `observation/DefaultObservationCaptureService.java` — 实现
- `retrieval/MemoryRetrievalService.java` — 接口
- `retrieval/DefaultMemoryRetrievalService.java` — 实现

### cortex-mem-starter (5 文件)
- `pom.xml`
- `autoconfigure/CortexMemAutoConfiguration.java` — 条件化 Bean 注册
- `autoconfigure/EnableCortexMem.java` — @EnableCortexMem 注解
- `autoconfigure/CortexMemHealthIndicator.java` — Actuator 健康检查
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 关键设计决策

1. **Spring AI 1.1 新 API**: 使用 `CallAdvisor`/`StreamAdvisor` + `ChatClientRequest` (非旧版 `CallAroundAdvisor`/`AdvisedRequest`)
2. **Fire-and-forget 捕获**: 所有捕获操作失败只记日志不抛异常，不阻塞 AI 管道
3. **条件化自动配置**: Spring AI / AOP / Actuator classpath 检测，按需激活
4. **重试机制**: 可配置的重试次数和退避时间
5. **降级策略**: 检索失败返回空列表，ICL 返回空 prompt

## 单元测试覆盖 (Phase 8)

| 模块 | 测试类 | 用例数 | 说明 |
|------|--------|--------|------|
| cortex-mem-client | DtoTest | 10 | DTO toWireFormat、Builder、experienceCountAsInt |
| cortex-mem-client | CortexMemClientImplTest | 13 | MockWebServer 测试所有 REST 调用 |
| cortex-mem-spring-ai | CortexSessionContextTest | 5 | ThreadLocal begin/end/isActive |
| cortex-mem-spring-ai | DefaultObservationCaptureServiceTest | 4 | Mock 委托验证 |
| cortex-mem-spring-ai | DefaultMemoryRetrievalServiceTest | 4 | Mock 委托验证 |
| cortex-mem-spring-ai | CortexMemoryAdvisorTest | 6 | ICL 注入、空/异常降级 |
| cortex-mem-spring-ai | CortexToolAspectTest | 2 | @SpringBootTest AOP 集成 |
| cortex-mem-starter | CortexMemHealthIndicatorTest | 3 | up/down/exception |
| cortex-mem-starter | CortexMemAutoConfigurationTest | 4 | 条件化 Bean 注册 |

**总计**: 51 个测试，全部通过 (`mvn clean test`)

## E2E 验证 (Phase 9) — 2026-03-18

| 端点 | 方法 | 状态 |
|------|------|------|
| `/actuator/health` | GET | ✅ |
| `/demo/projects` | GET | ✅ |
| `/memory/quality?project=/` | GET | ✅ |
| `/memory/experiences?task=...` | GET | ✅ |
| `/memory/icl?task=...` | GET | ✅ |
| `/memory/refine?project=/` | GET | ✅ |
| `/demo/tool?path=...&project=...` | GET | ✅ |
| `/demo/session/lifecycle?project=...` | POST | ✅ |
| `/chat?message=...&project=...` | GET | ✅ |

**E2E 结果**: 10/10 通过 (`./e2e/run-e2e.sh`)

**关键修复**: ToolsController 自调用导致 AOP 不生效 → 抽 `FileReadTool` 独立 Bean。

## Phase 10: Demo 增强 — 2026-03-18

| 能力 | 实现 |
|------|------|
| 项目配置 | `demo.projects` + `cortex.mem.project-path` |
| 编程切换 | `?project=` 或 `CortexSessionContext.begin(sessionId, projectPath)` |
| 会话生命周期 | `POST /demo/session/start`, `prompt`, `tool`, `end`, `lifecycle` |
| 多种捕获 | user-prompt, tool-use, session-end |
| startSession | `CortexMemClient.startSession()` + `SessionStartRequest` |
