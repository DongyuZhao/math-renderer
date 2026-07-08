import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const syncedPairs = [
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

test("platform MathJax assets are synced from packages/mathjax-core", () => {
    for (const [source, destination] of syncedPairs) {
        assert.equal(readFileSync(destination, "utf8"), readFileSync(source, "utf8"), destination);
    }
});

test("mathjax-core native LaTeX-to-SVG IIFE is generated from source TypeScript", () => {
    const output = execFileSync(process.execPath, ["scripts/build-latex-to-svg.mjs", "--check"], {
        encoding: "utf8"
    });

    assert.match(output, /latex-to-svg\.js is up to date/);
});

test("mathjax-core runtime is generated from pinned mathjax-full", () => {
    const output = execFileSync(
        process.execPath,
        ["scripts/build-mathjax-core-runtime.mjs", "--check"],
        { encoding: "utf8" }
    );

    assert.match(output, /mathjax-core-runtime\.js is up to date/);
});

test("React MathJax dependency is pinned exactly", () => {
    const reactPackage = JSON.parse(readFileSync("packages/react-math/package.json", "utf8"));
    const reactLock = JSON.parse(readFileSync("packages/react-math/package-lock.json", "utf8"));
    const declaredVersion = reactPackage.dependencies["mathjax-full"];

    assert.match(declaredVersion, /^\d+\.\d+\.\d+$/);
    assert.equal(reactLock.packages[""].dependencies["mathjax-full"], declaredVersion);
    assert.equal(reactLock.packages["node_modules/mathjax-full"].version, declaredVersion);
});

test("React package delegates MathJax binding to generated shared host", () => {
    const indexSource = readFileSync("packages/react-math/src/index.tsx", "utf8");

    assert.doesNotMatch(indexSource, /mathjax-full/);
    assert.match(indexSource, /renderLatexToSvg/);
});

test("legacy native bridge and renderer assets are no longer packaged", () => {
    const legacyPaths = [
        "shared/mathjax/mathjax-renderer.js",
        "shared/mathjax/mathjax-bridge.js",
        "shared/mathjax/mathjax-bridge.ts",
        "packages/swift-math/Sources/SwiftMath/Resources/MathJax/mathjax-renderer.js",
        "packages/swift-math/Sources/SwiftMath/Resources/MathJax/mathjax-bridge.js",
        "packages/compose-math/src/main/assets/math-renderer/mathjax-renderer.js",
        "packages/compose-math/src/main/assets/math-renderer/mathjax-bridge.js",
        "packages/react-math/src/generated/mathjax-bridge.ts",
        "shared/mathjax/mathjax-core-runtime.js",
        "shared/mathjax/latex-to-svg.js",
        "shared/mathjax/latex-to-svg-core.ts",
        "shared/mathjax/native-latex-to-svg.ts",
        "shared/mathjax/react-latex-to-svg.ts",
        "shared/mathjax/LICENSE-MathJax.txt",
        "shared/mathjax/mathjax-lite-runtime.js",
        "packages/swift-math/Sources/SwiftMath/Resources/MathJax/mathjax-lite-runtime.js",
        "packages/compose-math/src/main/assets/math-renderer/mathjax-lite-runtime.js",
        "scripts/build-mathjax-lite-runtime.mjs"
    ];

    for (const legacyPath of legacyPaths) {
        assert.equal(existsSync(legacyPath), false, legacyPath);
    }
});
