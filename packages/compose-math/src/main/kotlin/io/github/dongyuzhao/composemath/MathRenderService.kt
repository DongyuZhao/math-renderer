package io.github.dongyuzhao.composemath

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class MathRenderService internal constructor(
    private val render: suspend (String, MathRenderOptions) -> MathJaxSvg?,
    scope: CoroutineScope,
) {
    private data class Request(
        val tex: String,
        val options: MathRenderOptions,
        val completion: CompletableDeferred<MathJaxSvg?>,
    )

    private val queue = Channel<Request>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (request in queue) {
                if (!request.completion.isActive) {
                    continue
                }

                val result = try {
                    render(request.tex, request.options)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }

                if (request.completion.isActive) {
                    request.completion.complete(result)
                }
            }
        }
    }

    suspend fun renderSvg(tex: String, options: MathRenderOptions = MathRenderOptions()): MathJaxSvg? {
        val completion = CompletableDeferred<MathJaxSvg?>()
        return try {
            queue.send(Request(tex, options, completion))
            completion.await()
        } catch (cancelled: CancellationException) {
            completion.cancel(cancelled)
            throw cancelled
        }
    }

    private constructor(context: Context) : this(
        render = MathJaxBridge(
            QuickJsMathJaxRuntime.getInstance(context.applicationContext),
        )::render,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    companion object {
        @Volatile
        private var instance: MathRenderService? = null

        @JvmStatic
        fun getInstance(context: Context): MathRenderService = instance ?: synchronized(this) {
            instance ?: MathRenderService(context.applicationContext).also { instance = it }
        }
    }
}
