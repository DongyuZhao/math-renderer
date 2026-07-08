# math-renderer

Multi-platform MathJax-based math rendering packages.

## Packages

- `packages/react-math`: React component and SVG renderer.
- `packages/swift-math`: Swift Package with MathJax SVG rendering plus native rasterization helpers.
- `packages/compose-math`: Android Compose AAR package with QuickJS-backed MathJax execution and native SVG rasterization.
- `packages/mathjax-core`: private shared LaTeX-to-SVG core and generated MathJax runtime used by all platform packages.

All packages use MathJax to render TeX into SVG and expose the same core options:

- `displayMode`: inline or block.
- `fontSize`: CSS pixels / points / sp, depending on platform.
- `scale`: MathJax output scale.

## Shared MathJax Assets

`packages/mathjax-core` is the source of truth for LaTeX-to-SVG behavior. MathJax is only used inside shared host code to produce a stable SVG payload; each platform renders that SVG using its own native surface.

- `packages/mathjax-core/src/latex-to-svg-core.ts` contains the platform-neutral payload contract and SVG sanitization/fallback behavior.
- `packages/mathjax-core/generated/mathjax-core-runtime.js` is generated from the exact `mathjax-full` dependency pinned by `packages/react-math`, using native-safe MathJax ES5 components.
- `packages/mathjax-core/generated/latex-to-svg.js` is generated from the shared core plus the native host for Swift and Android.
- `packages/react-math/src/generated/react-latex-to-svg.ts` is synchronized from the shared React host, which uses the same core and pinned MathJax package.

Run this after changing `packages/mathjax-core` files:

```sh
npm run sync:mathjax
```

The sync command builds the MathJax core runtime, builds the native LaTeX-to-SVG IIFE, then copies shared assets into:

- `packages/swift-math/Sources/SwiftMath/Resources/MathJax`
- `packages/compose-math/src/main/assets/math-renderer`
- `packages/react-math/src/generated`

Platform code remains responsible only for executing the host and rendering the returned SVG:

- Swift evaluates `MathRenderLatexToSvg.renderJSON(...)` through one process-wide JavaScriptCore context owned by a background actor, then rasterizes the SVG for SwiftUI or attributed-string attachments.
- Android evaluates `MathRenderLatexToSvg.renderJSON(...)` through one process-wide QuickJS runtime owned by a background service, parses the SVG with a hardened native parser, and rasterizes it to a tinted Compose `ImageBitmap`.
- React calls generated `renderLatexToSvg(...)` and mounts the returned SVG markup with the browser's native SVG renderer.

Swift and Android initialize MathJax once and submit all render requests to a FIFO queue with maximum concurrency one. Cancelling a queued request removes it before evaluation; cancelling an active request suppresses result delivery after the platform JavaScript call finishes.

Common formula coverage is shared through `packages/mathjax-core/fixtures/common-formulas.tsv`
(columns: `id, category, level, displayMode, fontSize, scale, tex`). The ~59-formula corpus
spans elementary and advanced mathematics, physics, and chemistry from primary through university
levels, with chemical notation provided by mhchem (`\ce{...}`). Every platform's tests consume
this one file, so adding a row extends
coverage everywhere. `packages/mathjax-core/fixtures/formulas.mjs` (Node) and `parse-formulas.mjs`
(browser-safe) load it.

## Unified Render State API

All public platform renderers publish the same state lifecycle:

1. `pending`
2. `succeeded` with the platform render artifact
3. `failed` with fallback TeX text and a failure reason

Platform spellings follow each ecosystem:

- Swift: `MathRenderer.render(...) -> AsyncStream<MathRenderState>`
- Android: `MathRenderer.render(...) -> Flow<MathRenderState>`
- React: `renderMath(...) -> AsyncGenerator<MathRenderState>`

MathJax parse errors are terminal failures across all three platforms, so consumers can consistently show the fallback TeX instead of rendering an error SVG.
Fallback text uses standard LaTeX math delimiters: `\(...\)` for inline formulas and `\[...\]` for block formulas.

