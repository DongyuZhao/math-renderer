import Foundation

#if canImport(JavaScriptCore)
    import JavaScriptCore
#endif

struct MathJavaScriptResource: Equatable, Sendable {
    var name: String
    var subdir: String?

    init(name: String, subdir: String? = nil) {
        self.name = name
        self.subdir = subdir
    }
}

final class MathJavaScriptEnvironment: @unchecked Sendable {
    private let name: String
    private let scripts: [MathJavaScriptResource]
    private let bundle: Bundle
    private let decoder: JSONDecoder
    private let lock = NSLock()

    #if canImport(JavaScriptCore)
        private var context: JSContext?
    #endif

    init(
        name: String,
        scripts: [MathJavaScriptResource],
        bundle: Bundle = .module,
        decoder: JSONDecoder = JSONDecoder()
    ) {
        self.name = name
        self.scripts = scripts
        self.bundle = bundle
        self.decoder = decoder
    }

    func call<Result: Decodable>(
        _ method: String,
        arguments: [Any] = [],
        as resultType: Result.Type = Result.self
    ) async -> Result? {
        #if canImport(JavaScriptCore)
            let invocation = MathJavaScriptInvocation(method: method, arguments: arguments)
            return await Task.detached(priority: .utility) {
                guard !Task.isCancelled else {
                    return nil
                }
                return self.callSynchronously(
                    invocation.method,
                    arguments: invocation.arguments,
                    as: resultType
                )
            }.value
        #else
            return nil
        #endif
    }

    #if canImport(JavaScriptCore)
        private func callSynchronously<Result: Decodable>(
            _ method: String,
            arguments: [Any],
            as resultType: Result.Type
        ) -> Result? {
            lock.lock()
            defer { lock.unlock() }

            guard let context = context ?? makeContext() else {
                return nil
            }
            self.context = context

            guard let json = invoke(method, arguments: arguments, in: context),
                let data = json.data(using: .utf8),
                let result = try? decoder.decode(resultType, from: data)
            else {
                return nil
            }
            return result
        }

        private func makeContext() -> JSContext? {
            guard let context = JSContext() else {
                return nil
            }
            context.exceptionHandler = { _, _ in }

            guard scripts.allSatisfy({ evaluate($0, in: context) }) else {
                return nil
            }

            let bridge = context.objectForKeyedSubscript(name)
            return bridge?.isUndefined == false ? context : nil
        }

        private func invoke(
            _ method: String,
            arguments: [Any],
            in context: JSContext
        ) -> String? {
            let bridge = context.objectForKeyedSubscript(name)
            guard bridge?.isUndefined == false else {
                return nil
            }

            context.exception = nil
            let result = bridge?.invokeMethod(method, withArguments: arguments)
            if context.exception != nil {
                context.exception = nil
                return nil
            }
            return result?.toString()
        }

        private func evaluate(_ resource: MathJavaScriptResource, in context: JSContext) -> Bool {
            guard
                let url =
                    bundle.url(
                        forResource: resource.name,
                        withExtension: "js",
                        subdirectory: resource.subdir
                    ) ?? bundle.url(forResource: resource.name, withExtension: "js"),
                let script = try? String(contentsOf: url, encoding: .utf8)
            else {
                return false
            }

            context.exception = nil
            context.evaluateScript(script, withSourceURL: url)
            if context.exception != nil {
                context.exception = nil
                return false
            }
            return true
        }
    #endif
}

private struct MathJavaScriptInvocation: @unchecked Sendable {
    var method: String
    var arguments: [Any]
}
