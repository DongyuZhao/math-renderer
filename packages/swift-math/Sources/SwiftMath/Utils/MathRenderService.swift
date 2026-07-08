import Foundation

actor MathRenderService {
    typealias Evaluator = @Sendable (String, MathRenderOptions) async -> MathJaxSVG?

    static let shared = MathRenderService(evaluate: makeEvaluator())

    private struct Request {
        let id: UUID
        let tex: String
        let options: MathRenderOptions
        let continuation: CheckedContinuation<MathJaxSVG?, Never>
    }

    private let evaluate: Evaluator
    private var queue: [Request] = []
    private var currentID: UUID?
    private var cancelledIDs: Set<UUID> = []
    private var isDraining = false

    init(evaluate: @escaping Evaluator) {
        self.evaluate = evaluate
    }

    func svg(
        for tex: String,
        options: MathRenderOptions = MathRenderOptions()
    ) async -> MathJaxSVG? {
        let id = UUID()
        return await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                enqueue(
                    Request(
                        id: id,
                        tex: tex,
                        options: options,
                        continuation: continuation
                    )
                )
            }
        } onCancel: {
            Task { await self.cancel(id: id) }
        }
    }

    private func enqueue(_ request: Request) {
        guard cancelledIDs.remove(request.id) == nil else {
            request.continuation.resume(returning: nil)
            return
        }

        queue.append(request)
        guard !isDraining else {
            return
        }
        isDraining = true
        Task { await drain() }
    }

    private func cancel(id: UUID) {
        if let index = queue.firstIndex(where: { $0.id == id }) {
            let request = queue.remove(at: index)
            request.continuation.resume(returning: nil)
            return
        }
        cancelledIDs.insert(id)
    }

    private func drain() async {
        while !queue.isEmpty {
            let request = queue.removeFirst()
            if cancelledIDs.remove(request.id) != nil {
                request.continuation.resume(returning: nil)
                continue
            }

            currentID = request.id
            let result = await evaluate(request.tex, request.options)
            let wasCancelled = cancelledIDs.remove(request.id) != nil
            currentID = nil
            request.continuation.resume(returning: wasCancelled ? nil : result)
        }
        isDraining = false
    }

    private static func makeEvaluator() -> Evaluator {
        let environment = MathJavaScriptEnvironment(
            name: "MathRenderLatexToSvg",
            scripts: [
                MathJavaScriptResource(name: "mathjax-core-runtime", subdir: "MathJax"),
                MathJavaScriptResource(name: "latex-to-svg", subdir: "MathJax"),
            ]
        )

        return { tex, options in
            guard
                let payload = await environment.call(
                    "renderJSON",
                    arguments: [tex, options.bridgeOptions],
                    as: MathJaxRenderPayload.self
                )
            else {
                return nil
            }
            return MathJaxBridge.svg(from: payload, tex: tex, options: options)
        }
    }
}