Color and text scaling are handled in each platform renderer:

- React SVG output uses `currentColor`; the `Math` component sets wrapper `fontSize` from `fontSize * scale` so browser SVG sizing follows CSS text sizing.
- SwiftUI `MathText` follows `colorScheme` by default, accepts an explicit `CGColor`, uses `@ScaledMetric` for Dynamic Type, and rasterizes at the environment display scale.
- Android `MathText` uses `LocalContentColor`, folds `fontSizeSp`, `scale`, and `Density.fontScale` into the em size, and rasterizes the SVG to a pixel-resolution bitmap that is drawn with `Image`.

## Tests

Testing is split along two axes:

- **Generation** — does the shared MathJax core produce the right SVG? Verified once, at the
  shared layer, by the **golden-SVG** test: it renders every corpus formula and byte-compares
  against committed `packages/mathjax-core/test/golden/svg/<id>.svg`. Since all platforms render this same markup,
  this is the single source of truth for SVG correctness.
- **Rasterization** — does each platform turn that SVG into the right pixels? Verified by
  per-platform **pixel snapshots**, one PNG per formula, named to match the golden (`<id>.png`).

```sh
npm run test:shared     # golden SVG (all formulas) + asset checks
npm run test:react      # React Vitest unit/component (jsdom)
npm run test:swift      # Swift contract + unit tests
npm run test:android    # Android JVM unit tests
```

Regenerate golden SVGs (after changing the corpus): `npm run test:shared:update`.

### Pixel snapshots (rasterization)

One baseline per formula per platform. Record, review the diff, then commit:

| Platform | Tool                                      | Verify / Record                               | Baselines                                                           |
| -------- | ----------------------------------------- | --------------------------------------------- | ------------------------------------------------------------------- |
| React    | Vitest Browser Mode (Playwright chromium) | `npm run snapshot:react:visual` / `:record`   | `packages/react-math/test/__screenshots__/`                         |
| Swift    | XCTest CoreGraphics raster                | `npm run snapshot:swift:visual` / `:record`   | `packages/swift-math/Tests/SwiftMathTests/VisualSnapshots/<id>.png` |
| Android  | Roborazzi + Robolectric (JVM)             | `npm run snapshot:android:visual` / `:record` | `packages/compose-math/src/test/screenshots/<id>.png`               |

- React pixel tests render `<Math>` in a real browser; `packages/react-math/vitest.config.ts`
  shims `__dirname`/`global` so the bundled `mathjax-full` runs client-side.
- Android pixel tests run on the JVM (no emulator) via Roborazzi + Robolectric, rendering each
  formula's golden SVG through the same pure-Kotlin parser + rasterizer the app uses (no JavaScript runtime).
  Inputs are generated from the corpus with `npm run gen:compose-goldens` (golden SVGs copied to
  the test classpath + `GoldenCorpus.kt`). Roborazzi keeps the project on AGP 9 — the official
  `com.android.compose.screenshot` plugin does not yet support AGP 9.

The on-device Android contract test (`MathJaxRuntimeInstrumentationTest`) renders every corpus
formula through the process-wide QuickJS runtime and MathJax service on an emulator.

## Sample Apps

- React: `samples/react-math-sample` (demo only; tests live in `packages/react-math`)
    - `npm run sample:react:dev`
    - `npm run sample:react:build`
- Swift: `samples/swift-math-sample/SwiftMathSample.xcodeproj`
    - Open the `.xcodeproj` in Xcode and run the `SwiftMathSample-iOS` scheme.
    - Regenerate with `npm run sample:swift:generate` after changing `project.yml`.
    - CLI build: `npm run sample:swift:build`.
- Android: `samples/compose-math-sample`
    - Open the repository root in Android Studio and run `samples:compose-math-sample`.
    - CLI build: `npm run sample:android:build`.
    - Pixel snapshots now live in the `compose-math` library (`npm run snapshot:android:visual`), not the sample.
