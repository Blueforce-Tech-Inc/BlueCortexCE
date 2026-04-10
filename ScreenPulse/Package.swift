// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ScreenPulse",
    platforms: [.macOS(.v13)],
    dependencies: [],
    targets: [
        .executableTarget(
            name: "ScreenPulse",
            dependencies: [],
            path: "Sources/ScreenPulse",
            resources: [.process("Resources")]
        )
    ]
)
