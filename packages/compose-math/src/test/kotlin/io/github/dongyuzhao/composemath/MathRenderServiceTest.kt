package io.github.dongyuzhao.composemath

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MathRenderServiceTest {
    @Test
    fun processServiceIsSingleton() {
        val context = RuntimeEnvironment.getApplication()

        assertSame(
            MathRenderService.getInstance(context),
            MathRenderService.getInstance(context),
        )
    }

    @Test
    fun requestsEvaluateInFIFOOrderWithMaximumConcurrencyOne() = runBlocking {
        val controlled = ControlledRenderer()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val service = MathRenderService(controlled::render, serviceScope)

        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { service.renderSvg("first") }
            val second = async(start = CoroutineStart.UNDISPATCHED) { service.renderSvg("second") }
            val third = async(start = CoroutineStart.UNDISPATCHED) { service.renderSvg("third") }

            controlled.waitUntilStarted("first")
            assertEquals(listOf("first"), controlled.started)

            controlled.release("first")
            controlled.waitUntilStarted("second")
            controlled.release("second")
            controlled.waitUntilStarted("third")
            controlled.release("third")

            first.await()
            second.await()
            third.await()

            assertEquals(listOf("first", "second", "third"), controlled.started)
            assertEquals(listOf("first", "second", "third"), controlled.completed)
            assertEquals(1, controlled.maximumActive.get())
        } finally {
            serviceScope.cancel()
        }
    }

    @Test
    fun cancellingQueuedRequestPreventsEvaluation() = runBlocking {
        val controlled = ControlledRenderer()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val service = MathRenderService(controlled::render, serviceScope)

        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { service.renderSvg("first") }
            controlled.waitUntilStarted("first")
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
                service.renderSvg("cancelled")
            }
            val third = async(start = CoroutineStart.UNDISPATCHED) { service.renderSvg("third") }

            cancelled.cancel()
            cancelled.join()
            assertTrue(cancelled.isCancelled)
            controlled.release("first")
            controlled.waitUntilStarted("third")
            controlled.release("third")

            first.await()
            third.await()

            assertEquals(listOf("first", "third"), controlled.started)
        } finally {
            serviceScope.cancel()
        }
    }

    private class ControlledRenderer {
        val started = CopyOnWriteArrayList<String>()
        val completed = CopyOnWriteArrayList<String>()
        val maximumActive = AtomicInteger()
        private val active = AtomicInteger()
        private val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

        suspend fun render(tex: String, options: MathRenderOptions): MathJaxSvg? {
            check(options == MathRenderOptions())
            val gate = CompletableDeferred<Unit>()
            gates[tex] = gate
            started += tex
            val currentActive = active.incrementAndGet()
            maximumActive.updateAndGet { previous -> maxOf(previous, currentActive) }
            gate.await()
            active.decrementAndGet()
            completed += tex
            return MathJaxSvg(
                ok = true,
                markup = """<svg viewBox="0 0 1 1"></svg>""",
                viewBox = MathJaxViewBox(0f, 0f, 1f, 1f),
            )
        }

        fun release(tex: String) {
            checkNotNull(gates[tex]).complete(Unit)
        }

        suspend fun waitUntilStarted(tex: String) {
            withTimeout(5_000) {
                while (tex !in started || !gates.containsKey(tex)) {
                    kotlinx.coroutines.yield()
                }
            }
        }
    }
}
