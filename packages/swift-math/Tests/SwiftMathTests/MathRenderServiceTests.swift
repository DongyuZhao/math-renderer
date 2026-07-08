import XCTest

@testable import SwiftMath

#if canImport(JavaScriptCore)
    final class MathRenderServiceTests: XCTestCase {
        func testSharedServiceIsProcessSingleton() {
            XCTAssertTrue(MathRenderService.shared === MathRenderService.shared)
        }

        func testRequestsEvaluateInFIFOOrderWithMaximumConcurrencyOne() async throws {
            let evaluator = ControlledMathEvaluator()
            let service = MathRenderService { tex, _ in
                await evaluator.evaluate(tex)
            }

            let first = Task { await service.svg(for: "first") }
            try await evaluator.waitUntilStarted(count: 1)
            let second = Task { await service.svg(for: "second") }
            let third = Task { await service.svg(for: "third") }

            await Task.yield()
            let initiallyStarted = await evaluator.startedValues()
            XCTAssertEqual(initiallyStarted, ["first"])

            await evaluator.release("first")
            try await evaluator.waitUntilStarted(count: 2)
            await evaluator.release("second")
            try await evaluator.waitUntilStarted(count: 3)
            await evaluator.release("third")

            _ = await first.value
            _ = await second.value
            _ = await third.value

            let started = await evaluator.startedValues()
            let completed = await evaluator.completedValues()
            let maximumActive = await evaluator.maximumActiveCount()
            XCTAssertEqual(started, ["first", "second", "third"])
            XCTAssertEqual(completed, ["first", "second", "third"])
            XCTAssertEqual(maximumActive, 1)
        }

        func testCancellingQueuedRequestPreventsEvaluation() async throws {
            let evaluator = ControlledMathEvaluator()
            let service = MathRenderService { tex, _ in
                await evaluator.evaluate(tex)
            }

            let first = Task { await service.svg(for: "first") }
            try await evaluator.waitUntilStarted(count: 1)
            let cancelled = Task { await service.svg(for: "cancelled") }
            await Task.yield()
            cancelled.cancel()

            let cancelledResult = await cancelled.value
            XCTAssertNil(cancelledResult)
            await evaluator.release("first")
            _ = await first.value
            await Task.yield()

            let started = await evaluator.startedValues()
            XCTAssertEqual(started, ["first"])
        }
    }

    private actor ControlledMathEvaluator {
        private var started: [String] = []
        private var completed: [String] = []
        private var activeCount = 0
        private var maximumActive = 0
        private var gates: [String: CheckedContinuation<Void, Never>] = [:]

        func evaluate(_ tex: String) async -> MathJaxSVG? {
            started.append(tex)
            activeCount += 1
            maximumActive = max(maximumActive, activeCount)

            await withCheckedContinuation { continuation in
                gates[tex] = continuation
            }

            activeCount -= 1
            completed.append(tex)
            return MathJaxSVG(
                markup: #"<svg viewBox="0 0 1 1"></svg>"#,
                viewBox: MathJaxViewBox(minX: 0, minY: 0, width: 1, height: 1),
                fallbackText: tex
            )
        }

        func release(_ tex: String) {
            gates.removeValue(forKey: tex)?.resume()
        }

        func waitUntilStarted(count: Int) async throws {
            for _ in 0..<2_000 {
                if started.count >= count {
                    return
                }
                await Task.yield()
            }
            XCTFail("Timed out waiting for \(count) started evaluations; got \(started)")
        }

        func startedValues() -> [String] {
            started
        }

        func completedValues() -> [String] {
            completed
        }

        func maximumActiveCount() -> Int {
            maximumActive
        }
    }
#endif
