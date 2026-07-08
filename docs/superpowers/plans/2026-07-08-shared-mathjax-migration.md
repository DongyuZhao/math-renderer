# Shared LaTeX-to-SVG Migration Implementation Plan

**Goal:** keep MathJax parsing identical across React, Swift, and Android by sharing one LaTeX-to-SVG engine, while each platform renders the resulting SVG with native platform primitives.

**Architecture:** `packages/mathjax-core` owns the MathJax configuration, SVG payload contract, fallback behavior, and sanitization. Platform packages do not maintain separate MathJax wrappers. They call the shared host and render the returned SVG payload:

- React calls generated `renderLatexToSvg(...)` and mounts SVG markup with browser-native SVG.
- Swift calls `MathRenderLatexToSvg.renderJSON(...)` in its process-wide JavaScriptCore actor and rasterizes SVG for SwiftUI.
- Android calls `MathRenderLatexToSvg.renderJSON(...)` through its process-wide QuickJS service and renders SVG in Compose.

## Migration 1: Shared Native Runtime

**Files:**

- `packages/mathjax-core/generated/mathjax-core-runtime.js`
- `packages/mathjax-core/generated/latex-to-svg.js`
- `packages/mathjax-core/src/latex-to-svg-core.ts`
- `packages/mathjax-core/src/native-latex-to-svg.ts`
- `scripts/build-mathjax-core-runtime.mjs`
- `scripts/build-latex-to-svg.mjs`
- `scripts/sync-mathjax-assets.mjs`
- `packages/swift-math/Sources/SwiftMath/Resources/MathJax/*`
- `packages/compose-math/src/main/assets/math-renderer/*`

**Steps:**

- [x] Add a shared contract test proving Swift and Android packaged assets are byte-for-byte synced from `packages/mathjax-core`.
- [x] Generate `mathjax-core-runtime.js` from the pinned `mathjax-full@3.2.2` native-safe ES5 components.
- [x] Generate `latex-to-svg.js` from shared TypeScript core plus the native host.
- [x] Remove stale `mathjax-renderer.js`, `mathjax-bridge.js`, and `mathjax-bridge.ts` assets.
- [x] Switch Swift and Android to `MathRenderLatexToSvg.renderJSON(...)`.

## Migration 2: Shared React Host

**Files:**

- `packages/mathjax-core/src/react-latex-to-svg.ts`
- `packages/react-math/src/generated/latex-to-svg-core.ts`
- `packages/react-math/src/generated/react-latex-to-svg.ts`
- `packages/react-math/src/index.tsx`

**Steps:**

- [x] Add a shared contract test proving React generated files are synced from `packages/mathjax-core`.
- [x] Move MathJax binding code out of `packages/react-math/src/index.tsx` and into generated shared host code.
- [x] Keep React platform code focused on converting props to shared options and rendering returned SVG markup.
- [x] Keep React, Swift, and Android aligned on the same options: `standalone`, `fontSize`, and `scale`.

## Verification

- [x] `node --test test/shared-mathjax-assets.test.mjs`
- [x] `npm --prefix packages/react-math test`
- [x] `swift test` in `packages/swift-math`
- [x] `env JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ANDROID_HOME=/Users/donz/Library/Android/sdk ./gradlew :packages:compose-math:testDebugUnitTest --rerun-tasks`
