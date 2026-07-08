import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, unlinkSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)));

execFileSync(process.execPath, [join(repoRoot, "scripts/build-mathjax-core-runtime.mjs")], {
    stdio: "inherit"
});

execFileSync(process.execPath, [join(repoRoot, "scripts/build-latex-to-svg.mjs")], {
    stdio: "inherit"
});

const copies = [
    [
        "packages/mathjax-core/generated/mathjax-core-runtime.js",
        "packages/swift-math/Sources/SwiftMath/Resources/MathJax/mathjax-core-runtime.js"
    ],
    [
        "packages/mathjax-core/generated/latex-to-svg.js",
        "packages/swift-math/Sources/SwiftMath/Resources/MathJax/latex-to-svg.js"
    ],
    [
        "packages/mathjax-core/LICENSE-MathJax.txt",
        "packages/swift-math/Sources/SwiftMath/Resources/MathJax/LICENSE-MathJax.txt"
    ],
    [
        "packages/mathjax-core/generated/mathjax-core-runtime.js",
        "packages/compose-math/src/main/assets/math-renderer/mathjax-core-runtime.js"
    ],
    [
        "packages/mathjax-core/generated/latex-to-svg.js",
        "packages/compose-math/src/main/assets/math-renderer/latex-to-svg.js"
    ],
    [
        "packages/mathjax-core/LICENSE-MathJax.txt",
        "packages/compose-math/src/main/assets/math-renderer/LICENSE-MathJax.txt"
    ],
    [
        "packages/mathjax-core/src/latex-to-svg-core.ts",
        "packages/react-math/src/generated/latex-to-svg-core.ts"
    ],
    [
        "packages/mathjax-core/src/react-latex-to-svg.ts",
        "packages/react-math/src/generated/react-latex-to-svg.ts"
    ]
];

const staleFiles = [
    "shared/mathjax/mathjax-renderer.js",
    "shared/mathjax/mathjax-bridge.js",
    "shared/mathjax/mathjax-bridge.ts",
    "shared/mathjax/mathjax-core-runtime.js",
    "shared/mathjax/latex-to-svg.js",
    "shared/mathjax/latex-to-svg-core.ts",
    "shared/mathjax/native-latex-to-svg.ts",
    "shared/mathjax/react-latex-to-svg.ts",
    "shared/mathjax/LICENSE-MathJax.txt",
    "packages/swift-math/Sources/SwiftMath/Resources/MathJax/mathjax-renderer.js",
    "packages/swift-math/Sources/SwiftMath/Resources/MathJax/mathjax-bridge.js",
    "packages/compose-math/src/main/assets/math-renderer/mathjax-renderer.js",
    "packages/compose-math/src/main/assets/math-renderer/mathjax-bridge.js",
    "packages/react-math/src/generated/mathjax-bridge.ts",
    "shared/mathjax/mathjax-lite-runtime.js",
    "packages/swift-math/Sources/SwiftMath/Resources/MathJax/mathjax-lite-runtime.js",
    "packages/compose-math/src/main/assets/math-renderer/mathjax-lite-runtime.js",
    "scripts/build-mathjax-lite-runtime.mjs"
];

for (const [source, destination] of copies) {
    const destinationPath = join(repoRoot, destination);
    mkdirSync(dirname(destinationPath), { recursive: true });
    copyFileSync(join(repoRoot, source), destinationPath);
    console.log(`synced ${source} -> ${destination}`);
}

for (const staleFile of staleFiles) {
    const stalePath = join(repoRoot, staleFile);
    if (existsSync(stalePath)) {
        unlinkSync(stalePath);
        console.log(`removed stale ${staleFile}`);
    }
}
