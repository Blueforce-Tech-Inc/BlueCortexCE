# ScreenPulse

> **English Version**: [README.md](README.md)

**ScreenPulse** 是一款 macOS 菜单栏应用，通过 macOS Accessibility API 捕获前台窗口内容，并将观察数据流式传输到 Cortex CE 记忆系统。

```
┌─────────────────────────────────────────────────────────────────┐
│                        ScreenPulse                               │
│                                                                  │
│   [👁️ 菜单栏图标]  ←  LSUIElement 应用 (无 Dock 图标)          │
│                                                                  │
│   捕获对象:  Safari, VS Code, Slack, Terminal...                │
│                  ↓  POST /api/ingest/observation               │
│            ┌─────────────────────┐                              │
│            │    Cortex CE        │                              │
│            │  (记忆系统)          │                              │
│            └─────────────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
```

## 目录

- [功能特性](#功能特性)
- [架构设计](#架构设计)
- [前置条件](#前置条件)
- [快速开始](#快速开始)
  - [1. 启动 Cortex CE 后端](#1-启动-cortex-ce-后端)
  - [2. 编译 ScreenPulse](#2-编译-screenpulse)
  - [3. 授予辅助功能权限](#3-授予辅助功能权限)
  - [4. 运行 ScreenPulse](#4-运行-screenpulse)
- [开发指南](#开发指南)
  - [项目结构](#项目结构)
  - [编译](#编译)
  - [调试](#调试)
  - [运行测试](#运行测试)
- [配置说明](#配置说明)
  - [端点配置](#端点配置)
  - [忽略的 Bundle ID](#忽略的-bundle-id)
  - [捕获间隔](#捕获间隔)
- [控制台窗口使用指南](#控制台窗口使用指南)
- [故障排除](#故障排除)
  - [辅助功能权限被拒绝](#辅助功能权限被拒绝)
  - [无法连接到后端](#无法连接到后端)
  - [应用未出现在 Dock](#应用未出现在-dock)
- [安全与隐私](#安全与隐私)
- [路线图](#路线图)
- [贡献指南](#贡献指南)
- [许可证](#许可证)

---

## 功能特性

| 功能 | 描述 |
|------|------|
| **菜单栏应用** | 作为 `LSUIElement` 运行（无 Dock 图标），通过菜单栏访问 |
| **定时轮询** | 每 5 秒捕获一次前台窗口 |
| **手动捕获** | 点击 "Capture Now" 立即捕获 |
| **本地日志** | 日志存储在 `~/Library/Application Support/ScreenPulse/logs/` |
| **API 集成** | POST 到 Cortex CE `/api/ingest/observation` |
| **三层数据模型** | AX 快照 → 语义字段 → Markdown 叙述 |
| **隐私保护** | 过滤密码，跳过安全文本字段 |
| **忽略列表** | 可配置的 Bundle ID 忽略列表 |

### V1.0 功能

- 菜单栏状态指示器（眼睛图标）
- 带事件列表和详情视图的控制台窗口
- 5 秒间隔轮询捕获
- 本地文件日志
- HTTP POST 到记忆系统
- 内存缓存（200 条事件，滚动淘汰）

### V1.1（计划中）

- AXObserver 事件驱动捕获（无轮询）
- App 特化解析器（浏览器 URL 提取、IDE 文件路径等）
- API 调用失败重试队列

---

## 架构设计

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ScreenPulse 架构                             │
└─────────────────────────────────────────────────────────────────────┘

  ┌──────────────┐      ┌──────────────────┐      ┌─────────────────┐
  │  菜单栏       │ ──── │  AppDelegate     │ ──── │ ScreenCapture   │
  │  (NSStatusItem)│      │  (NSApplication │      │ Manager         │
  └──────────────┘      └──────────────────┘      │                 │
                                                    │  ┌───────────┐ │
  ┌──────────────┐      ┌──────────────────┐      │  │ Identity  │ │
  │  控制台       │ ──── │  ContentView     │ ──── │  │ Manager   │ │
  │  窗口          │      │  (SwiftUI)       │      │  └───────────┘ │
  │  (NSWindow)   │      └──────────────────┘      │                 │
                                                    │  ┌───────────┐ │
                                                    │  │Observation│ │
                                                    │  │Payload    │ │
                                                    │  └───────────┘ │
                                                    └───────┬─────────┘
                                                            │
                          ┌─────────────────────────────────┘
                          ↓
  ┌─────────────────────────────────────┐
  │         Cortex CE 后端                │
  │   POST /api/ingest/observation       │
  │   (localhost:37777)                  │
  └─────────────────────────────────────┘
```

### 数据流

```
前台应用窗口
        ↓ (AXUIElement API)
ScreenCaptureManager.performCapture()
        ↓
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Layer 1: AXNode │ →  │ Layer 2:        │ →  │ Layer 3:        │
│ (原始 AX 树)     │    │ SemanticFields  │    │ Markdown        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                    ↓
                                         ┌─────────────────┐
                                         │  narrative      │
                                         │  (LLM 上下文)    │
                                         └─────────────────┘
                                                    ↓
                                         ┌─────────────────┐
                                         │ extractedData   │
                                         │ (结构化数据)     │
                                         └─────────────────┘
                                                    ↓
                                         POST 到 Cortex CE
```

---

## 前置条件

| 要求 | 版本 | 说明 |
|------|------|------|
| macOS | 13.0+ (Ventura) | SwiftUI 菜单栏 API 需要 |
| Swift | 5.9+ | Xcode 15+ 自带 |
| Xcode | 15.0+ | 可选（swift build 也可） |
| Cortex CE 后端 | 运行中 | 默认: `localhost:37777` |

### 所需权限

- **辅助功能**: 需要读取其他应用窗口内容
- **网络**: 需要 POST 观察数据到记忆系统

---

## 快速开始

### 1. 启动 Cortex CE 后端

首先确保 Cortex CE 后端正在运行：

```bash
# 进入后端目录
cd /path/to/BlueCortexCE/backend

# 启动后端（如果尚未运行）
./mvnw spring-boot:run

# 或使用打包的 JAR
java -jar target/claude-mem-java-*.jar
```

后端应在 `http://localhost:37777` 可用。

**验证后端运行状态:**
```bash
curl http://localhost:37777/actuator/health
# 预期: {"status":"UP"}
```

### 2. 编译 ScreenPulse

```bash
# 进入 ScreenPulse 目录
cd /path/to/BlueCortexCE/ScreenPulse

# 编译项目
swift build

# 发布版编译（优化）
swift build -c release
```

**输出:** `.build/release/ScreenPulse` (发布版)

### 3. 授予辅助功能权限

ScreenPulse 需要辅助功能权限来读取窗口内容。

1. **首次启动**会自动弹出提示，或
2. 手动导航: **系统设置 → 隐私与安全性 → 辅助功能**
3. 启用 **ScreenPulse**（或如果从命令行运行则启用 **终端**）

> **重要:** 如果通过 Xcode 或终端运行，确保该应用（Xcode/终端）已在辅助功能中启用。

### 4. 运行 ScreenPulse

#### 方式 A: 从命令行运行（开发）

```bash
# 调试版
swift build && swift run

# 或运行编译后的二进制文件
.build/debug/ScreenPulse
# 或
.build/release/ScreenPulse
```

#### 方式 B: 打包为 .app 并运行

```bash
# 创建 app 包结构
APP=ScreenPulse.app
mkdir -p ${APP}/Contents/MacOS
mkdir -p ${APP}/Contents/Resources
cp .build/release/ScreenPulse ${APP}/Contents/MacOS/
cp Info.plist ${APP}/Contents/

# 签名（开发用 ad-hoc）
codesign --sign - --entitlements entitlements.plist --force --deep ${APP}

# 验证签名
codesign --verify --verbose ${APP}

# 运行
open ${APP}
```

#### 方式 C: 在 Xcode 中打开

```bash
# 生成 Xcode 项目（可选）
swift package generate-xcodeproj

# 在 Xcode 中打开
open ScreenPulse.xcodeproj
```

然后按 **Cmd+R** 编译并运行。

---

## 开发指南

### 项目结构

```
ScreenPulse/
├── Package.swift                    # Swift 包管理器配置
├── Info.plist                       # 应用元数据 (LSUIElement=true)
├── entitlements.plist               # 签名权限
├── README.md                        # 英文说明文档
├── README-zh-CN.md                  # 中文说明文档
└── Sources/ScreenPulse/
    ├── App.swift                    # 入口点（极简）
    ├── AppDelegate.swift            # @main, 菜单栏, 窗口设置
    ├── ContentView.swift            # SwiftUI 控制台 UI
    ├── ScreenCaptureManager.swift    # 核心捕获逻辑
    ├── IdentityManager.swift         # 设备/会话/用户 ID
    └── ObservationPayload.swift      # API 负载 + 发送器
```

### 编译

| 命令 | 描述 |
|------|------|
| `swift build` | 调试版编译 |
| `swift build -c release` | 发布版编译 |
| `swift build --verbose` | 详细输出 |
| `swift build -Xswiftc -warnings-as-errors` | 将警告视为错误 |

### 调试

#### 使用 Xcode 调试

```bash
# 生成 Xcode 项目
swift package generate-xcodeproj

# 在 Xcode 中打开
open ScreenPulse.xcodeproj
```

在 Xcode 中:
1. 选择 **Product → Scheme → Edit Scheme**
2. 设置 **Run → Info → Build Configuration** 为 **Debug**
3. 按 **Cmd+R** 运行

#### 使用 VS Code 调试

安装 **CodeLLDB** 扩展，然后创建 `.vscode/launch.json`:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "name": "Debug ScreenPulse",
            "type": "lldb",
            "request": "launch",
            "program": "${workspaceFolder}/.build/debug/ScreenPulse",
            "cwd": "${workspaceFolder}"
        }
    ]
}
```

#### 打印调试信息

应用会打印到 stdout/stderr。查看日志:

```bash
# 运行并显示输出
.build/debug/ScreenPulse

# 或重定向到文件
.build/debug/ScreenPulse 2>&1 | tee screenpulse.log
```

代码中的关键调试打印:
- `ScreenCaptureManager.swift:306` - "ScreenPulse: Successfully posted observation"
- `ScreenCaptureManager.swift:308` - "ScreenPulse: Failed to post observation: \(error)"

#### 运行时调试

控制台窗口显示:
- 捕获事件列表（左侧面板）
- 事件详情（右侧面板）
- 状态消息（左上角）
- 端点响应（通过打印语句）

### 运行测试

**注意:** V1.0 不包含单元测试。添加测试:

```bash
# 创建测试目标（添加到 Package.swift）
swift package init --type test

# 运行测试
swift test
```

---

## 配置说明

### 端点配置

默认端点是 `http://localhost:37777/api/ingest/observation`。

**修改方法:**
1. 打开 ScreenPulse 控制台（菜单栏 → "Open Console"）
2. 编辑 **Memory System Endpoint** 文本框
3. 点击 **Save**

端点会持久化到 `UserDefaults`（键: `screenpulse.endpoint`）。

### 忽略的 Bundle ID

默认忽略的应用:
- `com.agilebits.onepassword7` (1Password 7)
- `com.apple.systempreferences` (系统设置)
- `com.apple.keychainaccess` (钥匙串访问)

**添加更多:**
1. 打开控制台 → **Ignored Bundle IDs**
2. 输入 Bundle ID（例如 `com.apple.Safari`）
3. 点击 **Add**

**查找应用的 Bundle ID:**
```bash
osascript -e 'id of app "Safari"'  # 返回: com.apple.Safari
```

### 捕获间隔

当前固定为 **5 秒**（在 `ScreenCaptureManager.swift:136` 硬编码）。

修改方法，编辑:
```swift
// ScreenCaptureManager.swift, 第 136 行
let interval: TimeInterval = 5.0  # 改成想要的间隔
```

---

## 控制台窗口使用指南

```
┌─────────────────────────────────────────────────────────────────────┐
│  [●] Idle                                        [眼睛图标]          │
├────────────────────────┬────────────────────────────────────────────┤
│  状态                   │  事件详情                                   │
│  ─────────             │  ───────────────                          │
│  [●] 运行中            │  （从左侧选择事件查看）                      │
│                        │                                            │
│  控制                   │                                            │
│  ─────────             │                                            │
│  [✓] 启用捕获          │                                            │
│  [立即捕获]            │                                            │
│                        │                                            │
│  端点                   │                                            │
│  ─────────             │                                            │
│  [http://localhost:3..│                                            │
│  [保存]                │                                            │
│                        │                                            │
│  忽略的 Bundle ID      │                                            │
│  ─────────             │                                            │
│  + com.apple.Safari  │                                            │
│    com.agilebits.onep..│                                           │
│                        │                                            │
│  捕获事件               │                                            │
│  ─────────             │                                            │
│  🟢 Safari - "Apple"   │                                            │
│    VS Code - "main.sw" │                                            │
│    终端 - "$ ls"        │                                            │
└────────────────────────┴────────────────────────────────────────────┘
```

| 元素 | 描述 |
|------|------|
| **状态指示器** | 绿色=捕获中，灰色=空闲 |
| **启用捕获** | 捕获开关 |
| **立即捕获** | 手动触发一次捕获 |
| **端点** | 记忆系统 URL（可编辑） |
| **忽略的 Bundle ID** | 捕获时跳过的应用 |
| **捕获事件** | 已捕获事件列表 |
| **事件详情** | 选中事件的完整文本 |

---

## 故障排除

### 辅助功能权限被拒绝

**症状:** 状态显示 "⚠️ 权限未授权"，捕获不工作

**解决方法:**
1. 进入 **系统设置 → 隐私与安全性 → 辅助功能**
2. 确保 ScreenPulse 已 **启用**
3. 如果通过终端运行，确保 **终端**（或你的终端应用）已启用
4. 授予权限后重启 ScreenPulse

### 无法连接到后端

**症状:** 日志中显示 "Failed to post observation"

**解决方法:**
1. 验证 Cortex CE 正在运行:
   ```bash
   curl http://localhost:37777/actuator/health
   ```
2. 检查控制台窗口中的端点 URL
3. 如果从不同来源访问，检查后端 CORS 设置

### 应用未出现在 Dock

**这是预期行为** - ScreenPulse 使用 `LSUIElement=true` 隐藏 Dock 图标。

**退出方法:**
- 菜单栏图标 → "Quit ScreenPulse"
- 或当控制台窗口聚焦时按 **Cmd+Q**

### 未捕获到任何事件

**可能原因:**
1. 前台窗口没有文本（某些应用没有可访问的文本）
2. 应用在忽略列表中（检查忽略的 Bundle ID）
3. 窗口没有焦点文本字段

**测试:**
1. 打开 Safari 并导航到某个网页
2. 确保 "启用捕获" 是开启状态
3. 等待 5 秒进行捕获
4. 在列表中选择事件查看详情

### 编译错误

**错误: `cannot find 'AXIsProcessTrustedWithOptions' in scope`**

确保使用 Accessibility API 的文件顶部有 `import ApplicationServices`。

**错误: `expressions are not allowed at top level`**

确保正确使用 `@main` 属性，且没有游离的文件级表达式。

---

## 安全与隐私

### ScreenPulse 捕获什么

- 前台窗口可访问性树中的**文本内容**
- **窗口标题**
- **应用名称和 Bundle ID**
- 捕获的**时间戳**

### ScreenPulse 不捕获什么

- **密码**: `AXSecureTextField` 元素被明确跳过
- **密码类字段**: 标题中包含 "password" 的字段被跳过
- **图片/媒体**: 仅捕获文本内容
- **剪贴板**: 不读取剪贴板内容

### 数据存储

- **本地日志**: `~/Library/Application Support/ScreenPulse/logs/`
  - 每日轮转日志文件（`yyyy-MM-dd.log`）
  - 格式: `[HH:mm:ss] [bundleId] [windowTitle]\n[text]\n---\n`
- **内存缓存**: 最多 200 条事件，不持久化

### 网络安全

- 默认端点是 `localhost`（仅本地）
- 默认不启用 HTTPS（生产环境配置反向代理）
- API 端点无认证（假设本地可信）

---

## 路线图

| 版本 | 里程碑 | 状态 |
|------|--------|------|
| V1.0 | 定时轮询、菜单栏、POST 到 Cortex CE | **已完成** |
| V1.1 | AXObserver 事件驱动、App 特化解析器 | 计划中 |
| V2.0 | 屏幕截图、图片描述 | 计划中 |

---

## 贡献指南

欢迎贡献！请：

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开 Pull Request

### 开发工作流程

```bash
# 克隆你的 fork
git clone https://github.com/YOUR_USERNAME/BlueCortexCE.git
cd BlueCortexCE/ScreenPulse

# 创建功能分支
git checkout -b feature/my-feature

# 编译和测试
swift build

# 提交
git add .
git commit -m "Add feature: description"

# 推送并创建 PR
git push origin feature/my-feature
```

---

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](../../LICENSE) 文件。

---

## 相关文档

| 文档 | 描述 |
|------|------|
| [`docs/drafts/screenpulse-implementation-plan.md`](docs/drafts/screenpulse-implementation-plan.md) | 完整实现规范 |
| [`docs/drafts/screenpulse-implementation-progress.md`](docs/drafts/screenpulse-implementation-progress.md) | 实现进度追踪 |
| [Cortex CE README](../../README.md) | 后端记忆系统文档 |

---

## 支持

如有问题或疑问：
1. 查看上面的 [故障排除](#故障排除)
2. 搜索 [现有 issues](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)
3. 打开新 issue，包含：
   - macOS 版本
   - Swift 版本 (`swift --version`)
   - ScreenPulse 编译输出
   - 重现步骤
