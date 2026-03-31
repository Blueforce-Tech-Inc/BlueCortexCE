// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ScreenPulse",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "ScreenPulse",
            path: "Sources/ScreenPulse"
        )
    ]
)
