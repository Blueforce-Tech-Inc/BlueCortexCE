# ScreenPulse macOS 应用实现规划

> **项目名称**: ScreenPulse
> **版本**: 1.0.0
> **创建日期**: 2026-03-31
> **状态**: 待评审

---

## 一、项目概述

### 1.1 核心价值

ScreenPulse 是 Cortex CE 记忆系统的"屏幕感知模块"，负责捕获用户前台窗口内容，为记忆系统提供"用户当前在看什么"的原始素材。

**典型场景**:
1. 用户在 Safari/Chrome 中阅读文章
2. 用户在 VS Code 中编写代码
3. 用户在 Slack 中查看消息
4. ScreenPulse 捕获这些内容，POST 到 Cortex CE
5. Cortex CE 经 LLM 处理后存储
6. 用户在 Claude Code 中说"帮我总结刚才看的那篇"，记忆系统注入上下文

### 1.2 技术选型

| 组件 | 技术选择 | 理由 |
|------|----------|------|
| 平台 | macOS 13+ | SwiftUI/系统图标支持好 |
| 语言 | Swift 5.9 | 现代语法、良好生态 |
| UI | SwiftUI | 声明式、快速迭代 |
| 屏幕读取 | Accessibility API (AXUIElement) | macOS 标准、权限可控 |
| 构建工具 | Swift Package Manager | 命令行友好、CI 集成方便 |
| 签名 | Ad-hoc (无开发者账号) | 简化测试流程 |

### 1.3 与 Cortex CE 的 API 集成

**发现结果**: Cortex CE 使用 `content_session_id` 作为 API 字段名（snake_case），`session_id` 作为别名。核心端点 `/api/ingest/observation` 的 `ObservationCreateRequest` 接受以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `content_session_id` (或 `session_id`) | string | 必填，会话标识 |
| `project_path` (或 `cwd`) | string | 必填，项目路径 |
| `source` | string | 来源标识 |
| `title` | string | 标题 |
| `narrative` (或 `content`) | string | 叙述内容 |
| `type` | string | 类型 |
| `extractedData` | map | 结构化提取数据 |

**集成决策**:
- ScreenPulse **复用**现有的 `/api/ingest/observation` 端点，无需新建后端端点
- Payload 字段映射：
  - `content_session_id` ← IdentityManager.sessionId
  - `project_path` ← `"screenpulse"` (固定值，表示来源为屏幕捕获)
  - `source` ← `"screenpulse-macos"`
  - `title` ← `"[appName] windowTitle"`
  - `narrative` ← 捕获的完整文本
  - `type` ← `"screen-capture"`
  - `extractedData` ← 完整的三层结构化数据（axSnapshot、semantic、meta 等）
- 字段名遵循 Cortex CE 的 snake_case 惯例
- 端点默认 `http://localhost:37777/api/ingest/observation`

---

## 二、功能规格

### 2.1 MVP 功能 (V1.0)

#### 2.1.1 菜单栏常驻

- 使用 `NSStatusBar.system.statusItem(withLength:)` 创建
- SF Symbol 图标: `eye.circle.fill` (运行中) / `eye.slash.circle` (暂停)
- 菜单项:
  - "Open Console" (Cmd+O) - 打开主控制台窗口
  - "Start Capture" / "Pause Capture" (Cmd+S) - 切换捕获状态
  - 分隔线
  - "Quit ScreenPulse" (Cmd+Q) - 退出应用

#### 2.1.2 控制台窗口

- **窗口属性**: 900x580, 可调整大小, 标题 "ScreenPulse Console"
- **LSUIElement=true**: 不出现在 Dock
- **左侧面板** (340pt):
  - 状态指示器: 绿色圆点(运行中) / 灰色圆点(暂停)
  - 状态文字: "Idle" / "Running" / "⚠️ 权限未授权"
  - Toggle 开关: "Enable Capture"
  - 按钮: "Capture Now" (手动抓取一次)
  - 记忆系统端点配置: TextField + "Save" 按钮
  - 忽略 Bundle ID 列表: 可添加/删除
  - 捕获事件列表: List with selection
    - 每行: App图标 + App名称 + 时间 + 窗口标题 + 预览文本
    - 选择后右侧显示详情
