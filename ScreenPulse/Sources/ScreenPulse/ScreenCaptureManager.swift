import AppKit
import Combine
import ApplicationServices

// MARK: - CaptureEvent
struct CaptureEvent: Identifiable, Hashable {
    let id = UUID()
    let timestamp: Date
    let appName: String
    let bundleId: String
    let windowTitle: String
    let fullText: String
    let trigger: CaptureTrigger
    let axSnapshot: AXNode?  // Layer 1: Structured AX tree

    var timestampString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter.string(from: timestamp)
    }

    // Hashable conformance (only id matters)
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: CaptureEvent, rhs: CaptureEvent) -> Bool {
        lhs.id == rhs.id
    }
}

enum CaptureTrigger: String, Codable {
    case manual
    case timer
    case appSwitch
    case windowChange
    case titleChange
    case contentChange
}

enum AppCategory: String, Codable {
    case browser
    case ide
    case terminal
    case communication
    case editor
    case other
}

// MARK: - Meaningful AX Roles (for filtering UI noise)

/// Roles that contain meaningful content (used for Layer 1 AX tree filtering)
private let meaningfulAXRoles: Set<String> = [
    "AXStaticText", "AXHeading", "AXLink", "AXTextField",
    "AXTextArea", "AXButton", "AXMenuBarItem", "AXMenuItem",
    "AXImage", "AXWebArea", "AXGroup", "AXList", "AXListItem",
    "AXTable", "AXRow", "AXCell", "AXTabGroup", "AXTab",
    // Window and document roles (containers, not filtered at root)
    "AXWindow", "AXSheet", "AXDialog", "AXDocument"
]

// MARK: - ScreenCaptureManager
final class ScreenCaptureManager: ObservableObject {
    static let shared = ScreenCaptureManager()

    // Published state (UI bindings)
    @Published var isCapturing: Bool = false
    @Published var events: [CaptureEvent] = []
    @Published var selectedEventId: UUID?

    var selectedEvent: CaptureEvent? {
        guard let id = selectedEventId else { return nil }
        return events.first { $0.id == id }
    }
    @Published var ignoreBundleIds: Set<String> = [
        "com.agilebits.onepassword7",
        "com.apple.systempreferences",
        "com.apple.keychainaccess"
    ]
    @Published var newIgnoreInput: String = ""
    @Published var statusMessage: String = "Idle"
    @Published var endpointInput: String = "http://localhost:37777/api/ingest/observation"
    @Published var pollingInterval: Double = 5.0  // seconds, configurable

    // Internal state
    private let systemElement = AXUIElementCreateSystemWide()
    private let captureQueue = DispatchQueue(label: "screenpulse.capture", qos: .background)
    private var timer: DispatchSourceTimer?

    // Content deduplication state
    private var lastContentHash: Int = 0
    private var lastSkipTime: Date?
    private let skipLogInterval: TimeInterval = 30.0  // Log "skipping duplicate" at most every 30s

    // Capture statistics (published for UI binding)
    @Published var totalCaptures: Int = 0
    @Published var skippedDuplicates: Int = 0

    private let logDir: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let url = base.appendingPathComponent("ScreenPulse/logs", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()

    private var memoryCache: [CaptureEvent] = []
    private let maxCacheSize = 200

    private init() {}

    // MARK: - Control Interface

    func toggleCapture() {
        if isCapturing {
            stopCapturing()
        } else {
            startCapturing()
        }
    }

    func startCapturing() {
        guard !isCapturing else { return }

        // Check accessibility permissions
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: false] as CFDictionary
        guard AXIsProcessTrustedWithOptions(options) else {
            statusMessage = "⚠️ 权限未授权"
            return
        }

        isCapturing = true
        statusMessage = "Running"
        startTimer()
    }

    func stopCapturing() {
        isCapturing = false
        statusMessage = "Idle"
        stopTimer()
    }

    func captureOnceManually() {
        captureOnce(trigger: .manual)
    }

    func addIgnoreBundle() {
        let trimmed = newIgnoreInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        ignoreBundleIds.insert(trimmed)
        newIgnoreInput = ""
    }

    func removeIgnoreBundle(_ id: String) {
        ignoreBundleIds.remove(id)
    }

    func saveEndpoint() {
        // Endpoint is already bound via @Published, just trigger a save
        UserDefaults.standard.set(endpointInput, forKey: "screenpulse.endpoint")
    }

    // MARK: - Internal

    private func startTimer() {
        // Stop existing timer if any
        stopTimer()

        let interval: TimeInterval = pollingInterval
        timer = DispatchSource.makeTimerSource(queue: captureQueue)
        timer?.schedule(deadline: .now() + interval, repeating: interval)
        timer?.setEventHandler { [weak self] in
            self?.captureOnce(trigger: .timer)
        }
        timer?.resume()
    }

    private func stopTimer() {
        timer?.cancel()
        timer = nil
    }

