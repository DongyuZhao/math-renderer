// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "swift-math",
    platforms: [
        .iOS("16.0"),
        .macOS("13.0"),
    ],
    products: [
        .library(
            name: "swift-math",
            targets: ["SwiftMath"]
        )
    ],
    targets: [
        .target(
            name: "SwiftMath",
            resources: [
                .process("Resources")
            ]
        ),
        .testTarget(
            name: "SwiftMathTests",
            dependencies: ["SwiftMath"],
            exclude: ["VisualSnapshots"]
        ),
    ]
)