- **右侧面板** (400pt+):
  - 选中事件的完整文本 (TextEditor, monospaced)
  - 无选中时显示 "← 从左侧选择一条记录查看完整文本"

#### 2.1.3 屏幕内容捕获 (定时轮询模式)

- **触发间隔**: 5 秒 (开发期可调)
- **触发条件**:
  - 定时触发 (唯一方式，V1.0)
  - 手动触发: 用户点击 "Capture Now" 按钮
- **注意**: App 切换时立即触发是 V1.1 AXObserver 的功能，V1.0 只有定时 + 手动
- **捕获内容**:
  - 前台 App 的 bundleId、名称
  - 当前焦点窗口的标题
  - 窗口内所有可访问文本 (递归遍历 AX 树)
- **V1.0 App 分类**: 所有 App 归类为 `other` (无 App 特化解析器)
- **黑名单过滤**: 默认忽略 1Password、系统偏好设置、Keychain Access
- **密码保护**: 跳过 `AXSecureTextField` 和标题含 "password" 的字段

#### 2.1.4 本地存储

- **日志文件**: `~/Library/Application Support/ScreenPulse/logs/yyyy-MM-dd.log`
- **格式**: `[HH:mm:ss] [bundleId] [windowTitle]\n[text]\n---\n`
- **内存缓存**: 最多 200 条事件，滚动淘汰

#### 2.1.5 发送到记忆系统

- **端点**: 可配置，默认为 `http://localhost:37777/api/ingest/observation`
- **方法**: POST
- **Content-Type**: application/json
- **Payload**: 使用三层数据模型（`schema_version: "2.0"`），V1.0 仅使用 Layer 3 Markdown 作为 `narrative`，`axSnapshot` 和 `semantic` 中仅填充基础字段。

