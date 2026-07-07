# math-renderer

A pure, use-case-agnostic **MathJax-driven LaTeX formula renderer**.

MathJax runs inside JavaScriptCore (Swift) / `androidx.javascriptengine` (Android) to convert LaTeX to SVG. An in-house CoreGraphics / Android Canvas SVG subset renderer then rasterises the SVG to a platform image.

## Packages

| Package | Artifact |
|---------|----------|
| **npm** | `@dongyuzhao/math-renderer` |
| **SwiftPM** | `MathRenderer` |
| **Android AAR** | `io.github.dongyuzhao:math-renderer-android` |

---

## npm

Provides the MathJax wrapper API and, via `npm run build`, produces the JSC-compatible bundle consumed by Swift and Android.

```js
const { renderToSVG } = require('@dongyuzhao/math-renderer');

const { markup, viewBox, ok } = renderToSVG('x^2 + y^2 = r^2');
// markup  → sanitised SVG string
// viewBox → "minX minY width height"
// ok      → true on success
```

**Build the JSC bundle** (required before using Swift or Android):

```sh
npm install
npm run build
# Writes:
#   swift/Sources/MathRenderer/Resources/MathJax/mathjax-renderer.js
#   android/mathrenderer/src/main/assets/MathJax/mathjax-renderer.js
```

---

## Swift (SwiftPM)

Add to your `Package.swift`:

```swift
.package(url: "https://github.com/DongyuZhao/math-renderer", from: "0.1.0")
```

Then:

```swift
import MathRenderer

// Inline formula (default black, system scale)
if let img = await MathRenderer.render("x^2 + y^2 = r^2") {
    // img.image           → UIImage / NSImage
    // img.size            → CGSize in points
    // img.baselineOffset  → for inline text placement
}

// Display (block) mode with custom options
let opts = FormulaRenderOptions(standalone: true, fontSize: 20, scale: 2)
let img = await MathRenderer.render(#"\int_0^\infty e^{-x^2}\,dx"#, options: opts)

// SVG only (no rasterisation)
let svg = await MathRenderer.svg("E = mc^2")
```

**Requirements:** iOS 15+, macOS 12+, tvOS 15+, watchOS 8+, visionOS 1+

---

## Android (Kotlin)

```kotlin
import io.github.dongyuzhao.mathrenderer.MathRenderer
import io.github.dongyuzhao.mathrenderer.FormulaRenderOptions

// In a coroutine:
val image = MathRenderer.render(context, "x^2 + y^2 = r^2")
// image?.bitmap           → android.graphics.Bitmap
// image?.baselineOffsetDp → for inline text alignment

val opts = FormulaRenderOptions(standalone = true, fontSize = 20.0)
val image = MathRenderer.render(context, #"\int_0^\infty e^{-x^2}\,dx"#, opts)
```

**Requirements:** Android API 26+ (for `androidx.javascriptengine`).

---

## Architecture

```
LaTeX string
    │
    ▼  (JavaScriptCore / androidx.javascriptengine)
MathJax IIFE bundle  →  SVG string + viewBox
    │
    ▼  (in-house subset SVG parser)
CoreGraphics CGPath / Android Path draw commands
    │
    ▼  (CoreGraphics / Android Canvas)
UIImage / NSImage / android.graphics.Bitmap
```
