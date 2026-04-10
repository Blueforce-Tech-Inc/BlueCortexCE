import SwiftUI

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
            Text("Event Details")
                .font(.headline)

            if let event = manager.selectedEvent {
                TextEditor(text: .constant(event.fullText))
                    .font(.system(.body, design: .monospaced))
                    .scrollContentBackground(.hidden)
                    .background(Color(nsColor: .textBackgroundColor))
                    .border(Color.secondary.opacity(0.3))
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
