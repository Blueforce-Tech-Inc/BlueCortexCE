# ScreenPulse Implementation Progress

**Last Updated**: 2026-04-09
**Status**: M1-M4 Complete, V1.0 Architecture Fixed (Timer Polling Mode)

## Overview

This file tracks the implementation progress of the ScreenPulse macOS App according to `screenpulse-implementation-plan.md`.

## Implementation Phases

| Phase | Description | Status | Notes |
|-------|-------------|--------|-------|
| M1 | Project skeleton, menu bar, console UI | **COMPLETE** | |
| M2 | Timed polling capture, local logs | **COMPLETE** | 5s interval |
| M3 | POST to memory system | **COMPLETE** | |
| M4 | Packaging, signing, build | **COMPLETE** | Build verified |
| M5 | AXObserver event-driven (V1.1) | **REVERTED** | Race condition issues, see Section X |
| M6 | App specialized parsers (V1.1) | Pending | |
| M7 | Reliability enhancements (V1.1) | Pending | |

## Completed Files

### Project Configuration
- `ScreenPulse/Package.swift` - Swift PM configuration
- `ScreenPulse/Info.plist` - App metadata with LSUIElement=true
- `ScreenPulse/entitlements.plist` - Sandbox disabled, network client enabled

### Source Files
- `ScreenPulse/Sources/ScreenPulse/App.swift` - Entry point placeholder
- `ScreenPulse/Sources/ScreenPulse/AppDelegate.swift` - Menu bar management + @main
- `ScreenPulse/Sources/ScreenPulse/ContentView.swift` - Console UI (SwiftUI)
- `ScreenPulse/Sources/ScreenPulse/ScreenCaptureManager.swift` - Core capture logic
- `ScreenPulse/Sources/ScreenPulse/IdentityManager.swift` - Device/session/user ID management
- `ScreenPulse/Sources/ScreenPulse/ObservationPayload.swift` - API payload structure + sender

## Backend Improvement Notes

Any backend improvements needed will be recorded here:

| Date | Issue | Status |
|------|-------|--------|
| 2026-04-09 | Self-capture crash: app captured own windows causing autolayout crashes on background threads | **FIXED** |
| 2026-04-09 | App activation policy: improper accessory/regular policy switching | **FIXED** |
| 2026-04-09 | List selection binding complexity | **FIXED** |
| 2026-03-31 | (none yet) | - |

## Architecture Issues (V1.0) - FIXED

All three-layer data model issues have been resolved:

| Layer | Issue | Resolution |
|-------|-------|------------|
| Layer 1 (AXSnapshot) | Was `nil` | ✅ Now builds full AXNode tree with role/title/value/url/position/size |
| Layer 2 (Semantic) | role always `"AXStaticText"` | ✅ Extracts headings/links/buttons/textFields with proper categorization |
| Layer 3 (Markdown) | Pure text concatenation | ✅ Renders structured Markdown with ## for headings, [text](url) for links |

### Important Clarification: TextBlock.text is Natural Text by Design

For `AXStaticText` elements, the `value` is inherently natural language text (a leaf node with no children). This is **expected and correct**:

```json
{
  "visibleTextBlocks": [
    { "role": "AXStaticText", "text": "Welcome to GitHub" },
    { "role": "AXLink", "text": "Pull requests", "url": "https://github.com/pulls" },
    { "role": "AXButton", "text": "New" }
  ]
}
```

**The structure comes from `role`, not from splitting the text.** The `text` field being natural text is the correct behavior - macOS AX API does not provide sub-word structure for static text content.

### Implementation Details

**Layer 1**: `ScreenCaptureManager.buildAXNodeTree()` replaces `collectText()`:
- Extracts role, title, value, description, url, position, size, isFocused, isSelected, isEnabled
- Uses `meaningfulAXRoles` set (includes AXWindow, AXSheet, AXDocument as containers)
- Recursively builds AXNode tree

