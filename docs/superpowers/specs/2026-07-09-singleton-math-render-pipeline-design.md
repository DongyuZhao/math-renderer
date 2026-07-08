# Singleton Math Render Pipeline Design

## Goal

Ensure the Swift and Android renderers each use one process-wide JavaScript runtime, one initialized MathJax environment, a background execution boundary, and a global FIFO render queue that executes at most one MathJax request at a time.

## Scope and constraints

- Preserve the existing public rendering behavior and the `Pending` followed by terminal-state contract.
- Swift uses the platform JavaScriptCore framework and adds no dependency.
- Android replaces WebView with one QuickJS dependency: `io.github.dokar3:quickjs-kt:1.0.5`.
- Android keeps `minSdk = 24`.
- MathJax remains sourced from the shared generated assets in `packages/mathjax-core`.
- Queued requests execute in global FIFO order with a maximum MathJax concurrency of one.
- Cancellation removes a request that has not started. A request whose JavaScript evaluation has started may finish, but its result is not delivered to a cancelled caller.
- UI components do not own or close JavaScript runtimes.

## Swift architecture

`MathRenderService.shared` is an actor and the sole owner of the Swift JavaScript execution path. Actor isolation supplies the background-safe serialization boundary and the render queue. The service owns one lazily initialized `MathJavaScriptEnvironment`, which owns one `JSContext`. Loading `mathjax-core-runtime.js` and `latex-to-svg.js` initializes MathJax exactly once for the life of that context.

`MathJaxBridge.shared` remains the package-level bridge but delegates all evaluation to `MathRenderService.shared`. Public construction must not create another JavaScript environment. `MathRenderer.render(...)` retains its current `AsyncStream` API and submits work through the shared service.

The service assigns each request an identity, appends it to a FIFO queue, and starts the queue processor when idle. Cancellation removes a waiting identity. The processor evaluates one request, resumes its continuation if still active, then advances to the next request. JavaScriptCore work does not run on the main actor.

## Android architecture

The Compose package adds `io.github.dokar3:quickjs-kt:1.0.5` as an implementation dependency. `WebViewMathJaxRuntime` is removed and replaced by `QuickJsMathJaxRuntime`, a long-lived runtime adapter backed by one `QuickJs` instance created with a background dispatcher.

`MathRenderService` is an application-process singleton. It owns the sole `QuickJsMathJaxRuntime`, the sole `MathJaxBridge`, a service coroutine scope, and a FIFO channel or equivalent queue. The consumer processes one request at a time. On first use, the runtime evaluates `mathjax-core-runtime.js` followed by `latex-to-svg.js`; subsequent renders evaluate only the render invocation. Initialization failure is retained and reported consistently rather than creating replacement runtimes implicitly.

`MathText` no longer creates a runtime, bridge, or renderer with `remember`, and it never closes the shared runtime. It submits rendering through the shared service. QuickJS evaluation, payload parsing, and SVG parsing occur away from the Android main thread. Compose state delivery returns to the composition coroutine naturally.

The `MathJaxRuntime` abstraction remains available internally so deterministic JVM tests can use fakes without loading native QuickJS. Android instrumentation tests exercise the real singleton QuickJS runtime and packaged MathJax assets.

## Request and cancellation flow

1. A caller requests a render and immediately observes `Pending`.
2. The platform renderer validates options before enqueueing JavaScript work.
3. The request is appended to the process-wide FIFO queue.
4. If the request is cancelled while waiting, it is removed and never evaluated.
5. The queue processor initializes its sole runtime and MathJax environment on first use.
6. Exactly one `renderJSON` call executes.
7. The returned payload is decoded, sanitized, parsed, and rasterized using the existing native platform pipeline.
8. If the caller is still active, it receives `Succeeded` or `Failed`; otherwise the completed result is discarded.
9. The processor advances to the next queued request.

## Error handling

Invalid options continue to produce `InvalidInput` without entering the queue. Runtime creation, script loading, or evaluation failures map to `BridgeUnavailable`. A MathJax error payload maps to `MathJaxError`; malformed or unusable SVG maps to `RenderFailed`. A failure does not create a second runtime and does not stall later queued requests.

On Android, explicit test-only shutdown closes the QuickJS instance and cancels the service scope. Production UI lifecycle events do not close the process singleton. On Swift, the actor and JSContext live for the process lifetime.

## Testing

Swift unit tests will verify:

- repeated access uses the same service and JavaScript environment;
- MathJax resources initialize once;
- concurrent submissions complete in FIFO order;
- observed MathJax concurrency never exceeds one;
- cancelling a queued request prevents its evaluation;
- existing renderer, bridge, parser, and visual tests remain green.

Android JVM tests with a fake runtime will verify:

- repeated access uses the same service;
- initialization scripts run once and in order;
- render invocations execute FIFO with maximum concurrency one;
- queued cancellation prevents evaluation;
- completion or failure always advances the queue;
- `MathText` and renderer contracts remain unchanged.

Android instrumentation tests will verify that the packaged scripts load and representative formulae render through the real QuickJS runtime. Existing golden SVG and raster screenshot tests remain unchanged except for WebView-specific naming and setup.

## Documentation and compatibility

README descriptions will identify JavaScriptCore on Swift and QuickJS on Android, state that each platform uses one process-wide MathJax runtime and serial render queue, and remove the WebView-specific claims. No React behavior changes are included.
