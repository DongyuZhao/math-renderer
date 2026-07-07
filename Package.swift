// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MathRenderer",
    platforms: [
        .iOS(.v15),
        .macOS(.v12),
        .tvOS(.v15),
        .watchOS(.v8),
        .visionOS(.v1),
    ],
    products: [
        .library(
            name: "MathRenderer",
            targets: ["MathRenderer"]
        ),
        .executable(
            name: "MathRendererSample",
            targets: ["MathRendererSample"]
        ),
    ],
    targets: [
        .target(
            name: "MathRenderer",
            path: "swift/Sources/MathRenderer",
            resources: [
                .process("Resources"),
            ]
        ),
        .executableTarget(
            name: "MathRendererSample",
            dependencies: ["MathRenderer"],
            path: "swift/SampleApp"
        ),
        .testTarget(
            name: "MathRendererTests",
            dependencies: ["MathRenderer"],
            path: "swift/Tests/MathRendererTests"
        ),
    ]
)
