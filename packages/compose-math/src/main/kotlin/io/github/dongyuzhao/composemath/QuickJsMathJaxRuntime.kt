package io.github.dongyuzhao.composemath

import android.content.Context
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

internal class QuickJsMathJaxRuntime private constructor(context: Context) :
    MathJaxRuntime,
    Closeable {
    private val appContext = context.applicationContext
    private val quickJsDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        QuickJs.create(Dispatchers.Default).apply {
            maxStackSize = 2L * 1024L * 1024L
        }
    }
    private val quickJs by quickJsDelegate
    private val mutex = Mutex()
    private var initialized = false

    override suspend fun evaluate(script: String): String? = mutex.withLock {
        try {
            check(!quickJs.isClosed) { "QuickJS runtime is closed." }
            ensureInitialized()
            quickJs.evaluate<String?>(script, filename = "math-render-invocation.js")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        if (quickJsDelegate.isInitialized()) {
            quickJs.close()
        }
    }

    private suspend fun ensureInitialized() {
        if (initialized) {
            return
        }

        val runtime = requireNotNull(
            appContext.assets.readMathAsset("math-renderer/mathjax-core-runtime.js"),
        ) { "Missing packaged MathJax runtime." }
        val bridge = requireNotNull(
            appContext.assets.readMathAsset("math-renderer/latex-to-svg.js"),
        ) { "Missing packaged LaTeX-to-SVG bridge." }

        quickJs.evaluate<Any?>(runtime, filename = "mathjax-core-runtime.js")
        quickJs.evaluate<Any?>(bridge, filename = "latex-to-svg.js")
        initialized = true
    }

    companion object {
        @Volatile
        private var instance: QuickJsMathJaxRuntime? = null

        fun getInstance(context: Context): QuickJsMathJaxRuntime = instance ?: synchronized(this) {
            instance ?: QuickJsMathJaxRuntime(context.applicationContext).also { instance = it }
        }
    }
}

private fun android.content.res.AssetManager.readMathAsset(path: String): String? = runCatching {
    open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}.getOrNull()
