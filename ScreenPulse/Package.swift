// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ScreenPulse",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/gonzalezreal/swift-markdown-ui", from: "2.0.0")
    ],
    targets: [
        .executableTarget(
            name: "ScreenPulse",
            dependencies: [
                .product(name: "MarkdownUI", package: "swift-markdown-ui")
            ],
            path: "Sources/ScreenPulse"
        )
    ]
)
