import Foundation

// MARK: - ScreenPulseData (V1.2)

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

    // Layer 1: Trimmed AX tree (UI noise nodes removed)
    let axSnapshot: AXNode?

    // Layer 2: Semantic fields
    let semantic: SemanticFields

    // Metadata
    let meta: MetaInfo

    struct AppInfo: Codable {
        let name: String
        let bundleId: String
        let category: String
        let version: String?
    }

    struct MetaInfo: Codable {
        let axNodeCount: Int
        let markdownLength: Int
        let captureMs: Int
        let hasSelectedText: Bool
    }
}

// MARK: - AXNode (Layer 1)

struct AXNode: Codable {
    let role: String
    let title: String?
    let value: String?
    let description: String?
    let url: String?
    let level: Int?
    let position: CGPointCodable?
    let size: CGSizeCodable?
    let isFocused: Bool
    let isSelected: Bool
    let isEnabled: Bool
    var children: [AXNode]

    static let meaningfulRoles: Set<String> = [
        "AXStaticText", "AXHeading", "AXLink", "AXTextField",
        "AXTextArea", "AXButton", "AXMenuBarItem", "AXMenuItem",
        "AXImage", "AXWebArea", "AXGroup", "AXList", "AXListItem",
        "AXTable", "AXRow", "AXCell", "AXTabGroup", "AXTab"
    ]
}

struct CGPointCodable: Codable {
    let x: Double
    let y: Double
    init(_ p: CGPoint) { x = p.x; y = p.y }
}

struct CGSizeCodable: Codable {
    let width: Double
    let height: Double
    init(_ s: CGSize) { width = s.width; height = s.height }
}

// MARK: - SemanticFields (Layer 2)

struct SemanticFields: Codable {
    var windowTitle: String = ""
    var selectedText: String?
    var focusedElementRole: String?
    var focusedElementValue: String?

    // Browser-specific
    var url: String?
    var pageTitle: String?
    var headings: [HeadingItem] = []
    var links: [LinkItem] = []
    var visibleTextBlocks: [TextBlock] = []

    // IDE-specific
    var filePath: String?
    var language: String?
    var codeSnippet: String?
    var terminalOutput: String?

    // Communication tool-specific
    var channelName: String?
    var recentMessages: [MessageItem] = []

    struct HeadingItem: Codable {
        let level: Int
        let text: String
    }

    struct LinkItem: Codable {
        let text: String
        let url: String?
    }

    struct TextBlock: Codable {
        let text: String           // 文本内容
        let role: String          // AX 角色：AXStaticText/AXHeading/AXLink/AXButton/...
        let title: String?        // 元素的 title 属性（按钮名称等）
        let url: String?           // AXLink 的 URL
        let level: Int?           // AXHeading 的层级 (1-6)
        let position: CGPointCodable?  // 屏幕坐标
        let size: CGSizeCodable?      // 元素尺寸
        let isFocused: Bool        // 是否聚焦

        init(text: String, role: String, title: String? = nil, url: String? = nil,
             level: Int? = nil, position: CGPointCodable? = nil, size: CGSizeCodable? = nil,
             isFocused: Bool = false) {
            self.text = text
            self.role = role
            self.title = title
            self.url = url
            self.level = level
            self.position = position
            self.size = size
            self.isFocused = isFocused
        }
    }

    struct MessageItem: Codable {
        let sender: String?
        let text: String
    }
}

// MARK: - Payload Builder

enum ObservationPayloadBuilder {
    /// Extracts SemanticFields (Layer 2) from AXNode tree (Layer 1)
    static func extractSemanticFields(from axNode: AXNode?, windowTitle: String) -> SemanticFields {
        var semantic = SemanticFields(windowTitle: windowTitle)

        guard let root = axNode else { return semantic }

        // Traverse the tree and categorize nodes
        extractFromNode(root, into: &semantic)

        return semantic
    }

    /// Recursively extracts structured data from AXNode into SemanticFields
    private static func extractFromNode(_ node: AXNode, into semantic: inout SemanticFields) {
        // Track focused element
        if node.isFocused {
            semantic.focusedElementRole = node.role
            semantic.focusedElementValue = node.value
        }

        // Check for selected text (strongest intent signal)
        if node.isSelected, let text = node.value, !text.isEmpty {
            semantic.selectedText = text
        }

        // Categorize by role
        switch node.role {
        case "AXHeading":
            if let text = node.value, !text.isEmpty, let level = node.level {
                semantic.headings.append(SemanticFields.HeadingItem(level: level, text: text))
            }

        case "AXLink":
            if let text = node.value ?? node.title, !text.isEmpty {
                semantic.links.append(SemanticFields.LinkItem(text: text, url: node.url))
            }

        case "AXTextField", "AXTextArea":
            if let text = node.value, !text.isEmpty {
                let block = SemanticFields.TextBlock(
                    text: text,
                    role: node.role,
                    title: node.title,
                    url: nil,
                    level: nil,
                    position: node.position,
                    size: node.size,
                    isFocused: node.isFocused
                )
                semantic.visibleTextBlocks.append(block)
            }

        case "AXStaticText", "AXListItem":
            if let text = node.value, !text.isEmpty {
                let block = SemanticFields.TextBlock(
                    text: text,
                    role: node.role,
                    title: node.title,
                    url: nil,
                    level: nil,
                    position: node.position,
                    size: node.size,
                    isFocused: node.isFocused
                )
                semantic.visibleTextBlocks.append(block)
            }

        case "AXButton", "AXMenuItem":
            if let text = node.value ?? node.title, !text.isEmpty {
                let block = SemanticFields.TextBlock(
                    text: text,
                    role: node.role,
                    title: node.title,
                    url: nil,
                    level: nil,
                    position: node.position,
                    size: node.size,
                    isFocused: node.isFocused
                )
                semantic.visibleTextBlocks.append(block)
            }

        case "AXWebArea":
            // For web content, extract URL from the document
            semantic.url = node.url
            semantic.pageTitle = node.title

        default:
            break
        }

        // Recurse into children
        for child in node.children {
            extractFromNode(child, into: &semantic)
        }
    }

