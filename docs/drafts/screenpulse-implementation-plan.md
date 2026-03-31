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

**发现结果**: Cortex CE 使用 `session_id` 作为 API 字段名（snake_case），核心端点是 `/api/ingest/observation`。

**集成决策**:
- ScreenPulse 不复用现有的 `/api/ingest/observation`（那是给工具调用用的）
- ScreenPulse 使用独立的端点 `/api/ingest/screen-capture`（新建）
- 如果后端暂未实现，ScreenPulse 先 POST 到 `http://localhost:37777/api/ingest/screen-capture`
- 字段名遵循 Cortex CE 的 snake_case 惯例

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

- **端点**: 可配置，默认为 `http://localhost:37777/api/ingest/screen-capture`
- **方法**: POST
- **Content-Type**: application/json
- **Payload**:
```json
{
  "schema_version": "1.0",
  "observation_id": "UUID",
  "session_id": "UUID (app生命周期内固定)",
  "user_id": "hostname@deviceId前缀",
  "device_id": "UUID (首次启动生成，存UserDefaults)",
  "timestamp": 1743466800.123,
  "source": "screenpulse-macos",
  "trigger": "timer",  // V1.0: "timer" 或 "manual"; V1.1: 更多类型
  "app": {
    "name": "Safari",
    "bundle_id": "com.apple.Safari",
    "category": "other",  // V1.0 始终 "other"; V1.1 有 browser/ide/terminal/communication
    "version": "18.3"
  },
  "context": {
    "window_title": "Littlebird – AI助手的终极形态",
    "text": "前Sentieo创始人新项目Littlebird获1100万美元融资...",
    "url": null,       // V1.1: 浏览器会填充
    "file_path": null, // V1.1: IDE 会填充
    "channel_name": null
  },
  "meta": {
    "text_length": 4821,
    "capture_ms": 38
  }
}
```

### 2.2 V1.1 功能 (迭代计划)

#### 2.2.1 AXObserver 事件驱动

- 替换定时轮询为 `AXObserver` 订阅
- 监听: `kAXFocusedWindowChangedNotification`, `kAXTitleChangedNotification`, `kAXValueChangedNotification`
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
        ├── AXEventWatcher.swift     # AXObserver 事件订阅 (V1.1)
        ├── AppParsers.swift         # App 特化解析器 (V1.1)
        ├── ObservationPayload.swift  # API Payload 结构 (V1.1)
        └── IdentityManager.swift    # 设备/会话 ID 管理
```

### 3.2 文件依赖关系

**V1.0 (M1-M4)**
```
App.swift
├── AppDelegate.swift
│   └── ScreenCaptureManager.swift
│       └── IdentityManager.swift
└── ContentView.swift
    └── ScreenCaptureManager.swift (通过 @EnvironmentObject)
```

**V1.1 (M5-M7) - 新增依赖**
```
ScreenCaptureManager.swift
├── AXEventWatcher.swift (V1.1)
├── AppParsers.swift (V1.1)
└── ObservationPayload.swift (V1.1)  // 内含 PayloadSender 类
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
    @Published var ignoreBundleIds: Set<String>
    @Published var newIgnoreInput: String = ""
    @Published var statusMessage: String = "Idle"
    @Published var endpointInput: String = "http://localhost:37777/api/ingest/screen-capture"

    // 内部状态
    private let systemElement = AXUIElementCreateSystemWide()
    private let captureQueue = DispatchQueue(label: "screenpulse.capture", qos: .background)
    private var timer: DispatchSourceTimer?
    private let logDir: URL  // ~/Library/Application Support/ScreenPulse/logs

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
struct CaptureEvent: Identifiable {
    let id: UUID
    let timestamp: Date
    let appName: String
    let bundleId: String
    let windowTitle: String
    let text: String
    // V1.0 固定值
    let category: AppCategory = .other
    let trigger: CaptureTrigger = .timer  // V1.0 只有 timer/manual
    var url: String? = nil       // V1.1 - 浏览器 URL
    var filePath: String? = nil  // V1.1 - IDE 文件路径

    var preview: String { ... }
    var timeString: String { ... }
}

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

### 4.3 API Payload (V1.1)

**JSON 编码策略**: Swift struct 字段为 camelCase，API wire format 使用 snake_case。编码时必须设置:
```swift
let encoder = JSONEncoder()
encoder.keyEncodingStrategy = .convertToSnakeCase
```

