# Swagger/OpenAPI Annotation Guidelines

> **用途**: Backend REST API 的 Swagger/OpenAPI 注解规范和决策记录
> **维护者**: Cortex CE Team
> **创建日期**: 2026-03-27

## 注解层次结构

| 层级 | 注解 | 必须 | 说明 |
|------|------|------|------|
| Controller 类 | `@Tag(name, description)` | ✅ | API 分组 |
| 端点方法 | `@Operation(summary, description)` | ✅ | 端点功能描述 |
| 端点方法 | `@ApiResponse(responseCode, description)` | ✅ | 所有可能的 HTTP 状态码 |
| 参数 | `@Parameter(description, required, example)` | ✅ | 路径/查询参数 |
| 请求体 | `@RequestBody(description)` | ✅ | 请求体描述 |
| Record 字段 | `@Schema(description, example)` | ✅ | record 类型的字段说明 |

## 决策记录

### Q1: Entity 类需要加 `@Schema` 吗？

**决策**: 不需要。

**理由**:
1. Entity 是 JPA 映射类，加 `@Schema` 会让职责混乱
2. Swagger 能通过反射自动推断字段名和类型
3. Entity 字段在 SDK 的 DTO 中已有对应说明
4. 如果需要详细文档，应创建独立的 API Response DTO

### Q2: `Map<String, Object>` 响应体需要创建 DTO 吗？

**决策**: 不需要，用 `@RequestBody(description=...)` 描述即可。

**理由**:
1. Swagger UI 会展示 JSON 结构示例
2. `Map<String, Object>` 提供了灵活性，不需要为每个端点创建 Request 类
3. SDK 开发者看端点描述 + 参数 example 就够了

### Q3: 已有 record 类型需要额外创建 @Schema 类吗？

**决策**: 不需要，直接在现有 record 字段上加 `@Schema`。

**示例**:
```java
public record ModeSwitchRequest(
    @Schema(description = "Mode ID to activate", example = "code", requiredMode = REQUIRED)
    String modeId
) {}
```

## 当前注解统计

> **统计时间**: 2026-10-02
> **统计方法**: 对 `backend/src/main/java/com/ablueforce/cortexce/controller/*.java`
> 先剥离 `//` 与 `/* */` 注释（`ViewerController` 中有被注释掉的 `/concepts` 映射），
> 再统计注解的**出现次数**（不是包含它的行数）。`@Schema` 拆成两列，因为
> `@Content(schema = @Schema(implementation = ...))` 指向响应类型，而直接写在
> record 字段上的 `@Schema(description = ...)` 才是本规范要求的那一种。
>
> **交叉校验**: `@Operation` 合计 67，与本仓库其他章节独立统计出的 67 个生效端点
> 数量一致，可作为该统计方法未漏计的旁证。

| Controller | @Operation | @Parameter | @Schema(字段) | @Schema(impl) | @ApiResponse |
|-----------|-----------|-----------|---------------|----------------|-------------|
| ContextController | 7 | 18 | 0 | 6 | 7 |
| CursorController | 6 | 4 | 7 | 8 | 13 |
| ExtractionController | 3 | 8 | 0 | 6 | 9 |
| HealthController | 3 | 0 | 3 | 0 | 4 |
| ImportController | 5 | 0 | 4 | 5 | 5 |
| IngestionController | 4 | 0 | 7 | 6 | 9 |
| LogsController | 2 | 1 | 3 | 0 | 3 |
| MemoryController | 7 | 4 | 11 | 7 | 16 |
| ModeController | 8 | 2 | 14 | 2 | 9 |
| SessionController | 3 | 2 | 0 | 10 | 8 |
| StreamController | 1 | 0 | 1 | 0 | 1 |
| TestController | 3 | 0 | 5 | 0 | 5 |
| ViewerController | 15 | 33 | 13 | 11 | 22 |
| **合计** | **67** | **72** | **68** | **61** | **111** |

> 本表此前标注为 2026-03-27 的统计，其中 `@Schema` 与 `@ApiResponse` 多列偏低
> （例如 `MemoryController` 的 `@Schema` 记为 0、实为 11+7；`ViewerController` 的
> `@ApiResponse` 记为 27、实为 22）。原表未说明统计方法，无法复现，故一并改为
> 可复现口径。

## 维护规则

1. **新增端点必须有完整的 Swagger 注解**（@Operation + @ApiResponse + @Parameter）
2. **新增 record 类型的字段必须加 `@Schema`**
3. **修改端点行为时同步更新 @Operation description**
4. **回归测试通过后才提交注解变更**