    /// Counts total nodes in AXNode tree
    static func countNodes(_ node: AXNode?) -> Int {
        guard let node = node else { return 0 }
        return 1 + node.children.reduce(0) { $0 + countNodes($1) }
    }

    static func build(event: CaptureEvent, captureMs: Int) -> [String: Any] {
        // Extract Layer 2 semantic fields from Layer 1 AXNode tree
        let semantic = extractSemanticFields(from: event.axSnapshot, windowTitle: event.windowTitle)

        // Count AX nodes for metadata
        let axNodeCount = countNodes(event.axSnapshot)

        let screenPulseData = ScreenPulseData(
            observationId: UUID().uuidString,
            sessionId: IdentityManager.sessionId,
            userId: IdentityManager.userId,
            deviceId: IdentityManager.deviceId,
            timestamp: Date().timeIntervalSince1970,
            trigger: event.trigger.rawValue,
            app: ScreenPulseData.AppInfo(
                name: event.appName,
                bundleId: event.bundleId,
                category: "other",
                version: nil
            ),
            axSnapshot: event.axSnapshot,  // Layer 1: Full AX tree
            semantic: semantic,            // Layer 2: Extracted semantic fields
            meta: ScreenPulseData.MetaInfo(
                axNodeCount: axNodeCount,
                markdownLength: event.fullText.count,
                captureMs: captureMs,
                hasSelectedText: semantic.selectedText != nil
            )
        )

        // Convert to dictionary
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase

        guard let jsonData = try? encoder.encode(screenPulseData),
              let extractedData = try? JSONSerialization.jsonObject(with: jsonData) else {
            return [:]
        }

        let narrative = renderMarkdown(from: semantic)

        return [
            "content_session_id": IdentityManager.sessionId,
            "project_path": "screenpulse",
            "source": "screenpulse-macos",
            "type": "screen-capture",
            "title": "\(event.appName) - \(event.windowTitle)",
            "narrative": narrative,
            "extractedData": extractedData
        ]
    }

    /// Renders structured semantic fields as Markdown (Layer 3)
    static func renderMarkdown(from semantic: SemanticFields) -> String {
        var lines: [String] = []

        // URL and page title (browser) - most important for context
        if let url = semantic.url {
            let title = semantic.pageTitle ?? url
            lines.append("[\(title)](\(url))")
        }

        // Window title
        if !semantic.windowTitle.isEmpty {
            lines.append("# \(semantic.windowTitle)")
        }

        // Selected text (strongest intent signal)
        if let selected = semantic.selectedText, !selected.isEmpty {
            lines.append("> **Selected:** \(selected)")
        }

        // Headings (structured, with levels)
        for heading in semantic.headings.sorted(by: { $0.level < $1.level }) {
            let prefix = String(repeating: "#", count: min(heading.level + 1, 6))
            lines.append("\(prefix) \(heading.text)")
        }

        // Links (structured)
        for link in semantic.links {
            if let url = link.url {
                lines.append("[\(link.text)](\(url))")
            } else {
                lines.append("[\(link.text)]")
            }
        }

        // Visible text blocks (now with proper roles)
        for block in semantic.visibleTextBlocks {
            switch block.role {
            case "AXButton", "AXMenuItem":
                // Render interactive elements with clear markers
                if let title = block.title {
                    lines.append("[\(block.text)] (\(title))")
                } else {
                    lines.append("[\(block.text)]")
                }
            case "AXTextField":
                if let title = block.title {
                    lines.append("**\(title):** \(block.text)")
                } else {
                    lines.append("**Input:** \(block.text)")
                }
            default:
                // AXStaticText, AXListItem - render as-is
                lines.append(block.text)
            }
        }

        // File path (IDE)
        if let fp = semantic.filePath {
            lines.append("`\(fp)`")
        }

        // Terminal output
        if let term = semantic.terminalOutput {
            lines.append("```\n\(term)\n```")
        }

        return lines.joined(separator: "\n\n")
    }
}

// MARK: - Payload Sender

enum ObservationPayloadSender {
    static func send(payload: [String: Any], completion: @escaping (Result<Void, Error>) -> Void) {
        guard let url = URL(string: ScreenCaptureManager.shared.endpointInput) else {
            completion(.failure(PayloadError.invalidURL))
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        guard let body = try? JSONSerialization.data(withJSONObject: payload) else {
            completion(.failure(PayloadError.encodingError))
            return
        }

        request.httpBody = body

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }

            guard let httpResponse = response as? HTTPURLResponse else {
                completion(.failure(PayloadError.invalidResponse))
                return
            }

            if (200...299).contains(httpResponse.statusCode) {
                completion(.success(()))
            } else {
                completion(.failure(PayloadError.httpError(statusCode: httpResponse.statusCode)))
            }
        }.resume()
    }

    enum PayloadError: Error {
        case invalidURL
        case encodingError
        case invalidResponse
        case httpError(statusCode: Int)
    }
}
