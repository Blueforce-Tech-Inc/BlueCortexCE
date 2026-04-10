import SwiftUI
import WebKit
import os.log

struct ContentView: View {
    @ObservedObject private var manager = ScreenCaptureManager.shared

    var body: some View {
        HSplitView {
            // Left Panel: Controls and Configuration (280pt)
            leftPanel
                .frame(minWidth: 240, idealWidth: 280, maxWidth: 320)

            // Middle Panel: Events List (250pt)
            middlePanel
                .frame(minWidth: 200, idealWidth: 250, maxWidth: 300)

            // Right Panel: Event Details (400pt+)
            rightPanel
                .frame(minWidth: 400)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Left Panel
    private var leftPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Status Section
            statusSection

            Divider()

            // Statistics Section
            statisticsSection

            Divider()

            // Control Section
            controlSection

            Divider()

            // Endpoint Configuration
            endpointSection

            Divider()

            // Ignore Bundle IDs
            ignoreListSection

            Spacer()
        }
        .padding()
    }

    // MARK: - Middle Panel
    private var middlePanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Capture Events")
                .font(.headline)

            List(manager.events, selection: $manager.selectedEventId) { event in
                EventRow(event: event)
            }
        }
        .padding()
    }

    private var statusSection: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(manager.isCapturing ? Color.green : Color.gray)
                .frame(width: 12, height: 12)

            Text(manager.statusMessage)
                .font(.system(.body, design: .default))

            Spacer()
        }
    }

    private var statisticsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Statistics")
                    .font(.headline)

                Spacer()

                Button(action: {
                    manager.resetStatistics()
                }) {
                    Text("Reset")
                        .font(.caption)
                }
                .buttonStyle(.bordered)
            }

            HStack {
                StatBox(title: "Captures", value: "\(manager.totalCaptures)")
                Spacer()
                StatBox(title: "Skipped", value: "\(manager.skippedDuplicates)")
                Spacer()
                StatBox(title: "Errors", value: "\(manager.sendErrors)")
            }

            if let error = manager.lastErrorMessage {
                Text("Last error: \(error)")
                    .font(.caption)
                    .foregroundColor(.red)
                    .lineLimit(2)
            }
        }
    }

    private var controlSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Toggle("Enable Capture", isOn: Binding(
                get: { manager.isCapturing },
                set: { newValue in
                    if newValue {
                        manager.startCapturing()
                    } else {
                        manager.stopCapturing()
                    }
                }
            ))
            .toggleStyle(.switch)

            Button("Capture Now") {
                manager.captureOnceManually()
            }
            .buttonStyle(.borderedProminent)

            Button("Force Capture") {
                manager.captureOnceForced()
            }
            .buttonStyle(.bordered)
            .help("Capture even if content hasn't changed")

            HStack {
                Text("Interval:")
                    .font(.caption)
                Stepper(
                    value: $manager.pollingInterval,
                    in: 1...30,
                    step: 1
                ) {
                    Text("\(Int(manager.pollingInterval))s")
                        .font(.caption)
                        .frame(minWidth: 30)
                }
                .onChange(of: manager.pollingInterval) { _ in
                    manager.restartTimerWithNewInterval()
                    manager.saveSettings()
                }
            }
        }
    }

    private var endpointSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Memory System Endpoint")
                .font(.headline)

            HStack {
                TextField("Endpoint URL", text: $manager.endpointInput)
                    .textFieldStyle(.roundedBorder)

                Button("Save") {
                    manager.saveEndpoint()
                }
            }
        }
    }

    private var ignoreListSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Ignored Bundle IDs")
                .font(.headline)

            HStack {
                TextField("Bundle ID to ignore", text: $manager.newIgnoreInput)
                    .textFieldStyle(.roundedBorder)

                Button("Add") {
                    manager.addIgnoreBundle()
                }
            }

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(manager.ignoreBundleIds), id: \.self) { bundleId in
                        HStack {
                            Text(bundleId)
                                .font(.caption)
                                .foregroundColor(.secondary)

                            Spacer()

                            Button(action: {
                                manager.removeIgnoreBundle(bundleId)
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.secondary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .frame(maxHeight: 100)
        }
    }

    // MARK: - Right Panel
    private var rightPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Event Details")
                    .font(.headline)

                Spacer()

                // Toggle between JSON tree view and Plain text
                Picker("View:", selection: Binding(
                    get: { viewMode },
                    set: { viewMode = $0 }
                )) {
                    Text("JSON").tag(ViewMode.json)
                    Text("Plain").tag(ViewMode.plain)
                }
                .pickerStyle(.segmented)
                .frame(width: 150)
            }

            if let event = manager.selectedEvent {
                if viewMode == .json {
                    JSONTreeView(jsonString: event.jsonString)
                } else {
                    TextEditor(text: .constant(event.detailedText))
                        .font(.system(.body, design: .monospaced))
                        .scrollContentBackground(.hidden)
                        .background(Color(nsColor: .textBackgroundColor))
                        .border(Color.secondary.opacity(0.3))
                }
            } else {
                VStack {
                    Spacer()
                    Text("← 从中间选择一条记录查看完整文本")
                        .foregroundColor(.secondary)
                    Spacer()
                }
            }
        }
        .padding()
    }

    @State private var viewMode: ViewMode = .json

    enum ViewMode {
        case json
        case plain
    }
}

