import Foundation

#if canImport(JavaScriptCore)
    import JavaScriptCore
#endif

struct JavaScriptResource: Equatable, Sendable {
    var name: String
    var subdir: String?

    init(name: String, subdir: String? = nil) {
        self.name = name
        self.subdir = subdir
    }
}

/// Wraps a JSContext, lazily loading scripts on first use.
/// Thread-safe: all JSC calls are serialised through an NSLock.
final class JavaScriptEnvironment: @unchecked Sendable {
    private let bridgeName: String
    private let scripts: [JavaScriptResource]
    private let bundle: Bundle
    private let decoder: JSONDecoder
    private let lock = NSLock()

    #if canImport(JavaScriptCore)
        private var context: JSContext?
    #endif

    init(
        bridgeName: String,
        scripts: [JavaScriptResource],
        bundle: Bundle = .module,
        decoder: JSONDecoder = JSONDecoder()
    ) {
        self.bridgeName = bridgeName
        self.scripts = scripts
        self.bundle = bundle
        self.decoder = decoder
    }

    /// Calls `method` on the bridge object and decodes the JSON result.
    func call<Result: Decodable>(
        _ method: String,
        arguments: [Any] = [],
        as resultType: Result.Type = Result.self
    ) async -> Result? {
        #if canImport(JavaScriptCore)
            let invocation = Invocation(method: method, arguments: arguments)
            return await Task.detached(priority: .utility) { [self] in
                guard !Task.isCancelled else { return nil }
                return self.callSynchronously(invocation.method, arguments: invocation.arguments, as: resultType)
            }.value
        #else
            return nil
        #endif
    }

    // MARK: - Private

    #if canImport(JavaScriptCore)
        private func callSynchronously<Result: Decodable>(
            _ method: String,
            arguments: [Any],
            as resultType: Result.Type
        ) -> Result? {
            lock.lock()
            defer { lock.unlock() }

            guard let ctx = context ?? makeContext() else { return nil }
            self.context = ctx

            guard
                let json = invoke(method, arguments: arguments, in: ctx),
                let data = json.data(using: .utf8),
                let result = try? decoder.decode(resultType, from: data)
            else { return nil }

            return result
        }

        private func makeContext() -> JSContext? {
            guard let ctx = JSContext() else { return nil }
            ctx.exceptionHandler = { _, _ in }

            guard scripts.allSatisfy({ evaluate($0, in: ctx) }) else { return nil }

            let bridge = ctx.objectForKeyedSubscript(bridgeName)
            return bridge?.isUndefined == false ? ctx : nil
        }

        private func invoke(_ method: String, arguments: [Any], in ctx: JSContext) -> String? {
            let bridge = ctx.objectForKeyedSubscript(bridgeName)
            guard bridge?.isUndefined == false else { return nil }

            ctx.exception = nil
            let result = bridge?.invokeMethod(method, withArguments: arguments)
            if ctx.exception != nil {
                ctx.exception = nil
                return nil
            }
            return result?.toString()
        }

        private func evaluate(_ resource: JavaScriptResource, in ctx: JSContext) -> Bool {
            guard
                let url =
                    bundle.url(forResource: resource.name, withExtension: "js", subdirectory: resource.subdir)
                    ?? bundle.url(forResource: resource.name, withExtension: "js"),
                let script = try? String(contentsOf: url, encoding: .utf8)
            else { return false }

            ctx.exception = nil
            ctx.evaluateScript(script, withSourceURL: url)
            if ctx.exception != nil {
                ctx.exception = nil
                return false
            }
            return true
        }
    #endif
}

private struct Invocation: @unchecked Sendable {
    var method: String
    var arguments: [Any]
}