**Layer 2**: `ObservationPayloadBuilder.extractSemanticFields()`:
- Traverses AXNode tree and categorizes nodes by role
- Extracts `headings[]` with level, `links[]` with URL, `visibleTextBlocks[]` with full context
- Tracks `focusedElementRole/Value` and `selectedText` (strongest intent signal)

**Layer 3**: `renderMarkdown()` enhanced:
- Headings render with proper `#` markers based on level
- Links render as `[text](url)`
- Buttons render as `[text]` with title in parentheses
- TextFields render as `**Title:** value`

## Pending Tasks

| Priority | Task | Status |
|----------|------|--------|
| HIGH | Fix Layer 1 extraction - build AXNode tree | **FIXED** |
| HIGH | Fix Layer 2 extraction - populate SemanticFields correctly | **FIXED** |
| HIGH | Fix visibleTextBlocks - include role/title/url/position/size | **FIXED** |
| MEDIUM | Enhance TextBlock struct in ObservationPayload.swift | **FIXED** |
| HIGH | Fix AXObserver event-driven capture (M5) - see Section X | **PENDING** |

## File Locations

- Project root: `ScreenPulse/`
- Source files: `ScreenPulse/Sources/ScreenPulse/`
- Plan document: `docs/drafts/screenpulse-implementation-plan.md`
- Progress file: `docs/drafts/screenpulse-implementation-progress.md`

## Build Instructions

```bash
cd ScreenPulse
swift build -c release
# Output: .build/release/ScreenPulse

# To package as .app:
# (see screenpulse-implementation-plan.md Section 6.3)
```

## Build Status

**Last build**: 2026-04-09 - SUCCESS (Timer Polling Mode)

```
swift build
Build complete! (0.76s)
```

**Note**: AXEventWatcher code was removed. Current mode: 5s timer polling only.

## Bug Fixes

| Date | Bug | Fix |
|------|-----|-----|
| 2026-04-09 | `AXWindow` not in meaningfulRoles caused root node to be filtered, resulting in nil AXSnapshot | Added AXWindow/AXSheet/AXDialog/AXDocument to meaningfulAXRoles set |

## Event-Driven Capture Issues (M5 - REVERTED)

### Attempted Implementation

Attempted to replace timer polling (5s) with **AXObserver event-driven capture** using:
- `kAXFocusedWindowChangedNotification` - triggers on window change
- `kAXMainWindowChangedNotification` - triggers on main window change
- `kAXTitleChangedNotification` - triggers on title change
- 1.5s debounce

### Problem: Race Condition

**Root Cause**: AXEventWatcher is tied to a **single app's PID**

When user switches apps:
```
1. User in Safari → watcher listens to Safari PID
2. User switches to Chrome → AXObserver fires event
3. handleEvent() calls captureOnce() (async, queued on captureQueue)
4. captureOnce() calls restartEventWatcherIfNeeded()
5. BUT performCapture() runs later on captureQueue
6. Meanwhile user quickly switches back to Safari
7. performCapture() runs, but watcher is now for Chrome (wrong app!)
```

### Impact

- Capture targets wrong app after app switches
- Inconsistent behavior depending on timing
- Some window types not captured at all

### Recommended Fixes (Not Yet Implemented)

| Option | Approach |
|--------|----------|
| **A** | Use system-wide AXObserver listening to `kAXApplicationActivatedNotification` |
| **B** | Use `NSWorkspace.didActivateApplicationNotification` (simpler approach) |
| **C** | Keep per-app watcher but fix race: capture current frontmost app, not assumed app |

### Current Status

**REVERTED**: Code was removed, timer polling (5s) restored as sole capture mechanism.

### Future Work

1. Implement Option B (NSWorkspace notifications) - simplest approach
2. Test thoroughly with various apps (Safari, Chrome, Terminal, Finder, etc.)
3. Re-enable event-driven capture only after fixing race condition

## Known Warnings (Non-blocking)

- `ObservationPayload.swift:6`: immutable property `schemaVersion` has initial value
- `ObservationPayload.swift:12`: immutable property `source` has initial value

These are intentional defaults for the ScreenPulseData struct.
