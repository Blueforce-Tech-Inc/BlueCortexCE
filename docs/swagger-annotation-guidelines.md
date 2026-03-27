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

| Controller | @Operation | @Parameter | @Schema | @ApiResponse |
|-----------|-----------|-----------|---------|-------------|
| ContextController | 6 | 18 | 0 | 10 |
| CursorController | 6 | 4 | 0 | 16 |
| ExtractionController | 3 | 8 | 0 | 12 |
| HealthController | 3 | 0 | 2 | 5 |
| ImportController | 5 | 0 | 4 | 5 |
| IngestionController | 4 | 0 | 1 | 13 |
| LogsController | 2 | 1 | 0 | 4 |
| MemoryController | 7 | 4 | 0 | 18 |
| ModeController | 8 | 2 | 7 | 10 |
| SessionController | 3 | 2 | 0 | 11 |
| StreamController | 1 | 0 | 1 | 1 |
| TestController | 3 | 0 | 0 | 7 |
| ViewerController | 15 | 29 | 2 | 27 |

## 维护规则

1. **新增端点必须有完整的 Swagger 注解**（@Operation + @ApiResponse + @Parameter）
2. **新增 record 类型的字段必须加 `@Schema`**
3. **修改端点行为时同步更新 @Operation description**
4. **回归测试通过后才提交注解变更**