    /// Restart timer with new interval (call when pollingInterval changes)
    func restartTimerWithNewInterval() {
        if isCapturing {
            startTimer()
        }
    }

    /// Reset capture statistics
    func resetStatistics() {
        totalCaptures = 0
        skippedDuplicates = 0
    }

    private func captureOnce(trigger: CaptureTrigger) {
        captureQueue.async { [weak self] in
            self?.performCapture(trigger: trigger)
        }
    }

    private func performCapture(trigger: CaptureTrigger) {
        let startTime = CFAbsoluteTimeGetCurrent()

        // Get frontmost app
        guard let frontmostApp = NSWorkspace.shared.frontmostApplication else {
            return
        }

        // Never capture our own process (avoids self-capture + background-thread autolayout crashes)
        guard frontmostApp.processIdentifier != ProcessInfo.processInfo.processIdentifier else {
            return
        }

        let bundleId = frontmostApp.bundleIdentifier ?? "unknown"
        let appName = frontmostApp.localizedName ?? "Unknown App"

        // Check ignore list
        guard !ignoreBundleIds.contains(bundleId) else {
            return
        }

        // Get focused window
        let pid = frontmostApp.processIdentifier
        let appElement = AXUIElementCreateApplication(pid)

        var focusedWindow: CFTypeRef?
        let windowResult = AXUIElementCopyAttributeValue(appElement, kAXFocusedWindowAttribute as CFString, &focusedWindow)

        guard windowResult == .success, let window = focusedWindow else {
            return
        }

        let windowElement = window as! AXUIElement

        // Get window title
        var titleValue: CFTypeRef?
        AXUIElementCopyAttributeValue(windowElement, kAXTitleAttribute as CFString, &titleValue)
        let windowTitle = (titleValue as? String) ?? ""

        // Build AX node tree (Layer 1)
        let axRoot = buildAXNodeTree(from: windowElement, depth: 0)

        // Extract text from AX tree for backward compatibility
        let fullText = extractText(from: axRoot)

        // Skip if no text content
        guard !fullText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }

        // Content deduplication: skip if content hash hasn't changed
        let contentHash = hashContent(bundleId: bundleId, windowTitle: windowTitle, fullText: fullText)
        if contentHash == lastContentHash {
            // Log skip message at most once per skipLogInterval
            if let lastSkip = lastSkipTime, Date().timeIntervalSince(lastSkip) < skipLogInterval {
                // Too soon to log again
            } else {
                print("ScreenPulse: Skipping duplicate capture for \(appName) - \(windowTitle)")
                lastSkipTime = Date()
            }
            skippedDuplicates += 1
            return
        }
        lastContentHash = contentHash

        let captureTime = CFAbsoluteTimeGetCurrent()
        let captureMs = Int((captureTime - startTime) * 1000)

        // Create event
        let event = CaptureEvent(
            timestamp: Date(),
            appName: appName,
            bundleId: bundleId,
            windowTitle: windowTitle,
            fullText: fullText,
            trigger: trigger,
            axSnapshot: axRoot
        )

        // Increment capture statistics
        totalCaptures += 1

        // Update UI on main thread
        DispatchQueue.main.async { [weak self] in
            self?.handleCapturedEvent(event)
        }

        // Write to log
        writeLog(event)

