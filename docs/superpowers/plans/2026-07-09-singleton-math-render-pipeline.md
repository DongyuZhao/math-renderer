# Singleton Math Render Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Swift and Android one process-wide JavaScript runtime, one MathJax initialization, a background execution boundary, and a cancellable FIFO render queue with maximum concurrency one.

**Architecture:** Swift routes every `MathJaxBridge` through a shared actor that owns the only `MathJavaScriptEnvironment`. Android replaces WebView with a long-lived QuickJS instance and routes Compose rendering through a process singleton backed by a coroutine `Channel`; both queues discard cancelled work and serialize MathJax calls.

**Tech Stack:** Swift 5.9, JavaScriptCore, Swift concurrency/XCTest, Kotlin 2.4, coroutines 1.11, QuickJS-KT 1.0.5, Compose, JUnit 4, Android instrumentation.

## Global Constraints

- Preserve the existing `Pending` followed by terminal-state rendering contract.
- Swift adds no dependency and supports iOS 16/macOS 13.
- Android adds only `io.github.dokar3:quickjs-kt:1.0.5` and keeps `minSdk = 24`.
- Packaged MathJax scripts remain byte-for-byte synced from `packages/mathjax-core`.
- Each platform has exactly one process-wide runtime and MathJax initialization.
- Render requests are FIFO with maximum MathJax concurrency one.
- Cancellation removes waiting work; an active evaluation may finish but cannot deliver to its cancelled caller.
- UI lifecycle events never close the shared runtime.

---

### Task 1: Swift FIFO actor and singleton JavaScript environment

**Files:**
- Create: `packages/swift-math/Sources/SwiftMath/Utils/MathRenderService.swift`
- Modify: `packages/swift-math/Sources/SwiftMath/Utils/MathJaxBridge.swift`
- Test: `packages/swift-math/Tests/SwiftMathTests/MathRenderServiceTests.swift`

**Interfaces:**
- Consumes: `MathJavaScriptEnvironment.call(_:arguments:as:)`, `MathRenderOptions`, `MathJaxSVG`.
- Produces: `actor MathRenderService`, `static let shared`, and `func svg(for:options:) async -> MathJaxSVG?`.

- [ ] **Step 1: Write failing actor queue tests**

Create an `@testable import SwiftMath` test suite with an injected async evaluator. The evaluator records `started`, blocks the first request with an `AsyncStream`/continuation gate, and records maximum active count. Submit `first`, `second`, and `third`; assert only `first` starts before release, then assert start and completion order is `first, second, third` and maximum active count is one. Create a second test that cancels `second` before releasing `first` and asserts `second` is never evaluated. Add a singleton identity test using `MathRenderService.shared === MathRenderService.shared`.

The service test initializer is internal:

```swift
init(evaluate: @escaping @Sendable (String, MathRenderOptions) async -> MathJaxSVG?)
```

- [ ] **Step 2: Run the focused Swift tests and verify RED**

Run: `cd packages/swift-math && swift test --filter MathRenderServiceTests`

Expected: compilation fails because `MathRenderService` does not exist.

- [ ] **Step 3: Implement the minimal actor queue**

Implement `MathRenderService` with a `Request` containing `UUID`, TeX/options, and a checked continuation. `svg(for:options:)` installs a cancellation handler, enqueues the request, and starts one drain task when idle. `cancel(id:)` removes and resumes a waiting request with `nil`, or marks the active request cancelled. `drain()` removes exactly one head request, awaits the evaluator, suppresses delivery for a cancelled active request, resumes all continuations exactly once, and advances after success or failure.

The production initializer captures one environment:

```swift
private static func makeEvaluator() -> @Sendable (String, MathRenderOptions) async -> MathJaxSVG? {
    let environment = MathJavaScriptEnvironment(
        name: "MathRenderLatexToSvg",
        scripts: [
            MathJavaScriptResource(name: "mathjax-core-runtime", subdir: "MathJax"),
            MathJavaScriptResource(name: "latex-to-svg", subdir: "MathJax"),
        ]
    )
    return { tex, options in
        guard let payload = await environment.call(
            "renderJSON",
            arguments: [tex, options.bridgeOptions],
            as: MathJaxRenderPayload.self
        ) else { return nil }
        return MathJaxBridge.svg(from: payload, tex: tex, options: options)
    }
}
```

Change `MathJaxRenderPayload` and `MathJaxBridge.svg(from:tex:options:)` from `private` to internal. Remove the per-instance environment from `MathJaxBridge`; keep `public init()` for source compatibility but make every instance call `MathRenderService.shared.svg(...)`.

- [ ] **Step 4: Run focused and existing Swift tests and verify GREEN**

Run: `cd packages/swift-math && swift test --filter MathRenderServiceTests`

Expected: all new queue tests pass.