// MARK: - JSON Tree View using WKWebView + json-viewer
struct JSONTreeView: NSViewRepresentable {
    let jsonString: String

    func makeNSView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()

        // Use WKURLSchemeHandler to load local resources
        config.setURLSchemeHandler(LocalResourceHandler(), forURLScheme: "local")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.setValue(true, forKey: "drawsBackground")
        webView.setValue(NSColor(red: 0.12, green: 0.12, blue: 0.18, alpha: 1.0), forKey: "backgroundColor")
        webView.navigationDelegate = context.coordinator

        // Load HTML using local:// scheme
        webView.load(URLRequest(url: URL(string: "local://json-viewer.html")!))

        return webView
    }

    func updateNSView(_ webView: WKWebView, context: Context) {
        // Wait for page to load before injecting JSON
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            self.injectJSON(into: webView)
        }
    }

    private func injectJSON(into webView: WKWebView) {
        let escaped = jsonString
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "")

        let js = "window.renderJson && window.renderJson(\"\(escaped)\");"
        webView.evaluateJavaScript(js, completionHandler: nil)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator: NSObject, WKNavigationDelegate {
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            // Page loaded
        }
    }
}

// MARK: - Local Resource Handler for WKURLSchemeHandler
private let localResourceLogger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "ScreenPulse", category: "LocalResourceHandler")

