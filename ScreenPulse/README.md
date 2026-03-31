# ScreenPulse

> **中文版**: [README-zh-CN.md](README-zh-CN.md)

**ScreenPulse** is a macOS menu bar application that captures screen content from the foreground window using the macOS Accessibility API and streams observations to the Cortex CE memory system.

```
┌─────────────────────────────────────────────────────────────────┐
│                        ScreenPulse                               │
│                                                                  │
│   [👁️ Menu Bar Icon]  ←  LSUIElement app (no Dock icon)        │
│                                                                  │
│   Captures:  Safari, VS Code, Slack, Terminal...               │
│                  ↓  POST /api/ingest/observation               │
│            ┌─────────────────────┐                              │
│            │    Cortex CE        │                              │
│            │  (Memory System)    │                              │
│            └─────────────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
```

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
  - [1. Start Cortex CE Backend](#1-start-cortex-ce-backend)
  - [2. Build ScreenPulse](#2-build-screenpulse)
  - [3. Grant Accessibility Permissions](#3-grant-accessibility-permissions)
  - [4. Run ScreenPulse](#4-run-screenpulse)
- [Development Guide](#development-guide)
  - [Project Structure](#project-structure)
  - [Building](#building)
  - [Debugging](#debugging)
  - [Running Tests](#running-tests)
- [Configuration](#configuration)
  - [Endpoint Configuration](#endpoint-configuration)
  - [Ignored Bundle IDs](#ignored-bundle-ids)
  - [Capture Interval](#capture-interval)
- [Console Window Guide](#console-window-guide)
- [Troubleshooting](#troubleshooting)
  - [Accessibility Permission Denied](#accessibility-permission-denied)
  - [Cannot Connect to Backend](#cannot-connect-to-backend)
  - [App Not Appearing in Dock](#app-not-appearing-in-dock)
- [Security & Privacy](#security--privacy)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Features

| Feature | Description |
|---------|-------------|
| **Menu Bar App** | Runs as `LSUIElement` (no Dock icon), accessible via menu bar |
| **Timed Polling** | Captures foreground window every 5 seconds |
| **Manual Capture** | Click "Capture Now" for instant capture |
| **Local Logging** | Logs to `~/Library/Application Support/ScreenPulse/logs/` |
| **API Integration** | POSTs to Cortex CE `/api/ingest/observation` |
| **3-Layer Data Model** | AX snapshot → Semantic fields → Markdown narrative |
| **Privacy-Safe** | Filters passwords, skips secure text fields |
| **Ignore List** | Configurable Bundle ID ignore list |

### V1.0 Capabilities

- Menu bar status indicator (eye icon)
- Console window with event list and detail view
- 5-second interval polling capture
- Local file logging
- HTTP POST to memory system
- In-memory cache (200 events, rolling)

### V1.1 (Planned)

- AXObserver event-driven capture (no polling)
- App-specific parsers (browser URL extraction, IDE file path, etc.)
- Retry queue for failed API calls

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ScreenPulse Architecture                     │
└─────────────────────────────────────────────────────────────────────┘

  ┌──────────────┐      ┌──────────────────┐      ┌─────────────────┐
  │  Menu Bar    │ ──── │  AppDelegate     │ ──── │ ScreenCapture   │
  │  (NSStatusItem)│      │  (NSApplication │      │ Manager         │
  └──────────────┘      └──────────────────┘      │                 │
                                                    │  ┌───────────┐ │
  ┌──────────────┐      ┌──────────────────┐      │  │ Identity  │ │
  │  Console     │ ──── │  ContentView     │ ──── │  │ Manager   │ │
  │  Window      │      │  (SwiftUI)       │      │  └───────────┘ │
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
  │         Cortex CE Backend            │
  │   POST /api/ingest/observation       │
  │   (localhost:37777)                  │
  └─────────────────────────────────────┘
```

### Data Flow

```
Foreground App Window
        ↓ (AXUIElement API)
ScreenCaptureManager.performCapture()
        ↓
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Layer 1: AXNode │ →  │ Layer 2:        │ →  │ Layer 3:        │
│ (Raw AX Tree)   │    │ SemanticFields  │    │ Markdown        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                    ↓
                                         ┌─────────────────┐
                                         │  narrative      │
                                         │  (LLM context)  │
                                         └─────────────────┘
                                                    ↓
                                         ┌─────────────────┐
                                         │ extractedData   │
                                         │ (structured)    │
                                         └─────────────────┘
                                                    ↓
                                         POST to Cortex CE
```

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| macOS | 13.0+ (Ventura) | Required for SwiftUI menu bar APIs |
| Swift | 5.9+ | Bundled with Xcode 15+ |
| Xcode | 15.0+ | Optional (swift build works too) |
| Cortex CE Backend | Running | Default: `localhost:37777` |

### Required Permissions

- **Accessibility**: Required to read window content from other apps
- **Network**: Required to POST observations to memory system

---

## Quick Start

### 1. Start Cortex CE Backend

First, ensure the Cortex CE backend is running:

```bash
# Navigate to backend directory
cd /path/to/BlueCortexCE/backend

# Start the backend (if not already running)
./mvnw spring-boot:run

# Or using the packaged JAR
java -jar target/claude-mem-java-*.jar
```

The backend should be available at `http://localhost:37777`.

**Verify backend is running:**
```bash
curl http://localhost:37777/actuator/health
# Expected: {"status":"UP"}
```

### 2. Build ScreenPulse

```bash
# Navigate to ScreenPulse directory
cd /path/to/BlueCortexCE/ScreenPulse

# Build the project
swift build

# Build for release (optimized)
swift build -c release
```

**Output:** `.build/release/ScreenPulse` (release build)

### 3. Grant Accessibility Permissions

ScreenPulse requires accessibility permissions to read window content.

1. **First launch** will prompt automatically, OR
2. Navigate manually: **System Settings → Privacy & Security → Accessibility**
3. Enable **ScreenPulse** (or **Terminal** if running from command line)

![Accessibility Settings](https://help.apple.com/assets/5AF9D涕涕C8BB9E1AF9D涕涕E1AF9D涕涕E1AF9D涕涕.png)

> **Important:** If running via Xcode or Terminal, ensure that app (Xcode/Terminal) is enabled in Accessibility.

### 4. Run ScreenPulse

#### Option A: Run from Command Line (Development)

```bash
# Debug build
swift build && swift run

# Or run the compiled binary
.build/debug/ScreenPulse
# or
.build/release/ScreenPulse
```

#### Option B: Package as .app and Run

```bash
# Create app bundle structure
APP=ScreenPulse.app
mkdir -p ${APP}/Contents/MacOS
mkdir -p ${APP}/Contents/Resources
cp .build/release/ScreenPulse ${APP}/Contents/MacOS/
cp Info.plist ${APP}/Contents/

# Sign (ad-hoc for development)
codesign --sign - --entitlements entitlements.plist --force --deep ${APP}

# Verify signature
codesign --verify --verbose ${APP}

# Run
open ${APP}
```

#### Option C: Open in Xcode

```bash
# Generate Xcode project (optional)
swift package generate-xcodeproj

# Open in Xcode
open ScreenPulse.xcodeproj
```

Then press **Cmd+R** to build and run.

---

## Development Guide

### Project Structure

```
ScreenPulse/
├── Package.swift                    # Swift Package Manager config
├── Info.plist                       # App metadata (LSUIElement=true)
├── entitlements.plist               # Signing entitlements
├── README.md                        # This file
└── Sources/ScreenPulse/
    ├── App.swift                    # Entry point (minimal)
    ├── AppDelegate.swift            # @main, menu bar, window setup
    ├── ContentView.swift            # SwiftUI console UI
    ├── ScreenCaptureManager.swift    # Core capture logic
    ├── IdentityManager.swift         # Device/session/user ID
    └── ObservationPayload.swift      # API payload + sender
```

### Building

| Command | Description |
|---------|-------------|
| `swift build` | Debug build |
| `swift build -c release` | Release build |
| `swift build --verbose` | Verbose output |
| `swift build -Xswiftc -warnings-as-errors` | Treat warnings as errors |

### Debugging

#### Debug Build with Xcode

```bash
# Generate Xcode project
swift package generate-xcodeproj

# Open in Xcode
open ScreenPulse.xcodeproj
```

In Xcode:
1. Select **Product → Scheme → Edit Scheme**
2. Set **Run → Info → Build Configuration** to **Debug**
3. Press **Cmd+R** to run

#### Debug with VS Code

Install the **CodeLLDB** extension, then create `.vscode/launch.json`:

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

#### Printing Debug Info

The app prints to stdout/stderr. View logs:

```bash
# Run with output visible
.build/debug/ScreenPulse

# Or redirect to file
.build/debug/ScreenPulse 2>&1 | tee screenpulse.log
```

Key debug prints in the code:
- `ScreenCaptureManager.swift:306` - "ScreenPulse: Successfully posted observation"
- `ScreenCaptureManager.swift:308` - "ScreenPulse: Failed to post observation: \(error)"

#### Runtime Debugging

The Console window shows:
- Capture events list (left panel)
- Event details (right panel)
- Status messages (top left)
- Endpoint response (via print statements)

### Running Tests

**Note:** V1.0 does not include unit tests. To add tests:

```bash
# Create test target (add to Package.swift)
swift package init --type test

# Run tests
swift test
```

---

## Configuration

### Endpoint Configuration

The default endpoint is `http://localhost:37777/api/ingest/observation`.

**To change:**
1. Open ScreenPulse Console (menu bar → "Open Console")
2. Edit the **Memory System Endpoint** text field
3. Click **Save**

The endpoint is persisted to `UserDefaults` (key: `screenpulse.endpoint`).

### Ignored Bundle IDs

Default ignored apps:
- `com.agilebits.onepassword7` (1Password 7)
- `com.apple.systempreferences` (System Settings)
- `com.apple.keychainaccess` (Keychain Access)

**To add more:**
1. Open Console → **Ignored Bundle IDs**
2. Enter Bundle ID (e.g., `com.apple.Safari`)
3. Click **Add**

**To find Bundle ID of an app:**
```bash
osascript -e 'id of app "Safari"'  # Returns: com.apple.Safari
```

### Capture Interval

Currently fixed at **5 seconds** (hardcoded in `ScreenCaptureManager.swift:136`).

To change, edit:
```swift
// ScreenCaptureManager.swift, line 136
let interval: TimeInterval = 5.0  // Change to desired interval
```

---

## Console Window Guide

```
┌─────────────────────────────────────────────────────────────────────┐
│  [●] Idle                                        [Eye Icon]        │
├────────────────────────┬────────────────────────────────────────────┤
│  STATUS                │  EVENT DETAILS                             │
│  ─────────             │  ───────────────                           │
│  [●] Running           │  (Select an event from the left)           │
│                        │                                            │
│  CONTROLS              │                                            │
│  ─────────             │                                            │
│  [✓] Enable Capture    │                                            │
│  [Capture Now]         │                                            │
│                        │                                            │
│  ENDPOINT              │                                            │
│  ─────────             │                                            │
│  [http://localhost:3..│                                            │
│  [Save]                │                                            │
│                        │                                            │
│  IGNORED BUNDLE IDS    │                                            │
│  ─────────             │                                            │
│  + com.apple.Safari   │                                            │
│    com.agilebits.onep..│                                           │
│                        │                                            │
│  CAPTURE EVENTS        │                                            │
│  ─────────             │                                            │
│  🟢 Safari - "Apple"   │                                            │
│    VS Code - "main.sw" │                                            │
│    Terminal - "$ ls"   │                                            │
└────────────────────────┴────────────────────────────────────────────┘
```

| Element | Description |
|---------|-------------|
| **Status Indicator** | Green = capturing, Gray = idle |
| **Enable Capture** | Toggle switch for capture on/off |
| **Capture Now** | Manually trigger one capture |
| **Endpoint** | Memory system URL (editable) |
| **Ignored Bundle IDs** | Apps to skip during capture |
| **Capture Events** | List of captured events |
| **Event Details** | Full text of selected event |

---

## Troubleshooting

### Accessibility Permission Denied

**Symptom:** Status shows "⚠️ 权限未授权", capture doesn't work

**Solution:**
1. Go to **System Settings → Privacy & Security → Accessibility**
2. Ensure ScreenPulse is **enabled**
3. If running via Terminal, ensure **Terminal** (or your terminal app) is enabled
4. Restart ScreenPulse after granting permission

### Cannot Connect to Backend

**Symptom:** Console shows "Failed to post observation" in logs

**Solutions:**
1. Verify Cortex CE is running:
   ```bash
   curl http://localhost:37777/actuator/health
   ```
2. Check endpoint URL in Console window
3. Check backend CORS settings if accessing from different origin

### App Not Appearing in Dock

**This is expected behavior** - ScreenPulse uses `LSUIElement=true` to hide from Dock.

**To quit:**
- Menu bar icon → "Quit ScreenPulse"
- Or press **Cmd+Q** when Console window is focused

### No Events Captured

**Possible causes:**
1. No text in foreground window (some apps have no accessible text)
2. App is in ignore list (check ignored Bundle IDs)
3. Window has no focused text field

**Test:**
1. Open Safari and navigate to a webpage
2. Ensure "Enable Capture" is ON
3. Wait 5 seconds for capture
4. Select event in list to view details

### Build Errors

**Error: `cannot find 'AXIsProcessTrustedWithOptions' in scope`**

Ensure `import ApplicationServices` is present at top of files using Accessibility API.

**Error: `expressions are not allowed at top level`**

Ensure `@main` attribute is used correctly and no loose expressions exist at file scope.

---

## Security & Privacy

### What ScreenPulse Captures

- **Text content** from the foreground window's accessibility tree
- **Window titles**
- **App names and Bundle IDs**
- **Timestamps** of captures

### What ScreenPulse Does NOT Capture

- **Passwords**: `AXSecureTextField` elements are explicitly skipped
- **Password-like fields**: Fields with "password" in the title are skipped
- **Images/Media**: Only text content is captured
- **Clipboard**: Does not read clipboard contents

### Data Storage

- **Local logs**: `~/Library/Application Support/ScreenPulse/logs/`
  - Daily rotating log files (`yyyy-MM-dd.log`)
  - Format: `[HH:mm:ss] [bundleId] [windowTitle]\n[text]\n---\n`
- **In-memory cache**: Max 200 events, not persisted

### Network Security

- Default endpoint is `localhost` (local only)
- No HTTPS by default (configure reverse proxy for production)
- No authentication on the API endpoint (assumes localhost trust)

---

## Roadmap

| Version | Milestone | Status |
|---------|-----------|--------|
| V1.0 | Timed polling, menu bar, POST to Cortex CE | **Complete** |
| V1.1 | AXObserver event-driven, app-specific parsers | Planned |
| V2.0 | Screenshot capture, image description | Planned |

---

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Workflow

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/BlueCortexCE.git
cd BlueCortexCE/ScreenPulse

# Create feature branch
git checkout -b feature/my-feature

# Build and test
swift build

# Commit
git add .
git commit -m "Add feature: description"

# Push and create PR
git push origin feature/my-feature
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](../../LICENSE) file for details.

---

## Related Documents

| Document | Description |
|----------|-------------|
| [`docs/drafts/screenpulse-implementation-plan.md`](docs/drafts/screenpulse-implementation-plan.md) | Full implementation specification |
| [`docs/drafts/screenpulse-implementation-progress.md`](docs/drafts/screenpulse-implementation-progress.md) | Implementation progress tracker |
| [Cortex CE README](../../README.md) | Backend memory system documentation |

---

## Support

For issues or questions:
1. Check [Troubleshooting](#troubleshooting) above
2. Search [existing issues](https://github.com/Blueforce-Tech-Inc/BlueCortexCE/issues)
3. Open a new issue with:
   - macOS version
   - Swift version (`swift --version`)
   - ScreenPulse build output
   - Steps to reproduce