```swift
struct ObservationPayload: Codable {
    let schemaVersion: String = "1.0"
    let observationId: String
    let sessionId: String
    let userId: String
    let deviceId: String
    let timestamp: TimeInterval
    let source: String = "screenpulse-macos"
    let trigger: String
    let app: AppInfo
    let context: ContextInfo
    let meta: MetaInfo

    struct AppInfo: Codable {
        let name: String
        let bundleId: String
        let category: String   // "browser" | "ide" | "terminal" | "communication" | "other"
        let version: String?
    }

    struct ContextInfo: Codable {
        let windowTitle: String
        let text: String
        let url: String?        // 浏览器 URL (V1.1)
        let filePath: String?  // IDE 文件路径 (V1.1)
        let channelName: String? // 通讯工具频道
    }

    struct MetaInfo: Codable {
        let textLength: Int
        let captureMs: Int
    }
}
```

### 4.4 IdentityManager

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
POST /api/ingest/screen-capture
Host: localhost:37777
Content-Type: application/json
X-Session-Id: <session_id>
X-Device-Id: <device_id>
```

**请求体**: 见 2.1.5节的 Payload JSON

**预期响应** (成功):
```json
{
  "status": "ok",
  "observation_id": "生成的 UUID"
}
```

**预期响应** (失败):
```json
{
  "status": "error",
  "message": "错误描述"
}
```

### 5.2 后端实现要求 (Cortex CE 侧)

Cortex CE 需要新增一个端点 `POST /api/ingest/screen-capture`:

1. 接收 ScreenPulse 的 observation
2. 调用 LLM 做摘要/embedding
3. 存入 `mem_observations` 表，`source='screenpulse'`
4. 返回 observation_id

**如果后端暂未实现**: ScreenPulse 会失败但不影响本地功能，可配置端点为 mock server 进行测试。

---

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
| M3 | POST 到记忆系统 | ScreenCaptureManager (网络部分) |
| M4 | 打包、签名、运行测试 | Info.plist, entitlements.plist, build scripts |
| M5 | AXObserver 事件驱动 | AXEventWatcher.swift |
| M6 | App 特化解析器 | AppParsers.swift |
| M7 | 可靠性增强 (重试队列) | ObservationPayload.swift (含 PayloadSender 类) |

---

## 十二、风险与缓解

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 辅助功能权限被拒 | 中 | 高 | 清晰的用户引导、状态提示 |
| 某些 App 无障碍支持差 | 高 | 中 | App 特化解析器、通用兜底 |
| 后端 API 未就绪 | 中 | 低 | Mock server 测试、本地功能独立工作 |
| 沙箱签名限制 | 低 | 高 | entitlements.plist 配置、Ad-hoc 签名 |

---

## 附录 A: 与 Demo 文档的差异说明

本文档与 `一个捕获屏幕内容的macOS应用.md` 有以下关键差异：

1. **项目名**: ScreenPulse (原 Demo 用 ScreenWatcherDemo)
2. **Bundle ID**: com.blueforce.ScreenPulse (原 Demo 用 com.example)
3. **API 端点**: `/api/ingest/screen-capture` (原 Demo 用 `/api/observations`)
4. **Payload 结构**: V1.1 版本增加 `schema_version`, `user_id`, `device_id`, `trigger`, `category` 字段
5. **包管理**: Swift Package Manager (原 Demo 也用 SPM，代码一致)
6. **功能演进**: 明确 V1.0 (轮询) -> V1.1 (AXObserver) -> V2.0 (截图) 路线图

---

## 附录 B: 与 Cortex CE 的 API 对齐

根据代码库探索结果，Cortex CE 使用 snake_case 作为 wire format 惯例。ScreenPulse 的 Payload 遵循此惯例：

| Payload 字段 | 类型 | 说明 |
|-------------|------|------|
| schema_version | string | 协议版本，便于后端兼容 |
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
| context.window_title | string | 窗口标题 |
| context.text | string | 捕获的完整文本 |
| context.url | string? | 浏览器 URL (V1.1) |
| context.file_path | string? | IDE 文件路径 (V1.1) |
| context.channel_name | string? | 通讯工具频道 (V1.1) |
| meta.text_length | number | 文本长度 |
| meta.capture_ms | number | 捕获耗时 (毫秒) |

---

*文档版本: 1.0.0*
*待评审状态*
