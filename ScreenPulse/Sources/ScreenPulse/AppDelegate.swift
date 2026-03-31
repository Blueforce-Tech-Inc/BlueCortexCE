import AppKit
import SwiftUI
import ApplicationServices

@main
struct ScreenPulseApp {
    static func main() {
        let app = NSApplication.shared
        let delegate = AppDelegate()
        app.delegate = delegate
        app.run()
    }
}

class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private var popover: NSPopover!
    private var consoleWindow: NSWindow!

    func applicationDidFinishLaunching(_ notification: Notification) {
        setupStatusItem()
        setupConsoleWindow()
        checkAccessibilityPermissions()
    }

    private func setupStatusItem() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)

        if let button = statusItem.button {
            button.image = NSImage(systemSymbolName: "eye.circle.fill", accessibilityDescription: "ScreenPulse")
            button.image?.isTemplate = true
        }

        let menu = NSMenu()

        menu.addItem(NSMenuItem(title: "Open Console", action: #selector(openConsole), keyEquivalent: "o"))
        menu.addItem(NSMenuItem(title: "Start Capture", action: #selector(toggleCapture), keyEquivalent: "s"))

        menu.addItem(NSMenuItem.separator())

        menu.addItem(NSMenuItem(title: "Quit ScreenPulse", action: #selector(quitApp), keyEquivalent: "q"))

        statusItem.menu = menu
    }

    private func setupConsoleWindow() {
        let contentView = ContentView()

        consoleWindow = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 900, height: 580),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        consoleWindow.title = "ScreenPulse Console"
        consoleWindow.contentView = NSHostingView(rootView: contentView)
        consoleWindow.minSize = NSSize(width: 700, height: 400)
        consoleWindow.center()
    }

    private func checkAccessibilityPermissions() {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        let trusted = AXIsProcessTrustedWithOptions(options)

        if !trusted {
            ScreenCaptureManager.shared.statusMessage = "⚠️ 权限未授权"
        }
    }

    @objc private func openConsole() {
        consoleWindow.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    @objc private func toggleCapture() {
        let manager = ScreenCaptureManager.shared

        if manager.isCapturing {
            manager.stopCapturing()
            updateMenuTitle(isCapturing: false)
        } else {
            manager.startCapturing()
            updateMenuTitle(isCapturing: true)
        }
    }

    private func updateMenuTitle(isCapturing: Bool) {
        guard let menu = statusItem.menu else { return }

        let title = isCapturing ? "Pause Capture" : "Start Capture"
        if let item = menu.item(withTitle: "Start Capture") {
            item.title = title
        } else if let item = menu.item(withTitle: "Pause Capture") {
            item.title = title
        }

        if let button = statusItem.button {
            let symbolName = isCapturing ? "eye.slash.circle" : "eye.circle.fill"
            button.image = NSImage(systemSymbolName: symbolName, accessibilityDescription: "ScreenPulse")
            button.image?.isTemplate = true
        }
    }

    @objc private func quitApp() {
        NSApp.terminate(nil)
    }
}