> 完整 Payload 结构见 [4.4 API Payload](#44-api-payload-v12) 节。

### 2.2 V1.1 功能 (迭代计划)

#### 2.2.1 AXObserver 事件驱动

- 替换定时轮询为 `AXObserver` 订阅
- 监听: `kAXFocusedWindowChangedNotification`, `kAXMainWindowChangedNotification`, `kAXTitleChangedNotification`, `kAXFocusedUIElementChangedNotification`, `kAXValueChangedNotification`
- Debounce: 1.5s (防止输入时过频触发)
- 保留定时 fallback (每 30s) 防止遗漏

#### 2.2.2 App 特化解析器

| App 类型 | Parser | 特殊处理 |
|----------|--------|----------|
| 浏览器 | BrowserParser | 提取 URL (从地址栏 AXTextField) |
| IDE | IDEParser | 提取当前文件路径 (从窗口标题解析) |
| 终端 | TerminalParser | 提取命令输出 |
| 通讯 | CommunicationParser | 提取频道名称、消息列表 |

#### 2.2.3 可靠性增强

- 本地失败重试队列 (最多 100 条)
- 网络断开时缓存，连接恢复后批量发送
- 可配置的批量发送间隔

### 2.3 V2.0 功能 (远期规划)

- 截图功能 (用于无障碍 API 无法读取的场景)
- 图像描述 (本地 LLM 或调用记忆系统的 OCR)
- Menu Bar Extra (macOS 14+ 新 API，更原生)

---

## 三、项目结构

### 3.1 目录结构

```
ScreenPulse/
├── Package.swift                    # Swift PM 配置
├── Info.plist                       # App 元信息
├── entitlements.plist               # 签名权限
└── Sources/
    └── ScreenPulse/
        ├── App.swift                # @main 入口
        ├── AppDelegate.swift        # 菜单栏管理
        ├── ContentView.swift        # 控制台 UI
        ├── ScreenCaptureManager.swift # 核心捕获逻辑
        ├── IdentityManager.swift    # 设备/会话 ID 管理 (V1.0)
        ├── ObservationPayload.swift  # API Payload 结构 + 发送器 (V1.0)
        ├── AXEventWatcher.swift     # AXObserver 事件订阅 (V1.1)
        └── AppParsers.swift         # App 特化解析器 (V1.1)
```

### 3.2 文件依赖关系

**V1.0 (M1-M4)**
```
App.swift
├── AppDelegate.swift
│   └── ScreenCaptureManager.swift
│       ├── IdentityManager.swift
│       └── ObservationPayload.swift (含 PayloadSender)
└── ContentView.swift
    └── ScreenCaptureManager.swift (通过 @EnvironmentObject)
```

**V1.1 (M5-M7) - 新增依赖**
```
ScreenCaptureManager.swift
├── AXEventWatcher.swift (V1.1)
├── AppParsers.swift (V1.1)
└── (ObservationPayload.swift 已在 V1.0 中)
```

---

## 四、详细设计

### 4.1 ScreenCaptureManager (核心)

```swift
final class ScreenCaptureManager: ObservableObject {
    static let shared = ScreenCaptureManager()

    // Published 状态 (UI 绑定)
    @Published var isCapturing: Bool = false
    @Published var events: [CaptureEvent] = []
    @Published var selectedEvent: CaptureEvent?
    @Published var ignoreBundleIds: Set<String> = [
        "com.agilebits.onepassword7",
        "com.apple.systempreferences",
        "com.apple.keychainaccess"
    ]
    @Published var newIgnoreInput: String = ""
    @Published var statusMessage: String = "Idle"
    @Published var endpointInput: String = "http://localhost:37777/api/ingest/observation"

    // 内部状态
    private let systemElement = AXUIElementCreateSystemWide()
    private let captureQueue = DispatchQueue(label: "screenpulse.capture", qos: .background)
    private var timer: DispatchSourceTimer?
    private let logDir: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let url = base.appendingPathComponent("ScreenPulse/logs", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()

    // MARK: - 控制接口
    func toggleCapture()
    func startCapturing()
    func stopCapturing()
    func captureOnceManually()
    func addIgnoreBundle()
    func removeIgnoreBundle(_ id: String)

    // MARK: - 内部
    private func captureOnce()
    private func collectText(element: AXUIElement, depth: Int, into: inout [String])
    private func writeLog(_ event: CaptureEvent)
    private func postToMemorySystem(_ event: CaptureEvent)
}
```

### 4.2 CaptureEvent 数据模型

```swift
// V1.0 实际只用到: .manual, .timer
// V1.1 扩展: .appSwitch, .windowChange, .titleChange, .contentChange
enum CaptureTrigger: String {
    case manual
    case timer
    case appSwitch       // V1.1
    case windowChange    // V1.1
    case titleChange     // V1.1
    case contentChange   // V1.1
}

enum AppCategory: String, Codable {
    case browser  // V1.1
    case ide      // V1.1
    case terminal // V1.1
    case communication // V1.1
    case editor   // V1.1
    case other   // V1.0 (通用兜底)
}
```

### 4.3 三层数据模型

macOS Accessibility API 返回的是一棵**有类型的属性树**（AXUIElement Tree），每个节点有 role（类型）、attributes（属性集合）和 children（子节点）。直接拼为平铺文本会丢失结构语义、交互状态和空间关系，且会污染下游向量检索。正确做法是分层处理：

```
┌─────────────────────────────────────────────────────┐
│  Layer 1: 原始 AX 快照（结构化 JSON）               │
│  用途：可溯源、可重新解析、供后端做任意处理          │
├─────────────────────────────────────────────────────┤
│  Layer 2: 语义字段提取（key-value 结构化）          │
│  用途：精准索引、过滤查询、MCP 工具精准取字段        │
├─────────────────────────────────────────────────────┤
│  Layer 3: 语义文本表示（Markdown/纯文本）           │
│  用途：LLM embedding、RAG 检索、直接注入 context    │
└─────────────────────────────────────────────────────┘
```

**关键设计决策**：
- `selectedText`（用户选中文字）是**信噪比最高的信号**——用户主动选中某段文字，几乎确定他马上要对这段内容提问，比整页文本重要 10 倍
- 密码字段（`AXSecureTextField`）不提取 value
- 纯 UI 噪音节点（"后退"、"前进"、"分享"等按钮）在 Layer 1 保留但 Layer 2/3 过滤

#### Layer 1: AXNode 结构化快照

```swift
struct AXNode: Codable {
    let role: String              // AXStaticText, AXHeading, AXLink, AXTextField, ...
    let title: String?
    let value: String?
    let description: String?
    let url: String?              // AXLink 等有 URL 的元素
    let level: Int?               // AXHeading 的层级 (1-6)
    let position: CGPointCodable?
    let size: CGSizeCodable?
    let isFocused: Bool
    let isSelected: Bool
    let isEnabled: Bool
    var children: [AXNode]

    // 只保留有内容意义的 role（过滤纯 UI 噪音）
    static let meaningfulRoles: Set<String> = [
        "AXStaticText", "AXHeading", "AXLink", "AXTextField",
        "AXTextArea", "AXButton", "AXMenuBarItem", "AXMenuItem",
        "AXImage", "AXWebArea", "AXGroup", "AXList", "AXListItem",
        "AXTable", "AXRow", "AXCell", "AXTabGroup", "AXTab"
    ]
}

struct CGPointCodable: Codable {
    let x: Double; let y: Double
    init(_ p: CGPoint) { x = p.x; y = p.y }
}
struct CGSizeCodable: Codable {
    let width: Double; let height: Double
    init(_ s: CGSize) { width = s.width; height = s.height }
}
```

#### Layer 2: SemanticFields 语义字段提取

```swift
struct SemanticFields: Codable {
    // 通用
    var windowTitle: String = ""
    var selectedText: String? = nil          // 最强意图信号
    var focusedElementRole: String? = nil
    var focusedElementValue: String? = nil

    // 浏览器专属
    var url: String? = nil
    var pageTitle: String? = nil
    var headings: [HeadingItem] = []
    var links: [LinkItem] = []
    var visibleTextBlocks: [TextBlock] = []

    // IDE 专属
    var filePath: String? = nil
    var language: String? = nil              // 从扩展名推断
    var codeSnippet: String? = nil           // 聚焦代码片段（前 2000 字符）
    var terminalOutput: String? = nil

    // 通讯工具专属
    var channelName: String? = nil
    var recentMessages: [MessageItem] = []

    struct HeadingItem: Codable { let level: Int; let text: String }
    struct LinkItem: Codable { let text: String; let url: String? }
    struct TextBlock: Codable { let text: String; let role: String }
    struct MessageItem: Codable { let sender: String?; let text: String }
}
```

#### Layer 3: Markdown 渲染（供 LLM 直接消费）

将 SemanticFields 渲染为结构化 Markdown，保留语义但去掉 UI 噪音。`selectedText` 以 blockquote 高亮显示。

```swift
func renderMarkdown(from semantic: SemanticFields) -> String {
    var lines: [String] = []

    // URL 和页面标题（浏览器）
    if let url = semantic.url {
        lines.append("[\(semantic.pageTitle ?? url))](\(url))")
    }

    // 标题层级
    for h in semantic.headings.sorted(by: { $0.level < $1.level }) {
        let prefix = String(repeating: "#", count: h.level)
        lines.append("\(prefix) \(h.text)")
    }

    // 选中文本（最强意图信号）
    if let selected = semantic.selectedText, !selected.isEmpty {
        lines.append("> **Selected:** \(selected)")
    }

    // 可见文本块
    for block in semantic.visibleTextBlocks {
        lines.append(block.text)
    }

    // 文件路径（IDE）
    if let fp = semantic.filePath {
        lines.append("`\(fp)`")
    }

    // 终端输出
    if let term = semantic.terminalOutput {
        lines.append("```\n\(term)\n```")
    }

    return lines.joined(separator: "\n\n")
}
```

#### ParsedContext 输出分层结构

```swift
struct ParsedContext {
    var category: AppCategory = .other
    var axSnapshot: AXNode?         // Layer 1：结构化 AX 树
    var semantic: SemanticFields    // Layer 2：语义字段
    // Layer 3 的 markdown 由 renderMarkdown() 在发送前生成
}
```

### 4.4 API Payload (V1.2)

**JSON 编码策略**: Swift struct 字段为 camelCase，API wire format 使用 snake_case。编码时必须设置:
```swift
let encoder = JSONEncoder()
encoder.keyEncodingStrategy = .convertToSnakeCase
```

**Payload 结构**: 三层数据放入 `extractedData`，`narrative` 字段放 Layer 3 Markdown 供 LLM 直接消费。

```swift
// 内层：V1.2 结构化数据（放入 extractedData）
struct ScreenPulseData: Codable {
    let schemaVersion: String = "1.2"
    let observationId: String
    let sessionId: String
    let userId: String
    let deviceId: String
    let timestamp: TimeInterval
    let source: String = "screenpulse-macos"
    let trigger: String
    let app: AppInfo

    // Layer 1：裁剪过的 AX 树（去掉纯 UI 噪音节点）
    let axSnapshot: AXNode?

    // Layer 2：语义字段提取
    let semantic: SemanticFields

    // 元信息
    let meta: MetaInfo

    struct AppInfo: Codable {
        let name: String
        let bundleId: String
        let category: String   // "browser" | "ide" | "terminal" | "communication" | "other"
        let version: String?
    }

    struct MetaInfo: Codable {
        let axNodeCount: Int         // AX 树节点总数（裁剪后）
        let markdownLength: Int
        let captureMs: Int
        let hasSelectedText: Bool    // 是否有用户选中文字（高意图信号）
    }
}

// POST 到 /api/ingest/observation 的请求体（手动构建字典，不用 Codable）
// 对应后端 ObservationCreateRequest 字段
func buildIngestionRequest(
    sessionId: String,
    title: String,
    narrative: String,
    extractedData: [String: Any]
) -> [String: Any] {
    // extractedData 由 JSONEncoder + JSONSerialization.jsonObject 转换 ScreenPulseData 得到
    [
        "content_session_id": sessionId,
        "project_path": "screenpulse",
        "source": "screenpulse-macos",
        "type": "screen-capture",
        "title": title,
        "narrative": narrative,
        "extractedData": extractedData
    ]
}
```

**后端使用方式**：

| 使用场景 | 用哪一层 |
|---|---|
| LLM 直接注入 context（给 Claude Code） | `narrative`（Layer 3 Markdown） |
| 向量 embedding / 语义检索 | `narrative` 做 embedding |
| 精准字段查询（"用户在什么 URL"） | `extractedData.semantic.url`（Layer 2） |
| 判断用户意图（"用户选中了什么"） | `extractedData.semantic.selectedText`（最强信号） |
| 后端重新解析、未来新功能 | `extractedData.axSnapshot`（Layer 1） |
| 多设备/多会话时间线 | `sessionId` + `deviceId` + `timestamp` |

### 4.5 IdentityManager

```swift
enum IdentityManager {
    // 设备 ID：固定写 UserDefaults，首次运行时生成，跨会话不变
    static var deviceId: String {
        let key = "screenpulse.deviceId"
        if let existing = UserDefaults.standard.string(forKey: key) {
            return existing
        }
        let new = UUID().uuidString
        UserDefaults.standard.set(new, forKey: key)
        return new
    }

    // 用户 ID：hostname + deviceId 前缀的组合（方便多机区分）
    static var userId: String {
        let host = Host.current().localizedName ?? "unknown-mac"
        return "\(host)@\(deviceId.prefix(8))"
    }

    // 会话 ID：每次 App 启动新建
    static let sessionId: String = UUID().uuidString
}
```

---

## 五、API 端点约定

### 5.1 ScreenPulse -> Cortex CE

**请求**
```
POST /api/ingest/observation
Host: localhost:37777
Content-Type: application/json
```

**请求体**: 使用 `ObservationCreateRequest` 格式，`extractedData` 字段承载完整的三层结构化数据。详见 [4.4 API Payload](#44-api-payload-v12) 节。

**预期响应** (成功): 返回创建的 ObservationEntity 对象

**预期响应** (失败):
```json
{
  "error": "Missing required field: content_session_id (or session_id)"
}
```

### 5.2 后端适配说明 (Cortex CE 侧)

ScreenPulse 复用现有端点 `POST /api/ingest/observation`，无需新增后端端点。详见上方 [5.2 后端适配说明](#52-后端适配说明-cortex-ce-侧) 节。

## 六、构建与部署

### 6.1 环境要求

- macOS 13+ (Ventura 或更新)
- Xcode 15+ (或仅需 swift build)
- 网络访问 (用于 POST 到记忆系统)

### 6.2 编译步骤

```bash
# 1. 创建项目目录
mkdir -p ScreenPulse/Sources/ScreenPulse
cd ScreenPulse

# 2. 创建 Package.swift
cat > Package.swift << 'EOF'
// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ScreenPulse",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "ScreenPulse",
            path: "Sources/ScreenPulse"
        )
    ]
)
EOF

# 3. 编译
swift build -c release
# 输出: .build/release/ScreenPulse
```

### 6.3 打包 .app

```bash
APP=ScreenPulse.app
mkdir -p ${APP}/Contents/MacOS
mkdir -p ${APP}/Contents/Resources
cp .build/release/ScreenPulse ${APP}/Contents/MacOS/
cp Info.plist ${APP}/Contents/

# Ad-hoc 签名
codesign --sign - --entitlements entitlements.plist --force --deep ${APP}

# 验证
codesign --verify --verbose ${APP}
```

### 6.4 运行

```bash
open ScreenPulse.app
```

**首次运行**:
1. 菜单栏出现眼睛图标
2. 系统弹窗请求辅助功能权限
3. 前往 "系统设置 > 隐私与安全 > 辅助功能" 开启 ScreenPulse
4. 点击菜单栏图标 -> "Open Console" 打开主窗口

---

## 七、Info.plist 配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
    "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key><string>ScreenPulse</string>
    <key>CFBundleIdentifier</key><string>com.blueforce.ScreenPulse</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>CFBundleShortVersionString</key><string>1.0.0</string>
    <key>CFBundleName</key><string>ScreenPulse</string>
    <key>NSPrincipalClass</key><string>NSApplication</string>
    <key>LSUIElement</key><true/>
    <key>NSAppleEventsUsageDescription</key>
    <string>ScreenPulse 需要辅助功能权限来读取屏幕内容，用于构建 AI 记忆上下文。</string>
</dict>
</plist>
```

### 7.1 entitlements.plist 配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
    "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- 关闭沙盒，允许读取其他 App 的 UI 和发出本地网络请求 -->
    <key>com.apple.security.app-sandbox</key>
    <false/>
    <!-- 允许向 localhost 发请求（POST 到记忆系统） -->
    <key>com.apple.security.network.client</key>
    <true/>
</dict>
</plist>
```

> **注意**: V1.0 采用非沙箱模式简化开发和测试。生产部署时可按需开启沙箱并配置相应权限。

### 7.2 LSUIElement=true 的影响

- App 不出现在 Dock
- 菜单栏常驻，用户通过图标交互
- Cmd+Tab 切换不会显示 ScreenPulse (除非前台窗口是它的 Console)

### 7.3 辅助功能权限

- macOS 要求显式用户授权
- App 首次使用 Accessibility API 时触发系统弹窗
- 用户必须手动在 "系统设置 > 隐私与安全 > 辅助功能" 中开启

---

## 八、错误处理与边界情况

### 8.1 权限未授权

- `AXIsProcessTrustedWithOptions()` 返回 false
- 状态消息显示警告，用户点击 "Open Console" 时可看到
- 捕获逻辑静默跳过，不崩溃

### 8.2 网络不可用

- POST 失败不影响本地日志和内存缓存
- V1.1 实现重试队列

### 8.3 前台窗口无文本

- `kAXFocusedWindow` 返回 nil 或窗口无子元素
- `collectText` 返回空
- 空文本不创建事件，不 POST

### 8.4 深度递归防护

- `collectText` 有 `depth < 20` 限制
- 超过深度静默返回，防止极深 AX 树导致栈溢出

### 8.5 App 切换时的竞态

- AXObserver 回调在主线程
- ScreenCaptureManager 的 captureQueue 是 background QoS
- UI 更新必须 `DispatchQueue.main.async`

---

## 九、安全与隐私

### 9.1 数据最小化

- 只读取文本，不读取图片/媒体
- 密码字段自动过滤 (`AXSecureTextField`)
- 标题含 "password" 的字段跳过

### 9.2 本地存储

- 日志文件在 `~/Library/Application Support/ScreenPulse/logs/`
- 用户可删除日志目录清空历史

### 9.3 网络传输

- 默认 localhost，适合开发测试
- 生产部署建议配置 HTTPS 端点
- 不传输敏感凭证 (已过滤)

### 9.4 隐私描述

- Info.plist 的 `NSAppleEventsUsageDescription` 明确用途
- 用户知情同意是辅助功能权限的前提

---

## 十、测试计划

### 10.1 本地测试

```bash
# 1. 启动 mock server (另一个 Terminal)
python3 -c "
from http.server import HTTPServer, BaseHTTPRequestHandler
import json
class H(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(n)
        print(json.dumps(json.loads(body), indent=2, ensure_ascii=False))
        self.send_response(200)
        self.end_headers()
    def log_message(self, *args): pass
HTTPServer(('localhost', 37777), H).serve_forever()
"

# 2. 运行 ScreenPulse
open ScreenPulse.app

# 3. 切换到 Safari 查看网页
# 4. 观察 mock server 输出
```

### 10.2 集成测试

- 与真实 Cortex CE 后端联调
- 验证 observation 正确存入数据库
- 验证搜索能检索到 ScreenPulse 捕获的内容

---

## 十一、里程碑

| 阶段 | 目标 | 文件 |
|------|------|------|
| M1 | 项目骨架、菜单栏、控制台 UI | App.swift, AppDelegate.swift, ContentView.swift |
| M2 | 定时轮询捕获、本地日志 | ScreenCaptureManager.swift |
| M3 | POST 到记忆系统 | ScreenCaptureManager (网络部分), ObservationPayload.swift, IdentityManager.swift |
| M4 | 打包、签名、运行测试 | Info.plist, entitlements.plist, build scripts |
| M5 | AXObserver 事件驱动 | AXEventWatcher.swift |
| M6 | App 特化解析器 | AppParsers.swift |
| M7 | 可靠性增强 (重试队列) | PayloadSender 增强 (网络断线缓存、批量发送) |

---

## 十二、风险与缓解

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 辅助功能权限被拒 | 中 | 高 | 清晰的用户引导、状态提示 |
| 某些 App 无障碍支持差 | 高 | 中 | App 特化解析器、通用兜底 |
| 后端 API 不支持 extractedData | 中 | 中 | Payload 降级：V1.2 数据直接放入 narrative，只传文本 |
| 沙箱签名限制 | 低 | 高 | entitlements.plist 配置、Ad-hoc 签名 |

---

## 附录 A: 与 Demo 文档的差异说明

本文档与 `一个捕获屏幕内容的macOS应用.md` 有以下关键差异：

1. **项目名**: ScreenPulse (原 Demo 用 ScreenWatcherDemo)
2. **Bundle ID**: com.blueforce.ScreenPulse (原 Demo 用 com.example)
3. **API 端点**: `/api/ingest/observation` (复用现有端点，原 Demo 用 `/api/observations`)
4. **Payload 结构**: 两层结构——外层 ObservationCreateRequest 映射，内层 V1.2 数据放在 `extractedData` 中
5. **包管理**: Swift Package Manager (原 Demo 也用 SPM，代码一致)
6. **功能演进**: 明确 V1.0 (轮询) -> V1.1 (AXObserver) -> V2.0 (截图) 路线图

---

## 附录 B: 与 Cortex CE 的 API 对齐

根据代码库探索结果，Cortex CE 使用 snake_case 作为 wire format 惯例。ScreenPulse 的 Payload 遵循此惯例。

**外层字段** (ObservationCreateRequest):

| 字段 | 类型 | 说明 |
|------|------|------|
| content_session_id | string | 必填，会话标识 |
| project_path | string | 必填，固定 "screenpulse" |
| source | string | 固定 "screenpulse-macos" |
| type | string | 固定 "screen-capture" |
| title | string | "[AppName] WindowTitle" |
| narrative | string | 捕获的完整文本 |
| extractedData | object | 下层 V1.2 结构化数据 |

**内层 extractedData 字段** (V1.2):

| 字段 | 类型 | 说明 |
|------|------|------|
| schema_version | string | 协议版本 "1.2" |
| observation_id | string | UUID，去重用 |
| session_id | string | App 生命周期内固定 |
| user_id | string | hostname@deviceId前缀 |
| device_id | string | 首次启动生成，UserDefaults 存储 |
| timestamp | number | Unix timestamp (秒) |
| source | string | 固定 "screenpulse-macos" |
| trigger | string | 触发来源枚举值 |
| app.name | string | App 名称 |
| app.bundle_id | string | Bundle identifier |
| app.category | string | App 类型 |
| app.version | string? | App 版本 |
| ax_snapshot | object | Layer 1: AX 树快照 |
| semantic | object | Layer 2: 语义字段（URL、文件路径、选中文本等） |
| rendered_markdown | string | Layer 3: 渲染后的 Markdown |
| meta.text_length | number | 文本长度 |
| meta.capture_ms | number | 捕获耗时 (毫秒) |

---

*文档版本: 1.0.0*
*待评审状态*