Run: `cd packages/swift-math && swift test`

Expected: all SwiftMath tests pass with zero failures.

- [ ] **Step 5: Commit Swift service work**

```bash
git add packages/swift-math/Sources/SwiftMath/Utils/MathRenderService.swift packages/swift-math/Sources/SwiftMath/Utils/MathJaxBridge.swift packages/swift-math/Tests/SwiftMathTests/MathRenderServiceTests.swift
git commit -m "feat(swift): serialize MathJax through shared actor"
```

### Task 2: QuickJS runtime adapter and one-time MathJax initialization

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `packages/compose-math/gradle/libs.versions.toml`
- Modify: `packages/compose-math/build.gradle.kts`
- Modify: `packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathTypes.kt`
- Modify: `packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathJaxBridge.kt`
- Create: `packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/QuickJsMathJaxRuntime.kt`
- Delete: `packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/WebViewMathJaxRuntime.kt`
- Test: `packages/compose-math/src/test/kotlin/io/github/dongyuzhao/composemath/MathJaxBridgeContractTest.kt`

**Interfaces:**
- Consumes: `QuickJs.create(Dispatchers.Default)` and bundled assets under `math-renderer/`.
- Produces: `internal fun interface MathJaxRuntime { suspend fun evaluate(script: String): String? }` and `class QuickJsMathJaxRuntime(context: Context) : MathJaxRuntime, Closeable`.

- [ ] **Step 1: Write failing suspend-runtime contract tests**

Convert the fake runtime in `MathJaxBridgeContractTest` to a suspend lambda. Add a counting runtime test that calls `render` twice and asserts the runtime sees exactly two render invocations without WebView JSON-string decoding. Keep assertions for quoted TeX, `standalone`, `fontSize`, and `scale`.

- [ ] **Step 2: Run the focused Android test and verify RED**

Run: `env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :packages:compose-math:testDebugUnitTest --tests '*MathJaxBridgeContractTest'`

Expected: compilation fails because `MathJaxRuntime.evaluate` is callback-based and `MathJaxBridge.render` is not suspend.

- [ ] **Step 3: Add QuickJS-KT and implement the adapter**

Add:

```toml
quickjs-kt = "1.0.5"
quickjs-kt = { module = "io.github.dokar3:quickjs-kt", version.ref = "quickjs-kt" }
```

and `implementation(libs.quickjs.kt)`.

Change `MathJaxRuntime.evaluate` and `MathJaxBridge.render` to suspend functions. `QuickJsMathJaxRuntime` stores `context.applicationContext`, creates exactly one `QuickJs` with `Dispatchers.Default`, and guards initialization with a `Mutex`. Its first evaluation reads and evaluates `mathjax-core-runtime.js` and `latex-to-svg.js` once, then evaluates the supplied invocation as `String?`. Return `null` on asset or `QuickJsException` failure. `close()` closes the one QuickJS instance and prevents later evaluation.

Remove WebView-specific result decoding from `MathJaxBridge`; QuickJS already returns the JavaScript string value. Retain payload sanitization and error mapping unchanged.

- [ ] **Step 4: Run focused Android tests and verify GREEN**

Run the command from Step 2.

Expected: all `MathJaxBridgeContractTest` tests pass.

- [ ] **Step 5: Commit the QuickJS adapter**

```bash
git add gradle/libs.versions.toml packages/compose-math/gradle/libs.versions.toml packages/compose-math/build.gradle.kts packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathTypes.kt packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathJaxBridge.kt packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/QuickJsMathJaxRuntime.kt packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/WebViewMathJaxRuntime.kt packages/compose-math/src/test/kotlin/io/github/dongyuzhao/composemath/MathJaxBridgeContractTest.kt
git commit -m "feat(android): replace WebView runtime with QuickJS"
```

### Task 3: Android singleton background service and render queue

**Files:**
- Create: `packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathRenderService.kt`
- Modify: `packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathRenderer.kt`
- Modify: `packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathText.kt`
- Test: `packages/compose-math/src/test/kotlin/io/github/dongyuzhao/composemath/MathRenderServiceTest.kt`
- Test: `packages/compose-math/src/test/kotlin/io/github/dongyuzhao/composemath/MathRendererContractTest.kt`

**Interfaces:**
- Consumes: suspend `MathJaxBridge.render(tex, options)` and `MathSvgParser.parse(markup)`.
- Produces: process singleton `MathRenderService.getInstance(context)`, internal injected `MathRenderService(bridge, scope)`, and `suspend fun renderSvg(tex, options): MathJaxSvg?`.

- [ ] **Step 1: Write failing FIFO, concurrency, cancellation, and singleton tests**

