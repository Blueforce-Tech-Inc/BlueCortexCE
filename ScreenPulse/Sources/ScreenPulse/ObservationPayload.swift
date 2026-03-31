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
        let text: String
        let role: String
    }

    struct MessageItem: Codable {
        let sender: String?
        let text: String
    }
}

// MARK: - Payload Builder

enum ObservationPayloadBuilder {
    static func build(event: CaptureEvent, captureMs: Int) -> [String: Any] {
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
            axSnapshot: nil,
            semantic: SemanticFields(
                windowTitle: event.windowTitle,
                selectedText: nil,
                focusedElementRole: nil,
                focusedElementValue: nil,
                url: nil,
                pageTitle: nil,
                headings: [],
                links: [],
                visibleTextBlocks: [SemanticFields.TextBlock(text: event.fullText, role: "AXStaticText")],
                filePath: nil,
                language: nil,
                codeSnippet: nil,
                terminalOutput: nil,
                channelName: nil,
                recentMessages: []
            ),
            meta: ScreenPulseData.MetaInfo(
                axNodeCount: 0,
                markdownLength: event.fullText.count,
                captureMs: captureMs,
                hasSelectedText: false
            )
        )

        // Convert to dictionary
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase

        guard let jsonData = try? encoder.encode(screenPulseData),
              let extractedData = try? JSONSerialization.jsonObject(with: jsonData) else {
            return [:]
        }

        let narrative = renderMarkdown(from: screenPulseData.semantic)

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

    static func renderMarkdown(from semantic: SemanticFields) -> String {
        var lines: [String] = []

        // URL and page title (browser)
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

        // Visible text blocks
        for block in semantic.visibleTextBlocks where block.role == "AXStaticText" || block.role == "AXListItem" {
            lines.append(block.text)
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
