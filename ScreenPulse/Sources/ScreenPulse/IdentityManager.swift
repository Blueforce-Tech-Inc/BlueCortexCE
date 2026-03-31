import Foundation

enum IdentityManager {
    private static let deviceIdKey = "screenpulse.deviceId"

    /// Device ID: Fixed in UserDefaults, generated on first run, persists across sessions
    static var deviceId: String {
        if let existing = UserDefaults.standard.string(forKey: deviceIdKey) {
            return existing
        }
        let new = UUID().uuidString
        UserDefaults.standard.set(new, forKey: deviceIdKey)
        return new
    }

    /// User ID: hostname + deviceId prefix combination (for multi-machine distinction)
    static var userId: String {
        let host = Host.current().localizedName ?? "unknown-mac"
        return "\(host)@\(deviceId.prefix(8))"
    }

    /// Session ID: New UUID each app launch
    static let sessionId: String = UUID().uuidString
}