Use a fake suspend runtime whose first request waits on a `CompletableDeferred`. Start three `async` renders, assert only the first invocation starts, release each gate, and assert FIFO order plus maximum active count one. Cancel the second deferred while the first is blocked and assert the fake never receives the second TeX. Add an identity assertion that two `getInstance(applicationContext)` calls return the same object.

- [ ] **Step 2: Run service tests and verify RED**

Run: `env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :packages:compose-math:testDebugUnitTest --tests '*MathRenderServiceTest'`

Expected: compilation fails because `MathRenderService` does not exist.

- [ ] **Step 3: Implement the singleton channel service**

Create a service-owned `CoroutineScope(SupervisorJob() + Dispatchers.Default)` and an unlimited `Channel<Request>`. Each `Request` contains TeX/options and a `CompletableDeferred<MathJaxSvg?>`. One consumer loops over the channel and calls the bridge sequentially. `renderSvg` sends a request and awaits its deferred; cancellation marks the deferred cancelled, and the consumer skips cancelled requests before evaluation and suppresses result delivery after active cancellation. A failed request completes with `null` and cannot terminate the consumer.

The companion object uses double-checked locking on a volatile instance and always stores `context.applicationContext`. A test-injected constructor accepts bridge/scope and does not touch native QuickJS.

Refactor `MathRenderer` to depend on a suspend `(String, MathRenderOptions) -> MathJaxSvg?`; production uses the shared service and tests inject a bridge-backed lambda. Remove `suspendCancellableCoroutine`. Refactor `MathText` to obtain `MathRenderService.getInstance(LocalContext.current)`, remember only the lightweight renderer, and remove `DisposableEffect` and all runtime closing.

- [ ] **Step 4: Run service, renderer, and full Android unit tests**

Run the focused command from Step 2, then:

`env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :packages:compose-math:testDebugUnitTest`

Expected: all tests pass with zero failures.

- [ ] **Step 5: Commit Android queue work**

```bash
git add packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathRenderService.kt packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathRenderer.kt packages/compose-math/src/main/kotlin/io/github/dongyuzhao/composemath/MathText.kt packages/compose-math/src/test/kotlin/io/github/dongyuzhao/composemath/MathRenderServiceTest.kt packages/compose-math/src/test/kotlin/io/github/dongyuzhao/composemath/MathRendererContractTest.kt
git commit -m "feat(android): add singleton MathJax render service"
```

### Task 4: Real-runtime integration, documentation, and final verification

**Files:**
- Modify: `samples/compose-math-sample/app/src/androidTest/kotlin/io/github/dongyuzhao/composemathsample/MathJaxRuntimeInstrumentationTest.kt`
- Modify: `README.md`
- Modify: `packages/mathjax-core/test/support/render-native.mjs`
- Modify: `docs/superpowers/plans/2026-07-08-shared-mathjax-migration.md`

**Interfaces:**
- Consumes: `MathRenderService.getInstance(context)` and existing common formula fixtures.
- Produces: instrumentation proof that the packaged scripts render through shared QuickJS and accurate public documentation.

- [ ] **Step 1: Replace WebView instrumentation usage before implementation verification**

Rewrite the instrumentation test with `runBlocking` and the shared service. Render a representative formula twice, assert both SVGs are valid, and run the common fixture corpus through the same singleton. Remove latches, `WebViewMathJaxRuntime`, and explicit runtime closing.

- [ ] **Step 2: Build and run real-runtime verification**

Run: `env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :samples:compose-math-sample:connectedDebugAndroidTest`

Expected: the QuickJS integration tests and formula corpus pass on the connected emulator/device. If no device is connected, record that limitation and at minimum run `:samples:compose-math-sample:assembleDebugAndroidTest` successfully.

- [ ] **Step 3: Update documentation**

Replace WebView descriptions with QuickJS. Document the single JavaScriptCore actor on Swift, the single QuickJS service on Android, one MathJax initialization per process, FIFO maximum-concurrency-one behavior, and cancellation semantics. Update historical migration wording so current architecture is unambiguous.

- [ ] **Step 4: Run fresh complete verification**

Run:

```bash
node --test test/shared-mathjax-assets.test.mjs
cd packages/swift-math && swift test
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :packages:compose-math:testDebugUnitTest :samples:compose-math-sample:assembleDebug :samples:compose-math-sample:assembleDebugAndroidTest
git diff --check
```

Expected: every command exits zero; Swift and Android report zero test failures; generated asset sync remains intact; Git reports no whitespace errors.

- [ ] **Step 5: Commit integration and docs**

```bash
git add README.md packages/mathjax-core/test/support/render-native.mjs docs/superpowers/plans/2026-07-08-shared-mathjax-migration.md samples/compose-math-sample/app/src/androidTest/kotlin/io/github/dongyuzhao/composemathsample/MathJaxRuntimeInstrumentationTest.kt
git commit -m "docs: describe singleton native math runtimes"
```
