package tiiehenry.android.snapshot.provider.filesystem

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class TaskCancellationTest {
    @Test fun `cancel closes the blocking stream and refuses onDone`() {
        val task = TaskCancellation()
        val open = AtomicBoolean(true)
        task.track { open.set(false) }
        task.ensureActive()
        task.cancel()
        assertTrue(task.cancelled.get())
        assertFalse(open.get())
        try {
            task.ensureActive()
            fail("cancelled task must not continue to onDone")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
        assertEquals(CompressResult.CANCELLED, finishCompress(cancelled = true, targetExists = true))
    }

    @Test fun `a flag set before tar returns still does not count as done`() {
        assertEquals(CompressResult.CANCELLED, finishCompress(cancelled = true, targetExists = true))
        assertEquals(CompressResult.DONE, finishCompress(cancelled = false, targetExists = true))
        assertEquals(CompressResult.MISSING, finishCompress(cancelled = false, targetExists = false))
    }

    @Test fun `a stream tracked after cancel is closed immediately`() {
        val task = TaskCancellation()
        task.cancel()
        val open = AtomicBoolean(true)
        task.track { open.set(false) }
        assertFalse(open.get())
    }
}