        // Post to memory system
        postToMemorySystem(event, captureMs: captureMs)
    }

    // MARK: - Content Deduplication

    /// Computes a hash of the content to detect duplicates
    /// We hash bundleId + windowTitle + first 500 chars of fullText
    private func hashContent(bundleId: String, windowTitle: String, fullText: String) -> Int {
        let prefix = String(fullText.prefix(500))
        let combined = "\(bundleId)|\(windowTitle)|\(prefix)"
        return combined.hashValue
    }

    // MARK: - AX Node Tree Builder (Layer 1 extraction)

    /// Builds a structured AXNode tree from an AXUIElement
    private func buildAXNodeTree(from element: AXUIElement, depth: Int) -> AXNode? {
        guard depth < 20 else { return nil }

        // Get role
        var roleValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXRoleAttribute as CFString, &roleValue)
        let role = roleValue as? String ?? ""

        // Skip if not a meaningful role (but allow container roles like AXWindow)
        guard meaningfulAXRoles.contains(role) else {
            return nil
        }

        // Get title
        var titleValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXTitleAttribute as CFString, &titleValue)
        let title = titleValue as? String

        // Get value
        var valueValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXValueAttribute as CFString, &valueValue)
        let value = valueValue as? String

        // Get description
        var descValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXDescriptionAttribute as CFString, &descValue)
        let description = descValue as? String

        // Get URL (for AXLink)
        var urlValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXURLAttribute as CFString, &urlValue)
        let url = urlValue as? String

        // Get level (for AXHeading) - kAXARIALevelAttribute is iOS only, not available on macOS
        // On macOS, we infer level from the number in the title (e.g., "1. Heading" -> level 1)
        var level: Int? = nil
        if role == "AXHeading", let title = title ?? value {
            // Try to extract heading level from title (e.g., "1. Title", "Section 2.3")
            let pattern = "^([0-9]+)[.\\s]"
            if let regex = try? NSRegularExpression(pattern: pattern),
               let match = regex.firstMatch(in: title, range: NSRange(title.startIndex..., in: title)),
               let range = Range(match.range(at: 1), in: title) {
                level = Int(title[range])
            }
        }

        // Get position
        var positionValue: CFTypeRef?
        let positionResult = AXUIElementCopyAttributeValue(element, kAXPositionAttribute as CFString, &positionValue)
        var position: CGPointCodable? = nil
        if positionResult == .success, let pos = positionValue {
            var point = CGPoint.zero
            if AXValueGetValue(pos as! AXValue, .cgPoint, &point) {
                position = CGPointCodable(point)
            }
        }

        // Get size
        var sizeValue: CFTypeRef?
        let sizeResult = AXUIElementCopyAttributeValue(element, kAXSizeAttribute as CFString, &sizeValue)
        var size: CGSizeCodable? = nil
        if sizeResult == .success, let sz = sizeValue {
            var cgSize = CGSize.zero
            if AXValueGetValue(sz as! AXValue, .cgSize, &cgSize) {
                size = CGSizeCodable(cgSize)
            }
        }

        // Get focused state
        var focusedValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXFocusedAttribute as CFString, &focusedValue)
        let isFocused = (focusedValue as? NSNumber)?.boolValue ?? false

        // Get selected state
        var selectedValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXSelectedAttribute as CFString, &selectedValue)
        let isSelected = (selectedValue as? NSNumber)?.boolValue ?? false

        // Get enabled state
        var enabledValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXEnabledAttribute as CFString, &enabledValue)
        let isEnabled = (enabledValue as? NSNumber)?.boolValue ?? true

        // Skip secure text fields
        if role == "AXSecureTextField" {
            return nil
        }

        // Skip if title contains "password"
        if let title = title, title.lowercased().contains("password") {
            return nil
        }

        // Build children
        var children: [AXNode] = []
        var childrenValue: CFTypeRef?
        let result = AXUIElementCopyAttributeValue(element, kAXChildrenAttribute as CFString, &childrenValue)

        if result == .success, let childrenArray = childrenValue as? [AXUIElement] {
            for child in childrenArray {
                if let childNode = buildAXNodeTree(from: child, depth: depth + 1) {
                    children.append(childNode)
                }
            }
        }

        return AXNode(
            role: role,
            title: title,
            value: value,
            description: description,
            url: url,
            level: level,
            position: position,
            size: size,
            isFocused: isFocused,
            isSelected: isSelected,
            isEnabled: isEnabled,
            children: children
        )
    }

    /// Extracts plain text from AXNode tree (for backward compatibility with fullText)
    private func extractText(from node: AXNode?) -> String {
        guard let node = node else { return "" }

        var texts: [String] = []

        // Skip secure text fields
        if node.role == "AXSecureTextField" {
            return ""
        }

        // Skip if title contains "password"
        if let title = node.title, title.lowercased().contains("password") {
            return ""
        }

        // Collect value text
        if let value = node.value, !value.isEmpty {
            texts.append(value)
        }

        // Recurse into children
        for child in node.children {
            let childText = extractText(from: child)
            if !childText.isEmpty {
                texts.append(childText)
            }
        }

        return texts.joined(separator: "\n")
    }

    private func handleCapturedEvent(_ event: CaptureEvent) {
        memoryCache.append(event)
        if memoryCache.count > maxCacheSize {
            memoryCache.removeFirst()
        }
        events = memoryCache
    }

    private func writeLog(_ event: CaptureEvent) {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let dateStr = formatter.string(from: event.timestamp)

        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm:ss"
        let timeStr = timeFormatter.string(from: event.timestamp)

        let logFile = logDir.appendingPathComponent("\(dateStr).log")
        let logEntry = "[\(timeStr)] [\(event.bundleId)] [\(event.windowTitle)]\n\(event.fullText)\n---\n"

        if let data = logEntry.data(using: .utf8) {
            if FileManager.default.fileExists(atPath: logFile.path) {
                if let fileHandle = try? FileHandle(forWritingTo: logFile) {
                    fileHandle.seekToEndOfFile()
                    fileHandle.write(data)
                    fileHandle.closeFile()
                }
            } else {
                try? data.write(to: logFile)
            }
        }
    }

    private func postToMemorySystem(_ event: CaptureEvent, captureMs: Int) {
        let payload = ObservationPayloadBuilder.build(
            event: event,
            captureMs: captureMs
        )

        ObservationPayloadSender.send(payload: payload) { result in
            switch result {
            case .success:
                print("ScreenPulse: Successfully posted observation")
            case .failure(let error):
                print("ScreenPulse: Failed to post observation: \(error)")
            }
        }
    }
}