class LocalResourceHandler: NSObject, WKURLSchemeHandler {
    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let url = urlSchemeTask.request.url else {
            urlSchemeTask.didFailWithError(NSError(domain: "LocalResourceHandler", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid URL"]))
            return
        }

        localResourceLogger.info("Received request for: \(url.absoluteString)")

        let resourceName: String
        let mimeType: String

        if let host = url.host, !host.isEmpty {
            // local://json-viewer.js -> json-viewer.js
            // local://json-viewer.html -> json-viewer.html
            resourceName = host
            // Determine MIME type based on extension
            if resourceName.hasSuffix(".js") {
                mimeType = "application/javascript"
            } else if resourceName.hasSuffix(".html") {
                mimeType = "text/html"
            } else if resourceName.hasSuffix(".css") {
                mimeType = "text/css"
            } else {
                mimeType = "application/octet-stream"
            }
            localResourceLogger.debug("Using host as resource name: \(resourceName), mimeType: \(mimeType)")
        } else {
            // local://json-viewer.html -> json-viewer.html
            // Extract filename from path
            let path = url.path
            resourceName = path.hasPrefix("/") ? String(path.dropFirst()) : path
            mimeType = "text/html"
            localResourceLogger.debug("Using path as resource name: \(resourceName)")
        }

        // Try to load from bundle - try multiple locations
        var bundlePath: String?

        // First try Bundle.main (works when app is bundled)
        if let path = Bundle.main.path(forResource: resourceName, ofType: nil) {
            bundlePath = path
        }

        // If not found, try Bundle.module (for SPM packages)
        if bundlePath == nil {
            let bundle = Bundle.module
            if let path = bundle.path(forResource: resourceName, ofType: nil) {
                bundlePath = path
                localResourceLogger.debug("Found via Bundle.module: \(bundle.bundlePath)")
            }
        }

        // If still not found, try relative to executable path
        if bundlePath == nil {
            if let execPath = Bundle.main.executablePath {
                let execDir = (execPath as NSString).deletingLastPathComponent
                let bundleDir = (execDir as NSString).appendingPathComponent("\(resourceName)")
                if FileManager.default.fileExists(atPath: bundleDir) {
                    bundlePath = bundleDir
                    localResourceLogger.debug("Found relative to executable: \(bundleDir)")
                }
            }
        }

        guard let finalPath = bundlePath else {
            localResourceLogger.error("Resource NOT found in bundle: \(resourceName)")
            localResourceLogger.debug("Bundle.main = \(Bundle.main.bundlePath)")
            localResourceLogger.debug("Bundle.module = \(Bundle.module.bundlePath)")
            urlSchemeTask.didFailWithError(NSError(domain: "LocalResourceHandler", code: -2, userInfo: [NSLocalizedDescriptionKey: "Resource not found: \(resourceName)"]))
            return
        }

        localResourceLogger.debug("Resource found at: \(finalPath)")

        do {
            let data = try Data(contentsOf: URL(fileURLWithPath: finalPath))
            let response = URLResponse(url: url, mimeType: mimeType, expectedContentLength: data.count, textEncodingName: nil)
            urlSchemeTask.didReceive(response)
            urlSchemeTask.didReceive(data)
            urlSchemeTask.didFinish()
            localResourceLogger.info("Successfully served: \(resourceName) (\(data.count) bytes)")
        } catch {
            localResourceLogger.error("Error loading resource: \(error.localizedDescription)")
            urlSchemeTask.didFailWithError(error)
        }
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        // Handle stop if needed
    }
}

// MARK: - HTML Template for JSON Viewer
enum HTMLTemplate {
    /// Fallback JSON viewer with simple syntax highlighting (no collapsible tree)
    /// Used when bundle resources fail to load
    static let jsonViewer = #"""
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="utf-8">
    <style>
        body {
            margin: 0;
            padding: 8px 12px;
            background: #1e1e2e;
            color: #cdd6f4;
            font-family: -apple-system, "SF Mono", Menlo, Monaco, Consolas, monospace;
            font-size: 13px;
        }
        pre { margin: 0; padding: 0; white-space: pre-wrap; word-wrap: break-word; }
        .string { color: #a6e3a1; }
        .number { color: #fab387; }
        .boolean { color: #f38ba8; }
        .null { color: #6c7086; }
        .key { color: #89b4fa; }
    </style>
    </head>
    <body>
    <pre id="json"></pre>
    <script>
    function syntaxHighlight(json) {
        json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
            function (match) { var cls = 'number'; if (/^"/.test(match)) { cls = /:$/.test(match) ? 'key' : 'string'; } else if (/true|false/.test(match)) { cls = 'boolean'; } else if (/null/.test(match)) { cls = 'null'; } return '<span class="' + cls + '">' + match + '</span>'; });
    }
    window.renderJson = function(jsonString) {
        try {
            var obj = JSON.parse(jsonString);
            document.getElementById('json').innerHTML = syntaxHighlight(JSON.stringify(obj, null, 2));
        } catch(e) { document.getElementById('json').innerHTML = '<span style="color:#f38ba8;">JSON Error: ' + e + '</span>'; }
    };
    </script>
    </body>
    </html>
    """#
}

// MARK: - Statistics Box
struct StatBox: View {
    let title: String
    let value: String

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(.title2, design: .monospaced))
                .fontWeight(.bold)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Color.secondary.opacity(0.1))
        .cornerRadius(6)
    }
}

// MARK: - Event Row
struct EventRow: View {
    let event: CaptureEvent

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "app.fill")
                .foregroundColor(.secondary)

            VStack(alignment: .leading, spacing: 2) {
                Text(event.appName)
                    .font(.system(.body, design: .default))

                Text(event.windowTitle)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }

            Spacer()

            Text(event.timestampString)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Preview
#if DEBUG
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .frame(width: 900, height: 580)
    }
}
#endif
