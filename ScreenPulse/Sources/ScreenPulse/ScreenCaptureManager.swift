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

// MARK: - ScreenCaptureManager
final class ScreenCaptureManager: ObservableObject {
    static let shared = ScreenCaptureManager()

    // Published state (UI bindings)
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

    // Internal state
    private let systemElement = AXUIElementCreateSystemWide()
    private let captureQueue = DispatchQueue(label: "screenpulse.capture", qos: .background)
    private var timer: DispatchSourceTimer?
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
        let interval: TimeInterval = 5.0
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

        // Collect text from window
        var collectedText: [String] = []
        collectText(from: windowElement, depth: 0, into: &collectedText)

        let fullText = collectedText.joined(separator: "\n")

        // Skip if no text content
        guard !fullText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }

        let captureTime = CFAbsoluteTimeGetCurrent()
        let captureMs = Int((captureTime - startTime) * 1000)

        // Create event
        let event = CaptureEvent(
            timestamp: Date(),
            appName: appName,
            bundleId: bundleId,
            windowTitle: windowTitle,
            fullText: fullText,
            trigger: trigger
        )

        // Update UI on main thread
        DispatchQueue.main.async { [weak self] in
            self?.handleCapturedEvent(event)
        }

        // Write to log
        writeLog(event)

        // Post to memory system
        postToMemorySystem(event, captureMs: captureMs)
    }

    private func collectText(from element: AXUIElement, depth: Int, into text: inout [String]) {
        guard depth < 20 else { return }

        // Get role
        var roleValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXRoleAttribute as CFString, &roleValue)
        let role = roleValue as? String ?? ""

        // Skip secure text fields
        if role == "AXSecureTextField" {
            return
        }

        // Get value
        var valueValue: CFTypeRef?
        AXUIElementCopyAttributeValue(element, kAXValueAttribute as CFString, &valueValue)
        if let value = valueValue as? String, !value.isEmpty {
            // Skip if title contains "password"
            var titleValue: CFTypeRef?
            AXUIElementCopyAttributeValue(element, kAXTitleAttribute as CFString, &titleValue)
            let title = (titleValue as? String) ?? ""

            if !title.lowercased().contains("password") {
                text.append(value)
            }
        }

        // Get children
        var childrenValue: CFTypeRef?
        let result = AXUIElementCopyAttributeValue(element, kAXChildrenAttribute as CFString, &childrenValue)

        if result == .success, let children = childrenValue as? [AXUIElement] {
            for child in children {
                collectText(from: child, depth: depth + 1, into: &text)
            }
        }
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
