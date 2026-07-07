// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MathRenderer",
    products: [
        .library(
            name: "MathRenderer",
            targets: ["MathRenderer"]
        )
    ],
    targets: [
        .target(
            name: "MathRenderer",
            path: "swift/Sources/MathRenderer"
        )
    ]
)
