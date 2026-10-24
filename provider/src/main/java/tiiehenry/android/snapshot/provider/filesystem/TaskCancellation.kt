package tiiehenry.android.snapshot.provider.filesystem

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal enum class CompressResult { DONE, CANCELLED, MISSING }

internal fun finishCompress(cancelled: Boolean, targetExists: Boolean): CompressResult = when {
    cancelled -> CompressResult.CANCELLED
    targetExists -> CompressResult.DONE
    else -> CompressResult.MISSING
}

/**
 * One compress or extract task. [cancel] flips the existing flag and closes
 * whatever FIFO or stream the task is blocked on, then cancels its coroutine.
 */
internal class TaskCancellation(
    val cancelled: AtomicBoolean = AtomicBoolean(false)
) {
    private val closers = CopyOnWriteArrayList<() -> Unit>()
    private val job = AtomicReference<Job?>(null)

    fun track(close: () -> Unit) {
        closers.add(close)
        if (cancelled.get()) runCatching { close() }
    }

    fun bindJob(current: Job) {
        job.set(current)
        if (cancelled.get()) current.cancel(CancellationException("cancelled"))
    }

    fun cancel() {
        cancelled.set(true)
        closers.forEach { runCatching { it() } }
        job.get()?.cancel(CancellationException("cancelled"))
    }

    fun ensureActive() {
        if (cancelled.get()) throw CancellationException("cancelled")
    }
}
